package com.easydonald.tvhgo;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Random;

/**
 * HTTP 认证：同时支持 Basic 与 Digest。
 *
 * Tvheadend 本身通常使用 Basic 认证，但有些部署前面挂了反向代理（例如某些网关），
 * 只接受 Digest。此类的流程是：
 *   1. 先按当前已知方式（默认 Basic）发请求；
 *   2. 若返回 401 且响应头带 Digest 挑战，则按 RFC 2617 计算摘要并重试一次；
 *   3. 记住服务端的认证方式，后续请求直接复用。
 */
public class HttpAuth {

    private static final int UNKNOWN = 0, BASIC = 1, DIGEST = 2;

    private final String user;
    private final String pass;

    private volatile int scheme = UNKNOWN;
    private volatile String realm = "";
    private volatile String nonce = "";
    private volatile String opaque = "";
    private volatile String qop = "";
    private int nc = 0;
    private final Random rnd = new Random();

    public HttpAuth(String user, String pass) {
        this.user = user == null ? "" : user;
        this.pass = pass == null ? "" : pass;
    }

    public void reset() {
        scheme = UNKNOWN;
        nonce = "";
        realm = "";
        opaque = "";
        qop = "";
        nc = 0;
    }

    public boolean isDigest() {
        return scheme == DIGEST && nonce != null && !nonce.isEmpty();
    }

    /** 打开已认证连接（可带 POST body），遇到 Digest 挑战自动重试一次 */
    public HttpURLConnection open(String url, String method, byte[] body,
                                  int connectTimeout, int readTimeout) throws IOException {
        HttpURLConnection c = raw(url, method, body, connectTimeout, readTimeout);
        int code = c.getResponseCode();
        if (code == 401) {
            String ch = c.getHeaderField("WWW-Authenticate");
            c.disconnect();
            if (ch != null && ch.trim().toLowerCase().startsWith("digest")) {
                parseChallenge(ch);
                c = raw(url, method, body, connectTimeout, readTimeout);
            }
        }
        return c;
    }

    private HttpURLConnection raw(String url, String method, final byte[] body,
                                  int connectTimeout, int readTimeout) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(connectTimeout);
        c.setReadTimeout(readTimeout);
        c.setRequestMethod(method);
        c.setRequestProperty("Accept", "application/json, */*");
        c.setRequestProperty("User-Agent", "TvhGO");

        if (user.isEmpty() && pass.isEmpty()) {
            // 无需认证
        } else if (isDigest()) {
            c.setRequestProperty("Authorization", digestHeader(method, url));
        } else {
            String token = android.util.Base64.encodeToString(
                    (user + ":" + pass).getBytes("UTF-8"), android.util.Base64.NO_WRAP);
            c.setRequestProperty("Authorization", "Basic " + token);
        }

        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            c.setFixedLengthStreamingMode(body.length);
            OutputStream os = c.getOutputStream();
            os.write(body);
            os.flush();
            os.close();
        }
        return c;
    }

    private synchronized void parseChallenge(String header) {
        String h = header.trim();
        if (!h.toLowerCase().startsWith("digest")) return;
        scheme = DIGEST;
        String rest = h.substring(6).trim();
        int i = 0;
        while (i < rest.length()) {
            int eq = rest.indexOf('=', i);
            if (eq < 0) break;
            String key = rest.substring(i, eq).trim().toLowerCase();
            int j = eq + 1;
            String val;
            if (j < rest.length() && rest.charAt(j) == '"') {
                int end = rest.indexOf('"', j + 1);
                if (end < 0) break;
                val = rest.substring(j + 1, end);
                i = end + 1;
            } else {
                int end = rest.indexOf(',', j);
                if (end < 0) end = rest.length();
                val = rest.substring(j, end).trim();
                i = end;
            }
            if ("realm".equals(key)) realm = val;
            else if ("nonce".equals(key)) nonce = val;
            else if ("opaque".equals(key)) opaque = val;
            else if ("qop".equals(key)) qop = val;
            while (i < rest.length() && (rest.charAt(i) == ',' || rest.charAt(i) == ' ')) i++;
        }
        nc = 0;
    }

    private String digestHeader(String method, String url) {
        String uri = pathOf(url);
        int n;
        synchronized (this) {
            n = ++nc;
        }
        String ncHex = String.format("%08x", n);
        byte[] cnonceBytes = new byte[8];
        rnd.nextBytes(cnonceBytes);
        String cnonce = hex(cnonceBytes);
        boolean hasQop = qop != null && !qop.isEmpty();
        String ha1 = md5(user + ":" + realm + ":" + pass);
        String ha2 = md5(method + ":" + uri);
        String response = hasQop
                ? md5(ha1 + ":" + nonce + ":" + ncHex + ":" + cnonce + ":auth:" + ha2)
                : md5(ha1 + ":" + nonce + ":" + ha2);

        StringBuilder sb = new StringBuilder("Digest ");
        sb.append("username=\"").append(user).append("\", ");
        sb.append("realm=\"").append(realm).append("\", ");
        sb.append("nonce=\"").append(nonce).append("\", ");
        sb.append("uri=\"").append(uri).append("\", ");
        sb.append("response=\"").append(response).append("\"");
        if (hasQop) {
            sb.append(", qop=auth, nc=").append(ncHex).append(", cnonce=\"").append(cnonce).append("\"");
        }
        if (opaque != null && !opaque.isEmpty()) {
            sb.append(", opaque=\"").append(opaque).append("\"");
        }
        return sb.toString();
    }

    private static String pathOf(String url) {
        int i = url.indexOf("://");
        int start = i < 0 ? 0 : url.indexOf('/', i + 3);
        return start < 0 ? "/" : url.substring(start);
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return hex(md.digest(s.getBytes("UTF-8")));
        } catch (Exception e) {
            return "";
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
