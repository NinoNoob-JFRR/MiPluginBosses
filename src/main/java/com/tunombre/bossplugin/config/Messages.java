package com.tunombre.bossplugin.config;

import com.tunombre.bossplugin.util.Text;
import org.bukkit.command.CommandSender;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Acceso centralizado a messages.yml.
 */
public class Messages {

    private final ConfigManager config;
    private String prefix = "";

    public Messages(ConfigManager config) {
        this.config = config;
        reload();
    }

    public void reload() {
        prefix = config.messages().getString("prefix", "&8[&cJefes&8]&r ");
    }

    public String raw(String key) {
        String value = config.messages().getString(key);
        return value == null ? key : value;
    }

    public String prefix() {
        return Text.color(prefix);
    }

    public String get(String key, String... placeholders) {
        return Text.color(prefix + Text.apply(raw(key), placeholders));
    }

    public String getRaw(String key, String... placeholders) {
        return Text.color(Text.apply(raw(key), placeholders));
    }

    public void send(CommandSender sender, String key, String... placeholders) {
        if (sender == null) {
            return;
        }
        String message = get(key, placeholders);
        if (message.isBlank()) {
            return;
        }
        sender.sendMessage(Text.c(message));
    }

    public void sendRaw(CommandSender sender, String key, String... placeholders) {
        if (sender == null) {
            return;
        }
        String message = getRaw(key, placeholders);
        if (message.isBlank()) {
            return;
        }
        sender.sendMessage(Text.c(message));
    }

    public void sendMap(CommandSender sender, String key, Map<String, String> placeholders) {
        if (sender == null) {
            return;
        }
        String message = Text.color(prefix + Text.apply(raw(key), placeholders));
        if (message.isBlank()) {
            return;
        }
        sender.sendMessage(Text.c(message));
    }

    public Map<String, String> map(String... placeholders) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            map.put(placeholders[i], placeholders[i + 1]);
        }
        return map;
    }
}
