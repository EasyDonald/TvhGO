package com.easydonald.tvhgo;

import android.app.Activity;

import static com.easydonald.tvhgo.I18n.tr;
import static com.easydonald.tvhgo.Theme.*;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private Prefs prefs;
    private TvhApi api;
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final List<Channel> all = new ArrayList<Channel>();
    private final List<Channel> shown = new ArrayList<Channel>();
    private Map<String, String> tagNames = new LinkedHashMap<String, String>();
    private String query = "";
    private String tagFilter = null;
    private String serverInfo = "";

    private static final int TAB_CH = 0, TAB_DVR = 1, TAB_EPG = 2, TAB_STATUS = 3, TAB_SET = 4;
    private int tab = TAB_CH;
    private boolean statusLoaded = false, epgLoaded = false, dvrLoaded = false;

    // 频道页
    private LinearLayout channelsView;
    private ListView channelList;
    private ChannelAdapter channelAdapter;
    private TextView tagPicker;
    private final List<String> tagLabels = new ArrayList<String>();
    private TextView statusLine;
    private final List<String> spinnerKeys = new ArrayList<String>();

    // 状态页
    private ScrollView statusView;
    private LinearLayout statusContent;
    private boolean statusBusy = false;
    private String lastInfo = "";
    private int lastConns = 0, lastChCount = 0, lastTagCount = 0;
    private final Map<String, Long> lastInputBps = new LinkedHashMap<String, Long>();
    private List<TvhApi.ConnInfo> connList = new ArrayList<TvhApi.ConnInfo>();
    private final Map<String, Integer> inputZeroTicks = new LinkedHashMap<String, Integer>();
    private TextView titleView;
    private TextView subtitleView;

    private final Runnable statusTicker = new Runnable() {
        public void run() {
            if (tab == TAB_STATUS && !isFinishing()) {
                loadStatus(true);
                ui.postDelayed(this, 1000);
            }
        }
    };

    // EPG
    private LinearLayout epgView;
    private EpgTimelineView timeline;
    private TextView epgStatus;

    // 录制
    private LinearLayout dvrView;
    private LinearLayout dvrTabsBox;
    private final List<TextView> dvrTabViews = new ArrayList<TextView>();
    private final List<AutoRec> recItems = new ArrayList<AutoRec>();
    private AutoRecAdapter autoAdapter;
    private ListView dvrList;
    private DvrAdapter dvrAdapter;
    private TextView dvrStatus;
    private final List<DvrEntry> dvrItems = new ArrayList<DvrEntry>();
    private int dvrTab = 0;   // 0待录制 1已完成 2失败 3已移除 4自动录制 5定时录制
    private List<String[]> dvrConfigList = null;

    private ScrollView settingsView;
    private ProgressBar progress;
    private final TextView[] tabButtons = new TextView[5];


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.load(this);
        I18n.setLang(prefs.lang);
        Theme.apply(prefs.themeMode, isSystemNight(this));
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Theme.BG));
        api = TvhApi.from(prefs);
        buildUi();
        loadChannels();
        if (prefs.pass.isEmpty()) showSettings(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tab == TAB_STATUS) {
            ui.removeCallbacks(statusTicker);
            ui.post(statusTicker);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(statusTicker);
    }

    // ---------------- 基础 ----------------

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private android.graphics.drawable.Drawable roundBg(int color, int radiusDp) {
        return roundBg(color, radiusDp, true);
    }

    private android.graphics.drawable.Drawable roundBg(int color, int radiusDp, boolean stroke) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (stroke) {
            int w = Math.max(1, (int) (getResources().getDisplayMetrics().density * 0.6f));
            g.setStroke(w, LINE);
        }
        return g;
    }

    /** 主题化的下拉框适配器（默认布局在深浅色下文字颜色不对） */
    private ArrayAdapter<String> spinnerAdapter(List<String> items) {
        ArrayAdapter<String> a = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextColor(TEXT);
                v.setBackgroundColor(CARD);
                v.setPadding(dp(10), dp(9), dp(10), dp(9));
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                v.setTextColor(TEXT);
                v.setBackgroundColor(CARD);
                v.setPadding(dp(14), dp(11), dp(14), dp(11));
                return v;
            }
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    private TextView text(String s, int sizeSp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setOnClickListener(l);
        return b;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // ---- 顶栏 ----
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(14), dp(10), dp(10));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleView = text("TvhGO", 21, TEXT, true);
        titleCol.addView(titleView);
        subtitleView = text(tr("正在连接…"), 10, DIM, false);
        subtitleView.setPadding(0, dp(2), 0, 0);
        titleCol.addView(subtitleView);
        header.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView refresh = new ImageView(this);
        refresh.setImageResource(R.drawable.ic_refresh);
        refresh.setColorFilter(DIM);
        refresh.setScaleType(ImageView.ScaleType.FIT_CENTER);
        refresh.setBackground(roundBg(Theme.isDark() ? 0x14FFFFFF : 0x0F000000, 20, false));
        refresh.setPadding(dp(9), dp(9), dp(9), dp(9));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(38), dp(38));
        refresh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (tab == TAB_CH) loadChannels();
                else if (tab == TAB_STATUS) loadStatus(false);
                else if (tab == TAB_EPG) loadEpg();
                else if (tab == TAB_DVR) loadDvr();
            }
        });
        header.addView(refresh, rlp);
        root.addView(header);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        // ---- 页面 ----
        channelsView = buildChannelsView();
        statusView = buildStatusView();
        epgView = buildEpgView();
        dvrView = buildDvrView();
        settingsView = buildSettingsView();
        root.addView(channelsView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(statusView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(epgView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(dvrView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(settingsView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ---- 底部分隔线 + 导航 ----
        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        root.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setGravity(Gravity.CENTER);
        tabBar.setBackgroundColor(TAB_BG);
        String[] names = {tr("频道"), tr("录制"), "EPG", tr("状态"), tr("设置")};
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setGravity(Gravity.CENTER);
            TextView lbl = text(names[i], 12, DIM, true);
            lbl.setPadding(dp(15), dp(7), dp(15), dp(7));
            cell.addView(lbl);
            cell.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { switchTab(idx); }
            });
            tabButtons[i] = lbl;
            tabBar.addView(cell, new LinearLayout.LayoutParams(0, dp(56), 1f));
        }
        root.addView(tabBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        setContentView(root);
        updateTitle();
        switchTab(TAB_CH);
    }

    private void updateTitle() {
        if (titleView != null) titleView.setText("TvhGO");
        if (subtitleView != null) {
            String n = prefs.cur().name;
            subtitleView.setText(n == null || n.isEmpty() ? "Tvheadend" : n);
        }
    }

    /** 切换/修改服务器配置后清空所有缓存数据 */
    private void resetData() {
        all.clear();
        shown.clear();
        tagNames.clear();
        spinnerKeys.clear();
        statusLoaded = epgLoaded = dvrLoaded = false;
        dvrConfigList = null;
        streamProfiles = null;
        lastInputBps.clear();
        inputZeroTicks.clear();
        if (channelAdapter != null) channelAdapter.notifyDataSetChanged();
        if (statusLine != null) statusLine.setText("");
        if (dvrItems != null) {
            dvrItems.clear();
            if (dvrAdapter != null) dvrAdapter.notifyDataSetChanged();
        }
    }

    private void switchTab(int idx) {
        tab = idx;
        channelsView.setVisibility(idx == TAB_CH ? View.VISIBLE : View.GONE);
        statusView.setVisibility(idx == TAB_STATUS ? View.VISIBLE : View.GONE);
        epgView.setVisibility(idx == TAB_EPG ? View.VISIBLE : View.GONE);
        dvrView.setVisibility(idx == TAB_DVR ? View.VISIBLE : View.GONE);
        settingsView.setVisibility(idx == TAB_SET ? View.VISIBLE : View.GONE);
        for (int i = 0; i < tabButtons.length; i++) {
            boolean sel = i == idx;
            tabButtons[i].setTextColor(sel ? ACCENT : DIM);
            tabButtons[i].setBackground(sel ? roundBg(0x1F26C6DA, 20, false) : null);
        }
        ui.removeCallbacks(statusTicker);
        if (idx == TAB_STATUS) {
            if (!statusLoaded) loadStatus(false);
            ui.post(statusTicker);
        }
        if (idx == TAB_EPG && !epgLoaded) loadEpg();
        if (idx == TAB_DVR && !dvrLoaded) loadDvr();
        if (idx == TAB_SET) refreshSettingsTab();
    }

    // ---------------- 频道 ----------------

    private LinearLayout buildChannelsView() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackgroundColor(BG);

        // 搜索框
        LinearLayout searchWrap = new LinearLayout(this);
        searchWrap.setPadding(dp(14), dp(4), dp(14), dp(6));
        final EditText search = field();
        search.setHint(tr("搜索频道（名称或频道号）"));
        search.setHintTextColor(DIM);
        search.setTextColor(TEXT);
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setBackground(roundBg(CARD, 16));
        search.setPadding(dp(14), dp(10), dp(10), dp(10));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                query = s.toString().trim();
                filterChannels();
                search.setCompoundDrawablesWithIntrinsicBounds(0, 0,
                        s.length() > 0 ? R.drawable.ic_clear : 0, 0);
            }
            public void afterTextChanged(Editable s) {}
        });
        search.setCompoundDrawablePadding(dp(6));
        search.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getAction() == android.view.MotionEvent.ACTION_UP
                        && !search.getText().toString().isEmpty()
                        && e.getX() >= search.getWidth() - search.getPaddingRight() - dp(28)) {
                    search.setText("");
                    return true;
                }
                return false;
            }
        });
        searchWrap.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        v.addView(searchWrap);

        // 分组
        LinearLayout filterWrap = new LinearLayout(this);
        filterWrap.setPadding(dp(14), 0, dp(14), dp(6));
        tagPicker = pickerButton(tr("全部频道"), new View.OnClickListener() {
            public void onClick(View view) { showTagPicker(); }
        });
        filterWrap.addView(tagPicker, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        v.addView(filterWrap);

        statusLine = text("", 10, DIM, false);
        statusLine.setPadding(dp(18), dp(0), dp(18), dp(6));
        v.addView(statusLine);

        channelList = new ListView(this);
        channelList.setDividerHeight(1);
        channelList.setDivider(new android.graphics.drawable.ColorDrawable(LINE));
        channelList.setPadding(0, 0, 0, dp(8));
        channelList.setClipToPadding(false);
        channelList.setBackgroundColor(BG);
        channelAdapter = new ChannelAdapter();
        channelList.setAdapter(channelAdapter);
        channelList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View view, int pos, long id) { openDetail(shown.get(pos)); }
        });
        channelList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View view, int pos, long id) {
                channelMenu(shown.get(pos));
                return true;
            }
        });
        v.addView(channelList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return v;
    }

    // ---------------- 状态 ----------------

    private ScrollView buildStatusView() {
        statusView = new ScrollView(this);
        statusView.setBackgroundColor(BG);
        statusContent = new LinearLayout(this);
        statusContent.setOrientation(LinearLayout.VERTICAL);
        statusContent.setPadding(0, dp(4), 0, dp(12));
        statusView.addView(statusContent);
        return statusView;
    }

    private void loadStatus(final boolean silent) {
        if (statusBusy) return;
        statusBusy = true;
        if (!silent) {
            progress.setVisibility(View.VISIBLE);
            statusContent.removeAllViews();
            statusContent.addView(text(tr("正在读取状态…"), 12, DIM, false));
        }
        bg.execute(new Runnable() {
            public void run() {
                try {
                    final List<SubInfo> subs = api.subscriptions();
                    final List<InputInfo> ins = api.inputs();
                    final List<TvhApi.ConnInfo> cl = api.connectionsList();
                    final String info;
                    final int conns, chCount, tagCount;
                    if (silent) {
                        info = lastInfo;
                        conns = cl.size();
                        chCount = lastChCount;
                        tagCount = lastTagCount;
                    } else {
                        info = api.serverInfo();
                        conns = cl.size();
                        chCount = all.size();
                        tagCount = tagNames.size();
                    }
                    ui.post(new Runnable() {
                        public void run() {
                            statusBusy = false;
                            progress.setVisibility(View.GONE);
                            statusLoaded = true;
                            lastInfo = info;
                            lastConns = conns;
                            connList = cl;
                            lastChCount = chCount;
                            lastTagCount = tagCount;
                            int y = statusView.getScrollY();
                            renderStatus(info, subs, ins, conns, chCount, tagCount);
                            statusView.scrollTo(0, y);
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        public void run() {
                            statusBusy = false;
                            progress.setVisibility(View.GONE);
                            if (!silent) {
                                statusContent.removeAllViews();
                                statusContent.addView(text(tr("读取失败：") + e.getMessage(), 12, WARN, false));
                            }
                        }
                    });
                }
            }
        });
    }

    private TextView kv(String k, String v) {
        TextView t = text(k + "  " + v, 12, TEXT, false);
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    private TextView section(String s) {
        TextView t = text(s, 13, ACCENT, true);
        t.setPadding(0, dp(12), 0, dp(4));
        return t;
    }

    private void renderStatus(String info, List<SubInfo> subs, List<InputInfo> ins,
                              int conns, int chCount, int tagCount) {
        statusContent.removeAllViews();
        statusContent.addView(section(tr("服务器")));
        statusContent.addView(kv(tr("名称"), info.isEmpty() ? "Tvheadend" : info));
        statusContent.addView(kv(tr("频道 / 分组"), chCount + tr(" 个 / ") + tagCount + tr(" 个")));
        statusContent.addView(kv(tr("事件连接"), conns + tr(" 条")));

        statusContent.addView(section(tr("当前订阅（") + subs.size() + "）"));
        if (subs.isEmpty()) statusContent.addView(text(tr("暂无客户端在观看"), 12, DIM, false));
        for (SubInfo s : subs) {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackground(roundBg(CARD, 14));
            box.setPadding(dp(10), dp(8), dp(10), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            box.addView(text(s.username + "  ·  " + shorten(s.client, 42), 13, TEXT, true));
            box.addView(text(tr("频道：") + s.channel + (s.service.isEmpty() ? "" : "（" + shorten(s.service, 40) + "）"), 11, DIM, false));
            box.addView(text(tr("状态：") + s.state + "  ·  " + s.profile + "  ·  " +
                    kbps(s.inBps) + tr(" 入 / ") + kbps(s.outBps) + tr(" 出"), 11, DIM, false));
            final SubInfo subRef = s;
            box.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { subDialog(subRef); }
            });
            statusContent.addView(box, lp);
        }

        statusContent.addView(section(tr("输入流（") + ins.size() + "）"));
        if (ins.isEmpty()) statusContent.addView(text(tr("当前没有活动的输入流"), 12, DIM, false));
        for (InputInfo in : ins) {
            // 输入流的 bps 会偶发跳 0，做几拍保持，避免界面闪烁
            String key = in.input + "|" + in.stream;
            long shownBps = in.bps;
            if (in.bps <= 0) {
                Long prev = lastInputBps.get(key);
                Integer z = inputZeroTicks.get(key);
                int zz = (z == null ? 0 : z) + 1;
                inputZeroTicks.put(key, zz);
                if (prev != null && prev > 0 && zz <= 3) shownBps = prev;
            } else {
                lastInputBps.put(key, in.bps);
                inputZeroTicks.put(key, 0);
            }
            statusContent.addView(kv(in.input, shorten(in.stream, 46)));
            statusContent.addView(text(tr("  订阅 ") + in.subs + "  ·  " + kbpsInput(shownBps) +
                    (in.signal > 0 ? tr("  ·  信号 ") + in.signal : ""), 10, DIM, false));
        }
    }

    /** 订阅详情：可断开该用户的连接（对应网页版 状态→连接→Cancel） */
    private void subDialog(final SubInfo s) {
        int matched = 0;
        for (TvhApi.ConnInfo c : connList) {
            if (s.username != null && s.username.equals(c.user)) matched++;
        }
        String msg = tr("用户") + "：" + s.username + "\n"
                + tr("客户端") + "：" + s.client + "\n"
                + tr("频道") + "：" + s.channel + "\n"
                + tr("服务") + "：" + s.service + "\n"
                + "profile：" + s.profile + "\n"
                + tr("码率") + "：" + kbps(s.inBps) + " / " + kbps(s.outBps) + "\n\n"
                + tr("该用户当前有") + " " + matched + " " + tr("条连接");
        dlg().setTitle(tr("订阅详情"))
                .setMessage(msg)
                .setPositiveButton(matched > 0 ? tr("断开该用户") : tr("断开全部连接"),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                int n = 0;
                                for (TvhApi.ConnInfo c : connList) {
                                    if (s.username != null && s.username.equals(c.user)) n++;
                                }
                                if (n > 0) cancelUserConnections(s.username);
                                else confirmCancelAll();
                            }
                        })
                .setNeutralButton(tr("断开全部连接"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { confirmCancelAll(); }
                })
                .setNegativeButton(tr("关闭"), null)
                .show();
    }

    private void cancelUserConnections(final String username) {
        progress.setVisibility(View.VISIBLE);
        bg.execute(new Runnable() {
            public void run() {
                int ok = 0, fail = 0;
                for (TvhApi.ConnInfo c : new ArrayList<TvhApi.ConnInfo>(connList)) {
                    if (username != null && username.equals(c.user)) {
                        try {
                            api.cancelConnection(c.id);
                            ok++;
                        } catch (Exception e) {
                            fail++;
                        }
                    }
                }
                final int fok = ok, ffail = fail;
                ui.post(new Runnable() {
                    public void run() {
                        progress.setVisibility(View.GONE);
                        toast(tr("已断开") + " " + fok + (ffail > 0 ? ("，" + tr("失败") + " " + ffail) : ""));
                        loadStatus(false);
                    }
                });
            }
        });
    }

    private void confirmCancelAll() {
        dlg().setTitle(tr("断开全部连接"))
                .setMessage(tr("断开所有客户端与 Tvheadend 的连接（包括你自己的设备），确定？"))
                .setPositiveButton(tr("确定"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        progress.setVisibility(View.VISIBLE);
                        bg.execute(new Runnable() {
                            public void run() {
                                try {
                                    api.cancelAllConnections();
                                    ui.post(new Runnable() {
                                        public void run() {
                                            progress.setVisibility(View.GONE);
                                            toast(tr("已断开全部连接"));
                                            loadStatus(false);
                                        }
                                    });
                                } catch (final Exception e) {
                                    ui.post(new Runnable() {
                                        public void run() {
                                            progress.setVisibility(View.GONE);
                                            toast(tr("断开失败：") + e.getMessage());
                                        }
                                    });
                                }
                            }
                        });
                    }
                })
                .setNegativeButton(tr("取消"), null)
                .show();
    }

    private String shorten(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    /** 订阅的 in/out：接口返回字节/秒，按 ÷125 换算成 kbit/s */
    private String kbps(double bytesPerSec) {
        if (bytesPerSec <= 0) return "0 kbps";
        return String.format(Locale.US, "%.0f kbps", bytesPerSec / 125.0);
    }

    /** 输入流的 bps：接口返回比特/秒，按 ÷1000 换算成 kbit/s */
    private String kbpsInput(long bitsPerSec) {
        if (bitsPerSec <= 0) return "0 kbps";
        return String.format(Locale.US, "%.0f kbps", bitsPerSec / 1000.0);
    }

    // ---------------- EPG 时间轴 ----------------

    private LinearLayout buildEpgView() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackgroundColor(BG);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        epgStatus = text(tr("拖动浏览，点节目可播放/录制"), 10, DIM, false);
        bar.addView(epgStatus, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(button(tr("现在"), new View.OnClickListener() {
            public void onClick(View view) { loadEpg(); }
        }));
        v.addView(bar);

        timeline = new EpgTimelineView(this);
        timeline.setListener(new EpgTimelineView.Listener() {
            public void onEventClick(final EpgEvent e) { epgEventMenu(e); }
        });
        v.addView(timeline, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return v;
    }

    private void loadEpg() {
        progress.setVisibility(View.VISIBLE);
        epgStatus.setText(tr("正在读取 EPG…"));
        final long now = System.currentTimeMillis() / 1000L;
        final long from = now - (now % 1800) - 1800;
        final long to = from + 6 * 3600;
        bg.execute(new Runnable() {
            public void run() {
                try {
                    final List<EpgEvent> evs = api.epgWindow(from, to);
                    ui.post(new Runnable() {
                        public void run() {
                            progress.setVisibility(View.GONE);
                            epgLoaded = true;
                            Map<String, Channel> byUuid = new LinkedHashMap<String, Channel>();
                            for (Channel c : all) byUuid.put(c.uuid, c);
                            List<Channel> rows = new ArrayList<Channel>();
                            HashSet<String> seen = new HashSet<String>();
                            for (EpgEvent e : evs) {
                                if (!seen.add(e.channelUuid)) continue;
                                Channel c = byUuid.get(e.channelUuid);
                                if (c == null) {
                                    c = new Channel();
                                    c.uuid = e.channelUuid;
                                    c.name = e.channelName;
                                    try { c.number = Integer.parseInt(e.channelNumber); } catch (Exception ignored) {}
                                }
                                rows.add(c);
                            }
                            Collections.sort(rows, new java.util.Comparator<Channel>() {
                                public int compare(Channel a, Channel b) {
                                    if (a.number != b.number) return a.number - b.number;
                                    return String.valueOf(a.name).compareToIgnoreCase(String.valueOf(b.name));
                                }
                            });
                            timeline.setData(rows, evs, from, to);
                            epgStatus.setText(tr("节目 ") + evs.size() + tr(" 条 · ") + rows.size() + tr(" 个频道 · ") +
                                    fmt(from) + " ~ " + fmt(to));
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        public void run() {
                            progress.setVisibility(View.GONE);
                            epgStatus.setText(tr("EPG 读取失败：") + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private String fmt(long sec) {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(sec * 1000));
    }

    private void epgEventMenu(final EpgEvent e) {
        String[] items = {tr("播放该频道"), tr("录制此节目"), tr("查看详情")};
        dlg()
                .setTitle((e.channelNumber.isEmpty() ? "" : e.channelNumber + " ") + e.channelName + "\n" + e.title)
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) playUuid(e.channelUuid, e.channelName);
                        else if (which == 1) recordEvent(e);
                        else showEventDetail(e);
                    }
                }).show();
    }

    private void showEventDetail(EpgEvent e) {
        String msg = fmt(e.start) + " - " + fmt(e.stop) + "\n" + e.channelName + "\n\n" +
                (e.title == null ? "" : e.title) + "\n" +
                (e.subtitle == null ? "" : e.subtitle) + "\n\n" +
                (e.description == null ? "" : e.description);
        dlg().setTitle(tr("节目详情")).setMessage(msg)
                .setPositiveButton(tr("关闭"), null).show();
    }

    private void recordEvent(final EpgEvent e) {
        progress.setVisibility(View.VISIBLE);
        bg.execute(new Runnable() {
            public void run() {
                try {
                    String cfg = api.dvrConfigUuid();
                    if (cfg.isEmpty()) throw new Exception(tr("服务器上没有可用的录制配置"));
                    api.recordEvent(e.eventId, cfg);
                    ui.post(new Runnable() {
                        public void run() {
                            progress.setVisibility(View.GONE);
                            toast(tr("已加入录制：") + e.title);
                            dvrLoaded = false;
                        }
                    });
                } catch (final Exception ex) {
                    ui.post(new Runnable() {
                        public void run() {
                            progress.setVisibility(View.GONE);
                            toast(tr("录制失败：") + ex.getMessage());
                        }
                    });
                }
            }
        });
    }

    // ---------------- 录制 ----------------

    private LinearLayout buildDvrView() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackgroundColor(BG);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), dp(6), dp(12), dp(8));
        dvrStatus = text("", 11, DIM, false);
        bar.addView(dvrStatus, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView addBtn = text("+ " + tr("新建录制"), 12, ACCENT, true);
        addBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
        addBtn.setBackground(roundBg(Theme.isDark() ? 0x2626C6DA : 0x3326C6DA, 16, false));
        addBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) { prepareCustomRecording(); }
        });
        bar.addView(addBtn);
        v.addView(bar);

        LinearLayout filterWrap = new LinearLayout(this);
        filterWrap.setPadding(dp(14), 0, dp(14), dp(4));
        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        dvrTabsBox = new LinearLayout(this);
        dvrTabsBox.setOrientation(LinearLayout.HORIZONTAL);
        dvrTabViews.clear();
        String[] tabNames = {tr("待录制"), tr("已完成"), tr("失败"), tr("已移除"),
                tr("自动录制"), tr("定时录制")};
        for (int i = 0; i < tabNames.length; i++) {
            final int idx = i;
            TextView tab = text(tabNames[i], 12, DIM, true);
            tab.setPadding(dp(13), dp(7), dp(13), dp(7));
            tab.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { setDvrTab(idx); }
            });
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tp.rightMargin = dp(6);
            dvrTabsBox.addView(tab, tp);
            dvrTabViews.add(tab);
        }
        tabScroll.addView(dvrTabsBox);
        filterWrap.addView(tabScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setDvrTab(0);
        v.addView(filterWrap);

        dvrList = new ListView(this);
        dvrList.setDividerHeight(1);
        dvrList.setDivider(new android.graphics.drawable.ColorDrawable(LINE));
        dvrList.setPadding(0, 0, 0, dp(8));
        dvrList.setClipToPadding(false);
        dvrList.setBackgroundColor(BG);
        dvrAdapter = new DvrAdapter();
        dvrList.setAdapter(dvrAdapter);
        dvrList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View view, int pos, long id) { dvrMenu(dvrItems.get(pos)); }
        });
        v.addView(dvrList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return v;
    }

    private void loadDvr() {
        progress.setVisibility(View.VISIBLE);
        dvrStatus.setText(tr("正在读取…"));
        final int tab = dvrTab;
        bg.execute(new Runnable() {
            public void run() {
                try {
                    final List<DvrEntry> list = new ArrayList<DvrEntry>();
                    final List<AutoRec> rules = new ArrayList<AutoRec>();
                    if (tab == 4 || tab == 5) {
                        rules.addAll(api.autorecs(tab == 5));
                    } else if (tab == 0) {
                        list.addAll(api.dvrUpcoming());
                    } else if (tab == 1) {
                        list.addAll(api.dvrFinished());
                    } else if (tab == 2) {
                        list.addAll(api.dvrFailed());
                    } else {
                        list.addAll(api.dvrRemoved());
                    }
                    if (dvrConfigList == null) {
                        try { dvrConfigList = api.dvrConfigs(); } catch (Exception ignored) {}
                    }
                    ui.post(new Runnable() {
                        public void run() {
                            progress.setVisibility(View.GONE);
                            dvrLoaded = true;
                            dvrItems.clear();
                            dvrItems.addAll(list);
                            recItems.clear();
                            recItems.addAll(rules);
                            if (tab >= 4) {
                                if (autoAdapter != null) autoAdapter.notifyDataSetChanged();
                            } else if (dvrAdapter != null) {
                                dvrAdapter.notifyDataSetChanged();
                            }
                            String[] names = {tr("待录制"), tr("已完成"), tr("失败"), tr("已移除"),
                                    tr("自动录制"), tr("定时录制")};
                            String extra;
                            if (tab >= 4) {
                                extra = "  ·  " + recItems.size() + tr(" 条规则");
                            } else if (tab == 1) {
                                long total = 0;
                                for (DvrEntry e : list) total += e.filesize;
                                extra = "  ·  " + list.size() + tr(" 条")
                                        + (total > 0 ? "  ·  " + tr("占用") + " " + fmtSize(total) : "");
                            } else {
                                extra = "  ·  " + list.size() + tr(" 条");
                            }
                            dvrStatus.setText(names[tab] + extra);
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        public void run() {
                            progress.setVisibility(View.GONE);
                            dvrStatus.setText(tr("读取失败：") + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void dvrMenu(final DvrEntry d) {
        List<String> items = new ArrayList<String>();
        final int tab = dvrTab;
        if (tab == 1) {
            items.add(tr("播放"));
            items.add(tr("下载到手机"));
            items.add(tr("删除录制"));
        } else if (tab == 2) {
            items.add(tr("播放"));
            items.add(tr("重新录制"));
            items.add(tr("下载到手机"));
            items.add(tr("删除录制"));
        } else if (tab == 3) {
            items.add(tr("重新录制"));
            items.add(tr("删除录制"));
        } else {
            items.add(tr("编辑"));
            items.add(tr("取消录制"));
            items.add(tr("停止录制"));
            items.add(tr("删除条目"));
        }
        final String[] arr = items.toArray(new String[0]);
        String head = d.title;
        if (d.errors > 0) head = head + "  ·  " + tr("错误") + " " + d.errors;
        dlg()
                .setTitle(head)
                .setItems(arr, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dlg, int which) {
                        String label = arr[which];
                        if (label.equals(tr("播放"))) {
                            startVlc(api.recordingUrl(d.uuid), d.title);
                        } else if (label.equals(tr("下载到手机"))) {
                            downloadRecording(d);
                        } else if (label.equals(tr("重新录制"))) {
                            rerecordEntry(d);
                        } else if (tab == 1 || tab == 2) {
                            dvrAction(3, d.uuid);
                        } else if (tab == 3) {
                            dvrAction(3, d.uuid);
                        } else if (which == 0) {
                            editEntry(d);
                        } else if (which == 1) {
                            dvrAction(1, d.uuid);
                        } else if (which == 2) {
                            dvrAction(2, d.uuid);
                        } else {
                            dvrAction(3, d.uuid);
                        }
                    }
                }).show();
    }

    /** 编辑待录制/录制中任务（标题、起止时间、提前/延后、启用） */
    private void editEntry(final DvrEntry d) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));

        final EditText title = field();
        title.setText(d.title);
        box.addView(text(tr("标题"), 12, DIM, false));
        box.addView(title);

        final SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        final long[] startMs = {d.start * 1000L};
        final long[] stopMs = {d.stop * 1000L};

        final TextView startTv = text(f.format(new Date(startMs[0])), 16, ACCENT, true);
        startTv.setPadding(dp(4), dp(10), 0, dp(10));
        box.addView(text(tr("开始时间"), 12, DIM, false));
        box.addView(startTv);
        startTv.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pickDateTime(startMs[0], new OnTime() {
                    public void onTime(long ms) {
                        startMs[0] = ms;
                        if (stopMs[0] <= ms) stopMs[0] = ms + 1800_000L;
                        startTv.setText(f.format(new Date(startMs[0])));
                        startTv.setTag(null);
                    }
                });
            }
        });

        final TextView stopTv = text(f.format(new Date(stopMs[0])), 16, ACCENT, true);
        stopTv.setPadding(dp(4), dp(10), 0, dp(10));
        box.addView(text(tr("结束时间"), 12, DIM, false));
        box.addView(stopTv);
        stopTv.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pickDateTime(stopMs[0], new OnTime() {
                    public void onTime(long ms) {
                        if (ms <= startMs[0]) {
                            toast(tr("结束时间必须晚于开始时间"));
                            return;
                        }
                        stopMs[0] = ms;
                        stopTv.setText(f.format(new Date(stopMs[0])));
                    }
                });
            }
        });

        final EditText preExtra = field();
        preExtra.setText(String.valueOf(d.startExtra / 60));
        preExtra.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(text(tr("提前开始（分钟）"), 12, DIM, false));
        box.addView(preExtra);

        final EditText postExtra = field();
        postExtra.setText(String.valueOf(d.stopExtra / 60));
        postExtra.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(text(tr("延后结束（分钟）"), 12, DIM, false));
        box.addView(postExtra);

        final CheckBox enabled = new CheckBox(this);
        enabled.setText(tr("启用该任务"));
        enabled.setTextColor(TEXT);
        enabled.setChecked(d.enabled);
        box.addView(enabled);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        dlg().setTitle(tr("编辑录制"))
                .setView(sv)
                .setPositiveButton(tr("保存"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dd, int w) {
                        int pre = 0, post = 0;
                        try { pre = Integer.parseInt(preExtra.getText().toString().trim()); } catch (Exception ignored) {}
                        try { post = Integer.parseInt(postExtra.getText().toString().trim()); } catch (Exception ignored) {}
                        if (stopMs[0] <= startMs[0]) {
                            toast(tr("结束时间必须晚于开始时间"));
                            return;
                        }
                        final long st = startMs[0] / 1000, en = stopMs[0] / 1000;
                        final String t = title.getText().toString().trim();
                        final int fpre = pre * 60, fpost = post * 60;
                        final boolean fen = enabled.isChecked();
                        progress.setVisibility(View.VISIBLE);
                        bg.execute(new Runnable() {
                            public void run() {
                                try {
                                    api.updateEntry(d.uuid, t, st, en, fpre, fpost, fen);
                                    ui.post(new Runnable() {
                                        public void run() {
                                            progress.setVisibility(View.GONE);
                                            toast(tr("已保存"));
                                            loadDvr();
                                        }
                                    });
                                } catch (final Exception e) {
                                    ui.post(new Runnable() {
                                        public void run() {
                                            progress.setVisibility(View.GONE);
                                            toast(tr("保存失败：") + e.getMessage());
                                        }
                                    });
                                }
                            }
                        });
                    }
                })
                .setNegativeButton(tr("取消"), null)
                .show();
    }

    private void rerecordEntry(final DvrEntry d) {
        progress.setVisibility(View.VISIBLE);
        bg.execute(new Runnable() {
            public void run() {
                try {
                    api.rerecord(d.uuid);
                    ui.post(new Runnable() {
                        public void run() {
                            progress.setVisibility(View.GONE);
                            toast(tr("已安排重新录制"));
                            loadDvr();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        public void run() {
                            progress.setVisibility(View.GONE);
                            toast(tr("操作失败：") + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    /** 自动录制 / 定时录制规则菜单 */
    private void autoRecMenu(final AutoRec a) {
        final String[] items = {a.enabled ? tr("停用规则") : tr("启用规则"), tr("删除规则"), tr("详情")};
        StringBuilder sb = new StringBuilder();
        sb.append(tr("名称")).append("：").append(a.name.isEmpty() ? a.title : a.name).append("\n");
        if (!a.title.isEmpty()) sb.append(tr("标题")).append("：").append(a.title).append("\n");
        if (!a.channelName.isEmpty()) sb.append(tr("频道")).append("：").append(a.channelName).append("\n");
        if (!a.daysOfWeek.isEmpty()) sb.append(tr("星期")).append("：").append(a.daysOfWeek).append("\n");
        if (a.start > 0) sb.append(tr("开始时间")).append("：").append(mdhm.format(new Date(a.start * 1000))).append("\n");
        if (!a.comment.isEmpty()) sb.append(tr("备注")).append("：").append(a.comment).append("\n");
        sb.append(tr("状态")).append("：").append(a.enabled ? tr("启用") : tr("停用"));
        dlg().setTitle(a.timerec ? tr("定时录制") : tr("自动录制"))
                .setMessage(sb.toString())
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dlg, int which) {
                        if (which == 0) autoToggle(a, !a.enabled);
                        else if (which == 1) autoDelete(a);
                    }
                }).show();
    }

    private void autoToggle(final AutoRec a, final boolean enable) {
        progress.setVisibility(View.VISIBLE);
        bg.execute(new Runnable() {
            public void run() {
                try {
                    api.setRuleEnabled(a.uuid, enable);
                    ui.post(new Runnable() {
                        public void run() {
                            progress.setVisibility(View.GONE);
                            toast(enable ? tr("已启用") : tr("已停用"));
                            loadDvr();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        public void run() {
                            progress.setVisibility(View.GONE);
                            toast(tr("操作失败：") + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void autoDelete(final AutoRec a) {
        progress.setVisibility(View.VISIBLE);
        bg.execute(new Runnable() {
            public void run() {
                try {
                    api.dvrDelete(a.uuid);
                    ui.post(new Runnable() {
                        public void run() {
                            progress.setVisibility(View.GONE);
                            toast(tr("已删除规则"));
                            loadDvr();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        public void run() {
                            progress.setVisibility(View.GONE);
                            toast(tr("操作失败：") + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    /** 用系统 DownloadManager 把录制文件下到手机「下载」目录（带 Basic 认证头） */
    private void downloadRecording(final DvrEntry d) {
        try {
            String clean = api.base + "/dvrfile/" + d.uuid;
            android.app.DownloadManager dm =
                    (android.app.DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            android.app.DownloadManager.Request req =
                    new android.app.DownloadManager.Request(android.net.Uri.parse(clean));
            String token = android.util.Base64.encodeToString(
                    (prefs.user + ":" + prefs.pass).getBytes("UTF-8"), android.util.Base64.NO_WRAP);
            req.addRequestHeader("Authorization", "Basic " + token);
            String base = (d.title == null || d.title.trim().isEmpty()) ? "recording" : d.title.trim();
            base = base.replaceAll("[\\\\/:*?\"<>|]", "_");
            String name = base + "-" + d.uuid.substring(0, 8) + ".ts";
            req.setTitle(base);
            req.setDescription("TvhGO");
            req.setNotificationVisibility(
                    android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, name);
            dm.enqueue(req);
            toast(tr("已开始下载"));
        } catch (Exception e) {
            toast(tr("下载失败：") + e.getMessage());
        }
    }

    private String fmtSize(long bytes) {
        if (bytes <= 0) return "0 MB";
        if (bytes >= 1073741824L) return String.format(Locale.US, "%.2f GB", bytes / 1073741824.0);
        return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
    }

    private void dvrAction(final int kind, final String uuid) {
        progress.setVisibility(View.VISIBLE);
        bg.execute(new Runnable() {
            public void run() {
                try {
                    if (kind == 1) api.dvrCancel(uuid);
                    else if (kind == 2) api.dvrStop(uuid);
                    else api.dvrDelete(uuid);
                    ui.post(new Runnable() {
                        public void run() {
                            progress.setVisibility(View.GONE);
                            toast(tr("操作完成"));
                            loadDvr();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        public void run() {
                            progress.setVisibility(View.GONE);
                            toast(tr("操作失败：") + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    // ---------- 自定义录制 ----------

    private void prepareCustomRecording() {
        if (all.isEmpty()) {
            toast(tr("频道列表还没加载完"));
            return;
        }
        if (dvrConfigList == null) {
            bg.execute(new Runnable() {
                public void run() {
                    try {
                        dvrConfigList = api.dvrConfigs();
                    } catch (Exception ignored) {}
                    ui.post(new Runnable() {
                        public void run() { showCustomRecordingDialog(); }
                    });
                }
            });
        } else {
            showCustomRecordingDialog();
        }
    }

    private interface OnTime {
        void onTime(long ms);
    }

    /** 日期 + 时间选择 */
    private void pickDateTime(long initialMs, final OnTime cb) {
        final java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(initialMs);
        new android.app.DatePickerDialog(this, new android.app.DatePickerDialog.OnDateSetListener() {
            public void onDateSet(android.widget.DatePicker dp, int y, int m, int d) {
                cal.set(java.util.Calendar.YEAR, y);
                cal.set(java.util.Calendar.MONTH, m);
                cal.set(java.util.Calendar.DAY_OF_MONTH, d);
                new android.app.TimePickerDialog(MainActivity.this, new android.app.TimePickerDialog.OnTimeSetListener() {
                    public void onTimeSet(android.widget.TimePicker tp, int h, int min) {
                        cal.set(java.util.Calendar.HOUR_OF_DAY, h);
                        cal.set(java.util.Calendar.MINUTE, min);
                        cal.set(java.util.Calendar.SECOND, 0);
                        cb.onTime(cal.getTimeInMillis());
                    }
                }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).show();
            }
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
    }

    private void showCustomRecordingDialog() {
        final List<String[]> configs = dvrConfigList == null ? new ArrayList<String[]>() : dvrConfigList;
        if (configs.isEmpty()) {
            toast(tr("服务器上没有可用的录制配置，请先在网页版 DVR 里建一个"));
            return;
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));

        final List<Channel> chItems = new ArrayList<Channel>(all);
        List<String> chLabels = new ArrayList<String>();
        for (Channel c : chItems) chLabels.add((c.number > 0 ? c.number + " " : "") + c.name);
        final int[] chSel = {0};
        final TextView chPicker = pickerButton(chLabels.isEmpty() ? "" : chLabels.get(0), null);
        chPicker.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showPicker(tr("频道"), chLabels, chSel[0], new OnPick() {
                    public void onPick(int index) {
                        chSel[0] = index;
                        chPicker.setText(chLabels.get(index));
                    }
                });
            }
        });
        box.addView(text(tr("频道"), 12, DIM, false));
        box.addView(chPicker, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final EditText title = field();
        title.setHint(tr("录制标题（可留空）"));
        box.addView(text(tr("标题"), 12, DIM, false));
        box.addView(title);

        final SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        long nowMs = System.currentTimeMillis();
        final long[] startMs = {((nowMs / 300000L) + 1) * 300000L};
        final long[] stopMs = {startMs[0] + 3600_000L};

        final TextView startTv = text(f.format(new Date(startMs[0])), 16, ACCENT, true);
        startTv.setPadding(dp(4), dp(10), 0, dp(10));
        box.addView(text(tr("开始时间"), 12, DIM, false));
        box.addView(startTv);
        startTv.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pickDateTime(startMs[0], new OnTime() {
                    public void onTime(long ms) {
                        startMs[0] = ms;
                        if (stopMs[0] <= ms) {
                            stopMs[0] = ms + 3600_000L;
                            startTv.setText(f.format(new Date(startMs[0])));
                            if (box.getTag() instanceof TextView) {
                                ((TextView) box.getTag()).setText(f.format(new Date(stopMs[0])));
                            }
                        }
                        startTv.setText(f.format(new Date(startMs[0])));
                    }
                });
            }
        });

        final TextView stopTv = text(f.format(new Date(stopMs[0])), 16, ACCENT, true);
        stopTv.setPadding(dp(4), dp(10), 0, dp(10));
        box.addView(text(tr("结束时间"), 12, DIM, false));
        box.addView(stopTv);
        box.setTag(stopTv);
        stopTv.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pickDateTime(stopMs[0], new OnTime() {
                    public void onTime(long ms) {
                        if (ms <= startMs[0]) {
                            toast(tr("结束时间必须晚于开始时间"));
                            return;
                        }
                        stopMs[0] = ms;
                        stopTv.setText(f.format(new Date(stopMs[0])));
                    }
                });
            }
        });

        final EditText pri = field();
        pri.setText("6");
        pri.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(text(tr("优先级 (pri)"), 12, DIM, false));
        box.addView(pri);

        final EditText preExtra = field();
        preExtra.setText("0");
        preExtra.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(text(tr("提前开始（分钟）"), 12, DIM, false));
        box.addView(preExtra);

        final EditText postExtra = field();
        postExtra.setText("0");
        postExtra.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(text(tr("延后结束（分钟）"), 12, DIM, false));
        box.addView(postExtra);

        List<String> cfgLabels = new ArrayList<String>();
        for (String[] c : configs) cfgLabels.add(c[1]);
        final int[] cfgSel = {0};
        final TextView cfgPicker = pickerButton(cfgLabels.isEmpty() ? "" : cfgLabels.get(0), null);
        cfgPicker.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showPicker(tr("录制配置"), cfgLabels, cfgSel[0], new OnPick() {
                    public void onPick(int index) {
                        cfgSel[0] = index;
                        cfgPicker.setText(cfgLabels.get(index));
                    }
                });
            }
        });
        box.addView(text(tr("录制配置"), 12, DIM, false));
        box.addView(cfgPicker, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView sv = new ScrollView(this);
        sv.addView(box);

        dlg()
                .setTitle(tr("新建录制"))
                .setView(sv)
                .setPositiveButton(tr("创建"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        long st = startMs[0] / 1000;
                        long en = stopMs[0] / 1000;
                        if (en <= st) {
                            toast(tr("结束时间必须晚于开始时间"));
                            return;
                        }
                        int p = 6, pre = 0, post = 0;
                        try { p = Integer.parseInt(pri.getText().toString().trim()); } catch (Exception ignored) {}
                        try { pre = Integer.parseInt(preExtra.getText().toString().trim()); } catch (Exception ignored) {}
                        try { post = Integer.parseInt(postExtra.getText().toString().trim()); } catch (Exception ignored) {}
                        Channel ch = chItems.get(chSel[0]);
                        String cfgUuid = configs.get(cfgSel[0])[0];
                        createCustom(st, en, ch, title.getText().toString().trim(), p, pre, post, cfgUuid);
                    }
                })
                .setNegativeButton(tr("取消"), null)
                .show();
    }

    private void createCustom(final long start, final long stop, final Channel ch, final String title,
                              final int pri, final int pre, final int post, final String cfgUuid) {
        progress.setVisibility(View.VISIBLE);
        bg.execute(new Runnable() {
            public void run() {
                try {
                    api.createRecording(start, stop, ch.uuid, ch.name, title, pri, pre, post, cfgUuid);
                    ui.post(new Runnable() {
                        public void run() {
                            progress.setVisibility(View.GONE);
                            toast(tr("已创建录制：") + (title.isEmpty() ? ch.name : title));
                            setDvrTab(0);
                            loadDvr();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        public void run() {
                            progress.setVisibility(View.GONE);
                            toast(tr("创建失败：") + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    // ---------------- 数据 ----------------

    // ---------------- 设置页 ----------------

    private LinearLayout settingsContent;

    private ScrollView buildSettingsView() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        settingsContent = new LinearLayout(this);
        settingsContent.setOrientation(LinearLayout.VERTICAL);
        settingsContent.setPadding(0, dp(4), 0, dp(16));
        sv.addView(settingsContent);
        return sv;
    }

    private LinearLayout cardGroup() {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setBackground(roundBg(CARD, 16));
        return g;
    }

    private void addGroupRow(LinearLayout group, String label, String value, View.OnClickListener l) {
        if (group.getChildCount() > 0) {
            View line = new View(this);
            line.setBackgroundColor(LINE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
            lp.leftMargin = dp(16);
            lp.rightMargin = dp(16);
            group.addView(line, lp);
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(14), dp(13));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(label, 14, TEXT, false));
        if (value != null && !value.isEmpty()) col.addView(text(value, 11, DIM, false));
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(text("›", 18, DIM, false));
        if (l != null) row.setOnClickListener(l);
        group.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    interface OnPick {
        void onPick(int index);
    }

    /** 自绘下拉按钮（替代原生 Spinner 的丑样式） */
    private TextView pickerButton(String value, View.OnClickListener l) {
        TextView t = text(value, 14, TEXT, false);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(14), dp(11), dp(12), dp(11));
        t.setBackground(roundBg(CARD, 14));
        t.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0);
        t.setCompoundDrawablePadding(dp(8));
        t.setOnClickListener(l);
        return t;
    }

    /** 主题化单选对话框 */
    private void showPicker(String title, List<String> items, int selected, final OnPick cb) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(6), 0, dp(6));
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            boolean sel = i == selected;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(20), dp(13), dp(18), dp(13));
            row.addView(text(items.get(i), 15, sel ? ACCENT : TEXT, sel),
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            if (sel) row.addView(text("✓", 15, ACCENT, true));
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    cb.onPick(idx);
                    if (pickerDialog != null) pickerDialog.dismiss();
                }
            });
            box.addView(row);
        }
        pickerDialog = dlg().setTitle(title).setView(sv)
                .setNegativeButton(tr("取消"), null).show();
    }

    private Sheet pickerDialog;

    /** 主题化输入框：文字/提示色跟随主题，圆角底，去掉系统下划线 */
    private EditText field() {
        EditText e = new EditText(this);
        e.setTextColor(TEXT);
        e.setHintTextColor(DIM);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setBackground(roundBg(Theme.isDark() ? 0x14FFFFFF : 0x0F000000, 12));
        e.setPadding(dp(14), dp(11), dp(14), dp(11));
        return e;
    }

    private TextView groupTitle(String s) {
        TextView t = text(s, 12, ACCENT, true);
        t.setPadding(dp(20), dp(16), dp(20), dp(8));
        return t;
    }

    private LinearLayout settingsRow(String label, String value, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(12), dp(10), dp(12));
        row.setBackground(roundBg(CARD, 14));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(label, 14, TEXT, false));
        if (value != null && !value.isEmpty()) col.addView(text(value, 11, DIM, false));
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(text("›", 18, DIM, false));
        if (l != null) row.setOnClickListener(l);
        return row;
    }

    private void refreshSettingsTab() {
        if (settingsContent == null) return;
        settingsContent.removeAllViews();
        ServerProfile p = prefs.cur();

        settingsContent.addView(groupTitle(tr("服务器")));
        LinearLayout g1 = cardGroup();
        addGroupRow(g1, tr("切换服务器"), p.name + " · " + p.hostLabel(), new View.OnClickListener() {
            public void onClick(View v) { showProfileSwitcher(); }
        });
        addGroupRow(g1, tr("编辑当前服务器"), p.user + " · " + p.profile, new View.OnClickListener() {
            public void onClick(View v) { showProfileEdit(prefs.current); }
        });
        addGroupRow(g1, tr("新增服务器"), "", new View.OnClickListener() {
            public void onClick(View v) { showProfileEdit(-1); }
        });
        settingsContent.addView(pad(g1));

        settingsContent.addView(groupTitle(tr("播放")));
        LinearLayout g2 = cardGroup();
        addGroupRow(g2, tr("默认串流配置"), prefs.profile, new View.OnClickListener() {
            public void onClick(View v) { chooseStreamProfile(); }
        });
        addGroupRow(g2, tr("播放器"),
                prefs.playerPkg == null || prefs.playerPkg.isEmpty() ? tr("系统选择器") : prefs.playerName(),
                new View.OnClickListener() {
                    public void onClick(View v) { choosePlayer(); }
                });
        addGroupRow(g2, tr("管理播放器"), prefs.players.size() + tr(" 个"), new View.OnClickListener() {
            public void onClick(View v) { showPlayerManager(); }
        });
        settingsContent.addView(pad(g2));

        settingsContent.addView(groupTitle(tr("频道列表")));
        LinearLayout g3 = cardGroup();
        addGroupRow(g3, tr("左侧显示"), prefs.showIcons ? tr("频道图标") : tr("频道号"), new View.OnClickListener() {
            public void onClick(View v) {
                dlg().setTitle(tr("列表左侧显示"))
                        .setItems(new String[]{tr("频道号"), tr("频道图标")}, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int which) {
                                prefs.showIcons = (which == 1);
                                prefs.save(MainActivity.this);
                                channelAdapter.notifyDataSetChanged();
                                refreshSettingsTab();
                            }
                        }).show();
            }
        });
        addGroupRow(g3, tr("副标题显示"), prefs.subtitleMode == 1 ? tr("当前节目") : tr("分组"), new View.OnClickListener() {
            public void onClick(View v) {
                dlg().setTitle(tr("副标题显示"))
                        .setItems(new String[]{tr("分组"), tr("当前节目")}, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int which) {
                                prefs.subtitleMode = which;
                                prefs.save(MainActivity.this);
                                if (which == 1) loadNowPlaying();
                                channelAdapter.notifyDataSetChanged();
                                refreshSettingsTab();
                            }
                        }).show();
            }
        });
        settingsContent.addView(pad(g3));

        settingsContent.addView(groupTitle(tr("外观")));
        LinearLayout g4 = cardGroup();
        addGroupRow(g4, tr("主题"),
                prefs.themeMode == 1 ? tr("深色") : (prefs.themeMode == 2 ? tr("浅色") : tr("跟随系统")),
                new View.OnClickListener() {
                    public void onClick(View v) { chooseTheme(); }
                });
        addGroupRow(g4, tr("界面语言"), langName(prefs.lang), new View.OnClickListener() {
            public void onClick(View v) { chooseLanguage(); }
        });
        settingsContent.addView(pad(g4));

        settingsContent.addView(groupTitle(tr("其它")));
        LinearLayout g5 = cardGroup();
        addGroupRow(g5, tr("打开 Tvheadend 网页后台"), "", new View.OnClickListener() {
            public void onClick(View v) { openWebUi(); }
        });
        String verName = "";
        try {
            verName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        addGroupRow(g5, tr("关于"), "TvhGO " + (verName == null ? "" : verName) + " · " + getPackageName(), null);
        settingsContent.addView(pad(g5));
    }

    private LinearLayout pad(View v) {
        LinearLayout w = new LinearLayout(this);
        w.setOrientation(LinearLayout.VERTICAL);
        w.setPadding(dp(14), 0, dp(14), 0);
        w.addView(v, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return w;
    }

    /** 只做切换的配置列表（不含编辑/新增入口） */
    private void showProfileSwitcher() {
        List<String> labels = new ArrayList<String>();
        for (int i = 0; i < prefs.profiles.size(); i++) {
            ServerProfile sp = prefs.profiles.get(i);
            labels.add((i == prefs.current ? "● " : "○ ") + sp.name);
        }
        dlg()
                .setTitle(tr("切换服务器"))
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (which != prefs.current) switchProfile(which);
                    }
                })
                .setNegativeButton(tr("取消"), null)
                .show();
    }

    private List<String[]> streamProfiles = null;

    /** 从服务器读取 profile 列表供选择 */
    private void chooseStreamProfile() {
        bg.execute(new Runnable() {
            public void run() {
                try {
                    streamProfiles = api.profiles();
                } catch (Exception ignored) {
                }
                ui.post(new Runnable() {
                    public void run() {
                        if (streamProfiles == null || streamProfiles.isEmpty()) {
                            toast(tr("读取 profile 失败，请检查权限"));
                            return;
                        }
                        showProfileChoice();
                    }
                });
            }
        });
    }

    private void showProfileChoice() {
        List<String> labels = new ArrayList<String>();
        int sel = -1;
        for (int i = 0; i < streamProfiles.size(); i++) {
            String name = streamProfiles.get(i)[0];
            labels.add(name);
            if (name.equals(prefs.profile)) sel = i;
        }
        dlg()
                .setTitle(tr("拉流 profile"))
                .setSingleChoiceItems(labels.toArray(new String[0]), sel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        prefs.profile = streamProfiles.get(which)[0];
                        prefs.profileChosen = true;
                        prefs.save(MainActivity.this);
                        api = TvhApi.from(prefs);
                        d.dismiss();
                        refreshSettingsTab();
                    }
                })
                .setNegativeButton(tr("取消"), null)
                .show();
    }

    private String langName(int l) {
        switch (l) {
            case 1: return tr("繁体中文");
            case 2: return tr("英语");
            case 3: return tr("日语");
            default: return tr("简体中文");
        }
    }

    private static boolean isSystemNight(android.content.Context c) {
        int mode = c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private Sheet.Builder dlg() {
        return new Sheet.Builder(this);
    }

    private void chooseTheme() {
        final String[] names = {tr("跟随系统"), tr("深色"), tr("浅色")};
        dlg()
                .setTitle(tr("主题"))
                .setSingleChoiceItems(names, prefs.themeMode, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        prefs.themeMode = which;
                        prefs.save(MainActivity.this);
                        d.dismiss();
                        recreate();
                    }
                })
                .setNegativeButton(tr("取消"), null)
                .show();
    }

    /** 切换播放器（含“系统选择器”） */
    private void choosePlayer() {
        final java.util.List<String> labels = new java.util.ArrayList<String>();
        labels.add(tr("系统选择器"));
        for (Prefs.Player p : prefs.players) labels.add(p.name + "  ·  " + p.pkg);
        int sel = 0;
        for (int i = 0; i < prefs.players.size(); i++) {
            if (prefs.players.get(i).pkg.equals(prefs.playerPkg)) sel = i + 1;
        }
        showPicker(tr("播放器"), labels, sel, new OnPick() {
            public void onPick(int index) {
                prefs.playerPkg = index == 0 ? "" : prefs.players.get(index - 1).pkg;
                prefs.save(MainActivity.this);
                refreshSettingsTab();
            }
        });
    }

    /** 管理外部播放器：新增 / 编辑 / 删除 */
    private void showPlayerManager() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(6), dp(8), dp(6));
        for (int i = 0; i < prefs.players.size(); i++) {
            final int idx = i;
            Prefs.Player pl = prefs.players.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            row.setBackground(roundBg(CARD, 12));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(text(pl.name, 15, TEXT, false));
            col.addView(text(pl.pkg, 11, DIM, false));
            row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(text("›", 18, DIM, false));
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (pickerDialog != null) pickerDialog.dismiss();
                    editPlayer(idx);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            box.addView(row, lp);
        }
        TextView add = text("+ " + tr("添加播放器"), 14, ACCENT, true);
        add.setGravity(Gravity.CENTER);
        add.setPadding(dp(12), dp(12), dp(12), dp(12));
        add.setBackground(roundBg(Theme.isDark() ? 0x1F26C6DA : 0x3326C6DA, 12, false));
        add.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (pickerDialog != null) pickerDialog.dismiss();
                editPlayer(-1);
            }
        });
        box.addView(add, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        pickerDialog = dlg().setTitle(tr("外部播放器")).setView(sv)
                .setNegativeButton(tr("关闭"), null).show();
    }

    private void editPlayer(final int idx) {
        final boolean isNew = idx < 0;
        Prefs.Player src = isNew ? new Prefs.Player("", "") : prefs.players.get(idx);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));
        final EditText name = field();
        name.setText(src.name);
        name.setHint(tr("例如：VLC / MX Player"));
        box.addView(text(tr("名称"), 12, DIM, false));
        box.addView(name);
        final EditText pkg = field();
        pkg.setText(src.pkg);
        pkg.setHint("org.videolan.vlc");
        box.addView(text(tr("包名"), 12, DIM, false));
        box.addView(pkg);
        pickerDialog = dlg().setTitle(isNew ? tr("添加播放器") : tr("编辑播放器"))
                .setView(box)
                .setPositiveButton(tr("保存"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String nm = name.getText().toString().trim();
                        String pk = pkg.getText().toString().trim();
                        if (pk.isEmpty()) {
                            toast(tr("包名不能为空"));
                            return;
                        }
                        Prefs.Player pl = new Prefs.Player(nm.isEmpty() ? pk : nm, pk);
                        if (isNew) prefs.players.add(pl);
                        else prefs.players.set(idx, pl);
                        if (prefs.playerPkg == null || prefs.playerPkg.isEmpty() || isNew) {
                            prefs.playerPkg = pk;
                        }
                        prefs.save(MainActivity.this);
                        refreshSettingsTab();
                    }
                })
                .setNeutralButton(isNew ? null : tr("删除"), isNew ? null : new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        if (prefs.players.size() <= 1) {
                            toast(tr("至少保留一个播放器"));
                            return;
                        }
                        Prefs.Player removed = prefs.players.remove(idx);
                        if (removed.pkg.equals(prefs.playerPkg)) prefs.playerPkg = prefs.players.get(0).pkg;
                        prefs.save(MainActivity.this);
                        refreshSettingsTab();
                    }
                })
                .show();
    }

    private void chooseLanguage() {
        final String[] names = {tr("简体中文"), tr("繁体中文"), tr("英语"), tr("日语")};
        dlg()
                .setTitle(tr("界面语言"))
                .setSingleChoiceItems(names, prefs.lang, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        prefs.lang = which;
                        prefs.save(MainActivity.this);
                        d.dismiss();
                        recreate();
                    }
                })
                .setNegativeButton(tr("取消"), null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (Sheet.closeTop()) return;
        // 返回键逐级回退，而不是直接退出
        if (tab != TAB_CH) {
            switchTab(TAB_CH);
            return;
        }
        if (tagFilter != null) {
            tagFilter = null;
            if (tagPicker != null) {
                tagFilter = null;
                tagPicker.setText(tr("全部频道"));
            }
            filterChannels();
            return;
        }
        if (query != null && !query.isEmpty()) {
            query = "";
            filterChannels();
            return;
        }
        dlg()
                .setTitle(tr("退出"))
                .setMessage(tr("确定要退出应用吗？"))
                .setPositiveButton(tr("退出"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { finish(); }
                })
                .setNegativeButton(tr("取消"), null)
                .show();
    }

    /** 拉取“全频道正在播”用于副标题 */
    private final Map<String, String> nowPlaying = new LinkedHashMap<String, String>();

    private void loadNowPlaying() {
        bg.execute(new Runnable() {
            public void run() {
                try {
                    final List<EpgEvent> evs = api.epgNow();
                    ui.post(new Runnable() {
                        public void run() {
                            nowPlaying.clear();
                            for (EpgEvent e : evs) {
                                if (e.channelUuid != null && e.title != null) nowPlaying.put(e.channelUuid, e.title);
                            }
                            if (channelAdapter != null) channelAdapter.notifyDataSetChanged();
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        });
    }

    private interface OnText {
        void onText(String s);
    }

    private void editTextDialog(String title, String initial, final OnText cb) {
        final EditText et = field();
        et.setText(initial);
        dlg()
                .setTitle(title)
                .setView(et)
                .setPositiveButton(tr("保存"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { cb.onText(et.getText().toString()); }
                })
                .setNegativeButton(tr("取消"), null)
                .show();
    }

    private void loadChannels() {
        ImageLoader.get().configure(api.base, prefs.user, prefs.pass);
        progress.setVisibility(View.VISIBLE);
        statusLine.setText(tr("正在连接…"));
        bg.execute(new Runnable() {
            public void run() {
                try {
                    final String info = api.serverInfo();
                    final Map<String, String> tags = api.tags();
                    final List<Channel> chs = api.channels();
                    try {
                        streamProfiles = api.profiles();
                    } catch (Exception ignored) {
                    }
                    boolean hasProf = false;
                    if (streamProfiles != null) {
                        for (String[] pr : streamProfiles) {
                            if (pr[0].equals(prefs.profile)) { hasProf = true; break; }
                        }
                    }
                    if (!hasProf && streamProfiles != null && !streamProfiles.isEmpty()) {
                        String fallback = streamProfiles.get(0)[0];
                        for (String[] pr : streamProfiles) {
                            if ("pass".equals(pr[0])) { fallback = pr[0]; break; }
                        }
                        prefs.profile = fallback;
                        prefs.save(MainActivity.this);
                        api = TvhApi.from(prefs);
                    }
                    ui.post(new Runnable() {
                        public void run() {
                            serverInfo = info;
                            tagNames = tags;
                            all.clear();
                            all.addAll(chs);
                            fillTagSpinner();
                            filterChannels();
                            progress.setVisibility(View.GONE);
                            epgLoaded = false;
                            dvrConfigList = null;
                            if (prefs.subtitleMode == 1) loadNowPlaying();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        public void run() {
                            progress.setVisibility(View.GONE);
                            statusLine.setText(tr("连接失败：") + e.getMessage());
                            toast(e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void fillTagSpinner() {
        tagLabels.clear();
        spinnerKeys.clear();
        tagLabels.add(tr("全部频道"));
        spinnerKeys.add(null);
        for (Map.Entry<String, String> e : tagNames.entrySet()) {
            tagLabels.add(e.getValue());
            spinnerKeys.add(e.getKey());
        }
        if (tagPicker != null) {
            tagPicker.setText(tagFilter == null ? tr("全部频道") : (tagNames.containsKey(tagFilter) ? tagNames.get(tagFilter) : tr("全部频道")));
        }
    }

    private void showTagPicker() {
        int sel = 0;
        if (tagFilter != null) {
            for (int i = 0; i < spinnerKeys.size(); i++) {
                if (tagFilter.equals(spinnerKeys.get(i))) { sel = i; break; }
            }
        }
        showPicker(tr("选择分组"), tagLabels, sel, new OnPick() {
            public void onPick(int index) {
                tagFilter = index == 0 ? null : spinnerKeys.get(index);
                if (tagPicker != null) tagPicker.setText(tagLabels.get(index));
                filterChannels();
            }
        });
    }

    /** 录制页切换分类 */
    private void setDvrTab(int idx) {
        dvrTab = idx;
        for (int i = 0; i < dvrTabViews.size(); i++) {
            boolean sel = i == idx;
            dvrTabViews.get(i).setTextColor(sel ? ACCENT : DIM);
            dvrTabViews.get(i).setBackground(sel
                    ? roundBg(Theme.isDark() ? 0x2626C6DA : 0x3326C6DA, 16, false) : null);
        }
        if (dvrList != null) {
            if (idx >= 4) {
                if (autoAdapter == null) autoAdapter = new AutoRecAdapter();
                dvrList.setAdapter(autoAdapter);
            } else if (dvrAdapter != null) {
                dvrList.setAdapter(dvrAdapter);
            }
        }
        if (dvrLoaded) loadDvr();
    }

    private void filterChannels() {
        shown.clear();
        String q = query.toLowerCase();
        for (Channel c : all) {
            String name = String.valueOf(c.name);
            if (!q.isEmpty() && !name.toLowerCase().contains(q) && !String.valueOf(c.number).contains(q)) continue;
            if (tagFilter != null && !c.tags.contains(tagFilter)) continue;
            shown.add(c);
        }
        if (channelAdapter != null) channelAdapter.notifyDataSetChanged();
        if (statusLine != null) {
            statusLine.setText((serverInfo.isEmpty() ? "Tvheadend" : serverInfo) + tr(" · 共 ") + all.size()
                    + tr(" 个频道 · 显示 ") + shown.size());
        }
    }

    // ---------------- 播放 ----------------

    private void play(Channel c) {
        if (!c.enabled) {
            toast(tr("该频道已禁用，先启用再播放"));
            return;
        }
        playUuid(c.uuid, c.name);
    }

    /** 点频道：进入详情页（EPG 列表 + 录制 + 右上角串流） */
    private void openDetail(Channel c) {
        Intent i = new Intent(this, ChannelDetailActivity.class);
        i.putExtra("uuid", c.uuid);
        i.putExtra("name", c.name);
        i.putExtra("number", c.number);
        startActivity(i);
    }

    private void playUuid(String uuid, String title) {
        startVlc(api.streamUrlForUuid(uuid, prefs.profileFor(uuid)), title);
    }

    private void startVlc(String url, String title) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(Uri.parse(url), "video/*");
        i.putExtra("title", title);
        if (prefs.playerPkg != null && !prefs.playerPkg.isEmpty()) {
            i.setPackage(prefs.playerPkg);
            try {
                startActivity(i);
                return;
            } catch (ActivityNotFoundException e) {
                toast(tr("未找到该播放器，改用系统选择器"));
            }
        }
        Intent plain = new Intent(Intent.ACTION_VIEW);
        plain.setDataAndType(Uri.parse(url), "video/*");
        try {
            startActivity(Intent.createChooser(plain, tr("选择播放器")));
        } catch (ActivityNotFoundException e) {
            toast(tr("没有可用的播放器，请先安装 VLC"));
        }
    }

    private void channelMenu(final Channel c) {
        final String[] items = {tr("播放"), tr("复制串流链接"), tr("查看节目单 (EPG)"),
                tr("编辑频道"), tr("复制频道 ID")};
        dlg()
                .setTitle(c.number + "  " + c.name)
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        switch (which) {
                            case 0: play(c); break;
                            case 1: showStreamLink(c); break;
                            case 2: switchTab(TAB_EPG); break;
                            case 3: showEdit(c); break;
                            case 4: copy(c.uuid); break;
                        }
                    }
                }).show();
    }

    /** 显示 / 复制带账号密码的串流链接 */
    private void showStreamLink(final Channel c) {
        final String url = api.streamUrl(c);
        dlg()
                .setTitle(tr("串流链接 · ") + c.name)
                .setMessage(url)
                .setPositiveButton(tr("复制链接"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { copy(url); }
                })
                .setNegativeButton(tr("关闭"), null)
                .show();
    }

    private void showEdit(final Channel c) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));

        final EditText name = field();
        name.setText(c.name);
        box.addView(text(tr("名称"), 12, DIM, false));
        box.addView(name);

        final EditText number = field();
        number.setText(String.valueOf(c.number));
        number.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(text(tr("频道号"), 12, DIM, false));
        box.addView(number);


        dlg()
                .setTitle(tr("编辑频道"))
                .setView(box)
                .setPositiveButton(tr("保存"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        final String nm = name.getText().toString().trim();
                        int num;
                        try { num = Integer.parseInt(number.getText().toString().trim()); } catch (Exception e) { num = c.number; }
                        final int fn = num;
                        final boolean en = c.enabled;
                        bg.execute(new Runnable() {
                            public void run() {
                                try {
                                    api.updateChannel(c.uuid, nm, fn, en);
                                    ui.post(new Runnable() {
                                        public void run() { toast(tr("已保存")); loadChannels(); }
                                    });
                                } catch (final Exception e) {
                                    ui.post(new Runnable() { public void run() { toast(tr("保存失败：") + e.getMessage()); } });
                                }
                            }
                        });
                    }
                })
                .setNegativeButton(tr("取消"), null)
                .show();
    }

    private void openWebUi() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(prefs.base())));
        } catch (Exception e) {
            toast(tr("没有可用的浏览器"));
        }
    }

    private void copy(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("tvh", s));
        toast(tr("已复制"));
    }

    private void showSettings(final boolean firstRun) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));

        final EditText server = field();
        server.setText(prefs.server);
        server.setHint("http://192.168.1.10:9981");
        box.addView(text(tr("服务器地址"), 12, DIM, false));
        box.addView(server);

        final EditText user = field();
        user.setText(prefs.user);
        box.addView(text(tr("用户名"), 12, DIM, false));
        box.addView(user);

        final EditText pass = field();
        pass.setText(prefs.pass);
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(text(tr("密码"), 12, DIM, false));
        box.addView(pass);




        Sheet.Builder b = dlg()
                .setTitle(firstRun ? tr("首次使用：填写服务器") : (tr("设置 · ") + prefs.cur().name))
                .setView(box)
                .setPositiveButton(tr("保存"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        prefs.server = server.getText().toString().trim();
                        prefs.user = user.getText().toString().trim();
                        prefs.pass = pass.getText().toString();
                        prefs.save(MainActivity.this);
                        api = TvhApi.from(prefs);
                        resetData();
                        updateTitle();
                        loadChannels();
                    }
                })
                .setNeutralButton(tr("服务器配置"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { showProfileSwitcher(); }
                });
        if (!firstRun) b.setNegativeButton(tr("取消"), null);
        b.show();
    }

    // ---------------- 多服务器配置 ----------------

    private void switchProfile(int idx) {
        prefs.switchTo(idx);
        prefs.save(MainActivity.this);
        api = TvhApi.from(prefs);
        resetData();
        updateTitle();
        toast(tr("已切换到配置：") + prefs.cur().name);
        switchTab(TAB_CH);
        loadChannels();
    }

    private void deleteProfile(final int idx) {
        if (prefs.profiles.size() <= 1) {
            toast(tr("至少要保留一套配置"));
            return;
        }
        final ServerProfile p = prefs.profiles.get(idx);
        dlg()
                .setTitle(tr("删除配置"))
                .setMessage(tr("确定删除「") + p.name + "」？")
                .setPositiveButton(tr("删除"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        prefs.profiles.remove(idx);
                        if (prefs.current >= prefs.profiles.size()) prefs.current = prefs.profiles.size() - 1;
                        prefs.syncFromProfile();
                        prefs.save(MainActivity.this);
                        api = TvhApi.from(prefs);
                        resetData();
                        updateTitle();
                        toast(tr("已删除，当前配置：") + prefs.cur().name);
                        loadChannels();
                    }
                })
                .setNegativeButton(tr("取消"), null)
                .show();
    }

    private void showProfileEdit(final int idx) {
        final boolean isNew = idx < 0;
        final ServerProfile src = isNew ? new ServerProfile() : prefs.profiles.get(idx);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));

        final EditText name = field();
        name.setHint(tr("例如：家里 / 机房"));
        name.setText(isNew ? (tr("配置 ") + (prefs.profiles.size() + 1)) : src.name);
        box.addView(text(tr("名称"), 12, DIM, false));
        box.addView(name);

        final EditText server = field();
        server.setText(src.server);
        server.setHint("http://192.168.1.10:9981");
        box.addView(text(tr("服务器地址"), 12, DIM, false));
        box.addView(server);

        final EditText user = field();
        user.setText(src.user);
        box.addView(text(tr("用户名"), 12, DIM, false));
        box.addView(user);

        final EditText pass = field();
        pass.setText(src.pass);
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(text(tr("密码"), 12, DIM, false));
        box.addView(pass);


        ScrollView sv = new ScrollView(this);
        sv.addView(box);

        dlg()
                .setTitle(isNew ? tr("新增配置") : tr("编辑配置"))
                .setView(sv)
                .setPositiveButton(tr("保存"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String nm = name.getText().toString().trim();
                        if (nm.isEmpty()) nm = tr("配置 ") + (prefs.profiles.size() + 1);
                        String sv2 = server.getText().toString().trim();
                        String u = user.getText().toString().trim();
                        String pw = pass.getText().toString();
                        String pf = src.profile;
                        if (isNew) {
                            prefs.profiles.add(new ServerProfile(nm, sv2, u, pw, pf));
                            prefs.current = prefs.profiles.size() - 1;
                            prefs.syncFromProfile();
                        } else {
                            ServerProfile p = prefs.profiles.get(idx);
                            p.name = nm;
                            p.server = sv2;
                            p.user = u;
                            p.pass = pw;
                            p.profile = pf;
                            if (idx == prefs.current) prefs.syncFromProfile();
                        }
                        prefs.save(MainActivity.this);
                        api = TvhApi.from(prefs);
                        resetData();
                        updateTitle();
                        toast(tr("已保存配置：") + nm);
                        switchTab(TAB_CH);
                        loadChannels();
                    }
                })
                .setNegativeButton(tr("取消"), null)
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ---------------- 适配器 ----------------

    private class ChannelAdapter extends BaseAdapter {
        public int getCount() { return shown.size(); }
        public Object getItem(int i) { return shown.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int pos, View convert, ViewGroup parent) {
            Channel c = shown.get(pos);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(18), dp(12), dp(14), dp(12));
            row.setBackgroundColor(BG);

            if (prefs.showIcons) {
                final FrameLayout box = new FrameLayout(MainActivity.this);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(42), dp(42));
                ip.rightMargin = dp(12);
                box.setLayoutParams(ip);
                final TextView fallback = text(c.number > 0 ? String.valueOf(c.number) : "\u2014", 11, DIM, true);
                fallback.setGravity(Gravity.CENTER);
                box.addView(fallback, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                final ImageView iv = new ImageView(MainActivity.this);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                box.addView(iv, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                final String path = c.iconUrl == null ? "" : c.iconUrl;
                iv.setTag(path);
                if (!path.isEmpty()) {
                    ImageLoader.get().load(path, new ImageLoader.Callback() {
                        public void onLoaded(android.graphics.Bitmap bmp) {
                            if (path.equals(iv.getTag())) {
                                iv.setImageBitmap(bmp);
                                fallback.setVisibility(View.GONE);
                            }
                        }
                    });
                }
                row.addView(box);
            } else {
                TextView num = text(c.number > 0 ? String.valueOf(c.number) : "\u2014", 12, DIM, true);
                num.setTypeface(Typeface.MONOSPACE);
                num.setGravity(Gravity.CENTER);
                num.setBackground(roundBg(Theme.isDark() ? 0x14FFFFFF : 0x0F000000, 10, false));
                LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(dp(46), dp(32));
                np.rightMargin = dp(12);
                row.addView(num, np);
            }

            LinearLayout col = new LinearLayout(MainActivity.this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(text(String.valueOf(c.name), 16, c.enabled ? TEXT : DIM, true));
            String sub;
            if (prefs.subtitleMode == 1) {
                String now = nowPlaying.get(c.uuid);
                sub = (now == null || now.isEmpty()) ? "\u2014" : now;
            } else {
                StringBuilder tags = new StringBuilder();
                int n = 0;
                for (String t : c.tags) {
                    String tv = tagNames.get(t);
                    if (tv == null) continue;
                    if (n++ > 0) tags.append(" \u00b7 ");
                    tags.append(tv);
                    if (n >= 2) break;
                }
                sub = tags.toString();
            }
            if (!sub.isEmpty()) col.addView(text(sub, 11, DIM, false));
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(text("\u203a", 18, DIM, false));
            return row;
        }
    }

    private final SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.CHINA);
    private final SimpleDateFormat mdhm = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);

    private class AutoRecAdapter extends BaseAdapter {
        public int getCount() { return recItems.size(); }

        public Object getItem(int i) { return recItems.get(i); }

        public long getItemId(int i) { return i; }

        public View getView(int pos, View convert, ViewGroup parent) {
            AutoRec a = recItems.get(pos);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(18), dp(13), dp(14), dp(13));
            row.setBackgroundColor(BG);

            LinearLayout top = new LinearLayout(MainActivity.this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(text(a.timerec ? tr("定时录制") : tr("自动录制"), 10, DIM, false),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView st = text(a.enabled ? tr("启用") : tr("停用"), 9, a.enabled ? ACCENT : WARN, true);
            st.setPadding(dp(6), dp(2), dp(6), dp(2));
            st.setBackground(roundBg(a.enabled ? 0x1F26C6DA : 0x1FFF7043, 8, false));
            top.addView(st);
            row.addView(top);

            TextView title = text(a.name.isEmpty() ? a.title : a.name, 15, TEXT, true);
            title.setPadding(0, dp(3), 0, dp(2));
            row.addView(title);

            StringBuilder meta = new StringBuilder();
            if (!a.channelName.isEmpty()) meta.append(a.channelName);
            if (!a.daysOfWeek.isEmpty()) {
                if (meta.length() > 0) meta.append("  ·  ");
                meta.append(a.daysOfWeek);
            }
            if (a.start > 0) {
                if (meta.length() > 0) meta.append("  ·  ");
                meta.append(mdhm.format(new Date(a.start * 1000)));
            }
            if (meta.length() > 0) row.addView(text(meta.toString(), 11, DIM, false));
            return row;
        }
    }

    private class DvrAdapter extends BaseAdapter {
        public int getCount() { return dvrItems.size(); }
        public Object getItem(int i) { return dvrItems.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int pos, View convert, ViewGroup parent) {
            DvrEntry d = dvrItems.get(pos);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(18), dp(13), dp(14), dp(13));
            row.setBackgroundColor(BG);

            String time = mdhm.format(new Date(d.start * 1000)) + " - " + hm.format(new Date(d.stop * 1000));
            LinearLayout top = new LinearLayout(MainActivity.this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(text(time, 10, DIM, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (!d.finished && d.schedStatus != null && d.schedStatus.contains("recording")) {
                TextView live = text(tr("录制中"), 9, WARN, true);
                live.setPadding(dp(6), dp(2), dp(6), dp(2));
                live.setBackground(roundBg(0x1FFF7043, 8, false));
                top.addView(live);
            }
            row.addView(top);

            TextView title = text(d.title, 15, TEXT, true);
            title.setPadding(0, dp(3), 0, dp(2));
            row.addView(title);

            StringBuilder meta = new StringBuilder();
            meta.append(d.channelName);
            if (d.finished) {
                if (d.filesize > 0) meta.append("  ·  ").append(fmtSize(d.filesize));
                if (d.errors > 0) meta.append("  ·  ").append(tr("错误")).append(" ").append(d.errors);
            } else {
                String st = d.schedStatus.isEmpty() ? d.status : d.schedStatus;
                if (st != null && !st.isEmpty()) meta.append("  ·  ").append(st);
                if (d.autorec != null && !d.autorec.isEmpty()) meta.append("  ·  ").append(tr("自动录制"));
            }
            row.addView(text(meta.toString(), 11, DIM, false));
            return row;
        }
    }
}
