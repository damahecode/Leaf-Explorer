package com.leaf.explorer.util;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.genonbeta.android.framework.app.Storage;
import com.genonbeta.android.framework.app.SuperUser;
import com.genonbeta.android.framework.io.DocumentFile;
import com.leaf.explorer.R;
import com.leaf.explorer.config.Keyword;
import com.leaf.explorer.util.CopyPasteUtils.*;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ArchiveUtils {
    public static final String TAG = "ArchiveUtils";
    static OperationBuilder archive;

    public static void  CreateZipFile(Uri calcUri, Context context, Storage storage, ArrayList<DocumentFile> copiedItems, Uri destination) {
        ArrayList<Storage.Node> selected = CopyPasteUtils.getNode(context, storage, copiedItems);

        final String name;
        if (selected.size() == 1)
            name = selected.get(0).name;
        else
            name = "Archive";
        String to = Storage.getNextName(context, calcUri, name, "zip");
        try {
            Storage.UriOutputStream os = storage.open(destination, to);
            archive(context, os, selected, calcUri);
        } catch (Exception e) {
            Toast.makeText(context, R.string.unknown_failure, Toast.LENGTH_SHORT).show();
        }
    }

    static void archive(Context context, Storage.UriOutputStream uos, ArrayList<Storage.Node> selected, Uri uri) {
        Handler handler = new Handler();
        archive = new OperationBuilder(context);
        archive.create(R.layout.layout_paste);
        archive.setTitle(R.string.menu_archive);
        final PendingOperation op = new PendingOperation(context, uri, selected) {
            ZipOutputStream zip;

            {
                t = uos.uri;
            }

            @Override
            public void run() {
                try {
                    if (calcIndex < calcs.size()) {
                        if (!calc())
                            os = zip = new ZipOutputStream(new BufferedOutputStream(uos.os));
                        archive.title.setGravity(Gravity.NO_GRAVITY);
                        archive.title.setText(context.getString(R.string.files_calculating) + ": " + formatCalc());
                        archive.update(this);
                        archive.from.setText(context.getString(R.string.files_archiving) + ": " + formatStart());
                        archive.to.setText(context.getString(R.string.copy_to) + Storage.getDisplayName(context, t));
                        post();
                        return;
                    }
                    synchronized (lock) {
                        if (is != null) {
                            int old = filesIndex;
                            Uri oldt = t;
                            final Storage.Node f = files.get(filesIndex);
                            if (thread == null) {
                                interrupt.set(false);
                                thread = new Thread("Zip") {
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
                                                is.close();
                                                is = null;
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
                            archive.title.setGravity(Gravity.CENTER);
                            archive.title.setText(context.getString(R.string.files_archiving) + " " + Utils.formatSize(context, a) + context.getString(R.string.per_second) + ", " + e);
                            archive.update(this, old, f);
                            archive.from.setText(context.getString(R.string.copy_from) + " " + Storage.getDisplayName(context, f.uri));
                            archive.to.setText(context.getString(R.string.copy_to) + " " + Storage.getDisplayName(context, oldt));
                            return;
                        }
                    }
                    if (filesIndex < files.size()) {
                        int old = filesIndex;
                        final Storage.Node f = files.get(filesIndex);

                        if (f.dir || f instanceof Storage.SymlinkNode && ((Storage.SymlinkNode) f).isSymDir()) {
                            ZipEntry entry = new ZipEntry(f.name + "/");
                            zip.putNextEntry(entry);
                        } else {
                            ZipEntry entry = new ZipEntry(f.name);
                            zip.putNextEntry(entry);
                            is = storage.open(f.uri);
                            current = 0;
                            post();
                            return;
                        }

                        filesIndex++;
                        archive.title.setText(context.getString(R.string.files_archiving) + ": " + formatStart());
                        archive.update(this, old, f);
                        archive.from.setText(Storage.getDisplayName(context, f.uri));
                        post();
                        return;
                    }
                    Uri to = t;
                    t = null;
                    archive.dismiss();
                    Toast.makeText(context, context.getString(R.string.toast_files_archived, Storage.getName(context, to), files.size()), Toast.LENGTH_LONG).show();
                    context.sendBroadcast(new Intent(Keyword.FILE_BROWSER_REFRESH));

                } catch (IOException | RuntimeException e) {
                    if (check(e).iterator().next() == OPERATION.SKIP) {
                        Log.e(TAG, "skip", e);
                        filesIndex++;
                        cancel();
                        post();
                        return;
                    }
                    if (retry != null)
                        retry.dismiss();
                    retry = CopyPasteUtils.pasteError(archive, this, e, false);
                }
            }

            public void post(long d) {
                handler.removeCallbacks(this);
                handler.postDelayed(this, d);
            }

            @Override
            public void post() {
                post(0);
            }

            @Override
            public void cancel() {
                super.cancel();
                handler.removeCallbacks(this);
            }

            @Override
            public void retry() {
                cancel();
                if (calcIndex < calcs.size()) {
                    calcIndex = 0;
                    filesIndex = 0;
                } else {
                    filesIndex = 0;
                }
                try {
                    Storage.UriOutputStream k = storage.write(uos.uri);
                    os = zip = new ZipOutputStream(new BufferedOutputStream(k.os));
                    t = k.uri;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        archive.neutral = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final View.OnClickListener neutral = this;
                op.pause();
                handler.removeCallbacks(op);
                final Button b = archive.d.getButton(DialogInterface.BUTTON_NEUTRAL);
                b.setText(R.string.resume);
                archive.neutral = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        op.run();
                        b.setText(R.string.pause);
                        archive.neutral = neutral;
                    }
                };
            }
        };
        archive.dismiss = new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                Log.d(TAG, "onDismiss");
                op.close();
                archive.dismiss();
                archive = null;
            }
        };
        archive.show();
        op.run();
    }

}
