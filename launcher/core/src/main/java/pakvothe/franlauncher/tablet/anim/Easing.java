package pakvothe.franlauncher.tablet.anim;

/** cubic-bezier(0.2, 0, 0, 1) tabulada: sin cálculo ni allocations por frame. */
public final class Easing {
    private static final int N = 512;
    private static final float[] TABLE = build(0.2f, 0f, 0f, 1f);

    private Easing() {}

    /** p en [0, 1] (progreso lineal del tiempo) → valor de la curva. */
    public static float standard(float p) {
        if (p <= 0f) return 0f;
        if (p >= 1f) return 1f;
        float f = p * (N - 1);
        int i = (int) f;
        return TABLE[i] + (TABLE[i + 1] - TABLE[i]) * (f - i);
    }

    static float[] build(float x1, float y1, float x2, float y2) {
        float[] t = new float[N];
        for (int i = 0; i < N; i++) t[i] = solve(i / (float) (N - 1), x1, y1, x2, y2);
        return t;
    }

    static float solve(float x, float x1, float y1, float x2, float y2) {
        // Bisección sobre el parámetro u: x(u) es monótona para x1, x2 en [0, 1].
        float lo = 0f, hi = 1f, u = x;
        for (int k = 0; k < 40; k++) {
            float bx = bezier(u, x1, x2);
            if (Math.abs(bx - x) < 1e-6f) break;
            if (bx < x) lo = u; else hi = u;
            u = (lo + hi) / 2f;
        }
        return bezier(u, y1, y2);
    }

    private static float bezier(float u, float p1, float p2) {
        float v = 1f - u;
        return 3f * v * v * u * p1 + 3f * v * u * u * p2 + u * u * u;
    }
}
