package pl.boss.zamek;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Menu /msitems. Kazdy przedmiot dodaje sie jedna linijka: {@link #register(int, Supplier)}.
 */
final class ItemsMenu implements Listener {
    private static final int SIZE = 27; // 3 rzedy
    private static final String PERMISSION = "zamek.admin";

    private record Entry(int slot, Supplier<ItemStack> reward) {}

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() { return inventory; }
    }

    private final List<Entry> entries = new ArrayList<>();

    /** Dodaje przedmiot do menu na wskazanym slocie (0-26). Klik daje graczowi swiezy egzemplarz z {@code reward}. */
    void register(int slot, Supplier<ItemStack> reward) {
        entries.add(new Entry(slot, reward));
    }

    void open(Player player) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, SIZE, Text.of("MS Items", NamedTextColor.DARK_GRAY));
        holder.inventory = inv;

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" "));
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        for (Entry entry : entries) {
            ItemStack icon = entry.reward().get();
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Text.of("Kliknij, aby odebrac", NamedTextColor.GREEN));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(entry.slot(), icon);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder)) return;
        e.setCancelled(true); // nic nie wolno wynosic ani wkladac do menu
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (e.getClickedInventory() != top) return;
        if (!player.hasPermission(PERMISSION)) {
            player.closeInventory();
            return;
        }
        for (Entry entry : entries) {
            if (entry.slot() == e.getSlot()) {
                give(player, entry.reward().get());
                return;
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        if (e.getRawSlots().stream().anyMatch(slot -> slot < SIZE)) e.setCancelled(true);
    }

    private void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> left = player.getInventory().addItem(item);
        left.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        ItemMeta meta = item.getItemMeta();
        Component name = meta.hasDisplayName() ? meta.displayName() : Component.text(item.getType().name());
        player.sendMessage(Text.of("Otrzymales: ", NamedTextColor.GREEN).append(name));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
    }
}
