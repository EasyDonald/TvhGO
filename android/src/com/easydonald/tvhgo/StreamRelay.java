package com.easydonald.tvhgo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 本地串流中继：把 127.0.0.1 上的请求转发给 Tvheadend，并代为完成 HTTP 认证。
 *
 * 用途：当 Tvheadend 前面的反向代理只接受 Digest 认证时，
 * 外部播放器（VLC 等）拿不到可用凭据，会直接 401。
 * 这里在手机本地开一个端口，播放器连本地端口，App 负责用 Digest 认证取流并转发。
 */
public class StreamRelay {

    public interface Idle {
        void onIdle();
    }

    private static StreamRelay instance;

    private ServerSocket server;
    private volatile boolean running;
    private volatile int active = 0;
    private String base = "";
    private HttpAuth auth;
    private Idle idleCallback;

    public static synchronized StreamRelay get() {
        if (instance == null) instance = new StreamRelay();
        return instance;
    }

    public synchronized int ensure(String base, String user, String pass) throws IOException {
        if (running && server != null && !server.isClosed()) return server.getLocalPort();
        this.base = base;
        this.auth = new HttpAuth(user, pass);
        server = new ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"));
        running = true;
        Thread t = new Thread(new Runnable() {
            public void run() {
                acceptLoop();
            }
        }, "stream-relay");
        t.setDaemon(true);
        t.start();
        AppLog.i("RELAY", "本地中继已启动，端口 " + server.getLocalPort());
        return server.getLocalPort();
    }

    public void setIdleCallback(Idle cb) {
        idleCallback = cb;
    }

    public boolean isRunning() {
        return running;
    }

    public synchronized void stop() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {
        }
        server = null;
    }

    private void acceptLoop() {
        while (running) {
            Socket s = null;
            try {
                s = server.accept();
            } catch (Exception e) {
                break;
            }
            final Socket socket = s;
            active++;
            Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        handle(socket);
                    } finally {
                        try {
                            socket.close();
                        } catch (Exception ignored) {
                        }
                        if (--active <= 0 && idleCallback != null) idleCallback.onIdle();
                    }
                }
            }, "relay-conn");
            t.setDaemon(true);
            t.start();
        }
    }

    private void handle(Socket socket) {
        HttpURLConnection conn = null;
        try {
            socket.setSoTimeout(20000);
            InputStream in = socket.getInputStream();
            StringBuilder head = new StringBuilder();
            int c, count = 0;
            while ((c = in.read()) != -1 && count < 8192) {
                head.append((char) c);
                count++;
                if (head.length() >= 4 && head.substring(head.length() - 4).equals("\r\n\r\n")) break;
            }
            String[] lines = head.toString().split("\r\n");
            if (lines.length == 0) return;
            String[] req = lines[0].split(" ");
            if (req.length < 2) return;
            String path = req[1];
            conn = auth.open(base + path, "GET", null, 10000, 0);
            int code = conn.getResponseCode();
            String ctype = conn.getContentType();

            OutputStream out = socket.getOutputStream();
            if (code >= 400) {
                out.write(("HTTP/1.1 " + code + " Error\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                out.flush();
                AppLog.i("RELAY", "上游返回 " + code + " " + path);
                return;
            }
            StringBuilder h = new StringBuilder();
            h.append("HTTP/1.1 ").append(code == 206 ? "206 Partial Content" : "200 OK").append("\r\n");
            if (ctype != null) h.append("Content-Type: ").append(ctype).append("\r\n");
            h.append("Accept-Ranges: none\r\n");
            h.append("Connection: close\r\n\r\n");
            out.write(h.toString().getBytes("UTF-8"));
            out.flush();

            InputStream up = conn.getInputStream();
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = up.read(buf)) > 0) {
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (Exception e) {
            AppLog.i("RELAY", "转发失败：" + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
