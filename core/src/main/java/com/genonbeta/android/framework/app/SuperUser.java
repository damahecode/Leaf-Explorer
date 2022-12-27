package com.genonbeta.android.framework.app;

import android.util.Log;
import androidx.annotation.NonNull;
import com.genonbeta.android.framework.util.JavaUtils;
import org.apache.commons.io.IOUtils;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;

public class SuperUser extends LeafSuperUser {

    public static String BIN_SUIO;

    public static long length(SuIO su, File f) {
        try {
            su.write("length", f);
            return Long.valueOf(su.readString());
        } catch (IOException e) {
            throw new RuntimeException(new Result(su.cmd, su.su, e).errno());
        }
    }

    public static Result delete(SuIO su, File target) {
        try {
            su.write("delete", target);
            return su.ok();
        } catch (IOException e) {
            throw new RuntimeException(new Result(su.cmd, su.su, e).errno());
        }
    }

    public static Result mkdir(SuIO su, File target) {
        try {
            su.write("mkdir", target);
            return su.ok();
        } catch (IOException e) {
            throw new RuntimeException(new Result(su.cmd, su.su, e).errno());
        }
    }

    public static Result ln(SuIO su, File target, File file) {
        try {
            su.write("ln", target, file);
            return su.ok();
        } catch (IOException e) {
            throw new RuntimeException(new Result(su.cmd, su.su, e).errno());
        }
    }

    public static Result rename(SuIO su, File f, File t) {
        try {
            su.write("rename", f, t);
            return su.ok();
        } catch (IOException e) {
            throw new RuntimeException(new Result(su.cmd, su.su, e).errno());
        }
    }

    public static Result touch(SuIO su, File target, long time) {
        try {
            su.write("touch", target, time / 1000);
            return su.ok();
        } catch (IOException e) {
            throw new RuntimeException(new Result(su.cmd, su.su, e).errno());
        }
    }

    public static ArrayList<File> lsa(SuIO su, File f) { // walk
        return ls(su, f, null);
    }

    public static ArrayList<File> ls(SuIO su, File f, FileFilter filter) {
        try {
            ArrayList<File> ff = new ArrayList<>();
            su.write("lsa", f);
            String type;
            while (!(type = su.readString()).equals("EOF")) {
                final long size = Long.valueOf(su.readString());
                final long last = Long.valueOf(su.readString());
                String name = su.readString();
                File k = new File(name);
                if (!k.equals(DOTDOT) && (filter == null || filter.accept(k))) {
                    switch (type) {
                        case "d":
                            if (k.equals(DOT))
                                k = f;
                            else
                                k = new File(f, name);
                            ff.add(new Directory(k, last));
                            break;
                        case "ld": {
                            String target = su.readString();
                            if (k.equals(DOT))
                                k = f;
                            else
                                k = new File(f, name);
                            ff.add(new SymDirLink(k, size, new File(target)));
                            break;
                        }
                        case "lf": {
                            String target = su.readString();
                            if (k.equals(DOT))
                                k = f;
                            else
                                k = new File(f, name);
                            ff.add(new SymLink(k, last, new File(target)) {
                                @Override
                                public long length() {
                                    return size;
                                }
                            });
                            break;
                        }
                        default:
                            if (k.equals(DOT))
                                k = f;
                            if (!f.equals(k)) // ls file return full path, ls dir return relative path
                                k = new File(f, name);
                            ff.add(new NativeFile(k, size, last));
                            break;
                    }
                }
            }
            return ff;
        } catch (IOException e) {
            throw new RuntimeException(new Result(su.cmd, su.su, e).errno());
        }
    }

    public static class SuIO {
        public InputStream is;
        public OutputStream os;
        public Commands cmd;
        public Process su;
        public boolean valid = true;

        public SuIO() {
            this(BIN_SU);
        }

        public SuIO(String shell) {
            try {
                cmd = new Commands(BIN_SUIO + ";" + BIN_EXIT).exit(true);
                su = Runtime.getRuntime().exec(shell);
                os = new BufferedOutputStream(su.getOutputStream());
                if (cmd.exit && !EXITCODE)
                    SuperUser.writeString(BIN_TRAP + " '" + KILL_SELF + "' ERR" + EOL, os);
                SuperUser.writeString(cmd.build(), os);
                is = new BufferedInputStream(su.getInputStream());
            } catch (IOException e) {
                if (su != null)
                    throw new RuntimeException(new Result(cmd, su, e).errno());
                else
                    throw new RuntimeException(e);
            }
        }

        public void writeString(String str) throws IOException {
            os.write(str.getBytes(Charset.defaultCharset()));
            os.write(0);
            os.flush();
        }

        public void write(Object... oo) throws IOException {
            for (Object o : oo) {
                if (o instanceof String)
                    writeString((String) o);
                else if (o instanceof Long)
                    writeString(Long.toString((Long) o));
                else if (o instanceof Integer)
                    writeString(Integer.toString((Integer) o));
                else if (o instanceof File)
                    writeString(((File) o).getPath());
                else
                    throw new IOException("unknown type");
            }
        }

        public String readString() throws IOException {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            int c;
            while ((c = is.read()) != 0) {
                if (c == -1) {
                    valid = false;
                    try {
                        su.waitFor(); // wait to read exitCode() or exception will be thrown
                    } catch (InterruptedException e) {
                        throw new IOException(e);
                    }
                    throw new EOFException();
                }
                os.write(c);
            }
            return os.toString();
        }

        public boolean ping() throws IOException {
            write("ping");
            String str = readString();
            return str.equals("pong");
        }

        public void clear() {
            try {
                skipAll(su.getInputStream());
                skipAll(su.getErrorStream());
            } catch (IOException e) {
                Log.e(TAG, "clear", e);
                valid = false;
            }
        }

        public boolean valid() {
            return valid;
        }

        public void exit() throws IOException { // no handling exit codes and stderr here
            valid = false;
            write("exit");
            try {
                su.waitFor();
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
            Result.must(su);
        }

        public void alive() throws IOException {
            try {
                su.exitValue();
                valid = false;
                throw new IOException("!alive");
            } catch (IllegalThreadStateException ignore) { // not exited
            }
        }

        public Result ok() { // input / output stream sanity checks
            try {
                String ok = readString();
                if (ok.equals("ok"))
                    return new Result(0);
                valid = false;
                return new Result(cmd, su, new Throwable("!ok: " + ok));
            } catch (IOException e) { // wrap exceptions, so caller decide raise or twist
                valid = false;
                return new Result(cmd, su, e);
            }
        }

        public void close() {
            valid = false;
            su.destroy();
        }
    }

    public static boolean exists(SuIO su, File f) {
        try {
            su.write("exists", f);
            return toBoolean(su.readString());
        } catch (IOException e) {
            throw new RuntimeException(new Result(su.cmd, su.su, e).errno());
        }
    }

    public static boolean isDirectory(SuIO su, File f) {
        try {
            su.write("isdir", f);
            return toBoolean(su.readString());
        } catch (IOException e) {
            throw new RuntimeException(new Result(su.cmd, su.su, e).errno());
        }
    }

    public static boolean toBoolean(String str) throws IOException {
        if (str.equals("true"))
            return true;
        if (str.equals("false"))
            return false;
        throw new IOException("bad input");
    }

    public static void skipAll(InputStream is) throws IOException {
        int a;
        while ((a = is.available()) > 0)
            IOUtils.skip(is, a);
    }

    public static ArrayList<File> lsA(SuIO su, File f) { // list
        return ls(su, f, new FileFilter() {
            @Override
            public boolean accept(File f) {
                return !f.equals(DOT);
            }
        });
    }

    public static long lastModified(SuIO su, File f) {
        try {
            su.write("last", f);
            return Long.valueOf(su.readString());
        } catch (IOException e) {
            throw new RuntimeException(new Result(su.cmd, su.su, e).errno());
        }
    }

    public static class RandomAccessFile extends LeafSuperUser.RandomAccessFile {
        public static final String R = "rb";
        public static final String W = "wb"; // open and truncate

        public SuIO su;

        public RandomAccessFile(File f, String mode) throws FileNotFoundException {
            try {
                su = new SuIO();
                su.write("rafopen", f, mode);
                if (mode.equals(R))
                    size = Long.valueOf(su.readString());
                else
                    su.ok().must();
            } catch (final IOException e) {
                su.valid = false;
                if (su != null)
                    throw JavaUtils.fnfe(new Result(su.cmd, su.su, e).errno());
                else
                    throw JavaUtils.fnfe(e);
            }
        }

        public RandomAccessFile(File f) throws FileNotFoundException {
            this(f, R);
        }

        public int read() throws IOException {
            try {
                int size = 1;
                long last = offset + size;
                if (last > this.size) {
                    size -= last - this.size;
                    if (size == 0)
                        return -1;
                }
                su.write("rafread", offset, size);
                int b = su.is.read();
                offset += size;
                return b;
            } catch (IOException e) {
                su.valid = false;
                throw new IOException(new Result(su.cmd, su.su, e).errno());
            }
        }

        public int read(byte[] buf, int off, int size) throws IOException {
            try {
                long last = offset + size;
                if (last > this.size) {
                    size -= last - this.size;
                    if (size == 0)
                        return -1;
                }
                su.write("rafread", offset, size);
                long len;
                int read = 0;
                while ((len = su.is.read(buf, off, size)) > 0) {
                    off += len;
                    offset += len;
                    size -= len;
                    read += len;
                }
                return read;
            } catch (IOException e) {
                su.valid = false;
                throw new IOException(new Result(su.cmd, su.su, e).errno());
            }
        }

        public void write(int b) throws IOException {
            try {
                int size = 1;
                su.write("rafwrite", offset, size);
                su.os.write(b);
                su.os.flush();
                offset += size;
                su.ok().must();
            } catch (IOException e) {
                su.valid = false;
                throw new IOException(new Result(su.cmd, su.su, e).errno());
            }
        }

        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            try {
                su.write("rafwrite", offset, len);
                su.os.write(b, off, len);
                su.os.flush();
                offset += len;
                su.ok().must();
            } catch (IOException e) {
                su.valid = false;
                throw new IOException(new Result(su.cmd, su.su, e).errno());
            }
        }

        @Override
        public void close() throws IOException {
            try {
                su.write("rafclose");
                su.ok().must();
                su.exit();
            } catch (IOException e) {
                if (su.valid) { // only raise exception if here is no errors during IO
                    su.valid = false;
                    throw new IOException(new Result(su.cmd, su.su, e).errno());
                } else {
                    Log.e(TAG, "double error on close", e);
                }
            } finally {
                su.close();
            }
        }
    }
}
