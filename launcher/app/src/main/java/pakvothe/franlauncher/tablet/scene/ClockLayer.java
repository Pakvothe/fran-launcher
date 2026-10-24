package pakvothe.franlauncher.tablet.scene;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import pakvothe.franlauncher.tablet.R;
import pakvothe.franlauncher.tablet.data.Config;
import pakvothe.franlauncher.tablet.theme.PhaseResolver;
import pakvothe.franlauncher.tablet.theme.Theme;
import pakvothe.franlauncher.tablet.util.Typography;

/** Hora grande con los dos puntos tenues, y la fecha con la fase. Se re-graba una vez por minuto. */
public final class ClockLayer extends Layer {
    private final Context context;
    private final Paint time, colon, date;
    private final Calendar cal = Calendar.getInstance();
    private final char[] hh = new char[2], mm = new char[2];
    private String dateText = "";
    private int dayKey = -1, hourFormat = Config.HOUR_SYSTEM, phase;
    private boolean system24;
    /** Centro horizontal del bloque, su borde de arriba y de abajo, y el ancho de la hora más ancha, en px. */
    public float centerX, top, bottom, width;
    /** Zonas tocables en pantalla (sin reposo): la hora abre las alarmas, la fecha el calendario. */
    public final android.graphics.RectF timeRect = new android.graphics.RectF(), dateRect = new android.graphics.RectF();
    private float size;

    public ClockLayer(Context context) {
        super("clock");
        this.context = context;
        time = Typography.paint(context, 100, 200, true);
        // Desde el constructor: si se fija después de medir, el tamaño cambia en cada recálculo del diseño.
        time.setLetterSpacing(-0.04f);
        colon = Typography.paint(context, 100, 200, true);
        date = Typography.paint(context, 20, 300, false);
        date.setTextAlign(Paint.Align.CENTER);
        system24 = DateFormat.is24HourFormat(context);
    }

    public void setTheme(Theme t) {
        time.setColor(t.text);
        colon.setColor(Theme.alpha(t.text, 0.45f));
        date.setColor(t.textSecondary);
        phase = t.phase;
        dayKey = -1;
        invalidate();
    }

    public void setHourFormat(int f) {
        hourFormat = f;
        system24 = DateFormat.is24HourFormat(context);
        invalidate();
    }

    /**
     * Posición y tamaño: en vertical 150 dp arriba; en horizontal 120 dp, centrado en altura en la mitad izquierda
     * (top se ignora).
     */
    public void place(float centerX, float top, boolean landscape) {
        this.centerX = centerX;
        size = (landscape ? 120 : 150) * s;
        // Que entre en el ancho disponible (celulares, pantalla dividida): se mide la hora más ancha posible.
        float avail = (landscape ? w * 0.48f : w - 48 * s);
        time.setTextSize(size);
        float widest = time.measureText("00:00");
        if (widest > avail) size *= avail / widest;
        width = Math.min(widest, avail);
        this.top = landscape ? (h - (size * 0.95f + 34 * s)) / 2 : top;
        top = this.top;
        time.setTextSize(size);
        colon.setTextSize(size);
        date.setTextSize(20 * s);
        bottom = top + size * 0.95f + 34 * s;
        float half = width / 2;
        timeRect.set(centerX - half, top, centerX + half, top + size * 0.85f);
        dateRect.set(centerX - half, top + size * 0.85f, centerX + half, bottom + 8 * s);
        // Nodo del tamaño justo del bloque: la fecha y la hora no invalidan el resto de la pantalla.
        node.setPosition(0, (int) top, w, (int) Math.ceil(bottom));
        // Pivote en el centro del bloque: el reposo lo escala sin correrlo.
        node.setPivotX(centerX);
        node.setPivotY((bottom - top) / 2);
        invalidate();
    }

    public void tick(long wallMs) {
        cal.setTimeInMillis(wallMs);
        invalidate();
    }

    public void timeZoneChanged() {
        cal.setTimeZone(java.util.TimeZone.getDefault());
        dayKey = -1;
        invalidate();
    }

    @Override
    protected void record(RecordingCanvas c) {
        boolean h24 = hourFormat == Config.HOUR_24 || (hourFormat == Config.HOUR_SYSTEM && system24);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        if (!h24) hour = hour % 12 == 0 ? 12 : hour % 12;
        int min = cal.get(Calendar.MINUTE);
        hh[0] = (char) ('0' + hour / 10); hh[1] = (char) ('0' + hour % 10);
        mm[0] = (char) ('0' + min / 10); mm[1] = (char) ('0' + min % 10);
        int day = cal.get(Calendar.YEAR) * 400 + cal.get(Calendar.DAY_OF_YEAR);
        if (day != dayKey) {
            // Solo al cambiar el día: crea objetos, fuera del camino de cada minuto.
            dayKey = day;
            String d = new SimpleDateFormat("EEEE d", Locale.getDefault()).format(cal.getTime());
            dateText = d + " · " + context.getString(PHASES[phase]);
        }
        c.translate(0, -top);
        float base = top + size * 0.78f;
        boolean lead = h24 || hour >= 10;
        // Centrado: se mide el bloque hh:mm y se arranca desde la mitad.
        float total = time.measureText(hh, lead ? 0 : 1, lead ? 2 : 1) + colon.measureText(":") + time.measureText(mm, 0, 2);
        float x = centerX - total / 2;
        c.drawText(hh, lead ? 0 : 1, lead ? 2 : 1, x, base, time);
        x += time.measureText(hh, lead ? 0 : 1, lead ? 2 : 1);
        c.drawText(":", x, base, colon);
        x += colon.measureText(":");
        c.drawText(mm, 0, 2, x, base, time);
        c.drawText(dateText, centerX, top + size * 0.95f + 24 * s, date);
    }

    private static final int[] PHASES = {R.string.phase_night, R.string.phase_dawn, R.string.phase_day, R.string.phase_dusk};

    public static int phaseFor(long wallMs, boolean follow) {
        if (!follow) return PhaseResolver.DUSK;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(wallMs);
        return PhaseResolver.phaseAt(c.get(Calendar.HOUR_OF_DAY));
    }
}
