package pakvothe.franlauncher.tablet.media;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import java.util.List;

/** Sesión de medios activa, solo por eventos (sin polling). Se registra en onStart y se suelta en onStop. */
public final class MediaWatcher implements MediaSessionManager.OnActiveSessionsChangedListener {
    public interface Listener {
        /** title vacío = no hay nada sonando. */
        void onNowPlaying(String title, String artist, String pkg, Bitmap art, boolean playing);
    }

    private static final int ART = 112;
    private final Context context;
    private final ComponentName component;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaSessionManager msm;
    private MediaController controller;
    private boolean running;
    private Bitmap lastArtSource, lastArt;
    // Lo último publicado: no se re-publica si nada visible cambió.
    private String pubTitle = "", pubArtist = "", pubPkg = "";
    private Bitmap pubArt;
    private boolean pubPlaying;
    /** Al pausar o reanudar, las apps pasan unas décimas por NONE/BUFFERING: ocultar espera 1,5 s. */
    private final Runnable hide = () -> emit("", "", "", null, false);

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            publish();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            publish();
        }

        @Override
        public void onSessionDestroyed() {
            if (msm != null) select(msm.getActiveSessions(component));
        }
    };

    public MediaWatcher(Context c, Listener l) {
        context = c.getApplicationContext();
        component = new ComponentName(context, MediaAccessService.class);
        listener = l;
    }

    public ComponentName component() {
        return component;
    }

    public boolean granted() {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        return nm != null && nm.isNotificationListenerAccessGranted(component);
    }

    public void start() {
        if (running || !granted()) return;
        msm = context.getSystemService(MediaSessionManager.class);
        if (msm == null) return;
        try {
            msm.addOnActiveSessionsChangedListener(this, component, handler);
            running = true;
            select(msm.getActiveSessions(component));
        } catch (SecurityException e) {
            // El listener todavía no está conectado (p. ej. tras actualizar la app): se reintenta en onListenerConnected.
            running = false;
            android.util.Log.w("FranLauncher", "media: listener sin conectar");
        }
    }

    public void stop() {
        if (!running) return;
        running = false;
        handler.removeCallbacks(hide);
        msm.removeOnActiveSessionsChangedListener(this);
        if (controller != null) controller.unregisterCallback(callback);
        controller = null;
    }

    public void toggle() {
        if (controller == null) return;
        // Tecla de media a la sesión, como el control remoto: Spotify TV ignora getTransportControls().pause()
        // de otra app (probado en la tele), pero responde a MEDIA_PAUSE / MEDIA_PLAY.
        PlaybackState st = controller.getPlaybackState();
        int code = st != null && st.getState() == PlaybackState.STATE_PLAYING
            ? android.view.KeyEvent.KEYCODE_MEDIA_PAUSE : android.view.KeyEvent.KEYCODE_MEDIA_PLAY;
        controller.dispatchMediaButtonEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code));
        controller.dispatchMediaButtonEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code));
    }

    @Override
    public void onActiveSessionsChanged(List<MediaController> list) {
        select(list);
    }

    /** Prefiere la que está sonando; si no, la primera con metadatos (en pausa). */
    private void select(List<MediaController> list) {
        MediaController pick = null;
        if (list != null) {
            for (MediaController c : list) {
                PlaybackState st = c.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                    pick = c;
                    break;
                }
                if (pick == null && c.getMetadata() != null) pick = c;
            }
        }
        if (controller != null && (pick == null || !pick.getSessionToken().equals(controller.getSessionToken()))) {
            controller.unregisterCallback(callback);
            controller = null;
        }
        if (pick != null && controller == null) {
            controller = pick;
            controller.registerCallback(callback, handler);
        }
        publish();
    }

    private void publish() {
        MediaMetadata md = controller != null ? controller.getMetadata() : null;
        PlaybackState st = controller != null ? controller.getPlaybackState() : null;
        int s = st != null ? st.getState() : PlaybackState.STATE_NONE;
        if (md == null || s == PlaybackState.STATE_NONE || s == PlaybackState.STATE_STOPPED || s == PlaybackState.STATE_ERROR) {
            if (!pubTitle.isEmpty()) {
                handler.removeCallbacks(hide);
                handler.postDelayed(hide, 1500);
            }
            return;
        }
        handler.removeCallbacks(hide);
        String title = first(md, MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE);
        String artist = first(md, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, MediaMetadata.METADATA_KEY_ARTIST);
        Bitmap art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        // Estados de transición (cargando, conectando, saltando): se mantiene el ícono de antes.
        boolean playing = s == PlaybackState.STATE_PLAYING || (s != PlaybackState.STATE_PAUSED && pubPlaying);
        emit(title, artist, controller.getPackageName(), small(art), playing);
    }

    private void emit(String title, String artist, String pkg, Bitmap art, boolean playing) {
        if (title.equals(pubTitle) && artist.equals(pubArtist) && pkg.equals(pubPkg) && art == pubArt && playing == pubPlaying)
            return;
        pubTitle = title;
        pubArtist = artist;
        pubPkg = pkg;
        pubArt = art;
        pubPlaying = playing;
        listener.onNowPlaying(title, artist, pkg, art, playing);
    }

    /** Carátula a 112 px como máximo; se reusa si no cambió. */
    private Bitmap small(Bitmap art) {
        if (art == null) return null;
        if (art == lastArtSource) return lastArt;
        lastArtSource = art;
        int max = Math.max(art.getWidth(), art.getHeight());
        lastArt = max <= ART ? art : Bitmap.createScaledBitmap(art, art.getWidth() * ART / max, art.getHeight() * ART / max, true);
        return lastArt;
    }

    private static String first(MediaMetadata md, String a, String b) {
        CharSequence v = md.getText(a);
        if (v == null || v.length() == 0) v = md.getText(b);
        return v == null ? "" : v.toString();
    }
}
