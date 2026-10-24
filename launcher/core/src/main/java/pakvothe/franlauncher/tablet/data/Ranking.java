package pakvothe.franlauncher.tablet.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Peso por uso de las favoritas: tiempo en pantalla de 7 días + el de hoy contado doble.
 * Histéresis: dos vecinas solo cambian de lugar si la de abajo pesa más de un 20% que la de arriba,
 * para que la home no salte a cada rato.
 */
public final class Ranking {
    public static final float HYSTERESIS = 1.2f;

    private Ranking() {}

    public static long weight(String pkg, Map<String, Long> week, Map<String, Long> today) {
        Long w = week.get(pkg), t = today.get(pkg);
        return (w == null ? 0 : w) + (t == null ? 0 : t);
    }

    /**
     * favorites = orden anterior (componentes). Devuelve el nuevo orden, más pesada primero.
     * El uso se cuenta por paquete.
     */
    public static List<String> order(List<String> favorites, Map<String, Long> week, Map<String, Long> today) {
        List<String> out = new ArrayList<>(favorites);
        int n = out.size();
        long[] w = new long[n];
        for (int i = 0; i < n; i++) w[i] = weight(pkg(out.get(i)), week, today);
        // Burbuja estable con umbral: n ≤ ~10, el costo no importa y el resultado es predecible.
        boolean moved = true;
        while (moved) {
            moved = false;
            for (int i = 0; i + 1 < n; i++) {
                if (w[i + 1] > w[i] * HYSTERESIS && w[i + 1] - w[i] > 60_000L) {
                    long tw = w[i]; w[i] = w[i + 1]; w[i + 1] = tw;
                    String ts = out.get(i); out.set(i, out.get(i + 1)); out.set(i + 1, ts);
                    moved = true;
                }
            }
        }
        return out;
    }

    public static String pkg(String component) {
        int i = component.indexOf('/');
        return i < 0 ? component : component.substring(0, i);
    }
}
