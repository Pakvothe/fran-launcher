package pakvothe.franlauncher.tablet.data;

/**
 * Orden alfabético sin ICU: minúsculas y acentos latinos plegados (á → a, ñ → n, ç → c).
 * Para es/en/pt da el mismo orden que un Collator primario, sin cargar sus tablas nativas (~1 MB).
 */
public final class Fold {
    private static final String FROM = "àáâãäåāçèéêëēìíîïīñòóôõöøōùúûüūýÿ";
    private static final String TO = "aaaaaaaceeeeeiiiiinooooooouuuuuyy";

    private Fold() {}

    public static char fold(char c) {
        char l = Character.toLowerCase(c);
        int i = FROM.indexOf(l);
        return i >= 0 ? TO.charAt(i) : l;
    }

    public static int compare(String a, String b) {
        int n = Math.min(a.length(), b.length());
        for (int i = 0; i < n; i++) {
            char x = fold(a.charAt(i)), y = fold(b.charAt(i));
            if (x != y) return x - y;
        }
        return a.length() - b.length();
    }
}
