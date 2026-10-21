package com.easydonald.tvhgo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 服务器配置（多套）+ 播放器设置 */
public class Prefs {
    private static final String NAME = "tvhclient";

    public final List<ServerProfile> profiles = new ArrayList<ServerProfile>();
    public int current = 0;

    // 当前配置的实时值（供 TvhApi / UI 直接使用）
    public String server = "http://192.168.1.10:9981";
    public String user = "admin";
    public String pass = "";
    public String profile = "pass";
    /** 用户是否手动选过默认串流配置（没选过就跟随录制配置的 profile） */
    public boolean profileChosen = false;

    // 播放器设置（全局，与服务器无关）
    public String vlcPackage = "org.videolan.vlc";
    public boolean preferVlc = true;

    /** 外部播放器 */
    public static class Player {
        public String name = "";
        public String pkg = "";

        public Player() {}

        public Player(String name, String pkg) {
            this.name = name;
            this.pkg = pkg;
        }
    }

    /** 播放器列表（默认内置一个 VLC） */
    public final java.util.List<Player> players = new java.util.ArrayList<Player>();
    /** 当前使用的播放器包名；空字符串 = 每次弹系统选择器 */
    public String playerPkg = "org.videolan.vlc";
    /** 频道列表左侧显示：false=频道号，true=频道图标 */
    public boolean showIcons = false;
    /** 频道副标题：0=分组，1=当前播出节目 */
    public int subtitleMode = 1;
    /** 界面语言：0=简中，1=繁中，2=英语，3=日语 */
    public int lang = 0;
    /** 主题：0=跟随系统，1=深色，2=浅色 */
    public int themeMode = 0;
    /** 频道单独指定的串流 profile（uuid -> profile 名），没有则跟随默认 */
    public final java.util.Map<String, String> channelProfiles = new java.util.LinkedHashMap<String, String>();

    public static Prefs load(Context c) {
        SharedPreferences sp = c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
        Prefs p = new Prefs();

        String raw = sp.getString("profiles", "");
        if (raw != null && !raw.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    p.profiles.add(new ServerProfile(
                            o.optString("name", "配置"),
                            o.optString("server", "http://192.168.1.10:9981"),
                            o.optString("user", "admin"),
                            o.optString("pass", ""),
                            o.optString("profile", "pass")));
                }
            } catch (Exception ignored) {
            }
        }

        // 迁移旧版单服务器配置
        if (p.profiles.isEmpty()) {
            p.profiles.add(new ServerProfile(
                    "默认",
                    sp.getString("server", "http://192.168.1.10:9981"),
                    sp.getString("user", "admin"),
                    sp.getString("pass", ""),
                    sp.getString("profile", "pass")));
        }

        p.current = sp.getInt("current_profile", 0);
        if (p.current < 0 || p.current >= p.profiles.size()) p.current = 0;

        p.vlcPackage = sp.getString("vlc", p.vlcPackage);
        String playersJson = sp.getString("players", "");
        if (playersJson != null && !playersJson.isEmpty()) {
            try {
                org.json.JSONArray arr = new org.json.JSONArray(playersJson);
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject o = arr.getJSONObject(i);
                    String nm = o.optString("name");
                    String pk = o.optString("pkg");
                    if (!pk.isEmpty()) p.players.add(new Player(nm.isEmpty() ? pk : nm, pk));
                }
            } catch (Exception ignored) {
            }
        }
        if (p.players.isEmpty()) {
            p.players.add(new Player("VLC", p.vlcPackage == null || p.vlcPackage.isEmpty()
                    ? "org.videolan.vlc" : p.vlcPackage));
        }
        if (sp.contains("player_pkg")) {
            p.playerPkg = sp.getString("player_pkg", p.players.get(0).pkg);
        } else if (sp.contains("prefer_vlc") && !sp.getBoolean("prefer_vlc", true)) {
            p.playerPkg = "";
        } else {
            p.playerPkg = p.players.get(0).pkg;
        }
        p.preferVlc = sp.getBoolean("prefer_vlc", true);
        p.showIcons = sp.getBoolean("show_icons", false);
        p.subtitleMode = sp.getInt("subtitle_mode", 1);
        p.lang = sp.getInt("lang", 0);
        p.themeMode = sp.getInt("theme_mode", 0);
        p.profileChosen = sp.getBoolean("profile_chosen", false);

        String cp = sp.getString("channel_profiles", "");
        if (cp != null && !cp.isEmpty()) {
            try {
                org.json.JSONObject o = new org.json.JSONObject(cp);
                java.util.Iterator<String> it = o.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    p.channelProfiles.put(k, o.optString(k));
                }
            } catch (Exception ignored) {
            }
        }

        p.syncFromProfile();
        return p;
    }

    public void save(Context c) {
        syncToProfile();
        JSONArray arr = new JSONArray();
        for (ServerProfile p : profiles) {
            try {
                JSONObject o = new JSONObject();
                o.put("name", p.name);
                o.put("server", p.server);
                o.put("user", p.user);
                o.put("pass", p.pass);
                o.put("profile", p.profile);
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        org.json.JSONObject cpObj = new org.json.JSONObject();
        for (java.util.Map.Entry<String, String> e : channelProfiles.entrySet()) {
            try {
                cpObj.put(e.getKey(), e.getValue());
            } catch (Exception ignored) {
            }
        }

        c.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
                .putString("channel_profiles", cpObj.toString())
                .putString("profiles", arr.toString())
                .putInt("current_profile", current)
                .putString("server", server)
                .putString("user", user)
                .putString("pass", pass)
                .putString("profile", profile)
                .putBoolean("profile_chosen", profileChosen)
                .putString("vlc", vlcPackage)
                .putString("players", playersJson())
                .putString("player_pkg", playerPkg)
                .putBoolean("prefer_vlc", preferVlc)
                .putBoolean("show_icons", showIcons)
                .putInt("subtitle_mode", subtitleMode)
                .putInt("lang", lang)
                .putInt("theme_mode", themeMode)
                .apply();
    }

    public ServerProfile cur() {
        if (profiles.isEmpty()) profiles.add(new ServerProfile());
        if (current < 0 || current >= profiles.size()) current = 0;
        return profiles.get(current);
    }

    /** 把当前配置写进实时字段 */
    public void syncFromProfile() {
        ServerProfile p = cur();
        server = p.server;
        user = p.user;
        pass = p.pass;
        profile = p.profile;
    }

    /** 把实时字段回写到当前配置 */
    public void syncToProfile() {
        ServerProfile p = cur();
        p.server = server;
        p.user = user;
        p.pass = pass;
        p.profile = profile;
    }

    public void switchTo(int idx) {
        if (idx < 0 || idx >= profiles.size()) return;
        syncToProfile();
        current = idx;
        syncFromProfile();
    }

    private String playersJson() {
        org.json.JSONArray arr = new org.json.JSONArray();
        for (Player pl : players) {
            try {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("name", pl.name);
                o.put("pkg", pl.pkg);
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        return arr.toString();
    }

    /** 当前播放器显示名 */
    public String playerName() {
        if (playerPkg == null || playerPkg.isEmpty()) return null;
        for (Player p : players) {
            if (p.pkg.equals(playerPkg)) return p.name;
        }
        return playerPkg;
    }

    /** 某频道实际使用的 profile（频道单独设置优先，否则用默认） */
    public String profileFor(String channelUuid) {
        String v = channelProfiles.get(channelUuid);
        return (v == null || v.isEmpty()) ? profile : v;
    }

    public void setProfileFor(String channelUuid, String profileName) {
        if (profileName == null || profileName.isEmpty()) channelProfiles.remove(channelUuid);
        else channelProfiles.put(channelUuid, profileName);
    }

    public String base() {
        String s = server == null ? "" : server.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        return s;
    }
}
