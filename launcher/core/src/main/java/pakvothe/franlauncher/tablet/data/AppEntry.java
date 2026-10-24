package pakvothe.franlauncher.tablet.data;

/** Una app lanzable, tal como vive en el caché. */
public final class AppEntry {
    /** "paquete/clase". */
    public final String component;
    public final String label;
    /** Color dominante del ícono. */
    public final int color;
    /** Celda en el atlas de íconos. */
    public final int icon;
    /** lastUpdateTime del paquete: si cambia, se regenera su ícono. */
    public final long stamp;
    /** App del sistema: no se puede desinstalar. */
    public final boolean system;

    public AppEntry(String component, String label, int color, int icon, long stamp, boolean system) {
        this.component = component;
        this.label = label;
        this.color = color;
        this.icon = icon;
        this.stamp = stamp;
        this.system = system;
    }

    public String packageName() {
        int i = component.indexOf('/');
        return i < 0 ? component : component.substring(0, i);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AppEntry)) return false;
        AppEntry e = (AppEntry) o;
        return component.equals(e.component) && label.equals(e.label) && color == e.color
            && icon == e.icon && stamp == e.stamp && system == e.system;
    }

    @Override
    public int hashCode() {
        return component.hashCode();
    }
}
