package pakvothe.franlauncher.tablet.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.Test;

public class SnapshotCodecTest {
    @Test
    public void configIdaYVuelta() throws IOException {
        Config c = new Config();
        c.background = Config.BG_WALLPAPER;
        c.icons = false;
        c.hourFormat = Config.HOUR_24;
        c.favorites.add("a/A");
        c.favorites.add("b/B");
        c.favoritesInitialized = true;
        c.hidden.add("z/Z");
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        SnapshotCodec.writeConfig(new DataOutputStream(bo), c);
        Config r = SnapshotCodec.readConfig(new DataInputStream(new ByteArrayInputStream(bo.toByteArray())));
        assertEquals(Config.BG_WALLPAPER, r.background);
        assertEquals(false, r.icons);
        assertEquals(Config.HOUR_24, r.hourFormat);
        assertEquals(c.favorites, r.favorites);
        assertTrue(r.favoritesInitialized);
        assertTrue(r.hidden.contains("z/Z"));
    }

    @Test
    public void configCorruptaDaDefaults() {
        Config r = SnapshotCodec.readConfig(new DataInputStream(new ByteArrayInputStream(new byte[] {1, 2, 3})));
        assertEquals(Config.BG_ORBS, r.background);
        assertTrue(r.favorites.isEmpty());
    }

    @Test
    public void cacheSeDescartaSiCambiaLaDensidad() throws IOException {
        Cache c = new Cache();
        c.density = 280;
        c.iconCols = 8;
        c.iconCount = 1;
        c.locale = "es-AR";
        c.apps.add(new AppEntry("a/A", "App", 0xFF112233, 0, 5L, false));
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        SnapshotCodec.writeCache(new DataOutputStream(bo), c);
        byte[] b = bo.toByteArray();
        Cache ok = SnapshotCodec.readCache(new DataInputStream(new ByteArrayInputStream(b)), 280, "es-AR");
        assertEquals(1, ok.apps.size());
        assertEquals("App", ok.apps.get(0).label);
        assertNull(SnapshotCodec.readCache(new DataInputStream(new ByteArrayInputStream(b)), 320, "es-AR"));
        assertNull(SnapshotCodec.readCache(new DataInputStream(new ByteArrayInputStream(b)), 280, "en"));
    }
}
