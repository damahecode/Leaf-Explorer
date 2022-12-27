package com.genonbeta.android.framework.app;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import net.lingala.zip4j.NativeFile;
import net.lingala.zip4j.NativeStorage;
import net.lingala.zip4j.io.inputstream.ZipInputStream;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class ZipSAF extends NativeStorage {
    Context context;
    Uri u;
    Uri parent;
    ZipSAF parentFolder;

    public static class ZipInputStreamSafe extends InputStream {
        ZipInputStream is;

        public ZipInputStreamSafe(ZipInputStream is) {
            this.is = is;
        }

        @Override
        public int read() throws IOException {
            return is.read();
        }

        @Override
        public int read(@NonNull byte[] b, int off, int len) throws IOException {
            return is.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            is.close();
        }
    }

    public static class File extends NativeFile {
        ParcelFileDescriptor fd;
        FileChannel c;
        FileInputStream fis;

        FileOutputStream fos;

        public File(Context context, Uri u, String mode) throws FileNotFoundException {
            ContentResolver resolver = context.getContentResolver();
            fd = resolver.openFileDescriptor(u, "rw");
            if (mode.equals("r")) {
                fis = new FileInputStream(fd.getFileDescriptor());
                c = fis.getChannel();
            }
            if (mode.equals("rw")) {
                fos = new FileOutputStream(fd.getFileDescriptor());
                c = fos.getChannel();
            }
        }

        @Override
        public int skipBytes(int i) throws IOException {
            seek(c.position() + i);
            return i;
        }

        @Override
        public int read() throws IOException {
            byte[] b = new byte[1];
            read(b);
            return b[0];
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public long length() throws IOException {
            return c.size();
        }

        @Override
        public void seek(long s) throws IOException {
            c.position(s);
        }

        @Override
        public void readFully(byte[] buf) throws IOException {
            readFully(buf, 0, buf.length);
        }

        @Override
        public void readFully(byte[] buf, int off, int len) throws IOException {
            int r;
            while (len > 0 && (r = read(buf, off, len)) > 0) {
                off += r;
                len -= r;
            }
            if (len > 0)
                throw new IOException("bad read");
        }

        @Override
        public int read(byte[] buf) throws IOException {
            return read(buf, 0, buf.length);
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            ByteBuffer bb = ByteBuffer.wrap(buf, off, len);
            return c.read(bb);
        }

        @Override
        public long getFilePointer() throws IOException {
            return c.position();
        }

        @Override
        public void close() throws IOException {
            if (c != null) {
                c.close();
                c = null;
            }
            if (fis != null) {
                fis.close();
                fis = null;
            }
            if (fos != null) {
                fos.close();
                fos = null;
            }
            if (fd != null) {
                fd.close();
                fd = null;
            }
        }

        @Override
        public void write(byte[] buf) throws IOException {
            write(buf, 0, buf.length);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            ByteBuffer bb = ByteBuffer.wrap(b, off, len);
            c.write(bb);
        }
    }

    public ZipSAF(Context context, Uri parent, Uri u) {
        super((java.io.File) null);
        this.context = context;
        this.u = u;
        this.parent = parent;
    }

    public ZipSAF(Context context, ZipSAF parent, Uri u) {
        super((java.io.File) null);
        this.context = context;
        this.u = u;
        this.parentFolder = parent;
        this.parent = parentFolder.u;
    }

    public ZipSAF(ZipSAF v) {
        super((java.io.File) null);
        u = Uri.parse(v.u.toString());
        context = v.context;
        parent = v.parent;
    }

    @Override
    public File read() throws FileNotFoundException {
        return new File(context, u, "r");
    }

    @Override
    public File write() throws FileNotFoundException {
        return new File(context, u, "rw");
    }

    @Override
    public NativeStorage open(String name) {
        return new ZipSAF(context, this, Storage.getDocumentFile(context, parent, name).getUri());
    }

    @Override
    public boolean exists() {
        return Storage.getDocumentFile(context, u).exists();
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    public boolean canWrite() {
        return true;
    }

    @Override
    public boolean isHidden() {
        return false;
    }

    @Override
    public NativeStorage getParent() {
        return parentFolder;
    }

    @Override
    public String getName() {
        return Storage.getDocumentName(context, u);
    }

    @Override
    public boolean isDirectory() {
        return Storage.getDocumentFile(context, u).isDirectory();
    }

    @Override
    public long lastModified() {
        return Storage.getDocumentFile(context, u).lastModified();
    }

    @Override
    public long length() {
        return Storage.getDocumentFile(context, u).length();
    }

    @Override
    public boolean renameTo(NativeStorage f) {
        String name = Storage.getDocumentName(context, ((ZipSAF) f).u);
        try {
            Uri m = DocumentsContract.renameDocument(context.getContentResolver(), u, name);
            return m != null;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new LeafStorage.UnknownUri();
        }
    }

    @Override
    public void setLastModified(long l) {
    }

    @Override
    public void setReadOnly() {
    }

    @Override
    public boolean mkdirs() {
        DocumentFile f = Storage.getDocumentFile(context, parent);
        if (f == null)
            return false;
        DocumentFile t = f.createDirectory(Storage.buildDocumentPath(context, parent, u));
        return t != null;
    }

    @Override
    public boolean delete() {
        try {
            return DocumentsContract.deleteDocument(context.getContentResolver(), u);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new LeafStorage.UnknownUri();
        }
    }

    @SuppressLint("Range")
    @Override
    public NativeStorage[] listFiles() {
        ContentResolver resolver = context.getContentResolver();
        Cursor c = resolver.query(u, null, null, null, null);
        if (c == null)
            return null;
        NativeStorage[] nn = new NativeStorage[c.getCount()];
        int i = 0;
        while (c.moveToNext()) {
            String id = c.getString(c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
            Uri f = DocumentsContract.buildChildDocumentsUriUsingTree(parent, id);
            nn[i++] = new ZipSAF(context, parentFolder, f);
        }
        c.close();
        return nn;
    }

    @Override
    public String getPath() {
        return u.toString();
    }
}