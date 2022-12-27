package com.leaf.explorer.util;

import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.system.ErrnoException;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.genonbeta.android.framework.app.Storage;
import com.genonbeta.android.framework.app.SuperUser;
import com.genonbeta.android.framework.util.JavaUtils;
import com.leaf.explorer.R;
import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CopyPasteUtils {

    public static final String TAG = CopyPasteUtils.class.getSimpleName();

    public static ArrayList<Storage.Node> getNode(Context context, Storage storage, List<com.genonbeta.android.framework.io.DocumentFile> copiedItems) {
        Storage.Nodes selected = new Storage.Nodes();
        ArrayList<Storage.Node> files = new ArrayList<>();

        for (com.genonbeta.android.framework.io.DocumentFile items : copiedItems) {
            String[] splitPath = items.getUri().getPath().split(File.separator);
            Storage.ArchiveReader r = storage.fromArchive(items.getUri(), true);

            if (r != null) {
                files.add(new Storage.Node(items.getUri(), items.getName(), items.isDirectory(), items.getLength(), items.getLastModified()));
            } else if (splitPath[1].equals("storage") || splitPath[2].equals("emulated")) {
                File mFile = new File(items.getUri().getPath());
                files.add(new Storage.Node(mFile));
            } else {
                DocumentFile mFile = DocumentFile.fromSingleUri(context, items.getUri());
                if (mFile != null) {
                    files.add(new Storage.Node(mFile));
                }
            }
        }

        selected.addAll(files);

        return new Storage.Nodes(selected);
    }

    public static class PasteBuilder extends OperationBuilder {
        Handler handler = new Handler(Looper.myLooper());
        PendingOperation op;
        String n;
        public static HashMap<String, String> rename = new HashMap<>();

        public PasteBuilder(Context context) {
            super(context);
            create(R.layout.layout_paste);
        }

        public void create(Uri calcUri, final ArrayList<Storage.Node> ff, final boolean move, final Uri uri) {
            if (move)
                n = getContext().getString(R.string.files_moving);
            else
                n = getContext().getString(R.string.files_copying);
            setTitle(n);

            op = new PendingOperation(getContext(), calcUri, ff) {
                int deleteIndex = -1;
                ArrayList<Storage.Node> delete = new ArrayList<>();

                @Override
                public void run() {
                    try {
                        if (calcIndex < calcs.size()) {
                            if (!calc()) {
                                if (move)
                                    Collections.sort(files, new SortMove()); // we have to sort only if array contains symlinks and move operation
                                calcDone();
                            }
                            title.setGravity(Gravity.NO_GRAVITY);
                            title.setText(context.getString(R.string.files_calculating) + ": " + formatCalc());
                            update(this);
                            from.setText(context.getString(R.string.copy_from) + " " + formatStart());
                            to.setText(context.getString(R.string.copy_to) + " " + Storage.getDisplayName(context, uri));
                            post();
                            return;
                        }
                        synchronized (lock) {
                            if (is != null && os != null) {
                                final Storage.Node f = files.get(filesIndex);
                                int old = filesIndex;
                                Uri oldt = t;
                                if (thread == null) {
                                    interrupt.set(false);
                                    thread = new Thread("Copy thread") {
                                        @Override
                                        public void run() {
                                            byte[] buf = new byte[SuperUser.BUF_SIZE];
                                            try {
                                                int len;
                                                while ((len = copy(buf)) > 0) {
                                                    synchronized (lock) {
                                                        current += len;
                                                        processed += len;
                                                        info.step(current);
                                                    }
                                                    if (Thread.currentThread().isInterrupted() || interrupt.get())
                                                        return;
                                                }
                                                synchronized (lock) {
                                                    finish();
                                                    if (move)
                                                        FileUtils.delete(context, f.uri, storage);
                                                    filesIndex++;
                                                    thread = null; // close thread
                                                    post();
                                                }
                                            } catch (Exception e) {
                                                synchronized (lock) {
                                                    delayed = e; // thread != null
                                                    post();
                                                }
                                            }
                                        }
                                    };
                                    thread.start();
                                } else {
                                    if (delayed != null) {
                                        Throwable d = delayed;
                                        thread = null;
                                        delayed = null;
                                        throw new RuntimeException(d);
                                    }
                                }
                                post(thread == null ? 0 : 1000);
                                int a = info.getAverageSpeed();
                                String e;
                                long diff = 0;
                                if (a > 0)
                                    diff = (f.size - current) * 1000 / a;
                                if (diff >= 1000)
                                    e = Utils.formatLeftExact(context, diff);
                                else
                                    e = "∞";
                                title.setGravity(Gravity.CENTER);
                                title.setText(n + " " + Utils.formatSize(context, a) + context.getString(R.string.per_second) + ", " + e);
                                update(this, old, f);
                                from.setText(context.getString(R.string.copy_from) + " " + Storage.getDisplayName(context, f.uri));
                                to.setText(context.getString(R.string.copy_to) + " " + Storage.getDisplayName(context, oldt));
                                return;
                            }
                        }
                        if (filesIndex < files.size()) {
                            cancel(); // cancel previous skiped operation if existed
                            Storage.Node f = files.get(filesIndex);
                            Storage.Node target = getTarget(f); // special Node with no 'uri' if not found, and full path 'name'
                            try {
                                if (f.dir) {
                                    if (!(target.uri != null && target.dir) && storage.mkdir(uri, target.name) == null)
                                        throw new RuntimeException("Unable create dir: " + target);
                                    filesIndex++;
                                    if (move) {
                                        delete.add(f);
                                        deleteIndex = delete.size() - 1; // reverse index
                                    }
                                } else {
                                    if (target.uri != null && !target.dir) {
                                        switch (check(f, target).iterator().next()) {
                                            case NONE:
                                                break;
                                            case ASK:
                                                pasteConflict(PasteBuilder.this, this, f, target);
                                                return;
                                            case SKIP:
                                                filesIndex++;
                                                post();
                                                return;
                                            case OVERWRITE:
                                                FileUtils.delete(context, target.uri, storage);
                                                break;
                                        }
                                    }
                                    if (f instanceof Storage.SymlinkNode) {
                                        Storage.SymlinkNode l = (Storage.SymlinkNode) f;
                                        if (storage.symlink(l, uri) || l.isSymDir()) { // if fails, continue with content copy
                                            filesIndex++;
                                            if (move)
                                                FileUtils.delete(context, f.uri, storage);
                                            post();
                                            return;
                                        } else { // we about to copy symlinks as files, update total!!!
                                            total += SuperUser.length(storage.getSu(), Storage.getFile(f.uri));
                                        }
                                    }
                                    if (move && storage.mv(f.uri, uri, target.name)) { // try same node device 'mv' operation
                                        filesIndex++;
                                        processed += f.size;
                                        post();
                                        return;
                                    }
                                    open(f, uri, target.name);
                                    info.start(current);
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            post();
                            return;
                        }
                        if (deleteIndex >= 0) {
                            Storage.Node f = delete.get(deleteIndex);
                            FileUtils.delete(context, f.uri, storage);
                            deleteIndex--;
                            title.setVisibility(View.GONE);
                            progressFile.setVisibility(View.GONE);
                            progressTotal.setVisibility(View.GONE);
                            from.setText(context.getString(R.string.files_deleting) + ": " + Storage.getDisplayName(context, f.uri));
                            to.setVisibility(View.GONE);
                            post();
                            return;
                        }
                        success();
                    } catch (RuntimeException e) {
                        switch (check(e).iterator().next()) {
                            case SKIP:
                                Log.e(TAG, "skip", e);
                                filesIndex++;
                                cancel();
                                post();
                                return;
                        }
                        if (retry != null)
                            retry.dismiss();
                        retry = pasteError(PasteBuilder.this, this, e, move);
                    }
                }

                Storage.Node getTarget(Storage.Node f) {
                    String target;
                    String p = getFirst(f.name);
                    String r = rename.get(p);
                    if (r != null)
                        target = new File(r, Storage.relative(p, f.name)).getPath();
                    else
                        target = f.name;
                    Storage.Node t;
                    String s = uri.getScheme();
                    if (s.equals(ContentResolver.SCHEME_FILE)) {
                        File k = Storage.getFile(uri);
                        File m = new File(k, target);
                        if (storage.getRoot()) {
                            t = new Storage.Node(m);
                            if (SuperUser.exists(storage.getSu(), m))
                                t.dir = SuperUser.isDirectory(storage.getSu(), m);
                            else
                                t.uri = null;
                        } else {
                            t = new Storage.Node(m);
                            if (!m.exists())
                                t.uri = null;
                        }
                    } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
                        DocumentFile k = Storage.getDocumentFile(context, uri, target); // target == path
                        if (k != null && k.exists())
                            t = new Storage.Node(k);
                        else
                            t = new Storage.Node(); // t.uri = null && t.dir = false
                    } else {
                        throw new Storage.UnknownUri();
                    }
                    t.name = target;
                    return t;
                }

                public void post() {
                    post(0);
                }

                public void post(long l) {
                    handler.removeCallbacks(this);
                    handler.postDelayed(this, l);
                }

                @Override
                public void retry() {
                    cancel();
                    if (calcIndex < calcs.size()) { // calc error
                        calcIndex = 0;
                        filesIndex = 0;
                    }
                }
            };

            neutral = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final View.OnClickListener neutral = this;
                    op.pause();
                    handler.removeCallbacks(op);
                    final Button b = d.getButton(DialogInterface.BUTTON_NEUTRAL);
                    b.setText(R.string.resume);
                    PasteBuilder.this.neutral = new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            op.run();
                            b.setText(R.string.pause);
                            PasteBuilder.this.neutral = neutral;
                        }
                    };
                }
            };

            dismiss = new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    op.close();
                    dismiss();
                    handler.removeCallbacks(op);
                }
            };
        }

        public void calcDone() {
        }

        public void success() {
            dismiss(); // all done!
        }

        @Override
        public AlertDialog show() {
            AlertDialog d = super.show();
            op.run();
            return d;
        }
    }

    public static class OperationBuilder extends AlertDialog.Builder {
        public View v;

        public TextView title;
        public TextView from;
        public TextView to;
        public ProgressBar progressFile;
        public ProgressBar progressTotal;
        public TextView filesCount;
        public TextView filesTotal;

        public AlertDialog d;

        public DialogInterface.OnDismissListener dismiss = new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
            }
        };
        public View.OnClickListener neutral = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        };
        public View.OnClickListener negative = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        };

        public OperationBuilder(Context context) {
            super(context);
        }

        void create(int id) {
            setCancelable(false);
            LayoutInflater inflater = LayoutInflater.from(getContext());
            v = inflater.inflate(id, null);
            setView(v);
            setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });
            setNeutralButton(R.string.pause, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });

            title = (TextView) v.findViewById(R.id.title);
            from = (TextView) v.findViewById(R.id.from);
            to = (TextView) v.findViewById(R.id.to);
            progressFile = (ProgressBar) v.findViewById(R.id.progress_file);
            progressTotal = (ProgressBar) v.findViewById(R.id.progress_total);
            filesCount = (TextView) v.findViewById(R.id.files_count);
            filesTotal = (TextView) v.findViewById(R.id.files_size);

            setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    dismiss.onDismiss(dialog);
                }
            });
        }

        @Override
        public AlertDialog create() {
            d = super.create();
            d.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    Button b = d.getButton(DialogInterface.BUTTON_NEGATIVE);
                    b.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            negative.onClick(v);
                        }
                    });
                    b = d.getButton(DialogInterface.BUTTON_NEUTRAL);
                    b.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            neutral.onClick(v);
                        }
                    });
                }
            });
            return d;
        }

        @Override
        public AlertDialog show() {
            return super.show();
        }

        public void dismiss() {
            d.dismiss();
        }

        public void update(PendingOperation op) {
            filesCount.setText(op.filesIndex + " / " + op.files.size());
            progressFile.setProgress(0);
            progressTotal.setProgress(0);
            filesTotal.setText(Utils.formatSize(getContext(), op.processed) + " / " + Utils.formatSize(getContext(), op.total));
        }

        public void update(PendingOperation op, int old, Storage.Node f) {
            filesCount.setText(old + " / " + op.files.size());
            progressFile.setProgress(f.size == 0 ? 0 : (int) (op.current * 100 / f.size));
            progressTotal.setProgress(op.total == 0 ? 0 : (int) (op.processed * 100 / op.total));
            filesTotal.setText(Utils.formatSize(getContext(), op.processed) + " / " + Utils.formatSize(getContext(), op.total));
        }
    }

    public static class PendingOperation implements Runnable {
        public Context context;
        public ContentResolver resolver;
        public Storage storage;
        public final Object lock = new Object();
        public final AtomicBoolean interrupt = new AtomicBoolean(); // soft interrupt
        public Thread thread;
        public Throwable delayed;

        public int calcIndex;
        public ArrayList<Storage.Node> calcs;
        public ArrayList<Storage.Node> calcsStart; // initial calcs dirs for UI
        public Uri calcUri; // root uri

        public int filesIndex;
        public ArrayList<Storage.Node> files = new ArrayList<>();

        public Storage.Node f;
        public InputStream is;
        public OutputStream os;
        public Uri t; // target file, to update last modified time or delete in case of errors

        public SpeedInfo info = new SpeedInfo(); // speed info
        public long current; // current file transfers
        public long processed; // processed files bytes
        public long total; // total size of all files

        public EnumSet<OPERATION> small = EnumSet.of(OPERATION.ASK); // overwrite file smaller then source
        public EnumSet<OPERATION> big = EnumSet.of(OPERATION.ASK); // overwrite file bigger then source
        public EnumSet<OPERATION> newer = EnumSet.of(OPERATION.ASK); // overwrite same size file but newer date
        public EnumSet<OPERATION> same = EnumSet.of(OPERATION.ASK); // same file size and date
        public EnumSet<OPERATION> original = EnumSet.of(OPERATION.SKIP); // same file size and date

        AlertDialog retry; // retry operation dialog

        public SparseArray<EnumSet<OPERATION>> errno = new SparseArray<EnumSet<OPERATION>>() {
            @Override
            public EnumSet<OPERATION> get(int key) {
                EnumSet<OPERATION> v = super.get(key);
                if (v == null)
                    put(key, v = EnumSet.of(OPERATION.ASK));
                return v;
            }
        };

        public enum OPERATION {NONE, ASK, SKIP, OVERWRITE}

        public PendingOperation(Context context) {
            this.context = context;
            this.storage = new Storage(context);
            this.resolver = context.getContentResolver();
        }

        public PendingOperation(Context context, Uri root, ArrayList<Storage.Node> ff) {
            this(context);
            calcIndex = 0;
            calcs = new ArrayList<>(ff);
            calcsStart = new ArrayList<>(ff);
            calcUri = root;
        }

        public boolean exists(Uri uri) { // prevent adding recurcive symlinks
            for (Storage.Node n : calcs) {
                if (n.uri.equals(uri))
                    return true;
            }
            return false;
        }

        public void walk(Uri uri) {
            ArrayList<Storage.Node> nn = storage.walk(calcUri, uri);
            for (Storage.Node n : nn) {
                if (n.dir) {
                    if (n.uri.equals(uri) || n instanceof Storage.SymlinkNode && exists(Uri.fromFile(((Storage.SymlinkNode) n).getTarget()))) // walk return current dirs, do not follow it
                        files.add(n);
                    else
                        calcs.add(n);
                } else {
                    files.add(n);
                    total += n.size;
                }
            }
        }

        public boolean calc() {
            Storage.Node c = calcs.get(calcIndex);
            if (c.dir || c instanceof Storage.SymlinkNode && ((Storage.SymlinkNode) c).isSymDir()) {
                walk(c.uri);
            } else {
                files.add(c);
                total += c.size;
            }
            calcIndex++;
            return calcIndex < calcs.size();
        }

        public void open(final Storage.Node f, Uri to, String target) throws IOException {
            this.f = f;
            is = storage.open(f.uri);
            String s = to.getScheme();
            if (s.equals(ContentResolver.SCHEME_FILE)) {
                SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
                File k = Storage.getFile(to);
                File m = new File(k, target);
                if (shared.getBoolean(JavaUtils.PREF_ROOT, false))
                    os = new SuperUser.FileOutputStream(m);
                else
                    os = new FileOutputStream(m);
                t = Uri.fromFile(m);
            } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
                Uri doc = Storage.createFile(context, to, target); // target == path
                if (doc == null)
                    throw new IOException("no permission");
                os = resolver.openOutputStream(doc);
                t = doc;
            } else {
                throw new Storage.UnknownUri();
            }
            current = 0;
        }

        public void finish() throws IOException {
            if (is != null) {
                is.close();
                is = null;
            }
            if (os != null) {
                os.close();
                os = null;
            }
            if (t != null) {
                storage.touch(t, f.last);
                t = null;
            }
            f = null;
        }

        public void cancel() {
            if (thread != null) {
                interrupt.set(true);
                thread.interrupt();
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                thread = null;
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.e(TAG, "close error", e);
                }
                is = null;
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    Log.e(TAG, "close error", e);
                }
                os = null;
            }
            if (t != null) {
                FileUtils.delete(context, t, storage);
                t = null;
            }
            f = null;
            if (retry != null) {
                retry.dismiss();
                retry = null;
            }
        }

        public void close() {
            cancel();
            storage.closeSu();
        }

        public EnumSet<OPERATION> check(Storage.Node f, Storage.Node t) { // ask user for confirmations?
            if (t.uri.getPath().equals(f.uri.getPath()))
                return original;
            if (t.size < f.size)
                return small;
            if (t.size > f.size)
                return big;
            if (t.size == f.size && t.last > f.last)
                return newer;
            if (t.size == f.size && t.last == f.last)
                return same;
            return EnumSet.of(OPERATION.NONE); // not asking
        }

        public EnumSet<OPERATION> check(Throwable e) { // ask user for confirmations?
            Throwable p = JavaUtils.getCause(e);
            if (Build.VERSION.SDK_INT >= 21) {
                if (p instanceof ErrnoException)
                    return errno.get(((ErrnoException) p).errno);
            } else {
                try {
                    Class klass = Class.forName("libcore.io.ErrnoException");
                    Field f = klass.getDeclaredField("errno");
                    if (klass.isInstance(p))
                        return errno.get(f.getInt(p));
                } catch (Exception ignore) {
                }
            }
            return EnumSet.of(OPERATION.NONE); // unknown error, always asking
        }

        public int copy(byte[] buf) throws IOException {
            int len;
            if ((len = is.read(buf)) < 0)
                return len;
            os.write(buf, 0, len);
            return len;
        }

        @Override
        public void run() {
        }

        public void pause() {
            if (thread != null) {
                interrupt.set(true);
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                thread = null;
            }
        }

        public String formatStart() {
            if (calcsStart.size() == 1) {
                return Storage.getDisplayName(context, calcsStart.get(0).uri);
            } else {
                String str = Storage.getDisplayName(context, calcUri) + "{";
                for (Storage.Node u : calcsStart)
                    str += Storage.getName(context, u.uri) + ",";
                str = stripRight(str, ",");
                str += "}";
                return str;
            }
        }

        public String formatCalc() {
            return Storage.getDisplayName(context, files.get(files.size() - 1).uri);
        }

        public void post() {
        }

        public void retry() { // tell 'op' we are retrying
        }
    }

    public static String getFirst(String name) {
        String[] ss = Storage.splitPath(name);
        return ss[0];
    }

    public static String stripRight(String s, String right) {
        if (s.endsWith(right))
            s = s.substring(0, s.length() - right.length());
        return s;
    }

    public static class SortMove implements Comparator<Storage.Node> { // we have to move symlinks first during move operation only
        @Override
        public int compare(Storage.Node o1, Storage.Node o2) {
            Boolean d1 = o1.dir;
            Boolean d2 = o2.dir;
            int c = d2.compareTo(d1);
            if (c != 0)
                return c; // directories first (ordered by lexagraphcally)
            Boolean s1 = o1 instanceof Storage.SymlinkNode;
            Boolean s2 = o2 instanceof Storage.SymlinkNode;
            if (s1 && !s2)
                return -1;
            if (!s1 && s2)
                return 1; // symlinks first (ordered by lexagraphcally)
            return o1.name.compareTo(o2.name);
        }
    }

    public static AlertDialog pasteError(final OperationBuilder paste, final PendingOperation op, final Throwable e, final boolean move) {
        Log.e(TAG, "paste", e);
        AlertDialog.Builder builder = new AlertDialog.Builder(paste.getContext());
        builder.setCancelable(false);
        builder.setTitle("Error");
        View p = LayoutInflater.from(paste.getContext()).inflate(R.layout.layout_paste_error, null);
        View sp = p.findViewById(R.id.skip_panel);
        TextView t = (TextView) p.findViewById(R.id.text);
        t.setText(JavaUtils.toMessage(e));
        final View retry = p.findViewById(R.id.retry);
        final View s1 = p.findViewById(R.id.spacer1);
        final View del = p.findViewById(R.id.delete);
        final View s2 = p.findViewById(R.id.spacer2);
        final View ss = p.findViewById(R.id.skip);
        final View s3 = p.findViewById(R.id.spacer3);
        final View sa = p.findViewById(R.id.skipall);
        builder.setView(p);
        builder.setPositiveButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                paste.dismiss();
            }
        });
        if (!move || op.f == null) { // always hide del if "no move" operation, or file is unkown
            del.setVisibility(View.GONE);
            s2.setVisibility(View.GONE);
        }
        if (op.check(e).contains(PendingOperation.OPERATION.NONE)) { // skip alls not supported?
            if (move) {
                s3.setVisibility(View.GONE);
                sa.setVisibility(View.GONE);
            } else { // no move and no skip all -> move all remaining buttons to the dialog
                sp.setVisibility(View.GONE);
                builder.setNeutralButton(R.string.retry, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        retry.performClick();
                    }
                });
                builder.setNegativeButton(R.string.skip, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ss.performClick();
                    }
                });
            }
        }
        final AlertDialog d = builder.create();
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                op.retry();
                op.run();
                d.dismiss();
            }
        });
        ss.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                op.filesIndex++;
                op.cancel(); // close curret is / os
                op.run();
                d.dismiss();
            }
        });
        sa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EnumSet<PendingOperation.OPERATION> o = op.check(e);
                o.clear();
                o.add(PendingOperation.OPERATION.SKIP);
                op.filesIndex++;
                op.cancel();
                op.run();
                d.dismiss();
            }
        });
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!FileUtils.delete(op.context, op.f.uri, op.storage)) {
                    op.retry = pasteError(paste, op, new RuntimeException("unable to delete: " + op.f.name), move);
                    d.dismiss();
                    return;
                }
                op.filesIndex++;
                op.cancel();
                op.run();
                d.dismiss();
            }
        });
        d.show();
        return d;
    }

    public static void pasteConflict(final OperationBuilder paste, final PendingOperation op, final Storage.Node f, final Storage.Node t) {
        AlertDialog.Builder builder = new AlertDialog.Builder(paste.getContext());
        String n = paste.getContext().getString(R.string.files_conflict);
        builder.setTitle(n);
        LayoutInflater inflater = LayoutInflater.from(paste.getContext());
        View v = inflater.inflate(R.layout.layout_paste_conflict, null);
        builder.setView(v);
        builder.setCancelable(false);
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                op.close();
                paste.dismiss();
            }
        });

        final AlertDialog d = builder.create();

        TextView target = (TextView) v.findViewById(R.id.target);
        TextView source = (TextView) v.findViewById(R.id.source);

        View overwrite = v.findViewById(R.id.overwrite);
        overwrite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FileUtils.delete(op.context, t.uri, op.storage);
                op.run();
                d.dismiss();
            }
        });

        View overwriteall = v.findViewById(R.id.overwriteall);
        overwriteall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EnumSet<PendingOperation.OPERATION> o = op.check(f, t);
                o.clear();
                o.add(PendingOperation.OPERATION.OVERWRITE);
                op.run();
                d.dismiss();
            }
        });

        View skip = v.findViewById(R.id.skip);
        skip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                op.filesIndex++;
                op.cancel();
                op.run();
                d.dismiss();
            }
        });

        View skipall = v.findViewById(R.id.skipall);
        skipall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EnumSet<PendingOperation.OPERATION> o = op.check(f, t);
                o.clear();
                o.add(PendingOperation.OPERATION.SKIP);
                op.run();
                d.dismiss();
            }
        });

        target.setText(paste.getContext().getString(R.string.overwrite) + " " +
                op.storage.getDisplayName(paste.getContext(), t.uri) + "\n\t\t" + BYTES.format(t.size) + " " +
                paste.getContext().getString(R.string.size_bytes) + ", " + SIMPLE.format(t.last));
        source.setText(paste.getContext().getString(R.string.with) + " " +
                op.storage.getDisplayName(paste.getContext(), f.uri) + "\n\t\t" + BYTES.format(f.size) + " " +
                paste.getContext().getString(R.string.size_bytes) + ", " + SIMPLE.format(f.last));

        d.show();
    }

    public static final SimpleDateFormat SIMPLE = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");

    public static final NumberFormat BYTES = new DecimalFormat("###,###,###,###,###.#", new DecimalFormatSymbols() {{
        setGroupingSeparator(' ');
    }});
}

