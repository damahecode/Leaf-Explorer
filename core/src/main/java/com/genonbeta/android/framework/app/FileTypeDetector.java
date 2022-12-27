package com.genonbeta.android.framework.app;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class FileTypeDetector { // take a look at tika from 'apache commons'
    public static final String TAG = FileTypeDetector.class.getSimpleName();

    public static int BUF_SIZE = 1024; // optimal buffer size / minimum detection range bytes

    ArrayList<Handler> list = new ArrayList<>();

    public static String detecting(Context context, FileTypeDetector.Detector[] dd, InputStream is, OutputStream os, Uri u) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
        FileTypeDetectorXml xml = new FileTypeDetectorXml(dd);
        FileTypeDetectorZip zip = new FileTypeDetectorZip(dd);
        FileTypeDetector bin = new FileTypeDetector(dd);
        ExtDetector ext = new ExtDetector(dd);

        byte[] buf = new byte[SuperUser.BUF_SIZE];
        int len;
        while ((len = is.read(buf)) > 0) {
            if (Thread.currentThread().isInterrupted())
                throw new DownloadInterrupted();
            digest.update(buf, 0, len);
            if (os != null)
                os.write(buf, 0, len);
            xml.write(buf, 0, len);
            zip.write(buf, 0, len);
            bin.write(buf, 0, len);
        }

        if (os != null)
            os.close();
        bin.close();
        zip.close();
        xml.close();

        String s = u.getScheme();
        if (s.equals(ContentResolver.SCHEME_CONTENT) || s.equals(ContentResolver.SCHEME_FILE)) // ext detection works for local files only
            ext.detect(context, u);

        return MD5.hex(digest.digest());
    }

    public static class DownloadInterrupted extends RuntimeException {
    }

    public static class Detector {
        public boolean done;
        public boolean detected;
        public String ext;

        public Detector(String ext) {
            this.ext = ext;
        }

        public void clear() {
            done = false;
            detected = false;
        }
    }

    public static class Handler extends Detector {
        public byte[] first;
        public ByteArrayOutputStream os; // no need to close

        public Handler(String ext) {
            super(ext);
            clear();
        }

        public Handler(String ext, String str) {
            super(ext);
            first = str.getBytes(Charset.defaultCharset());
            clear();
        }

        public Handler(String ext, int[] b) {
            super(ext);
            first = new byte[b.length];
            for (int i = 0; i < b.length; i++)
                first[i] = (byte) b[i];
            clear();
        }

        public void write(byte[] buf, int off, int len) {
            if (first != null) {
                int left = first.length - os.size();
                if (len > left)
                    len = left;
                os.write(buf, off, len);
                left = first.length - os.size();
                if (left == 0) {
                    done = true;
                    detected = equals(os.toByteArray(), first);
                }
            } else {
                os.write(buf, off, len);
            }
        }

        public boolean equals(byte[] buf1, byte[] buf2) {
            int len = buf1.length;
            if (len != buf2.length)
                return false;
            for (int i = 0; i < len; i++) {
                if (buf1[i] != buf2[i])
                    return false;
            }
            return true;
        }

        public byte[] head(byte[] buf, int head) {
            byte[] b = new byte[head];
            System.arraycopy(buf, 0, b, 0, head);
            return b;
        }

        public byte[] tail(byte[] buf, int tail) {
            byte[] b = new byte[tail];
            System.arraycopy(buf, buf.length - tail, b, 0, tail);
            return b;
        }

        public void clear() {
            super.clear();
            os = new ByteArrayOutputStream();
        }
    }

    public static class ExtDetector extends FileTypeDetector {
        ArrayList<Handler> list = new ArrayList<>();

        public static class Handler extends FileTypeDetector.Handler {
            public Handler(String ext) {
                super(ext);
                clear();
            }

            public Handler(String ext, String str) {
                super(ext, str);
            }

            public Handler(String ext, int[] b) {
                super(ext, b);
            }

            public boolean detect(String e) {
                return done && detected && ext.equals(e);
            }
        }

        public ExtDetector(Detector[] dd) {
            super(dd);
            for (Detector d : dd) {
                if (d instanceof Handler) {
                    Handler h = (Handler) d;
                    h.clear();
                    list.add(h);
                }
            }
        }

        public void detect(Context context, Uri u) {
            String name = Storage.getName(context, u);
            String e = Storage.getExt(name).toLowerCase();
            for (Handler h : list) {
                if (h.detect(e)) {
                    h.detected = true;
                    h.done = true;
                } else {
                    h.detected = false;
                }
            }
        }

        public void close() {
        }
    }

    public static class FileTypeDetectorZipExtract extends FileTypeDetectorZip {
        public static class Handler extends FileTypeDetectorZip.Handler {
            public Handler(String ext) {
                super(ext);
            }

            public String extract(File f, File t) {
                return null;
            }

            public String extract(ZipInputStream zip, File t) {
                try {
                    MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
                    FileOutputStream os = new FileOutputStream(t);

                    byte[] buf = new byte[SuperUser.BUF_SIZE];
                    int len;
                    while ((len = zip.read(buf)) > 0) {
                        digest.update(buf, 0, len);
                        os.write(buf, 0, len);
                    }

                    os.close();
                    return MD5.hex(digest.digest());
                } catch (RuntimeException r) {
                    throw r;
                } catch (Exception r) {
                    throw new RuntimeException(r);
                }
            }

            public String extract(ZipEntry e, File f, File t) {
                try {
                    MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
                    ZipFile zip = new ZipFile(f);
                    InputStream is = zip.getInputStream(e);
                    FileOutputStream os = new FileOutputStream(t);

                    byte[] buf = new byte[SuperUser.BUF_SIZE];
                    int len;
                    while ((len = is.read(buf)) > 0) {
                        digest.update(buf, 0, len);
                        os.write(buf, 0, len);
                    }

                    os.close();
                    is.close();
                    return MD5.hex(digest.digest());
                } catch (RuntimeException r) {
                    throw r;
                } catch (Exception r) {
                    throw new RuntimeException(r);
                }
            }
        }

        public FileTypeDetectorZipExtract(FileTypeDetector.Detector[] dd) {
            super(dd);
        }
    }

    public static class FileTypeDetectorIO {
        ParcelFileDescriptor.AutoCloseInputStream is;
        ParcelFileDescriptor.AutoCloseOutputStream os;

        public static class Handler extends FileTypeDetector.Detector {
            public Handler(String ext) {
                super(ext);
            }
        }

        public FileTypeDetectorIO() {
            try {
                ParcelFileDescriptor[] pp = ParcelFileDescriptor.createPipe();
                is = new ParcelFileDescriptor.AutoCloseInputStream(pp[0]);
                os = new ParcelFileDescriptor.AutoCloseOutputStream(pp[1]);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void write(byte[] buf, int off, int len) {
            try {
                os.write(buf, off, len);
            } catch (IOException e) { // ignore expcetions, stream can be closed by reading thread
            }
        }

        public void close() {
            try {
                os.close();
            } catch (IOException e) {
            }
        }
    }

    public static class FileTypeDetectorZip extends FileTypeDetectorIO {
        Thread thread;
        ArrayList<Handler> list = new ArrayList<>();

        public static class Handler extends FileTypeDetectorIO.Handler {
            public Handler(String ext) {
                super(ext);
            }

            public void nextEntry(ZipEntry entry) {
            }
        }

        public FileTypeDetectorZip(FileTypeDetector.Detector[] dd) {
            super();

            for (FileTypeDetector.Detector d : dd) {
                if (d instanceof Handler) {
                    Handler h = (Handler) d;
                    h.clear();
                    list.add(h);
                }
            }

            thread = new Thread("zip detector") {
                @Override
                public void run() {
                    ZipInputStream zip = null;
                    try {
                        zip = new ZipInputStream(is); // throws MALFORMED if encoding is incorrect
                        ZipEntry entry;
                        while ((entry = zip.getNextEntry()) != null) {
                            for (Handler h : new ArrayList<>(list)) {
                                h.nextEntry(entry);
                                if (h.done)
                                    list.remove(h);
                            }
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "zip Error", e);
                    } finally {
                        try {
                            is.close();
                        } catch (IOException e) {
                        }
                        try {
                            zip.close();
                        } catch (IOException e) {
                        }
                    }
                }
            };
            thread.start();
        }

        public void close() {
            super.close();
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static class FileTypeDetectorXml extends FileTypeDetectorIO {
        ArrayList<Handler> list = new ArrayList<>();
        Thread thread;

        public static class Handler extends FileTypeDetectorIO.Handler {
            boolean first = true;
            String firstTag;

            public Handler(String ext) {
                super(ext);
            }

            public Handler(String ext, String firstTag) {
                super(ext);
                this.firstTag = firstTag;
            }

            public void startElement(String uri, String localName, String qName, Attributes attributes) {
                if (first) {
                    if (firstTag != null) {
                        done = true;
                        if (localName.equals(firstTag))
                            detected = true;
                    }
                }
                first = false;
            }

            public void startDocument() {
            }

            public void endDocument() {
            }

            @Override
            public void clear() {
                super.clear();
                first = true;
            }
        }

        public FileTypeDetectorXml(FileTypeDetector.Detector[] dd) {
            for (FileTypeDetector.Detector d : dd) {
                if (d instanceof Handler) {
                    Handler h = (Handler) d;
                    h.clear();
                    list.add(h);
                }
            }

            thread = new Thread("xml detector") {
                @Override
                public void run() {
                    try {
                        SAXParserFactory saxPF = SAXParserFactory.newInstance();
                        SAXParser saxP = saxPF.newSAXParser();
                        XMLReader xmlR = saxP.getXMLReader();
                        DefaultHandler myXMLHandler = new DefaultHandler() {
                            @Override
                            public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                                super.startElement(uri, localName, qName, attributes);
                                for (Handler h : new ArrayList<>(list)) {
                                    h.startElement(uri, localName, qName, attributes);
                                    if (h.done)
                                        list.remove(h);
                                }
                            }

                            @Override
                            public void startDocument() throws SAXException {
                                for (Handler h : new ArrayList<>(list)) {
                                    h.startDocument();
                                    if (h.done)
                                        list.remove(h);
                                }
                            }

                            @Override
                            public void endDocument() throws SAXException {
                                for (Handler h : new ArrayList<>(list)) {
                                    h.endDocument();
                                    if (h.done)
                                        list.remove(h);
                                }
                            }
                        };
                        xmlR.setContentHandler(myXMLHandler);
                        xmlR.parse(new InputSource(is));
                    } catch (Exception e) {
                    } finally {
                        try {
                            is.close();
                        } catch (IOException e) {
                        }
                    }
                }
            };
            thread.start();
        }

        public void close() {
            super.close();
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static class FilePDF extends Handler {
        public FilePDF() {
            super("pdf", "%PDF-");
        }
    }

    public static class FileDjvu extends Handler {
        public FileDjvu() {
            super("djvu", "AT&TF");
        }
    }

    public static class FileDoc extends Handler {
        public FileDoc() {
            super("doc", new int[]{0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1, 0});
        }
    }

    public static class FileRTF extends Handler {
        public FileRTF() {
            super("rtf", "{\\rtf1");
        }
    }

    public static class FileHTML extends FileTypeDetectorXml.Handler {
        public String content;

        public FileHTML() {
            super("html");
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (first) {
                if (localName.equals("html"))
                    detected = true;
            }
            if (localName.equals("meta")) {
                content = attributes.getValue("content");
                if (detected)
                    done = true;
            }
            super.startElement(uri, localName, qName, attributes);
        }

        @Override
        public void endDocument() {
            super.endDocument();
            if (detected)
                done = true;
        }
    }

    // https://stackoverflow.com/questions/898669/how-can-i-detect-if-a-file-is-binary-non-text-in-python
    public static class FileTxt extends Handler {
        public static final int F = 0; /* character never appears in text */
        public static final int T = 1; /* character appears in plain ASCII text */
        public static final int I = 2; /* character appears in ISO-8859 text */
        public static final int X = 3; /* character appears in non-ISO extended ASCII (Mac, IBM PC) */
        public static final int R = 4; // lib.ru formatting, ^T and ^U

        // https://github.com/file/file/blob/f2a6e7cb7db9b5fd86100403df6b2f830c7f22ba/src/encoding.c#L151-L228
        public static byte[] TEXT_CHARS = new byte[]
                {
                        /*                  BEL BS HT LF VT FF CR    */
                        F, F, F, F, F, F, F, T, T, T, T, T, T, T, F, F,  /* 0x0X */
                        /*                              ESC          */
                        F, F, F, F, R, R, F, F, F, F, F, T, F, F, F, F,  /* 0x1X */
                        T, T, T, T, T, T, T, T, T, T, T, T, T, T, T, T,  /* 0x2X */
                        T, T, T, T, T, T, T, T, T, T, T, T, T, T, T, T,  /* 0x3X */
                        T, T, T, T, T, T, T, T, T, T, T, T, T, T, T, T,  /* 0x4X */
                        T, T, T, T, T, T, T, T, T, T, T, T, T, T, T, T,  /* 0x5X */
                        T, T, T, T, T, T, T, T, T, T, T, T, T, T, T, T,  /* 0x6X */
                        T, T, T, T, T, T, T, T, T, T, T, T, T, T, T, F,  /* 0x7X */
                        /*            NEL                            */
                        X, X, X, X, X, T, X, X, X, X, X, X, X, X, X, X,  /* 0x8X */
                        X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X,  /* 0x9X */
                        I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I,  /* 0xaX */
                        I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I,  /* 0xbX */
                        I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I,  /* 0xcX */
                        I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I,  /* 0xdX */
                        I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I,  /* 0xeX */
                        I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I   /* 0xfX */
                };

        public int count = 0;

        public FileTxt() {
            super("txt");
        }

        public FileTxt(String ext) {
            super(ext);
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            int end = off + len;
            for (int i = off; i < end; i++) {
                int b = buf[i] & 0xFF;
                for (int k = 0; k < TEXT_CHARS.length; k++) {
                    if (TEXT_CHARS[b] == F) {
                        done = true;
                        detected = false;
                        return;
                    }
                    count++;
                }
            }
            if (count >= 1000) {
                done = true;
                detected = true;
            }
        }
    }

    public static class FileFB2 extends FileTypeDetectorXml.Handler {
        public FileFB2() {
            super("fb2", "FictionBook");
        }
    }

    public static class FileZip extends ExtDetector.Handler {
        public static final String EXT = "zip";

        public FileZip(String ext) {
            super(ext, new int[]{0x50, 0x4B, 0x03, 0x04});
        }

        public FileZip() {
            this(EXT);
        }
    }

    public static class FileRar extends ExtDetector.Handler {
        public static final String EXT = "rar";

        public FileRar(String ext) {
            super(ext, "Rar!");
        }

        public FileRar() {
            this(EXT);
        }
    }

    public static class FileTxtZip extends FileTypeDetector.FileTypeDetectorZipExtract.Handler {
        ZipEntry e;

        public FileTxtZip() {
            super("txt");
        }

        @Override
        public void nextEntry(ZipEntry entry) {
            if (Storage.getExt(entry.getName()).toLowerCase().equals("txt")) {
                e = entry;
                detected = true;
                done = true;
            }
        }

        @Override
        public String extract(File f, File t) {
            return extract(e, f, t);
        }
    }

    public static class FileRTFZip extends FileTypeDetectorZipExtract.Handler {
        ZipEntry e;

        public FileRTFZip() {
            super("rtf");
        }

        @Override
        public void nextEntry(ZipEntry entry) {
            if (Storage.getExt(entry.getName()).toLowerCase().equals("rtf")) {
                e = entry;
                detected = true;
                done = true;
            }
        }

        @Override
        public String extract(File f, File t) {
            return extract(e, f, t);
        }
    }

    public static class FileHTMLZip extends FileTypeDetectorZipExtract.Handler {
        ZipEntry e;

        public FileHTMLZip() {
            super("html");
        }

        @Override
        public void nextEntry(ZipEntry entry) {
            String ext = Storage.getExt(entry.getName()).toLowerCase();
            if (ext.equals("html")) {
                e = entry;
                detected = true;
                done = true;
            }
        }

        @Override
        public String extract(File f, File t) {
            return extract(e, f, t);
        }
    }

    public static class FileEPUB extends FileTypeDetectorZip.Handler {
        public FileEPUB() {
            super("epub");
        }

        @Override
        public void nextEntry(ZipEntry entry) {
            if (entry.getName().equals("META-INF/container.xml")) {
                detected = true;
                done = true;
            }
        }
    }

    public static class FileMobi extends FileTypeDetector.Handler { // PdbReader.cpp
        byte[] m = "BOOKMOBI".getBytes(Charset.defaultCharset());

        public FileMobi() {
            super("mobi");
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            super.write(buf, off, len);
            if (os.size() >= 68) { // 60 offset + 8 len
                done = true;
                byte[] head = head(os.toByteArray(), 68);
                byte[] id = tail(head, 8);
                detected = equals(id, m);
            }
        }
    }

    public static class FileFB2Zip extends FileTypeDetector.FileTypeDetectorZipExtract.Handler {
        ZipEntry e;

        public FileFB2Zip() {
            super("fb2");
        }

        @Override
        public void nextEntry(ZipEntry entry) {
            if (Storage.getExt(entry.getName()).toLowerCase().equals("fb2")) {
                e = entry;
                detected = true;
                done = true;
            }
        }

        @Override
        public String extract(File f, File t) {
            return extract(e, f, t);
        }
    }

    public static class FileJSON extends FileTxt {
        public FileJSON() {
            super("json");
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            super.write(buf, off, len);
            if (done && !detected)
                return;
            int end = off + len;
            for (int i = off; i < end; i++) {
                int c = buf[i];
                if (Character.isWhitespace(c))
                    continue;
                done = true;
                detected = (c == '{' || c == '['); // first symbol after spaces ends
                return;
            }
        }
    }

    public FileTypeDetector(Detector[] dd) {
        for (Detector d : dd) {
            if (d instanceof Handler) {
                Handler h = (Handler) d;
                h.clear();
                list.add(h);
            }
        }
    }

    public void write(byte[] buf, int off, int len) {
        for (Handler h : new ArrayList<>(list)) {
            h.write(buf, off, len);
            if (h.done)
                list.remove(h);
        }
    }

    public void close() {
    }
}

