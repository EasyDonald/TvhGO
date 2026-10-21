package com.easydonald.tvhgo;

import java.util.ArrayList;
import java.util.List;

/** EPG 节目 */
class EpgEvent {
    long eventId;
    String channelUuid = "";
    String channelName = "";
    String channelNumber = "";
    long start;
    long stop;
    String title = "";
    String subtitle = "";
    String description = "";
}

/** 当前订阅（谁在看什么） */
class SubInfo {
    long id;
    String username = "";
    String client = "";
    String channel = "";
    String service = "";
    String state = "";
    String profile = "";
    double inBps;
    double outBps;
}

/** 输入流 / 调谐器状态 */
class InputInfo {
    String input = "";
    String stream = "";
    int subs;
    long bps;
    int signal;
    int snr;
}

/** 录制任务 */
class DvrEntry {
    String uuid = "";
    long start;
    long stop;
    String channelName = "";
    String title = "";
    String subtitle = "";
    String description = "";
    String status = "";
    String schedStatus = "";
    String filename = "";
    long filesize;
    int errors;
    int playcount;
    boolean finished;
    String autorec = "";
}

/** 自动录制 / 定时录制规则 */
class AutoRec {
    String uuid = "";
    boolean enabled = true;
    String name = "";
    String title = "";
    String channel = "";
    String channelName = "";
    String comment = "";
    String directory = "";
    long start;
    long stop;
    int pri;
    String daysOfWeek = "";
    boolean timerec;
}
