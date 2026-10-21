package com.easydonald.tvhgo;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 全自绘底部卡片对话框：不依赖系统 AlertDialog，颜色/圆角/间距跟随 Theme。
 * 用法与 AlertDialog.Builder 对齐（setTitle/setMessage/setView/setItems/
 * setSingleChoiceItems/setPositiveButton/setNegativeButton/setNeutralButton）。
 */
public class Sheet {

    private static final List<Sheet> STACK = new ArrayList<Sheet>();

    private final Context ctx;
    private final FrameLayout overlay;
    private final ViewGroup host;
    private DialogInterface.OnDismissListener dismissListener;

    private Sheet(ViewGroup host, Context ctx) {
        this.host = host;
        this.ctx = ctx;
        this.overlay = new FrameLayout(ctx);
        this.overlay.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        this.overlay.setBackgroundColor(0x99000000);
        this.overlay.setClickable(true);
    }

    private int dp(int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private GradientDrawable sheetBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Theme.CARD);
        float r = dp(22);
        g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        return g;
    }

    public void dismiss() {
        if (!STACK.remove(this)) return;
        host.removeView(overlay);
        if (dismissListener != null) dismissListener.onDismiss(proxy);
    }

    public void cancel() {
        dismiss();
    }

    /** 传给 listener 的 DialogInterface 代理（老代码里会调用 d.dismiss()） */
    public DialogInterface dialog() {
        return proxy;
    }

    private final DialogInterface proxy = new DialogInterface() {
        public void cancel() { Sheet.this.dismiss(); }
        public void dismiss() { Sheet.this.dismiss(); }
        public void setOnCancelListener(OnCancelListener l) {}
        public void setOnDismissListener(OnDismissListener l) { dismissListener = l; }
        public void setOnShowListener(OnShowListener l) {}
    };

    /** 关掉所有卡片（Activity 销毁时调用） */
    public static void closeAll() {
        while (!STACK.isEmpty()) {
            STACK.get(STACK.size() - 1).dismiss();
        }
    }

    /** 返回键：关掉最上层卡片，返回 true 表示已消费 */
    public static boolean closeTop() {
        if (STACK.isEmpty()) return false;
        STACK.get(STACK.size() - 1).dismiss();
        return true;
    }

    // ---------------------------------------------------------------- builder

    public static class Builder {
        private final Context ctx;
        private CharSequence title;
        private CharSequence message;
        private View view;
        private CharSequence[] items;
        private int checked = -1;
        private DialogInterface.OnClickListener itemListener;
        private CharSequence posText, negText, neuText;
        private DialogInterface.OnClickListener posListener, negListener, neuListener;
        private boolean cancelable = true;

        public Builder(Context c) {
            ctx = c;
        }

        public Builder setTitle(CharSequence s) {
            title = s;
            return this;
        }

        public Builder setMessage(CharSequence s) {
            message = s;
            return this;
        }

        public Builder setView(View v) {
            view = v;
            return this;
        }

        public Builder setItems(CharSequence[] arr, DialogInterface.OnClickListener l) {
            items = arr;
            itemListener = l;
            checked = -1;
            return this;
        }

        public Builder setSingleChoiceItems(CharSequence[] arr, int sel, DialogInterface.OnClickListener l) {
            items = arr;
            checked = sel;
            itemListener = l;
            return this;
        }

        public Builder setPositiveButton(CharSequence s, DialogInterface.OnClickListener l) {
            posText = s;
            posListener = l;
            return this;
        }

        public Builder setNegativeButton(CharSequence s, DialogInterface.OnClickListener l) {
            negText = s;
            negListener = l;
            return this;
        }

        public Builder setNeutralButton(CharSequence s, DialogInterface.OnClickListener l) {
            neuText = s;
            neuListener = l;
            return this;
        }

        public Builder setCancelable(boolean b) {
            cancelable = b;
            return this;
        }

        public Sheet create() {
            return build();
        }

        public Sheet show() {
            Sheet s = build();
            s.attach();
            return s;
        }

        private Sheet build() {
            Sheet sheet = new Sheet(hostOf(), ctx);
            sheet.buildContent(this, cancelable);
            return sheet;
        }

        private ViewGroup hostOf() {
            android.app.Activity a = (android.app.Activity) ctx;
            return (ViewGroup) a.getWindow().getDecorView();
        }
    }

    private void attach() {
        android.view.ViewGroup.LayoutParams lp = overlay.getLayoutParams();
        host.addView(overlay, lp);
        STACK.add(this);
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(150).start();
    }

    private void buildContent(Builder b, final boolean cancelable) {
        int dp12 = dp(12);

        // 面板：底部卡片
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(sheetBg());
        panel.setPadding(0, 0, 0, dp(8));

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        panelLp.gravity = Gravity.BOTTOM;

        // 拖动条
        View handle = new View(ctx);
        handle.setBackground(rounded(Theme.LINE, 2));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(38), dp(4));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.topMargin = dp(10);
        hlp.bottomMargin = dp(4);
        panel.addView(handle, hlp);

        // 标题
        if (b.title != null) {
            TextView t = new TextView(ctx);
            t.setText(b.title);
            t.setTextSize(17);
            t.setTypeface(null, android.graphics.Typeface.BOLD);
            t.setTextColor(Theme.TEXT);
            t.setPadding(dp(20), dp(10), dp(20), dp(4));
            panel.addView(t);
        }

        // 正文
        if (b.message != null) {
            TextView m = new TextView(ctx);
            m.setText(b.message);
            m.setTextSize(13);
            m.setTextColor(Theme.DIM);
            m.setLineSpacing(dp(3), 1f);
            m.setPadding(dp(20), dp(4), dp(20), dp(8));
            panel.addView(m);
        }

        // 自定义内容
        if (b.view != null) {
            ScrollView sv = new ScrollView(ctx);
            sv.setPadding(0, 0, 0, 0);
            ViewGroup parent = (ViewGroup) b.view.getParent();
            if (parent != null) parent.removeView(b.view);
            sv.addView(b.view);
            int maxH = (int) (ctx.getResources().getDisplayMetrics().heightPixels * 0.62f);
            LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, maxH);
            svlp.topMargin = dp(4);
            panel.addView(sv, svlp);
            panel.setMinimumHeight(dp(120));
        }

        // 选项列表
        if (b.items != null) {
            ScrollView sv = new ScrollView(ctx);
            LinearLayout list = new LinearLayout(ctx);
            list.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < b.items.length; i++) {
                final int idx = i;
                boolean sel = i == b.checked;
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(20), dp(13), dp(20), dp(13));

                TextView label = new TextView(ctx);
                label.setText(b.items[i]);
                label.setTextSize(15);
                label.setTextColor(sel ? Theme.ACCENT : Theme.TEXT);
                label.setTypeface(null, sel ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                if (b.checked >= 0) {
                    TextView check = new TextView(ctx);
                    check.setText(sel ? "✓" : "");
                    check.setTextSize(15);
                    check.setTextColor(Theme.ACCENT);
                    row.addView(check);
                }

                final DialogInterface.OnClickListener l = b.itemListener;
                row.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (l != null) l.onClick(proxy, idx);
                        dismiss();
                    }
                });
                list.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            sv.addView(list);
            int per = dp(52);
            int want = b.items.length * per + dp(8);
            int maxH = (int) (ctx.getResources().getDisplayMetrics().heightPixels * 0.62f);
            LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.min(want, maxH));
            panel.addView(sv, svlp);
            panel.setMinimumHeight(dp(120));
        }

        // 按钮区
        if (b.posText != null || b.negText != null || b.neuText != null) {
            LinearLayout btnRow = new LinearLayout(ctx);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            btnRow.setPadding(dp(10), dp(8), dp(10), dp(2));

            if (b.negText != null) {
                btnRow.addView(actionButton(b.negText, false, new View.OnClickListener() {
                    public void onClick(View v) {
                        if (b.negListener != null) b.negListener.onClick(proxy, -2);
                        dismiss();
                    }
                }));
            }
            if (b.neuText != null) {
                btnRow.addView(actionButton(b.neuText, false, new View.OnClickListener() {
                    public void onClick(View v) {
                        if (b.neuListener != null) b.neuListener.onClick(proxy, -3);
                        dismiss();
                    }
                }));
            }
            if (b.posText != null) {
                btnRow.addView(actionButton(b.posText, true, new View.OnClickListener() {
                    public void onClick(View v) {
                        if (b.posListener != null) b.posListener.onClick(proxy, -1);
                        dismiss();
                    }
                }));
            }
            panel.addView(btnRow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        overlay.addView(panel, panelLp);
        overlay.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (cancelable) dismiss();
            }
        });
        panel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // 吃掉点击，避免穿透关闭
            }
        });
    }

    private TextView actionButton(CharSequence text, boolean primary, View.OnClickListener l) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(14);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(primary ? Theme.ACCENT : Theme.DIM);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(10), dp(16), dp(10));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(4);
        t.setLayoutParams(lp);
        return t;
    }
}
