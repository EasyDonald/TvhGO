package com.easydonald.tvhgo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 频道图标加载（带内存缓存 + Basic 认证） */
public class ImageLoader {

    public interface Callback {
        void onLoaded(Bitmap bmp);
    }

    private static ImageLoader instance;

    private final Map<String, Bitmap> cache = new LinkedHashMap<String, Bitmap>(256, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
            return size() > 256;
        }
    };
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private String base = "";
    private String auth = null;

    public static synchronized ImageLoader get() {
        if (instance == null) instance = new ImageLoader();
        return instance;
    }

    public void configure(String base, String user, String pass) {
        this.base = base;
        cache.clear();
        if ((user != null && !user.isEmpty()) || (pass != null && !pass.isEmpty())) {
            String token = Base64.encodeToString(
                    ((user == null ? "" : user) + ":" + (pass == null ? "" : pass)).getBytes(), Base64.NO_WRAP);
            auth = "Basic " + token;
        } else {
            auth = null;
        }
    }

    public void load(final String path, final Callback cb) {
        if (path == null || path.isEmpty()) return;
        Bitmap cached = cache.get(path);
        if (cached != null) {
            cb.onLoaded(cached);
            return;
        }
        pool.execute(new Runnable() {
            public void run() {
                Bitmap bmp = null;
                try {
                    String url = path.startsWith("http") ? path : base + "/" + path;
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(10000);
                    if (auth != null) c.setRequestProperty("Authorization", auth);
                    InputStream in = c.getInputStream();
                    bmp = BitmapFactory.decodeStream(in);
                    in.close();
                    c.disconnect();
                } catch (Exception ignored) {
                }
                final Bitmap fb = bmp;
                if (fb != null) cache.put(path, fb);
                ui.post(new Runnable() {
                    public void run() {
                        if (fb != null) cb.onLoaded(fb);
                    }
                });
            }
        });
    }
}
