package pakvothe.franlauncher.tablet.util;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/** Íconos dibujados con primitivas (sin vector drawables). Centro (cx, cy), lado nominal size. Solo al grabar. */
public final class Icons {
    public static final int PLAY = 0, PAUSE = 1, SEARCH = 2, CHEVRON_UP = 3;

    private Icons() {}

    public static void draw(Canvas c, int icon, float cx, float cy, float size, Paint fill, Paint stroke) {
        float u = size / 24f;
        switch (icon) {
            case PLAY: {
                Path p = new Path();
                p.moveTo(cx - 5 * u, cy - 7 * u);
                p.lineTo(cx + 7 * u, cy);
                p.lineTo(cx - 5 * u, cy + 7 * u);
                p.close();
                c.drawPath(p, fill);
                break;
            }
            case PAUSE:
                c.drawRoundRect(cx - 6 * u, cy - 7 * u, cx - 2 * u, cy + 7 * u, u, u, fill);
                c.drawRoundRect(cx + 2 * u, cy - 7 * u, cx + 6 * u, cy + 7 * u, u, u, fill);
                break;
            case SEARCH:
                stroke.setStrokeWidth(2 * u);
                c.drawCircle(cx - 1 * u, cy - 1 * u, 7 * u, stroke);
                c.drawLine(cx + 4 * u, cy + 4 * u, cx + 8 * u, cy + 8 * u, stroke);
                break;
            default: {
                stroke.setStrokeWidth(2 * u);
                Path p = new Path();
                p.moveTo(cx - 6 * u, cy + 3 * u);
                p.lineTo(cx, cy - 3 * u);
                p.lineTo(cx + 6 * u, cy + 3 * u);
                c.drawPath(p, stroke);
            }
        }
    }

    public static Paint strokePaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        return p;
    }
}
