package com.genonbeta.android.framework.util;

import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.apache.commons.io.IOUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class JavaUtils {

    public static String TAG = JavaUtils.class.getSimpleName();
    public static final String PREF_ROOT = "root";
    public static final String ROOT = "/";
    public static final int RW = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    public static FileNotFoundException fnfe(final Throwable e) {
        return (FileNotFoundException) new FileNotFoundException() {
            @Override
            public String getMessage() {
                return e.getMessage();
            }
        }.initCause(e);
    }

    public static Throwable getCause(Throwable e) { // get to the bottom
        Throwable c = null;
        while (e != null) {
            c = e;
            e = e.getCause();
        }
        return c;
    }

    public static String toMessage(Throwable e) { // eat RuntimeException's
        Throwable p = e;
        while (e instanceof RuntimeException) {
            e = e.getCause();
            if (e != null)
                p = e;
        }
        String msg = p.getMessage();
        if (msg == null || msg.isEmpty())
            msg = p.getClass().getCanonicalName();
        return msg;
    }

    public static class ParcelInputStream extends ParcelFileDescriptor {
        Thread thread;
        ParcelFileDescriptor w;

        public ParcelInputStream(ParcelFileDescriptor[] ff) {
            super(ff[0]);
            w = ff[1];
        }

        public ParcelInputStream(final InputStream is) throws IOException {
            this(ParcelFileDescriptor.createPipe());
            thread = new Thread("ParcelInputStream") {
                @Override
                public void run() {
                    OutputStream os = new ParcelFileDescriptor.AutoCloseOutputStream(w);
                    try {
                        IOUtils.copy(is, os);
                    } catch (IOException e) {
                        Log.d(TAG, "Copy error", e);
                    } finally {
                        try {
                            os.close();
                        } catch (Throwable e) {
                            Log.d(TAG, "Copy close error", e);
                        }
                        try {
                            is.close();
                        } catch (Throwable e) {
                            Log.d(TAG, "Copy close error", e);
                        }
                    }
                }
            };
            thread.start();
        }

        public ParcelInputStream() throws IOException {
            this(ParcelFileDescriptor.createPipe());
            thread = new Thread("ParcelInputStream") {
                @Override
                public void run() {
                    OutputStream os = new ParcelFileDescriptor.AutoCloseOutputStream(w);
                    try {
                        copy(os);
                    } catch (Exception e) {
                        Log.d(TAG, "Copy error", e);
                    } finally {
                        try {
                            os.close();
                        } catch (Throwable e) {
                            Log.d(TAG, "Copy close error", e);
                        }
                    }
                }
            };
            thread.start();
        }

        public void copy(OutputStream os) throws IOException {
        }

        @Override
        public long getStatSize() {
            return super.getStatSize();
        }
    }


}
