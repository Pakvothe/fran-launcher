package pakvothe.franlauncher.tablet.anim;

import android.os.Handler;
import android.os.Looper;

/** Una vez por minuto, alineado al cambio de minuto del reloj de pared (no hay segundero). */
public final class ClockTicker implements Runnable {
    public interface Listener {
        void onMinute(long wallMs);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private boolean running;

    public ClockTicker(Listener listener) {
        this.listener = listener;
    }

    public void start() {
        if (running) return;
        running = true;
        run();
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(this);
    }

    /** Para cambios de hora o huso: re-alinea sin esperar al próximo tick. */
    public void now() {
        if (!running) return;
        handler.removeCallbacks(this);
        run();
    }

    @Override
    public void run() {
        if (!running) return;
        long now = System.currentTimeMillis();
        listener.onMinute(now);
        // +5 ms para caer siempre del lado nuevo del minuto.
        handler.postDelayed(this, 60_000 - (now % 60_000) + 5);
    }
}
