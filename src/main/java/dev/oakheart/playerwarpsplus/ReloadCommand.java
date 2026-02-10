package dev.oakheart.playerwarpsplus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Command handler for the /playerwarpsplus reload command.
 *
 * <p>This command allows server administrators with the appropriate permission
 * to reload the plugin's configuration without restarting the server.
 *
 * <p>Usage: /playerwarpsplus reload
 * <p>Aliases: /pwplus reload, /pwp reload
 * <p>Permission: playerwarpsplus.reload (default: op)
 *
 * <p>After reloading, displays a summary of key configuration values including:
 * <ul>
 *   <li>Countdown duration</li>
 *   <li>Final sound enabled/disabled status</li>
 *   <li>Blindness effect enabled/disabled status</li>
 * </ul>
 */
public class ReloadCommand implements CommandExecutor, TabCompleter {

    private final PlayerWarpsPlus plugin;

    public ReloadCommand(PlayerWarpsPlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check permission first to avoid leaking command structure
        if (!sender.hasPermission("playerwarpsplus.reload")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        // Check if "reload" subcommand was provided
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(Component.text("Usage: /" + label + " reload", NamedTextColor.RED));
            return true;
        }

        try {
            // Reload config
            plugin.reloadConfig();

            sender.sendMessage(Component.text()
                    .append(Component.text("✓", NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text(" PlayerWarpsPlus config reloaded successfully!", NamedTextColor.GREEN))
                    .build());

            sender.sendMessage(Component.text()
                    .append(Component.text("Countdown duration: ", NamedTextColor.GRAY))
                    .append(Component.text(plugin.getConfig().getInt("countdown.duration", 3) + " seconds", NamedTextColor.WHITE))
                    .build());

            sender.sendMessage(Component.text()
                    .append(Component.text("Final sound: ", NamedTextColor.GRAY))
                    .append(Component.text(plugin.getConfig().getBoolean("countdown.final-sound.enabled", true) ? "Enabled" : "Disabled", NamedTextColor.WHITE))
                    .build());

            sender.sendMessage(Component.text()
                    .append(Component.text("Blindness effect: ", NamedTextColor.GRAY))
                    .append(Component.text(plugin.getConfig().getBoolean("countdown.blindness.enabled", true) ? "Enabled" : "Disabled", NamedTextColor.WHITE))
                    .build());

        } catch (Exception e) {
            sender.sendMessage(Component.text()
                    .append(Component.text("✗", NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.text(" Error reloading config: " + e.getMessage(), NamedTextColor.RED))
                    .build());
            plugin.getLogger().log(Level.SEVERE, "Error reloading config", e);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Tab complete "reload" if player has permission
            if (sender.hasPermission("playerwarpsplus.reload")) {
                String input = args[0].toLowerCase();
                if ("reload".startsWith(input)) {
                    completions.add("reload");
                }
            }
        }

        return completions;
    }
}
