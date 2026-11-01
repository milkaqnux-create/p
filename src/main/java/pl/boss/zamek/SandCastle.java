package pl.boss.zamek;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Level;

/**
 * Zamek z Piasku: po PPM przedmiotem wkleja budowle ze schematu na {@link #DURATION_SECONDS} sekund,
 * chroni ja, a potem przywraca poprzedni teren.
 */
final class SandCastle implements Listener {
    /** Jak dlugo stoi zamek. */
    static final int DURATION_SECONDS = 7;

    private final JavaPlugin plugin;
    private final SchematicData schematic;
    private final BlockData[] blockData;
    private final StackSettings stacks;
    private final NamespacedKey itemKey;

    private final Map<UUID, List<BlockState>> backups = new HashMap<>();
    private final Map<UUID, Set<BlockPos>> activeBlocks = new HashMap<>();
    private final Map<BlockPos, UUID> protectedBlocks = new HashMap<>();

    private record BlockPos(UUID world, int x, int y, int z) {
        static BlockPos of(Block block) {
            return new BlockPos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    SandCastle(JavaPlugin plugin, SchematicData schematic, StackSettings stacks) {
        this.plugin = plugin;
        this.stacks = stacks;
        this.schematic = schematic;
        this.itemKey = new NamespacedKey(plugin, "sandcastle_item");

        String[] palette = schematic.palette();
        this.blockData = new BlockData[palette.length];
        for (int i = 0; i < palette.length; i++) {
            try {
                blockData[i] = Bukkit.createBlockData(palette[i]);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().log(Level.WARNING, "Nieznany blok w schemacie: " + palette[i] + " (zastapiony powietrzem)");
                blockData[i] = Material.AIR.createBlockData();
            }
        }
    }

    /** Przedmiot, ktorym tworzy sie zamek. */
    ItemStack createItem() {
        ItemStack item = new ItemStack(Material.SAND);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.of("ZAMEK Z PIASKU", NamedTextColor.YELLOW));
        meta.lore(List.of(
                Text.bullet("×", Text.white("Po użyciu "), Text.of("tworzymy", NamedTextColor.GOLD), Text.white(" dookoła")),
                Text.bullet("×", Text.white("nas "), Text.of("zamek z piasku", NamedTextColor.YELLOW), Text.white(" i zamykamy w nim")),
                Text.bullet("×", Text.white("wszystkich graczy w obrębie na określony czas")),
                Text.blank(),
                Text.of("Kliknij PPM aby użyć", NamedTextColor.GREEN)));
        meta.setItemModel(new NamespacedKey("ms", "zamek")); // tekstura z resource packa (assets/ms)
        stacks.apply("zamek", meta);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()
                || !item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE)) return;
        event.setCancelled(true);
        paste(event.getPlayer());
    }

    private boolean paste(Player player) {
        World world = player.getWorld();
        Location loc = player.getLocation();

        // Budowla jest wysrodkowana na graczu, a jej podloga (warstwa 0) jest tuz pod jego stopami.
        int bx = loc.getBlockX() - schematic.width / 2;
        int by = loc.getBlockY() - 1;
        int bz = loc.getBlockZ() - schematic.length / 2;

        if (by < world.getMinHeight() || by + schematic.height > world.getMaxHeight()) {
            player.sendMessage(Text.of("Tutaj nie ma miejsca na zamek (za wysoko / za nisko)!", NamedTextColor.RED));
            return false;
        }

        // Najpierw sprawdzamy, czy nie nachodzi na inny aktywny zamek, i zapisujemy stare bloki.
        UUID id = UUID.randomUUID();
        Set<BlockPos> positions = new HashSet<>();
        List<BlockState> saved = new ArrayList<>();
        for (int y = 0; y < schematic.height; y++) {
            for (int z = 0; z < schematic.length; z++) {
                for (int x = 0; x < schematic.width; x++) {
                    if (!schematic.shouldPaste(x, y, z)) continue;
                    Block block = world.getBlockAt(bx + x, by + y, bz + z);
                    BlockPos pos = BlockPos.of(block);
                    if (protectedBlocks.containsKey(pos)) {
                        player.sendMessage(Text.of("W poblizu jest juz aktywny zamek!", NamedTextColor.RED));
                        return false;
                    }
                    positions.add(pos);
                    saved.add(block.getState());
                }
            }
        }
        backups.put(id, saved);
        activeBlocks.put(id, positions);
        for (BlockPos pos : positions) protectedBlocks.put(pos, id);

        // Wklejenie schematu.
        for (int y = 0; y < schematic.height; y++) {
            for (int z = 0; z < schematic.length; z++) {
                for (int x = 0; x < schematic.width; x++) {
                    if (!schematic.shouldPaste(x, y, z)) continue;
                    BlockData data = blockData[schematic.paletteIndexAt(x, y, z)];
                    world.getBlockAt(bx + x, by + y, bz + z).setBlockData(data, false);
                }
            }
        }

        player.sendMessage(Text.of("Zamek z piasku zostal stworzony na " + DURATION_SECONDS + " sekund!", NamedTextColor.GOLD));
        world.playSound(player.getLocation(), Sound.BLOCK_SAND_PLACE, 1f, 0.8f);
        world.spawnParticle(Particle.CLOUD,
                new Location(world, bx + schematic.width / 2.0, by + 1, bz + schematic.length / 2.0),
                35, 2, 1, 2, 0.03);
        Bukkit.getScheduler().runTaskLater(plugin, () -> restore(id), 20L * DURATION_SECONDS);
        return true;
    }

    private void restore(UUID id) {
        List<BlockState> states = backups.remove(id);
        Set<BlockPos> positions = activeBlocks.remove(id);
        if (states == null || positions == null) return;
        for (BlockState state : states) state.update(true, false);
        for (BlockPos pos : positions) protectedBlocks.remove(pos);
    }

    /** Wywolywane przy wylaczaniu pluginu - przywraca teren po wszystkich aktywnych zamkach. */
    void restoreAll() {
        for (UUID id : new ArrayList<>(backups.keySet())) restore(id);
    }

    @EventHandler public void onBreak(BlockBreakEvent e) { if (isProtected(e.getBlock())) e.setCancelled(true); }
    @EventHandler public void onPlace(BlockPlaceEvent e) { if (isProtected(e.getBlockPlaced()) || isProtected(e.getBlockAgainst())) e.setCancelled(true); }
    @EventHandler public void onExplode(BlockExplodeEvent e) { e.blockList().removeIf(this::isProtected); }
    @EventHandler public void onEntityExplode(EntityExplodeEvent e) { e.blockList().removeIf(this::isProtected); }
    @EventHandler public void onBurn(BlockBurnEvent e) { if (isProtected(e.getBlock())) e.setCancelled(true); }
    @EventHandler public void onFade(BlockFadeEvent e) { if (isProtected(e.getBlock())) e.setCancelled(true); }
    @EventHandler public void onPistonExtend(BlockPistonExtendEvent e) { if (e.getBlocks().stream().anyMatch(this::isProtected) || isProtected(e.getBlock().getRelative(e.getDirection()))) e.setCancelled(true); }
    @EventHandler public void onPistonRetract(BlockPistonRetractEvent e) { if (e.getBlocks().stream().anyMatch(this::isProtected)) e.setCancelled(true); }

    private boolean isProtected(Block block) { return protectedBlocks.containsKey(BlockPos.of(block)); }
}
