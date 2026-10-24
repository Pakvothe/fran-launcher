package pakvothe.franlauncher.tablet.scene;

import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;

/** Un RenderNode que se re-graba solo cuando está sucio. Las subclases manejan sus nodos hijos. */
public abstract class Layer {
    public final RenderNode node;
    protected int w, h;
    /** dp → px reales. */
    protected float s = 1f;
    private boolean dirty = true;

    protected Layer(String name) {
        node = new RenderNode(name);
        node.setHasOverlappingRendering(false);
    }

    public void setSize(int w, int h, float density) {
        this.w = w;
        this.h = h;
        s = density;
        node.setPosition(0, 0, w, h);
        onSizeChanged();
        invalidate();
    }

    protected void onSizeChanged() {}

    /**
     * El sistema borró las display lists (al liberar la ventana cuando abre una app pesada, destroyHardwareResources
     * se lleva también los nodos hijos). Cada capa vuelve a grabar todo lo suyo.
     */
    public void restore() {
        invalidate();
        if (w > 0) onRestore();
    }

    protected void onRestore() {}

    public final void invalidate() {
        dirty = true;
    }

    /** Graba los hijos sucios y, si hace falta, el nodo propio. Se llama en cada frame del Stage. */
    public final void draw(Canvas canvas) {
        prepare();
        if (dirty) {
            dirty = false;
            RecordingCanvas c = node.beginRecording(w, h);
            try {
                record(c);
            } finally {
                node.endRecording();
            }
        }
        canvas.drawRenderNode(node);
    }

    /** Hook para re-grabar nodos hijos antes de dibujar. */
    protected void prepare() {}

    protected abstract void record(RecordingCanvas c);

    protected static void record(RenderNode n, int w, int h, Recorder r) {
        RecordingCanvas c = n.beginRecording(w, h);
        try {
            r.record(c);
        } finally {
            n.endRecording();
        }
    }

    protected interface Recorder {
        void record(RecordingCanvas c);
    }
}
