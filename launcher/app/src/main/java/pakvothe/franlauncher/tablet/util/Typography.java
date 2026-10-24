package pakvothe.franlauncher.tablet.util;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;

/**
 * La fuente (Outfit variable, recortada a wght 200–400) y los Paint de texto.
 * Una Typeface por peso, creada una vez desde assets sin comprimir (se mapea, no se copia).
 */
public final class Typography {
    private static final int[] WEIGHTS = {200, 300, 400};
    private static final Typeface[] FACES = new Typeface[WEIGHTS.length];

    private Typography() {}

    public static Typeface face(Context c, int weight) {
        int i = 0;
        while (i < WEIGHTS.length - 1 && WEIGHTS[i] < weight) i++;
        if (FACES[i] == null) {
            Typeface t = new Typeface.Builder(c.getAssets(), "outfit.ttf")
                .setFontVariationSettings("'wght' " + WEIGHTS[i]).build();
            FACES[i] = t != null ? t : Typeface.create("sans-serif-light", Typeface.NORMAL);
        }
        return FACES[i];
    }

    /** weight: 200, 300 o 400. */
    public static Paint paint(Context c, float size, int weight, boolean tabular) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(face(c, weight));
        p.setTextSize(size);
        if (tabular) p.setFontFeatureSettings("tnum");
        return p;
    }
}
