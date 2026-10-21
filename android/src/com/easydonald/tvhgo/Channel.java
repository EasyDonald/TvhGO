package com.easydonald.tvhgo;

import java.util.ArrayList;
import java.util.List;

/** 频道数据 */
public class Channel {
    public String uuid;
    public String name;
    public int number;
    public boolean enabled = true;
    public List<String> tags = new ArrayList<String>();
    public String iconUrl = "";
    public String status = "";

    @Override
    public String toString() {
        return (number > 0 ? number + " " : "") + name;
    }
}
