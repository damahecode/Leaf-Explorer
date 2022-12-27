package com.genonbeta.android.framework.app;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import de.innosystec.unrar.NativeFile;
import de.innosystec.unrar.NativeStorage;

public class RarSu extends NativeStorage {
    SuperUser.SuIO su;
    RarSu parent;

    public static class SuFile extends NativeFile {
        SuperUser.RandomAccessFile r;

        public SuFile(File f) throws FileNotFoundException {
            r = new SuperUser.RandomAccessFile(f);
        }

        @Override
        public void setPosition(long s) throws IOException {
            r.seek(s);
        }

        @Override
        public long getPosition() throws IOException {
            return r.getPosition();
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            return r.read(buf, off, len);
        }

        @Override
        public int readFully(byte[] buf, int len) throws IOException {
            int r;
            int off = 0;
            while ((r = read(buf, off, len)) > 0) {
                off += r;
                len -= r;
            }
            if (len > 0)
                throw new IOException("bad read");
            return off;
        }

        @Override
        public int read() throws IOException {
            return r.read();
        }

        @Override
        public void close() throws IOException {
            r.close();
        }
    }

    public RarSu(SuperUser.SuIO su, File f) {
        super(f);
        this.su = su;
    }

    public RarSu(RarSu parent, File f) {
        super(f);
        this.su = parent.su;
        this.parent = parent;
    }

    public RarSu(RarSu v) {
        super(v.f);
        su = v.su;
        parent = v.parent;
    }

    @Override
    public SuFile read() throws FileNotFoundException {
        return new SuFile(f);
    }

    @Override
    public NativeStorage open(String name) {
        return new RarSu(this, new File(f, name));
    }

    @Override
    public boolean exists() {
        return SuperUser.exists(su, f);
    }

    @Override
    public NativeStorage getParent() {
        return parent;
    }

    @Override
    public long length() {
        return SuperUser.length(su, f);
    }

    @Override
    public String getPath() {
        return f.getPath();
    }
}
