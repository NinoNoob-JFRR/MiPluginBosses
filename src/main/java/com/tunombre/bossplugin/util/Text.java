package com.tunombre.bossplugin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;

/**
 * Utilidades de texto y placeholders.
 */
public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    public static Component c(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    public static String color(String text) {
        return text == null ? "" : text.replace('&', '\u00a7');
    }

    public static String strip(String text) {
        return text == null ? "" : text.replaceAll("(?i)\u00a7[0-9a-fk-or]", "");
    }

    public static String apply(String text, String... placeholders) {
        if (text == null) {
            return "";
        }
        String out = text;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            out = out.replace("%" + placeholders[i] + "%", placeholders[i + 1] == null ? "" : placeholders[i + 1]);
        }
        return out;
    }

    public static String apply(String text, Map<String, String> placeholders) {
        if (text == null) {
            return "";
        }
        String out = text;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            out = out.replace("%" + e.getKey() + "%", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    public static String roman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(number);
        };
    }

    public static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(java.util.Locale.US, "%.2f", value);
    }
}
