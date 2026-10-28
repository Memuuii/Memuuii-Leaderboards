package hu.memuuii.leaderboards;

import hu.memuuii.core.MemuuiiCore;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class MemuuiiLeaderboards extends JavaPlugin {
    private MemuuiiCore core;
    private LeaderboardGui gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        Plugin corePlugin = getServer().getPluginManager().getPlugin("MemuuiiCore");
        if (!(corePlugin instanceof MemuuiiCore memuuiiCore) || !corePlugin.isEnabled()) {
            getLogger().severe("MemuuiiCore is required.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        core = memuuiiCore;
        core.getMessageManager().register(this);
        gui = new LeaderboardGui(this);
        getServer().getPluginManager().registerEvents(gui, this);

        PluginCommand command = getCommand("toplista");
        if (command == null) {
            getLogger().severe("Toplista command is missing from plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        LeaderboardCommand handler = new LeaderboardCommand(this, gui);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
        getLogger().info("MemuuiiLeaderboards enabled.");
    }

    @Override
    public void onDisable() {
        if (core != null && core.getMessageManager() != null) core.getMessageManager().unregister(this);
        gui = null;
        core = null;
    }

    public void reloadPlugin() {
        reloadConfig();
        if (core != null) core.getMessageManager().reload(this);
        if (gui != null) gui.reload();
    }

    public String message(Player player, String path) {
        return core.getMessageManager().get(this, player, path);
    }

    public String message(Player player, String path, Map<String, ?> placeholders) {
        return core.getMessageManager().get(this, player, path, placeholders);
    }

    public String message(String path) {
        return core.getMessageManager().get(this, path, core.getPlayerLanguageService().defaultLanguage());
    }

    public MemuuiiCore getCore() {
        return core;
    }
}
