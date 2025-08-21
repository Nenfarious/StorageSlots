package dev.lsdmc;

import java.util.concurrent.CompletableFuture;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StoragePermissionManager {

  
  private final LuckPerms luckPerms;
  
  public StoragePermissionManager(StorageSlots plugin, LuckPerms luckPerms) {
    this.luckPerms = luckPerms;
  }
  
  public boolean hasPermission(CommandSender sender, String permission) {
    if (sender.hasPermission("storageslots.*"))
      return true; 
    return sender.hasPermission(permission);
  }
  
  public boolean hasPermission(Player player, String permission) {
    if (player.hasPermission("storageslots.*"))
      return true; 
    return player.hasPermission(permission);
  }
  
  public CompletableFuture<Boolean> checkRankRequirement(Player player, String requiredRank) {
    if (player.hasPermission("storageslots.bypass.rank"))
      return CompletableFuture.completedFuture(Boolean.valueOf(true)); 
    if (requiredRank == null || requiredRank.isEmpty())
      return CompletableFuture.completedFuture(Boolean.valueOf(true)); 
    User user = this.luckPerms.getUserManager().getUser(player.getUniqueId());
    if (user == null)
      return CompletableFuture.completedFuture(Boolean.valueOf(false)); 
    return CompletableFuture.completedFuture(
        Boolean.valueOf(user.getCachedData().getPermissionData().checkPermission(requiredRank).asBoolean()));
  }
  
  public User getUser(Player player) {
    return this.luckPerms.getUserManager().getUser(player.getUniqueId());
  }
  
  public boolean hasAdminPermission(Player player) {
    return player.hasPermission("storageslots.admin");
  }
  
  public boolean hasUsePermission(Player player) {
    return player.hasPermission("storageslots.use");
  }
  
  public boolean canBypassCost(Player player) {
    return player.hasPermission("storageslots.bypass.cost");
  }
  
  public boolean canBypassRank(Player player) {
    return player.hasPermission("storageslots.bypass.rank");
  }
}
