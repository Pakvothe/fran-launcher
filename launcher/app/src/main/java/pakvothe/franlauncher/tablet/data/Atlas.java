package pakvothe.franlauncher.tablet.data;

import android.graphics.Bitmap;
import android.graphics.Rect;

/** Grilla de celdas de igual tamaño dentro de un Bitmap. Pixeles crudos en disco, sin PNG ni decodificación. */
public final class Atlas {
    public final Bitmap bitmap;
    public final int cols, cellW, cellH;

    public Atlas(Bitmap bitmap, int cols, int cellW, int cellH) {
        this.bitmap = bitmap;
        this.cols = cols;
        this.cellW = cellW;
        this.cellH = cellH;
    }

    public static Atlas create(int count, int cols, int cellW, int cellH, Bitmap.Config config) {
        int rows = Math.max(1, (count + cols - 1) / cols);
        return new Atlas(Bitmap.createBitmap(cols * cellW, rows * cellH, config), cols, cellW, cellH);
    }

    /** Rect de la celda i (se pasa el Rect para no crear objetos al dibujar). */
    public void cell(int i, Rect out) {
        int x = (i % cols) * cellW, y = (i / cols) * cellH;
        out.set(x, y, x + cellW, y + cellH);
    }
}
