package pakvothe.franlauncher.tablet.media;

import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashMap;
import java.util.Map;

/**
 * Habilita getActiveSessions() (lo que suena) y cuenta notificaciones por paquete para los puntitos.
 * No lee ni guarda el contenido de ninguna notificación: solo el paquete. Nada sale de la tablet.
 */
public final class MediaAccessService extends NotificationListenerService {
    public interface DotsListener {
        /** En el main. El mapa es una copia: paquete → cantidad. */
        void onDots(Map<String, Integer> counts);
    }

    /** Lo pone el Stage: si la Activity arrancó antes de que el sistema conectara el listener, reintenta acá. */
    public static Runnable onConnected;
    public static DotsListener dots;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public void onListenerConnected() {
        Runnable r = onConnected;
        if (r != null) r.run();
        publish();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        publish();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        publish();
    }

    private void publish() {
        DotsListener l = dots;
        if (l == null) return;
        StatusBarNotification[] all;
        try {
            all = getActiveNotifications();
        } catch (RuntimeException e) {
            return;
        }
        Map<String, Integer> counts = new HashMap<>();
        if (all != null) for (StatusBarNotification n : all) {
            // Las persistentes (música, descargas) no son "algo nuevo": no llevan puntito.
            if (n.isOngoing() || !n.isClearable()) continue;
            counts.merge(n.getPackageName(), 1, Integer::sum);
        }
        main.post(() -> l.onDots(counts));
    }
}
