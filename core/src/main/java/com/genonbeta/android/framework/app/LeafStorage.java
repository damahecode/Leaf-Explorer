package com.genonbeta.android.framework.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.system.Os;
import android.system.StructStatVfs;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.ProgressBar;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

import com.genonbeta.android.framework.util.JavaUtils;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LeafStorage {
    private static final String TAG = LeafStorage.class.getSimpleName();

    protected static boolean permittedForce = false; // bugged phones has no PackageManager.ACTION_REQUEST_PERMISSIONS activity. allow it all.

    public static final String MANAGE_EXTERNAL_STORAGE = "android.permission.MANAGE_EXTERNAL_STORAGE"; // Manifest.permission.MANAGE_EXTERNAL_STORAGE
    public static final String ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION = "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION";

    public static final String PATH_TREE = "tree";
    public static final String PATH_DOCUMENT = "document";
    public static final String[] PERMISSIONS_RO = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    public static final String[] PERMISSIONS_RW = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    public static final String SAF = "com.android.externalstorage";

    public final static String STORAGE_PRIMARY = "primary"; // sdcard name
    public final static String STORAGE_HOME = "home"; // 'Documents' folder on internal sdcard
    public final static String STORAGE_DOWNLOADS = "downloads"; // 'Downloads' folder on internal sdcard

    public static final String CONTENTTYPE_OCTETSTREAM = "application/octet-stream";
    public static final String CONTENTTYPE_OPUS = "audio/opus";
    public static final String CONTENTTYPE_OGG = "audio/ogg";
    public static final String CONTENTTYPE_FB2 = "application/x-fictionbook";
    public static final String CONTENTTYPE_XRAR = "application/x-rar-compressed";
    public static final String CONTENTTYPE_RAR = "application/rar";

    public static final String SCHEME_PACKAGE = "package";

    public static final String COLON = ":";
    public static final String CSS = COLON + "//"; // COLON SLASH SLASH

    protected Context context;
    protected ContentResolver resolver;

    // String functions

    public static String relative(String base, String file) {
        return relative(base, file, File.separatorChar);
    }

    public static String relative(String base, String file, char s) { // home:system64 <-> home:system64/test, but not home:system
        if (file.startsWith(base)) {
            int l = base.length();
            if (l == 0) // base is ""
                return file;
            if (base.charAt(l - 1) != s && file.length() > l) { // base not ends with '/', same path or relative?
                if (file.charAt(l) != s)
                    return null; // not relative
                l++; // 'l' points to '/'
            }
            return file.substring(l); // "" or relative path
        }
        return null; // not relative
    }

    public static String[] splitPath(String s) {
        return s.split(JavaUtils.ROOT);
    }

    public static String formatNextFile(String name, int i, String ext) {
        if (i == 0) {
            if (ext == null || ext.isEmpty())
                return name;
            else
                return String.format("%s.%s", name, ext);
        } else {
            if (ext == null || ext.isEmpty())
                return String.format("%s (%d)", name, i);
            else
                return String.format("%s (%d).%s", name, i, ext);
        }
    }

    public static String wildcard(String wildcard) { // https://stackoverflow.com/questions/28734455/java-converting-file-pattern-to-regular-expression-pattern
        StringBuilder s = new StringBuilder(wildcard.length());
        s.append('^');
        for (int i = 0, is = wildcard.length(); i < is; i++) {
            char c = wildcard.charAt(i);
            switch (c) {
                case '*':
                    s.append(".*");
                    break;
                case '?':
                    s.append(".");
                    break;
                case '^': // escape character in cmd.exe
                    s.append("\\");
                    break;
                // escape special regexp-characters
                case '+':
                case '(':
                case ')':
                case '[':
                case ']':
                case '$':
                case '.':
                case '{':
                case '}':
                case '|':
                case '\\':
                    s.append("\\");
                    s.append(c);
                    break;
                default:
                    s.append(c);
                    break;
            }
        }
        s.append('$');
        return (s.toString());
    }

    public static String getNameNoExt(String name) {
        int i = name.lastIndexOf('.');
        if (i > 0)
            name = name.substring(0, i);
        return name;
    }

    public static String getExt(String name) { // FilenameUtils.getExtension(n)
        int i = name.lastIndexOf('.');
        if (i > 0)
            return name.substring(i + 1);
        return "";
    }

    public static String filterDups(String name) { // "test (1)" --> "test"
        Pattern p = Pattern.compile("(.*)\\s\\(\\d+\\)");
        Matcher m = p.matcher(name);
        if (m.matches()) {
            name = m.group(1);
            return filterDups(name);
        }
        return name;
    }

    public static String getTypeByName(String name) {
        String ext = getExt(name);
        return getTypeByExt(ext);
    }

    public static String getTypeByExt(String ext) {
        if (ext == null || ext.isEmpty())
            return CONTENTTYPE_OCTETSTREAM; // replace 'null'
        ext = ext.toLowerCase();
        switch (ext) {
            case "opus":
                return CONTENTTYPE_OPUS; // android missing
            case "ogg":
                return CONTENTTYPE_OGG; // replace 'application/ogg'
            case "fb2":
                return CONTENTTYPE_FB2;
            case "rar":
                return CONTENTTYPE_RAR;
        }
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (type == null)
            return CONTENTTYPE_OCTETSTREAM;
        return type;
    }

    // file functions

    public static File getFile(Uri u) {
        return new File(u.getPath());
    }

    public static File relative(File base, File file) { // system/test64/123 - system/test64 == 123, but not 'system/test'
        String f = file.getPath();
        String r = relative(base.getPath(), f);
        if (r == null)
            return null;
        if (f == r) // ==
            return file;
        return new File(r);
    }

    public static long getFree(File f) {
        while (!f.exists()) {
            f = f.getParentFile();
            if (f == null)
                return 0;
        }
        StatFs fsi = new StatFs(f.getPath());
        if (Build.VERSION.SDK_INT >= 18)
            return fsi.getBlockSizeLong() * fsi.getAvailableBlocksLong();
        else
            return fsi.getBlockSize() * (long) fsi.getAvailableBlocks();
    }

    public static boolean canWrite(File f) {
        if (!f.canWrite())
            return false;
        if (f.exists() && f.getFreeSpace() > 0)
            return true;
        File p = f.getParentFile();
        if (!f.exists() && !p.canWrite())
            return false;
        if (!f.exists() && p.exists() && p.getFreeSpace() > 0)
            return true;
        return false;
    }

    public static String getNameNoExt(File f) {
        return getNameNoExt(f.getName());
    }

    public static String getExt(File f) {
        String fileName = f.getName();
        return getExt(fileName);
    }

    public static File getNextFile(File f) {
        File parent = f.getParentFile();
        String fileName = f.getName();

        String extension = "";

        int i = fileName.lastIndexOf('.');
        if (i >= 0) {
            extension = fileName.substring(i + 1);
            fileName = fileName.substring(0, i);
        }

        fileName = filterDups(fileName);

        return getNextFile(parent, fileName, extension);
    }

    public static File getNextFile(File parent, String name, String ext) {
        return getNextFile(parent, name, 0, ext);
    }

    public static File getNextFile(File parent, String name, int i, String ext) {
        String fileName;
        fileName = formatNextFile(name, i, ext);

        File file = new File(parent, fileName);

        i++;
        while (file.exists()) {
            fileName = formatNextFile(name, i, ext);
            fileName = fileName.trim(); // if filename is empty
            file = new File(parent, fileName);
            i++;
        }

        return file;
    }

    public static String getNextName(File parent, String name, int i, String ext) {
        return getNextFile(parent, name, i, ext).getName();
    }

    public static boolean delete(File f) {
        return FileUtils.deleteQuietly(f);
    }

    public static boolean isSame(File f, File t) {
        try {
            return f.getCanonicalPath().equals(t.getCanonicalPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static File move(File f, File to) {
        long last = f.lastModified();
        if (f.renameTo(to)) {
            to.setLastModified(last);
            return to;
        }
        copy(f, to);
        delete(f);
        to.setLastModified(last);
        return to;
    }

    public static boolean mkdirs(File f) {
        return f.exists() || f.mkdirs() || f.exists(); // double check for multiprocess folder creation
    }

    public static File copy(File f, File to) {
        long last = f.lastModified();
        try {
            InputStream in = new BufferedInputStream(new FileInputStream(f));
            OutputStream out = new BufferedOutputStream(new FileOutputStream(to));
            IOUtils.copy(in, out);
            in.close();
            out.close();
            if (last > 0)
                to.setLastModified(last);
            return to;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean ejected(File p) { // check target 'parent RW' access if child does not exist, and 'child R' if exists
        if (!p.exists()) {
            while (!p.exists())
                p = p.getParentFile();
            if (p.canWrite())
                return false; // torrent parent folder not exist, but we have write access and can create subdirs
            else
                return true; // no write access - ejected
        }
        return !p.canRead(); // readonly check
    }

    public static void touch(File f) {
        if (f.setLastModified(System.currentTimeMillis())) // not working on android
            return;
        try {
            FileOutputStream os = new FileOutputStream(f, true);
            os.close();
        } catch (IOException e) {
            Log.e(TAG, "touch failed", e);
        }
    }

    // document methods
    //
    // tree - content://com.android.externalstorage.documents/tree/A598-18E4%3A
    //        content://com.android.externalstorage.documents/tree/A598-18E4%3A222
    //        content://com.android.externalstorage.documents/tree/A598-18E4%3A222%2Feeee
    // actions: takePersistableUriPermission, query throws UnsupportedOperationException
    //
    // document - content://com.android.externalstorage.documents/tree/A598-18E4%3A/document/A598-18E4%3A
    //            content://com.android.externalstorage.documents/tree/A598-18E4%3A/document/A598-18E4%3Aeeee
    //            content://com.android.externalstorage.documents/tree/A598-18E4%3A222/document/A598-18E4%3A222
    //            content://com.android.externalstorage.documents/tree/A598-18E4%3A222/document/A598-18E4%3A222%2Feeee
    //            content://com.android.externalstorage.documents/tree/A598-18E4%3A222%2Feeee/document/A598-18E4%3A222%2Feeee%2Fhhhh
    // actions: query returns current document info
    //
    // childrens - content://com.android.externalstorage.documents/tree/A598-18E4%3A/document/A598-18E4%3A/children
    //             content://com.android.externalstorage.documents/tree/A598-18E4%3A222/document/A598-18E4%3A222/children
    //             content://com.android.externalstorage.documents/tree/A598-18E4%3A222%2Feeee/document/A598-18E4%3A222%2Feeee/children
    // actions: query returns documents list or empty cursor

    public static String getDocumentStorage(String s) {
        if (s.equals(STORAGE_PRIMARY))
            return "[i]";
        else if (s.equals(STORAGE_HOME))
            return "[h]";
        else if (s.equals(STORAGE_DOWNLOADS))
            return "[d]";
        else
            return "[e]"; // 12f1-2211
    }

    @TargetApi(21)
    public static String getDocumentStorage(Context context, Uri uri) { // tree uri can points to 'home:'
        String id;
        if (DocumentsContract.isDocumentUri(context, uri))
            id = DocumentsContract.getDocumentId(uri);
        else
            id = DocumentsContract.getTreeDocumentId(uri);
        String[] ss = id.split(COLON, 2);
        return getDocumentStorage(ss[0]);
    }

    @TargetApi(21)
    public static DocumentFile getDocumentFile(Context context, Uri uri) {
        if (DocumentsContract.isDocumentUri(context, uri))
            return DocumentFile.fromSingleUri(context, uri);
        else
            return DocumentFile.fromTreeUri(context, uri);
    }

    @TargetApi(21)
    public static DocumentFile getDocumentFile(Context context, Uri uri, final String path) {
        if (uri.getAuthority().startsWith(SAF)) {
            Uri doc = child(context, uri, path);
            return getDocumentFile(context, doc);
        } else {
            final File f = new File(path);
            final File p = f.getParentFile();
            if (p != null) {
                DocumentFile k = getDocumentFile(context, uri, p.getPath());
                if (k == null)
                    return null;
                uri = k.getUri();
            }
            ArrayList<Node> nn = list(context, uri, new NodeFilter() {
                @Override
                public boolean accept(Node n) {
                    return n.name.equals(f.getName());
                }
            });
            if (nn.isEmpty())
                return null;
            return getDocumentFile(context, nn.get(0).uri);
        }
    }

    @TargetApi(21)
    public static String getDocumentPath(Context context, Uri uri) { // for display purpose only
        String pid = DocumentsContract.getTreeDocumentId(uri);
        String[] pss = pid.split(COLON, 2);
        if (!DocumentsContract.isDocumentUri(context, uri))
            return pss[1];
        String did = DocumentsContract.getDocumentId(uri);
        if (pid.equals(did))
            return pss[1];
        String[] dss = did.split(COLON, 2);
        if (!pss[0].equals(dss[0])) {
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(uri, dss[0] + COLON);
            String n = DocumentFile.fromSingleUri(context, doc).getName();
            if (pss[1].isEmpty())
                pss[1] = n;
            else
                pss[1] = new File(pss[1], n).getPath();
        }
        if (dss[1].isEmpty())
            return pss[1];
        else if (pss[1].isEmpty())
            return dss[1];
        return new File(pss[1], dss[1]).getPath();
    }

    @TargetApi(21)
    public static Uri getDocumentParent(Context context, Uri uri) { // either we have to store all parents for current uri, or have getDocument* methods
        String pid = DocumentsContract.getTreeDocumentId(uri);
        String[] pss = pid.split(COLON, 2);
        if (DocumentsContract.isDocumentUri(context, uri)) {
            String did = DocumentsContract.getDocumentId(uri);
            String[] dss = did.split(COLON, 2);
            if (dss.length == 1) { // no ':' == downloads/home/etc tree's
                String path = uri.getPath();
                File p = new File(path).getParentFile();
                path = p.toString();
                if (path.endsWith(PATH_DOCUMENT))
                    p = new File(path).getParentFile();
                Uri.Builder b = uri.buildUpon();
                b.path(p.toString());
                return b.build();
            } else {
                File p = new File(dss[1]);
                p = p.getParentFile();
                if (pid.equals(did))
                    return null;
                if (p == null) {
                    if (pss[0].equals(dss[0]) || dss[1].isEmpty())
                        return DocumentsContract.buildDocumentUriUsingTree(uri, pid);
                    return DocumentsContract.buildDocumentUriUsingTree(uri, dss[0] + COLON);
                }
                return DocumentsContract.buildDocumentUriUsingTree(uri, dss[0] + COLON + p.getPath());
            }
        } else {
            return null;
        }
    }

    @TargetApi(21)
    public static Uri getDocumentChild(Context context, Uri uri, String path) {
        String id;
        if (DocumentsContract.isDocumentUri(context, uri))
            id = DocumentsContract.getDocumentId(uri);
        else
            id = DocumentsContract.getTreeDocumentId(uri);
        String[] ss = id.split(COLON, 2);
        if (ss.length == 1) {
            return DocumentsContract.buildDocumentUriUsingTree(uri, path);
        } else {
            File f = new File(ss[1], path);
            return DocumentsContract.buildDocumentUriUsingTree(uri, ss[0] + COLON + f.getPath());
        }
    }

    @TargetApi(21)
    public static String getDocumentChildPath(Uri uri) { // tree documents points to id:system/f1/ child points to id:system/f1/bin/1/ == 'bin/1/'
        String id = DocumentsContract.getDocumentId(uri);
        if (id.contains(COLON)) {
            String parent = DocumentsContract.getTreeDocumentId(uri);
            String r = relative(parent, id, '/'); // folder can ends with ':' so, we try '/' first
            if (r != null)
                return r;
            return relative(parent, id, ':'); // id never ends with '/'
        } else { // downloads/home/etc
            return id;
        }
    }

    @TargetApi(21)
    public static String getDocumentDisplayName(Context context, Uri uri) {
        String saf = "sdcard";
        if (DocumentsContract.isDocumentUri(context, uri)) {
            String id = DocumentsContract.getTreeDocumentId(uri);
            String[] ss = id.split(COLON, 2); // 1D13-0F08:private
            if (ss.length > 1)
                return saf + getDocumentStorage(ss[0]) + CSS + getDocumentPath(context, uri);
            else
                return id + CSS; // uknown device path. new saf location?
        } else {
            String id = DocumentsContract.getTreeDocumentId(uri);
            String[] ss = id.split(COLON, 2); // 1D13-0F08:private
            if (ss.length > 1) // has colon
                return saf + getDocumentStorage(ss[0]) + CSS + ss[1];
            else if (!id.contains(COLON)) // isDocumentHomeUri
                return saf + getDocumentStorage(id) + CSS;
            else
                return id + CSS; // unknown device path. new saf location?
        }
    }

    @TargetApi(19)
    public static boolean isDocumentExists(Context context, Uri uri) {
        DocumentFile k = getDocumentFile(context, uri);
        return k != null && k.exists();
    }

    public static boolean isDocumentHomeUri(Uri uri) { // downloads/home/document/14
        if (Build.VERSION.SDK_INT >= 24 && DocumentsContract.isTreeUri(uri)) {
            String pid = DocumentsContract.getTreeDocumentId(uri);
            return !pid.contains(COLON);
        } else {
            return false;
        }
    }

    @TargetApi(21)
    public static long getDocumentFree(Context context, Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        Uri docTreeUri = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
        try {
            ParcelFileDescriptor pfd = resolver.openFileDescriptor(docTreeUri, "r");
            StructStatVfs stats = Os.fstatvfs(pfd.getFileDescriptor());
            return stats.f_bavail * stats.f_bsize;
        } catch (Exception e) { // IllegalArgumentException | FileNotFoundException | ErrnoException | NullPointerException (readExceptionWithFileNotFoundExceptionFromParcel)
            return 0;
        }
    }

    @TargetApi(21)
    public static Uri createDocumentFile(Context context, Uri u, String name) throws FileNotFoundException {
        if (!DocumentsContract.isDocumentUri(context, u)) // tree uri?
            u = DocumentsContract.buildDocumentUriUsingTree(u, DocumentsContract.getTreeDocumentId(u));
        ContentResolver resolver = context.getContentResolver();
        String ext = getExt(name);
        String mime = getTypeByExt(ext);
        return DocumentsContract.createDocument(resolver, u, mime, name);
    }

    @TargetApi(21)
    public static Uri createDocumentFolder(Context context, Uri u, String name) throws FileNotFoundException {
        if (!DocumentsContract.isDocumentUri(context, u)) // tree uri?
            u = DocumentsContract.buildDocumentUriUsingTree(u, DocumentsContract.getTreeDocumentId(u));
        ContentResolver resolver = context.getContentResolver();
        return DocumentsContract.createDocument(resolver, u, DocumentsContract.Document.MIME_TYPE_DIR, name);
    }

    @TargetApi(21)
    public static String buildDocumentPath(Context context, Uri end) {
        return buildDocumentPath(context, DocumentsContract.buildDocumentUriUsingTree(end, DocumentsContract.getTreeDocumentId(end)), end);
    }

    @SuppressLint("Range")
    public static String buildDocumentPath(Context context, Uri start, Uri end) {
        if (!DocumentsContract.isDocumentUri(context, end))
            return JavaUtils.ROOT;
        String eid = DocumentsContract.getDocumentId(end);
        String path = "";
        ContentResolver resolver = context.getContentResolver();
        if (!DocumentsContract.isDocumentUri(context, start))
            start = DocumentsContract.buildDocumentUriUsingTree(start, DocumentsContract.getTreeDocumentId(start));
        Cursor cursor = resolver.query(start, null, null, null, null);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    String id = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                    String name = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                    String type = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE));
                    boolean d = type.equals(DocumentsContract.Document.MIME_TYPE_DIR);
                    if (d) {
                        Uri doc = DocumentsContract.buildChildDocumentsUriUsingTree(start, id);
                        Cursor cursor2 = resolver.query(doc, null, null, null, null);
                        if (cursor2 != null) {
                            try {
                                while (cursor2.moveToNext()) {
                                    id = cursor2.getString(cursor2.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                                    name = cursor2.getString(cursor2.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                                    type = cursor2.getString(cursor2.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE));
                                    d = type.equals(DocumentsContract.Document.MIME_TYPE_DIR);
                                    if (relative(id, eid) != null) {
                                        path = new File(path, name).getPath();
                                        if (d) {
                                            Uri uri = DocumentsContract.buildDocumentUriUsingTree(doc, id);
                                            return new File(path, buildDocumentPath(context, uri, end)).getPath();
                                        }
                                    }
                                }
                            } finally {
                                cursor2.close();
                            }
                        }
                    } else {
                        return new File(name).getPath();
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return path;
    }

    @TargetApi(21)
    public static Uri getDocumentTreeUri(Uri uri) { // build document tree uri (root saf folder) from document uri
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(uri.getAuthority()).appendPath(PATH_TREE)
                .appendPath(DocumentsContract.getTreeDocumentId(uri))
                .build();
    }

    @SuppressLint("Range")
    public static String getQueryName(Context context, Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, null, null, null, null); // can throw UnsupportedOperationException
            if (cursor != null && cursor.moveToNext())
                return cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
        } catch (Exception ignore) { // UnsupportedOperationException | IllegalArgumentException
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return uri.getLastPathSegment();
    }

    @SuppressLint("Range")
    public static String getQueryDocumentId(Context context, Uri uri, String name) { // convert name into id, need for /downloads/document/14 uris
        String id = DocumentsContract.getTreeDocumentId(uri);
        Uri doc = DocumentsContract.buildChildDocumentsUriUsingTree(uri, id);
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = resolver.query(doc, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, DocumentsContract.Document.COLUMN_DISPLAY_NAME + "=?", new String[]{name}, null);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)).equals(name)) // some provaiders fails to filter
                        return cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                }
            } finally {
                cursor.close();
            }
        }
        return name;
    }

    @TargetApi(21)
    public static String getDocumentName(Context context, Uri uri) {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            String id = DocumentsContract.getDocumentId(uri);
            if (Build.VERSION.SDK_INT >= 24 && DocumentsContract.isTreeUri(uri)) {
                String pid = DocumentsContract.getTreeDocumentId(uri);
                if (pid.equals(id))
                    return JavaUtils.ROOT;
                if (!pid.contains(COLON)) // isDocumentHomeUri()
                    return getQueryName(context, uri);
            }
            String[] ss = id.split(COLON, 2);
            if (ss[1].isEmpty())  // unknown id format or root
                return getQueryName(context, uri); // DocumentFile.getName() return null for non existent files
            else
                return new File(ss[1]).getName(); // not using query when it is possible
        } else {
            return JavaUtils.ROOT;
        }
    }

    @TargetApi(19)
    public static String getContentName(Context context, Uri uri) {
        if (uri.getAuthority().startsWith(SAF)) // query crashed for DocumentsContract.isTreeUri() uris
            return getDocumentName(context, uri);
        else
            return getQueryName(context, uri);
    }

    @TargetApi(21)
    public static boolean isEjected(Context context, Uri uri, int takeFlags) { // check folder existes and childs can be read
        ContentResolver resolver = context.getContentResolver();
        try {
            resolver.takePersistableUriPermission(uri, takeFlags);
            Cursor childCursor = null;
            Cursor childCursor2 = null;
            try {
                Uri doc = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
                childCursor = resolver.query(doc, null, null, null, null); // check target folder
                if (childCursor != null && childCursor.moveToNext()) {
                    Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
                    childCursor2 = resolver.query(childrenUri, null, null, null, null); // check read directory content
                    if (childCursor2 != null)
                        return false;
                }
            } finally {
                if (childCursor != null)
                    childCursor.close();
                if (childCursor2 != null)
                    childCursor2.close();
            }
            return true;
        } catch (RuntimeException e) {
            Log.d(TAG, "open SAF failed", e);
        }
        return true;
    }

    synchronized public static Uri createFile(Context context, Uri parent, String path) throws FileNotFoundException {
        DocumentFile k = getDocumentFile(context, parent, path);
        if (k != null && k.exists())
            return k.getUri();

        String id;
        if (DocumentsContract.isDocumentUri(context, parent))
            id = DocumentsContract.getDocumentId(parent);
        else
            id = DocumentsContract.getTreeDocumentId(parent);
        Uri docUri = DocumentsContract.buildDocumentUriUsingTree(parent, id);

        File f = new File(path);
        String p = f.getParent();
        if (p != null && !p.isEmpty())
            docUri = createFolder(context, docUri, p);

        Log.d(TAG, "createFile " + path);
        String n = f.getName();
        return createDocumentFile(context, docUri, n);
    }

    synchronized public static Uri createFolder(Context context, Uri parent, String path) throws FileNotFoundException {
        DocumentFile k = getDocumentFile(context, parent, path);
        if (k != null && k.exists())
            return k.getUri();

        String id;
        if (DocumentsContract.isDocumentUri(context, parent))
            id = DocumentsContract.getDocumentId(parent);
        else
            id = DocumentsContract.getTreeDocumentId(parent);
        Uri docUri = DocumentsContract.buildDocumentUriUsingTree(parent, id);

        File p = new File(path);
        String n = p.getParent();
        if (n != null && !n.isEmpty())
            docUri = createFolder(context, docUri, n);

        Log.d(TAG, "createFolder " + path);
        return createDocumentFolder(context, docUri, p.getName());
    }

    public static Uri move(Context context, File f, Uri dir, String t) throws FileNotFoundException {
        Uri u = createDocumentFile(context, dir, t);
        if (u == null)
            throw new RuntimeException("Unable to create file " + t);
        ContentResolver resolver = context.getContentResolver();
        try {
            InputStream is = new FileInputStream(f);
            OutputStream os = resolver.openOutputStream(u);
            IOUtils.copy(is, os);
            is.close();
            os.close();
            delete(f);
            return u;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // context methods

    public static boolean permitted(Context context, String[] ss) {
        if (permittedForce)
            return true;
        if (Build.VERSION.SDK_INT < 16)
            return true;
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(context, s) != PackageManager.PERMISSION_GRANTED)
                return false;
        }
        return true;
    }

    public static boolean permitted(Activity a, String[] ss, int code) {
        if (permittedForce)
            return true;
        if (Build.VERSION.SDK_INT < 16)
            return true;
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(a, s) != PackageManager.PERMISSION_GRANTED) {
                try {
                    ActivityCompat.requestPermissions(a, ss, code); // API23
                } catch (ActivityNotFoundException e) {
                    permittedForce = true;
                    return true;
                }
                return false;
            }
        }
        return true;
    }

    public static boolean permitted(Fragment f, String[] ss, int code) {
        if (permittedForce)
            return true;
        if (Build.VERSION.SDK_INT < 16)
            return true;
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(f.getContext(), s) != PackageManager.PERMISSION_GRANTED) {
                try {
                    f.requestPermissions(ss, code); // API23
                } catch (ActivityNotFoundException e) {
                    permittedForce = true;
                    return true;
                }
                return false;
            }
        }
        return true;
    }

    public static void showPermissions(Context context) {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts(SCHEME_PACKAGE, context.getPackageName(), null);
        intent.setData(uri);
        context.startActivity(intent);
    }

    public static boolean isExternalStorageManager(Context context) { // API30
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                return (boolean) Environment.class.getMethod("isExternalStorageManager").invoke(null);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        return permitted(context, PERMISSIONS_RW);
    }

    public static void showExternalStorageManager(Context context) {
        Uri uri = Uri.parse("package:" + context.getPackageName());
        context.startActivity(new Intent(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri));
    }

//    public static boolean hasRequestedLegacyExternalStorage(Context context) { // API29
//        if (Build.VERSION.SDK_INT >= 29) {
//            try {
//                return (boolean) AssetsDexLoader.getPrivateMethod(ApplicationInfo.class, "hasRequestedLegacyExternalStorage").invoke(context.getApplicationInfo());
//            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
//                int PRIVATE_FLAG_REQUEST_LEGACY_EXTERNAL_STORAGE = 1 << 29;
//                return (context.getApplicationInfo().flags & PRIVATE_FLAG_REQUEST_LEGACY_EXTERNAL_STORAGE) != 0;
//            }
//        }
//        return false;
//    }

    public static boolean isExternalStorageLegacy(Context context) {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                return (boolean) Environment.class.getMethod("isExternalStorageLegacy").invoke(null);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                Log.w(TAG, e);
            }
        }
        return false;
    }

    // file and content twists

    public static String getName(Context context, Uri uri) {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_CONTENT)) { // all SDK_INT
            return getContentName(context, uri);
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            return getFile(uri).getName();
        } else {
            throw new UnknownUri();
        }
    }

    public static Uri getParent(Context context, Uri uri) {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File f = getFile(uri).getParentFile();
            if (f == null)
                return null;
            return Uri.fromFile(f);
        } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            if (uri.getAuthority().startsWith(SAF))
                return getDocumentParent(context, uri);
            else
                return null;
        } else {
            throw new UnknownUri();
        }
    }

    public static String getExt(Context context, Uri f) {
        String s = f.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return getExt(new File(getContentName(context, f)));
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            return getExt(getFile(f));
        } else {
            throw new UnknownUri();
        }
    }


    public static boolean exists(Context context, Uri uri) { // document query uri
        String s = uri.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return isDocumentExists(context, uri);
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File f1 = getFile(uri);
            if (!f1.canRead())
                return false;
            return f1.exists();
        } else {
            throw new UnknownUri();
        }
    }

    public static Uri child(Context context, Uri uri, String name) {
        String s = uri.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            if (isDocumentHomeUri(uri))
                name = getQueryDocumentId(context, uri, name);
            return getDocumentChild(context, uri, name);
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File f1 = new File(getFile(uri), name);
            return Uri.fromFile(f1);
        } else {
            throw new UnknownUri();
        }
    }

    public static boolean delete(Context context, Uri f) throws FileNotFoundException {
        ContentResolver resolver = context.getContentResolver();
        String s = f.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return DocumentsContract.deleteDocument(resolver, f);
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File ff = getFile(f);
            return delete(ff);
        } else {
            throw new UnknownUri();
        }
    }

    public static String getNameNoExt(Context context, Uri f) {
        String s = f.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return getNameNoExt(new File(getContentName(context, f)));
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            return getNameNoExt(getFile(f));
        } else {
            throw new UnknownUri();
        }
    }

    public static Uri rename(Context context, Uri f, String t) throws FileNotFoundException {
        ContentResolver resolver = context.getContentResolver();
        String s = f.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return DocumentsContract.renameDocument(resolver, f, t);
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File f1 = getFile(f);
            File ff = new File(f1.getParent(), t);
            if (ff.exists())
                ff = getNextFile(ff);
            f1.renameTo(ff);
            return Uri.fromFile(ff);
        } else {
            throw new UnknownUri();
        }
    }

    // parent = DocumentsContract.buildTreeDocumentUri(t.getAuthority(), DocumentsContract.getTreeDocumentId(t));
    public static Uri getNextFile(Context context, Uri parent, String name, String ext) {
        return getNextFile(context, parent, name, 0, ext);
    }

    public static Uri getNextFile(Context context, Uri parent, String name, int i, String ext) {
        String s = parent.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            String fileName = formatNextFile(name, i, ext);

            Uri uri = child(context, parent, fileName);

            i++;
            while (exists(context, uri)) {
                fileName = formatNextFile(name, i, ext);
                fileName = fileName.trim(); // if filename is empty
                uri = child(context, parent, fileName);
                i++;
            }

            return uri;
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File f1 = getFile(parent);
            return Uri.fromFile(getNextFile(f1, name, i, ext));
        } else {
            throw new UnknownUri();
        }
    }

    public static String getNextName(Context context, Uri parent, String name, String ext) {
        return getNextName(context, parent, name, 0, ext);
    }

    public static String getNextName(Context context, Uri parent, String name, int i, String ext) {
        String s = parent.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            String fileName = formatNextFile(name, i, ext);

            DocumentFile k = getDocumentFile(context, parent, fileName);

            i++;
            while (k != null && k.exists()) {
                fileName = formatNextFile(name, i, ext);
                fileName = fileName.trim(); // if filename is empty
                k = getDocumentFile(context, parent, fileName);
                i++;
            }

            return fileName;
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File f1 = getFile(parent);
            return getNextName(f1, name, i, ext);
        } else {
            throw new UnknownUri();
        }
    }

    public static long getFree(Context context, Uri uri) {
        String s = uri.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return getDocumentFree(context, uri);
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            try {
                File file = getFile(uri);
                return getFree(file);
            } catch (Exception e) { // IllegalArgumentException
                return 0;
            }
        } else {
            throw new UnknownUri();
        }
    }

    public static boolean ejected(Context context, Uri path) { // check target 'parent RW' access if child does not exist, and 'child R' if exists
        String s = path.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return isEjected(context, path, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            return ejected(getFile(path));
        } else {
            throw new UnknownUri();
        }
    }

    public static Uri touch(Context context, Uri uri, String name) {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = getFile(uri);
            File m = new File(k, name);
            try {
                new FileOutputStream(m, true).close();
                return Uri.fromFile(m);
            } catch (IOException e) {
                Log.d(TAG, "touch", e);
                return null;
            }
        } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            DocumentFile k = getDocumentFile(context, uri, name);
            try {
                Uri doc;
                if (k == null || !k.exists()) {
                    doc = createDocumentFile(context, uri, name);
                    if (doc == null)
                        throw new IOException("no permission");
                } else {
                    doc = k.getUri();
                }
                ContentResolver resolver = context.getContentResolver();
                OutputStream os = resolver.openOutputStream(doc, "wa");
                os.close();
                return doc;
            } catch (IOException e) {
                Log.d(TAG, "touch", e);
                return null;
            }
        } else {
            throw new UnknownUri();
        }
    }

    public static Uri mkdir(Context context, Uri to, String name) throws FileNotFoundException {
        String s = to.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = getFile(to);
            File m = new File(k, name);
            if (m.mkdir())
                return Uri.fromFile(m);
        } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            DocumentFile m = getDocumentFile(context, to, name);
            if (m != null && m.exists()) // directory or file == failed
                return null;
            File f = new File(name);
            File p = f.getParentFile();
            if (p != null) {
                m = getDocumentFile(context, to, p.getPath());
                if (m == null || !m.exists())
                    return null;
                to = m.getUri();
                name = f.getName();
            }
            return createDocumentFolder(context, to, name); // createFolder() 'mkdirs' mode
        } else {
            throw new UnknownUri();
        }
        return null;
    }

    public static long getLength(Context context, Uri uri) {
        String s = uri.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return getDocumentFile(context, uri).length();
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File f = getFile(uri);
            return f.length();
        } else {
            throw new UnknownUri();
        }
    }

    public static long getLastModified(Context context, Uri uri) {
        String s = uri.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return getDocumentFile(context, uri).lastModified();
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File f = getFile(uri);
            return f.lastModified();
        } else {
            throw new UnknownUri();
        }
    }

    public static String getDisplayName(Context context, Uri uri) {
        String s = uri.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) { // saf folder for content
            return getDocumentDisplayName(context, uri);
        } else if (s.equals(ContentResolver.SCHEME_FILE)) { // full destionation for files
            return getFile(uri).getPath();
        } else {
            throw new UnknownUri();
        }
    }

    // call getNextFile() on 't'
    public static Uri move(Context context, File f, Uri t) throws FileNotFoundException {
        String s = t.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            Uri root = getDocumentTreeUri(t);
            return move(context, f, root, getDocumentChildPath(t));
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File parent = f.getParentFile();

            String ext = getExt(context, t);
            String n = getNameNoExt(context, t);

            File tf = getFile(t);
            File td = tf.getParentFile();

            if (isSame(parent, td))
                return null;

            if (!mkdirs(td))
                throw new RuntimeException("Unable to create: " + td);

            File to = getNextFile(td, n, ext);

            if (isSame(f, to))
                return null;

            return Uri.fromFile(move(f, to));
        } else {
            throw new UnknownUri();
        }
    }

    public static Uri migrate(Context context, File f, Uri dir) throws FileNotFoundException {
        String s = dir.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            Log.d(TAG, "migrate: " + f + " --> " + getDisplayName(context, dir));
            String n = f.getName();
            if (f.isDirectory()) {
                Uri tt = createDocumentFolder(context, dir, n); // create (1) automatically
                File[] files = f.listFiles();
                if (files != null) {
                    for (File m : files)
                        migrate(context, m, tt);
                }
                delete(f);
                return tt;
            } else {
                return move(context, f, dir, n); // create (1) automatically
            }
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            Log.d(TAG, "migrate: " + f + " --> " + dir.getPath());
            if (f.isDirectory()) {
                File[] files = f.listFiles();
                if (files != null) {
                    for (File ff : files) {
                        File tt = new File(getFile(dir), ff.getName());
                        if (!mkdirs(tt))
                            throw new RuntimeException("No permissions: " + tt);
                        move(ff, tt);
                    }
                }
                delete(f);
                return dir;
            } else {
                File to = getFile(dir);
                if (!mkdirs(to))
                    throw new RuntimeException("No permissions: " + to);
                File tofile = new File(to, f.getName());
                return Uri.fromFile(move(f, tofile));
            }
        } else {
            throw new UnknownUri();
        }
    }

    public static ArrayList<Node> list(Context context, Uri uri) {
        return list(context, uri, null);
    }

    public static ArrayList<Node> list(Context context, Uri uri, NodeFilter filter) { // Node.name = file name, return _no_ root uris
        ArrayList<Node> files = new ArrayList<>();
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File file = getFile(uri);
            File[] ff = file.listFiles();
            if (ff != null) {
                for (File f : ff) {
                    Node n = new Node(f);
                    if (filter == null || filter.accept(n))
                        files.add(n);
                }
                return files;
            }
            throw new RuntimeException("Unable to read");
        } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            String id;
            if (DocumentsContract.isDocumentUri(context, uri))
                id = DocumentsContract.getDocumentId(uri);
            else
                id = DocumentsContract.getTreeDocumentId(uri);
            Uri doc = DocumentsContract.buildChildDocumentsUriUsingTree(uri, id);
            ContentResolver resolver = context.getContentResolver();
            Cursor cursor = resolver.query(doc, null, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    Node n = new Node(doc, cursor);
                    if (filter == null || filter.accept(n))
                        files.add(n);
                }
                cursor.close();
                return files;
            }
            throw new RuntimeException("Unable to read");
        } else {
            throw new UnknownUri();
        }
    }

    public static ArrayList<Node> walk(Context context, Uri root, Uri uri) {
        return walk(context, root, uri, null);
    }

    @SuppressLint("Range")
    public static ArrayList<Node> walk(Context context, Uri root, Uri uri, NodeFilter filter) { // Node.name = path relative to 'root' and _return_ root uris
        ArrayList<Node> files = new ArrayList<>();
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File r = getFile(root);
            File f = getFile(uri);
            files.add(new Node(uri, relative(r, f).getPath(), f.isDirectory(), f.length(), f.lastModified()));
            File[] kk = f.listFiles();
            if (kk != null) {
                for (File k : kk) {
                    Node n = new Node(Uri.fromFile(k), relative(r, k).getPath(), k.isDirectory(), k.length(), k.lastModified());
                    if (filter == null || filter.accept(n))
                        files.add(n);
                }
            }
        } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            String p = buildDocumentPath(context, root, uri);
            ContentResolver resolver = context.getContentResolver();
            Uri doc;
            if (DocumentsContract.isDocumentUri(context, uri))
                doc = uri;
            else
                doc = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
            Cursor cursor = resolver.query(doc, null, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String id = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                    String type = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE));
                    long size = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE));
                    long last = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED));
                    boolean d = type.equals(DocumentsContract.Document.MIME_TYPE_DIR);
                    files.add(new Node(uri, p, d, size, last)); // root uri (unchanged)
                    if (d) {
                        doc = DocumentsContract.buildChildDocumentsUriUsingTree(uri, id);
                        Cursor cursor2 = resolver.query(doc, null, null, null, null);
                        if (cursor2 != null) {
                            while (cursor2.moveToNext()) {
                                id = cursor2.getString(cursor2.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                                String name = cursor2.getString(cursor2.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                                type = cursor2.getString(cursor2.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE));
                                size = cursor2.getLong(cursor2.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE));
                                last = cursor2.getLong(cursor2.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED));
                                d = type.equals(DocumentsContract.Document.MIME_TYPE_DIR);
                                Node n = new Node(DocumentsContract.buildDocumentUriUsingTree(doc, id), new File(p, name).getPath(), d, size, last);
                                if (filter == null || filter.accept(n))
                                    files.add(n);
                            }
                            cursor2.close();
                        }
                    }
                }
                cursor.close();
            }
        } else {
            throw new UnknownUri();
        }
        return files;
    }

    // classes

    public static class UnknownUri extends RuntimeException {
    }

    @SuppressLint("Range")
    public static class Node {
        public Uri uri;
        public String name;
        public boolean dir;
        public long size;
        public long last;

        public Node() {
        }

        public Node(Uri uri, String n, boolean dir, long size, long last) {
            this.uri = uri;
            this.name = n;
            this.dir = dir;
            this.size = size;
            this.last = last;
        }

        public Node(File f) {
            this.uri = Uri.fromFile(f);
            this.name = f.getName();
            this.dir = f.isDirectory();
            this.size = f.length();
            this.last = f.lastModified();
        }

        public Node(DocumentFile f) {
            this.uri = f.getUri();
            this.name = f.getName();
            this.dir = f.isDirectory();
            this.size = f.length();
            this.last = f.lastModified();
        }

        @TargetApi(21)
        public Node(Uri doc, Cursor cursor) {
            String id = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
            String type = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE));
            name = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
            size = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE));
            last = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED));
            uri = DocumentsContract.buildDocumentUriUsingTree(doc, id);
            dir = type.equals(DocumentsContract.Document.MIME_TYPE_DIR);
        }

        public String toString() { // display version of file name
            if (dir)
                return name.endsWith(JavaUtils.ROOT) ? name : name + JavaUtils.ROOT;
            return name;
        }
    }

    public interface NodeFilter {
        boolean accept(Node n);
    }

    public static class SAFCache extends HashMap<Uri, Uri> {
        public Uri getParent(Context context, Uri uri) {
            String s = uri.getScheme();
            if (s.equals(ContentResolver.SCHEME_CONTENT) && !uri.getAuthority().startsWith(LeafStorage.SAF))
                return get(uri);
            return LeafStorage.getParent(context, uri);
        }

        public void addParents(Uri u, ArrayList<LeafStorage.Node> nn) {
            for (LeafStorage.Node n : nn)
                put(n.uri, u);
        }

        public void removeParents(Uri u, boolean keep) {
            for (Map.Entry<Uri, Uri> e : new HashSet<>(entrySet())) {
                if (e.getValue().equals(u)) {
                    if (!keep)
                        remove(e.getKey());
                    removeParents(e.getKey(), false);
                }
            }
        }
    }

    public static class SAFCaches<T> extends HashMap<T, SAFCache> {
        public Uri getParent(Context context, Uri uri) {
            for (SAFCache safCache : values()) {
                if (safCache.containsKey(uri))
                    return safCache.get(uri);
            }
            return LeafStorage.getParent(context, uri);
        }

        @Override
        public SAFCache get(Object t) {
            SAFCache c = super.get(t);
            if (c == null) {
                c = new SAFCache();
                put((T) t, c);
            }
            return c;
        }
    }

    public LeafStorage(Context context) {
        this.context = context;
        this.resolver = context.getContentResolver();
    }

    public Context getContext() {
        return context;
    }

    public File getLocalInternal() {
        return getLocalInternal(context);
    }

    public static File getLocalInternal(Context context) {
        File file = context.getFilesDir();
        if (file == null)
            return getDataDir(context);
        return file;
    }

    public static File getDataDir(Context context) {
        return new File(context.getApplicationContext().getApplicationInfo().dataDir);
    }

    public File getLocalExternal() {
        File external = context.getExternalFilesDir("");

        // Starting in KITKAT, no permissions are required to read or write to the getExternalFilesDir;
        // it's always accessible to the calling app.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            if (!permitted(context, PERMISSIONS_RW))
                return null;
        }

        return external;
    }

    public boolean isLocalStorage(Uri u) {
        String s = u.getScheme();
        if (s.equals(ContentResolver.SCHEME_CONTENT))
            return false;
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            return isLocalStorage(getFile(u));
        } else {
            throw new UnknownUri();
        }
    }

    public boolean isLocalStorage(File f) {
        String path = f.getPath();
        if (relative(context.getApplicationInfo().dataDir, path) != null)
            return true;

        File internal = getLocalInternal();

        File external = getLocalExternal();
        if (external != null) // some old phones <15API with disabled sdcard return null
            if (relative(external.getPath(), path) != null)
                return true;

        return relative(internal.getPath(), path) != null;
    }

    public File getLocalStorage() {
        File internal = getLocalInternal();

        File external = getLocalExternal();
        if (external == null) // some old phones <15API with disabled sdcard return null
            return internal;

        return external;
    }

    public File fallbackStorage() {
        File internal = getLocalInternal();

        // Starting in KITKAT, no permissions are required to read or write to the returned path;
        // it's always accessible to the calling app.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            if (!permitted(context, PERMISSIONS_RW))
                return internal;
        }

        File external = getLocalExternal();

        if (external == null)
            return internal;

        return external;
    }

    public File getStoragePath(File file) {
        if (ejected(file))
            return getLocalStorage();
        if (file.exists() && canWrite(file))
            return file;
        File p = file.getParentFile();
        if (!canWrite(p)) // storage con points to non existed folder, but parent should be writable
            return getLocalStorage();
        return file;
    }

    public Uri getStoragePath(String path) {
        File f;
        if (Build.VERSION.SDK_INT >= 21 && path.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Uri u = Uri.parse(path);
            if (!isEjected(context, u, JavaUtils.RW))
                return u;
            f = fallbackStorage(); // we need to fallback to local storage internal or exernal
        } else if (path.startsWith(ContentResolver.SCHEME_FILE)) {
            f = getFile(Uri.parse(path));
        } else if (path.isEmpty()) {
            return Uri.fromFile(getLocalStorage());
        } else {
            f = new File(path);
        }
        if (!permitted(context, PERMISSIONS_RW))
            return Uri.fromFile(getLocalStorage());
        else
            return Uri.fromFile(getStoragePath(f));
    }

    public void migrateLocalStorageDialog(final Activity a) {
        ProgressBar progress = new ProgressBar(context);
        progress.setIndeterminate(true);
        AlertDialog.Builder b = new AlertDialog.Builder(a);
        b.setTitle("migrating_data");
        b.setView(progress);
        b.setCancelable(false);
        final AlertDialog dialog = b.create();
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    migrateLocalStorage();
                } catch (final RuntimeException e) {
                   // Toast.Post(a, e);
                }
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dialog.cancel();
                    }
                });
            }
        });
        dialog.show();
        thread.start();
    }

    public void migrateLocalStorage() {
    }
}
