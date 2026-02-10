package dev.oakheart.playerwarpsplus;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for PlayerWarpsPlus.
 *
 * <p>PlayerWarpsPlus is an addon for the PlayerWarps plugin that adds
 * cinematic countdown and transition effects to warp teleportation.
 *
 * <p>Features:
 * <ul>
 *   <li>Configurable countdown timer with customizable messages</li>
 *   <li>Movement and damage cancellation during countdown</li>
 *   <li>Cinematic camera zoom effect using invisible bat vehicles</li>
 *   <li>Blindness and darkness transition effects</li>
 *   <li>Fully customizable with MiniMessage formatting</li>
 *   <li>Bypass permission for instant teleports</li>
 * </ul>
 *
 * <p>This plugin requires PlayerWarps to be installed and loaded.
 *
 * @author Loralon
 * @version 1.0.0
 */
public final class PlayerWarpsPlus extends JavaPlugin {

    private WarpCommandListener warpCommandListener;
    private int batCleanupTaskId = -1;

    @Override
    public void onEnable() {
        // Check if PlayerWarps is loaded
        if (getServer().getPluginManager().getPlugin("PlayerWarps") == null) {
            getLogger().severe("PlayerWarps not found! This plugin requires PlayerWarps to function.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Save default config if it doesn't exist
        saveDefaultConfig();

        // Register event listener
        warpCommandListener = new WarpCommandListener(this);
        getServer().getPluginManager().registerEvents(warpCommandListener, this);

        // Register reload command
        ReloadCommand reloadCommand = new ReloadCommand(this);
        if (getCommand("playerwarpsplus") != null) {
            getCommand("playerwarpsplus").setExecutor(reloadCommand);
            getCommand("playerwarpsplus").setTabCompleter(reloadCommand);
        } else {
            getLogger().severe("Failed to register 'playerwarpsplus' command - is it defined in plugin.yml?");
        }

        // Start periodic cleanup task to remove orphaned bats
        long cleanupInterval = getConfig().getLong("bat-cleanup-interval", 100L);
        batCleanupTaskId = getServer().getScheduler().runTaskTimer(this, () -> {
            if (warpCommandListener != null) {
                warpCommandListener.cleanupOrphanedBats();
            }
        }, cleanupInterval, cleanupInterval).getTaskId();

        getLogger().info("PlayerWarpsPlus has been enabled!");
        getLogger().info("Countdown duration: " + getConfig().getInt("countdown.duration", 3) + " seconds");
        getLogger().info("IMPORTANT: Remove wait-commands from PlayerWarps config to avoid conflicts!");
    }

    @Override
    public void onDisable() {
        // Cancel periodic cleanup task
        if (batCleanupTaskId != -1) {
            getServer().getScheduler().cancelTask(batCleanupTaskId);
        }

        // Clean up tracked tasks
        if (warpCommandListener != null) {
            warpCommandListener.cleanup();
        }

        getLogger().info("PlayerWarpsPlus has been disabled.");
    }
}
