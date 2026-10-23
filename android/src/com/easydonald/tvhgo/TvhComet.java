package com.easydonald.tvhgo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Tvheadend comet 长轮询客户端（供日志页使用）。
 * 与服务端网页版使用同一通道：POST /comet/poll 取回推送消息，
 * 打开日志页时额外调用 /comet/debug 提升服务端日志级别。
 */
public class TvhComet {

    public interface Listener {
        void onLog(String text);

        void onConnected(boolean ok);
    }

    private final String base;
    private final String authHeader;
    private final Listener listener;

    private volatile boolean running;
    private volatile String boxid = "";
    private Thread thread;

    public TvhComet(String base, String user, String pass, Listener l) {
        this.base = base;
        this.listener = l;
        String token = android.util.Base64.encodeToString(
                ((user == null ? "" : user) + ":" + (pass == null ? "" : pass)).getBytes(),
                android.util.Base64.NO_WRAP);
        this.authHeader = "Basic " + token;
    }

    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(new Runnable() {
            public void run() {
                loop();
            }
        }, "tvh-comet");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) thread.interrupt();
        thread = null;
    }

    private void loop() {
        boolean first = true;
        boolean debugSent = false;
        while (running) {
            try {
                String resp = post("/comet/poll",
                        "boxid=" + URLEncoder.encode(boxid, "UTF-8") + "&immediate=" + (first ? "1" : "0"));
                first = false;
                if (resp == null) {
                    listener.onConnected(false);
                    sleep(2000);
                    continue;
                }
                JSONObject o = new JSONObject(resp);
                String nb = o.optString("boxid", "");
                if (nb != null && !nb.isEmpty()) boxid = nb;
                listener.onConnected(true);

                if (!debugSent && !boxid.isEmpty()) {
                    debugSent = true;
                    post("/comet/debug", "boxid=" + URLEncoder.encode(boxid, "UTF-8"));
                }

                JSONArray msgs = o.optJSONArray("messages");
                if (msgs != null) {
                    for (int i = 0; i < msgs.length(); i++) {
                        JSONObject m = msgs.optJSONObject(i);
                        if (m == null) continue;
                        if ("logmessage".equals(m.optString("notificationClass"))) {
                            String txt = m.optString("logtxt", "");
                            if (!txt.isEmpty()) listener.onLog(txt);
                        }
                    }
                }
            } catch (Exception e) {
                listener.onConnected(false);
                sleep(1500);
            }
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private String post(String path, String body) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(base + path).openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(8000);
            c.setReadTimeout(45000);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            c.setRequestProperty("Authorization", authHeader);
            OutputStream os = c.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.flush();
            os.close();
            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (is == null) return null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            return bos.toString("UTF-8");
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
