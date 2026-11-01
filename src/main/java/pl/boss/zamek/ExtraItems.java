package pl.boss.zamek;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/** Kokos (jedzenie z efektami) oraz Lod na patyku (lewitacja po uderzeniu gracza). */
final class ExtraItems implements Listener {
    // Wyglad przedmiotow w grze. Podmien material (albo ustaw item_model z resource packa), jesli chcesz inny model.
    private static final Material COCONUT_MATERIAL = Material.COOKIE;
    private static final Material ICE_MATERIAL = Material.STICK;
    private static final Material SHARD_MATERIAL = Material.AMETHYST_SHARD;

    /** Ticki: 20 = 1 sekunda. */
    private static final int LEVITATION_TICKS = 20 * 10;
    private static final int REGENERATION_TICKS = 20 * 5;
    private static final int ABSORPTION_TICKS = 20 * 120;
    private static final int SPEED_TICKS = 20 * 10;

    private final StackSettings stacks;
    private final NamespacedKey coconutKey;
    private final NamespacedKey iceKey;
    private final NamespacedKey shardKey;

    ExtraItems(JavaPlugin plugin, StackSettings stacks) {
        this.stacks = stacks;
        this.coconutKey = new NamespacedKey(plugin, "coconut_item");
        this.iceKey = new NamespacedKey(plugin, "ice_on_stick_item");
        this.shardKey = new NamespacedKey(plugin, "dragon_shard_item");
    }

    ItemStack createCoconut() {
        ItemStack item = new ItemStack(COCONUT_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.of("KOKOS", NamedTextColor.GOLD));
        meta.lore(List.of(
                Text.bullet("≡", Text.white("Po zjedzeniu otrzymujemy:")),
                Text.bullet("→", Text.of("Efekt regeneracja II", NamedTextColor.LIGHT_PURPLE), Text.white(" (5s)")),
                Text.bullet("→", Text.of("Efekt absorpcja II", NamedTextColor.YELLOW), Text.white(" (120s)")),
                Text.bullet("→", Text.of("Efekt szybkość I", NamedTextColor.AQUA), Text.white(" (10s)")),
                Text.blank(),
                Text.bullet("×", Text.bold("Uwaga!", NamedTextColor.DARK_RED), Text.of(" Przedmiot jest jednorazowy", NamedTextColor.RED)),
                Text.blank(),
                Text.of("Przytrzymaj PPM aby zjeść", NamedTextColor.GREEN)));

        // Mozna jesc zawsze (tez z pelnym glodem).
        FoodComponent food = meta.getFood();
        food.setNutrition(2);
        food.setSaturation(1f);
        food.setCanAlwaysEat(true);
        meta.setFood(food);

        meta.setItemModel(new NamespacedKey("ms", "kokos"));
        meta.setEnchantmentGlintOverride(true); // kokos swieci jak enchantowany
        stacks.apply("kokos", meta);
        meta.getPersistentDataContainer().set(coconutKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    ItemStack createIceOnStick() {
        ItemStack item = new ItemStack(ICE_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.of("LÓD NA PATXKU", NamedTextColor.LIGHT_PURPLE));
        meta.lore(List.of(
                Text.bullet("×", Text.white("Po uderzeniu gracza nadajemy mu oraz")),
                Text.bullet("×", Text.white("sobie "), Text.of("efekt lewitacji", NamedTextColor.AQUA), Text.white(" na określony czas"))));
        meta.setItemModel(new NamespacedKey("ms", "lod_na_patyku"));
        stacks.apply("lod", meta);
        meta.getPersistentDataContainer().set(iceKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Smoczy Odlamek - zwykly przedmiot (do wymiany u NPC), bez wlasnej akcji. */
    ItemStack createDragonShard() {
        ItemStack item = new ItemStack(SHARD_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.of("SMOCZY ODŁAMEK", NamedTextColor.LIGHT_PURPLE));
        meta.lore(List.of(
                Text.bullet("•", NamedTextColor.BLUE, Text.white("Przedmiot możesz wykorzystać u "), Text.of("NPC", NamedTextColor.GREEN), Text.white(" na spawnie")),
                Text.blank(),
                Text.bullet("•", NamedTextColor.BLUE, Text.of("Zakupisz na ", NamedTextColor.GREEN), Text.of("www.catstar.pl", NamedTextColor.GREEN))));
        meta.setItemModel(new NamespacedKey("ms", "smoczy_odlamek"));
        stacks.apply("odlamek", meta);
        meta.getPersistentDataContainer().set(shardKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!hasKey(event.getItem(), coconutKey)) return;
        Player player = event.getPlayer();
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, REGENERATION_TICKS, 1)); // II
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, ABSORPTION_TICKS, 1));       // II
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, SPEED_TICKS, 0));                 // I
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) return;
        if (!hasKey(attacker.getInventory().getItemInMainHand(), iceKey)) return;
        PotionEffect levitation = new PotionEffect(PotionEffectType.LEVITATION, LEVITATION_TICKS, 0);
        victim.addPotionEffect(levitation);
        attacker.addPotionEffect(levitation);
    }

    private static boolean hasKey(ItemStack item, NamespacedKey key) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
