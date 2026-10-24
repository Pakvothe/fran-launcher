package pakvothe.franlauncher.tablet.anim;

import android.graphics.RenderNode;
import android.view.Choreographer;
import android.view.View;

/**
 * Motor de animación propio sobre propiedades de RenderNode. Solo pide frames mientras haya algo activo.
 * Re-targeting: si una propiedad ya se está animando, la nueva parte del valor actual. Sin allocations por frame.
 */
public final class Animator implements Choreographer.FrameCallback {
    public static final int ALPHA = 0, SCALE = 1, TX = 2, TY = 3;
    private static final int MAX = 128;

    private final View host;
    private final RenderNode[] node = new RenderNode[MAX];
    private final int[] prop = new int[MAX];
    private final float[] from = new float[MAX], to = new float[MAX];
    private final long[] start = new long[MAX];
    private final int[] dur = new int[MAX];
    private final Runnable[] end = new Runnable[MAX];
    private int count;
    private boolean posted;

    public Animator(View host) {
        this.host = host;
    }

    public void animate(RenderNode n, int p, float target, int durationMs) {
        animate(n, p, target, durationMs, null);
    }

    public void animate(RenderNode n, int p, float target, int durationMs, Runnable onEnd) {
        int i = find(n, p);
        if (durationMs <= 0) {
            if (i >= 0) remove(i);
            set(n, p, target);
            host.invalidate();
            if (onEnd != null) onEnd.run();
            return;
        }
        float current = get(n, p);
        if (i < 0) {
            if (current == target && onEnd == null) return;
            if (count == MAX) { set(n, p, target); return; }
            i = count++;
            node[i] = n;
            prop[i] = p;
        }
        from[i] = current;
        to[i] = target;
        start[i] = -1;
        dur[i] = durationMs;
        end[i] = onEnd;
        if (!posted) {
            posted = true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    /** Valor final si se está animando, si no el actual. */
    public float target(RenderNode n, int p) {
        int i = find(n, p);
        return i >= 0 ? to[i] : get(n, p);
    }

    public boolean isRunning() {
        return count > 0;
    }

    public void cancelAll() {
        for (int i = count - 1; i >= 0; i--) {
            set(node[i], prop[i], to[i]);
            Runnable r = end[i];
            remove(i);
            if (r != null) r.run();
        }
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        posted = false;
        long now = frameTimeNanos / 1_000_000L;
        for (int i = count - 1; i >= 0; i--) {
            if (start[i] < 0) start[i] = now;
            float t = (now - start[i]) / (float) dur[i];
            float v = from[i] + (to[i] - from[i]) * Easing.standard(t);
            set(node[i], prop[i], v);
            if (t >= 1f) {
                Runnable r = end[i];
                remove(i);
                if (r != null) r.run();
            }
        }
        host.invalidate();
        if (count > 0 && !posted) {
            posted = true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    private int find(RenderNode n, int p) {
        for (int i = 0; i < count; i++) if (node[i] == n && prop[i] == p) return i;
        return -1;
    }

    private void remove(int i) {
        int last = --count;
        node[i] = node[last];
        prop[i] = prop[last];
        from[i] = from[last];
        to[i] = to[last];
        start[i] = start[last];
        dur[i] = dur[last];
        end[i] = end[last];
        node[last] = null;
        end[last] = null;
    }

    private static float get(RenderNode n, int p) {
        switch (p) {
            case ALPHA: return n.getAlpha();
            case SCALE: return n.getScaleX();
            case TX: return n.getTranslationX();
            default: return n.getTranslationY();
        }
    }

    private static void set(RenderNode n, int p, float v) {
        switch (p) {
            case ALPHA: n.setAlpha(v); break;
            case SCALE: n.setScaleX(v); n.setScaleY(v); break;
            case TX: n.setTranslationX(v); break;
            default: n.setTranslationY(v); break;
        }
    }
}
