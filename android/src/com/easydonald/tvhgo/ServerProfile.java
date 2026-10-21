package com.easydonald.tvhgo;

/** 一套 Tvheadend 服务器配置 */
public class ServerProfile {
    public String name = "";
    public String server = "http://192.168.1.10:9981";
    public String user = "admin";
    public String pass = "";
    public String profile = "pass";

    public ServerProfile() {
    }

    public ServerProfile(String name, String server, String user, String pass, String profile) {
        this.name = name;
        this.server = server;
        this.user = user;
        this.pass = pass;
        this.profile = profile;
    }

    public String hostLabel() {
        String s = server == null ? "" : server.replace("http://", "").replace("https://", "");
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
