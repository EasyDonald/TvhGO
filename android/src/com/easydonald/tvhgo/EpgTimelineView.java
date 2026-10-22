package com.easydonald.tvhgo;

import android.content.Context;

import static com.easydonald.tvhgo.I18n.tr;
import static com.easydonald.tvhgo.Theme.*;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** EPG 时间轴网格：左列频道名，横向时间轴，节目块可点击 */
public class EpgTimelineView extends View {

    public interface Listener {
        void onEventClick(EpgEvent e);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SimpleDateFormat hhmm = new SimpleDateFormat("HH:mm", Locale.CHINA);

    private List<Channel> channels = new ArrayList<Channel>();
    private List<EpgEvent> events = new ArrayList<EpgEvent>();
    private final Map<String, Integer> rowOf = new HashMap<String, Integer>();

    private long winStart = 0, winEnd = 0;
    private float density = 1f;
    private float pxPerMin = 2.4f;   // dp per minute
    private float labelW = 104f, rulerH = 26f, rowH = 46f;  // dp
    private float scrollX = 0, scrollY = 0;
    private float downX, downY, baseX, baseY;
    private boolean dragging = false;
    private Listener listener;


    public EpgTimelineView(Context c) {
        super(c);
        density = getResources().getDisplayMetrics().density;
        labelW *= density;
        rulerH *= density;
        rowH *= density;
        pxPerMin *= density;
        text.setTextSize(12 * density);
        paint.setStyle(Paint.Style.FILL);
    }

    public void setListener(Listener l) { listener = l; }

    public void setData(List<Channel> chs, List<EpgEvent> evs, long startSec, long endSec) {
        channels = chs == null ? new ArrayList<Channel>() : chs;
        events = evs == null ? new ArrayList<EpgEvent>() : evs;
        winStart = startSec;
        winEnd = endSec;
        rowOf.clear();
        for (int i = 0; i < channels.size(); i++) rowOf.put(channels.get(i).uuid, i);
        // 初始定位到当前时刻
        long now = System.currentTimeMillis() / 1000L;
        float x = (now - winStart) / 60f * pxPerMin - (getWidth() - labelW) / 3f;
        scrollX = clamp(x, 0, maxScrollX());
        scrollY = 0;
        invalidate();
    }

    private float clamp(float v, float lo, float hi) {
        if (hi < lo) hi = lo;
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private float contentW() {
        return Math.max(0, (winEnd - winStart) / 60f * pxPerMin);
    }

    private float maxScrollX() {
        return Math.max(0, contentW() - (getWidth() - labelW));
    }

    private float maxScrollY() {
        return Math.max(0, channels.size() * rowH - (getHeight() - rulerH));
    }

    private float xFor(long sec) {
        return labelW + (sec - winStart) / 60f * pxPerMin - scrollX;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(BG);
        if (winEnd <= winStart) {
            paint.setColor(DIM);
            text.setColor(DIM);
            canvas.drawText(tr("按“刷新”加载节目单"), labelW + 16 * density, rulerH + 40 * density, text);
            return;
        }

        int firstRow = Math.max(0, (int) (scrollY / rowH));
        int lastRow = Math.min(channels.size() - 1, (int) ((scrollY + getHeight() - rulerH) / rowH));

        // 行背景 + 分隔线
        paint.setColor(ROW_B);
        for (int r = firstRow; r <= lastRow; r++) {
            float y = rulerH + r * rowH - scrollY;
            if (r % 2 == 0) {
                paint.setColor(ROW_A);
                canvas.drawRect(labelW, y, getWidth(), y + rowH, paint);
            }
            paint.setColor(LINE);
            canvas.drawRect(labelW, y + rowH - 1, getWidth(), y + rowH, paint);
        }

        // 顶部时间刻度带
        paint.setColor(RULER_BG);
        canvas.drawRect(0, 0, getWidth(), rulerH, paint);
        paint.setColor(LABEL_BG);
        canvas.drawRect(0, 0, labelW, rulerH, paint);

        long tick = winStart - (winStart % 1800);
        text.setTextSize(10 * density);
        for (long t = tick; t <= winEnd; t += 1800) {
            float x = xFor(t);
            if (x < labelW - 1 || x > getWidth()) continue;
            boolean hour = (t % 3600) == 0;
            paint.setColor(hour ? TEXT : LINE);
            canvas.drawRect(x, hour ? rulerH - 9 * density : rulerH - 5 * density, x + 1, getHeight(), paint);
            String label = hhmm.format(new Date(t * 1000));
            text.setColor(hour ? TEXT : DIM);
            text.setTypeface(hour ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            float tw = text.measureText(label);
            if (x + tw + 6 * density < getWidth()) {
                canvas.drawText(label, x + 3 * density, rulerH - 10 * density, text);
            }
        }
        text.setTypeface(Typeface.DEFAULT);
        paint.setColor(LINE);
        canvas.drawRect(0, rulerH - 1, getWidth(), rulerH, paint);

        // 节目块
        text.setTextSize(11 * density);
        RectF rect = new RectF();
        for (EpgEvent e : events) {
            Integer row = rowOf.get(e.channelUuid);
            if (row == null || row < firstRow || row > lastRow) continue;
            float x1 = xFor(e.start);
            float x2 = xFor(e.stop);
            if (x2 < labelW || x1 > getWidth()) continue;
            float left = Math.max(x1, labelW);
            float right = Math.min(x2, getWidth());
            if (right - left < 3 * density) continue;
            float y = rulerH + row * rowH - scrollY + 3 * density;
            rect.set(left + 1, y, right - 2, y + rowH - 7 * density);

            paint.setColor(CH_COLORS[Math.abs(e.channelUuid.hashCode()) % CH_COLORS.length]);
            canvas.drawRoundRect(rect, 4 * density, 4 * density, paint);
            paint.setColor(Theme.isDark() ? 0x33000000 : 0x14000000);
            canvas.drawRoundRect(new RectF(rect.left, rect.top, rect.right, rect.top + 2 * density), 2, 2, paint);

            // 标题（截断）—— 节目块底色是彩色，文字固定白色
            String title = e.title == null ? "" : e.title;
            text.setColor(0xFFFFFFFF);
            float avail = rect.width() - 8 * density;
            if (avail > 10) {
                String shown = clip(title, avail);
                if (shown.length() > 0) {
                    canvas.drawText(shown, rect.left + 4 * density, rect.centerY() + 4 * density, text);
                }
            }
        }

        // 当前时刻竖线
        long now = System.currentTimeMillis() / 1000L;
        if (now >= winStart && now <= winEnd) {
            paint.setColor(Color.parseColor("#EF5350"));
            float x = xFor(now);
            canvas.drawRect(x, rulerH, x + 1.5f * density, getHeight(), paint);
        }

        // 左侧频道列（不透明覆盖）
        paint.setColor(LABEL_BG);
        canvas.drawRect(0, rulerH, labelW, getHeight(), paint);
        paint.setColor(LINE);
        canvas.drawRect(labelW - 1, 0, labelW, getHeight(), paint);

        text.setTextSize(11 * density);
        for (int r = firstRow; r <= lastRow; r++) {
            Channel c = channels.get(r);
            float y = rulerH + r * rowH - scrollY;
            text.setColor(DIM);
            String num = c.number > 0 ? String.valueOf(c.number) : "";
            canvas.drawText(num, 4 * density, y + 17 * density, text);
            text.setColor(c.enabled ? TEXT : DIM);
            drawNameTwoLines(canvas, String.valueOf(c.name), 30 * density, y + 17 * density,
                    labelW - 34 * density, 13 * density);
            text.setColor(LINE);
            canvas.drawRect(0, y + rowH - 1, labelW, y + rowH, paint);
        }

        // 顶部刻度条
        paint.setColor(RULER_BG);
        canvas.drawRect(0, 0, getWidth(), rulerH, paint);
        paint.setColor(LINE);
        canvas.drawRect(0, rulerH - 1, getWidth(), rulerH, paint);
    }

    /** 频道名最多画两行，超出用省略号 */
    private void drawNameTwoLines(android.graphics.Canvas canvas, String name, float x, float baseY,
                                  float maxWidth, float lineGap) {
        if (name == null) name = "";
        int first = text.breakText(name, true, maxWidth, null);
        if (first >= name.length()) {
            canvas.drawText(name, x, baseY, text);
            return;
        }
        canvas.drawText(name.substring(0, first), x, baseY, text);
        String rest = name.substring(first);
        int second = text.breakText(rest, true, maxWidth, null);
        String line2 = second >= rest.length() ? rest : rest.substring(0, Math.max(1, second - 1)) + "…";
        canvas.drawText(line2, x, baseY + lineGap, text);
    }

    private String clip(String s, float width) {
        if (s == null) return "";
        if (text.measureText(s) <= width) return s;
        int n = s.length();
        while (n > 0 && text.measureText(s.substring(0, n) + "…") > width) n--;
        return n <= 0 ? "" : s.substring(0, n) + "…";
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                baseX = scrollX;
                baseY = scrollY;
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = ev.getX() - downX, dy = ev.getY() - downY;
                if (!dragging && (Math.abs(dx) > 8 * density || Math.abs(dy) > 8 * density)) dragging = true;
                if (dragging) {
                    scrollX = clamp(baseX - dx, 0, maxScrollX());
                    scrollY = clamp(baseY - dy, 0, maxScrollY());
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (!dragging) {
                    long hit = hitTest(ev.getX(), ev.getY());
                    if (hit > 0 && listener != null) {
                        for (EpgEvent e : events) {
                            if (e.eventId == hit) {
                                listener.onEventClick(e);
                                break;
                            }
                        }
                    }
                }
                dragging = false;
                return true;
        }
        return super.onTouchEvent(ev);
    }

    private long hitTest(float px, float py) {
        if (py < rulerH || px < labelW) return -1;
        int row = (int) ((py - rulerH + scrollY) / rowH);
        if (row < 0 || row >= channels.size()) return -1;
        String chUuid = channels.get(row).uuid;
        long sec = (long) (winStart + (px - labelW + scrollX) / pxPerMin * 60);
        for (EpgEvent e : events) {
            if (!chUuid.equals(e.channelUuid)) continue;
            if (sec >= e.start && sec < e.stop) return e.eventId;
        }
        return -1;
    }
}
