package pakvothe.franlauncher.tablet.data;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class RankingTest {
    private static final long MIN = 60_000L;

    private static Map<String, Long> m(Object... kv) {
        Map<String, Long> r = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) r.put((String) kv[i], (Long) kv[i + 1]);
        return r;
    }

    @Test
    public void ordenaPorPesoConHoyTriple() {
        List<String> favs = Arrays.asList("a/A", "b/B", "c/C");
        List<String> out = Ranking.order(favs, m("a", 10 * MIN, "b", 50 * MIN, "c", 10 * MIN), m("c", 20 * MIN));
        // c = 10 + 3·20 = 70, b = 50, a = 10 (cada salto supera el 10% de histéresis).
        assertEquals(Arrays.asList("c/C", "b/B", "a/A"), out);
    }

    @Test
    public void histeresisNoCambiaPorPoco() {
        List<String> favs = Arrays.asList("a/A", "b/B");
        // b pesa 5% más que a: no alcanza el 10%.
        assertEquals(favs, Ranking.order(favs, m("a", 100 * MIN, "b", 105 * MIN), m()));
        assertEquals(Arrays.asList("b/B", "a/A"), Ranking.order(favs, m("a", 100 * MIN, "b", 115 * MIN), m()));
    }

    @Test
    public void casoRealGrokPasaABrowser() {
        // Días anteriores (sin hoy) y hoy, en minutos: X 333/61, Browser 299/15, Grok 172/100.
        List<String> favs = Arrays.asList("x/X", "browser/B", "grok/G");
        List<String> out = Ranking.order(favs, m("x", 333 * MIN, "browser", 299 * MIN, "grok", 172 * MIN),
            m("x", 61 * MIN, "browser", 15 * MIN, "grok", 100 * MIN));
        assertEquals(Arrays.asList("x/X", "grok/G", "browser/B"), out);
    }

    @Test
    public void sinUsoQuedaElOrdenManual() {
        List<String> favs = Arrays.asList("x/X", "y/Y", "z/Z");
        assertEquals(favs, Ranking.order(favs, m(), m()));
    }
}
