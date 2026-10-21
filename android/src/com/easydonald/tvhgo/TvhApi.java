package com.easydonald.tvhgo;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tvheadend HTTP API 的最小封装（Basic 认证） */
public class TvhApi {
    public final String base;
    private final String user;
    private final String pass;
    public final String profile;

    public TvhApi(String base, String user, String pass, String profile) {
        this.base = base;
        this.user = user == null ? "" : user;
        this.pass = pass == null ? "" : pass;
        this.profile = (profile == null || profile.trim().isEmpty()) ? "pass" : profile.trim();
    }

    public static TvhApi from(Prefs p) {
        return new TvhApi(p.base(), p.user, p.pass, p.profile);
    }

    private HttpURLConnection open(String path, String method) throws IOException {
        URL u = new URL(base + path);
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestMethod(method);
        c.setRequestProperty("Accept", "application/json");
        if (!user.isEmpty() || !pass.isEmpty()) {
            String token = Base64.encodeToString((user + ":" + pass).getBytes("UTF-8"), Base64.NO_WRAP);
            c.setRequestProperty("Authorization", "Basic " + token);
        }
        return c;
    }

    private static String readAll(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        return sb.toString();
    }

    private JSONObject requestJson(String path, String method, String body) throws IOException {
        HttpURLConnection c = open(path, method);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            byte[] data = body.getBytes("UTF-8");
            c.setFixedLengthStreamingMode(data.length);
            OutputStream os = c.getOutputStream();
            os.write(data);
            os.flush();
            os.close();
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = in == null ? "" : readAll(in);
        c.disconnect();
        if (code == 401) throw new IOException("认证失败 (HTTP 401)，请检查用户名/密码");
        if (code == 403) throw new IOException("权限不足 (HTTP 403)：请检查该账号是否具有管理员（Admin）权限");
        if (code >= 400) throw new IOException("HTTP " + code + (text.isEmpty() ? "" : ": " + text.trim()));
        try {
            return text.trim().isEmpty() ? new JSONObject() : new JSONObject(text);
        } catch (org.json.JSONException e) {
            throw new IOException("服务器返回的不是合法 JSON：" + e.getMessage());
        }
    }

    /** 服务器信息 */
    public String serverInfo() throws IOException {
        try {
            JSONObject o = requestJson("/api/serverinfo", "GET", null);
            return o.optString("name", "Tvheadend") + " " + o.optString("sw_version", "");
        } catch (Exception e) {
            return "";
        }
    }

    /** 标签 id -> 名称 */
    public Map<String, String> tags() throws IOException {
        Map<String, String> map = new LinkedHashMap<String, String>();
        JSONArray arr = requestJson("/api/channeltag/list", "GET", null).optJSONArray("entries");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                try {
                    JSONObject o = arr.getJSONObject(i);
                    map.put(o.optString("key"), o.optString("val"));
                } catch (org.json.JSONException ignored) {
                }
            }
        }
        return map;
    }

    /** 全部频道 */
    public List<Channel> channels() throws IOException {
        List<Channel> out = new ArrayList<Channel>();
        int start = 0;
        final int limit = 500;
        while (true) {
            JSONObject o = requestJson("/api/channel/grid?start=" + start + "&limit=" + limit, "GET", null);
            JSONArray arr = o.optJSONArray("entries");
            if (arr == null) break;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e;
                try {
                    e = arr.getJSONObject(i);
                } catch (org.json.JSONException ex) {
                    continue;
                }
                Channel c = new Channel();
                c.uuid = e.optString("uuid");
                c.name = e.optString("name");
                c.number = e.optInt("number", 0);
                c.enabled = e.optBoolean("enabled", true);
                c.iconUrl = e.optString("icon_public_url", "");
                JSONArray tags = e.optJSONArray("tags");
                if (tags != null) {
                    for (int t = 0; t < tags.length(); t++) c.tags.add(tags.optString(t));
                }
                if (c.uuid != null && c.uuid.length() > 0) out.add(c);
            }
            int total = o.optInt("total", out.size());
            if (arr.length() < limit || out.size() >= total) break;
            start += arr.length();
        }
        Collections.sort(out, new Comparator<Channel>() {
            public int compare(Channel a, Channel b) {
                if (a.number != b.number) return a.number - b.number;
                return String.valueOf(a.name).compareToIgnoreCase(String.valueOf(b.name));
            }
        });
        return out;
    }

    /** 拉流 profile 列表：每项 [profile 名, uuid]（用 idnode 接口，能拿到 pass-norewrite 等全部配置） */
    public List<String[]> profiles() throws IOException {
        List<String[]> out = new ArrayList<String[]>();
        JSONArray arr = requestJson("/api/idnode/load?class=profile", "GET", null).optJSONArray("entries");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                String cls = o.optString("class");
                String name = o.optString("text");
                String uuid = o.optString("uuid");
                if (cls != null && cls.contains("htsp")) continue;
                if (name != null && !name.isEmpty()) out.add(new String[]{name, uuid});
            } catch (org.json.JSONException ignored) {
            }
        }
        return out;
    }

    /** 当前订阅（谁在看什么） */
    public List<SubInfo> subscriptions() throws IOException {
        List<SubInfo> out = new ArrayList<SubInfo>();
        JSONArray arr = requestJson("/api/status/subscriptions?start=0&limit=200", "GET", null).optJSONArray("entries");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o;
            try {
                o = arr.getJSONObject(i);
            } catch (org.json.JSONException e) {
                continue;
            }
            SubInfo s = new SubInfo();
            s.id = o.optLong("id");
            s.username = o.optString("username");
            s.client = o.optString("client");
            s.channel = o.optString("channel");
            s.service = o.optString("service");
            s.state = o.optString("state");
            s.profile = o.optString("profile");
            s.inBps = o.optDouble("in", 0);
            s.outBps = o.optDouble("out", 0);
            out.add(s);
        }
        return out;
    }

    /** 输入流 / 调谐器状态 */
    public List<InputInfo> inputs() throws IOException {
        List<InputInfo> out = new ArrayList<InputInfo>();
        JSONArray arr = requestJson("/api/status/inputs?start=0&limit=200", "GET", null).optJSONArray("entries");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o;
            try {
                o = arr.getJSONObject(i);
            } catch (org.json.JSONException e) {
                continue;
            }
            InputInfo in = new InputInfo();
            in.input = o.optString("input");
            in.stream = o.optString("stream");
            in.subs = o.optInt("subs", 0);
            in.bps = (long) o.optDouble("bps", 0);
            in.signal = o.optInt("signal", 0);
            in.snr = o.optInt("snr", 0);
            out.add(in);
        }
        return out;
    }

    /** 连接信息（状态页「连接」列表） */
    public static class ConnInfo {
        public long id;
        public String user = "";
        public String peer = "";
        public String type = "";
        public int streaming;
    }

    public List<ConnInfo> connectionsList() throws IOException {
        List<ConnInfo> out = new ArrayList<ConnInfo>();
        JSONArray arr = requestJson("/api/status/connections?start=0&limit=200", "GET", null).optJSONArray("entries");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                ConnInfo c = new ConnInfo();
                c.id = o.optLong("id");
                c.user = o.optString("user");
                c.peer = o.optString("peer") + ":" + o.optString("peer_port");
                c.type = o.optString("type");
                c.streaming = o.optInt("streaming", 0);
                out.add(c);
            } catch (org.json.JSONException ignored) {
            }
        }
        return out;
    }

    /** 事件连接数 */
    public int connections() throws IOException {
        return connectionsList().size();
    }

    /** 断开某条连接（网页版 状态→连接→Cancel） */
    public void cancelConnection(long id) throws IOException {
        postForm("/api/connections/cancel", "id=" + id);
    }

    /** 断开全部连接 */
    public void cancelAllConnections() throws IOException {
        postForm("/api/connections/cancel", "id=all");
    }

    /** 从偏移量取一个事件的开始时间（EPG 按开始时间升序） */
    private long epgStartAt(int offset) throws IOException {
        JSONObject o = requestJson("/api/epg/events/grid?start=" + offset + "&limit=1&sort=start&dir=ASC", "GET", null);
        JSONArray arr = o.optJSONArray("entries");
        if (arr == null || arr.length() == 0) return Long.MIN_VALUE;
        try {
            return arr.getJSONObject(0).optLong("start", 0);
        } catch (org.json.JSONException e) {
            return Long.MIN_VALUE;
        }
    }

    /**
     * 全频道「正在播出」：服务端不支持按时间过滤，这里用二分查找定位到当前时刻附近，
     * 再取一段窗口，客户端筛选 start <= now < stop。
     */
    public List<EpgEvent> epgNow() throws IOException {
        long now = System.currentTimeMillis() / 1000L;
        int total = epgTotal();
        if (total <= 0) return new ArrayList<EpgEvent>();
        int pos = epgOffsetAt(now - 12 * 3600L);
        List<EpgEvent> out = new ArrayList<EpgEvent>();
        final int pageSize = 800;
        for (int p = 0; p < 3; p++) {
            int from = pos + p * pageSize;
            if (from >= total) break;
            int count = Math.min(pageSize, total - from);
            JSONArray arr = requestJson("/api/epg/events/grid?start=" + from + "&limit=" + count + "&sort=start&dir=ASC", "GET", null)
                    .optJSONArray("entries");
            if (arr == null || arr.length() == 0) break;
            long maxStart = 0;
            for (int i = 0; i < arr.length(); i++) {
                EpgEvent e = toEvent(arr, i);
                if (e == null) continue;
                if (e.start > maxStart) maxStart = e.start;
                if (e.start <= now && e.stop > now) out.add(e);
            }
            if (arr.length() < pageSize || maxStart >= now) break;
        }
        return out;
    }

    /** EPG 总条数 */
    public int epgTotal() throws IOException {
        JSONObject head = requestJson("/api/epg/events/grid?start=0&limit=1&sort=start&dir=ASC", "GET", null);
        return head.optInt("totalCount", 0);
    }

    /** 二分查找：第一个开始时间 > sec 的事件偏移量（结果可直接当 start 参数） */
    public int epgOffsetAt(long sec) throws IOException {
        int total = epgTotal();
        if (total <= 0) return 0;
        int lo = 0, hi = total - 1, pos = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long st = epgStartAt(mid);
            if (st <= sec) { pos = mid; lo = mid + 1; } else { hi = mid - 1; }
        }
        return pos;
    }

    /** 取时间窗口内的 EPG（按页累积到窗口末尾，最多 8 页 × 800 条） */
    public List<EpgEvent> epgWindow(long fromSec, long toSec) throws IOException {
        List<EpgEvent> out = new ArrayList<EpgEvent>();
        int total = epgTotal();
        if (total <= 0) return out;
        int pos = epgOffsetAt(fromSec - 8 * 3600L);   // 往前多取，覆盖跨窗口的长节目
        final int pageSize = 800;
        for (int p = 0; p < 8; p++) {
            int start = pos + p * pageSize;
            if (start >= total) break;
            JSONArray arr = requestJson("/api/epg/events/grid?start=" + start + "&limit=" + pageSize + "&sort=start&dir=ASC", "GET", null)
                    .optJSONArray("entries");
            if (arr == null || arr.length() == 0) break;
            long maxStart = 0;
            for (int i = 0; i < arr.length(); i++) {
                EpgEvent e = toEvent(arr, i);
                if (e == null) continue;
                if (e.start > maxStart) maxStart = e.start;
                if (e.stop > fromSec && e.start < toSec) out.add(e);
            }
            if (arr.length() < pageSize || maxStart >= toSec) break;
        }
        return out;
    }

    private EpgEvent toEvent(JSONArray arr, int i) {
        try {
            JSONObject o = arr.getJSONObject(i);
            EpgEvent e2 = new EpgEvent();
            e2.eventId = o.optLong("eventId");
            e2.channelUuid = o.optString("channelUuid");
            e2.channelName = o.optString("channelName");
            e2.channelNumber = o.optString("channelNumber");
            e2.start = o.optLong("start");
            e2.stop = o.optLong("stop");
            e2.title = o.optString("title");
            e2.subtitle = o.optString("subtitle");
            e2.description = o.optString("description");
            return e2;
        } catch (org.json.JSONException ex) {
            return null;
        }
    }

    /** 某频道的 EPG 节目（不传 uuid 则取全部，按开始时间升序） */
    public List<EpgEvent> epg(String channelUuid) throws IOException {
        String path = "/api/epg/events/grid?start=0&limit=500&sort=start&dir=ASC";
        if (channelUuid != null && !channelUuid.isEmpty()) path += "&channel=" + channelUuid;
        List<EpgEvent> out = new ArrayList<EpgEvent>();
        JSONArray arr = requestJson(path, "GET", null).optJSONArray("entries");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            EpgEvent e2 = toEvent(arr, i);
            if (e2 != null) out.add(e2);
        }
        return out;
    }

    // ---------------- DVR 录制 ----------------

    private JSONObject postForm(String path, String body) throws IOException {
        return requestJson(path, "POST", body);
    }

    /** 录制配置（取第一个可用配置的 uuid） */
    public String dvrConfigUuid() throws IOException {
        JSONArray arr = requestJson("/api/dvr/config/grid?start=0&limit=50", "GET", null).optJSONArray("entries");
        if (arr == null || arr.length() == 0) return "";
        try {
            return arr.getJSONObject(0).optString("uuid", "");
        } catch (org.json.JSONException e) {
            return "";
        }
    }

    private List<DvrEntry> dvrGrid(String path) throws IOException {
        List<DvrEntry> out = new ArrayList<DvrEntry>();
        final int pageSize = 400;
        int start = 0;
        int total = -1;
        while (true) {
            JSONObject page = requestJson(path + "?start=" + start + "&limit=" + pageSize, "GET", null);
            JSONArray arr = page.optJSONArray("entries");
            int t = page.optInt("total", page.optInt("totalCount", -1));
            if (t >= 0) total = t;
            if (arr == null || arr.length() == 0) break;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o;
                try {
                    o = arr.getJSONObject(i);
                } catch (org.json.JSONException e) {
                    continue;
                }
                DvrEntry d = new DvrEntry();
                d.uuid = o.optString("uuid");
                d.start = o.optLong("start");
                d.stop = o.optLong("stop");
                d.channelName = o.optString("channelname");
                d.title = o.optString("disp_title", o.optString("title"));
                d.subtitle = o.optString("disp_subtitle");
                d.description = o.optString("disp_description");
                d.status = o.optString("status");
                d.schedStatus = o.optString("sched_status");
                d.filename = o.optString("filename");
                d.filesize = o.optLong("filesize", 0);
                d.errors = o.optInt("errors", 0);
                d.playcount = o.optInt("playcount", 0);
                d.autorec = o.optString("autorec_caption", "");
                d.pri = o.optInt("pri", 0);
                d.startExtra = o.optInt("start_extra", 0);
                d.stopExtra = o.optInt("stop_extra", 0);
                d.enabled = o.optBoolean("enabled", true);
                d.state = o.optString("state", "");
                out.add(d);
            }
            start += arr.length();
            if (arr.length() < pageSize) break;
            if (total > 0 && start >= total) break;
        }
        return out;
    }

    /** 失败 / 已移除 */
    public List<DvrEntry> dvrFailed() throws IOException {
        List<DvrEntry> list = dvrGrid("/api/dvr/entry/grid_failed");
        for (DvrEntry d : list) d.finished = true;
        return list;
    }

    public List<DvrEntry> dvrRemoved() throws IOException {
        List<DvrEntry> list = dvrGrid("/api/dvr/entry/grid_removed");
        for (DvrEntry d : list) d.finished = false;
        return list;
    }

    /** 编辑待录制/录制中的任务 */
    public void updateEntry(String uuid, String title, long start, long stop,
                            int startExtra, int stopExtra, boolean enabled) throws IOException {
        org.json.JSONObject node = new org.json.JSONObject();
        try {
            node.put("uuid", uuid);
            node.put("disp_title", title);
            node.put("start", start);
            node.put("stop", stop);
            node.put("start_extra", startExtra);
            node.put("stop_extra", stopExtra);
            node.put("enabled", enabled);
        } catch (org.json.JSONException e) {
            throw new IOException(e.getMessage());
        }
        postForm("/api/idnode/save", "node=" + enc(node.toString()));
    }

    /** 默认录制配置用的串流 profile 名称（读不到返回空串） */
    public String dvrConfigProfileName() throws IOException {
        JSONArray arr = requestJson("/api/dvr/config/grid?start=0&limit=1", "GET", null).optJSONArray("entries");
        if (arr == null || arr.length() == 0) return "";
        try {
            String puuid = arr.getJSONObject(0).optString("profile");
            if (puuid == null || puuid.isEmpty()) return "";
            for (String[] p : profiles()) {
                if (p[1].equals(puuid)) return p[0];
            }
        } catch (org.json.JSONException ignored) {
        }
        return "";
    }

    /** 重新录制（失败/已移除的条目） */
    public void rerecord(String uuid) throws IOException {
        postForm("/api/dvr/entry/rerecord/toggle", "uuid=" + uuid);
    }

    /** 自动录制 / 定时录制规则 */
    public List<AutoRec> autorecs(boolean timerec) throws IOException {
        String path = timerec ? "/api/dvr/timerec/grid" : "/api/dvr/autorec/grid";
        List<AutoRec> out = new ArrayList<AutoRec>();
        final int pageSize = 400;
        int start = 0;
        int total = -1;
        while (true) {
            JSONObject page = requestJson(path + "?start=" + start + "&limit=" + pageSize, "GET", null);
            JSONArray arr = page.optJSONArray("entries");
            int t = page.optInt("total", page.optInt("totalCount", -1));
            if (t >= 0) total = t;
            if (arr == null || arr.length() == 0) break;
            for (int i = 0; i < arr.length(); i++) {
                try {
                    JSONObject o = arr.getJSONObject(i);
                    AutoRec a = new AutoRec();
                    a.uuid = o.optString("uuid");
                    a.enabled = o.optBoolean("enabled", true);
                    a.name = o.optString("name");
                    a.title = o.optString("title");
                    a.channel = o.optString("channel");
                    a.channelName = o.optString("channelname");
                    a.comment = o.optString("comment");
                    a.directory = o.optString("directory");
                    a.start = o.optLong("start", 0);
                    a.stop = o.optLong("stop", 0);
                    a.pri = o.optInt("pri", 0);
                    a.daysOfWeek = o.optString("days_of_week");
                    a.timerec = timerec;
                    out.add(a);
                } catch (org.json.JSONException ignored) {
                }
            }
            start += arr.length();
            if (arr.length() < pageSize) break;
            if (total > 0 && start >= total) break;
        }
        return out;
    }

    /** 启停规则 */
    public void setRuleEnabled(String uuid, boolean enabled) throws IOException {
        postForm("/api/idnode/save", "node=" + enc("{\"uuid\":\"" + uuid + "\",\"enabled\":" + enabled + "}"));
    }

    public List<DvrEntry> dvrUpcoming() throws IOException {
        List<DvrEntry> list = dvrGrid("/api/dvr/entry/grid_upcoming");
        for (DvrEntry d : list) d.finished = false;
        return list;
    }

    /** 已完成 */
    public List<DvrEntry> dvrFinished() throws IOException {
        List<DvrEntry> list = dvrGrid("/api/dvr/entry/grid_finished");
        for (DvrEntry d : list) d.finished = true;
        return list;
    }

    /** 按 EPG 事件创建录制 */
    public void recordEvent(long eventId, String configUuid) throws IOException {
        String body = "event_id=" + eventId + "&config_uuid=" + URLEncoder.encode(configUuid, "UTF-8");
        postForm("/api/dvr/entry/create_by_event", body);
    }

    /** 取消待录制 / 停止正在录制 */
    public void dvrCancel(String uuid) throws IOException {
        postForm("/api/dvr/entry/cancel", "uuid=" + URLEncoder.encode(uuid, "UTF-8"));
    }

    public void dvrStop(String uuid) throws IOException {
        postForm("/api/dvr/entry/stop", "uuid=" + URLEncoder.encode(uuid, "UTF-8"));
    }

    /** 删除录制条目 */
    public void dvrDelete(String uuid) throws IOException {
        postForm("/api/idnode/delete", "uuid=" + URLEncoder.encode(uuid, "UTF-8"));
    }

    /** 已录文件播放地址 */
    public String recordingUrl(String uuid) {
        return base.replaceFirst("://", "://" + enc(user) + ":" + enc(pass) + "@") + "/dvrfile/" + uuid;
    }

    /** 录制配置列表：每项 [uuid, 显示名] */
    public List<String[]> dvrConfigs() throws IOException {
        List<String[]> out = new ArrayList<String[]>();
        JSONArray arr = requestJson("/api/dvr/config/grid?start=0&limit=50", "GET", null).optJSONArray("entries");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                String uuid = o.optString("uuid");
                String name = o.optString("name");
                if (name == null || name.trim().isEmpty()) name = "默认配置";
                out.add(new String[]{uuid, name});
            } catch (org.json.JSONException ignored) {
            }
        }
        return out;
    }

    /**
     * 自定义录制（网页版「添加」用的是 POST /api/dvr/entry/create，字段放在 conf=<JSON> 里；
     * 标题是 langstr，必须传 {"chi": "..."}，否则会被忽略）。
     */
    public void createRecording(long start, long stop, String channelUuid, String channelName,
                                String title, int pri, int startExtraMin, int stopExtraMin,
                                String configUuid) throws IOException {
        try {
            JSONObject conf = new JSONObject();
            conf.put("enabled", true);
            conf.put("start", start);
            conf.put("stop", stop);
            conf.put("channel", channelUuid);
            conf.put("channelname", channelName == null ? "" : channelName);
            conf.put("config_name", configUuid);
            conf.put("pri", pri);
            conf.put("start_extra", Math.max(0, startExtraMin) * 60);
            conf.put("stop_extra", Math.max(0, stopExtraMin) * 60);
            if (title != null && !title.trim().isEmpty()) {
                JSONObject t = new JSONObject();
                t.put("chi", title.trim());
                conf.put("title", t);
            }
            postForm("/api/dvr/entry/create", "conf=" + URLEncoder.encode(conf.toString(), "UTF-8"));
        } catch (org.json.JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    /** 某频道某时间窗口的 EPG（先二分定位，再取窗口，最多 500 条） */
    public List<EpgEvent> epgChannelWindow(String channelUuid, long fromSec, long toSec) throws IOException {
        List<EpgEvent> out = new ArrayList<EpgEvent>();
        JSONObject head = requestJson("/api/epg/events/grid?channel=" + channelUuid + "&start=0&limit=1&sort=start&dir=ASC", "GET", null);
        int total = head.optInt("totalCount", 0);
        if (total <= 0) return out;

        int lo = 0, hi = total - 1, pos = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            JSONObject one = requestJson("/api/epg/events/grid?channel=" + channelUuid + "&start=" + mid + "&limit=1&sort=start&dir=ASC", "GET", null);
            JSONArray arr = one.optJSONArray("entries");
            long st = 0;
            if (arr != null && arr.length() > 0) {
                try {
                    st = arr.getJSONObject(0).optLong("start", 0);
                } catch (org.json.JSONException ignored) {
                }
            }
            if (st <= fromSec) { pos = mid; lo = mid + 1; } else { hi = mid - 1; }
        }
        int count = Math.min(500, Math.max(1, total - pos));
        JSONArray arr = requestJson("/api/epg/events/grid?channel=" + channelUuid + "&start=" + pos + "&limit=" + count + "&sort=start&dir=ASC", "GET", null)
                .optJSONArray("entries");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            EpgEvent e = toEvent(arr, i);
            if (e != null && e.stop > fromSec && e.start < toSec) out.add(e);
        }
        return out;
    }

    /** 保存频道属性（只传需要改的字段） */
    public void saveChannel(JSONObject fields) throws IOException {
        String body = "node=" + URLEncoder.encode(fields.toString(), "UTF-8");
        requestJson("/api/idnode/save", "POST", body);
    }

    public void setEnabled(String uuid, boolean enabled) throws IOException {
        try {
            JSONObject f = new JSONObject();
            f.put("uuid", uuid);
            f.put("enabled", enabled);
            saveChannel(f);
        } catch (org.json.JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    public void updateChannel(String uuid, String name, int number, boolean enabled) throws IOException {
        try {
            JSONObject f = new JSONObject();
            f.put("uuid", uuid);
            if (name != null) f.put("name", name);
            if (number > 0) f.put("number", number);
            f.put("enabled", enabled);
            saveChannel(f);
        } catch (org.json.JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    /** 供 VLC 直接打开的播放地址（带上账号密码） */
    public String streamUrl(Channel c) {
        return streamUrlForUuid(c.uuid);
    }
    public String streamUrlForUuid(String uuid) {
        return streamUrlForUuid(uuid, profile);
    }

    public String streamUrlForUuid(String uuid, String profileName) {
        String prof = (profileName == null || profileName.isEmpty()) ? profile : profileName;
        return base.replaceFirst("://", "://" + enc(user) + ":" + enc(pass) + "@") +
                "/stream/channel/" + uuid + "?profile=" + enc(prof);
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return "";
        }
    }
}
