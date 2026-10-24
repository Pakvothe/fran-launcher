package pakvothe.franlauncher.tablet.theme;

/** Hora local → fase del cielo. */
public final class PhaseResolver {
    public static final int NIGHT = 0, DAWN = 1, DAY = 2, DUSK = 3;

    private PhaseResolver() {}

    /** Noche 21–5, amanecer 5–8, día 8–17, atardecer 17–21. */
    public static int phaseAt(int hourOfDay) {
        if (hourOfDay < 0 || hourOfDay > 23) throw new IllegalArgumentException("hora: " + hourOfDay);
        if (hourOfDay >= 21 || hourOfDay < 5) return NIGHT;
        if (hourOfDay < 8) return DAWN;
        if (hourOfDay < 17) return DAY;
        return DUSK;
    }
}
