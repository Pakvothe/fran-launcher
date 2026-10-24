package pakvothe.franlauncher.tablet.data;

/** Índice de letras del panel Todas: '#' (dígitos y otros) + A–Z. Los acentos van a su letra (Á → A, Ñ → N). */
public final class Letters {
    public static final int COUNT = 27;
    public static final String LABELS = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private Letters() {}

    public static int bucket(String label) {
        if (label == null || label.isEmpty()) return 0;
        char c = Fold.fold(label.charAt(0));
        return c >= 'a' && c <= 'z' ? 1 + (c - 'a') : 0;
    }
}
