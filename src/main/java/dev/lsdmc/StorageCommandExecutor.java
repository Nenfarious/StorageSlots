package dev.lsdmc;

import dev.lsdmc.utils.Constants;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class StorageCommandExecutor implements CommandExecutor, TabCompleter {
  private final StorageSlots plugin;
  
  private final StorageManager storageManager;
  
  private final StorageConfig config;
  
  private final StoragePermissionManager permissionManager;
  
  public StorageCommandExecutor(StorageSlots plugin, StorageManager storageManager, StorageConfig config, StoragePermissionManager permissionManager) {
    this.plugin = plugin;
    this.storageManager = storageManager;
    this.config = config;
    this.permissionManager = permissionManager;
  }
  
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    try {
      return handleCommand(sender, command, label, args);
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error executing command '" + command.getName() + "': " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      sender.sendMessage(this.config.getMessage("errors.command-execution-failed", 
            Map.of("error", e.getMessage())));
      return true;
    } 
  }
  
  private boolean handleCommand(CommandSender sender, Command command, String label, String[] args) {
    Player player;
    
    // Debug logging
    this.plugin.getLogger().info("Command received: '" + command.getName() + "' with label: '" + label + "'");
    
    if (command.getName().equalsIgnoreCase("storagereload"))
      return handleReloadCommand(sender); 
    if (sender instanceof Player) {
      player = (Player)sender;
    } else {
      sender.sendMessage(Component.text("This command can only be used by players!")
          .color((TextColor)Constants.Colors.ERROR));
      return true;
    } 
    
    String commandName = command.getName().toLowerCase();
    
    // Simple if-else structure for better reliability
    if (commandName.equals("storage")) {
      return handleStorageCommand(player);
    } else if (commandName.equals("buystorage")) {
      return handleBuyStorageCommand(player, args);
    } else if (commandName.equals("storagecost")) {
      return handleStorageCostCommand(player, args);
    } else if (commandName.equals("storagedelete")) {
      return handleStorageDeleteCommand(player, args);
    } else if (commandName.equals("storageadmin")) {
      return handleStorageAdminCommand(player, args);
    } else if (commandName.equals("viewstorage")) {
      return handleViewStorageCommand(player, args);
    } else if (commandName.equals("removeslot")) {
      return handleRemoveSlotCommand(player, args);
    } else if (commandName.equals("giveslot")) {
      return handleGiveSlotCommand(player, args);
    } else if (commandName.equals("listslots")) {
      return handleListSlotsCommand(player, args);
    } else if (commandName.equals("togglecooldown")) {
      return handleToggleCooldownCommand(player, args);
    } else if (commandName.equals("testeconomy")) {
      return handleTestEconomyCommand(player);
    } else if (commandName.equals("testfallback")) {
      return handleTestFallbackCommand(player);
    } else if (commandName.equals("resetapi")) {
      return handleResetApiCommand(player);
    } else if (commandName.equals("testnotifications")) {
      return handleTestNotificationsCommand(player);
    } else if (commandName.equals("debugsafezone")) {
      return handleDebugSafezoneCommand(player, args);
    }
    
    this.plugin.getLogger().warning("Unknown command: '" + command.getName() + "' (lowercase: '" + commandName + "')");
    player.sendMessage(this.config.getMessage("errors.unknown-command"));
    return false;
  }
  
  private boolean handleStorageCommand(Player player) {
    if (!this.permissionManager.hasPermission(player, "storageslots.use")) {
      player.sendMessage(this.config.getMessage("no-permission"));
      return true;
    } 
    if (!this.plugin.getSafezoneManager().isInSafezone(player)) {
      player.sendMessage(this.config.getMessage("safezone-required"));
      return true;
    } 
    this.storageManager.openStorage(player);
    return true;
  }
  
  private boolean handleBuyStorageCommand(Player player, String[] args) {
    if (!this.permissionManager.hasPermission(player, "storageslots.use")) {
      player.sendMessage(this.config.getMessage("no-permission"));
      return true;
    } 
    if (args.length != 1) {
      player.sendMessage(this.config.getMessage("usage-buystorage"));
      return true;
    } 
    try {
      int slot = Integer.parseInt(args[0]) - 1;
      if (!this.config.isValidSlot(slot)) {
        player.sendMessage(this.config.getMessage("invalid-slot"));
        return true;
      } 
      this.storageManager.purchaseSlot(player, slot);
    } catch (NumberFormatException e) {
      player.sendMessage(this.config.getMessage("invalid-number"));
    } 
    return true;
  }
  
  private boolean handleStorageCostCommand(Player player, String[] args) {
    if (!this.permissionManager.hasPermission(player, "storageslots.admin")) {
      player.sendMessage(this.config.getMessage("no-permission"));
      return true;
    } 
    if (args.length != 2) {
      player.sendMessage(this.config.getMessage("usage-storagecost"));
      return true;
    } 
    try {
      int slot = Integer.parseInt(args[0]) - 1;
      double cost = Double.parseDouble(args[1]);
      if (!this.config.isValidSlot(slot)) {
        player.sendMessage(this.config.getMessage("invalid-slot"));
        return true;
      } 
      if (cost < 0.0D) {
        player.sendMessage(Component.text("Cost cannot be negative!")
            .color((TextColor)Constants.Colors.ERROR));
        return true;
      } 
      this.config.setSlotCost(slot, cost);
             player.sendMessage(this.config.getMessage("cost-set", Map.of(
               "slot", String.valueOf(slot + 1), 
               "cost", this.plugin.getEconomyManager().formatCurrency(cost, "slot-purchase"), 
               "currency", this.plugin.getEconomyManager().getCurrencyName("slot-purchase"))));
    } catch (NumberFormatException e) {
      player.sendMessage(this.config.getMessage("invalid-number"));
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error setting slot cost: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      player.sendMessage(this.config.getMessage("errors.config-save-failed"));
    } 
    return true;
  }
  
  private boolean handleReloadCommand(CommandSender sender) {
    this.plugin.getLogger().info("Reload command executed by: " + sender.getName());
    
    if (!this.permissionManager.hasPermission(sender, "storageslots.admin")) {
      this.plugin.getLogger().warning("Player " + sender.getName() + " tried to use reload command without permission");
      sender.sendMessage(this.config.getMessage("no-permission"));
      return true;
    } 
    
    this.plugin.getLogger().info("Starting configuration reload...");
    try {
      this.config.reload();
      this.plugin.getLogger().info("Configuration reload completed successfully");
      sender.sendMessage(this.config.getMessage("config-reloaded"));
    } catch (Exception e) {
      this.plugin.getLogger().severe("Error during configuration reload: " + e.getMessage());
      e.printStackTrace();
      this.plugin.getComponentLogger().error(Component.text("Error reloading configuration: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      sender.sendMessage(this.config.getMessage("errors.config-load-failed", 
            Map.of("error", e.getMessage())));
    } 
    return true;
  }
  
  private boolean handleStorageDeleteCommand(Player player, String[] args) {
    if (!this.permissionManager.hasPermission(player, "storageslots.admin")) {
      player.sendMessage(this.config.getMessage("no-permission"));
      return true;
    } 
    if (args.length != 1) {
      player.sendMessage(this.config.getMessage("usage-storagedelete"));
      return true;
    } 
    try {
      if (args[0].equalsIgnoreCase("all")) {
        this.storageManager.resetAllStorage();
        player.sendMessage(this.config.getMessage("storage-reset"));
      } else {
        UUID targetId = this.storageManager.findPlayerUUID(args[0]);
        if (targetId == null) {
          player.sendMessage(this.config.getMessage("player-not-found"));
          return true;
        } 
        this.storageManager.resetPlayerStorage(targetId);
        player.sendMessage(this.config.getMessage("player-storage-reset", 
              Map.of("player", args[0])));
      } 
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error deleting storage: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      player.sendMessage(this.config.getMessage("errors.data-operation-failed"));
    } 
    return true;
  }
  
  private boolean handleStorageAdminCommand(Player player, String[] args) {
    if (!this.permissionManager.hasPermission(player, "storageslots.admin")) {
      player.sendMessage(this.config.getMessage("no-permission"));
      return true;
    } 
    if (args.length != 1) {
      player.sendMessage(this.config.getMessage("usage-storageadmin"));
      return true;
    } 
    if (!this.plugin.getSafezoneManager().isInSafezone(player)) {
      player.sendMessage(this.config.getMessage("safezone-required"));
      return true;
    } 
    try {
      UUID targetId = this.storageManager.findPlayerUUID(args[0]);
      if (targetId == null) {
        player.sendMessage(this.config.getMessage("player-not-found"));
        return true;
      } 
      this.storageManager.openPlayerStorage(player, targetId);
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error opening admin storage: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      player.sendMessage(this.config.getMessage("errors.inventory-creation-failed"));
    } 
    return true;
  }
  
  private boolean handleViewStorageCommand(Player player, String[] args) {
    if (!this.permissionManager.hasPermission(player, "storageslots.admin")) {
      player.sendMessage(this.config.getMessage("no-permission"));
      return true;
    } 
    if (args.length != 1) {
      player.sendMessage(this.config.getMessage("usage-viewstorage"));
      return true;
    } 
    if (!this.plugin.getSafezoneManager().isInSafezone(player)) {
      player.sendMessage(this.config.getMessage("safezone-required"));
      return true;
    } 
    try {
      UUID targetId = this.storageManager.findPlayerUUID(args[0]);
      if (targetId == null) {
        player.sendMessage(this.config.getMessage("player-not-found"));
        return true;
      } 
      this.storageManager.openPlayerStorage(player, targetId);
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error viewing storage: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      player.sendMessage(this.config.getMessage("errors.inventory-creation-failed"));
    } 
    return true;
  }
  
  private boolean handleRemoveSlotCommand(Player player, String[] args) {
    if (!this.permissionManager.hasPermission(player, "storageslots.admin")) {
      player.sendMessage(this.config.getMessage("no-permission"));
      return true;
    } 
    if (args.length != 2) {
      player.sendMessage(this.config.getMessage("usage-removeslot"));
      return true;
    } 
    try {
      UUID targetId = this.storageManager.findPlayerUUID(args[0]);
      if (targetId == null) {
        player.sendMessage(this.config.getMessage("player-not-found"));
        return true;
      } 
      int slot = Integer.parseInt(args[1]) - 1;
      if (!this.config.isValidSlot(slot)) {
        player.sendMessage(this.config.getMessage("invalid-slot"));
        return true;
      } 
      this.storageManager.removeSlot(player, args[0], slot);
    } catch (NumberFormatException e) {
      player.sendMessage(this.config.getMessage("invalid-number"));
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error removing slot: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      player.sendMessage(this.config.getMessage("errors.data-operation-failed"));
    } 
    return true;
  }
  
  private boolean handleGiveSlotCommand(Player player, String[] args) {
    if (!this.permissionManager.hasPermission(player, "storageslots.admin")) {
      player.sendMessage(this.config.getMessage("no-permission"));
      return true;
    } 
    if (args.length != 2) {
      player.sendMessage(this.config.getMessage("usage-giveslot"));
      return true;
    } 
    try {
      UUID targetId = this.storageManager.findPlayerUUID(args[0]);
      if (targetId == null) {
        player.sendMessage(this.config.getMessage("player-not-found"));
        return true;
      } 
      int slot = Integer.parseInt(args[1]) - 1;
      if (!this.config.isValidSlot(slot)) {
        player.sendMessage(this.config.getMessage("invalid-slot"));
        return true;
      } 
      this.storageManager.giveSlot(player, args[0], slot);
    } catch (NumberFormatException e) {
      player.sendMessage(this.config.getMessage("invalid-number"));
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error giving slot: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      player.sendMessage(this.config.getMessage("errors.data-operation-failed"));
    } 
    return true;
  }
  
  private boolean handleListSlotsCommand(Player player, String[] args) {
    if (!this.permissionManager.hasPermission(player, "storageslots.admin")) {
      player.sendMessage(this.config.getMessage("no-permission"));
      return true;
    } 
    if (args.length != 1) {
      player.sendMessage(this.config.getMessage("usage-listslots"));
      return true;
    } 
    try {
      UUID targetId = this.storageManager.findPlayerUUID(args[0]);
      if (targetId == null) {
        player.sendMessage(this.config.getMessage("player-not-found"));
        return true;
      } 
      this.storageManager.listSlots(player, args[0]);
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error listing slots: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      player.sendMessage(this.config.getMessage("errors.data-operation-failed"));
    } 
    return true;
  }
  
  private boolean handleToggleCooldownCommand(Player player, String[] args) {
    if (!this.permissionManager.hasPermission(player, "storageslots.admin")) {
      player.sendMessage(this.config.getMessage("no-permission"));
      return true;
    } 
    Player target = player;
    if (args.length == 1) {
      target = Bukkit.getPlayer(args[0]);
      if (target == null) {
        player.sendMessage(this.config.getMessage("player-not-found"));
        return true;
      } 
    } else if (args.length > 1) {
      player.sendMessage(Component.text("Usage: /togglecooldown [player]")
          .color((TextColor)Constants.Colors.ERROR));
      return true;
    } 
    try {
      boolean currentBypass = target.hasPermission("storageslots.bypass.cooldown");
      if (currentBypass) {
        target.removeAttachment(target.addAttachment((Plugin)this.plugin, "storageslots.bypass.cooldown", false));
        player.sendMessage(Component.text("Removed withdrawal cooldown bypass from " + target.getName())
            .color((TextColor)Constants.Colors.SUCCESS));
        if (!target.equals(player))
          target.sendMessage(Component.text("Your withdrawal cooldown bypass has been removed by " + player.getName())
              .color((TextColor)Constants.Colors.INFO)); 
      } else {
        target.addAttachment((Plugin)this.plugin, "storageslots.bypass.cooldown", true);
        player.sendMessage(Component.text("Granted withdrawal cooldown bypass to " + target.getName())
            .color((TextColor)Constants.Colors.SUCCESS));
        if (!target.equals(player))
          target.sendMessage(Component.text("You have been granted withdrawal cooldown bypass by " + player.getName())
              .color((TextColor)Constants.Colors.SUCCESS)); 
      } 
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error toggling cooldown bypass: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      player.sendMessage(Component.text("Failed to toggle cooldown bypass!")
          .color((TextColor)Constants.Colors.ERROR));
    } 
    return true;
  }
  
  private boolean handleTestEconomyCommand(Player player) {
    if (!this.permissionManager.hasPermission(player, "storageslots.admin")) {
      player.sendMessage(this.config.getMessage("no-permission"));
      return true;
    } 
    try {
      testEconomyIntegration(player);
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error testing economy integration: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      player.sendMessage(Component.text("Failed to test economy integration!")
          .color((TextColor)Constants.Colors.ERROR));
    } 
    return true;
  }
  
  private void testEconomyIntegration(Player player) {
    try {
      boolean integrationTest = this.plugin.getEconomyManager().testPlayerPointsIntegration();
      
      // Test all three currency types
      String slotPurchaseCurrency = this.plugin.getConfigManager().getSlotPurchaseCurrency();
      String withdrawalFeeCurrency = this.plugin.getConfigManager().getWithdrawalFeeCurrency();
      String donorSlotCurrency = this.plugin.getConfigManager().getDonorSlotCurrency();
      
      boolean useVaultForSlots = this.plugin.getConfigManager().useVaultForSlotPurchase();
      boolean useVaultForFees = this.plugin.getConfigManager().useVaultForWithdrawalFees();
      boolean useVaultForDonor = this.plugin.getConfigManager().useVaultForDonorSlots();
      
      player.sendMessage(Component.text("Economy Integration Test Results:").color((TextColor)Constants.Colors.HEADER));
      player.sendMessage(Component.text("Integration Test: " + (integrationTest ? "PASSED" : "FAILED")).color(integrationTest ? (TextColor)Constants.Colors.SUCCESS : (TextColor)Constants.Colors.ERROR));
      
      // Test slot purchase currency
      this.plugin.getEconomyManager().getBalance(player, "slot-purchase").thenAccept(balance -> {
        String economyType = useVaultForSlots ? "Vault" : "PlayerPoints";
        player.sendMessage(Component.text("Slot Purchase: " + this.plugin.getEconomyManager().formatCurrency(balance.doubleValue(), "slot-purchase") + " " + slotPurchaseCurrency + " (" + economyType + ")").color((TextColor)Constants.Colors.INFO));
        
        // Test withdrawal fee currency
        this.plugin.getEconomyManager().getBalance(player, "withdrawal-fees").thenAccept(feeBalance -> {
          String feeEconomyType = useVaultForFees ? "Vault" : "PlayerPoints";
          player.sendMessage(Component.text("Withdrawal Fees: " + this.plugin.getEconomyManager().formatCurrency(feeBalance.doubleValue(), "withdrawal-fees") + " " + withdrawalFeeCurrency + " (" + feeEconomyType + ")").color((TextColor)Constants.Colors.INFO));
          
          // Test donor slot currency
          this.plugin.getEconomyManager().getBalance(player, "donor-slots").thenAccept(donorBalance -> {
            String donorEconomyType = useVaultForDonor ? "Vault" : "PlayerPoints";
            player.sendMessage(Component.text("Donor Slots: " + this.plugin.getEconomyManager().formatCurrency(donorBalance.doubleValue(), "donor-slots") + " " + donorSlotCurrency + " (" + donorEconomyType + ")").color((TextColor)Constants.Colors.INFO));
            
            if (integrationTest) {
              player.sendMessage(Component.text("All economy integrations are working correctly!").color((TextColor)Constants.Colors.SUCCESS));
            } else {
              player.sendMessage(Component.text("Some economy integrations have issues. Check console for details.").color((TextColor)Constants.Colors.ERROR));
            }
          });
        });
      });
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error testing economy integration: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      player.sendMessage(Component.text("Failed to test economy integration!")
          .color((TextColor)Constants.Colors.ERROR));
    } 
  }
  
  private boolean handleTestFallbackCommand(Player player) {
    if (!this.permissionManager.hasPermission(player, "storageslots.admin")) {
      player.sendMessage(this.config.getMessage("no-permission"));
      return true;
    } 
    try {
      player.sendMessage(Component.text("Testing PlayerPoints Fallback Mechanism:")
          .color((TextColor)Constants.Colors.HEADER));
      boolean apiTest = this.plugin.getEconomyManager().testPlayerPointsIntegration();
      player.sendMessage(Component.text("API Test: " + (apiTest ? "PASSED" : "FAILED"))
          .color(apiTest ? (TextColor)Constants.Colors.SUCCESS : (TextColor)Constants.Colors.ERROR));
      this.plugin.getEconomyManager().getBalance(player).thenAccept(balance -> {
            player.sendMessage(Component.text("Balance Retrieval: " + this.plugin.getEconomyManager().formatCurrency(balance.doubleValue()) + " points").color((TextColor)Constants.Colors.INFO));
            this.plugin.getEconomyManager().takeMoney(player, 1, "slot-purchase").thenAccept(success -> {
              player.sendMessage(Component.text("Point Deduction Test: " + (success ? "PASSED" : "FAILED"))
                .color(success ? (TextColor)Constants.Colors.SUCCESS : (TextColor)Constants.Colors.ERROR));
            });
          });
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error testing fallback mechanism: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      player.sendMessage(Component.text("Failed to test fallback mechanism!")
          .color((TextColor)Constants.Colors.ERROR));
    } 
    return true;
  }
  
  private boolean handleResetApiCommand(Player player) {
    if (!this.permissionManager.hasPermission(player, "storageslots.admin")) {
      player.sendMessage(this.config.getMessage("no-permission"));
      return true;
    } 
    try {
      this.plugin.getEconomyManager().resetApiStatus();
      player.sendMessage(Component.text("PlayerPoints API status has been reset to working.")
          .color((TextColor)Constants.Colors.SUCCESS));
      player.sendMessage(Component.text("The system will now try the API first before using command fallback.")
          .color((TextColor)Constants.Colors.INFO));
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error resetting API status: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      player.sendMessage(Component.text("Failed to reset API status!")
          .color((TextColor)Constants.Colors.ERROR));
    } 
    return true;
  }
  
  private boolean handleTestNotificationsCommand(Player player) {
    if (!this.permissionManager.hasPermission(player, "storageslots.admin")) {
      player.sendMessage(this.config.getMessage("no-permission"));
      return true;
    } 
    try {
      player.sendMessage(Component.text("=== Testing Notification System ===")
          .color((TextColor)Constants.Colors.HEADER));
      PlayerStorageData data = this.storageManager.getDataManager().getPlayerData(player.getUniqueId());
      player.sendMessage(Component.text("Last notified rank: " + ((data.getLastNotifiedRank() != null) ? data.getLastNotifiedRank() : "None"))
          .color((TextColor)Constants.Colors.INFO));
      player.sendMessage(Component.text("Has seen storage: " + data.hasSeenNewSlotNotification())
          .color((TextColor)Constants.Colors.INFO));
      player.sendMessage(Component.text("Last reminder time: " + String.valueOf(new Date(data.getLastReminderTime())))
          .color((TextColor)Constants.Colors.INFO));
      int nextSlot = -1;
      for (int i = 0; i < this.config.getStorageSlots(); i++) {
        if (!data.hasSlotUnlocked(i))
          if (!this.config.isProgressionRequired() || i <= 0 || data.hasSlotUnlocked(i - 1)) {
            nextSlot = i;
            break;
          }  
      } 
      if (nextSlot == -1) {
        player.sendMessage(Component.text("No unlockable slots found - all slots already unlocked!")
            .color((TextColor)Constants.Colors.SUCCESS));
      } else {
        String requiredRank = this.config.getRequiredRank(nextSlot);
        boolean hasRank = this.config.hasRankRequirement(player, requiredRank);
        player.sendMessage(Component.text("Next unlockable slot: " + nextSlot + 1)
            .color((TextColor)Constants.Colors.INFO));
        player.sendMessage(Component.text("Required rank: " + ((requiredRank != null) ? requiredRank : "None"))
            .color((TextColor)Constants.Colors.INFO));
        player.sendMessage(Component.text("Has required rank: " + hasRank)
            .color(hasRank ? (TextColor)Constants.Colors.SUCCESS : (TextColor)Constants.Colors.ERROR));
        if (hasRank) {
          player.sendMessage(Component.text("Manually triggering notification check...")
              .color((TextColor)Constants.Colors.HIGHLIGHT));
          this.plugin.checkPlayerForNewSlots(player);
          player.sendMessage(Component.text("Notification check completed!")
              .color((TextColor)Constants.Colors.SUCCESS));
        } else {
          player.sendMessage(Component.text("Cannot send notification - player doesn't have required rank")
              .color((TextColor)Constants.Colors.ERROR));
        } 
      } 
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error testing notifications: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      player.sendMessage(Component.text("Failed to test notifications: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
    } 
    return true;
  }
  
  private boolean handleDebugSafezoneCommand(Player player, String[] args) {
    if (!this.permissionManager.hasPermission(player, "storageslots.admin")) {
      player.sendMessage(this.config.getMessage("no-permission"));
      return true;
    } 
    try {
      this.plugin.getSafezoneManager().debugSafezoneInfo(player, player);
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error checking safezone: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      player.sendMessage(this.config.getMessage("errors.data-operation-failed"));
    } 
    return true;
  }
  
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    if (!this.permissionManager.hasPermission(sender, "storageslots.use")) {
      return new ArrayList<>();
    }
    
    String commandName = command.getName().toLowerCase();
    List<String> completions = new ArrayList<>();
    
    switch (commandName) {
      case "storagedelete":
      case "viewstorage":
      case "giveslot":
      case "removeslot":
      case "listslots":
        if (args.length == 1) {
          return getPlayerNames(args[0]);
        }
        break;
      case "storage":
      case "buystorage":
      case "storagecost":
      case "storagereload":
      case "storageadmin":
      case "togglecooldown":
      case "testeconomy":
      case "testfallback":
      case "resetapi":
      case "debugsafezone":
        // These commands don't need tab completion
        break;
    }
    
    return completions;
  }

  private List<String> getPlayerNames(String prefix) {
    try {
      return (List<String>)Bukkit.getOnlinePlayers().stream()
        .map(Player::getName)
        .filter(name -> name.toLowerCase().startsWith(prefix.toLowerCase()))
        .sorted()
        .limit(10L)
        .collect(Collectors.toList());
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error getting player names: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      return new ArrayList<>();
    } 
  }
  
  private List<String> getSlotNumbers(String partial) {
    try {
      List<String> slots = new ArrayList<>();
      int maxSlots = Math.min(this.config.getStorageSlots(), 54);
      for (int i = 1; i <= maxSlots; i++) {
        String slot = String.valueOf(i);
        if (slot.startsWith(partial))
          slots.add(slot); 
      } 
      return slots;
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error getting slot numbers: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      return new ArrayList<>();
    } 
  }
}
