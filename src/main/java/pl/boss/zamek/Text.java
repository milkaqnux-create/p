package pl.boss.zamek;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Male pomocnicze metody do tekstow (bez kursywy, ktora Minecraft dodaje w nazwach i opisach przedmiotow). */
final class Text {
    private Text() {}

    static Component of(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    static Component bold(String text, TextColor color) {
        return of(text, color).decoration(TextDecoration.BOLD, true);
    }

    /** Sklada kilka kolorowych kawalkow w jedna linijke. */
    static Component line(Component... parts) {
        Component result = Component.empty().decoration(TextDecoration.ITALIC, false);
        for (Component part : parts) result = result.append(part);
        return result;
    }

    /** Linijka opisu: szary punktor + dowolne kawalki tekstu. */
    static Component bullet(String symbol, Component... parts) {
        Component result = line(of(symbol + " ", NamedTextColor.GRAY));
        for (Component part : parts) result = result.append(part);
        return result;
    }

    /** Linijka opisu z punktorem w wybranym kolorze (np. niebieska kropka). */
    static Component bullet(String symbol, TextColor symbolColor, Component... parts) {
        Component result = line(of(symbol + " ", symbolColor));
        for (Component part : parts) result = result.append(part);
        return result;
    }

    static Component white(String text) { return of(text, NamedTextColor.WHITE); }

    static Component blank() { return Component.empty(); }
}
