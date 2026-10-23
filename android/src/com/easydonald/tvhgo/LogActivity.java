package com.easydonald.tvhgo;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 日志查看页：Tvheadend 服务端日志（comet 实时推送）与应用日志 */
public class LogActivity extends Activity {

    public static final String EXTRA_SOURCE = "source";
    public static final String SOURCE_SERVER = "server";
    public static final String SOURCE_APP = "app";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final StringBuilder buf = new StringBuilder();
    private final SimpleDateFormat hms = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private TextView body;
    private ScrollView scroll;
    private Prefs prefs;
    private boolean serverMode;
    private TvhComet comet;
    private boolean cometDown = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Prefs.load(this);
        I18n.setLang(prefs.lang);
        Theme.apply(prefs.themeMode, isSystemNight(this));
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Theme.BG));

        serverMode = SOURCE_SERVER.equals(getIntent().getStringExtra(EXTRA_SOURCE));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.BG);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(12), dp(10), dp(8));

        TextView title = new TextView(this);
        title.setText(serverMode ? I18n.tr("Tvheadend 日志") : I18n.tr("App 日志"));
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Theme.TEXT);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView copy = new TextView(this);
        copy.setText(I18n.tr("复制"));
        copy.setTextSize(13);
        copy.setTypeface(null, Typeface.BOLD);
        copy.setTextColor(Theme.ACCENT);
        copy.setPadding(dp(12), dp(6), dp(12), dp(6));
        copy.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("TvhGO log", buf.toString()));
                Toast.makeText(LogActivity.this, I18n.tr("已复制"), Toast.LENGTH_SHORT).show();
            }
        });
        bar.addView(copy);

        TextView close = new TextView(this);
        close.setText(I18n.tr("关闭"));
        close.setTextSize(13);
        close.setTypeface(null, Typeface.BOLD);
        close.setTextColor(Theme.DIM);
        close.setPadding(dp(12), dp(6), dp(6), dp(6));
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
        bar.addView(close);

        root.addView(bar);

        View divider = new View(this);
        divider.setBackgroundColor(Theme.LINE);
        root.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        scroll = new ScrollView(this);
        body = new TextView(this);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextSize(11);
        body.setTextColor(Theme.TEXT);
        body.setPadding(dp(14), dp(10), dp(14), dp(16));
        body.setTextIsSelectable(true);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        if (serverMode) {
            appendNote(I18n.tr("已连接，显示服务端实时日志（仅包含打开本页之后产生的记录）"));
            startComet();
        } else {
            body.setText(AppLog.dump());
        }
    }

    private void startComet() {
        comet = new TvhComet(prefs.base(), prefs.user, prefs.pass, new TvhComet.Listener() {
            public void onLog(final String text) {
                append(text);
            }

            public void onConnected(boolean ok) {
                if (!ok && !cometDown) {
                    cometDown = true;
                    appendNote(I18n.tr("与 Tvheadend 的日志通道断开，正在重连…"));
                } else if (ok && cometDown) {
                    cometDown = false;
                    appendNote(I18n.tr("已重新连接"));
                }
            }
        });
        comet.start();
    }

    private void appendNote(final String s) {
        append("---- " + s);
    }

    private void append(final String line) {
        ui.post(new Runnable() {
            public void run() {
                buf.append(hms.format(new Date())).append("  ").append(line).append('\n');
                body.setText(buf.toString());
                scroll.post(new Runnable() {
                    public void run() {
                        scroll.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    private static boolean isSystemNight(Context c) {
        int mode = c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (comet != null) comet.stop();
    }
}
