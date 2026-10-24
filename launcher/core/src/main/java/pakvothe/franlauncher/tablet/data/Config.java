package pakvothe.franlauncher.tablet.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Ajustes, favoritas y ocultas. Chico, versionado y nunca se descarta: si cambia el formato, se migra. */
public final class Config {
    /** 2 era "color liso" (descartado): una config vieja con 2 pasa a blanco. */
    public static final int BG_ORBS = 0, BG_WALLPAPER = 1, BG_WHITE = 2, BG_BLACK = 3;
    public static final int HOUR_SYSTEM = 0, HOUR_24 = 1, HOUR_12 = 2;
    /** Rotación del launcher: la del sistema, fija vertical o fija horizontal. */
    public static final int ROT_AUTO = 0, ROT_PORTRAIT = 1, ROT_LANDSCAPE = 2;
    /** Segundos sin tocar hasta el reposo (reloj centrado). 0 = apagado. Índice en ambient. */
    public static final int[] AMBIENT_SECONDS = {8, 15, 30, 60, 0};

    public int background = BG_ORBS;
    public boolean icons = true;
    /** El fondo sigue la hora del día; si no, queda fijo en atardecer. */
    public boolean followPhase = true;
    public boolean nowPlaying = true;
    /** Ordenar y dar peso a las favoritas por uso (requiere el permiso de estadísticas de uso). */
    public boolean usageOrder = true;
    public int hourFormat = HOUR_SYSTEM;
    public int ambient = 0;
    public int rotation = ROT_AUTO;
    /** Componentes "paquete/clase". El orden es el último calculado: sirve para la histéresis y para el modo manual. */
    public final List<String> favorites = new ArrayList<>();
    public boolean favoritesInitialized;
    public final Set<String> hidden = new HashSet<>();

    /** Copia para persistir desde el hilo de I/O mientras el main sigue usando la suya. */
    public Config copy() {
        Config c = new Config();
        c.background = background;
        c.icons = icons;
        c.followPhase = followPhase;
        c.nowPlaying = nowPlaying;
        c.usageOrder = usageOrder;
        c.hourFormat = hourFormat;
        c.ambient = ambient;
        c.rotation = rotation;
        c.favorites.addAll(favorites);
        c.favoritesInitialized = favoritesInitialized;
        c.hidden.addAll(hidden);
        return c;
    }

    public int ambientSeconds() {
        return AMBIENT_SECONDS[Math.max(0, Math.min(ambient, AMBIENT_SECONDS.length - 1))];
    }
}
