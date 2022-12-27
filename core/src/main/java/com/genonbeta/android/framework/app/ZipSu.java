package com.genonbeta.android.framework.app;

import net.lingala.zip4j.NativeFile;
import net.lingala.zip4j.NativeStorage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

public class ZipSu extends NativeStorage {
    SuperUser.SuIO su;
    ZipSu parent;

    public static class SuFile extends NativeFile {
        SuperUser.RandomAccessFile r;

        @Override
        public int skipBytes(int i) throws IOException {
            r.seek(r.getPosition() + i);
            return i;
        }

        @Override
        public int read() throws IOException {
            return r.read();
        }

        @Override
        public void write(int b) throws IOException {
            r.write(b);
        }

        public SuFile(File f) throws FileNotFoundException {
            r = new SuperUser.RandomAccessFile(f);
        }

        @Override
        public long length() throws IOException {
            return r.getSize();
        }

        @Override
        public void seek(long s) throws IOException {
            r.seek(s);
        }

        @Override
        public void readFully(byte[] buf) throws IOException {
            readFully(buf, 0, buf.length);
        }

        @Override
        public void readFully(byte[] buf, int off, int len) throws IOException {
            int r;
            while ((r = read(buf, off, len)) > 0) {
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
            return r.read(buf, off, len);
        }

        @Override
        public long getFilePointer() throws IOException {
            return r.getPosition();
        }

        @Override
        public void close() throws IOException {
            r.close();
        }

        @Override
        public void write(byte[] buf) throws IOException {
            write(buf, 0, buf.length);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            r.write(b, off, len);
        }
    }

    public ZipSu(SuperUser.SuIO su, File f) {
        super(f);
        this.su = su;
    }

    public ZipSu(ZipSu parent, File f) {
        super(f);
        this.su = parent.su;
        this.parent = parent;
    }

    public ZipSu(ZipSu v) {
        super(v.f);
        this.su = v.su;
        parent = v.parent;
    }

    @Override
    public SuFile read() throws FileNotFoundException {
        return new SuFile(f);
    }

    @Override
    public SuFile write() throws FileNotFoundException {
        throw new FileNotFoundException("not supported");
    }

    @Override
    public NativeStorage open(String name) {
        return new ZipSu(this, new File(f, name));
    }

    @Override
    public boolean exists() {
        return SuperUser.exists(su, f);
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
        return parent;
    }

    @Override
    public String getName() {
        return f.getName();
    }

    @Override
    public boolean isDirectory() {
        return SuperUser.isDirectory(su, f);
    }

    @Override
    public long lastModified() {
        return SuperUser.lastModified(su, f);
    }

    @Override
    public long length() {
        return SuperUser.length(su, f);
    }

    @Override
    public boolean renameTo(NativeStorage t) {
        return SuperUser.rename(su, f, ((ZipSu) t).f).ok();
    }

    @Override
    public void setLastModified(long l) {
    }

    @Override
    public void setReadOnly() {
    }

    @Override
    public boolean mkdirs() {
        return false;
    }

    @Override
    public boolean delete() {
        return SuperUser.delete(su, f).ok();
    }

    @Override
    public NativeStorage[] listFiles() {
        ArrayList<File> ff = SuperUser.lsA(su, f);
        NativeStorage[] nn = new NativeStorage[ff.size()];
        for (int i = 0; i < ff.size(); i++) {
            nn[i++] = new ZipSu(this, f);
        }
        return nn;
    }

    @Override
    public String getPath() {
        return f.getPath();
    }
}
