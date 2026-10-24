package pakvothe.franlauncher.tablet.data;

import java.util.ArrayList;
import java.util.List;

/** Lista de apps + geometría del atlas. Descartable: si no coincide algo, se regenera desde PackageManager. */
public final class Cache {
    /** El atlas depende solo de la densidad: la rotación no lo invalida. */
    public int density;
    public int iconCols, iconCount;
    public String locale = "";
    public final List<AppEntry> apps = new ArrayList<>();
}
