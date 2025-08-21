package dev.lsdmc;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;

import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import dev.lsdmc.utils.Constants;
import net.kyori.adventure.text.BuildableComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class SafezoneManager {
  private final StorageSlots plugin;
  
  private final StorageConfig config;
  

  
  public SafezoneManager(StorageSlots plugin, StorageConfig config) {
    this.plugin = plugin;
    this.config = config;
  }
  
  public boolean isInSafezone(Player player) {
    if (!this.config.isSafezoneEnabled())
      return true; 
    if (player == null || !player.isOnline())
      return false; 
    Location location = player.getLocation();
    World world = location.getWorld();
    if (world == null)
      return false; 
    String detectionMethod = this.config.getSafezoneDetectionMethod();
    String str1;
    switch ((str1 = detectionMethod.toLowerCase()).hashCode()) {
      case -934795532:
        if (!str1.equals("region"))
          break; 
      case 111402:
        if (!str1.equals("pvp"))
          break; 
      case 113318802:
        if (!str1.equals("world"))
          break; 
    } 
    this.plugin.getLogger().warning("Unknown safezone detection method: " + detectionMethod + ". Using region detection as fallback.");
    return isInSafezoneRegion(location);
  }
  
  private boolean isInSafezoneWorld(World world) {
    return this.config.getSafezoneWorlds().contains(world.getName());
  }
  
  private boolean isInSafezoneRegion(Location location) {
    try {
      if (!this.plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
        this.plugin.getLogger().warning("WorldGuard is not enabled! Cannot check region safezones.");
        return true;
      } 
      RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
      RegionQuery query = container.createQuery();
      ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(location));
      String safezoneRegionName = this.config.getSafezoneRegionName();
      return regions.getRegions().stream()
        .anyMatch(region -> region.getId().equalsIgnoreCase(safezoneRegionName));
    } catch (Exception e) {
      this.plugin.getLogger().warning("Error checking region safezone for player at " + 
          formatLocation(location) + ": " + e.getMessage());
      return true;
    } 
  }
  
  private boolean isInPvPSafezone(Location location) {
    try {
      if (!this.plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
        this.plugin.getLogger().warning("WorldGuard is not enabled! Cannot check PvP safezones.");
        return true;
      } 
      RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
      RegionQuery query = container.createQuery();
      ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(location));
      int priorityThreshold = this.config.getSafezonePvPPriority();
      boolean hasExplicitPvpDeny = regions.getRegions().stream()
        .anyMatch(region -> {
            StateFlag.State pvpState = (StateFlag.State)region.getFlag((Flag)Flags.PVP);
            boolean isPvpDenied = (pvpState == StateFlag.State.DENY);
            boolean meetsMinPriority = (region.getPriority() >= priorityThreshold);
            if (this.config.getMessages().getBoolean("debug.log-safezone-checks", false))
              this.plugin.getLogger().info(String.format("Region '%s': PVP=%s, Priority=%d, Threshold=%d, Qualifies=%b", new Object[] { region.getId(), pvpState, Integer.valueOf(region.getPriority()), Integer.valueOf(priorityThreshold), Boolean.valueOf((isPvpDenied && meetsMinPriority)) })); 
            return (isPvpDenied && meetsMinPriority);
          });
      if (!hasExplicitPvpDeny) {
        StateFlag.State effectivePvpState = query.queryState(BukkitAdapter.adapt(location), null, new StateFlag[] { Flags.PVP });
        if (this.config.getMessages().getBoolean("debug.log-safezone-checks", false))
          this.plugin.getLogger().info(String.format("No explicit PVP deny regions found. Effective PVP state: %s", new Object[] { effectivePvpState })); 
        return (effectivePvpState == StateFlag.State.DENY);
      } 
      return hasExplicitPvpDeny;
    } catch (Exception e) {
      this.plugin.getLogger().warning("Error checking PvP safezone for player at " + 
          formatLocation(location) + ": " + e.getMessage());
      return true;
    } 
  }
  
  public boolean canUseStorage(Player player) {
    if (!isInSafezone(player))
      return false; 
    return true;
  }
  
  public void sendSafezoneMessage(Player player) {
    if (this.config.isSafezoneEnabled())
      player.sendMessage(this.config.getSafezoneMessage()); 
  }
  
  private String formatLocation(Location location) {
    return String.format("%s: %d, %d, %d", new Object[] { location.getWorld().getName(), 
          Integer.valueOf(location.getBlockX()), 
          Integer.valueOf(location.getBlockY()), 
          Integer.valueOf(location.getBlockZ()) });
  }
  
  public void debugSafezoneInfo(Player player, Player target) {
    if (!player.hasPermission("storageslots.admin")) {
      player.sendMessage(Component.text("You don't have permission to use this command!")
          .color((TextColor)Constants.Colors.ERROR));
      return;
    } 
    Location location = target.getLocation();
    World world = location.getWorld();
    BuildableComponent buildableComponent = ((TextComponent.Builder)((TextComponent.Builder)((TextComponent.Builder)((TextComponent.Builder)((TextComponent.Builder)((TextComponent.Builder)Component.text()
      .append(Component.text("=== Safezone Debug Info for " + target.getName() + " ===\n")
        .color((TextColor)Constants.Colors.HEADER)))
      .append(Component.text("Location: " + formatLocation(location) + "\n")
        .color((TextColor)Constants.Colors.INFO)))
      .append(Component.text("Safezone Enabled: ")
        .color((TextColor)Constants.Colors.INFO)))
      .append(Component.text(this.config.isSafezoneEnabled() ? "Yes" : "No")
        .color(this.config.isSafezoneEnabled() ? (TextColor)Constants.Colors.SUCCESS : (TextColor)Constants.Colors.ERROR)))
      .append(Component.text("\nDetection Method: ")
        .color((TextColor)Constants.Colors.INFO)))
      .append(Component.text(this.config.getSafezoneDetectionMethod())
        .color((TextColor)Constants.Colors.HIGHLIGHT)))
      .build();
    player.sendMessage((Component)buildableComponent);
    if (this.config.isSafezoneEnabled()) {
      BuildableComponent buildableComponent1 = ((TextComponent.Builder)((TextComponent.Builder)((TextComponent.Builder)((TextComponent.Builder)((TextComponent.Builder)((TextComponent.Builder)((TextComponent.Builder)((TextComponent.Builder)Component.text()
        .append(Component.text("World Check: ")
          .color((TextColor)Constants.Colors.INFO)))
        .append(Component.text(isInSafezoneWorld(world) ? "Pass" : "Fail")
          .color(isInSafezoneWorld(world) ? (TextColor)Constants.Colors.SUCCESS : (TextColor)Constants.Colors.ERROR)))
        .append(Component.text("\nRegion Check: ")
          .color((TextColor)Constants.Colors.INFO)))
        .append(Component.text(isInSafezoneRegion(location) ? "Pass" : "Fail")
          .color(isInSafezoneRegion(location) ? (TextColor)Constants.Colors.SUCCESS : (TextColor)Constants.Colors.ERROR)))
        .append(Component.text("\nPvP Check: ")
          .color((TextColor)Constants.Colors.INFO)))
        .append(Component.text(isInPvPSafezone(location) ? "Pass" : "Fail")
          .color(isInPvPSafezone(location) ? (TextColor)Constants.Colors.SUCCESS : (TextColor)Constants.Colors.ERROR)))
        .append(Component.text("\nOverall Result: ")
          .color((TextColor)Constants.Colors.INFO)))
        .append(Component.text(isInSafezone(target) ? "IN SAFEZONE" : "NOT IN SAFEZONE")
          .color(isInSafezone(target) ? (TextColor)Constants.Colors.SUCCESS : (TextColor)Constants.Colors.ERROR)))
        .build();
      player.sendMessage((Component)buildableComponent1);
    } 
  }
}
