package pakvothe.franlauncher.tablet.scene;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pakvothe.franlauncher.tablet.R;
import pakvothe.franlauncher.tablet.anim.Animator;
import pakvothe.franlauncher.tablet.data.AppEntry;
import pakvothe.franlauncher.tablet.data.Atlas;
import pakvothe.franlauncher.tablet.data.Fold;
import pakvothe.franlauncher.tablet.data.Letters;
import pakvothe.franlauncher.tablet.theme.Theme;
import pakvothe.franlauncher.tablet.util.Icons;
import pakvothe.franlauncher.tablet.util.Typography;

/**
 * Cajón: búsqueda, lista A-Z con separadores por letra, índice de letras y la letra gigante de fondo.
 * La lista entera se graba una vez por cambio de datos; desplazar es solo translationY del nodo.
 */
public final class DrawerLayer extends Layer {
    private static final int HEADER = -1;

    private final Context context;
    private final Animator animator;
    private final RenderNode bg = new RenderNode("drawer.bg");
    private final RenderNode search = new RenderNode("drawer.search");
    private final RenderNode viewport = new RenderNode("drawer.viewport");
    private final RenderNode list = new RenderNode("drawer.list");
    private final RenderNode index = new RenderNode("drawer.index");
    private final RenderNode big = new RenderNode("drawer.big");
    private final Paint label, header, letter, bigPaint, query, fill = new Paint(Paint.ANTI_ALIAS_FLAG),
        iconPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG), stroke = Icons.strokePaint();
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final RectF searchRect = new RectF();
    /** Filas: índice en apps, o HEADER. Y de cada fila y letra de cada header. */
    private final List<AppEntry> rowsApp = new ArrayList<>();
    private final List<Float> rowsY = new ArrayList<>();
    private final float[] letterY = new float[Letters.COUNT];
    private final boolean[] letterPresent = new boolean[Letters.COUNT];
    private List<AppEntry> apps = new ArrayList<>();
    private Set<String> hidden;
    private Map<String, Integer> dots;
    private Atlas atlas;
    private boolean showIcons = true;
    private Theme theme;
    private String q = "";
    private float top, viewTop, viewBottom, insetTop, insetBottom, content, scroll, x0, indexX;
    private int bigLetter = -1;

    public DrawerLayer(Context context, Animator animator) {
        super("drawer");
        this.context = context;
        this.animator = animator;
        for (RenderNode n : new RenderNode[] {bg, search, viewport, list, index, big}) n.setHasOverlappingRendering(false);
        viewport.setClipToBounds(true);
        big.setAlpha(0f);
        label = Typography.paint(context, 22, 300, false);
        header = Typography.paint(context, 14, 400, false);
        letter = Typography.paint(context, 12, 400, false);
        letter.setTextAlign(Paint.Align.CENTER);
        bigPaint = Typography.paint(context, 360, 200, false);
        bigPaint.setTextAlign(Paint.Align.RIGHT);
        query = Typography.paint(context, 22, 300, false);
    }

    public void setTheme(Theme t) {
        theme = t;
        label.setColor(t.text);
        header.setColor(t.accent);
        query.setColor(t.text);
        bigPaint.setColor(t.text);
        recordAll();
    }

    public void setData(List<AppEntry> apps, Set<String> hidden, Atlas atlas, boolean showIcons) {
        this.apps = apps;
        this.hidden = hidden;
        this.atlas = atlas;
        this.showIcons = showIcons;
        rebuild();
    }

    public void setDots(Map<String, Integer> dots) {
        this.dots = dots;
        recordList();
    }

    public void setInsets(float top, float bottom) {
        insetTop = top;
        insetBottom = bottom;
        recordAll();
    }

    public void setQuery(String q) {
        this.q = q;
        scroll = 0;
        rebuild();
    }

    public String query() {
        return q;
    }

    /** Primera app de la lista (Enter en el teclado la abre). */
    public AppEntry first() {
        for (AppEntry e : rowsApp) if (e != null) return e;
        return null;
    }

    /** Las alturas de fila dependen de la densidad: si se calcularon antes de conocerla, se rehacen. */
    @Override
    protected void onSizeChanged() {
        rebuild();
    }

    @Override
    protected void onRestore() {
        recordAll();
    }

    private void rebuild() {
        // Los Paint se crean antes de conocer la densidad: los tamaños se fijan acá, en dp.
        label.setTextSize(22 * s);
        header.setTextSize(14 * s);
        query.setTextSize(22 * s);
        rowsApp.clear();
        rowsY.clear();
        java.util.Arrays.fill(letterPresent, false);
        String fq = fold(q);
        List<AppEntry> shown = new ArrayList<>();
        for (AppEntry e : apps) {
            boolean match = fq.isEmpty() ? hidden == null || !hidden.contains(e.component) : fold(e.label).contains(fq);
            if (match) shown.add(e);
        }
        shown.sort((a, b) -> Fold.compare(a.label, b.label));
        float y = 0;
        int last = -1;
        for (AppEntry e : shown) {
            int l = Letters.bucket(e.label);
            if (l != last && fq.isEmpty()) {
                last = l;
                letterPresent[l] = true;
                letterY[l] = y;
                rowsApp.add(null);
                rowsY.add(y);
                y += 34 * s;
            }
            rowsApp.add(e);
            rowsY.add(y);
            y += 60 * s;
        }
        content = y + 16 * s;
        recordAll();
    }

    private static String fold(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) b.append(Fold.fold(s.charAt(i)));
        return b.toString();
    }

    // --- Geometría y toques ---

    public boolean inSearch(float x, float y) {
        return searchRect.contains(x, y - node.getTranslationY());
    }

    public boolean inIndex(float x, float y) {
        return x >= indexX - 8 * s && y >= viewTop && y <= viewBottom;
    }

    /** App bajo el dedo, o null. */
    public AppEntry appAt(float x, float y) {
        if (y < viewTop || y > viewBottom || x > indexX - 8 * s) return null;
        float ly = y - viewTop + scroll;
        for (int i = rowsY.size() - 1; i >= 0; i--) {
            if (ly >= rowsY.get(i)) {
                AppEntry e = rowsApp.get(i);
                return e != null && ly < rowsY.get(i) + 60 * s ? e : null;
            }
        }
        return null;
    }

    public boolean atTop() {
        return scroll <= 0.5f;
    }

    public float maxScroll() {
        return Math.max(0, content - (viewBottom - viewTop));
    }

    public void scrollBy(float dy) {
        setScroll(scroll + dy);
    }

    public void setScroll(float v) {
        scroll = Math.max(0, Math.min(maxScroll(), v));
        list.setTranslationY(-scroll);
    }

    public float scroll() {
        return scroll;
    }

    /** Arrastre sobre el índice: salta a la letra y la muestra gigante de fondo. */
    public void scrub(float y) {
        int n = presentCount();
        if (n == 0) return;
        float t = (y - viewTop) / (viewBottom - viewTop);
        int k = Math.max(0, Math.min(n - 1, (int) (t * n)));
        int l = nthPresent(k);
        setScroll(letterY[l]);
        if (l != bigLetter) {
            bigLetter = l;
            recordBig();
            recordIndex();
        }
        animator.animate(big, Animator.ALPHA, 0.12f, 120);
    }

    public void scrubEnd() {
        animator.animate(big, Animator.ALPHA, 0f, 400);
    }

    private int presentCount() {
        int n = 0;
        for (boolean b : letterPresent) if (b) n++;
        return n;
    }

    private int nthPresent(int k) {
        for (int i = 0; i < Letters.COUNT; i++) if (letterPresent[i] && k-- == 0) return i;
        return 0;
    }

    // --- Grabado ---

    @Override
    protected void record(RecordingCanvas c) {
        c.drawRenderNode(bg);
        c.drawRenderNode(big);
        c.drawRenderNode(search);
        c.drawRenderNode(viewport);
        c.drawRenderNode(index);
    }

    private void recordAll() {
        if (w == 0 || theme == null) return;
        x0 = 40 * s;
        indexX = w - 26 * s;
        float sTop = insetTop + 24 * s;
        searchRect.set(x0, sTop, w - x0, sTop + 60 * s);
        viewTop = searchRect.bottom + 12 * s;
        viewBottom = h - insetBottom;
        for (RenderNode n : new RenderNode[] {bg, search, index, big}) n.setPosition(0, 0, w, h);
        record(bg, w, h, c -> c.drawColor(Theme.alpha(theme.bg, 0.94f)));
        recordSearch();
        viewport.setPosition(0, (int) viewTop, w, (int) viewBottom);
        recordList();
        recordIndex();
        recordBig();
        setScroll(scroll);
        invalidate();
    }

    private void recordSearch() {
        record(search, w, h, c -> {
            fill.setColor(theme.fieldBg);
            float r = searchRect.height() / 2;
            c.drawRoundRect(searchRect, r, r, fill);
            stroke.setColor(theme.textSecondary);
            Icons.draw(c, Icons.SEARCH, searchRect.left + 32 * s, searchRect.centerY(), 22 * s, fill, stroke);
            float tx = searchRect.left + 58 * s, by = searchRect.centerY() + 8 * s;
            if (q.isEmpty()) {
                query.setColor(theme.textSecondary);
                c.drawText(context.getString(R.string.search), tx, by, query);
                query.setColor(theme.text);
            } else {
                c.drawText(q, tx, by, query);
                float cx = tx + query.measureText(q) + 3 * s;
                fill.setColor(theme.accent);
                c.drawRect(cx, by - 22 * s, cx + 2 * s, by + 4 * s, fill);
            }
        });
    }

    private void recordList() {
        if (w == 0 || theme == null) return;
        int lw = w, lh = (int) Math.max(1, content);
        list.setPosition(0, 0, lw, lh);
        float ic = showIcons && atlas != null ? 44 * s : 0;
        record(list, lw, lh, c -> {
            for (int i = 0; i < rowsApp.size(); i++) {
                float y = rowsY.get(i);
                AppEntry e = rowsApp.get(i);
                if (e == null) {
                    AppEntry next = i + 1 < rowsApp.size() ? rowsApp.get(i + 1) : null;
                    if (next != null) {
                        int l = Letters.bucket(next.label);
                        c.drawText(Letters.LABELS, l, l + 1, x0, y + 26 * s, header);
                    }
                    continue;
                }
                float x = x0;
                if (ic > 0) {
                    atlas.cell(e.icon, src);
                    dst.set(x, y + 8 * s, x + ic, y + 8 * s + ic);
                    c.drawBitmap(atlas.bitmap, src, dst, iconPaint);
                    x += ic + 16 * s;
                }
                boolean hiddenApp = hidden != null && hidden.contains(e.component);
                label.setAlpha(hiddenApp ? 120 : 255);
                c.drawText(HomeLayer.ellipsize(e.label, label, indexX - 24 * s - x), x, y + 38 * s, label);
                label.setAlpha(255);
                Integer n = dots != null ? dots.get(e.packageName()) : null;
                if (n != null && n > 0) {
                    fill.setColor(theme.dot);
                    c.drawCircle(x + label.measureText(e.label) + 12 * s, y + 24 * s, 4.5f * s, fill);
                }
            }
        });
        record(viewport, w, (int) (viewBottom - viewTop), c -> c.drawRenderNode(list));
    }

    private void recordIndex() {
        record(index, w, h, c -> {
            int n = presentCount();
            if (n == 0) return;
            float step = (viewBottom - viewTop - 24 * s) / n;
            float y = viewTop + 12 * s + step / 2;
            for (int i = 0; i < Letters.COUNT; i++) {
                if (!letterPresent[i]) continue;
                boolean on = i == bigLetter;
                letter.setColor(on ? theme.accent : theme.textSecondary);
                letter.setTextSize((on ? 17 : 12) * s);
                c.drawText(Letters.LABELS, i, i + 1, indexX, y + 4 * s, letter);
                y += step;
            }
        });
    }

    private void recordBig() {
        bigPaint.setTextSize(360 * s);
        record(big, w, h, c -> {
            if (bigLetter >= 0) c.drawText(Letters.LABELS, bigLetter, bigLetter + 1, w - 60 * s, viewTop + 340 * s, bigPaint);
        });
    }
}
