package pl.boss.zamek;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public final class ZamekZPiasku extends JavaPlugin {
    /** Schemat zamku jest wbudowany w JAR (src/main/resources/zamekzpiasku.schem). */
    private static final String SCHEMATIC_RESOURCE = "zamekzpiasku.schem";
    private static final String PERMISSION = "zamek.admin";

    private StackSettings stacks;
    private SandCastle castle;
    private ExtraItems extras;
    private SchoolBell bell;
    private ItemsMenu menu;

    @Override
    public void onEnable() {
        SchematicData schematic;
        try (InputStream in = getResource(SCHEMATIC_RESOURCE)) {
            if (in == null) throw new IOException("Brak pliku " + SCHEMATIC_RESOURCE + " w pluginie");
            schematic = SchematicData.load(in);
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Nie udalo sie wczytac schematu zamku - wylaczam plugin.", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Przedmioty, ktorych stackowanie mozna przelaczac: /msitems stacks <id> on/off
        stacks = new StackSettings(this);
        stacks.define("zamek", "Zamek z Piasku");
        stacks.define("kokos", "Kokos");
        stacks.define("lod", "Lod na patyku");
        stacks.define("dzwonek", "Dzwonek Szkolny");
        stacks.define("odlamek", "Smoczy Odlamek");

        castle = new SandCastle(this, schematic, stacks);
        extras = new ExtraItems(this, stacks);
        bell = new SchoolBell(this, stacks);
        menu = new ItemsMenu();
        // Kolejne przedmioty dodajemy tutaj: menu.register(slot 0-26, supplier przedmiotu).
        menu.register(11, castle::createItem);
        menu.register(12, extras::createCoconut);
        menu.register(13, extras::createIceOnStick);
        menu.register(14, bell::createItem);
        menu.register(15, extras::createDragonShard);

        getServer().getPluginManager().registerEvents(castle, this);
        getServer().getPluginManager().registerEvents(extras, this);
        getServer().getPluginManager().registerEvents(bell, this);
        bell.start();
        getServer().getPluginManager().registerEvents(menu, this);
        getLogger().info("ZamekZPiasku wlaczony (schemat " + schematic.width + "x" + schematic.height + "x" + schematic.length + ").");
    }

    @Override
    public void onDisable() {
        if (bell != null) bell.stop();
        if (castle != null) castle.restoreAll();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("msitems")) return false;
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Text.of("Nie masz uprawnien.", NamedTextColor.RED));
            return true;
        }

        // /msitems stacks <przedmiot> <on|off>
        if (args.length > 0 && args[0].equalsIgnoreCase("stacks")) {
            handleStacks(sender, args);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Tej komendy moze uzyc tylko gracz (konsola: /msitems stacks <przedmiot> <on|off>).");
            return true;
        }
        menu.open(player);
        return true;
    }

    private void handleStacks(CommandSender sender, String[] args) {
        String ids = String.join("/", stacks.ids());
        if (args.length != 3) {
            sender.sendMessage(Text.of("Uzycie: /msitems stacks <" + ids + "> <on|off>", NamedTextColor.RED));
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!stacks.has(id)) {
            sender.sendMessage(Text.of("Nieznany przedmiot '" + args[1] + "'. Dostepne: " + ids, NamedTextColor.RED));
            return;
        }
        boolean on;
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "on", "true", "tak" -> on = true;
            case "off", "false", "nie" -> on = false;
            default -> {
                sender.sendMessage(Text.of("Ostatni argument musi byc on albo off.", NamedTextColor.RED));
                return;
            }
        }
        stacks.set(id, on);
        sender.sendMessage(Text.of(stacks.displayName(id) + ": stackowanie " + (on ? "WLACZONE" : "WYLACZONE")
                + " (dotyczy nowo odebranych przedmiotow).", on ? NamedTextColor.GREEN : NamedTextColor.GOLD));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (!command.getName().equalsIgnoreCase("msitems") || !sender.hasPermission(PERMISSION)) return result;
        if (args.length == 1) {
            result.add("stacks");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("stacks")) {
            result.addAll(stacks.ids());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("stacks")) {
            result.add("on");
            result.add("off");
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        result.removeIf(option -> !option.startsWith(prefix));
        return result;
    }
}
