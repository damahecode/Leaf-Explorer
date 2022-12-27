package com.genonbeta.android.framework.app;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import androidx.annotation.NonNull;

import com.genonbeta.android.framework.util.JavaUtils;

import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LeafSuperUser {
    public static String TAG = SuperUser.class.getSimpleName();

    public static int BUF_SIZE = 4 * 1024; // IOUtils#DEFAULT_BUFFER_SIZE

    public static final SimpleDateFormat TOUCHDATE = new SimpleDateFormat("yyyyMMddHHmm.ss", Locale.US);
    public static final SimpleDateFormat LSDATE = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

    public static final String SYSTEM = "/system";
    public static final String ETC = "/etc";
    public static final String USR = "/usr";
    public static final String XBIN = "/xbin";
    public static final String SBIN = "/sbin";
    public static final String BIN = "/bin";

    public static String[] WHICH_USER = new String[]{SYSTEM + SBIN, SYSTEM + BIN,
            SYSTEM + USR + SBIN, SYSTEM + USR + BIN,
            USR + SBIN, USR + BIN,
            SBIN, BIN};
    public static String[] WHICH_XBIN = new String[]{SYSTEM + XBIN};
    public static String[] WHICH = concat(WHICH_XBIN, WHICH_USER);

    public static final String BIN_SH = which("sh");
    public static final String BIN_SU = which("su");
    public static final String BIN_TRUE = which("true");
    public static final String BIN_REBOOT = which("reboot");
    public static final String BIN_MOUNT = which("mount");
    public static final String BIN_CAT = which("cat");
    public static final String BIN_TOUCH = which("touch");
    public static final String BIN_RM = which("rm");
    public static final String BIN_MKDIR = which("mkdir");
    public static final String BIN_CHMOD = which("chmod");
    public static final String BIN_CHOWN = which("chown");
    public static final String BIN_MV = which("mv");
    public static final String BIN_CP = which("cp");
    public static final String BIN_KILL = which("kill");
    public static final String BIN_AM = which("am");
    public static final String BIN_EXIT = "exit"; // build-in
    public static final String BIN_SET = "set"; // build-in
    public static final String BIN_TRAP = "trap"; //build-in
    public static final String BIN_FALSE = which("false");
    public static final String BIN_READLINK = which("readlink");
    public static final String BIN_LN = which("ln");
    public static final String BIN_LS = which("ls");
    public static final String BIN_STAT = which("stat");

    public static final String SETE = BIN_SET + " -e";
    public static final String CAT_TO = BIN_CAT + " << 'EOF' > {0}\n{1}\nEOF";
    public static final String REMOUNT_SYSTEM = BIN_MOUNT + " -o remount,rw " + SYSTEM;
    public static final String MKDIRS = BIN_MKDIR + " -p {0}";
    public static final String TOUCH = BIN_TOUCH + " -a {0}"; // a = change access time
    public static final String DELETE = BIN_RM + " -rf {0}";
    public static final String MV = BIN_MV + " {0} {1} || " + BIN_CP + " {0} {1} && " + BIN_RM + " {0}";
    public static final String RENAME = BIN_MV + " {0} {1}";
    public static final String MKDIR = BIN_MKDIR + " {0}";
    public static final String READLINK = BIN_READLINK + " {0}";
    public static final String LNS = BIN_LN + " -s {0} {1}";
    public static final String TOUCHMCT = BIN_TOUCH + " -mct {0} {1}"; // m = modification time, c = do not create file, t = set date/time
    public static final String STATLCS = BIN_STAT + " -Lc%s {0}"; // L = follow symlinks, c = custom format, %s = file size
    public static final String STATLCY = BIN_STAT + "-Lc%y {0}"; // y = last modifed
    public static final String LSA = BIN_LS + " -AlH {0}"; // -A = all entries except "." and ".." -l = long format -H = follow symlinks
    public static final String LSa = BIN_LS + " -alH {0}"; // -a = all including starting with "." -l = long format -H = follow symlinks

    public static final String KILL_SELF = BIN_KILL + " -9 $$";
    public static final String SU1 = " || " + KILL_SELF; // some su does not return error codes for pipe scripts, kill it from inside pipe if script fails

    public static final String EOL = "\n";

    public static final File DOT = new File(".");
    public static final File DOTDOT = new File("..");

    public static boolean EXITCODE = false; // does su support for exit code for pipe scripts? run exitTest()
    public static boolean TRAPERR = false; // does sh support for trap ERR for scripts? run trapTest()

    public static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public FileDescriptor dup(FileDescriptor fd) {
        try {
            return Os.dup(fd);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void close(FileDescriptor fd) {
        try {
            Os.close(fd);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Exception errno(String func, int errno) {
        return new ErrnoException(func, errno);
    }

    public static String errnoName(int errno) {
        return OsConstants.errnoName(errno);
    }

    public static String strerror(int errno) {
        try {
            Class Libcore = Class.forName("libcore.io.Libcore");
            Object os = Libcore.getDeclaredField("os").get(null);
            Class ForwardingOs = Class.forName("libcore.io.ForwardingOs");
            Method m = ForwardingOs.getDeclaredMethod("strerror", int.class);
            return (String) m.invoke(os, errno);
        } catch (Exception ignore) {
            return "errno: " + errno;
        }
    }

    public static String find(String... args) {
        for (String s : args) {
            File f = new File(s);
            if (f.exists())
                return s;
        }
        return null;
    }

    public static String escape(File p) {
        return escape(p.getPath());
    }

    public static String escape(String p) { // https://unix.stackexchange.com/questions/347332
        if (p.startsWith("-"))
            p += "./";
        p = p.replaceAll("\\\\", "\\\\\\\\"); // has go first
        p = p.replaceAll("\\$", "\\\\\\$");
        p = p.replaceAll("\\*", "\\\\*");
        p = p.replaceAll("<", "\\\\<");
        p = p.replaceAll(">", "\\\\>");
        p = p.replaceAll("=", "\\\\=");
        p = p.replaceAll("\\[", "\\\\[");
        p = p.replaceAll("\\]", "\\\\]");
        p = p.replaceAll("\\{", "\\\\{");
        p = p.replaceAll("\\}", "\\\\}");
        p = p.replaceAll("\\|", "\\\\|");
        p = p.replaceAll("~", "\\\\~");
        p = p.replaceAll("`", "\\\\`");
        p = p.replaceAll(";", "\\\\;");
        p = p.replaceAll("&", "\\\\&");
        p = p.replaceAll("#", "\\\\#");
        p = p.replaceAll("\\)", "\\\\)");
        p = p.replaceAll("\\(", "\\\\(");
        p = p.replaceAll(" ", "\\\\ ");
        p = p.replaceAll("'", "\\\\'");
        p = p.replaceAll("\"", "\\\\\"");
        return p;
    }

    public static void writeString(String str, OutputStream os) throws IOException {
        os.write(str.getBytes(Charset.defaultCharset()));
        os.flush();
    }

    public static Result su(String pattern, Object... args) {
        return su(MessageFormat.format(pattern, args));
    }

    public static Result su(String cmd) {
        return su(new Commands(cmd).exit(true));
    }

    public static Result su(Commands cmd) {
        return exec(BIN_SU, cmd);
    }

    public static Result exec(String sh, Commands cmd) {
        Process su = null;
        try {
            su = Runtime.getRuntime().exec(sh);
            if (cmd.stderr != null && !cmd.stderr)
                su.getErrorStream().close();
            OutputStream os = su.getOutputStream();
            if (cmd.sete)
                writeString(SETE + EOL, os);
            if (cmd.exit && !EXITCODE && TRAPERR) // without 'trap' scrips with or without (set -e) always exit with '0'
                writeString(BIN_TRAP + " '" + KILL_SELF + "' ERR" + EOL, os);
            writeString(cmd.build(), os);
            writeString(BIN_EXIT + EOL, os);
            su.waitFor();
            return new Result(cmd, su);
        } catch (IOException e) {
            return new Result(cmd, su, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(cmd, su, e);
        }
    }

    public static Result reboot() {
        return su(BIN_REBOOT);
    }

    public static boolean isRooted() {
        return new File(BIN_SU).exists();
    }

    public static Result rootTest() {
        return su(BIN_TRUE);
    }

    public static Result startService(Intent intent) {
        return startService(intent.getComponent());
    }

    public static Result startService(ComponentName name) {
        return su(BIN_AM + " startservice -n " + name.flattenToShortString());
    }

    public static Result stopService(Intent intent) {
        return stopService(intent.getComponent());
    }

    public static Result stopService(ComponentName name) {
        return su(BIN_AM + " stopservice -n " + name.flattenToShortString());
    }

    public static boolean isReboot() {
        File f2 = new File(BIN_REBOOT);
        return isRooted() && f2.exists();
    }

    public static Result touch(File f) {
        return su(TOUCH, escape(f));
    }

    public static Result touch(File f, long last) {
        return su(TOUCHMCT, TOUCHDATE.format(last), escape(f));
    }

    public static Result mkdirs(File f) {
        return su(MKDIRS, escape(f));
    }

    public static Result delete(File f) {
        return su(DELETE, escape(f));
    }

    public static Result mv(File f, File to) {
        return su(MV, escape(f), escape(to));
    }

    public static boolean exitTest() {
        return EXITCODE = !su(new Commands(BIN_FALSE)).ok(); // && su(new Commands(BIN_TRUE)).ok();
    }

    public static boolean trapTest() {
        try {
            Process sh = Runtime.getRuntime().exec(new String[]{BIN_SH, "-c", BIN_TRAP + " '" + BIN_TRUE + "' ERR"});
            return TRAPERR = sh.waitFor() == 0;
        } catch (IOException e) {
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return TRAPERR = false;
    }

    public static Result rename(File f, File t) {
        return su(RENAME, escape(f), escape(t));
    }

    public static Result mkdir(File f) {
        return su(MKDIR, escape(f));
    }

    public static long length(File f) {
        Result r = su(new Commands(MessageFormat.format(STATLCS, escape(f))).stdout(true).exit(true)).must();
        return Long.valueOf(r.stdout.trim());
    }

    public static Result readlink(File f) {
        return su(READLINK, escape(f));
    }

    public static Result ln(File target, File file) {
        return su(LNS, escape(target), escape(file));
    }

    public static boolean isDirectory(File f) {
        return su("[ -d {0} ]", escape(f)).ok();
    }

    public static ArrayList<File> isDirectory(ArrayList<File> ff) {
        Commands cmd = new Commands();
        for (File f : ff)
            cmd.add("[ -d " + escape(f) + " ] && echo " + escape(f));
        Result r = su(cmd.stdout(true));
        ArrayList<File> a = new ArrayList<>();
        Scanner s = new Scanner(r.stdout);
        while (s.hasNextLine())
            a.add(new File(s.nextLine()));
        s.close();
        return a;
    }

    public static boolean exists(File f) {
        return su("[ -e {0} ]", escape(f)).ok();
    }

    public static long lastModified(File f) {
        Result r = su(new Commands(MessageFormat.format(STATLCY, escape(f))).stdout(true).exit(true)).must();
        try {
            return TOUCHDATE.parse(r.stdout.trim()).getTime();
        } catch (ParseException e) {
            return 0;
        }
    }

    public static String which(String cmd) {
        return which(WHICH, cmd);
    }

    public static String which(String[] ss, String cmd) {
        for (String s : ss) {
            String f = find(s + "/" + cmd);
            if (f != null)
                return f;
        }
        return cmd;
    }

    public static ArrayList<File> lsA(File f) { // list
        return ls(LSA, f, new FileFilter() {
            @Override
            public boolean accept(File f) {
                return !f.equals(DOT);
            }
        });
    }

    public static ArrayList<File> lsa(File f) { // walk
        return ls(LSa, f, null);
    }

    public static ArrayList<File> ls(String opt, File f, FileFilter filter) {
        ArrayList<File> ff = new ArrayList<>();
        Commands cmd = new Commands(MessageFormat.format(opt, escape(f))).stdout(true).exit(true);
        OutputStream os = null;
        InputStream is = null;
        Process su = null;
        try {
            su = Runtime.getRuntime().exec(BIN_SU);
            os = new BufferedOutputStream(su.getOutputStream());
            writeString(cmd.build(), os);
            writeString(BIN_EXIT + EOL, os);
            is = new BufferedInputStream(su.getInputStream());
            Scanner scanner = new Scanner(is);
            Pattern p = Pattern.compile("^([^\\s]+)\\s+([^\\s]+)\\s+([^\\s]+)\\s+([^\\s]+)\\s+([^\\s]+)\\s+([^\\s]+\\s+[^\\s]+)\\s(.*?)$");
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                Matcher m = p.matcher(line);
                if (m.matches()) {
                    String perms = m.group(1);
                    long size = 0;
                    try {
                        size = Long.valueOf(m.group(5));
                    } catch (NumberFormatException ignore) {
                    }
                    long last = 0;
                    try {
                        last = LSDATE.parse(m.group(6)).getTime();
                    } catch (ParseException ignore) {
                    }
                    String name = m.group(7);
                    File k = new File(name);
                    if (!k.equals(DOTDOT) && (filter == null || filter.accept(k))) {
                        if (perms.startsWith("d")) {
                            if (k.equals(DOT))
                                k = f;
                            else
                                k = new File(f, name);
                            ff.add(new Directory(k, last));
                        } else if (perms.startsWith("l")) {
                            String[] ss = name.split("->", 2);
                            name = ss[0].trim();
                            if (k.equals(DOT))
                                k = f;
                            else
                                k = new File(f, name);
                            ff.add(new SymLink(k, size, new File(ss[1].trim())));
                        } else {
                            if (k.equals(DOT))
                                k = f;
                            if (!f.equals(k)) // ls file return full path, ls dir return relative path
                                k = new File(f, name);
                            ff.add(new NativeFile(k, size, last));
                        }
                    }
                }
            }
            scanner.close();
            su.waitFor();
            new Result(cmd, su).must();
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (su != null)
                su.destroy();
            try {
                if (is != null)
                    is.close();
            } catch (IOException e) {
                Log.d(TAG, "close exception", e);
            }
            try {
                if (os != null)
                    os.close();
            } catch (IOException e) {
                Log.d(TAG, "close exception", e);
            }
        }
        return ff;
    }

    public static class NativeFile extends File {
        long size;
        long last;

        public NativeFile(File f, long size, long last) {
            super(f.getPath());
            this.size = size;
            this.last = last;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public boolean canWrite() {
            return true;
        }

        @Override
        public long length() {
            return size;
        }

        @Override
        public long lastModified() {
            return last;
        }

        @Override
        public boolean delete() {
            return SuperUser.delete(this).ok();
        }

        @Override
        public boolean renameTo(File dest) {
            return SuperUser.rename(this, dest).ok();
        }
    }

    public static class Directory extends File {
        long last;

        public Directory(File f, long last) {
            super(f.getPath());
            this.last = last;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public boolean isDirectory() {
            return true;
        }

        @Override
        public boolean canWrite() {
            return true;
        }

        @Override
        public long length() {
            return 0;
        }

        @Override
        public long lastModified() {
            return last;
        }

        @Override
        public boolean delete() {
            return SuperUser.delete(this).ok();
        }

        @Override
        public boolean mkdir() {
            return SuperUser.mkdir(this).ok();
        }

        @Override
        public boolean mkdirs() {
            return SuperUser.mkdirs(this).ok();
        }

        @Override
        public boolean renameTo(File dest) {
            return SuperUser.rename(this, dest).ok();
        }

        @Override
        public File[] listFiles() {
            return listFiles((FileFilter) null);
        }

        @Override
        public File[] listFiles(final FilenameFilter filter) {
            return listFiles(filter == null ? null : new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return filter.accept(pathname.getParentFile(), pathname.getName());
                }
            });
        }

        @Override
        public File[] listFiles(FileFilter filter) {
            ArrayList<File> all = SuperUser.lsA(this);
            if (filter != null) {
                ArrayList<File> ff = new ArrayList<>();
                for (File f : all) {
                    if (filter.accept(f))
                        ff.add(f);
                }
                all = ff;
            }
            return all.toArray(new File[]{});
        }
    }

    public static class SymLink extends Directory {
        File target;

        public SymLink(File f, long last, File target) {
            super(f, last);
            this.target = target;
        }

        @Override
        public boolean isDirectory() {
            return false; // false, but target may be
        }

        @Override
        public boolean exists() {
            return true; // symlink exists, but target may not
        }

        public File getTarget() {
            return target;
        }

        @Override
        public String toString() { // display name
            return super.toString() + " -> " + target;
        }
    }

    public static class SymDirLink extends SymLink {
        public SymDirLink(File f, long last, File target) {
            super(f, last, target);
        }
    }

    public static class VirtualFile extends SuperUser.Directory { // has no information about attrs (size, last, exists)
        boolean exists;

        public VirtualFile(File f) {
            super(f, 0);
            exists = true;
        }

        public VirtualFile(File f, String name) {
            this(new File(f, name));
            exists = SuperUser.exists(this);
        }

        @Override
        public File getParentFile() {
            String p = getParent();
            if (p == null)
                return null;
            return new VirtualFile(new File(p));
        }

        @Override
        public boolean exists() {
            return exists;
        }
    }

    public static class Commands {
        public StringBuilder sb = new StringBuilder();
        public boolean sete = true; // handle exit codes during script execution
        public boolean stdout = false;
        public Boolean stderr = null; // null means get error only on error
        public boolean exit = false; // does exit code matters?

        public Commands() {
        }

        public Commands(String cmd) {
            add(cmd);
        }

        public Commands sete(boolean b) {
            this.sete = b;
            return this;
        }

        public Commands stdout(boolean b) {
            stdout = b;
            return this;
        }

        public Commands stderr(boolean b) {
            stderr = b;
            return this;
        }

        public Commands exit(boolean b) {
            exit = b;
            return this;
        }

        public Commands add(String cmd) {
            sb.append(cmd);
            sb.append(EOL);
            return this;
        }

        public String build() {
            return sb.toString();
        }

        public String toString() {
            return build();
        }
    }

    public static class Result {
        public int errno;
        public String stdout;
        public String stderr;
        public Throwable e;

        public static void must(Process p) throws IOException {
            if (p.exitValue() != 0)
                throw new IOException("bad exit code");
        }

        public Result(int res) {
            this.errno = res;
        }

        public Result(Commands cmd, Process p) {
            errno = p.exitValue();
            captureOutputs(cmd, p);
        }

        public Result(Commands cmd, Process p, Throwable e) {
            if (p == null) {
                this.errno = 1;
                this.e = e;
                return;
            }
            this.e = e;
            captureOutputs(cmd, p);
            this.errno = p.exitValue();
            p.destroy();
        }

        public void captureOutputs(Commands cmd, Process p) {
            if (cmd.stdout) {
                try {
                    stdout = IOUtils.toString(p.getInputStream(), Charset.defaultCharset());
                } catch (IOException e1) {
                    Log.e(TAG, "unable to get error", e1);
                }
            }
            if ((cmd.stderr != null && cmd.stderr) || (cmd.stderr == null && !ok())) {
                try {
                    stderr = IOUtils.toString(p.getErrorStream(), Charset.defaultCharset());
                } catch (IOException e) {
                    Log.e(TAG, "unable to get error", e);
                }
            }
        }

        public boolean ok() {
            return errno == 0 && e == null;
        }

        public Result must() {
            if (!ok())
                throw new RuntimeException(errno());
            return this;
        }

        public Exception errno() {
            if (stderr != null && !stderr.isEmpty())
                return SuperUser.errno(stderr, errno);
            if (e != null)
                return SuperUser.errno(JavaUtils.toMessage(e), errno);
            return SuperUser.errno("", errno);
        }
    }

    public static class FileInputStream extends InputStream {
        Process su;
        InputStream is;

        public FileInputStream(File f) {
            Commands cmd = new Commands(MessageFormat.format("cat {0}", escape(f))).exit(true);
            try {
                su = Runtime.getRuntime().exec(BIN_SU);
                OutputStream os = su.getOutputStream();
                writeString(cmd.build(), os);
                writeString(BIN_EXIT + EOL, os);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
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
            su.destroy();
        }
    }

    public static class FileOutputStream extends OutputStream {
        Process su;
        OutputStream os;

        public FileOutputStream(File f) throws IOException {
            Commands cmd = new Commands(MessageFormat.format(BIN_CAT + " > {0}", escape(f))).exit(true);
            su = Runtime.getRuntime().exec(BIN_SU);
            os = su.getOutputStream();
            writeString(cmd.build(), os);
        }

        @Override
        public void write(int b) throws IOException {
            os.write(b);
        }

        @Override
        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            os.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            super.close();
            su.destroy();
        }
    }

    public static class RandomAccessFile {
        public int bs;

        Process su;
        public InputStream is;
        public OutputStream os;
        public long size;
        public long offset;

        public RandomAccessFile() {
        }

        public RandomAccessFile(int bs) {
            this.bs = bs;
        }

        public RandomAccessFile(File f, int bs) {
            this(bs);
            Commands cmd = new Commands(MessageFormat.format(STATLCS + "; while read offset size; do dd if={0} iseek=$offset count=$size bs={1}; done", escape(f), bs)).exit(true).stderr(false);
            try {
                su = Runtime.getRuntime().exec(BIN_SU);
                if (cmd.stderr != null && !cmd.stderr)
                    su.getErrorStream().close();
                os = new BufferedOutputStream(su.getOutputStream());
                writeString(cmd.build(), os);
                is = new BufferedInputStream(su.getInputStream());
                size = Long.valueOf(readLine().trim());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public RandomAccessFile(File f) {
            this(f, 512);
        }

        public String readLine() throws IOException {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            int b;
            while ((b = is.read()) > 0) {
                if (b == 0x0a)
                    break;
                os.write(b);
            }
            return os.toString();
        }

        public int read(byte[] buf, int off, int size) throws IOException {
            long fs = offset / bs; // first sector
            long ls = (offset + size) / bs; // last sector
            int sc = (int) (ls - fs + 1); // sectors count
            long so = fs * bs; // first sector offset in bytes
            int skip = (int) (offset - so); // bytes to skip from first reading sector
            int length = sc * bs; // to read from pipe
            long eof = so + length;
            if (eof > this.size)
                length = (int) (this.size - so); // do not cross end of file
            writeString(fs + " " + sc + EOL, os);
            long len;
            while (skip > 0) {
                len = is.skip(skip);
                if (len <= 0)
                    throw new RuntimeException("unable to skip");
                skip -= len;
                length -= len;
            }
            int read = 0;
            while ((len = is.read(buf, off, size)) > 0) {
                off += len;
                offset += len;
                size -= len;
                length -= len;
                read += len;
            }
            while (length > 0) {
                len = is.skip(length);
                if (len <= 0)
                    throw new RuntimeException("unable to skip");
                length -= len;
            }
            return read;
        }

        public long getSize() {
            return size;
        }

        public void seek(long l) {
            offset = l;
        }

        public long getPosition() {
            return offset;
        }

        public void close() throws IOException {
            is.close();
            os.close();
            su.destroy();
        }
    }
}

