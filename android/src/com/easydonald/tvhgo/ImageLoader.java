package com.easydonald.tvhgo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 频道台标加载（内存缓存 + Basic 认证 + 支持预加载） */
public class ImageLoader {

    public interface Callback {
        void onLoaded(Bitmap bmp);
    }

    /** 缓存上限按频道数量留余量；图片解码后最长边压到 128px，内存占用可控 */
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

    public static synchronized ImageLoader get() {
        if (instance == null) instance = new ImageLoader();
        return instance;
    }

    public void configure(String base, String user, String pass) {
        this.base = base;
        synchronized (cache) {
            cache.clear();
        }
        if ((user != null && !user.isEmpty()) || (pass != null && !pass.isEmpty())) {
            String token = Base64.encodeToString(
                    ((user == null ? "" : user) + ":" + (pass == null ? "" : pass)).getBytes(), Base64.NO_WRAP);
            auth = "Basic " + token;
        } else {
            auth = null;
        }
    }

    public boolean has(String path) {
        if (path == null || path.isEmpty()) return false;
        synchronized (cache) {
            return cache.containsKey(path);
        }
    }

    /** 同步获取（已缓存则直接返回），供预加载使用 */
    public Bitmap fetch(String path) {
        if (path == null || path.isEmpty()) return null;
        synchronized (cache) {
            Bitmap c = cache.get(path);
            if (c != null) return c;
        }
        Bitmap bmp = download(path);
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
            byte[] data = bos.toByteArray();
            if (data.length == 0) return null;

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
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
