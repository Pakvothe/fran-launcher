package pakvothe.franlauncher.tablet.app;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.OverScroller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import pakvothe.franlauncher.tablet.R;
import pakvothe.franlauncher.tablet.anim.Animator;
import pakvothe.franlauncher.tablet.anim.ClockTicker;
import pakvothe.franlauncher.tablet.data.AppEntry;
import pakvothe.franlauncher.tablet.data.AppRepository;
import pakvothe.franlauncher.tablet.data.Atlas;
import pakvothe.franlauncher.tablet.data.Cache;
import pakvothe.franlauncher.tablet.data.Config;
import pakvothe.franlauncher.tablet.data.Ranking;
import pakvothe.franlauncher.tablet.data.Snapshot;
import pakvothe.franlauncher.tablet.data.Usage;
import pakvothe.franlauncher.tablet.media.MediaAccessService;
import pakvothe.franlauncher.tablet.media.MediaWatcher;
import pakvothe.franlauncher.tablet.scene.ClockLayer;
import pakvothe.franlauncher.tablet.scene.DrawerLayer;
import pakvothe.franlauncher.tablet.scene.HomeLayer;
import pakvothe.franlauncher.tablet.scene.SheetLayer;
import pakvothe.franlauncher.tablet.scene.SkyLayer;
import pakvothe.franlauncher.tablet.theme.Theme;
import pakvothe.franlauncher.tablet.util.Launch;
import pakvothe.franlauncher.tablet.util.Trace;

/**
 * La única View. Dueña de las capas, del toque y del teclado de búsqueda. onDraw solo dibuja los nodos:
 * cada capa se re-graba sola cuando cambia algo, nunca por frame.
 */
public final class Stage extends View implements AppRepository.Listener, MediaWatcher.Listener,
    ClockTicker.Listener, MediaAccessService.DotsListener {

    private static final int T_HOME = 0, T_DRAWER = 1, T_SCRUB = 2, T_SHEET = 3, T_DRAWER_CLOSE = 4;
    /** "Hoy" se re-consulta al volver a la home, como mucho una vez por minuto. */
    private static final long USAGE_EVERY_MS = 60_000;
    private static final int SHEET_APP = 0, SHEET_SETTINGS = 1, SHEET_HIDDEN = 2;
    /** En el panel de ocultas, el id de cada fila es HIDDEN_BASE + su índice. */
    private static final int HIDDEN_BASE = 1000;
    // Filas de los paneles.
    private static final int R_FAV = 1, R_HIDE = 2, R_INFO = 3, R_UNINSTALL = 4,
        S_BG = 10, S_WALL_PICK = 11, S_ICONS = 12, S_PHASE = 13, S_PLAYING = 16,
        S_USAGE = 17, S_HOUR = 18, S_HOME = 19, S_SYSTEM = 20, S_HIDDEN = 21, S_AMBIENT = 22, S_ROTATION = 23;

    private final HomeActivity activity;
    private final long t0;
    final Config config;
    private Cache cache;
    private Atlas icons;
    private final Animator animator = new Animator(this);
    private final SkyLayer sky = new SkyLayer(animator);
    private final ClockLayer clock;
    private final HomeLayer home;
    private final DrawerLayer drawer;
    private final SheetLayer sheet;
    private final AppRepository repo;
    private final MediaWatcher media;
    private final ClockTicker minutes = new ClockTicker(this);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OverScroller scroller;
    private final int slop, minFling;
    private float density = 1f;
    private float insetTop, insetBottom, imeBottom;
    private boolean started, firstFrame, drawerOpen;
    private int phase = -1;
    private Theme theme;

    // Uso y notificaciones.
    private Usage usage;
    private long usageAt, weekDay = -1;
    private boolean synced;
    private String locale;
    private final android.content.pm.LauncherApps launcherApps;
    /**
     * Altas, bajas y actualizaciones de apps mientras el proceso vive. Lo que cambie con el proceso muerto lo
     * toma la sincronización completa del primer onStart de cada proceso.
     */
    private final android.content.pm.LauncherApps.Callback appsCallback = new android.content.pm.LauncherApps.Callback() {
        @Override public void onPackageRemoved(String pkg, android.os.UserHandle u) { syncSoon(); }
        @Override public void onPackageAdded(String pkg, android.os.UserHandle u) { syncSoon(); }
        @Override public void onPackageChanged(String pkg, android.os.UserHandle u) { syncSoon(); }
        @Override public void onPackagesAvailable(String[] pkgs, android.os.UserHandle u, boolean r) { syncSoon(); }
        @Override public void onPackagesUnavailable(String[] pkgs, android.os.UserHandle u, boolean r) { syncSoon(); }
    };
    private final Runnable syncNow = this::syncApps;

    /** Una instalación llega como alta + cambio en milisegundos: se juntan en una sola sincronización. */
    private void syncSoon() {
        main.removeCallbacks(syncNow);
        main.postDelayed(syncNow, 400);
    }
    private Map<String, Integer> dots = Collections.emptyMap();
    private String playingPkg = "";

    // Toque.
    private int target;
    private float downX, downY, lastY;
    private boolean moved, longPressed, dragDrawer, homeScroll;
    private int pressed = HomeLayer.HIT_NONE;
    private VelocityTracker velocity;
    private AppEntry sheetApp;
    private int sheetKind;
    private final Runnable longPress = this::onLongPress;
    /** Reposo: un solo timer que se reinicia con cada toque. Sin polling. */
    private boolean ambient, wakeTouch;
    private final Runnable enterAmbient = this::enterAmbient;
    private final Runnable homeFling = new Runnable() {
        @Override
        public void run() {
            if (!scroller.computeScrollOffset()) return;
            home.setScroll(scroller.getCurrY());
            invalidate();
            postOnAnimation(this);
        }
    };
    private final Runnable fling = new Runnable() {
        @Override
        public void run() {
            if (!scroller.computeScrollOffset()) return;
            drawer.setScroll(scroller.getCurrY());
            invalidate();
            postOnAnimation(this);
        }
    };

    // Búsqueda: texto confirmado + lo que el teclado está componiendo.
    private final StringBuilder typed = new StringBuilder();
    private String composing = "";

    public Stage(HomeActivity activity, long t0) {
        super(activity);
        this.activity = activity;
        this.t0 = t0;
        density = getResources().getDisplayMetrics().density;
        String locale = getResources().getConfiguration().getLocales().get(0).toLanguageTag();
        Snapshot snap = Snapshot.read(activity, getResources().getDisplayMetrics().densityDpi, locale,
            Snapshot.iconPx(density));
        config = snap.config;
        cache = snap.cache;
        icons = snap.icons;
        clock = new ClockLayer(activity);
        home = new HomeLayer(activity);
        drawer = new DrawerLayer(activity, animator);
        sheet = new SheetLayer(activity, animator);
        repo = new AppRepository(activity, this);
        media = new MediaWatcher(activity, this);
        scroller = new OverScroller(activity);
        this.locale = locale;
        launcherApps = activity.getSystemService(android.content.pm.LauncherApps.class);
        if (launcherApps != null) launcherApps.registerCallback(appsCallback, main);
        ViewConfiguration vc = ViewConfiguration.get(activity);
        slop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity();
        setFocusable(true);
        setFocusableInTouchMode(true);

        drawer.node.setTranslationY(10_000);
        sky.setMode(config.background);
        applyPhase(System.currentTimeMillis(), false);
        if (cache != null) applyApps();
    }

    // --- Ciclo de vida ---

    void onStart() {
        started = true;
        restoreAll();
        minutes.start();
        if (config.nowPlaying) media.start();
        MediaAccessService.onConnected = () -> main.post(() -> { if (started && config.nowPlaying) media.start(); });
        MediaAccessService.dots = this;
        // Sincronización completa solo una vez por proceso; después, solo por LauncherApps.Callback o idioma.
        if (!synced) {
            synced = true;
            syncApps();
        }
        refreshUsage(false);
        scheduleAmbient();
    }

    void onStop() {
        started = false;
        main.removeCallbacks(enterAmbient);
        if (ambient) exitAmbient(false);
        minutes.stop();
        media.stop();
        MediaAccessService.onConnected = null;
        MediaAccessService.dots = null;
        closeSheet();
        if (drawerOpen) closeDrawer(false);
    }

    void onDestroy() {
        if (launcherApps != null) launcherApps.unregisterCallback(appsCallback);
        repo.quit();
    }

    /** Home estando en el inicio: cierra lo que haya abierto. */
    void onHome() {
        if (ambient) exitAmbient(true);
        else if (sheet.isOpen()) closeSheet();
        else if (home.isExpanded()) expandHome(false);
        else if (drawerOpen) closeDrawer(true);
        scheduleAmbient();
    }

    void onBack() {
        if (sheet.isOpen()) closeSheet();
        else if (home.isExpanded()) expandHome(false);
        else if (drawerOpen) closeDrawer(true);
    }

    /** Desde onConfigurationChanged: resincroniza solo si cambió el idioma (cambian los nombres de las apps). */
    void onConfigChanged() {
        String l = getResources().getConfiguration().getLocales().get(0).toLanguageTag();
        float d = getResources().getDisplayMetrics().density;
        boolean densityChanged = d != density;
        if (densityChanged) {
            // Zoom de pantalla o cambio de resolución: la Activity no se recrea, así que se re-mide todo acá
            // y se regenera el atlas (sus celdas dependen de la densidad).
            density = d;
            if (getWidth() > 0) onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight());
            cache = null;
        }
        if (!l.equals(locale) || densityChanged) {
            locale = l;
            syncApps();
        }
    }

    void syncApps() {
        String locale = getResources().getConfiguration().getLocales().get(0).toLanguageTag();
        repo.sync(cache, icons, getResources().getDisplayMetrics().densityDpi, Snapshot.iconPx(density), locale);
    }

    void onTimeChanged() {
        clock.timeZoneChanged();
        minutes.now();
    }

    private void restoreAll() {
        sky.restore();
        clock.restore();
        home.restore();
        drawer.restore();
        sheet.restore();
        invalidate();
    }

    // --- Medidas e insets ---

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        sky.setSize(w, h, density);
        clock.setSize(w, h, density);
        home.setSize(w, h, density);
        drawer.setSize(w, h, density);
        sheet.setSize(w, h, density);
        layoutAll();
        if (!drawerOpen) drawer.node.setTranslationY(h);
    }

    @Override
    @SuppressWarnings("deprecation")
    public WindowInsets onApplyWindowInsets(WindowInsets in) {
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars = in.getInsets(WindowInsets.Type.systemBars());
            insetTop = bars.top;
            insetBottom = bars.bottom;
            imeBottom = in.getInsets(WindowInsets.Type.ime()).bottom;
        } else {
            insetTop = in.getSystemWindowInsetTop();
            insetBottom = in.getStableInsetBottom();
            imeBottom = in.getSystemWindowInsetBottom();
        }
        if (getWidth() > 0) layoutAll();
        return in;
    }

    private void layoutAll() {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        boolean land = w > h;
        clock.place(land ? w * 0.26f : w / 2f, insetTop + (land ? 48 : 72) * density, land);
        home.place(land ? insetTop : clock.bottom, insetTop, insetBottom, land);
        drawer.setInsets(insetTop, Math.max(insetBottom, imeBottom));
        // El panel queda siempre arriba del teclado (algunos teclados no se cierran al pedirlo).
        sheet.setInsetTop(insetTop);
        sheet.setInsetBottom(Math.max(insetBottom, imeBottom));
        if (ambient) placeAmbient(0);
        invalidate();
    }

    // --- Dibujo ---

    @Override
    protected void onDraw(Canvas canvas) {
        if (!sky.node.hasDisplayList()) restoreAllNoInvalidate();
        // Todas las capas se dibujan siempre: un nodo que sale del árbol pierde sus recursos en HWUI (la home quedaba
        // vacía al cerrar el cajón). Con alfa 0 o fuera de pantalla, HWUI las descarta sin costo.
        sky.draw(canvas);
        clock.draw(canvas);
        home.draw(canvas);
        drawer.draw(canvas);
        sheet.draw(canvas);
        if (!firstFrame) {
            firstFrame = true;
            Trace.begin(Trace.FIRST_FRAME);
            Trace.end();
            android.util.Log.i("FranTablet", "firstFrame=" + (SystemClock.uptimeMillis() - t0));
        }
    }

    private void restoreAllNoInvalidate() {
        sky.restore();
        clock.restore();
        home.restore();
        drawer.restore();
        sheet.restore();
    }

    // --- Tick ---

    @Override
    public void onMinute(long wallMs) {
        clock.tick(wallMs);
        applyPhase(wallMs, true);
        invalidate();
    }

    /** -1 = la de la hora. Solo para pruebas (build debuggable). */
    private int forcedPhase = -1;

    void forceBackground(int bg) {
        config.background = Math.max(0, Math.min(3, bg));
        sky.setMode(config.background);
        activity.setWallpaperMode(config.background == Config.BG_WALLPAPER);
        forcePhase(forcedPhase);
    }

    void forcePhase(int p) {
        forcedPhase = p >= 0 && p <= 3 ? p : -1;
        phase = -1;
        applyPhase(System.currentTimeMillis(), false);
        invalidate();
    }

    private void applyPhase(long wallMs, boolean animate) {
        int p = forcedPhase >= 0 ? forcedPhase : ClockLayer.phaseFor(wallMs, config.followPhase);
        if (p == phase) return;
        phase = p;
        theme = Theme.of(p, config.background == Config.BG_WHITE);
        activity.setLightBars(theme.light);
        sky.setTheme(theme, animate);
        clock.setTheme(theme);
        home.setTheme(theme);
        drawer.setTheme(theme);
        sheet.setTheme(theme);
        clock.setHourFormat(config.hourFormat);
        clock.tick(wallMs);
    }

    // --- Datos ---

    @Override
    public void onApps(Cache c, Atlas a) {
        // El atlas viejo no se recicla a mano: otra sincronización en cola puede estar copiando celdas de él.
        // Lo suelta el GC cuando nadie lo referencia.
        cache = c;
        icons = a;
        applyApps();
    }

    private AppEntry byComponent(String comp) {
        if (cache == null) return null;
        for (AppEntry e : cache.apps) if (e.component.equals(comp)) return e;
        return null;
    }

    private void applyApps() {
        if (cache == null) return;
        // Favoritas que se desinstalaron: afuera.
        boolean changed = config.favorites.removeIf(f -> byComponent(f) == null);
        maybeInitFavorites();
        drawer.setData(cache.apps, config.hidden, icons, config.icons);
        applyHome();
        if (changed) save();
        invalidate();
    }

    private void applyHome() {
        if (cache == null) return;
        List<AppEntry> favs = new ArrayList<>();
        for (String f : config.favorites) {
            AppEntry e = byComponent(f);
            if (e != null) favs.add(e);
        }
        String[] today = new String[favs.size()];
        if (usage != null && config.usageOrder) {
            for (int i = 0; i < favs.size(); i++) {
                Long t = usage.today.get(favs.get(i).packageName());
                if (t == null || t < 60_000) continue;
                long min = t / 60_000;
                today[i] = min >= 60 ? getContext().getString(R.string.today_h, (int) (min / 60), (int) (min % 60))
                    : getContext().getString(R.string.today_m, (int) min);
            }
        }
        home.setData(favs.toArray(new AppEntry[0]), today, icons, config.icons);
        home.setDots(dots);
        invalidate();
    }

    /** Primer arranque con permiso de uso: las 6 apps más usadas de la semana arrancan como favoritas. */
    private void maybeInitFavorites() {
        if (config.favoritesInitialized || usage == null || cache == null) return;
        List<AppEntry> byUse = new ArrayList<>(cache.apps);
        byUse.sort((a, b) -> Long.compare(Ranking.weight(b.packageName(), usage.week, usage.today),
            Ranking.weight(a.packageName(), usage.week, usage.today)));
        for (int i = 0; i < byUse.size() && config.favorites.size() < 6; i++) {
            AppEntry e = byUse.get(i);
            if (Ranking.weight(e.packageName(), usage.week, usage.today) == 0) break;
            config.favorites.add(e.component);
        }
        config.favoritesInitialized = true;
        save();
    }

    /** Se consulta en el hilo de I/O al volver a la home, como mucho cada 30 min (o forzado). */
    private void refreshUsage(boolean force) {
        if (!config.usageOrder) return;
        long now = SystemClock.uptimeMillis();
        if (!force && usage != null && now - usageAt < USAGE_EVERY_MS) return;
        usageAt = now;
        Context app = getContext().getApplicationContext();
        long day = Usage.midnight(System.currentTimeMillis());
        // La semana se reusa si es del mismo día; si no, se vuelve a consultar.
        java.util.Map<String, Long> week = usage != null && day == weekDay ? new java.util.HashMap<>(usage.week) : null;
        weekDay = day;
        repo.post(() -> {
            Usage u = Usage.query(app, week);
            main.post(() -> onUsage(u));
        });
    }

    private void onUsage(Usage u) {
        usage = u;
        if (u == null) return;
        maybeInitFavorites();
        List<String> order = Ranking.order(config.favorites, u.week, u.today);
        if (!order.equals(config.favorites)) {
            config.favorites.clear();
            config.favorites.addAll(order);
            save();
        }
        applyHome();
    }

    @Override
    public void onDots(Map<String, Integer> counts) {
        dots = counts;
        home.setDots(counts);
        drawer.setDots(counts);
        invalidate();
    }

    @Override
    public void onNowPlaying(String title, String artist, String pkg, android.graphics.Bitmap art, boolean playing) {
        playingPkg = pkg;
        String sub = artist;
        if (sub.isEmpty() && cache != null) {
            for (AppEntry e : cache.apps) if (e.packageName().equals(pkg)) { sub = e.label; break; }
        }
        home.setPill(config.nowPlaying ? title : "", sub, playing, art);
        invalidate();
    }

    private void save() {
        repo.saveConfig(config.copy());
    }

    // --- Toque ---

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                main.removeCallbacks(enterAmbient);
                // En reposo, el primer toque solo despierta: no abre nada.
                wakeTouch = ambient;
                if (ambient) {
                    exitAmbient(true);
                    return true;
                }
                downX = x; downY = y; lastY = y;
                moved = false; longPressed = false; dragDrawer = false; homeScroll = false;
                scroller.forceFinished(true);
                if (velocity == null) velocity = VelocityTracker.obtain();
                velocity.clear();
                velocity.addMovement(e);
                if (sheet.isOpen()) target = T_SHEET;
                else if (drawerOpen) target = drawer.inIndex(x, y) ? T_SCRUB : T_DRAWER;
                else target = T_HOME;
                if (target == T_HOME) {
                    pressed = home.hit(x, y);
                    home.press(pressed, true);
                    invalidate();
                }
                if (target == T_SCRUB) drawer.scrub(y);
                if (target == T_SHEET) sheet.dragStart();
                if (target != T_SHEET && target != T_SCRUB) main.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (wakeTouch) return true;
                velocity.addMovement(e);
                if (!moved && Math.hypot(x - downX, y - downY) > slop) {
                    moved = true;
                    main.removeCallbacks(longPress);
                    unpress();
                    boolean vertical = Math.abs(y - downY) > Math.abs(x - downX);
                    // Con la lista expandida, arrastrar la desplaza; si no, hacia arriba abre el cajón.
                    if (target == T_HOME && vertical && home.isExpanded()) homeScroll = true;
                    else if (target == T_HOME && y < downY && vertical) dragDrawer = true;
                    if (target == T_DRAWER && drawer.atTop() && y > downY && Math.abs(y - downY) > Math.abs(x - downX))
                        target = T_DRAWER_CLOSE;
                    if (target == T_DRAWER || target == T_DRAWER_CLOSE) hideIme();
                }
                if (longPressed) return true;
                if (target == T_HOME && homeScroll) home.scrollBy(y - lastY);
                else if (target == T_HOME && dragDrawer) setDrawerY(Math.max(0, getHeight() + (y - downY)));
                else if (target == T_DRAWER_CLOSE) setDrawerY(Math.max(0, y - downY));
                else if (target == T_DRAWER && moved) drawer.scrollBy(lastY - y);
                else if (target == T_SCRUB) drawer.scrub(y);
                else if (target == T_SHEET && moved) sheet.drag(y - downY);
                lastY = y;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (wakeTouch) {
                    wakeTouch = false;
                    scheduleAmbient();
                    return true;
                }
                velocity.addMovement(e);
                velocity.computeCurrentVelocity(1000);
                float vy = velocity.getYVelocity();
                main.removeCallbacks(longPress);
                if (!longPressed) onRelease(x, y, vy);
                unpress();
                scheduleAmbient();
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                main.removeCallbacks(longPress);
                unpress();
                if (target == T_HOME && dragDrawer) closeDrawer(true);
                if (target == T_SCRUB) drawer.scrubEnd();
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void unpress() {
        if (pressed != HomeLayer.HIT_NONE) home.press(pressed, false);
        pressed = HomeLayer.HIT_NONE;
    }

    private void onRelease(float x, float y, float vy) {
        int h = getHeight();
        switch (target) {
            case T_SHEET: {
                // Un arrastre nunca cuenta como toque: antes, bajar el panel tocaba la fila de abajo del dedo.
                if (moved) {
                    sheet.release(y - downY, vy, minFling * 4);
                    break;
                }
                int id = sheet.hit(x, y);
                if (id == -1) closeSheet();
                else if (id >= 0) onSheetRow(id);
                break;
            }
            case T_HOME:
                if (homeScroll) {
                    if (Math.abs(vy) > minFling) {
                        scroller.fling(0, (int) home.scroll(), 0, (int) vy, 0, 0, 0, (int) home.maxScroll());
                        postOnAnimation(homeFling);
                    }
                } else if (dragDrawer) {
                    if (vy < -minFling * 4 || drawer.node.getTranslationY() < h * 0.7f) openDrawer();
                    else closeDrawer(true);
                } else if (!moved) {
                    int hit = home.hit(x, y);
                    boolean clockVisible = !home.isExpanded() || getWidth() > getHeight();
                    if (hit == HomeLayer.HIT_MORE) expandHome(true);
                    else if (hit == HomeLayer.HIT_LESS) expandHome(false);
                    else if (hit == HomeLayer.HIT_NONE && clockVisible && clock.timeRect.contains(x, y)) Launch.alarms(getContext());
                    else if (hit == HomeLayer.HIT_NONE && clockVisible && clock.dateRect.contains(x, y)) Launch.calendar(getContext());
                    else if (hit == HomeLayer.HIT_PILL_BUTTON) media.toggle();
                    else if (hit == HomeLayer.HIT_PILL) Launch.pkg(getContext(), playingPkg);
                    else if (hit >= 0) launch(favAt(hit), home.rowRect(hit));
                } else if (y < downY - 80 * density) {
                    openDrawer();
                }
                break;
            case T_DRAWER_CLOSE:
                if (vy > minFling * 4 || drawer.node.getTranslationY() > h * 0.3f) closeDrawer(true);
                else openDrawer();
                break;
            case T_DRAWER:
                if (!moved) {
                    if (drawer.inSearch(x, y)) showIme();
                    else {
                        AppEntry a = drawer.appAt(x, y);
                        if (a != null) launch(a, null);
                    }
                } else if (Math.abs(vy) > minFling) {
                    scroller.fling(0, (int) drawer.scroll(), 0, (int) -vy, 0, 0, 0, (int) drawer.maxScroll());
                    postOnAnimation(fling);
                }
                break;
            case T_SCRUB:
                drawer.scrubEnd();
                break;
            default:
        }
    }

    private void onLongPress() {
        if (moved) return;
        longPressed = true;
        unpress();
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        if (target == T_HOME) {
            int hit = home.hit(downX, downY);
            if (hit >= 0) showAppSheet(favAt(hit));
            else if (hit == HomeLayer.HIT_NONE) showSettings();
        } else if (target == T_DRAWER) {
            AppEntry a = drawer.appAt(downX, downY);
            if (a != null) {
                hideIme();
                showAppSheet(a);
            }
        }
        invalidate();
    }

    private AppEntry favAt(int i) {
        String comp = i < config.favorites.size() ? config.favorites.get(i) : null;
        return comp == null ? null : byComponent(comp);
    }

    private void launch(AppEntry e, RectF from) {
        if (e == null) return;
        android.os.Bundle opts = null;
        if (from != null) opts = ActivityOptions.makeScaleUpAnimation(this, (int) from.left, (int) from.top,
            (int) from.width(), (int) from.height()).toBundle();
        Launch.app(getContext(), e.component, opts);
        invalidate();
    }

    // --- Ver más ---

    /** La lista ocupa la pantalla y se desplaza; en vertical el reloj se desvanece (en horizontal está al costado). */
    private void expandHome(boolean on) {
        home.setExpanded(on);
        if (on) main.removeCallbacks(enterAmbient);
        else scheduleAmbient();
        if (getHeight() >= getWidth()) animator.animate(clock.node, Animator.ALPHA, on ? 0f : 1f, 200);
        invalidate();
    }

    // --- Reposo ---

    /** Solo en el inicio, sin cajón ni panel abiertos. Con el reposo apagado no programa nada. */
    private void scheduleAmbient() {
        main.removeCallbacks(enterAmbient);
        int secs = config.ambientSeconds();
        // Con "Ver más" abierto no hay reposo: se reprograma al cerrarlo.
        if (started && secs > 0 && !ambient && !drawerOpen && !sheet.isOpen() && !home.isExpanded())
            main.postDelayed(enterAmbient, secs * 1000L);
    }

    /** El reloj crece y se centra, la lista se desvanece. Una animación de 400 ms y después nada. */
    private void enterAmbient() {
        if (!started || drawerOpen || sheet.isOpen()) return;
        if (home.isExpanded()) expandHome(false);
        ambient = true;
        placeAmbient(400);
        animator.animate(home.node, Animator.ALPHA, 0f, 300);
    }

    /** Reloj al centro de la pantalla. También al rotar en reposo: el corrimiento depende de la orientación. */
    private void placeAmbient(int d) {
        float clockMid = (clock.top + clock.bottom) / 2;
        animator.animate(clock.node, Animator.TY, getHeight() / 2f - clockMid, d);
        // En horizontal el reloj vive a la izquierda: también se centra en X.
        animator.animate(clock.node, Animator.TX, getWidth() / 2f - clock.centerX, d);
        // Crece, pero sin pasar el 88% del ancho: en celulares el reloj ya ocupa casi todo.
        float grow = getWidth() > getHeight() ? 1.35f : 1.25f;
        animator.animate(clock.node, Animator.SCALE, Math.max(1f, Math.min(grow, getWidth() * 0.88f / clock.width)), d);
    }

    private void exitAmbient(boolean animate) {
        ambient = false;
        int d = animate ? 300 : 0;
        animator.animate(clock.node, Animator.TY, 0f, d);
        animator.animate(clock.node, Animator.TX, 0f, d);
        animator.animate(clock.node, Animator.SCALE, 1f, d);
        animator.animate(home.node, Animator.ALPHA, 1f, d);
    }

    // --- Cajón ---

    private void setDrawerY(float y) {
        drawer.node.setTranslationY(y);
        float t = Math.min(1f, y / Math.max(1, getHeight()));
        clock.node.setAlpha(t);
        home.node.setAlpha(t);
    }

    private void openDrawer() {
        drawerOpen = true;
        animator.animate(drawer.node, Animator.TY, 0f, 280);
        animator.animate(clock.node, Animator.ALPHA, 0f, 200);
        animator.animate(home.node, Animator.ALPHA, 0f, 200);
        // Sin foco automático: el teclado aparece solo al tocar la búsqueda.
    }

    private void closeDrawer(boolean animate) {
        drawerOpen = false;
        hideIme();
        setQuery("", "");
        int d = animate ? 260 : 0;
        animator.animate(drawer.node, Animator.TY, getHeight(), d);
        animator.animate(clock.node, Animator.ALPHA, 1f, d);
        animator.animate(home.node, Animator.ALPHA, 1f, d);
        drawer.setScroll(0);
        scheduleAmbient();
    }

    // --- Teclado y búsqueda ---

    /**
     * Teclas sueltas con el cajón abierto (teclado físico, o lo que se tipea antes de que el teclado en pantalla
     * se conecte): van a la búsqueda igual. Sin esto se perdía la primera letra.
     */
    @Override
    public boolean onKeyDown(int code, KeyEvent ev) {
        if (!drawerOpen || sheet.isOpen()) return super.onKeyDown(code, ev);
        if (code == KeyEvent.KEYCODE_DEL) {
            typed.setLength(Math.max(0, typed.length() - 1));
            setQuery(typed.toString(), "");
            return true;
        }
        if (code == KeyEvent.KEYCODE_ENTER) {
            AppEntry a = drawer.first();
            if (a != null) launch(a, null);
            return true;
        }
        int ch = ev.getUnicodeChar();
        if (ch > 0 && !Character.isISOControl(ch)) {
            typed.append((char) ch);
            setQuery(typed.toString(), "");
            return true;
        }
        return super.onKeyDown(code, ev);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return drawerOpen && !sheet.isOpen();
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo out) {
        out.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        out.imeOptions = EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN;
        return new BaseInputConnection(this, false) {
            @Override
            public boolean commitText(CharSequence text, int pos) {
                typed.append(text);
                setQuery(typed.toString(), "");
                return true;
            }

            @Override
            public boolean setComposingText(CharSequence text, int pos) {
                setQuery(typed.toString(), text.toString());
                return true;
            }

            @Override
            public boolean finishComposingText() {
                typed.append(composing);
                setQuery(typed.toString(), "");
                return true;
            }

            @Override
            public boolean deleteSurroundingText(int before, int after) {
                if (!composing.isEmpty()) composing = "";
                else typed.setLength(Math.max(0, typed.length() - before));
                setQuery(typed.toString(), "");
                return true;
            }

            @Override
            public boolean performEditorAction(int action) {
                AppEntry a = drawer.first();
                if (a != null) launch(a, null);
                return true;
            }

            @Override
            public boolean sendKeyEvent(KeyEvent ev) {
                if (ev.getAction() == KeyEvent.ACTION_DOWN) {
                    if (ev.getKeyCode() == KeyEvent.KEYCODE_DEL) return deleteSurroundingText(1, 0);
                    if (ev.getKeyCode() == KeyEvent.KEYCODE_ENTER) return performEditorAction(0);
                    int ch = ev.getUnicodeChar();
                    if (ch > 0) return commitText(String.valueOf((char) ch), 1);
                }
                return true;
            }
        };
    }

    private void setQuery(String committed, String comp) {
        if (committed.isEmpty() && comp.isEmpty()) typed.setLength(0);
        composing = comp;
        String q = committed + comp;
        if (!q.equals(drawer.query())) drawer.setQuery(q);
        invalidate();
    }

    private void showIme() {
        requestFocus();
        InputMethodManager imm = getContext().getSystemService(InputMethodManager.class);
        if (imm != null) {
            imm.restartInput(this);
            imm.showSoftInput(this, 0);
        }
    }

    private void hideIme() {
        InputMethodManager imm = getContext().getSystemService(InputMethodManager.class);
        if (imm == null) return;
        imm.hideSoftInputFromWindow(getWindowToken(), 0);
        // Con el panel abierto la View deja de ser editor: el teclado suelta la conexión y se va.
        imm.restartInput(this);
    }

    // --- Paneles ---

    private void closeSheet() {
        sheet.hide();
        scheduleAmbient();
        invalidate();
    }

    private void showAppSheet(AppEntry e) {
        if (e == null) return;
        sheetApp = e;
        sheetKind = SHEET_APP;
        Context c = getContext();
        List<SheetLayer.Row> rows = new ArrayList<>();
        boolean fav = config.favorites.contains(e.component);
        rows.add(new SheetLayer.Row(R_FAV, c.getString(fav ? R.string.remove_home : R.string.add_home), null, false));
        boolean hidden = config.hidden.contains(e.component);
        rows.add(new SheetLayer.Row(R_HIDE, c.getString(hidden ? R.string.unhide : R.string.hide), null, false));
        rows.add(new SheetLayer.Row(R_INFO, c.getString(R.string.app_info), null, false));
        if (!e.system) rows.add(new SheetLayer.Row(R_UNINSTALL, c.getString(R.string.uninstall), null, true));
        sheet.show(e.label, e.packageName(), rows);
        hideIme();
        invalidate();
    }

    private void showSettings() {
        sheetKind = SHEET_SETTINGS;
        sheet.show(getContext().getString(R.string.settings), "", settingsRows());
        invalidate();
    }

    private List<SheetLayer.Row> settingsRows() {
        Context c = getContext();
        String yes = c.getString(R.string.yes), no = c.getString(R.string.no);
        int[] bg = {R.string.bg_orbs, R.string.bg_wall, R.string.bg_white, R.string.bg_black};
        int[] hour = {R.string.hour_system, R.string.hour_24, R.string.hour_12};
        List<SheetLayer.Row> r = new ArrayList<>();
        r.add(new SheetLayer.Row(S_BG, c.getString(R.string.set_bg), c.getString(bg[config.background]), false));
        if (config.background == Config.BG_WALLPAPER)
            r.add(new SheetLayer.Row(S_WALL_PICK, c.getString(R.string.wallpaper_pick), null, false));
        r.add(new SheetLayer.Row(S_ICONS, c.getString(R.string.set_icons), config.icons ? yes : no, false));
        r.add(new SheetLayer.Row(S_PHASE, c.getString(R.string.set_phase), config.followPhase ? yes : no, false));
        r.add(new SheetLayer.Row(S_PLAYING, c.getString(R.string.set_playing),
            config.nowPlaying ? (media.granted() ? yes : c.getString(R.string.grant)) : no, false));
        r.add(new SheetLayer.Row(S_USAGE, c.getString(R.string.set_usage),
            config.usageOrder ? (Usage.granted(c) ? yes : c.getString(R.string.grant)) : no, false));
        r.add(new SheetLayer.Row(S_HOUR, c.getString(R.string.set_hour), c.getString(hour[config.hourFormat]), false));
        if (!activity.isPhone()) {
            int[] rot = {R.string.rot_auto, R.string.rot_portrait, R.string.rot_landscape};
            r.add(new SheetLayer.Row(S_ROTATION, c.getString(R.string.set_rotation), c.getString(rot[config.rotation]), false));
        }
        int secs = config.ambientSeconds();
        r.add(new SheetLayer.Row(S_AMBIENT, c.getString(R.string.set_ambient),
            secs == 0 ? c.getString(R.string.off) : c.getString(R.string.ambient_after, secs), false));
        if (!config.hidden.isEmpty())
            r.add(new SheetLayer.Row(S_HIDDEN, c.getString(R.string.set_hidden), String.valueOf(config.hidden.size()), false));
        r.add(new SheetLayer.Row(S_HOME, c.getString(R.string.set_home), null, false));
        r.add(new SheetLayer.Row(S_SYSTEM, c.getString(R.string.set_system), null, false));
        return r;
    }

    /** Panel con las apps ocultas: tocar una la vuelve a mostrar en el cajón. */
    private void showHidden() {
        sheetKind = SHEET_HIDDEN;
        hiddenList.clear();
        List<SheetLayer.Row> rows = new ArrayList<>();
        for (String comp : config.hidden) {
            AppEntry e = byComponent(comp);
            if (e == null) continue;
            hiddenList.add(comp);
            rows.add(new SheetLayer.Row(HIDDEN_BASE + hiddenList.size() - 1, e.label, getContext().getString(R.string.unhide), false));
        }
        sheet.show(getContext().getString(R.string.set_hidden), "", rows);
        invalidate();
    }

    private final List<String> hiddenList = new ArrayList<>();

    private void onSheetRow(int id) {
        Context c = getContext();
        if (sheetKind == SHEET_HIDDEN) {
            int i = id - HIDDEN_BASE;
            if (i >= 0 && i < hiddenList.size()) {
                config.hidden.remove(hiddenList.get(i));
                drawer.setData(cache.apps, config.hidden, icons, config.icons);
                save();
            }
            if (config.hidden.isEmpty()) closeSheet();
            else showHidden();
            return;
        }
        if (sheetKind == SHEET_APP && sheetApp != null) {
            AppEntry e = sheetApp;
            switch (id) {
                case R_FAV:
                    if (!config.favorites.remove(e.component)) config.favorites.add(e.component);
                    config.favoritesInitialized = true;
                    // La nueva entra en su lugar por peso ya, no en la próxima consulta de uso.
                    if (usage != null && config.usageOrder) onUsage(usage);
                    else if (config.usageOrder) refreshUsage(true);
                    applyHome();
                    break;
                case R_HIDE:
                    if (!config.hidden.remove(e.component)) config.hidden.add(e.component);
                    drawer.setData(cache.apps, config.hidden, icons, config.icons);
                    break;
                case R_INFO:
                    Launch.appInfo(c, e.packageName());
                    break;
                case R_UNINSTALL:
                    Launch.uninstall(c, e.packageName());
                    break;
                default:
            }
            save();
            closeSheet();
            return;
        }
        switch (id) {
            case S_BG:
                config.background = (config.background + 1) % 4;
                sky.setMode(config.background);
                activity.setWallpaperMode(config.background == Config.BG_WALLPAPER);
                phase = -1;
                applyPhase(System.currentTimeMillis(), false);
                break;
            case S_WALL_PICK:
                Launch.start(c, Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), null)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                break;
            case S_ICONS:
                config.icons = !config.icons;
                applyApps();
                break;
            case S_PHASE:
                config.followPhase = !config.followPhase;
                phase = -1;
                applyPhase(System.currentTimeMillis(), true);
                break;
            case S_PLAYING:
                if (config.nowPlaying && !media.granted()) {
                    Launch.start(c, new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } else {
                    config.nowPlaying = !config.nowPlaying;
                    if (config.nowPlaying) media.start();
                    else {
                        media.stop();
                        home.setPill("", "", false, null);
                    }
                }
                break;
            case S_USAGE:
                if (config.usageOrder && !Usage.granted(c)) {
                    Launch.start(c, new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } else {
                    config.usageOrder = !config.usageOrder;
                    if (config.usageOrder) refreshUsage(true);
                    else {
                        usage = null;
                        applyHome();
                    }
                }
                break;
            case S_ROTATION:
                config.rotation = (config.rotation + 1) % 3;
                activity.applyRotation(config.rotation);
                break;
            case S_AMBIENT:
                config.ambient = (config.ambient + 1) % Config.AMBIENT_SECONDS.length;
                break;
            case S_HOUR:
                config.hourFormat = (config.hourFormat + 1) % 3;
                clock.setHourFormat(config.hourFormat);
                break;
            case S_HOME:
                activity.requestHome();
                break;
            case S_HIDDEN:
                showHidden();
                return;
            case S_SYSTEM:
                Launch.systemSettings(c);
                break;
            default:
        }
        save();
        sheet.replace(settingsRows());
        invalidate();
    }
}
