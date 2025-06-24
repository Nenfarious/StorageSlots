package dev.lsdmc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.World;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;

import dev.lsdmc.utils.Constants;

import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Manages safezone detection and validation for storage access.
 * Supports multiple detection methods: world-based, region-based, and PvP-based.
 */
public final class SafezoneManager {
    private final StorageSlots plugin;
    private final StorageConfig config;
    private final MiniMessage miniMessage;

    public SafezoneManager(StorageSlots plugin, StorageConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Checks if a player is in a safezone using the configured detection method.
     *
     * @param player The player to check
     * @return true if the player is in a safezone, false otherwise
     */
    public boolean isInSafezone(Player player) {
        if (!config.isSafezoneEnabled()) {
            return true; // If safezone is disabled, allow everywhere
        }

        if (player == null || !player.isOnline()) {
            return false;
        }
        
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        // Check based on configured detection method
        String detectionMethod = config.getSafezoneDetectionMethod();
        
        return switch (detectionMethod.toLowerCase()) {
            case Constants.DetectionMethods.WORLD -> isInSafezoneWorld(world);
            case Constants.DetectionMethods.REGION -> isInSafezoneRegion(location);
            case Constants.DetectionMethods.PVP -> isInPvPSafezone(location);
            default -> {
                plugin.getLogger().warning("Unknown safezone detection method: " + detectionMethod + 
                    ". Using region detection as fallback.");
                yield isInSafezoneRegion(location);
            }
        };
    }

    /**
     * Checks if a player is in a safezone world.
     *
     * @param world The world to check
     * @return true if the world is a safezone, false otherwise
     */
    private boolean isInSafezoneWorld(World world) {
        return config.getSafezoneWorlds().contains(world.getName());
    }

    /**
     * Checks if a location is in a safezone region.
     *
     * @param location The location to check
     * @return true if the location is in a safezone region, false otherwise
     */
    private boolean isInSafezoneRegion(Location location) {
        try {
            if (!plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
                plugin.getLogger().warning("WorldGuard is not enabled! Cannot check region safezones.");
                return true; // Fail safe - allow access
            }

            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(location));

            String safezoneRegionName = config.getSafezoneRegionName();
            
            // Check if player is in the specified safezone region
            return regions.getRegions().stream()
                .anyMatch(region -> region.getId().equalsIgnoreCase(safezoneRegionName));
                
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking region safezone for player at " + 
                formatLocation(location) + ": " + e.getMessage());
            return true; // Fail safe - allow access if there's an error
        }
    }

    /**
     * Checks if a location is in a PvP safezone.
     *
     * @param location The location to check
     * @return true if the location is in a PvP safezone, false otherwise
     */
    private boolean isInPvPSafezone(Location location) {
        try {
            if (!plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
                plugin.getLogger().warning("WorldGuard is not enabled! Cannot check PvP safezones.");
                return true; // Fail safe - allow access
            }

            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(location));

            int priorityThreshold = config.getSafezonePvPPriority();
            
            // Check if PvP is explicitly denied in any region with priority >= threshold
            // This ensures we only allow storage in regions where PVP is specifically disabled
            boolean hasExplicitPvpDeny = regions.getRegions().stream()
                .anyMatch(region -> {
                    StateFlag.State pvpState = region.getFlag(Flags.PVP);
                    boolean isPvpDenied = pvpState == StateFlag.State.DENY;
                    boolean meetsMinPriority = region.getPriority() >= priorityThreshold;
                    
                    if (config.getMessages().getBoolean("debug.log-safezone-checks", false)) {
                        plugin.getLogger().info(String.format("Region '%s': PVP=%s, Priority=%d, Threshold=%d, Qualifies=%b",
                            region.getId(), pvpState, region.getPriority(), priorityThreshold, 
                            isPvpDenied && meetsMinPriority));
                    }
                    
                    return isPvpDenied && meetsMinPriority;
                });
            
            // If no regions have explicit PVP deny, check the global/world setting
            if (!hasExplicitPvpDeny) {
                // Query the effective PVP state at this location
                StateFlag.State effectivePvpState = query.queryState(BukkitAdapter.adapt(location), null, Flags.PVP);
                
                if (config.getMessages().getBoolean("debug.log-safezone-checks", false)) {
                    plugin.getLogger().info(String.format("No explicit PVP deny regions found. Effective PVP state: %s", effectivePvpState));
                }
                
                // Only allow if PVP is effectively denied at this location
                return effectivePvpState == StateFlag.State.DENY;
            }
            
            return hasExplicitPvpDeny;
                
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking PvP safezone for player at " + 
                formatLocation(location) + ": " + e.getMessage());
            return true; // Fail safe - allow access if there's an error
        }
    }

    /**
     * Checks if a player can use storage based on safezone rules.
     *
     * @param player The player to check
     * @return true if the player can use storage, false otherwise
     */
    public boolean canUseStorage(Player player) {
        if (!isInSafezone(player)) {
            return false;
        }
        
        // Additional checks can be added here if needed
        // For example, checking if player is in combat, etc.
        
        return true;
    }

    /**
     * Sends the safezone message to a player.
     *
     * @param player The player to send the message to
     */
    public void sendSafezoneMessage(Player player) {
        if (config.isSafezoneEnabled()) {
            player.sendMessage(config.getSafezoneMessage());
        }
    }

    /**
     * Formats a location for logging purposes.
     *
     * @param location The location to format
     * @return A formatted string representation of the location
     */
    private String formatLocation(Location location) {
        return String.format("%s: %d, %d, %d", 
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ());
    }

    /**
     * Debug method for admins to check safezone status.
     *
     * @param player The admin player
     * @param target The target player to check
     */
    public void debugSafezoneInfo(Player player, Player target) {
        if (!player.hasPermission(Constants.Permissions.ADMIN)) {
            player.sendMessage(Component.text("You don't have permission to use this command!")
                .color(Constants.Colors.ERROR));
            return;
        }

        Location location = target.getLocation();
        World world = location.getWorld();
        
        Component message = Component.text()
            .append(Component.text("=== Safezone Debug Info for " + target.getName() + " ===\n")
                .color(Constants.Colors.HEADER))
            .append(Component.text("Location: " + formatLocation(location) + "\n")
                .color(Constants.Colors.INFO))
            .append(Component.text("Safezone Enabled: ")
                .color(Constants.Colors.INFO))
            .append(Component.text(config.isSafezoneEnabled() ? "Yes" : "No")
                .color(config.isSafezoneEnabled() ? Constants.Colors.SUCCESS : Constants.Colors.ERROR))
            .append(Component.text("\nDetection Method: ")
                .color(Constants.Colors.INFO))
            .append(Component.text(config.getSafezoneDetectionMethod())
                .color(Constants.Colors.HIGHLIGHT))
            .build();
        
        player.sendMessage(message);
        
        if (config.isSafezoneEnabled()) {
            Component checks = Component.text()
                .append(Component.text("World Check: ")
                    .color(Constants.Colors.INFO))
                .append(Component.text(isInSafezoneWorld(world) ? "Pass" : "Fail")
                    .color(isInSafezoneWorld(world) ? Constants.Colors.SUCCESS : Constants.Colors.ERROR))
                .append(Component.text("\nRegion Check: ")
                    .color(Constants.Colors.INFO))
                .append(Component.text(isInSafezoneRegion(location) ? "Pass" : "Fail")
                    .color(isInSafezoneRegion(location) ? Constants.Colors.SUCCESS : Constants.Colors.ERROR))
                .append(Component.text("\nPvP Check: ")
                    .color(Constants.Colors.INFO))
                .append(Component.text(isInPvPSafezone(location) ? "Pass" : "Fail")
                    .color(isInPvPSafezone(location) ? Constants.Colors.SUCCESS : Constants.Colors.ERROR))
                .append(Component.text("\nOverall Result: ")
                    .color(Constants.Colors.INFO))
                .append(Component.text(isInSafezone(target) ? "IN SAFEZONE" : "NOT IN SAFEZONE")
                    .color(isInSafezone(target) ? Constants.Colors.SUCCESS : Constants.Colors.ERROR))
                .build();
            
            player.sendMessage(checks);
        }
    }
} 