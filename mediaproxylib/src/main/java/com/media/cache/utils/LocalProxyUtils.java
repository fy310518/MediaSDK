package com.media.cache.utils;

import android.content.Context;
import android.text.TextUtils;

import com.android.baselib.utils.LogUtils;
import com.media.cache.model.VideoCacheInfo;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalProxyUtils {

    public static final int UPDATE_INTERVAL = 1000;
    public static final int DEFAULT_BUFFER_SIZE = 8 * 1024;
    private static final Pattern URL_PATTERN = Pattern.compile("GET /(.*) HTTP");
    public static final String INFO_FILE = "video.info";
    public static final String SPLIT_STR = "&jeffmony&";

    public static File getVideoCacheDir(Context context) {
        return new File(context.getExternalCacheDir(), ".local");
    }

    public static void close(Closeable closeable) {
        if (closeable != null){
            try {
                closeable.close();
            } catch (Exception e) {
                LogUtils.w("LocalProxyUtils close " + closeable +" failed, exception = " +e);
            }
        }
    }

    public static String findUrlForStream(InputStream inputStream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream,"UTF-8"));
        StringBuilder builder = new StringBuilder();
        String line;
        while (!TextUtils.isEmpty(line = reader.readLine())){
            builder.append(line)
                    .append("\n");
        }
        Matcher matcher = URL_PATTERN.matcher(builder.toString());
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new Exception("Url not found");
    }

    public static String encodeUri(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("Error encoding url", e);
        }
    }


    public static String decodeUri(String str) {
        String decoded = null;
        try {
            decoded = URLDecoder.decode(str, "UTF-8");
        } catch (Exception ignored) {
            LogUtils.w("Encoding not supported, ignored: "+ignored.getMessage());
        }
        return decoded;
    }

    private static String bytesToHexString(byte[] bytes) {
        StringBuffer sb = new StringBuffer();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String computeMD5(String string) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] digestBytes = messageDigest.digest(string.getBytes());
            return bytesToHexString(digestBytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object sFileLock = new Object();

    public static VideoCacheInfo readProxyCacheInfo(File dir) {
        File file = new File(dir, INFO_FILE);
        if (!file.exists()) {
            LogUtils.i("readProxyCacheInfo failed, file not exist.");
            return null;
        }
        ObjectInputStream fis = null;
        try {
            synchronized (sFileLock) {
                fis = new ObjectInputStream(new FileInputStream(file));
                VideoCacheInfo info = (VideoCacheInfo) fis.readObject();
                return info;
            }
        } catch (Exception e) {
            LogUtils.w("readProxyCacheInfo failed, exception="+e.getMessage());
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (Exception e) {
                LogUtils.w("readProxyCacheInfo failed, close fis failed.");
            }
        }
        return null;
    }

    public static void writeProxyCacheInfo(VideoCacheInfo info, File dir) {
        File file = new File(dir, INFO_FILE);
        ObjectOutputStream fos = null;
        try {
            synchronized (sFileLock) {
                fos = new ObjectOutputStream(new FileOutputStream(file));
                fos.writeObject(info);
            }
        } catch (Exception e) {
            LogUtils.w("writeProxyCacheInfo failed, exception="+e.getMessage());
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (Exception e) {
                LogUtils.w("writeProxyCacheInfo failed, close fos failed.");
            }
        }
    }

    public static void setLastModifiedNow(File file) throws IOException {
        if (file.exists()) {
            long now = System.currentTimeMillis();
            boolean modified = file.setLastModified(now); // on some devices (e.g. Nexus 5) doesn't work
            if (!modified) {
                modify(file);
            }
        }
    }

    private static void modify(File dir) throws IOException {
        File tempFile = new File(dir, "tempFile");
        if (!tempFile.exists()) {
            tempFile.createNewFile();
            tempFile.delete();
        } else {
            tempFile.delete();
        }
    }

    public static List<File> getLruFileList(File dir) {
        List<File> result = new LinkedList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            result = Arrays.asList(files);
            Collections.sort(result, new LastModifiedComparator());
        }
        return result;
    }

    private static final class LastModifiedComparator implements Comparator<File> {

        @Override
        public int compare(File lhs, File rhs) {
            return compareLong(lhs.lastModified(), rhs.lastModified());
        }

        private int compareLong(long first, long second) {
            return (first < second) ? -1 : ((first == second) ? 0 : 1);
        }
    }

    public static boolean deleteFile(File file) {
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                if (!f.delete())
                    return false;
            }
            return file.delete();
        } else {
            return file.delete();
        }
    }

    public static void deleteCacheFile(File file) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] listFiles = file.listFiles();
            for (File f : listFiles) {
                if (f.isDirectory()) {
                    deleteCacheFile(f);
                    f.delete();
                } else {
                    f.delete();
                }
            }
            file.delete();
        } else {
            file.delete();
        }
    }

    public static long countTotalSize(List<File> files) {
        long totalSize = 0;
        for (File file : files)  {
            totalSize += countTotalSize(file);
        }
        return totalSize;
    }

    public static long countTotalSize(File file) {
        if (file.isDirectory()) {
            long totalSize = 0;
            for (File f : file.listFiles()) {
                totalSize += f.length();
            }
            return totalSize;
        } else {
            return file.length();
        }
    }

    public static boolean isChinese(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        if (ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || ub == Character.UnicodeBlock.GENERAL_PUNCTUATION
                || ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || ub == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS) {
            return true;
        }
        return false;
    }

    public static boolean isMessyCode(String strName) {
        Pattern p = Pattern.compile("\\s*|t*|r*|n*");
        Matcher m = p.matcher(strName);
        String after = m.replaceAll("");
        String temp = after.replaceAll("\\p{P}", "");
        char[] ch = temp.trim().toCharArray();
        float chLength = ch.length;
        float count = 0;
        for (int i = 0; i < ch.length; i++) {
            char c = ch[i];
            if (!Character.isLetterOrDigit(c)) {
                if (!isChinese(c)) {
                    count = count + 1;
                }
            }
        }
        if (chLength <= 0)
            return false;
        float result = count / chLength;
        if (result > 0.4) {
            return true;
        } else {
            return false;
        }
    }
}
