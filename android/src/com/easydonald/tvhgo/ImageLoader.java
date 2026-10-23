package com.easydonald.tvhgo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 频道台标加载：内存缓存 + 磁盘缓存 + Basic 认证。
 *
 * 磁盘缓存位于应用私有目录 picons/，文件名是 URL 的 SHA-1，
 * 因此切换服务器、刷新、重启应用都不会重新下载台标；
 * 只有调用 clearDisk() 才会清空并重新拉取。
 */
public class ImageLoader {

    public interface Callback {
        void onLoaded(Bitmap bmp);
    }

    private static final int MAX_CACHE = 700;
    private static final int MAX_SIDE = 96;

    private static ImageLoader instance;

    private final Map<String, Bitmap> cache = new LinkedHashMap<String, Bitmap>(MAX_CACHE, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
            return size() > MAX_CACHE;
        }
    };
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Handler ui = new Handler(Looper.getMainLooper());

    private String base = "";
    private String auth = null;
    private String cfgBase = null, cfgUser = null, cfgPass = null;
    private File diskDir = null;

    public static synchronized ImageLoader get() {
        if (instance == null) instance = new ImageLoader();
        return instance;
    }

    public void configure(Context ctx, String base, String user, String pass) {
        this.base = base;
        diskDir = new File(ctx.getCacheDir(), "picons");
        if (!diskDir.exists()) diskDir.mkdirs();

        boolean changed = !eq(cfgBase, base) || !eq(cfgUser, user) || !eq(cfgPass, pass);
        cfgBase = base;
        cfgUser = user;
        cfgPass = pass;
        if (changed) {
            synchronized (cache) {
                cache.clear();
            }
        }
        if ((user != null && !user.isEmpty()) || (pass != null && !pass.isEmpty())) {
            String token = Base64.encodeToString(
                    ((user == null ? "" : user) + ":" + (pass == null ? "" : pass)).getBytes(), Base64.NO_WRAP);
            auth = "Basic " + token;
        } else {
            auth = null;
        }
    }

    private boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    public boolean has(String path) {
        if (path == null || path.isEmpty()) return false;
        synchronized (cache) {
            if (cache.containsKey(path)) return true;
        }
        File f = diskFile(path);
        return f != null && f.exists() && f.length() > 0;
    }

    /** 同步获取（内存 → 磁盘 → 网络），供预加载使用 */
    public Bitmap fetch(String path) {
        if (path == null || path.isEmpty()) return null;
        synchronized (cache) {
            Bitmap c = cache.get(path);
            if (c != null) return c;
        }
        Bitmap bmp = readDisk(path);
        if (bmp == null) {
            bmp = download(path);
            if (bmp != null) writeDisk(path, bmp);
        }
        if (bmp != null) {
            synchronized (cache) {
                cache.put(path, bmp);
            }
        }
        return bmp;
    }

    public void load(final String path, final Callback cb) {
        if (path == null || path.isEmpty()) return;
        synchronized (cache) {
            Bitmap cached = cache.get(path);
            if (cached != null) {
                cb.onLoaded(cached);
                return;
            }
        }
        pool.execute(new Runnable() {
            public void run() {
                final Bitmap bmp = fetch(path);
                if (bmp != null) {
                    ui.post(new Runnable() {
                        public void run() {
                            cb.onLoaded(bmp);
                        }
                    });
                }
            }
        });
    }

    // ---------------- 磁盘缓存 ----------------

    private File diskFile(String path) {
        if (diskDir == null || path == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(path.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return new File(diskDir, sb.toString() + ".img");
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap readDisk(String path) {
        File f = diskFile(path);
        if (f == null || !f.exists() || f.length() == 0) return null;
        try {
            byte[] data = readAll(f);
            return decode(data);
        } catch (Exception e) {
            return null;
        }
    }

    private void writeDisk(String path, Bitmap bmp) {
        File f = diskFile(path);
        if (f == null) return;
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
            os.flush();
        } catch (Exception ignored) {
        } finally {
            try {
                if (os != null) os.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** 磁盘缓存占用（字节） */
    public long diskBytes(Context ctx) {
        File dir = new File(ctx.getCacheDir(), "picons");
        long sum = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) sum += f.length();
        }
        return sum;
    }

    public int diskCount(Context ctx) {
        File dir = new File(ctx.getCacheDir(), "picons");
        File[] files = dir.listFiles();
        return files == null ? 0 : files.length;
    }

    /** 清空内存与磁盘缓存（设置中手动触发） */
    public void clearDisk(Context ctx) {
        synchronized (cache) {
            cache.clear();
        }
        File dir = new File(ctx.getCacheDir(), "picons");
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) f.delete();
        }
    }

    // ---------------- 网络 ----------------

    private Bitmap download(String path) {
        HttpURLConnection c = null;
        try {
            String url = path.startsWith("http") ? path : base + "/" + path;
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            if (auth != null) c.setRequestProperty("Authorization", auth);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return decode(bos.toByteArray());
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private Bitmap decode(byte[] data) {
        if (data == null || data.length == 0) return null;
        try {
            BitmapFactory.Options probe = new BitmapFactory.Options();
            probe.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, probe);
            int sample = 1;
            int maxSide = Math.max(probe.outWidth, probe.outHeight);
            while (maxSide > 0 && maxSide / sample > MAX_SIDE * 2) sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
            if (bmp == null) return null;

            int w = bmp.getWidth(), h = bmp.getHeight();
            int longest = Math.max(w, h);
            if (longest > MAX_SIDE) {
                float scale = (float) MAX_SIDE / longest;
                Bitmap scaled = Bitmap.createScaledBitmap(bmp,
                        Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), true);
                if (scaled != bmp) bmp.recycle();
                bmp = scaled;
            }
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] readAll(File f) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toByteArray();
    }
}
