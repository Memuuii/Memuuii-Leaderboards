package hu.memuuii.leaderboards;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class LeaderboardCommand implements CommandExecutor, TabCompleter {
    private final MemuuiiLeaderboards plugin;
    private final LeaderboardGui gui;

    public LeaderboardCommand(MemuuiiLeaderboards plugin, LeaderboardGui gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.message("command.players-only"));
                return true;
            }
            gui.openMain(player);
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("memuuii.leaderboards.admin")) {
                if (sender instanceof Player player) player.sendMessage(plugin.message(player, "command.no-permission"));
                else sender.sendMessage(plugin.message("command.no-permission"));
                return true;
            }
            plugin.reloadPlugin();
            if (sender instanceof Player player) player.sendMessage(plugin.message(player, "command.reloaded"));
            else sender.sendMessage(plugin.message("command.reloaded"));
            return true;
        }

        if (sender instanceof Player player) player.sendMessage(plugin.message(player, "command.usage"));
        else sender.sendMessage(plugin.message("command.usage"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1 || !sender.hasPermission("memuuii.leaderboards.admin")) return List.of();
        String input = args[0].toLowerCase(Locale.ROOT);
        return "reload".startsWith(input) ? List.of("reload") : List.of();
    }
}
