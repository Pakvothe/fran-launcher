package pakvothe.franlauncher.tablet.data;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pakvothe.franlauncher.tablet.util.Trace;

/**
 * Consulta de apps a PackageManager en el único hilo de I/O de la app. Calcula el diff contra el caché anterior,
 * regenera solo los íconos que cambiaron, avisa al main y persiste.
 */
public final class AppRepository {
    public interface Listener {
        /** En el main. El atlas es nuevo: el anterior se puede soltar. */
        void onApps(Cache cache, Atlas icons);
    }

    private static final int ICON_COLS = 8;

    private final Context context;
    private final Listener listener;
    private final HandlerThread thread = new HandlerThread("io", Process.THREAD_PRIORITY_BACKGROUND);
    private final Handler io;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Config pendingConfig;
    private final Runnable saveConfig = this::flushConfig;

    public AppRepository(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        thread.start();
        io = new Handler(thread.getLooper());
    }

    private void flushConfig() {
        Config c;
        synchronized (this) {
            c = pendingConfig;
            pendingConfig = null;
        }
        if (c != null) Snapshot.writeConfig(context, c);
    }

    public void quit() {
        io.post(saveConfig);
        thread.quitSafely();
    }

    /** Debounce de 500 ms después del último cambio. Recibe una copia: el main sigue usando la suya. */
    public void saveConfig(Config copy) {
        synchronized (this) {
            pendingConfig = copy;
        }
        io.removeCallbacks(saveConfig);
        io.postDelayed(saveConfig, 500);
    }

    public void post(Runnable r) {
        io.post(r);
    }

    public void sync(Cache prev, Atlas prevIcons, int density, int iconPx, String locale) {
        io.post(() -> {
            Trace.begin(Trace.SYNC_APPS);
            long t0 = android.os.SystemClock.uptimeMillis();
            try {
                Result r = build(prev, prevIcons, iconPx);
                r.cache.density = density;
                r.cache.locale = locale;
                if (prev == null || r.changed || !prev.locale.equals(locale)) {
                    main.post(() -> listener.onApps(r.cache, r.icons));
                    Snapshot.writeCache(context, r.cache, r.icons);
                }
                android.util.Log.i("FranTablet", "sync=" + (android.os.SystemClock.uptimeMillis() - t0)
                    + " apps=" + r.cache.apps.size() + " changed=" + r.changed);
            } finally {
                Trace.end();
            }
        });
    }

    private static final class Result {
        Cache cache;
        Atlas icons;
        boolean changed;
    }

    private Result build(Cache prev, Atlas prevIcons, int iconPx) {
        PackageManager pm = context.getPackageManager();
        String self = context.getPackageName();
        List<ResolveInfo> list = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
            if (!ri.activityInfo.packageName.equals(self)) list.add(ri);
        Map<String, AppEntry> old = new HashMap<>();
        if (prev != null) for (AppEntry e : prev.apps) old.put(e.component, e);

        Result r = new Result();
        r.cache = new Cache();
        r.cache.iconCols = ICON_COLS;
        r.icons = Atlas.create(list.size(), ICON_COLS, iconPx, iconPx, Bitmap.Config.ARGB_8888);
        Canvas ic = new Canvas(r.icons.bitmap);
        Rect src = new Rect(), dst = new Rect();
        Bitmap probe = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        boolean reusable = prevIcons != null && prevIcons.cellW == iconPx;
        for (int i = 0; i < list.size(); i++) {
            ResolveInfo ri = list.get(i);
            ActivityInfo ai = ri.activityInfo;
            String comp = ai.packageName + "/" + ai.name;
            String label = String.valueOf(ri.loadLabel(pm));
            long stamp = stamp(pm, ai.packageName);
            boolean system = (ai.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            AppEntry o = old.get(comp);
            int color;
            r.icons.cell(i, dst);
            if (reusable && o != null && o.stamp == stamp) {
                prevIcons.cell(o.icon, src);
                ic.drawBitmap(prevIcons.bitmap, src, dst, null);
                color = o.color;
                if (!label.equals(o.label)) r.changed = true;
            } else {
                r.changed = true;
                Drawable icon = ri.loadIcon(pm);
                drawInto(ic, icon, dst);
                color = averageColor(icon, probe);
            }
            r.cache.apps.add(new AppEntry(comp, label, color, i, stamp, system));
        }
        r.cache.iconCount = list.size();
        if (prev == null || prev.apps.size() != r.cache.apps.size()) r.changed = true;
        else for (int i = 0; i < prev.apps.size() && !r.changed; i++)
            if (!prev.apps.get(i).component.equals(r.cache.apps.get(i).component)) r.changed = true;
        probe.recycle();
        return r;
    }

    private static long stamp(PackageManager pm, String pkg) {
        try {
            return pm.getPackageInfo(pkg, 0).lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    private static void drawInto(Canvas c, Drawable d, Rect dst) {
        if (d == null) return;
        Rect keep = d.copyBounds();
        d.setBounds(dst);
        d.draw(c);
        d.setBounds(keep);
    }

    /** Promedio de los píxeles opacos del ícono, con la saturación subida. */
    static int averageColor(Drawable icon, Bitmap probe) {
        if (icon == null) return 0xFF6A3878;
        probe.eraseColor(0);
        Canvas c = new Canvas(probe);
        Rect keep = icon.copyBounds();
        icon.setBounds(0, 0, probe.getWidth(), probe.getHeight());
        icon.draw(c);
        icon.setBounds(keep);
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = 0; y < probe.getHeight(); y++)
            for (int x = 0; x < probe.getWidth(); x++) {
                int p = probe.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++;
            }
        if (n == 0) return 0xFF6A3878;
        float[] hsv = new float[3];
        Color.RGBToHSV((int) (r / n), (int) (g / n), (int) (b / n), hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.2f);
        hsv[2] = Math.max(0.35f, Math.min(hsv[2], 0.85f));
        return Color.HSVToColor(hsv);
    }
}
