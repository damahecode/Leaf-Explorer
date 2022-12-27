package com.genonbeta.android.framework.app;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import de.innosystec.unrar.NativeFile;
import de.innosystec.unrar.NativeStorage;
import de.innosystec.unrar.rarfile.FileHeader;
import de.innosystec.unrar.rarfile.HostSystem;

public class RarSAF extends NativeStorage {
    Context context;
    Uri u;
    Uri parent;
    RarSAF parentFolder;

    public static String getRarFileName(FileHeader header) {
        String s = header.getFileNameW();
        if (s == null || s.isEmpty())
            s = header.getFileNameString();
        if (header.getHostOS().equals(HostSystem.win32))
            s = s.replaceAll("\\\\", "/");
        return s;
    }

    public static class File extends NativeFile {
        ParcelFileDescriptor fd;
        FileInputStream fis;
        FileOutputStream fos;
        FileChannel c;

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
        public void setPosition(long s) throws IOException {
            c.position(s);
        }

        @Override
        public long getPosition() throws IOException {
            return c.position();
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            ByteBuffer bb = ByteBuffer.wrap(buf, off, len);
            return c.read(bb);
        }

        @Override
        public int readFully(byte[] buf, int len) throws IOException {
            ByteBuffer bb = ByteBuffer.wrap(buf, 0, len);
            int i = c.read(bb);
            if (i != len)
                throw new RuntimeException("uneable read fully");
            return i;
        }

        @Override
        public int read() throws IOException {
            ByteBuffer bb = ByteBuffer.allocate(Integer.SIZE / Byte.SIZE);
            int i = c.read(bb);
            if (i != bb.limit())
                throw new RuntimeException("unable to read int");
            bb.flip();
            return bb.asIntBuffer().get();
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
    }

    public RarSAF(Context context, Uri parent, Uri u) {
        super((java.io.File) null);
        this.context = context;
        this.u = u;
        this.parent = parent;
    }

    public RarSAF(Context context, RarSAF parent, Uri u) {
        super((java.io.File) null);
        this.context = context;
        this.u = u;
        this.parentFolder = parent;
        this.parent = parentFolder.u;
    }

    public RarSAF(RarSAF v) {
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
    public NativeStorage open(String name) {
        return new RarSAF(context, this, Storage.getDocumentFile(context, parent, name).getUri());
    }

    @Override
    public boolean exists() {
        return Storage.getDocumentFile(context, u).exists();
    }

    @Override
    public NativeStorage getParent() {
        return parentFolder;
    }

    @Override
    public long length() {
        return Storage.getDocumentFile(context, u).length();
    }

    @Override
    public String getPath() {
        return u.toString();
    }
}
