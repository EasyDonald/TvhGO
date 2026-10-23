package com.easydonald.tvhgo;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;

/** 应用内日志：环形缓冲，供「设置 → 日志 → App 日志」查看 */
public class AppLog {

    private static final int MAX = 400;
    private static final Deque<String> LINES = new ArrayDeque<String>();
    private static final SimpleDateFormat FMT = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);

    public static synchronized void i(String tag, String msg) {
        if (LINES.size() >= MAX) LINES.pollFirst();
        LINES.addLast(FMT.format(new Date()) + "  " + tag + "  " + msg);
    }

    public static synchronized String dump() {
        StringBuilder sb = new StringBuilder();
        for (String l : LINES) sb.append(l).append('\n');
        if (sb.length() == 0) sb.append("(空)\n");
        return sb.toString();
    }

    public static synchronized void clear() {
        LINES.clear();
    }
}
