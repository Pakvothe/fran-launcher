package pakvothe.franlauncher.tablet.scene;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.graphics.Shader;

import pakvothe.franlauncher.tablet.anim.Animator;
import pakvothe.franlauncher.tablet.data.Config;
import pakvothe.franlauncher.tablet.theme.Theme;

/**
 * Fondo: color de la fase + 2 orbes difusos fijos, o nada (el fondo del sistema se ve a través de la ventana).
 * Encima, el oscurecido de la noche (y el velo sobre la foto del sistema).
 */
public final class SkyLayer extends Layer {
    private static final int ORB_TEX = 128;
    private static final int CROSSFADE = 2000;

    private final Animator animator;
    private final RenderNode base = new RenderNode("sky.base");
    private final RenderNode fade = new RenderNode("sky.fade");
    private final RenderNode orbA = new RenderNode("sky.orbA");
    private final RenderNode orbB = new RenderNode("sky.orbB");
    private final RenderNode dim = new RenderNode("sky.dim");
    private final Paint fill = new Paint();
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect orbDst = new Rect();
    private Bitmap texA, texB;
    private Theme theme;
    private int mode = Config.BG_ORBS;
    private float orbPx, bxA, byA, bxB, byB;

    private final Runnable fadeDone = () -> {
        recordBase(base, theme);
        fade.setAlpha(0f);
    };

    public SkyLayer(Animator animator) {
        super("sky");
        this.animator = animator;
        for (RenderNode n : new RenderNode[] {base, fade, orbA, orbB, dim}) n.setHasOverlappingRendering(false);
        fade.setAlpha(0f);
        dim.setAlpha(0f);
    }

    public void setMode(int m) {
        mode = m;
        if (theme != null && w > 0) setTheme(theme, false);
    }

    public void setTheme(Theme t, boolean animate) {
        Theme old = theme;
        theme = t;
        if (w == 0) return;
        texA = orbTexture(texA, t.orbA);
        texB = orbTexture(texB, t.orbB);
        recordOrbs();
        record(dim, w, h, c -> c.drawColor(0xFF000000));
        boolean night = t.phase == pakvothe.franlauncher.tablet.theme.PhaseResolver.NIGHT;
        // De noche se baja el brillo sin una capa negra a pantalla completa (overdraw de cada frame):
        // los orbes se atenúan por alfa. Solo el fondo del sistema, que puede ser una foto clara, usa la capa.
        // Con foto del sistema, un velo oscuro siempre (las letras son claras y la foto puede ser clara o cargada).
        float veil = mode == Config.BG_WALLPAPER ? (night ? 0.5f : 0.38f) : 0f;
        animator.animate(dim, Animator.ALPHA, veil, animate ? CROSSFADE : 0);
        float orbs = mode == Config.BG_ORBS ? (night ? 0.75f : 1f) : 0f;
        orbA.setAlpha(orbs);
        orbB.setAlpha(orbs);
        if (old == null || !animate) {
            recordBase(base, t);
            fade.setAlpha(0f);
        } else {
            recordBase(fade, t);
            fade.setAlpha(0f);
            animator.animate(fade, Animator.ALPHA, 1f, CROSSFADE, fadeDone);
        }
    }


    @Override
    protected void onRestore() {
        if (theme != null) setTheme(theme, false);
    }

    @Override
    protected void onSizeChanged() {
        for (RenderNode n : new RenderNode[] {base, fade, dim}) n.setPosition(0, 0, w, h);
        float big = Math.max(w, h);
        orbPx = big * 0.95f;
        int o = Math.round(orbPx);
        orbA.setPosition(0, 0, o, o);
        orbB.setPosition(0, 0, o, o);
        orbDst.set(0, 0, o, o);
        bxA = w * 0.10f - orbPx / 2; byA = h * 0.12f - orbPx / 2;
        bxB = w * 0.95f - orbPx / 2; byB = h * 0.92f - orbPx / 2;
        if (theme != null) setTheme(theme, false);
        placeOrbs();
    }

    /** Orbes fijos: se ubican una vez por tamaño. Sin animación ni ticker (decisión de Fran, 2026-10-23). */
    private void placeOrbs() {
        orbA.setTranslationX(bxA);
        orbA.setTranslationY(byA);
        orbB.setTranslationX(bxB);
        orbB.setTranslationY(byB);
    }

    @Override
    protected void record(RecordingCanvas c) {
        c.drawRenderNode(base);
        c.drawRenderNode(fade);
        c.drawRenderNode(orbA);
        c.drawRenderNode(orbB);
        c.drawRenderNode(dim);
    }

    private void recordBase(RenderNode n, Theme t) {
        if (mode == Config.BG_WALLPAPER) {
            // Transparente explícito: limpia el buffer para que se vea el fondo del sistema y no queden restos.
            record(n, w, h, c -> c.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR));
            return;
        }
        int color = mode == Config.BG_BLACK ? 0xFF000000 : mode == Config.BG_WHITE ? 0xFFF6F4F0 : t.bg;
        fill.setColor(color);
        record(n, w, h, c -> c.drawColor(color));
    }

    private void recordOrbs() {
        record(orbA, orbDst.right, orbDst.bottom, c -> c.drawBitmap(texA, null, orbDst, bmp));
        record(orbB, orbDst.right, orbDst.bottom, c -> c.drawBitmap(texB, null, orbDst, bmp));
    }

    /** Gradiente radial de 128×128 que se dibuja escalado: al ser difuso no se nota la baja resolución. */
    private static Bitmap orbTexture(Bitmap reuse, int color) {
        Bitmap b = reuse != null ? reuse : Bitmap.createBitmap(ORB_TEX, ORB_TEX, Bitmap.Config.ARGB_8888);
        b.eraseColor(0);
        int c0 = 0xD0 << 24 | (color & 0xFFFFFF);
        int c1 = 0x50 << 24 | (color & 0xFFFFFF);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = ORB_TEX / 2f;
        p.setShader(new RadialGradient(r, r, r, new int[] {c0, c1, color & 0xFFFFFF}, new float[] {0f, 0.4f, 1f},
            Shader.TileMode.CLAMP));
        new Canvas(b).drawCircle(r, r, r, p);
        return b;
    }
}
