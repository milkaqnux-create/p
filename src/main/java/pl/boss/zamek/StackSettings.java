package pl.boss.zamek;

import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Ustawienia "czy przedmiot sie stackuje" - zapisywane w config.yml, zmieniane komenda /msitems stacks. */
final class StackSettings {
    private final JavaPlugin plugin;
    private final Map<String, String> names = new LinkedHashMap<>(); // id -> nazwa wyswietlana

    StackSettings(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        plugin.getConfig().options().copyDefaults(true); // dopisz nowe klucze do starego config.yml
        plugin.saveConfig();
    }

    void define(String id, String displayName) { names.put(id, displayName); }

    Set<String> ids() { return names.keySet(); }

    boolean has(String id) { return names.containsKey(id); }

    String displayName(String id) { return names.get(id); }

    boolean stacks(String id) { return plugin.getConfig().getBoolean("stacks." + id, true); }

    void set(String id, boolean value) {
        plugin.getConfig().set("stacks." + id, value);
        plugin.saveConfig();
    }

    /** Ustawia limit stacka w meta przedmiotu: wylaczone = 1 szt., wlaczone = domyslny limit materialu. */
    void apply(String id, ItemMeta meta) {
        meta.setMaxStackSize(stacks(id) ? null : Integer.valueOf(1));
    }
}
