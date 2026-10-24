package pakvothe.franlauncher.tablet.scene;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;

import java.util.Map;

import pakvothe.franlauncher.tablet.R;
import pakvothe.franlauncher.tablet.data.AppEntry;
import pakvothe.franlauncher.tablet.data.Atlas;
import pakvothe.franlauncher.tablet.theme.Theme;
import pakvothe.franlauncher.tablet.util.Icons;
import pakvothe.franlauncher.tablet.util.Typography;

/**
 * Favoritas con peso por uso, de abajo para arriba (la más pesada al lado del pulgar), y la píldora de lo que suena.
 * Un RenderNode por fila: apretar es solo alfa, no re-graba.
 */
public final class HomeLayer extends Layer {
    /** Tope de favoritas en el inicio (un nodo por fila, se crean una vez). */
    public static final int MAX = 40;
    public static final int HIT_NONE = -1, HIT_PILL = -2, HIT_PILL_BUTTON = -3, HIT_MORE = -4, HIT_LESS = -5;
    private static final float[] SIZES = {64, 54, 46, 38, 32, 28};

    private final Context context;
    private final RenderNode[] rows = new RenderNode[MAX];
    private final RenderNode pill = new RenderNode("home.pill");
    private final RectF[] hit = new RectF[MAX];
    /** Y de cada fila sin desplazamiento: desplazar es solo translationY, nunca re-grabar. */
    private final float[] baseY = new float[MAX];
    private final RenderNode less = new RenderNode("home.less");
    /** Contenedor de las filas que recorta arriba de la píldora: al desplazar no pasan por debajo. */
    private final RenderNode list = new RenderNode("home.list");
    private final RectF moreRect = new RectF(), lessRect = new RectF(), tmp = new RectF();
    /** Divisor centrado y tenue: lo dibuja cada fila en su borde de abajo (va con ella al desplazar). */
    private final Paint divider = new Paint();
    /** "Ver más": la lista ocupa la pantalla y se desplaza; hidden = filas que no entran sin expandir. */
    private boolean expanded;
    private int hidden;
    private float scroll, maxScroll, viewTop, insetTop;
    private final RectF pillRect = new RectF(), pillButton = new RectF();
    private final Paint[] name = new Paint[3];
    private final Paint today, hint, more, count, pillTitle, pillSub, fill = new Paint(Paint.ANTI_ALIAS_FLAG),
        iconPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG), stroke = Icons.strokePaint();
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private AppEntry[] favs = new AppEntry[0];
    private String[] todayText = new String[0];
    private Map<String, Integer> dots;
    private Atlas atlas;
    private boolean showIcons = true, landscape;
    private Theme theme;
    private String pillT = "", pillS = "";
    /** Carátula chica (≤ 112 px) de lo que suena, o null. */
    private android.graphics.Bitmap pillArt;
    private boolean playing, pillVisible;
    private float x0, colW, listBottom, listTop, insetBottom, clockBottom, layerTop;

    public HomeLayer(Context context) {
        super("home");
        this.context = context;
        for (int i = 0; i < MAX; i++) {
            rows[i] = new RenderNode("home.row");
            rows[i].setHasOverlappingRendering(false);
            hit[i] = new RectF();
        }
        pill.setHasOverlappingRendering(false);
        name[0] = Typography.paint(context, 40, 200, false);
        name[1] = Typography.paint(context, 40, 300, false);
        name[2] = Typography.paint(context, 40, 400, false);
        today = Typography.paint(context, 14, 400, false);
        // Contador "● 4": cifras tabulares para que no baile al cambiar.
        count = Typography.paint(context, 13, 400, true);
        hint = Typography.paint(context, 16, 300, false);
        hint.setTextAlign(Paint.Align.CENTER);
        more = Typography.paint(context, 14, 400, false);
        more.setTextAlign(Paint.Align.CENTER);
        less.setHasOverlappingRendering(false);
        list.setHasOverlappingRendering(false);
        list.setClipToBounds(true);
        pillTitle = Typography.paint(context, 15, 400, false);
        pillSub = Typography.paint(context, 13, 300, false);
    }

    public void setTheme(Theme t) {
        theme = t;
        for (Paint p : name) p.setColor(t.text);
        today.setColor(t.textSecondary);
        count.setColor(t.dot);
        hint.setColor(t.textSecondary);
        more.setColor(t.textSecondary);
        pillTitle.setColor(t.text);
        pillSub.setColor(t.textSecondary);
        recordAll();
    }

    public void setData(AppEntry[] favs, String[] todayText, Atlas atlas, boolean showIcons) {
        this.favs = favs;
        this.todayText = todayText;
        this.atlas = atlas;
        this.showIcons = showIcons;
        recordAll();
    }

    public void setDots(Map<String, Integer> dots) {
        this.dots = dots;
        recordAll();
    }

    public void setPill(String title, String sub, boolean playing, android.graphics.Bitmap art) {
        pillVisible = !title.isEmpty();
        pillArt = art;
        pillT = title;
        pillS = sub;
        this.playing = playing;
        recordAll();
    }

    /** clockBottom: dónde termina el reloj; insetTop/insetBottom: barras del sistema. */
    public void place(float clockBottom, float insetTop, float insetBottom, boolean landscape) {
        this.clockBottom = clockBottom;
        this.insetTop = insetTop;
        this.insetBottom = insetBottom;
        this.landscape = landscape;
        recordAll();
    }

    @Override
    protected void onSizeChanged() {
        recordAll();
    }

    @Override
    protected void onRestore() {
        recordAll();
    }

    public int rowCount() {
        return Math.min(favs.length, MAX);
    }

    public int hit(float x, float y) {
        if (pillVisible && pillRect.contains(x, y)) return pillButton.contains(x, y) ? HIT_PILL_BUTTON : HIT_PILL;
        if (expanded) {
            if (y < viewTop) return HIT_NONE;
            if (lessRect.contains(x, y - scroll)) return HIT_LESS;
            for (int i = 0; i < rowCount(); i++) if (hit[i].contains(x, y - scroll)) return i;
            return HIT_NONE;
        }
        if (hidden > 0 && moreRect.contains(x, y)) return HIT_MORE;
        for (int i = 0; i < rowCount(); i++) if (hit[i].contains(x, y)) return i;
        return HIT_NONE;
    }

    /** Rect de la fila en pantalla: de ahí sale la animación de apertura de la app. */
    public RectF rowRect(int i) {
        tmp.set(hit[i]);
        if (expanded) tmp.offset(0, scroll);
        return tmp;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean e) {
        if (expanded == e) return;
        expanded = e;
        scroll = 0;
        recordAll();
    }

    /** Deslizar la lista expandida: solo mueve los nodos de las filas. Devuelve si se movió. */
    public boolean scrollBy(float dy) {
        return setScroll(scroll + dy);
    }

    public boolean setScroll(float v) {
        float n = Math.max(0, Math.min(maxScroll, v));
        if (n == scroll) return false;
        scroll = n;
        for (int i = 0; i < rowCount(); i++) rows[i].setTranslationY(baseY[i] + scroll);
        less.setTranslationY(lessRect.top + scroll);
        return true;
    }

    public float scroll() {
        return scroll;
    }

    public float maxScroll() {
        return maxScroll;
    }

    public void press(int i, boolean down) {
        if (i >= 0 && i < rowCount()) rows[i].setAlpha(down ? 0.55f * rowAlpha(i) : rowAlpha(i));
        else if (i == HIT_PILL || i == HIT_PILL_BUTTON) pill.setAlpha(down ? 0.7f : 1f);
    }

    @Override
    protected void record(RecordingCanvas c) {
        c.translate(0, -layerTop);
        c.drawRenderNode(list);
        if (pillVisible) c.drawRenderNode(pill);
        if (expanded) return;
        // "+N más" arriba de la lista si sobran favoritas.
        if (hidden > 0 && w > 0) c.drawText(context.getString(R.string.more_apps, hidden), x0 + colW / 2, moreRect.centerY() + 5 * s, more);
        // Flecha sutil arriba de todo: deslizando hacia arriba se abre el cajón.
        if (w > 0 && theme != null) {
            stroke.setColor(Theme.alpha(theme.text, 0.35f));
            float ay = (favs.length == 0 ? listBottom : hidden > 0 ? moreRect.top : listTop) - 22 * s;
            Icons.draw(c, Icons.CHEVRON_UP, x0 + colW / 2, ay, 26 * s, fill, stroke);
        }
        if (favs.length == 0 && w > 0) {
            float y = (clockBottom + listBottom) / 2;
            String[] lines = context.getString(R.string.empty_home).split("\n");
            for (int k = 0; k < lines.length; k++) c.drawText(lines[k], x0 + colW / 2, y + k * 24 * s, hint);
        }
    }

    private float rowHeight(float fs, boolean sub) {
        return Math.max(44 * s, fs * 1.2f) + (sub ? 18 * s : 0);
    }

    /** Alto total de la lista con los tamaños multiplicados por f. */
    private float listHeight(float f) {
        float total = 0;
        for (int i = 0; i < rowCount(); i++) {
            boolean sub = i < todayText.length && todayText[i] != null;
            total += rowHeight(SIZES[Math.min(i, SIZES.length - 1)] * s * f, sub);
        }
        return total;
    }

    private static float rowAlpha(int i) {
        return Math.max(0.5f, 1f - i * 0.09f);
    }

    private void recordAll() {
        if (w == 0 || theme == null) return;
        today.setTextSize(14 * s);
        hint.setTextSize(16 * s);
        more.setTextSize(14 * s);
        pillTitle.setTextSize(15 * s);
        pillSub.setTextSize(13 * s);
        // Celular (lado corto < 600 dp): márgenes de 24 dp para aprovechar el ancho. Tablet: 48 dp.
        float side = (Math.min(w, h) < 600 * s ? 24 : 48) * s;
        x0 = landscape ? w * 0.52f : side;
        colW = w - x0 - side;
        float bottom = h - insetBottom - 28 * s;
        // Píldora: abajo de todo.
        float ph = 60 * s;
        if (pillVisible) {
            pillRect.set(x0, bottom - ph, x0 + colW, bottom);
            recordPill(ph);
            pill.setPosition(0, 0, (int) pillRect.width(), (int) ph);
            pill.setTranslationX(pillRect.left);
            pill.setTranslationY(pillRect.top);
            bottom = pillRect.top - 28 * s;
        }
        listBottom = bottom;
        float dw = colW * 0.90f, dx = (colW - dw) / 2;
        int line = Theme.alpha(theme.text, 0.14f);
        divider.setShader(new android.graphics.LinearGradient(dx, 0, dx + dw, 0, new int[] {line & 0xFFFFFF, line, line & 0xFFFFFF},
            null, android.graphics.Shader.TileMode.CLAMP));
        // Filas de abajo para arriba: la 0 (más pesada) es la de abajo. Primero se achican parejo (hasta 55%).
        // Cerrada: lo que no entra se oculta y aparece "+N más". Expandida: entra todo y se desplaza.
        float narrow = Math.min(1f, colW / (420 * s));
        viewTop = (landscape ? clockBottom : insetTop) + 16 * s;
        float top = expanded ? viewTop + 48 * s : clockBottom + 8 * s + 80 * s;
        float room = bottom - top;
        float k = 1f;
        while (k > 0.55f && listHeight(narrow * k) > room) k -= 0.05f;
        float y = bottom;
        listTop = bottom;
        hidden = 0;
        for (int i = 0; i < rowCount(); i++) {
            AppEntry e = favs[i];
            float fs = SIZES[Math.min(i, SIZES.length - 1)] * s * narrow * k;
            boolean sub = i < todayText.length && todayText[i] != null;
            float rh = rowHeight(fs, sub);
            if (!expanded && (hidden > 0 || y - rh < top)) {
                hidden++;
                hit[i].setEmpty();
                rows[i].setAlpha(0f);
                continue;
            }
            y -= rh;
            listTop = y;
            baseY[i] = y;
            hit[i].set(x0, y, x0 + colW, y + rh);
            int rw = (int) colW, rhi = (int) rh;
            rows[i].setPosition(0, 0, rw, rhi);
            rows[i].setTranslationX(x0);
            rows[i].setTranslationY(y + (expanded ? scroll : 0));
            rows[i].setAlpha(rowAlpha(i));
            Paint p = name[i < 2 ? 2 : i < 4 ? 1 : 0];
            p.setTextSize(fs);
            String todayLine = sub ? todayText[i] : null;
            Integer n = dots != null ? dots.get(e.packageName()) : null;
            String dot = n != null && n > 0 ? "● " + n : null;
            float ic = showIcons && atlas != null ? Math.max(40 * s, fs * 0.9f) : 0;
            boolean line0 = i > 0;
            record(rows[i], rw, rhi, rc -> {
                drawRow(rc, e, p, fs, ic, rh, todayLine, dot);
                // Divisor en el borde de abajo (entre esta fila y la de abajo): se mueve con la fila.
                if (line0) rc.drawRect(dx, rh - Math.max(1f, s * 0.6f), dx + dw, rh, divider);
            });
        }
        moreRect.set(x0, listTop - 40 * s, x0 + colW, listTop);
        if (expanded) {
            // "Ver menos" arriba de la primera fila; desplazar hasta acá lo deja a la vista.
            lessRect.set(x0, listTop - 48 * s, x0 + colW, listTop);
            int lw = (int) colW, lh = (int) (48 * s);
            less.setPosition(0, 0, lw, lh);
            less.setTranslationX(x0);
            less.setTranslationY(lessRect.top + scroll);
            record(less, lw, lh, c -> c.drawText(context.getString(R.string.less_apps), lw / 2f, lh / 2f + 5 * s, more));
            maxScroll = Math.max(0, viewTop - lessRect.top);
            scroll = Math.min(scroll, maxScroll);
        } else {
            maxScroll = 0;
            scroll = 0;
        }
        // Nodo del tamaño justo; expandida, recorta en viewTop (lo de arriba se ve al desplazar).
        float aboveList = hidden > 0 ? 88 * s : 48 * s;
        layerTop = expanded ? viewTop : Math.max(0, (favs.length == 0 ? Math.min(listBottom, clockBottom) : listTop) - aboveList);
        node.setPosition(0, (int) layerTop, w, h);
        // Las filas van en un nodo recortado de layerTop hasta el borde de abajo de la lista.
        int lt = (int) layerTop, lb = (int) Math.ceil(listBottom + 2 * s);
        list.setPosition(0, lt, w, Math.max(lt + 1, lb));
        record(list, w, Math.max(1, lb - lt), c -> {
            c.translate(0, -lt);
            for (int i = 0; i < rowCount(); i++) c.drawRenderNode(rows[i]);
            if (expanded) c.drawRenderNode(less);
        });
        invalidate();
    }

    private void drawRow(RecordingCanvas c, AppEntry e, Paint p, float fs, float ic, float rh, String todayLine, String dot) {
        float textH = fs * 0.72f + (todayLine != null ? 18 * s : 0);
        float top = (rh - textH) / 2;
        // Nombre a la izquierda, ícono a la derecha: la fila ocupa todo el ancho.
        float x = 0;
        if (ic > 0) {
            atlas.cell(e.icon, src);
            float iy = (rh - ic) / 2, rw = colW;
            dst.set(rw - ic, iy, rw, iy + ic);
            c.drawBitmap(atlas.bitmap, src, dst, iconPaint);
        }
        float base = top + fs * 0.72f;
        count.setTextSize(13 * s);
        // El nombre se corta con "…" para dejar lugar al contador y al ícono de la derecha.
        float room = colW - (ic > 0 ? ic + 16 * s : 0) - (dot != null ? count.measureText(dot) + 16 * s : 0);
        String name = ellipsize(e.label, p, room);
        c.drawText(name, x, base, p);
        if (dot != null) c.drawText(dot, x + p.measureText(name) + 12 * s, base - fs * 0.36f + 5 * s, count);
        if (todayLine != null) c.drawText(todayLine, x + 2 * s, base + 18 * s, today);
    }

    private void recordPill(float ph) {
        float pw = pillRect.width();
        float r = ph / 2;
        pillButton.set(pillRect.right - 8 * s - 44 * s, pillRect.top + (ph - 44 * s) / 2, pillRect.right - 8 * s,
            pillRect.top + (ph + 44 * s) / 2);
        record(pill, (int) pw, (int) ph, c -> {
            fill.setColor(theme.pillBg);
            c.drawRoundRect(0, 0, pw, ph, r, r, fill);
            float tx = 22 * s;
            // Carátula chica a la izquierda, redondeada: un shader del bitmap (solo al grabar, no por frame).
            if (pillArt != null) {
                float a = 44 * s, ax = 8 * s, ay = (ph - a) / 2;
                android.graphics.Matrix m = new android.graphics.Matrix();
                float sc = a / Math.min(pillArt.getWidth(), pillArt.getHeight());
                m.setScale(sc, sc);
                m.postTranslate(ax + (a - pillArt.getWidth() * sc) / 2, ay + (a - pillArt.getHeight() * sc) / 2);
                android.graphics.BitmapShader sh = new android.graphics.BitmapShader(pillArt,
                    android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
                sh.setLocalMatrix(m);
                Paint ap = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                ap.setShader(sh);
                c.drawRoundRect(ax, ay, ax + a, ay + a, a / 2, a / 2, ap);
                tx = ax + a + 14 * s;
            }
            float maxW = pw - tx - 64 * s;
            String t = ellipsize(pillT, pillTitle, maxW), sb = ellipsize(pillS, pillSub, maxW);
            c.drawText(t, tx, ph / 2 - 2 * s, pillTitle);
            c.drawText(sb, tx, ph / 2 + 16 * s, pillSub);
            float bx = pw - 8 * s - 22 * s, by = ph / 2;
            fill.setColor(theme.text);
            c.drawCircle(bx, by, 22 * s, fill);
            fill.setColor(theme.bg);
            Icons.draw(c, playing ? Icons.PAUSE : Icons.PLAY, bx, by, 18 * s, fill, stroke);
        });
    }

    static String ellipsize(String s, Paint p, float max) {
        if (p.measureText(s) <= max) return s;
        int n = p.breakText(s, true, max - p.measureText("…"), null);
        return s.substring(0, Math.max(0, n)) + "…";
    }
}
