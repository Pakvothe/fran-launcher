package pakvothe.franlauncher.tablet.util;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

/** Intents hacia afuera. */
public final class Launch {
    private Launch() {}

    /** component = "paquete/clase". */
    public static boolean app(Context c, String component, android.os.Bundle options) {
        ComponentName cn = ComponentName.unflattenFromString(component);
        if (cn == null) return false;
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            c.startActivity(i, options);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    public static boolean pkg(Context c, String pkg) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        return i != null && start(c, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    public static boolean appInfo(Context c, String pkg) {
        return start(c, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @SuppressWarnings("deprecation")
    public static boolean uninstall(Context c, String pkg) {
        return start(c, new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** App de alarmas, si hay alguna que atienda el intent estándar. */
    public static boolean alarms(Context c) {
        if (start(c, new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
            return true;
        // Algunos relojes (Samsung) no atienden SHOW_ALARMS: se abre la app que atiende "poner alarma".
        android.content.pm.ResolveInfo ri = c.getPackageManager().resolveActivity(
            new Intent(android.provider.AlarmClock.ACTION_SET_ALARM), 0);
        return ri != null && ri.activityInfo != null && pkg(c, ri.activityInfo.packageName);
    }

    /** Calendario en el día de hoy; si ninguno entiende esa URI, la app de calendario por categoría. */
    public static boolean calendar(Context c) {
        Uri today = Uri.parse("content://com.android.calendar/time/" + System.currentTimeMillis());
        if (start(c, new Intent(Intent.ACTION_VIEW, today).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return true;
        return start(c, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    public static boolean systemSettings(Context c) {
        return start(c, new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    public static boolean start(Context c, Intent i) {
        try {
            c.startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
