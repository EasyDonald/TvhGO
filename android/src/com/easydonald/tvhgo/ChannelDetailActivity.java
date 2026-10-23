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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 频道详情：按天分栏的 EPG 列表 + 录制 + 右上角串流/复制链接 */
public class ChannelDetailActivity extends Activity {

    private Prefs prefs;
    private TvhApi api;
    private String uuid = "";
    private String channelName = "";
    private int number = 0;

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final List<EpgEvent> allEvents = new ArrayList<EpgEvent>();
    private final List<EpgEvent> dayEvents = new ArrayList<EpgEvent>();
    private final List<String> days = new ArrayList<String>();
    private String selectedDay = "";
    private final Set<String> booked = new HashSet<String>();
    private List<String[]> profiles = new ArrayList<String[]>();

    private EpgAdapter adapter;
    private TextView statusLine;
    private ProgressBar progress;
    private LinearLayout dayTabs;


    private final SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.CHINA);
    private final SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
    private final SimpleDateFormat dayShow = new SimpleDateFormat("MM-dd", Locale.CHINA);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Prefs.load(this);
        I18n.setLang(prefs.lang);
        Theme.apply(prefs.themeMode, isSystemNight(this));
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Theme.BG));
        api = TvhApi.from(prefs);
        uuid = getIntent().getStringExtra("uuid");
        channelName = getIntent().getStringExtra("name");
        number = getIntent().getIntExtra("number", 0);
        if (channelName == null) channelName = "";
        buildUi();
        load();
    }

    private static boolean isSystemNight(android.content.Context c) {
        int mode = c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private Sheet.Builder dlg() {
        return new Sheet.Builder(this);
    }

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
        b.setTextSize(12);
        b.setOnClickListener(l);
        return b;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(0), dp(0), dp(0), dp(6));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(8), dp(10), dp(4));

        final int chipBg = Theme.isDark() ? 0x14FFFFFF : 0x0F000000;

        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setColorFilter(TEXT);
        back.setScaleType(ImageView.ScaleType.FIT_CENTER);
        back.setBackground(roundBg(chipBg, 20, false));
        back.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(34), dp(34));
        blp.rightMargin = dp(6);
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        top.addView(back, blp);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setPadding(dp(4), 0, dp(6), 0);
        titleCol.addView(text(channelName, 18, TEXT, true));
        titleCol.addView(text((number > 0 ? (tr("频道 ") + number + "  ·  ") : "") + prefs.profileFor(uuid),
                10, DIM, false));
        top.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView profBtn = new ImageView(this);
        profBtn.setImageResource(R.drawable.ic_tune);
        profBtn.setColorFilter(DIM);
        profBtn.setScaleType(ImageView.ScaleType.FIT_CENTER);
        profBtn.setBackground(roundBg(chipBg, 20, false));
        profBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(34), dp(34));
        plp.rightMargin = dp(6);
        profBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { chooseProfile(); }
        });
        top.addView(profBtn, plp);

        ImageView linkBtn = new ImageView(this);
        linkBtn.setImageResource(R.drawable.ic_link);
        linkBtn.setColorFilter(DIM);
        linkBtn.setScaleType(ImageView.ScaleType.FIT_CENTER);
        linkBtn.setBackground(roundBg(chipBg, 20, false));
        linkBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(34), dp(34));
        llp.rightMargin = dp(8);
        linkBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showLink(); }
        });
        top.addView(linkBtn, llp);

        ImageView playBtn = new ImageView(this);
        playBtn.setImageResource(R.drawable.ic_play);
        playBtn.setColorFilter(Theme.BG);
        playBtn.setScaleType(ImageView.ScaleType.FIT_CENTER);
        playBtn.setBackground(roundBg(ACCENT, 20, false));
        playBtn.setPadding(dp(10), dp(10), dp(10), dp(10));
        playBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { play(); }
        });
        top.addView(playBtn, new LinearLayout.LayoutParams(dp(38), dp(38)));
                root.addView(top);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));

        // 按天的标签栏
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setPadding(dp(12), dp(4), dp(12), dp(2));
        hs.setClipToPadding(false);
        dayTabs = new LinearLayout(this);
        dayTabs.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(dayTabs);
        root.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusLine = text(tr("正在读取节目单…"), 10, DIM, false);
        statusLine.setPadding(dp(20), dp(2), dp(20), dp(4));
        root.addView(statusLine);

        ListView list = new ListView(this);
        list.setDividerHeight(1);
        list.setDivider(new android.graphics.drawable.ColorDrawable(LINE));
        list.setPadding(0, 0, 0, dp(8));
        list.setClipToPadding(false);
        list.setBackgroundColor(BG);
        adapter = new EpgAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                eventMenu(dayEvents.get(pos));
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        statusLine.setText(tr("正在读取节目单…"));
        bg.execute(new Runnable() {
            public void run() {
                try {
                    final long now = System.currentTimeMillis() / 1000L;
                    final long from = now - 3600;
                    final long to = now + 7 * 24 * 3600L;
                    final List<EpgEvent> filtered = api.epgChannelWindow(uuid, from, to);
                    try {
                        profiles = api.profiles();
                        String cur = prefs.profileFor(uuid);
                        boolean okP = false;
                        for (String[] pr : profiles) if (pr[0].equals(cur)) { okP = true; break; }
                        if (!okP) {
                            prefs.setProfileFor(uuid, null);
                            prefs.save(ChannelDetailActivity.this);
                        }
                    } catch (Exception ignored) {
                    }
                    final List<DvrEntry> up = api.dvrUpcoming();
                    final Set<String> bookedSet = new HashSet<String>();
                    for (DvrEntry d : up) bookedSet.add(d.start + "|" + d.title);
                    ui.post(new Runnable() {
                        public void run() {
                            progress.setVisibility(View.GONE);
                            allEvents.clear();
                            allEvents.addAll(filtered);
                            booked.clear();
                            booked.addAll(bookedSet);
                            buildDays();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        public void run() {
                            progress.setVisibility(View.GONE);
                            statusLine.setText(tr("读取失败：") + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void buildDays() {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (EpgEvent e : allEvents) {
            String d = dayFmt.format(new Date(e.start * 1000));
            Integer c = counts.get(d);
            counts.put(d, c == null ? 1 : c + 1);
        }
        days.clear();
        days.addAll(counts.keySet());
        String today = dayFmt.format(new Date());
        selectedDay = days.contains(today) ? today : (days.isEmpty() ? "" : days.get(0));
        dayTabs.removeAllViews();
        for (final String d : days) {
            boolean sel = d.equals(selectedDay);
            String label = dayLabel(d) + " (" + weekdayLabel(d) + ")";
            TextView tab = text(label, 12, sel ? ACCENT : DIM, true);
            tab.setPadding(dp(14), dp(7), dp(14), dp(7));
            tab.setBackground(sel ? roundBg(0x2626C6DA, 18, false) : null);
            tab.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    selectedDay = d;
                    buildDaysTabsOnly();
                    applyDay();
                }
            });
            dayTabs.addView(tab);
        }
        applyDay();
    }

    private void buildDaysTabsOnly() {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (EpgEvent e : allEvents) {
            String d = dayFmt.format(new Date(e.start * 1000));
            Integer c = counts.get(d);
            counts.put(d, c == null ? 1 : c + 1);
        }
        dayTabs.removeAllViews();
        for (final String d : days) {
            boolean sel = d.equals(selectedDay);
            String label = dayLabel(d) + " (" + weekdayLabel(d) + ")";
            TextView tab = text(label, 13, sel ? ACCENT : DIM, true);
            tab.setPadding(dp(12), dp(8), dp(12), dp(8));
            tab.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    selectedDay = d;
                    buildDaysTabsOnly();
                    applyDay();
                }
            });
            dayTabs.addView(tab);
        }
    }

    /** 周几（按界面语言） */
    private String weekdayLabel(String d) {
        try {
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.setTime(dayFmt.parse(d));
            switch (c.get(java.util.Calendar.DAY_OF_WEEK)) {
                case java.util.Calendar.SUNDAY: return tr("周日");
                case java.util.Calendar.MONDAY: return tr("周一");
                case java.util.Calendar.TUESDAY: return tr("周二");
                case java.util.Calendar.WEDNESDAY: return tr("周三");
                case java.util.Calendar.THURSDAY: return tr("周四");
                case java.util.Calendar.FRIDAY: return tr("周五");
                default: return tr("周六");
            }
        } catch (Exception e) {
            return "";
        }
    }

    private String dayLabel(String d) {
        String today = dayFmt.format(new Date());
        long nowMs = System.currentTimeMillis();
        String tomorrow = dayFmt.format(new Date(nowMs + 24 * 3600 * 1000L));
        if (d.equals(today)) return "今天";
        if (d.equals(tomorrow)) return tr("明天");
        try {
            return dayShow.format(dayFmt.parse(d));
        } catch (Exception e) {
            return d;
        }
    }

    private void applyDay() {
        dayEvents.clear();
        for (EpgEvent e : allEvents) {
            if (dayFmt.format(new Date(e.start * 1000)).equals(selectedDay)) dayEvents.add(e);
        }
        adapter.notifyDataSetChanged();
        statusLine.setText(dayEvents.size() + tr(" 个节目 · 点节目可录制/播放"));
    }

    private boolean isBooked(EpgEvent e) {
        return booked.contains(e.start + "|" + e.title);
    }

    private void play() {
        String url = api.streamUrlForUuid(uuid, prefs.profileFor(uuid));
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(Uri.parse(url), "video/*");
        i.putExtra("title", channelName);
        if (prefs.playerPkg != null && !prefs.playerPkg.isEmpty()) {
            i.setPackage(prefs.playerPkg);
            try {
                startActivity(i);
                return;
            } catch (ActivityNotFoundException ignored) {
            }
        }
        try {
            startActivity(Intent.createChooser(
                    new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*"), tr("选择播放器")));
        } catch (ActivityNotFoundException ex) {
            toast(tr("没有可用的播放器"));
        }
    }

    /** 为该频道单独选串流 profile（默认跟随全局） */
    private void chooseProfile() {
        bg.execute(new Runnable() {
            public void run() {
                try {
                    final List<String[]> list =
                            (profiles != null && !profiles.isEmpty()) ? profiles : api.profiles();
                    ui.post(new Runnable() {
                        public void run() {
                            List<String> labels = new ArrayList<String>();
                            labels.add(tr("跟随默认") + " (" + prefs.profile + ")");
                            for (String[] p : list) labels.add(p[0]);
                            dlg()
                                    .setTitle(tr("串流配置"))
                                    .setSingleChoiceItems(labels.toArray(new String[0]), -1,
                                            new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface d, int which) {
                                                    if (which == 0) prefs.setProfileFor(uuid, null);
                                                    else prefs.setProfileFor(uuid, list.get(which - 1)[0]);
                                                    prefs.save(ChannelDetailActivity.this);
                                                    d.dismiss();
                                                    recreate();
                                                }
                                            })
                                    .setNegativeButton(tr("取消"), null)
                                    .show();
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void showLink() {
        final String url = api.streamUrlForUuid(uuid, prefs.profileFor(uuid));
        dlg()
                .setTitle(tr("串流链接 · ") + channelName)
                .setMessage(url)
                .setPositiveButton(tr("复制链接"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("tvh", url));
                        toast(tr("已复制"));
                    }
                })
                .setNegativeButton(tr("关闭"), null)
                .show();
    }

    private void eventMenu(final EpgEvent e) {
        String[] items = {isBooked(e) ? tr("已预约录制") : tr("录制此节目"), tr("播放该频道"), tr("查看详情")};
        dlg()
                .setTitle(fmtRange(e) + "\n" + e.title)
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            if (isBooked(e)) toast(tr("该节目已预约录制"));
                            else record(e);
                        } else if (which == 1) {
                            play();
                        } else {
                            showDetail(e);
                        }
                    }
                }).show();
    }

    private void showDetail(EpgEvent e) {
        String msg = fmtRange(e) + "\n\n" + (e.title == null ? "" : e.title) + "\n" +
                (e.subtitle == null ? "" : e.subtitle) + "\n\n" +
                (e.description == null ? "" : e.description);
        dlg().setTitle(tr("节目详情")).setMessage(msg)
                .setPositiveButton(tr("关闭"), null).show();
    }

    private String fmtRange(EpgEvent e) {
        return hm.format(new Date(e.start * 1000)) + " - " + hm.format(new Date(e.stop * 1000));
    }

    private void record(final EpgEvent e) {
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
                            toast(tr("已预约录制：") + e.title);
                            booked.add(e.start + "|" + e.title);
                            adapter.notifyDataSetChanged();
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

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private class EpgAdapter extends BaseAdapter {
        public int getCount() { return dayEvents.size(); }
        public Object getItem(int i) { return dayEvents.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int pos, View convert, ViewGroup parent) {
            EpgEvent e = dayEvents.get(pos);
            long now = System.currentTimeMillis() / 1000L;
            boolean live = e.start <= now && e.stop > now;

            LinearLayout row = new LinearLayout(ChannelDetailActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(9), dp(8), dp(9));
            row.setBackground(roundBg(CARD, 14));

            LinearLayout col = new LinearLayout(ChannelDetailActivity.this);
            col.setOrientation(LinearLayout.VERTICAL);

            LinearLayout head = new LinearLayout(ChannelDetailActivity.this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            TextView t1 = text(fmtRange(e), 10, live ? ACCENT : DIM, false);
            t1.setTypeface(Typeface.MONOSPACE);
            head.addView(t1);
            if (live) head.addView(text(tr("  正在播"), 10, ACCENT, true));
            col.addView(head);

            col.addView(text(e.title, 14, TEXT, true));
            if (e.subtitle != null && !e.subtitle.isEmpty()) {
                String sub = e.subtitle.length() > 60 ? e.subtitle.substring(0, 59) + "…" : e.subtitle;
                col.addView(text(sub, 10, DIM, false));
            }
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            final EpgEvent ev = e;
            boolean done = isBooked(e);
            TextView btn = text(done ? tr("已预约") : tr("录制"), 12, done ? DIM : ACCENT, true);
            btn.setPadding(dp(10), dp(6), dp(10), dp(6));
            btn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (isBooked(ev)) toast(tr("该节目已预约录制"));
                    else record(ev);
                }
            });
            row.addView(btn);
            return row;
        }
    }
}
