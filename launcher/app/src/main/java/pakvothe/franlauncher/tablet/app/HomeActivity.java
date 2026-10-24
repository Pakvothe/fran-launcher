package pakvothe.franlauncher.tablet.app;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;

import pakvothe.franlauncher.tablet.data.Config;
import pakvothe.franlauncher.tablet.util.Launch;

/** Ciclo de vida, Atrás y la ventana (edge-to-edge, fondo del sistema). Sin lógica de UI. */
public final class HomeActivity extends Activity {
    private Stage stage;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        long t0 = SystemClock.uptimeMillis();
        super.onCreate(null);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        stage = new Stage(this, t0);
        setContentView(stage);
        // Desde API 33 (y siempre con targetSdk 36) Atrás llega por acá, no por onBackPressed.
        // Prioridad por defecto y siempre registrado: un launcher nunca se cierra con Atrás.
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, stage::onBack);
        setWallpaperMode(stage.config.background == Config.BG_WALLPAPER);
        setLightBars(stage.config.background == Config.BG_WHITE);
        applyRotation(stage.config.rotation);
    }

    /** Celular: lado corto menor a 600 dp. Ahí el launcher es siempre vertical (en horizontal se ve raro). */
    boolean isPhone() {
        return getResources().getConfiguration().smallestScreenWidthDp < 600;
    }

    /** Auto sigue al sistema (incluido su bloqueo de rotación); las otras dos fijan la orientación del launcher. */
    void applyRotation(int r) {
        if (isPhone()) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            return;
        }
        setRequestedOrientation(r == Config.ROT_PORTRAIT ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            : r == Config.ROT_LANDSCAPE ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    /** Con el fondo del sistema la ventana es transparente y el compositor dibuja la foto: 0 MB en este proceso. */
    void setWallpaperMode(boolean on) {
        if (on) {
            getWindow().setFormat(PixelFormat.TRANSLUCENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(0));
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            getWindow().setFormat(PixelFormat.OPAQUE);
            // El Stage es opaco: el fondo de la ventana sería una capa de overdraw de más.
            getWindow().setBackgroundDrawable(null);
        }
    }

    /** Fondo blanco: íconos de la barra de estado y navegación oscuros. */
    @SuppressWarnings("deprecation")
    void setLightBars(boolean light) {
        // Antes de setContentView no hay ventana: onCreate lo vuelve a aplicar después.
        if (getWindow().peekDecorView() == null) return;
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController c = getWindow().getInsetsController();
            if (c == null) return;
            int m = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            c.setSystemBarsAppearance(light ? m : 0, m);
        } else {
            View d = getWindow().getDecorView();
            int f = d.getSystemUiVisibility();
            d.setSystemUiVisibility(light ? f | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : f & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    /** Prende el alias HOME (apagado mientras convive con otro launcher) y pide el rol de inicio. */
    void requestHome() {
        getPackageManager().setComponentEnabledSetting(new ComponentName(this, getPackageName() + ".Home"),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        RoleManager rm = getSystemService(RoleManager.class);
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 1);
        } else {
            Launch.start(this, new Intent(android.provider.Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home estando en el inicio: el sistema nos re-entrega el intent HOME.
        if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) stage.onHome();
        // Solo en builds de prueba: --ei fl.phase 0..3 fuerza la fase del cielo para revisar contraste.
        boolean debuggable = (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (debuggable && intent != null && intent.hasExtra("fl.bg")) stage.forceBackground(intent.getIntExtra("fl.bg", 0));
        if (debuggable && intent != null && intent.hasExtra("fl.phase")) stage.forcePhase(intent.getIntExtra("fl.phase", -1));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Un launcher no se cierra con Atrás.
        stage.onBack();
    }

    @Override
    protected void onStart() {
        super.onStart();
        stage.onStart();
    }

    @Override
    protected void onStop() {
        stage.onStop();
        super.onStop();
    }

    /** La Activity nunca se recrea: rotación, idioma y demás llegan acá. */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        stage.onTimeChanged();
        stage.onConfigChanged();
    }

    @Override
    protected void onDestroy() {
        stage.onDestroy();
        super.onDestroy();
    }
}
