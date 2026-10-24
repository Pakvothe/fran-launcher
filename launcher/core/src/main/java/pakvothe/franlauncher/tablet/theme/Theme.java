package pakvothe.franlauncher.tablet.theme;

/** Colores del fondo y del texto. Inmutable: al cambiar fase o fondo se toma otro y se re-graban los nodos afectados. */
public final class Theme {
    public final int phase;
    /** Fondo blanco: texto oscuro y barras del sistema oscuras. */
    public final boolean light;
    public final int bg, orbA, orbB;
    public final int text, textSecondary, sheet, divider, dot, accent, pillBg, fieldBg, handle, danger;

    private Theme(int phase, boolean light) {
        this.phase = phase;
        this.light = light;
        int[] p = PALETTE[phase];
        bg = light ? 0xFFF6F4F0 : p[0];
        orbA = p[1];
        orbB = p[2];
        if (light) {
            text = 0xFF1C1B19;
            textSecondary = alpha(0x1C1B19, 0.62f);
            sheet = 0xFFFFFFFF;
            divider = alpha(0x1C1B19, 0.12f);
            dot = 0xFFB5651D;
            accent = 0xFFB5651D;
            pillBg = alpha(0x1C1B19, 0.07f);
            fieldBg = alpha(0x1C1B19, 0.06f);
            handle = alpha(0x1C1B19, 0.25f);
            danger = 0xFFB3261E;
        } else {
            text = 0xFFFFF6EE;
            textSecondary = alpha(0xFFF6EE, 0.68f);
            sheet = 0xFF1A1020;
            divider = alpha(0xFFF6EE, 0.14f);
            dot = 0xFFFFD27A;
            accent = 0xFFFFB38F;
            pillBg = 0x21FFFFFF;
            fieldBg = 0x1AFFFFFF;
            handle = 0x55FFFFFF;
            danger = 0xFFE8A49A;
        }
    }

    /** Fondo, orbe A, orbe B. Índice = fase de PhaseResolver. */
    static final int[][] PALETTE = {
        {0xFF07080F, 0xFF2B3A8C, 0xFF5B2A7A},
        {0xFF2B2A4A, 0xFFFF9A8B, 0xFF6FB7FF},
        {0xFF1D4E7A, 0xFF6FD3FF, 0xFFFFD27A},
        {0xFF2A1630, 0xFFFF7A59, 0xFF7B5CFF},
    };

    private static final Theme[] DARK = {new Theme(0, false), new Theme(1, false), new Theme(2, false), new Theme(3, false)};
    private static final Theme[] LIGHT = {new Theme(0, true), new Theme(1, true), new Theme(2, true), new Theme(3, true)};

    public static Theme of(int phase, boolean light) {
        return (light ? LIGHT : DARK)[phase];
    }

    public static int alpha(int rgb, float a) {
        return (Math.round(a * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
