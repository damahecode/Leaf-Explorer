package com.genonbeta.android.framework.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;
import com.genonbeta.android.framework.util.JavaUtils;

import net.lingala.zip4j.NativeStorage;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import de.innosystec.unrar.Archive;
import de.innosystec.unrar.exception.RarException;

public class Storage extends LeafStorage {

    public static final String TAG = Storage.class.getSimpleName();
    SuperUser.SuIO su;

    public static final String CONTENTTYPE_ZIP = "application/zip";
    public static final HashMap<Uri, ArchiveCache> ARCHIVE_CACHE = new HashMap<>();

    public Storage(Context context) {
        super(context);
    }

    public static class Nodes extends ArrayList<Node> {
        public Nodes() {
        }

        public Nodes(ArrayList<Node> nn) {
            super(nn);
        }

        public Nodes(ArrayList<Node> nn, boolean dir) {
            for (Node n : nn) {
                if (n.dir == dir)
                    add(n);
            }
        }

        public boolean contains(Uri o) {
            for (Node n : this) {
                if (n.uri.equals(o))
                    return true;
            }
            return false;
        }

        public int find(Uri u) {
            for (int i = 0; i < size(); i++) {
                Node n = get(i);
                if (n.uri.equals(u))
                    return i;
            }
            return -1;
        }

        public boolean remove(Uri o) {
            for (Node n : this) {
                if (n.uri.equals(o))
                    return remove(n);
            }
            return false;
        }
    }

    public ArrayList<Node> list(Uri uri) {
        ArchiveReader r = fromArchive(uri, true);
        if (r != null)
            return r.list();
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE) && getRoot()) {
            ArrayList<Node> files = new ArrayList<>();
            ArrayList<File> ff = SuperUser.lsA(getSu(), Storage.getFile(uri));
            for (File f : ff) {
                if (f instanceof SuperUser.SymLink)
                    files.add(new SymlinkNode((SuperUser.SymLink) f));
                else
                    files.add(new Node(f));
            }
            return files;
        }
        return list(context, uri);
    }

    public boolean delete(Uri t) { // default Storage.delete() uses 'rm -rf'
        String s = t.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = Storage.getFile(t);
            if (getRoot())
                return SuperUser.delete(getSu(), k).ok();
            else
                return k.delete();
        } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            DocumentFile f = DocumentFile.fromSingleUri(context, t);
            return f.delete();
        } else {
            throw new UnknownUri();
        }
    }

    public boolean getRoot() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        return shared.getBoolean(JavaUtils.PREF_ROOT, false);
    }

    public SuperUser.SuIO getSu() {
        if (su == null)
            su = new SuperUser.SuIO();
        if (!su.valid()) {
            closeSu();
            su = new SuperUser.SuIO();
        }
        su.clear();
        if (!su.valid()) {
            closeSu();
            su = new SuperUser.SuIO();
        }
        return su;
    }

    public Uri mkdir(Uri to, String name) {
        if (to.getScheme().equals(ContentResolver.SCHEME_FILE) && getRoot()) {
            File k = getFile(to);
            File m = new File(k, name);
            if (SuperUser.mkdir(getSu(), m).ok())
                return Uri.fromFile(m);
        }
        try {
            return mkdir(context, to, name);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new UnknownUri();
        }
    }

    public static class SymlinkNode extends Node {
        File symlink;
        boolean symdir;

        public SymlinkNode(Uri uri, String name, long last, File target, boolean symdir) {
            this.uri = uri;
            this.name = name;
            this.last = last;
            this.symlink = target;
            this.symdir = symdir;
        }

        public SymlinkNode(SuperUser.SymLink f) {
            super(f);
            symlink = f.getTarget();
            symdir = f instanceof SuperUser.SymDirLink;
        }

        public boolean isSymDir() {
            return symdir;
        }

        public File getTarget() {
            return symlink;
        }

        @Override
        public String toString() {
            if (symdir)
                return name + " -> " + (symlink.getPath().endsWith(JavaUtils.ROOT) ? symlink.getPath() : symlink.getPath() + JavaUtils.ROOT);
            else
                return name + " -> " + symlink.getPath();
        }
    }

    public boolean symlink(SymlinkNode f, Uri uri) {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            if (!getRoot())
                return false; // users not allowed to create symlinks
            File k = getFile(uri);
            File m = new File(k, f.name);
            return SuperUser.ln(getSu(), f.getTarget(), m).ok();
        } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            return false;
        } else {
            throw new UnknownUri();
        }
    }

    public boolean mv(Uri f, Uri tp, String tn) {
        String s = tp.getScheme(); // target 's'
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            s = f.getScheme(); // source 's'
            if (s.equals(ContentResolver.SCHEME_FILE)) {
                File k = Storage.getFile(tp);
                File mf = Storage.getFile(f);
                File mt = new File(k, tn);
                if (getRoot()) {
                    if (SuperUser.rename(getSu(), mf, mt).ok())
                        return true;
                } else {
                    if (mf.renameTo(mt))
                        return true;
                }
            }
        } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            s = f.getScheme(); // source 's'
            if (s.equals(ContentResolver.SCHEME_CONTENT) && tp.getAuthority().startsWith(SAF) && f.getAuthority().startsWith(SAF)) {
                try {
                    if (Build.VERSION.SDK_INT >= 24 && DocumentsContract.moveDocument(resolver, f, Storage.getDocumentParent(context, f), tp) != null) // moveDocument api24+
                        return true;
                } catch (RuntimeException | FileNotFoundException e) { // IllegalStateException: "Failed to move"
                }
            }
        } else {
            throw new Storage.UnknownUri();
        }
        return false;
    }

    public ArrayList<Node> walk(Uri root, Uri uri) {
        ArchiveReader a = fromArchive(uri, true);
        if (a != null)
            return a.walk(root);
        if (uri.getScheme().equals(ContentResolver.SCHEME_FILE) && getRoot()) {
            File r = Storage.getFile(root);
            ArrayList<Node> files = new ArrayList<>();
            ArrayList<File> ff = SuperUser.lsa(getSu(), Storage.getFile(uri));
            for (File f : ff) {
                if (f instanceof SuperUser.SymLink)
                    files.add(new SymlinkNode(Uri.fromFile(f), relative(r, f).getPath(), f.lastModified(), ((SuperUser.SymLink) f).getTarget(), f instanceof SuperUser.SymDirLink));
                else
                    files.add(new Node(Uri.fromFile(f), relative(r, f).getPath(), f.isDirectory(), f.length(), f.lastModified()));
            }
            return files;
        }
        return walk(context, root, uri);
    }

    public InputStream open(Uri uri) throws IOException {
        ArchiveReader r = fromArchive(uri, false);
        if (r != null)
            return r.open();
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = getFile(uri);
            if (getRoot())
                return new SuperUser.FileInputStream(k);
            else
                return new FileInputStream(k);
        } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            return resolver.openInputStream(uri);
        } else {
            throw new UnknownUri();
        }
    }

    public boolean touch(Uri uri, long last) {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = Storage.getFile(uri);
            if (getRoot())
                return SuperUser.touch(getSu(), k, last).ok();
            else
                return k.setLastModified(last); // not working for most devices, requiring root
        } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            return false; // not supported operation for SAF
        } else {
            throw new Storage.UnknownUri();
        }
    }

    public void closeSu() {
        try {
            if (su != null) {
                su.exit();
                su.close();
                su = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "close", e);
            su.close();
            su = null;
        }
    }

    public static class ArchiveNode extends Node {
        public String getPath() {
            return null;
        }

        public InputStream open() {
            return null;
        }
    }

    public class ArchiveCache {
        public Uri uri; // archive uri
        public ArrayList<ArchiveNode> all;

        public void close() {
        }
    }

    public class ArchiveReader extends ArchiveCache {
        public String path; // inner path

        public ArchiveReader(Uri u, String p) {
            uri = u;
            if (p == null)
                p = "";
            path = p;
        }

        public ArchiveReader(ArchiveCache c, ArchiveReader r) {
            uri = c.uri;
            all = c.all;
            path = r.path;
        }

        public boolean isDirectory() {
            if (all == null)
                read();
            ArchiveNode n = find();
            if (n != null)
                return n.dir;
            return true;
        }

        public InputStream open() {
            if (all == null)
                read();
            final ArchiveNode n = find();
            if (n == null)
                return null;
            return n.open();
        }

        public ArchiveNode find() {
            for (ArchiveNode n : all) {
                if (n.getPath().equals(path))
                    return n;
            }
            return null;
        }

        public long length() {
            if (all == null)
                read();
            ArchiveNode n = find();
            if (n == null)
                return -1;
            return n.size;
        }

        public void read() {
        }

        public ArrayList<Node> list() {
            if (all == null)
                read();
            ArrayList<Node> nn = new ArrayList<>();
            for (ArchiveNode n : all) {
                String p = n.getPath();
                String r = relative(path, p);
                if (r != null && !r.isEmpty() && splitPath(r).length == 1)
                    nn.add(n);
            }
            return nn;
        }

        public ArrayList<Node> walk(Uri root) {
            if (all == null)
                read();
            ArchiveReader a = fromArchive(root, true);
            ArrayList<Node> nn = new ArrayList<>();
            for (ArchiveNode n : all) {
                String p = n.getPath();
                String r = relative(path, p);
                if (r != null && splitPath(r).length == 1) {
                    Node k = new Node();
                    k.size = n.size;
                    k.name = relative(a.path, p);
                    k.dir = n.dir;
                    k.last = n.last;
                    k.uri = n.uri;
                    nn.add(k);
                }
            }
            return nn;
        }
    }

    public ArchiveReader fromArchive(Uri uri, boolean root) { // root - open archive root 'file.zip/' or null
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            final File k = getFile(uri);
            byte[] buf = new byte[FileTypeDetector.BUF_SIZE];
            FileTypeDetector.FileRar rar = new FileTypeDetector.FileRar();
            FileTypeDetector.FileZip zip = new FileTypeDetector.FileZip();
            if (getRoot()) {
                try {
                    File p = k;
                    while (p != null && !SuperUser.exists(getSu(), p))
                        p = p.getParentFile();
                    if (p == null || SuperUser.isDirectory(getSu(), p))
                        return null;
                    String rel = relative(p.getPath(), k.getPath());
                    if (root || !rel.isEmpty()) {
                        InputStream is = new SuperUser.FileInputStream(p);
                        int len = is.read(buf);
                        if (len > 0) {
                            rar.write(buf, 0, len);
                            zip.write(buf, 0, len);
                        }
                        is.close();
                        if (rar.done && rar.detected)
                            return cache(new RarReader(Uri.fromFile(p), rel));
                        if (zip.done && zip.detected)
                            return cache(new ZipReader(Uri.fromFile(p), rel));
                    }
                } catch (IOException e) {
                    return null;
                }
            } else {
                try {
                    File p = k;
                    while (p != null && !p.exists())
                        p = p.getParentFile();
                    if (p == null || p.isDirectory())
                        return null;
                    String rel = relative(p.getPath(), k.getPath());
                    if (root || !rel.isEmpty()) {
                        FileInputStream is = new FileInputStream(p);
                        int len = is.read(buf);
                        if (len > 0) {
                            rar.write(buf, 0, len);
                            zip.write(buf, 0, len);
                        }
                        is.close();
                        if (rar.done && rar.detected)
                            return cache(new RarReader(Uri.fromFile(p), rel));
                        if (zip.done && zip.detected)
                            return cache(new ZipReader(Uri.fromFile(p), rel));
                    }
                } catch (IOException e) {
                    return null;
                }
            }
        } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            if (!DocumentsContract.isDocumentUri(context, uri))
                return null;
            Uri u = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getDocumentId(uri));
            DocumentFile f = DocumentFile.fromSingleUri(context, u);
            if (!f.exists() || f.isDirectory())
                return null;
            String t = f.getType();
            String rel = uri.getQueryParameter("p");
            if (t.equals(CONTENTTYPE_XRAR) || t.equals(CONTENTTYPE_RAR))
                return cache(new RarReader(u, rel));
            if (t.equals(CONTENTTYPE_ZIP))
                return cache(new ZipReader(u, rel));
        }
        return null;
    }

    public static class ZipNode extends ArchiveNode {
        public ZipFile zip;
        public FileHeader h;

        @Override
        public String getPath() {
            String s = h.getFileName();
            if (s.startsWith(JavaUtils.ROOT))
                s = s.substring(1);
            if (s.endsWith(JavaUtils.ROOT))
                s = s.substring(0, s.length() - 1);
            return s;
        }

        @Override
        public InputStream open() {
            try {
                return new ZipSAF.ZipInputStreamSafe(zip.getInputStream(h));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public ArchiveReader cache(ArchiveReader r) {
        ArchiveCache c = ARCHIVE_CACHE.get(r.uri);
        if (c != null)
            return new ArchiveReader(c, r);
        return r;
    }

    public class ZipReader extends ArchiveReader {
        ZipFile zip;

        public ZipReader(Uri u, String p) {
            super(u, p);
        }

        public void read() {
            try {
                String s = uri.getScheme();
                if (s.equals(ContentResolver.SCHEME_FILE)) {
                    if (getRoot()) {
                        File f = Storage.getFile(uri);
                        zip = new ZipFile(new ZipSu(getSu(), f));
                    } else {
                        File f = Storage.getFile(uri);
                        zip = new ZipFile(new NativeStorage(f));
                    }
                } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                    zip = new ZipFile(new ZipSAF(context, Storage.getDocumentTreeUri(uri), uri));
                } else {
                    throw new UnknownUri();
                }
                ArrayList<ArchiveNode> aa = new ArrayList<>();
                List list = zip.getFileHeaders();
                for (Object o : list) {
                    final FileHeader h = (FileHeader) o;
                    ZipNode n = new ZipNode();
                    n.h = h;
                    if (s.equals(ContentResolver.SCHEME_FILE))
                        n.uri = uri.buildUpon().appendPath(n.getPath()).build();
                    else if (s.equals(ContentResolver.SCHEME_CONTENT))
                        n.uri = uri.buildUpon().appendQueryParameter("p", n.getPath()).build();
                    else
                        throw new UnknownUri();
                    n.name = getLast(n.getPath());
                    n.size = h.getUncompressedSize();
                    n.last = h.getLastModifiedTime();
                    n.dir = h.isDirectory();
                    n.zip = zip;
                    aa.add(n);
                }
                ARCHIVE_CACHE.put(uri, this);
                all = aa;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void close() {
            super.close();
        }
    }

    public static String getLast(String name) {
        String[] ss = splitPath(name);
        return ss[ss.length - 1];
    }

    public class RarReader extends ArchiveReader {
        Archive rar;

        public RarReader(Uri u, String p) {
            super(u, p);
        }

        public void read() {
            try {
                String s = uri.getScheme();
                if (s.equals(ContentResolver.SCHEME_FILE)) {
                    if (getRoot()) {
                        File f = Storage.getFile(uri);
                        rar = new Archive(new RarSu(getSu(), f));
                    } else {
                        File f = Storage.getFile(uri);
                        rar = new Archive(new de.innosystec.unrar.NativeStorage(f));
                    }
                } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                    rar = new Archive(new RarSAF(context, Storage.getDocumentTreeUri(uri), uri));
                } else {
                    throw new UnknownUri();
                }
                ArrayList<ArchiveNode> aa = new ArrayList<>();
                List list = rar.getFileHeaders();
                for (Object o : list) {
                    final de.innosystec.unrar.rarfile.FileHeader h = (de.innosystec.unrar.rarfile.FileHeader) o;
                    RarNode n = new RarNode();
                    n.h = h;
                    if (s.equals(ContentResolver.SCHEME_FILE)) {
                        n.uri = uri.buildUpon().appendPath(n.getPath()).build();
                    } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                        n.uri = uri.buildUpon().appendQueryParameter("p", n.getPath()).build();
                    } else {
                        throw new UnknownUri();
                    }
                    n.name = getLast(n.getPath());
                    n.size = h.getFullUnpackSize();
                    n.last = h.getMTime().getTime();
                    n.dir = h.isDirectory();
                    n.rar = rar;
                    aa.add(n);
                }
                ARCHIVE_CACHE.put(uri, this);
                all = aa;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void close() {
            try {
                if (rar != null) {
                    rar.close();
                    rar = null;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class RarNode extends ArchiveNode {
        public Archive rar;
        public de.innosystec.unrar.rarfile.FileHeader h;

        @Override
        public String getPath() {
            String s = RarSAF.getRarFileName(h);
            if (s == null || s.isEmpty())
                s = h.getFileNameString();
            if (s.startsWith(JavaUtils.ROOT))
                s = s.substring(1);
            if (s.endsWith(JavaUtils.ROOT))
                s = s.substring(0, s.length() - 1);
            return s;
        }

        @Override
        public InputStream open() {
            try {
                return new ParcelFileDescriptor.AutoCloseInputStream(new JavaUtils.ParcelInputStream() {
                    @Override
                    public void copy(OutputStream os) throws IOException {
                        try {
                            rar.extractFile(h, os);
                        } catch (RarException e) {
                            throw new IOException(e);
                        }
                    }

                    @Override
                    public long getStatSize() {
                        return h.getFullUnpackSize();
                    }

                    @Override
                    public void close() throws IOException {
                        super.close();
                    }
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public void close() {
            try {
                rar.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class UriOutputStream {
        public Uri uri;
        public OutputStream os;

        public UriOutputStream(Uri u, OutputStream os) {
            this.uri = u;
            this.os = os;
        }

        public UriOutputStream(File f, OutputStream os) {
            this.uri = Uri.fromFile(f);
            this.os = os;
        }
    }

    public UriOutputStream write(Uri uri) throws IOException {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = getFile(uri);
            if (getRoot())
                return new UriOutputStream(k, new SuperUser.FileOutputStream(k));
            else
                return new UriOutputStream(k, new FileOutputStream(k));
        } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            return new UriOutputStream(uri, resolver.openOutputStream(uri, "rwt"));
        } else {
            throw new UnknownUri();
        }
    }

    public UriOutputStream open(Uri uri, String name) throws IOException {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = getFile(uri);
            File m = new File(k, name);
            if (getRoot())
                return new UriOutputStream(m, new SuperUser.FileOutputStream(m));
            else
                return new UriOutputStream(m, new FileOutputStream(m));
        } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            Uri doc = createDocumentFile(context, uri, name);
            return new UriOutputStream(doc, resolver.openOutputStream(doc, "rwt"));
        } else {
            throw new UnknownUri();
        }
    }
}
