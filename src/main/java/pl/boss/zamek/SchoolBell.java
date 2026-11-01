package pl.boss.zamek;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Dzwonek Szkolny: po uzyciu leci fala hałasu (kola Wardena) tam, gdzie patrzy gracz.
 * Gracze trafieni falą sa sciagani w dol i dostaja blokade elytry oraz fajerwerkow (biały cooldown w ekwipunku)
 * do momentu wyladowania.
 */
final class SchoolBell implements Listener {
    static final String ID = "dzwonek";
    private static final Material MATERIAL = Material.GOLD_NUGGET;

    private static final double RANGE = 24;          // zasieg fali (bloki)
    private static final double RADIUS = 4;          // szerokosc fali - ilu blokow od linii patrzenia lapie graczy
    private static final double PULL_SPEED = 1.0;    // minimalna predkosc spadania (bloki/tick) podczas blokady
    private static final int COOLDOWN_TICKS = 20 * 60; // cooldown na elytre/fajerwerki; zdejmowany po wyladowaniu
    private static final int MAX_LOCK_TICKS = 20 * 30; // zabezpieczenie: blokada nigdy nie trwa dluzej

    private static final TextColor ELYTRA_COLOR = TextColor.color(0xC3B1E1);
    private static final TextColor PULLED_COLOR = TextColor.color(0xFF8FB1);

    private final JavaPlugin plugin;
    private final StackSettings stacks;
    private final NamespacedKey itemKey;
    private final Map<UUID, Integer> locked = new HashMap<>(); // gracz -> ile ticków trwa blokada
    private BukkitTask ticker;

    SchoolBell(JavaPlugin plugin, StackSettings stacks) {
        this.plugin = plugin;
        this.stacks = stacks;
        this.itemKey = new NamespacedKey(plugin, "school_bell_item");
    }

    void start() {
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    void stop() {
        if (ticker != null) ticker.cancel();
        for (UUID id : locked.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) clearCooldowns(player);
        }
        locked.clear();
    }

    ItemStack createItem() {
        ItemStack item = new ItemStack(MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.of("DZWONEK SZKOLNY", NamedTextColor.YELLOW));
        meta.lore(java.util.List.of(
                Text.bullet("×", Text.white("Po użyciu w powietrzu tworzymy "), Text.of("fale hałasu", NamedTextColor.AQUA), Text.white(" tam")),
                Text.bullet("×", Text.white("gdzie się patrzymy a gracze w obrębie nie mogą")),
                Text.bullet("×", Text.white("używać "), Text.of("elytry", ELYTRA_COLOR), Text.white(" i zostają "), Text.of("przyciągnięci", PULLED_COLOR), Text.white(" w dół")),
                Text.blank(),
                Text.bullet("×", Text.bold("Uwaga!", NamedTextColor.DARK_RED)),
                Text.bullet("×", Text.of("Przedmiot działa tylko powyżej określonej kratki", NamedTextColor.RED)),
                Text.bullet("×", Text.of("oraz obejmuje graczy, którzy mają pod sobą blok", NamedTextColor.RED)),
                Text.blank(),
                Text.of("Kliknij PPM aby użyć", NamedTextColor.GREEN)));
        meta.setItemModel(new NamespacedKey("ms", "dzwonek_szkolny"));
        stacks.apply(ID, meta);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null) return;
        Player player = event.getPlayer();

        // Zablokowany gracz nie moze uzywac fajerwerkow.
        if (item.getType() == Material.FIREWORK_ROCKET && locked.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE)) return;
        event.setCancelled(true);
        use(player);
    }

    /** Zablokowany gracz nie moze zaczac lotu elytra. */
    @EventHandler
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.isGliding() && event.getEntity() instanceof Player player && locked.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void use(Player user) {
        double minY = plugin.getConfig().getDouble("bell.min-y", 100);
        if (user.getLocation().getY() < minY) {
            user.sendMessage(Text.of("Dzwonek działa tylko powyżej Y = " + (int) minY + "!", NamedTextColor.RED));
            return;
        }

        World world = user.getWorld();
        Location eye = user.getEyeLocation();
        Vector dir = eye.getDirection().normalize();

        world.playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1f);
        animateWave(world, eye.clone(), dir);

        int hits = 0;
        for (Player target : world.getPlayers()) {
            if (target.equals(user) || target.getGameMode() == GameMode.SPECTATOR) continue;
            Vector toTarget = target.getLocation().add(0, 1, 0).toVector().subtract(eye.toVector());
            double along = toTarget.dot(dir);
            if (along < 0 || along > RANGE) continue;
            double sideways = toTarget.clone().subtract(dir.clone().multiply(along)).length();
            if (sideways > RADIUS) continue;
            if (!hasBlockBelow(target)) continue;
            lock(target);
            hits++;
        }
        user.sendMessage(Text.of("Dzwonek zadzwonił! Trafionych graczy: " + hits, NamedTextColor.AQUA));
    }

    /** Kola Wardena rozchodzace sie przed graczem. */
    private void animateWave(World world, Location start, Vector dir) {
        new BukkitRunnable() {
            double distance = 2;

            @Override
            public void run() {
                if (distance > RANGE) {
                    cancel();
                    return;
                }
                world.spawnParticle(Particle.SONIC_BOOM, start.clone().add(dir.clone().multiply(distance)), 1, 0, 0, 0, 0);
                distance += 3;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean hasBlockBelow(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        double distance = Math.max(1, loc.getY() - world.getMinHeight());
        RayTraceResult result = world.rayTraceBlocks(loc, new Vector(0, -1, 0), distance, FluidCollisionMode.NEVER, true);
        return result != null;
    }

    private void lock(Player target) {
        locked.put(target.getUniqueId(), 0);
        target.setGliding(false);
        target.setCooldown(Material.ELYTRA, COOLDOWN_TICKS);
        target.setCooldown(Material.FIREWORK_ROCKET, COOLDOWN_TICKS);
        Vector v = target.getVelocity();
        target.setVelocity(new Vector(v.getX() * 0.3, -PULL_SPEED * 1.5, v.getZ() * 0.3));
        target.playSound(target.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.2f);
        target.sendMessage(Text.of("Dzwonek szkolny ściągnął cię w dół! Elytra i fajerwerki są zablokowane do lądowania.", NamedTextColor.RED));
    }

    private void tick() {
        Iterator<Map.Entry<UUID, Integer>> it = locked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            int ticks = entry.getValue() + 1;
            if (player == null || player.isDead() || ticks > MAX_LOCK_TICKS || hasLanded(player)) {
                if (player != null) clearCooldowns(player);
                it.remove();
                continue;
            }
            entry.setValue(ticks);
            if (player.isGliding()) player.setGliding(false);
            Vector v = player.getVelocity();
            if (v.getY() > -PULL_SPEED) {
                v.setY(-PULL_SPEED);
                player.setVelocity(v);
            }
        }
    }

    private boolean hasLanded(Player player) {
        return player.isOnGround() || player.isInWater() || player.isInLava() || player.isFlying()
                || player.getGameMode() == GameMode.SPECTATOR;
    }

    private void clearCooldowns(Player player) {
        player.setCooldown(Material.ELYTRA, 0);
        player.setCooldown(Material.FIREWORK_ROCKET, 0);
    }
}
