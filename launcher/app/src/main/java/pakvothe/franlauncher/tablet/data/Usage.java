package pakvothe.franlauncher.tablet.data;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Tiempo en pantalla por paquete: los 2 días anteriores completos ("week", sin hoy) y hoy. Se consulta en el hilo
 * de I/O: hoy al volver a la home, los días anteriores una vez por día (no cambian durante el día).
 */
public final class Usage {
    public final Map<String, Long> week = new HashMap<>(), today = new HashMap<>();

    public static boolean granted(Context c) {
        AppOpsManager ops = c.getSystemService(AppOpsManager.class);
        if (ops == null) return false;
        int mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /** null si no hay permiso. week = la de la consulta anterior si es del mismo día (se reusa, no se consulta). */
    public static Usage query(Context c, Map<String, Long> week) {
        if (!granted(c)) return null;
        UsageStatsManager usm = c.getSystemService(UsageStatsManager.class);
        if (usm == null) return null;
        long now = System.currentTimeMillis();
        Usage u = new Usage();
        if (week != null) u.week.putAll(week);
        long midnight = midnight(now);
        if (week == null) fill(usm.queryAndAggregateUsageStats(midnight - 2L * 86_400_000L, midnight), u.week);
        fill(usm.queryAndAggregateUsageStats(midnight, now), u.today);
        return u;
    }

    /** Clave del día local: los días anteriores se vuelven a consultar cuando cambia. */
    public static long midnight(long now) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static void fill(Map<String, UsageStats> src, Map<String, Long> out) {
        if (src == null) return;
        for (Map.Entry<String, UsageStats> e : src.entrySet()) {
            long t = e.getValue().getTotalTimeInForeground();
            if (t > 0) out.put(e.getKey(), t);
        }
    }
}
