package pakvothe.franlauncher.tablet.scene;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;

import java.util.ArrayList;
import java.util.List;

import pakvothe.franlauncher.tablet.anim.Animator;
import pakvothe.franlauncher.tablet.theme.Theme;
import pakvothe.franlauncher.tablet.util.Typography;

/** Panel desde abajo: menú de una app o ajustes. Filas de texto con valor opcional a la derecha. */
public final class SheetLayer extends Layer {
    public static final class Row {
        public final int id;
        public final String label;
        public String value;
        public final boolean danger;

        public Row(int id, String label, String value, boolean danger) {
            this.id = id;
            this.label = label;
            this.value = value;
            this.danger = danger;
        }
    }

    private final Animator animator;
    private final RenderNode scrim = new RenderNode("sheet.scrim");
    private final RenderNode card = new RenderNode("sheet.card");
    private final Paint title, sub, row, value, fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Row> rows = new ArrayList<>();
    private String t = "", st = "";
    private Theme theme;
    private float cardTop, cardH, insetBottom, insetTop, rowsTop, cardX, cardW;
    /** Posición de la tarjeta al empezar un arrastre. Si no entra en la pantalla, se desplaza hacia arriba. */
    private float dragFrom;
    private boolean open;

    public SheetLayer(Context context, Animator animator) {
        super("sheet");
        this.animator = animator;
        scrim.setHasOverlappingRendering(false);
        card.setHasOverlappingRendering(false);
        scrim.setAlpha(0f);
        title = Typography.paint(context, 30, 300, false);
        sub = Typography.paint(context, 13, 300, false);
        row = Typography.paint(context, 20, 300, false);
        value = Typography.paint(context, 17, 300, false);
        value.setTextAlign(Paint.Align.RIGHT);
    }

    public void setTheme(Theme th) {
        theme = th;
        title.setColor(th.text);
        sub.setColor(th.textSecondary);
        value.setColor(th.accent);
        recordAll();
    }

    /** Barra de navegación o teclado: si cambia con el panel abierto, la tarjeta se reacomoda. */
    public void setInsetTop(float t) {
        insetTop = t;
    }

    public void setInsetBottom(float b) {
        if (b == insetBottom) return;
        insetBottom = b;
        recordAll();
        if (open) animator.animate(card, Animator.TY, cardTop, 180);
    }

    public boolean isOpen() {
        return open;
    }

    public void show(String title, String subtitle, List<Row> r) {
        t = title;
        st = subtitle;
        rows.clear();
        rows.addAll(r);
        open = true;
        recordAll();
        card.setTranslationY(h);
        animator.animate(card, Animator.TY, cardTop, 260);
        animator.animate(scrim, Animator.ALPHA, 1f, 200);
    }

    /** Mismo panel con los valores cambiados (ajustes), sin animar. */
    public void replace(List<Row> r) {
        rows.clear();
        rows.addAll(r);
        recordAll();
        animator.animate(card, Animator.TY, cardTop, 0);
    }

    public void dragStart() {
        dragFrom = card.getTranslationY();
    }

    /** Lo más arriba que puede ir la tarjeta: su borde de abajo pegado al de la pantalla. */
    private float minTop() {
        return Math.min(cardTop, h - cardH);
    }

    /**
     * Arrastre: hacia arriba desplaza la tarjeta si es más alta que la pantalla (celular en horizontal);
     * hacia abajo de su lugar, la va cerrando.
     */
    public void drag(float dy) {
        float ty = Math.max(minTop(), dragFrom + dy);
        card.setTranslationY(ty);
        float down = Math.max(0, ty - cardTop);
        scrim.setAlpha(1f - Math.min(1f, down / Math.max(1, cardH)));
    }

    /** Al soltar: se cierra si bajó más de un cuarto de su lugar o con un tirón hacia abajo desde ahí. */
    public void release(float dy, float vy, float flingMin) {
        float ty = card.getTranslationY();
        float down = ty - cardTop;
        if (down > Math.min(cardH, h - cardTop) * 0.25f || (down > 0 && vy > flingMin)) hide();
        else if (down > 0) {
            animator.animate(card, Animator.TY, cardTop, 180);
            animator.animate(scrim, Animator.ALPHA, 1f, 180);
        }
    }

    public void hide() {
        if (!open) return;
        open = false;
        animator.animate(card, Animator.TY, h, 220);
        animator.animate(scrim, Animator.ALPHA, 0f, 220);
    }

    /** -1 = scrim (cerrar), -2 = tarjeta sin fila, si no el id de la fila. */
    public int hit(float x, float y) {
        float top = card.getTranslationY();
        if (y < top || x < cardX || x > cardX + cardW) return -1;
        float rh = 56 * s;
        int i = (int) ((y - top - rowsTop) / rh);
        if (y - top < rowsTop || i < 0 || i >= rows.size()) return -2;
        return rows.get(i).id;
    }

    @Override
    protected void onSizeChanged() {
        recordAll();
        if (open) card.setTranslationY(cardTop);
        else card.setTranslationY(h);
    }

    @Override
    protected void onRestore() {
        recordAll();
    }

    @Override
    protected void record(RecordingCanvas c) {
        c.drawRenderNode(scrim);
        c.drawRenderNode(card);
    }

    private void recordAll() {
        if (w == 0 || theme == null) return;
        title.setTextSize(30 * s);
        sub.setTextSize(13 * s);
        row.setTextSize(20 * s);
        value.setTextSize(17 * s);
        rowsTop = (st.isEmpty() ? 76 : 96) * s;
        cardH = rowsTop + rows.size() * 56 * s + 24 * s + insetBottom;
        cardW = Math.min(w, 640 * s);
        cardX = (w - cardW) / 2;
        // Si no entra, arranca debajo de la barra de estado y se desplaza con el dedo.
        cardTop = Math.max(insetTop, h - cardH);
        scrim.setPosition(0, 0, w, h);
        record(scrim, w, h, c -> c.drawColor(0x66000000));
        card.setPosition(0, 0, w, (int) cardH);
        record(card, w, (int) cardH, c -> {
            float r = 28 * s;
            fill.setColor(theme.sheet);
            c.drawRoundRect(cardX, 0, cardX + cardW, cardH + r, r, r, fill);
            fill.setColor(theme.handle);
            c.drawRoundRect(cardX + cardW / 2 - 20 * s, 12 * s, cardX + cardW / 2 + 20 * s, 16 * s, 2 * s, 2 * s, fill);
            float x = cardX + 32 * s;
            c.drawText(HomeLayer.ellipsize(t, title, cardW - 64 * s), x, 62 * s, title);
            if (!st.isEmpty()) c.drawText(st, x, 84 * s, sub);
            float y = rowsTop;
            for (Row rw : rows) {
                row.setColor(rw.danger ? theme.danger : theme.text);
                c.drawText(rw.label, x, y + 36 * s, row);
                if (rw.value != null) c.drawText(rw.value, cardX + cardW - 32 * s, y + 36 * s, value);
                y += 56 * s;
            }
        });
        invalidate();
    }
}
