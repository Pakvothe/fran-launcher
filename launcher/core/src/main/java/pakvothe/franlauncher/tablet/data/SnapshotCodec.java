package pakvothe.franlauncher.tablet.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Formato binario de config.bin y cache.bin. Sin reflexión ni serialización de Java. */
public final class SnapshotCodec {
    static final int CONFIG_MAGIC = 0x46544346; // FTCF
    static final int CACHE_MAGIC = 0x46544341;  // FTCA
    /** v2: reposo. v3: sin carátula de fondo. v4: rotación. v5: sin tinte. Lo descartado se lee y se saltea. */
    public static final int CONFIG_VERSION = 5;
    public static final int CACHE_VERSION = 1;

    private SnapshotCodec() {}

    public static void writeConfig(DataOutputStream out, Config c) throws IOException {
        out.writeInt(CONFIG_MAGIC);
        out.writeInt(CONFIG_VERSION);
        out.writeByte(c.background);
        out.writeBoolean(c.icons);
        out.writeBoolean(c.followPhase);
        out.writeBoolean(c.nowPlaying);
        out.writeBoolean(c.usageOrder);
        out.writeByte(c.hourFormat);
        out.writeBoolean(c.favoritesInitialized);
        out.writeShort(c.favorites.size());
        for (String f : c.favorites) out.writeUTF(f);
        out.writeShort(c.hidden.size());
        for (String h : c.hidden) out.writeUTF(h);
        out.writeByte(c.ambient);
        out.writeByte(c.rotation);
    }

    /** Nunca devuelve null: si no se puede leer, devuelve los valores por defecto. Versiones viejas se migran acá. */
    public static Config readConfig(DataInputStream in) {
        Config c = new Config();
        try {
            if (in.readInt() != CONFIG_MAGIC) return new Config();
            int version = in.readInt();
            if (version < 1 || version > CONFIG_VERSION) return new Config();
            c.background = clamp(in.readByte(), 0, 3);
            c.icons = in.readBoolean();
            c.followPhase = in.readBoolean();
            if (version < 5) in.readBoolean(); // tinte por app, descartado
            if (version < 3) in.readBoolean(); // carátula de fondo, descartada
            c.nowPlaying = in.readBoolean();
            c.usageOrder = in.readBoolean();
            c.hourFormat = clamp(in.readByte(), 0, 2);
            c.favoritesInitialized = in.readBoolean();
            int n = in.readUnsignedShort();
            for (int i = 0; i < n; i++) c.favorites.add(in.readUTF());
            n = in.readUnsignedShort();
            for (int i = 0; i < n; i++) c.hidden.add(in.readUTF());
            if (version >= 2) c.ambient = clamp(in.readByte(), 0, Config.AMBIENT_SECONDS.length - 1);
            if (version >= 4) c.rotation = clamp(in.readByte(), 0, 2);
            return c;
        } catch (IOException | RuntimeException e) {
            return new Config();
        }
    }

    public static void writeCache(DataOutputStream out, Cache c) throws IOException {
        out.writeInt(CACHE_MAGIC);
        out.writeInt(CACHE_VERSION);
        out.writeInt(c.density);
        out.writeShort(c.iconCols);
        out.writeShort(c.iconCount);
        out.writeUTF(c.locale);
        out.writeShort(c.apps.size());
        for (AppEntry e : c.apps) {
            out.writeUTF(e.component);
            out.writeUTF(e.label);
            out.writeInt(e.color);
            out.writeShort(e.icon);
            out.writeLong(e.stamp);
            out.writeBoolean(e.system);
        }
    }

    /** null si la versión, la densidad o el locale no coinciden, o si está corrupto. */
    public static Cache readCache(DataInputStream in, int density, String locale) {
        try {
            if (in.readInt() != CACHE_MAGIC || in.readInt() != CACHE_VERSION) return null;
            Cache c = new Cache();
            c.density = in.readInt();
            if (c.density != density) return null;
            c.iconCols = in.readShort();
            c.iconCount = in.readShort();
            c.locale = in.readUTF();
            if (!c.locale.equals(locale)) return null;
            int n = in.readUnsignedShort();
            for (int i = 0; i < n; i++) {
                String comp = in.readUTF();
                String label = in.readUTF();
                int color = in.readInt();
                int icon = in.readShort();
                long stamp = in.readLong();
                boolean system = in.readBoolean();
                if (icon >= c.iconCount) return null;
                c.apps.add(new AppEntry(comp, label, color, icon, stamp, system));
            }
            return c;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
