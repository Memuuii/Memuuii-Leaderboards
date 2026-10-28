package hu.memuuii.leaderboards;

import hu.memuuii.api.economy.BalanceEntry;
import hu.memuuii.api.economy.EconomyService;
import hu.memuuii.api.identity.IdentityService;
import hu.memuuii.api.stats.PlayerStats;
import hu.memuuii.api.stats.StatsLeaderboardMetric;
import hu.memuuii.api.stats.StatsService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LeaderboardGui implements Listener {
    private static final int[] RESULT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final MemuuiiLeaderboards plugin;
    private int topLimit;

    public LeaderboardGui(MemuuiiLeaderboards plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        topLimit = Math.max(1, Math.min(RESULT_SLOTS.length, plugin.getConfig().getInt("top-limit", 10)));
    }

    public void openMain(Player player) {
        int size = size("gui.main.size", 27);
        GuiHolder holder = new GuiHolder(Menu.MAIN, null);
        Inventory inventory = Bukkit.createInventory(holder, size, plugin.message(player, "gui.main.title"));
        holder.inventory = inventory;
        inventory.setItem(slot("gui.main.combat.slot", 11, size), item(
                material("gui.main.combat.material", Material.NETHERITE_SWORD),
                plugin.message(player, "gui.main.combat.name"),
                List.of(plugin.message(player, "gui.main.combat.lore"))
        ));
        inventory.setItem(slot("gui.main.currency.slot", 15, size), item(
                material("gui.main.currency.material", Material.GOLD_INGOT),
                plugin.message(player, "gui.main.currency.name"),
                List.of(plugin.message(player, "gui.main.currency.lore"))
        ));
        player.openInventory(inventory);
    }

    private void openCombat(Player player) {
        int size = size("gui.combat.size", 27);
        GuiHolder holder = new GuiHolder(Menu.COMBAT, null);
        Inventory inventory = Bukkit.createInventory(holder, size, plugin.message(player, "gui.combat.title"));
        holder.inventory = inventory;
        inventory.setItem(slot("gui.combat.kills.slot", 10, size), item(material("gui.combat.kills.material", Material.DIAMOND_SWORD), plugin.message(player, "gui.combat.kills.name"), List.of(plugin.message(player, "gui.combat.kills.lore"))));
        inventory.setItem(slot("gui.combat.deaths.slot", 12, size), item(material("gui.combat.deaths.material", Material.SKELETON_SKULL), plugin.message(player, "gui.combat.deaths.name"), List.of(plugin.message(player, "gui.combat.deaths.lore"))));
        inventory.setItem(slot("gui.combat.streak.slot", 14, size), item(material("gui.combat.streak.material", Material.BLAZE_POWDER), plugin.message(player, "gui.combat.streak.name"), List.of(plugin.message(player, "gui.combat.streak.lore"))));
        inventory.setItem(slot("gui.combat.kd.slot", 16, size), item(material("gui.combat.kd.material", Material.COMPARATOR), plugin.message(player, "gui.combat.kd.name"), List.of(plugin.message(player, "gui.combat.kd.lore"))));
        inventory.setItem(slot("gui.combat.back.slot", 22, size), backItem(player, "gui.combat.back.material"));
        player.openInventory(inventory);
    }

    private void openCurrency(Player player) {
        int size = size("gui.currency.size", 27);
        GuiHolder holder = new GuiHolder(Menu.CURRENCY, null);
        Inventory inventory = Bukkit.createInventory(holder, size, plugin.message(player, "gui.currency.title"));
        holder.inventory = inventory;
        inventory.setItem(slot("gui.currency.money.slot", 13, size), item(material("gui.currency.money.material", Material.EMERALD), plugin.message(player, "gui.currency.money.name"), List.of(plugin.message(player, "gui.currency.money.lore"))));
        inventory.setItem(slot("gui.currency.back.slot", 22, size), backItem(player, "gui.currency.back.material"));
        player.openInventory(inventory);
    }

    private void loadStats(Player player, Metric metric) {
        Optional<StatsService> stats = plugin.getCore().getServiceRegistry().find(StatsService.class);
        if (stats.isEmpty()) {
            player.sendMessage(plugin.message(player, "error.stats-unavailable"));
            return;
        }
        openLoading(player, metric);
        stats.get().top(metric.apiMetric, topLimit)
                .thenCompose(entries -> resolveStatsEntries(entries, metric))
                .whenComplete((entries, throwable) -> finishLoad(player, metric, entries, throwable));
    }

    private void loadMoney(Player player) {
        Optional<EconomyService> economy = plugin.getCore().getServiceRegistry().find(EconomyService.class);
        if (economy.isEmpty()) {
            player.sendMessage(plugin.message(player, "error.economy-unavailable"));
            return;
        }
        openLoading(player, Metric.MONEY);
        economy.get().topBalances(topLimit)
                .thenCompose(this::resolveMoneyEntries)
                .whenComplete((entries, throwable) -> finishLoad(player, Metric.MONEY, entries, throwable));
    }

    private void finishLoad(Player player, Metric metric, List<Entry> entries, Throwable throwable) {
        if (!plugin.isEnabled()) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled() || !player.isOnline()) return;
            if (throwable != null) {
                plugin.getLogger().warning("Failed to load " + metric.name().toLowerCase(Locale.ROOT) + " leaderboard: " + rootMessage(throwable));
                player.closeInventory();
                player.sendMessage(plugin.message(player, "error.load-failed"));
                return;
            }
            openResult(player, metric, entries == null ? List.of() : entries);
        });
    }

    private CompletableFuture<List<Entry>> resolveStatsEntries(List<PlayerStats> stats, Metric metric) {
        Optional<IdentityService> identity = plugin.getCore().getServiceRegistry().find(IdentityService.class);
        List<CompletableFuture<Entry>> futures = new ArrayList<>();
        for (PlayerStats entry : stats) {
            CompletableFuture<String> nameFuture = identity
                    .map(service -> resolveName(service, entry.playerId()))
                    .orElseGet(() -> CompletableFuture.completedFuture(shortId(entry.playerId())));
            futures.add(nameFuture.thenApply(name -> new Entry(entry.playerId(), name, metricValue(metric, entry))));
        }
        return sequence(futures);
    }

    private CompletableFuture<List<Entry>> resolveMoneyEntries(List<BalanceEntry> balances) {
        Optional<IdentityService> identity = plugin.getCore().getServiceRegistry().find(IdentityService.class);
        List<CompletableFuture<Entry>> futures = new ArrayList<>();
        for (BalanceEntry balance : balances) {
            CompletableFuture<String> nameFuture = identity
                    .map(service -> resolveName(service, balance.playerId()))
                    .orElseGet(() -> CompletableFuture.completedFuture(shortId(balance.playerId())));
            futures.add(nameFuture.thenApply(name -> new Entry(balance.playerId(), name, formatLong(balance.balance()))));
        }
        return sequence(futures);
    }

    private CompletableFuture<String> resolveName(IdentityService identity, UUID playerId) {
        return identity.getName(playerId)
                .exceptionally(throwable -> Optional.empty())
                .thenApply(name -> name.filter(value -> !value.isBlank()).orElseGet(() -> shortId(playerId)));
    }

    private CompletableFuture<List<Entry>> sequence(List<CompletableFuture<Entry>> futures) {
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> futures.stream().map(CompletableFuture::join).toList());
    }

    private void openLoading(Player player, Metric metric) {
        int size = size("gui.result.size", 54);
        GuiHolder holder = new GuiHolder(Menu.RESULT, metric);
        Inventory inventory = Bukkit.createInventory(holder, size, plugin.message(player, metric.titlePath));
        holder.inventory = inventory;
        inventory.setItem(size / 2, item(material("gui.result.loading-material", Material.CLOCK), plugin.message(player, "gui.result.loading"), List.of()));
        inventory.setItem(slot("gui.result.back-slot", 49, size), backItem(player, "gui.result.back-material"));
        player.openInventory(inventory);
    }

    private void openResult(Player player, Metric metric, List<Entry> entries) {
        int size = size("gui.result.size", 54);
        GuiHolder holder = new GuiHolder(Menu.RESULT, metric);
        Inventory inventory = Bukkit.createInventory(holder, size, plugin.message(player, metric.titlePath));
        holder.inventory = inventory;
        if (entries.isEmpty()) {
            inventory.setItem(size / 2, item(Material.PAPER, plugin.message(player, "gui.result.empty"), List.of()));
        } else {
            int count = Math.min(Math.min(entries.size(), topLimit), RESULT_SLOTS.length);
            for (int i = 0; i < count; i++) {
                Entry entry = entries.get(i);
                inventory.setItem(RESULT_SLOTS[i], playerEntry(player, metric, entry, i + 1));
            }
        }
        inventory.setItem(slot("gui.result.back-slot", 49, size), backItem(player, "gui.result.back-material"));
        player.openInventory(inventory);
    }

    private ItemStack playerEntry(Player viewer, Metric metric, Entry entry, int position) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta rawMeta = item.getItemMeta();
        if (rawMeta instanceof SkullMeta skullMeta) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.playerId);
            skullMeta.setOwningPlayer(offlinePlayer);
            skullMeta.setDisplayName(plugin.message(viewer, "gui.result.entry-name", Map.of("position", position, "name", entry.name)));
            skullMeta.setLore(List.of(plugin.message(viewer, metric.valuePath, Map.of("value", entry.value))));
            item.setItemMeta(skullMeta);
            return item;
        }
        return item(Material.PAPER, plugin.message(viewer, "gui.result.entry-name", Map.of("position", position, "name", entry.name)), List.of(plugin.message(viewer, metric.valuePath, Map.of("value", entry.value))));
    }

    private ItemStack backItem(Player player, String materialPath) {
        return item(material(materialPath, Material.ARROW), plugin.message(player, "gui.back.name"), List.of());
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (!lore.isEmpty()) meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private int size(String path, int fallback) {
        int value = plugin.getConfig().getInt(path, fallback);
        if (value < 9 || value > 54 || value % 9 != 0) return fallback;
        return value;
    }

    private int slot(String path, int fallback, int size) {
        int value = plugin.getConfig().getInt(path, fallback);
        if (value < 0 || value >= size) return Math.max(0, Math.min(size - 1, fallback));
        return value;
    }

    private Material material(String path, Material fallback) {
        String configured = plugin.getConfig().getString(path, fallback.name());
        Material material = configured == null ? null : Material.matchMaterial(configured);
        return material == null ? fallback : material;
    }

    private String metricValue(Metric metric, PlayerStats stats) {
        return switch (metric) {
            case KILLS -> formatLong(stats.kills());
            case DEATHS -> formatLong(stats.deaths());
            case STREAK -> formatLong(stats.bestKillStreak());
            case KD -> String.format(Locale.ROOT, "%.2f", stats.killDeathRatio());
            case MONEY -> "0";
        };
    }

    private String formatLong(long value) {
        return String.format(Locale.US, "%,d", value).replace(',', ' ');
    }

    private String shortId(UUID playerId) {
        return playerId.toString().substring(0, 8);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof GuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int clicked = event.getRawSlot();
        if (clicked < 0 || clicked >= top.getSize()) return;

        switch (holder.menu) {
            case MAIN -> handleMain(player, clicked, top.getSize());
            case COMBAT -> handleCombat(player, clicked, top.getSize());
            case CURRENCY -> handleCurrency(player, clicked, top.getSize());
            case RESULT -> handleResult(player, holder.metric, clicked, top.getSize());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiHolder) event.setCancelled(true);
    }

    private void handleMain(Player player, int clicked, int size) {
        if (clicked == slot("gui.main.combat.slot", 11, size)) openCombat(player);
        else if (clicked == slot("gui.main.currency.slot", 15, size)) openCurrency(player);
    }

    private void handleCombat(Player player, int clicked, int size) {
        if (clicked == slot("gui.combat.kills.slot", 10, size)) loadStats(player, Metric.KILLS);
        else if (clicked == slot("gui.combat.deaths.slot", 12, size)) loadStats(player, Metric.DEATHS);
        else if (clicked == slot("gui.combat.streak.slot", 14, size)) loadStats(player, Metric.STREAK);
        else if (clicked == slot("gui.combat.kd.slot", 16, size)) loadStats(player, Metric.KD);
        else if (clicked == slot("gui.combat.back.slot", 22, size)) openMain(player);
    }

    private void handleCurrency(Player player, int clicked, int size) {
        if (clicked == slot("gui.currency.money.slot", 13, size)) loadMoney(player);
        else if (clicked == slot("gui.currency.back.slot", 22, size)) openMain(player);
    }

    private void handleResult(Player player, Metric metric, int clicked, int size) {
        if (clicked != slot("gui.result.back-slot", 49, size)) return;
        if (metric == Metric.MONEY) openCurrency(player);
        else openCombat(player);
    }

    private enum Menu {
        MAIN,
        COMBAT,
        CURRENCY,
        RESULT
    }

    private enum Metric {
        KILLS(StatsLeaderboardMetric.KILLS, "gui.result.kills-title", "gui.result.kills-value"),
        DEATHS(StatsLeaderboardMetric.DEATHS, "gui.result.deaths-title", "gui.result.deaths-value"),
        STREAK(StatsLeaderboardMetric.STREAK, "gui.result.streak-title", "gui.result.streak-value"),
        KD(StatsLeaderboardMetric.KD, "gui.result.kd-title", "gui.result.kd-value"),
        MONEY(null, "gui.result.money-title", "gui.result.money-value");

        private final StatsLeaderboardMetric apiMetric;
        private final String titlePath;
        private final String valuePath;

        Metric(StatsLeaderboardMetric apiMetric, String titlePath, String valuePath) {
            this.apiMetric = apiMetric;
            this.titlePath = titlePath;
            this.valuePath = valuePath;
        }
    }

    private record Entry(UUID playerId, String name, String value) { }

    private static final class GuiHolder implements InventoryHolder {
        private final Menu menu;
        private final Metric metric;
        private Inventory inventory;

        private GuiHolder(Menu menu, Metric metric) {
            this.menu = menu;
            this.metric = metric;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
