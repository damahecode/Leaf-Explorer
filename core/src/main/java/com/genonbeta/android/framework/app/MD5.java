package com.genonbeta.android.framework.app;

import java.nio.charset.Charset;
import java.security.MessageDigest;

public class MD5 {
    public static String hex(byte[] in) {
        final StringBuilder builder = new StringBuilder();
        for (byte b : in)
            builder.append(String.format("%02x", b));
        return builder.toString();
    }

    public static String digest(String str) {
        byte[] buf = str.getBytes(Charset.defaultCharset());
        return hex(digest(buf));
    }

    public static byte[] digest(byte[] buf) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return md.digest(buf);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
