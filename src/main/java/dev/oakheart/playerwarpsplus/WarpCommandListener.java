package dev.oakheart.playerwarpsplus;

import com.olziedev.playerwarps.api.events.warp.PlayerWarpTeleportEvent;
import dev.oakheart.playerwarpsplus.util.MessageFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles PlayerWarps warp events and provides countdown functionality
 * with cinematic transition effects.
 *
 * <p>This listener intercepts warp teleport events and adds:
 * <ul>
 *   <li>Configurable countdown timer with title/subtitle messages</li>
 *   <li>Movement and damage cancellation during countdown</li>
 *   <li>Invisible bat-based camera zoom effect</li>
 *   <li>Blindness/darkness transition effects</li>
 *   <li>Sound effects and customizable messaging</li>
 * </ul>
 *
 * <p>Players with the "playerwarpsplus.bypass" permission skip the countdown
 * and teleport instantly.
 */
public class WarpCommandListener implements Listener {

    // Constants for magic numbers
    private static final double BAT_SPAWN_OFFSET = 0.3; // Spawn bat below player to compensate for mount height
    private static final int INVISIBILITY_DURATION_TICKS = 40; // 2 seconds - enough for transition, safe if player logs out
    private static final long PRE_TELEPORT_DELAY_TICKS = 2L; // Minimal delay to ensure bat entity is removed before teleport
    private static final double INITIAL_BAT_SPEED = 0.3; // Starting velocity for bat zoom effect
    private static final double BAT_ACCELERATION = 0.1; // Velocity increase per tick
    private static final long INVISIBILITY_APPLICATION_OFFSET_TICKS = 3L; // Apply invisibility 3 ticks before bat mount
    private static final double MOVEMENT_THRESHOLD = 0.1; // Minimum movement distance (in blocks) to cancel countdown

    private final PlayerWarpsPlus plugin;
    private final Map<UUID, List<BukkitTask>> playerCountdownTasks = new ConcurrentHashMap<>();
    private final Set<UUID> waitingPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> transitioningPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Bat> playerBats = new ConcurrentHashMap<>();
    private final Map<UUID, Location> playerOriginalLocations = new ConcurrentHashMap<>(); // Store original location for disconnect safety
    private final Set<UUID> postCountdownPlayers = ConcurrentHashMap.newKeySet(); // Players who completed countdown, bypass interception

    /**
     * Simple data holder for warp information
     */
    private static class WarpData {
        final String warpName;
        final Location destination;
        final UUID playerUuid;
        final PlayerWarpTeleportEvent originalEvent;

        WarpData(String warpName, Location destination, UUID playerUuid, PlayerWarpTeleportEvent originalEvent) {
            this.warpName = warpName;
            this.destination = destination;
            this.playerUuid = playerUuid;
            this.originalEvent = originalEvent;
        }
    }

    public WarpCommandListener(PlayerWarpsPlus plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWarpTeleport(PlayerWarpTeleportEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getTeleporter();
        UUID uuid = player.getUniqueId();

        // Check if player has bypass permission - if so, let them teleport instantly
        if (player.hasPermission("playerwarpsplus.bypass")) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info(player.getName() + " has bypass permission, allowing instant teleport");
            }
            return; // Let the event proceed normally (instant teleport)
        }

        // Check if this is a post-countdown warp (let it proceed)
        if (postCountdownPlayers.remove(uuid)) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info(player.getName() + " completed countdown, allowing warp to proceed");
            }
            return; // Let the event proceed normally
        }

        // Otherwise, cancel the immediate teleport and start our countdown
        event.setCancelled(true);

        // Validate and extract warp data
        WarpData warpData = validateAndExtractWarpData(event, player);
        if (warpData == null) {
            return; // Validation failed, error already logged
        }

        // Start the countdown sequence
        startCountdown(player, warpData);
    }

    /**
     * Validates and extracts warp data from the event
     *
     * @param event The warp teleport event
     * @param player The player warping
     * @return WarpData object if valid, null if validation fails
     */
    private WarpData validateAndExtractWarpData(PlayerWarpTeleportEvent event, Player player) {
        // Validate warp data
        if (event.getPlayerWarp() == null) {
            plugin.getLogger().severe("PlayerWarp is null for player: " + player.getName());
            return null;
        }

        if (event.getPlayerWarp().getWarpLocation() == null) {
            plugin.getLogger().severe("Warp location is null for player: " + player.getName());
            return null;
        }

        String warpName = event.getPlayerWarp().getWarpName();
        Location destination = event.getPlayerWarp().getWarpLocation().getLocation();

        // Validate destination
        if (destination == null) {
            plugin.getLogger().severe("Warp destination location is null for player: " + player.getName());
            return null;
        }

        // Validate warp name
        if (warpName == null || warpName.isEmpty()) {
            plugin.getLogger().warning("Warp name is null or empty for player: " + player.getName());
            warpName = "Unknown";
        }

        return new WarpData(warpName, destination, player.getUniqueId(), event);
    }

    /**
     * Starts the countdown sequence for a player
     *
     * @param player The player to start countdown for
     * @param warpData The warp data containing destination and name
     */
    private void startCountdown(Player player, WarpData warpData) {
        UUID uuid = warpData.playerUuid;

        // Mark player as waiting
        waitingPlayers.add(uuid);

        // Initialize task list
        List<BukkitTask> tasks = new ArrayList<>();

        // Get countdown duration from config
        int duration = getValidatedInt("countdown.duration", 3, 1, 10);
        int zoomDuration = getValidatedInt("countdown.zoom-duration", 5, 1, 100);
        int blackDuration = getValidatedInt("countdown.black-duration", 15, 1, 100);

        // Schedule all countdown tasks
        scheduleCountdownMessages(player, warpData, tasks, duration);
        scheduleTransitionEffects(player, warpData, tasks, duration);
        scheduleBatZoom(player, warpData, tasks, duration, zoomDuration);
        scheduleFinalMessage(player, warpData, tasks, duration, zoomDuration, blackDuration);
        scheduleTeleport(player, warpData, tasks, duration, zoomDuration, blackDuration);

        // Store tasks for cleanup
        playerCountdownTasks.put(uuid, tasks);
    }

    /**
     * Schedules countdown message tasks (3, 2, 1)
     *
     * @param player The player
     * @param warpData The warp data
     * @param tasks List to add tasks to
     * @param duration Countdown duration in seconds
     */
    private void scheduleCountdownMessages(Player player, WarpData warpData, List<BukkitTask> tasks, int duration) {
        UUID uuid = warpData.playerUuid;

        // Schedule countdown messages (3, 2, 1)
        for (int i = 0; i < duration; i++) {
            final int secondsLeft = duration - i;
            long delay = i * 20L;

            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    if (!waitingPlayers.contains(uuid)) return;
                    handleCountdownMessage(player, warpData.warpName, secondsLeft);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error in countdown message task for " + player.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                    cancelCountdown(player);
                }
            }, delay);

            tasks.add(task);
        }
    }

    /**
     * Schedules transition effect tasks (darkness, blindness, invisibility)
     *
     * @param player The player
     * @param warpData The warp data
     * @param tasks List to add tasks to
     * @param duration Countdown duration in seconds
     */
    private void scheduleTransitionEffects(Player player, WarpData warpData, List<BukkitTask> tasks, int duration) {
        UUID uuid = warpData.playerUuid;

        // Apply darkness/blindness early
        long darknessDelay = (duration - 1) * 20L;
        BukkitTask darknessTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (!waitingPlayers.contains(uuid)) return;

                if (plugin.getConfig().getBoolean("countdown.blindness.enabled", true)) {
                    applyBlindness(player);
                }
                if (plugin.getConfig().getBoolean("countdown.darkness.enabled", true)) {
                    applyDarkness(player);
                }

                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Applied darkness/blindness early for " + player.getName());
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error in darkness task for " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
                cancelCountdown(player);
            }
        }, darknessDelay);
        tasks.add(darknessTask);

        // Apply invisibility just before bat mounting
        long invisDelay = duration * 20L - INVISIBILITY_APPLICATION_OFFSET_TICKS;
        BukkitTask invisTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (!waitingPlayers.contains(uuid)) return;

                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.INVISIBILITY,
                        INVISIBILITY_DURATION_TICKS,
                        0,
                        false,
                        false
                ));

                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Applied invisibility for " + player.getName());
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error in invisibility task for " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
                cancelCountdown(player);
            }
        }, invisDelay);
        tasks.add(invisTask);
    }

    /**
     * Schedules bat zoom effect task
     *
     * @param player The player
     * @param warpData The warp data
     * @param tasks List to add tasks to
     * @param duration Countdown duration in seconds
     * @param zoomDuration Zoom effect duration in ticks
     */
    private void scheduleBatZoom(Player player, WarpData warpData, List<BukkitTask> tasks, int duration, int zoomDuration) {
        UUID uuid = warpData.playerUuid;

        long zoomDelay = duration * 20L;
        BukkitTask zoomTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (!waitingPlayers.contains(uuid)) return;

                transitioningPlayers.add(uuid);

                Location playerLoc = player.getLocation();
                // Store original location for disconnect safety - if player logs out during zoom,
                // they'll be teleported back here instead of being stuck in a wall
                playerOriginalLocations.put(uuid, playerLoc.clone());

                Location batLoc = playerLoc.clone();
                batLoc.setY(batLoc.getY() - BAT_SPAWN_OFFSET);

                Bat bat = player.getWorld().spawn(batLoc, Bat.class);
                bat.setInvisible(true);
                bat.setInvulnerable(true);
                bat.setSilent(true);
                bat.setAI(false);
                bat.setGravity(false);
                bat.setAwake(true);

                playerBats.put(uuid, bat);
                bat.addPassenger(player);

                // Apply speed effect to widen FOV during zoom
                int speedAmplifier = getValidatedInt("countdown.zoom-speed-amplifier", 4, 0, 10);
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED,
                        zoomDuration + 20, // Duration slightly longer than zoom
                        speedAmplifier,
                        false,
                        false
                ));

                // Calculate backwards direction based on yaw only (ignore pitch)
                // This ensures consistent movement regardless of where player is looking vertically
                double yawRadians = Math.toRadians(playerLoc.getYaw());
                Vector direction = new Vector(
                        Math.sin(yawRadians),   // Backwards X (opposite of forward)
                        0.5,                     // Upward movement
                        -Math.cos(yawRadians)   // Backwards Z (opposite of forward)
                ).normalize();

                final double[] speed = {INITIAL_BAT_SPEED};
                final BukkitTask[] moveTask = new BukkitTask[1];
                moveTask[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    try {
                        if (!transitioningPlayers.contains(uuid) || !bat.isValid()) {
                            if (moveTask[0] != null) {
                                moveTask[0].cancel();
                            }
                            return;
                        }

                        speed[0] += BAT_ACCELERATION;
                        Vector movement = direction.clone().multiply(speed[0]);
                        // Use teleportation instead of velocity - velocity doesn't work reliably with passengers
                        // Must use RETAIN_PASSENGERS flag or passengers won't move with the entity
                        bat.teleport(bat.getLocation().add(movement),
                                io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
                        player.sendActionBar(Component.empty());
                    } catch (Exception e) {
                        plugin.getLogger().severe("Error in bat movement task for " + player.getName() + ": " + e.getMessage());
                        e.printStackTrace();
                        if (moveTask[0] != null) {
                            moveTask[0].cancel();
                        }
                        cancelCountdown(player);
                    }
                }, 0L, 1L);

                tasks.add(moveTask[0]);

                if (plugin.getConfig().getBoolean("countdown.final-sound.enabled", true)) {
                    playFinalSound(player);
                }

                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Player " + player.getName() + " is now riding invisible bat");
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error in zoom task for " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
                cancelCountdown(player);
            }
        }, zoomDelay);
        tasks.add(zoomTask);
    }

    /**
     * Schedules final message task
     *
     * @param player The player
     * @param warpData The warp data
     * @param tasks List to add tasks to
     * @param duration Countdown duration in seconds
     * @param zoomDuration Zoom duration in ticks
     * @param blackDuration Black screen duration in ticks
     */
    private void scheduleFinalMessage(Player player, WarpData warpData, List<BukkitTask> tasks, int duration, int zoomDuration, int blackDuration) {
        UUID uuid = warpData.playerUuid;

        long zoomDelay = duration * 20L;
        long blackDelay = zoomDelay + zoomDuration;
        BukkitTask blackTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (!waitingPlayers.contains(uuid)) return;

                handleFinalMessage(player, warpData.warpName);

                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Showing final message for " + player.getName());
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error in final message task for " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
                cancelCountdown(player);
            }
        }, blackDelay);
        tasks.add(blackTask);
    }

    /**
     * Schedules the actual teleport task
     *
     * @param player The player
     * @param warpData The warp data
     * @param tasks List to add tasks to
     * @param duration Countdown duration in seconds
     * @param zoomDuration Zoom duration in ticks
     * @param blackDuration Black screen duration in ticks
     */
    private void scheduleTeleport(Player player, WarpData warpData, List<BukkitTask> tasks, int duration, int zoomDuration, int blackDuration) {
        UUID uuid = warpData.playerUuid;

        long zoomDelay = duration * 20L;
        long teleportDelay = zoomDelay + zoomDuration + blackDuration;
        BukkitTask teleportTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (!waitingPlayers.contains(uuid)) return;

                // Clean up countdown state
                waitingPlayers.remove(uuid);
                transitioningPlayers.remove(uuid);
                playerCountdownTasks.remove(uuid);
                playerOriginalLocations.remove(uuid);

                // Mark player as post-countdown so next warp event will proceed
                postCountdownPlayers.add(uuid);

                // Execute the warp command while player is still on the bat
                // The bat will teleport with the player, creating a seamless transition
                String warpCommand = plugin.getConfig().getString("warp-command", "pw");
                player.performCommand(warpCommand + " " + warpData.warpName);

                // Clean up bat and effects after teleport completes
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        // Remove player from bat and despawn it
                        Bat bat = playerBats.remove(uuid);
                        if (bat != null && bat.isValid()) {
                            bat.removePassenger(player);
                            bat.remove();
                        }

                        // Remove invisibility and speed effects after a few more ticks to ensure bat is fully despawned
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (player.isOnline()) {
                                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                                player.removePotionEffect(PotionEffectType.SPEED);
                            }
                        }, 10L);

                        // Optionally play arrival sound at destination
                        if (plugin.getConfig().getBoolean("countdown.arrival-sound.enabled", false)) {
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                if (player.isOnline()) {
                                    playArrivalSound(player);
                                }
                            }, 8L);
                        }
                    } else {
                        // Player went offline, clean up the bat anyway
                        Bat bat = playerBats.remove(uuid);
                        if (bat != null && bat.isValid()) {
                            bat.remove();
                        }
                    }
                }, PRE_TELEPORT_DELAY_TICKS);

                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Teleported " + player.getName() + " to " + warpData.warpName);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error in teleport task for " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
                cancelCountdown(player);
            }
        }, teleportDelay);
        tasks.add(teleportTask);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!waitingPlayers.contains(uuid)) return;

        // If player is in transition (spectator mode), prevent ANY movement
        if (transitioningPlayers.contains(uuid)) {
            // Cancel the movement event completely
            event.setCancelled(true);
            return;
        }

        // Check for actual position change (not just head rotation)
        // Use a threshold to ignore tiny floating-point position changes that occur naturally
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) return; // Safety check

        // Calculate distance moved
        double distanceSquared = from.distanceSquared(to);

        // Only cancel if player has actually moved beyond the threshold
        // Using distanceSquared is more efficient than distance (avoids sqrt calculation)
        if (distanceSquared > MOVEMENT_THRESHOLD * MOVEMENT_THRESHOLD) {
            cancelCountdown(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();

        // Block all damage during transition (zoom) phase - player may clip through blocks
        if (transitioningPlayers.contains(uuid)) {
            event.setCancelled(true);
            return;
        }

        // Cancel countdown if player takes damage during countdown phase
        if (waitingPlayers.contains(uuid)) {
            cancelCountdown(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up countdown tasks for disconnecting players
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (waitingPlayers.contains(uuid) || playerCountdownTasks.containsKey(uuid)) {
            // If player disconnects during zoom, teleport them back to original location
            // This prevents them from being stuck in walls when they log back in
            Location originalLocation = playerOriginalLocations.get(uuid);
            if (originalLocation != null && transitioningPlayers.contains(uuid)) {
                player.teleport(originalLocation);
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Teleported " + player.getName() + " back to original location on disconnect");
                }
            }

            cancelCountdown(player);

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Cleaned up countdown tasks for disconnecting player: " + player.getName());
            }
        }
    }

    /**
     * Handle countdown message (3, 2, 1)
     */
    private void handleCountdownMessage(Player player, String warpName, int secondsLeft) {
        String titleText = plugin.getConfig().getString("countdown.title", "<#f9e59d>ᴡᴀʀᴘɪɴɢ ɪɴ <seconds>");
        String subtitleText = plugin.getConfig().getString("countdown.subtitle", "<white>ᴅᴏ ɴᴏᴛ ᴍᴏᴠᴇ");

        // Get timing from config (0-200 ticks reasonable range)
        int fadeIn = getValidatedInt("countdown.title-timing.fade-in", 10, 0, 200);
        int stay = getValidatedInt("countdown.title-timing.stay", 40, 0, 200);
        int fadeOut = getValidatedInt("countdown.title-timing.fade-out", 10, 0, 200);

        // Send title
        MessageFormatter.sendTitle(player, titleText, subtitleText, fadeIn, stay, fadeOut,
                warpName, secondsLeft);

        // Play sound
        playCountdownSound(player, secondsLeft);
    }

    /**
     * Handle final message (black screen phase)
     * Note: darkness/blindness already applied earlier to allow fade-in time
     */
    private void handleFinalMessage(Player player, String warpName) {
        String titleText = plugin.getConfig().getString("countdown.final-title", "<#7f91fd>ᴡᴀʀᴘɪɴɢ ᴛᴏ");
        String subtitleText = plugin.getConfig().getString("countdown.final-subtitle", "<white><sc>%warp%</sc>");

        // Get timing from config (0-200 ticks reasonable range)
        int fadeIn = getValidatedInt("countdown.final-title-timing.fade-in", 10, 0, 200);
        int stay = getValidatedInt("countdown.final-title-timing.stay", 40, 0, 200);
        int fadeOut = getValidatedInt("countdown.final-title-timing.fade-out", 10, 0, 200);

        // Send title
        MessageFormatter.sendTitle(player, titleText, subtitleText, fadeIn, stay, fadeOut,
                warpName, -1);
    }

    /**
     * Play countdown sound
     */
    private void playCountdownSound(Player player, int secondsLeft) {
        if (!plugin.getConfig().getBoolean("countdown.sound.enabled", true)) return;

        String soundName = plugin.getConfig().getString("countdown.sound.type", "block.amethyst_block.break");
        float volume = (float) getValidatedDouble("countdown.sound.volume", 1.0, 0.0, 10.0);
        float pitch = (float) getValidatedDouble("countdown.sound.pitch-" + secondsLeft, 1.0, 0.0, 2.0);

        playSound(player, soundName, volume, pitch);
    }

    /**
     * Play final sound
     */
    private void playFinalSound(Player player) {
        String soundName = plugin.getConfig().getString("countdown.final-sound.type", "block.glass.break");
        float volume = (float) getValidatedDouble("countdown.final-sound.volume", 1.0, 0.0, 10.0);
        float pitch = (float) getValidatedDouble("countdown.final-sound.pitch", 0.8, 0.0, 2.0);

        playSound(player, soundName, volume, pitch);
    }

    /**
     * Play a sound to a player
     */
    private void playSound(Player player, String soundName, float volume, float pitch) {
        try {
            // soundName should already be in Minecraft format (e.g., "block.amethyst_block.break")
            // Use modern Adventure Sound API with self emitter so sound follows the player
            net.kyori.adventure.sound.Sound sound = net.kyori.adventure.sound.Sound.sound(
                    net.kyori.adventure.key.Key.key("minecraft", soundName),
                    net.kyori.adventure.sound.Sound.Source.MASTER,
                    volume,
                    pitch
            );

            // Play sound with SELF emitter so it follows the player (not positional)
            player.playSound(sound, net.kyori.adventure.sound.Sound.Emitter.self());

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Playing sound: " + soundName);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid sound: " + soundName + " - " + e.getMessage());
        }
    }

    /**
     * Apply blindness effect
     */
    private void applyBlindness(Player player) {
        int duration = getValidatedInt("countdown.blindness.duration", 3, 1, 60);
        int amplifier = getValidatedInt("countdown.blindness.amplifier", 0, 0, 10);

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.BLINDNESS,
                duration * 20,
                amplifier,
                false,
                false
        ));
    }

    /**
     * Apply darkness effect for smoother black screen transition
     */
    private void applyDarkness(Player player) {
        int duration = getValidatedInt("countdown.darkness.duration", 3, 1, 60);
        int amplifier = getValidatedInt("countdown.darkness.amplifier", 0, 0, 10);

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.DARKNESS,
                duration * 20,
                amplifier,
                false,
                false
        ));
    }

    /**
     * Play arrival sound at destination
     */
    private void playArrivalSound(Player player) {
        String soundName = plugin.getConfig().getString("countdown.arrival-sound.type", "entity.enderman.teleport");
        float volume = (float) getValidatedDouble("countdown.arrival-sound.volume", 1.0, 0.0, 10.0);
        float pitch = (float) getValidatedDouble("countdown.arrival-sound.pitch", 1.0, 0.0, 2.0);

        playSound(player, soundName, volume, pitch);
    }

    /**
     * Validate an integer config value
     *
     * @param path         Config path
     * @param defaultValue Default value to use
     * @param min          Minimum allowed value
     * @param max          Maximum allowed value
     * @return Validated value
     */
    private int getValidatedInt(String path, int defaultValue, int min, int max) {
        int value = plugin.getConfig().getInt(path, defaultValue);
        if (value < min || value > max) {
            plugin.getLogger().warning("Invalid config value for '" + path + "': " + value +
                    ". Must be between " + min + " and " + max + ". Using default: " + defaultValue);
            return defaultValue;
        }
        return value;
    }

    /**
     * Validate a double config value
     *
     * @param path         Config path
     * @param defaultValue Default value to use
     * @param min          Minimum allowed value
     * @param max          Maximum allowed value
     * @return Validated value
     */
    private double getValidatedDouble(String path, double defaultValue, double min, double max) {
        double value = plugin.getConfig().getDouble(path, defaultValue);
        if (value < min || value > max) {
            plugin.getLogger().warning("Invalid config value for '" + path + "': " + value +
                    ". Must be between " + min + " and " + max + ". Using default: " + defaultValue);
            return defaultValue;
        }
        return value;
    }

    /**
     * Cancel countdown for a player
     */
    private void cancelCountdown(Player player) {
        UUID uuid = player.getUniqueId();
        waitingPlayers.remove(uuid);
        transitioningPlayers.remove(uuid);
        postCountdownPlayers.remove(uuid);
        playerOriginalLocations.remove(uuid);

        // Clean up bat if exists
        Bat bat = playerBats.remove(uuid);
        if (bat != null && bat.isValid()) {
            bat.removePassenger(player);
            bat.remove();
        }

        // Remove all effects if they were applied
        player.removePotionEffect(PotionEffectType.DARKNESS);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.removePotionEffect(PotionEffectType.SPEED);

        List<BukkitTask> tasks = playerCountdownTasks.remove(uuid);
        if (tasks != null && !tasks.isEmpty()) {
            int cancelledCount = 0;
            for (BukkitTask task : tasks) {
                if (!task.isCancelled()) {
                    task.cancel();
                    cancelledCount++;
                }
            }

            // Show cancellation message
            String titleText = plugin.getConfig().getString("countdown.cancelled-title", "<red>TELEPORT CANCELLED");
            String subtitleText = plugin.getConfig().getString("countdown.cancelled-subtitle", "<gray>You moved");

            // Get timing from config (reuse title timing, 0-200 ticks reasonable range)
            int fadeIn = getValidatedInt("countdown.title-timing.fade-in", 10, 0, 200);
            int stay = getValidatedInt("countdown.title-timing.stay", 40, 0, 200);
            int fadeOut = getValidatedInt("countdown.title-timing.fade-out", 10, 0, 200);

            // Send cancellation title
            MessageFormatter.sendTitle(player, titleText, subtitleText, fadeIn, stay, fadeOut, "", -1);

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Cancelled " + cancelledCount + " countdown task(s) for " + player.getName());
            }
        }
    }

    /**
     * Clean up orphaned bats (called periodically)
     * Removes bats that are invalid or have no passengers
     */
    public void cleanupOrphanedBats() {
        // Count and remove orphaned bats
        final int[] removed = {0};
        playerBats.entrySet().removeIf(entry -> {
            Bat bat = entry.getValue();
            if (bat == null || !bat.isValid() || bat.getPassengers().isEmpty()) {
                // Remove invalid or riderless bat
                if (bat != null && bat.isValid()) {
                    bat.remove();
                }
                removed[0]++;
                return true; // Remove from map
            }
            return false; // Keep in map
        });

        if (removed[0] > 0 && plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Cleaned up " + removed[0] + " orphaned bat(s)");
        }
    }

    /**
     * Clean up all countdown tasks
     */
    public void cleanup() {
        playerCountdownTasks.values().forEach(tasks ->
                tasks.forEach(task -> {
                    if (!task.isCancelled()) {
                        task.cancel();
                    }
                })
        );

        playerCountdownTasks.clear();
        waitingPlayers.clear();
        transitioningPlayers.clear();
        postCountdownPlayers.clear();
        playerOriginalLocations.clear();

        // Clean up any remaining bats
        playerBats.values().forEach(bat -> {
            if (bat.isValid()) {
                bat.getPassengers().forEach(Entity::eject);
                bat.remove();
            }
        });
        playerBats.clear();
    }
}
