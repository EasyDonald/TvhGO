package com.easydonald.tvhgo;

/** 深浅色主题：静态字段，Activity 启动时按设置/系统模式刷新一次 */
public class Theme {

    public static int BG, CARD, TEXT, DIM, ACCENT, WARN, LINE, TAB_BG;
    public static int ROW_A, ROW_B, LABEL_BG, RULER_BG;
    public static int[] CH_COLORS = new int[0];

    private static boolean dark = true;

    public static boolean isDark() {
        return dark;
    }

    public static void apply(int modePref, boolean systemNight) {
        dark = (modePref == 1) || (modePref == 0 && systemNight);
        if (dark) {
            BG = 0xFF101214;
            CARD = 0xFF1B1E22;
            TEXT = 0xFFEEEEEE;
            DIM = 0xFF9AA0A6;
            ACCENT = 0xFF26C6DA;
            WARN = 0xFFFF7043;
            LINE = 0xFF2A2E33;
            TAB_BG = 0xFF0A0A0A;
            ROW_A = 0xFF141618;
            ROW_B = 0xFF1B1E22;
            LABEL_BG = 0xFF0D0F11;
            RULER_BG = 0xFF0A0C0E;
            CH_COLORS = new int[]{
                    0xFF2E7D32, 0xFF1565C0, 0xFF6A1B9A, 0xFFAD1457,
                    0xFF00838F, 0xFFEF6C00, 0xFF4527A0, 0xFF283593, 0xFF37474F};
        } else {
            BG = 0xFFEEF1F4;
            CARD = 0xFFFFFFFF;
            TEXT = 0xFF1B1F24;
            DIM = 0xFF6B7280;
            ACCENT = 0xFF00897B;
            WARN = 0xFFD84315;
            LINE = 0xFFD5DBE3;
            TAB_BG = 0xFFFFFFFF;
            ROW_A = 0xFFFFFFFF;
            ROW_B = 0xFFFFFFFF;
            LABEL_BG = 0xFFF0F2F5;
            RULER_BG = 0xFFE9ECEF;
            CH_COLORS = new int[]{
                    0xFF43A047, 0xFF1E88E5, 0xFF8E24AA, 0xFFD81B60,
                    0xFF00ACC1, 0xFFF4511E, 0xFF5E35B1, 0xFF3949AB, 0xFF546E7A};
        }
    }
}
