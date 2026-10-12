package com.example.examplemod.gui;

/**
 * Farbpalette des Clients (ARGB). Texte brauchen in 1.21.7 einen Alphawert, sonst bleiben sie unsichtbar.
 * Die Werte sind nicht final, weil Akzentfarbe und Stil im Modul "Design" umgeschaltet werden.
 */
public final class Theme {
    public static final String CLIENT_NAME = "LUMEN";
    public static final String CLIENT_TAG = "CLIENT";

    public static final String[] ACCENTS = {"Violett", "Blau", "Grün", "Rot", "Orange", "Pink"};
    public static final String[] STYLES = {"Dunkel", "Mitternacht", "Hell"};
    private static final int[][] ACCENT_COLORS = {
            {0xFF8B5CF6, 0xFF22D3EE}, {0xFF3B82F6, 0xFF22D3EE}, {0xFF10B981, 0xFFA3E635},
            {0xFFEF4444, 0xFFF97316}, {0xFFF97316, 0xFFFACC15}, {0xFFEC4899, 0xFFA855F7}};

    public static final int SHADOW = 0x40000000;
    public static final int ON_ACCENT = 0xFFFFFFFF;
    public static final int KNOB = 0xFFFFFFFF;
    public static final int SUCCESS = 0xFF34D399;
    public static final int DANGER = 0xFFF87171;
    public static final int WARNING = 0xFFFBBF24;
    public static final int INFO = 0xFF60A5FA;

    public static int OVERLAY, WINDOW, WINDOW_BORDER, SIDEBAR, HEADER, CARD, CARD_HOVER, CARD_ACTIVE;
    public static int FIELD, FIELD_BORDER, DIVIDER, SWITCH_OFF, SWITCH_OFF_HOVER, DANGER_BG, SCROLL_TRACK;
    public static int TEXT, TEXT_DIM, TEXT_MUTED;
    public static int HUD_BG, TOAST_BG;
    public static int ACCENT, ACCENT_2, ACCENT_SOFT;
    public static boolean light;
    public static float menuOpacity = .95F;

    static {
        apply("Violett", "Dunkel");
    }

    /** Menu surfaces only. Keep text, fields, HUD and notifications at their normal contrast. */
    public static void applyTransparency(int opacityPercent, int dimPercent) {
        menuOpacity = Math.max(.2F, Math.min(1F, opacityPercent / 100F));
        int opacity = Math.round(menuOpacity * 255);
        WINDOW = (WINDOW & 0xFFFFFF) | opacity << 24;
        SIDEBAR = (SIDEBAR & 0xFFFFFF) | opacity << 24;
        HEADER = (HEADER & 0xFFFFFF) | opacity << 24;
        CARD = (CARD & 0xFFFFFF) | opacity << 24;
        CARD_HOVER = (CARD_HOVER & 0xFFFFFF) | opacity << 24;
        CARD_ACTIVE = (CARD_ACTIVE & 0xFFFFFF) | opacity << 24;
        DANGER_BG = (DANGER_BG & 0xFFFFFF) | opacity << 24;
        OVERLAY = (OVERLAY & 0xFFFFFF) | Math.round(Math.max(0, Math.min(80, dimPercent)) * 2.55F) << 24;
    }

    private Theme() {}

    public static void apply(String accent, String style) {
        int accentIndex = Math.max(0, java.util.Arrays.asList(ACCENTS).indexOf(accent));
        ACCENT = ACCENT_COLORS[accentIndex][0];
        ACCENT_2 = ACCENT_COLORS[accentIndex][1];
        ACCENT_SOFT = alpha(ACCENT, 0.25F);
        light = style.equals("Hell");
        switch (style) {
            case "Hell" -> {
                OVERLAY = 0x70101018; WINDOW = 0xF7F5F6FA; WINDOW_BORDER = 0xFFD3D6E0; SIDEBAR = 0xFFECEEF3;
                HEADER = 0xFFF0F1F5; CARD = 0xFFFFFFFF; CARD_HOVER = 0xFFEEF0F6; CARD_ACTIVE = 0xFFF3F4F8;
                FIELD = 0xFFF1F2F6; FIELD_BORDER = 0xFFD0D4DE; DIVIDER = 0xFFE0E2EA; SWITCH_OFF = 0xFFC9CDD8;
                SWITCH_OFF_HOVER = 0xFFB8BDCB; DANGER_BG = 0xFFFDE2E2; SCROLL_TRACK = 0x20000000;
                TEXT = 0xFF16171D; TEXT_DIM = 0xFF4A4E61; TEXT_MUTED = 0xFF8A8FA2;
                HUD_BG = 0xD8F5F6FA; TOAST_BG = 0xF5FFFFFF;
            }
            case "Mitternacht" -> {
                OVERLAY = 0xA0030612; WINDOW = 0xF20D1424; WINDOW_BORDER = 0xFF233152; SIDEBAR = 0xFF0A101D;
                HEADER = 0xFF111A2E; CARD = 0xFF141E33; CARD_HOVER = 0xFF1C2843; CARD_ACTIVE = 0xFF18233B;
                FIELD = 0xFF0A1020; FIELD_BORDER = 0xFF283653; DIVIDER = 0xFF1E2A44; SWITCH_OFF = 0xFF2C3A58;
                SWITCH_OFF_HOVER = 0xFF384869; DANGER_BG = 0xFF3A1F2B; SCROLL_TRACK = 0x30FFFFFF;
                TEXT = 0xFFEEF2FA; TEXT_DIM = 0xFFA0AECB; TEXT_MUTED = 0xFF6A7899;
                HUD_BG = 0xC00A1020; TOAST_BG = 0xF0101A2E;
            }
            default -> {
                OVERLAY = 0xA0050508; WINDOW = 0xF2121319; WINDOW_BORDER = 0xFF2A2C3A; SIDEBAR = 0xFF0E0F14;
                HEADER = 0xFF15161E; CARD = 0xFF1B1C25; CARD_HOVER = 0xFF242632; CARD_ACTIVE = 0xFF20222E;
                FIELD = 0xFF0F1015; FIELD_BORDER = 0xFF2E3142; DIVIDER = 0xFF262836; SWITCH_OFF = 0xFF353848;
                SWITCH_OFF_HOVER = 0xFF3F4255; DANGER_BG = 0xFF3A1F27; SCROLL_TRACK = 0x30FFFFFF;
                TEXT = 0xFFF1F1F6; TEXT_DIM = 0xFFA3A6B8; TEXT_MUTED = 0xFF6C7085;
                HUD_BG = 0xC0101018; TOAST_BG = 0xF0141520;
            }
        }
    }

    /** Verlauf zwischen den beiden Akzentfarben, {@code t} von 0 bis 1. */
    public static int accent(float t) {
        return lerp(ACCENT, ACCENT_2, t);
    }

    /** Langsam laufender Akzentverlauf, z.B. für die HUD-Modulliste. */
    public static int accentWave(float offset) {
        double time = (System.currentTimeMillis() % 4000L) / 4000.0;
        float t = (float) (0.5 + 0.5 * Math.sin((time + offset) * Math.PI * 2));
        return accent(t);
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    public static int alpha(int color, float alpha) {
        int a = (int) ((color >>> 24) * Math.max(0, Math.min(1, alpha)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    /** Hover-Aufhellung, im hellen Stil stattdessen leicht abdunkeln. */
    public static int hover(int color) {
        return lerp(color, light ? 0xFF000000 : 0xFFFFFFFF, 0.08F);
    }
}
