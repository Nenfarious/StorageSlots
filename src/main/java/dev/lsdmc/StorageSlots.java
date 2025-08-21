package dev.lsdmc;

import dev.lsdmc.utils.Constants;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class StorageSlots extends JavaPlugin {
  private StorageManager storageManager;
  
  private StorageConfig configManager;
  
  private StorageEconomyManager economyManager;
  
  private StoragePermissionManager permissionManager;
  
  private StorageCommandExecutor commandExecutor;
  
  private SafezoneManager safezoneManager;
  
  private StorageInventoryManager inventoryManager;
  
  private LuckPerms luckPerms;
  
  private static StorageSlots instance;
  
  public void onEnable() {
    instance = this;
    getComponentLogger().info(Component.text("Starting StorageSlots...")
        .color((TextColor)Constants.Colors.INFO));
    try {
      Constants.Keys.initialize((Plugin)this);
      initializeConfiguration();
      if (!setupDependencies())
        return; 
      initializeManagers();
      registerEvents();
      registerCommands();
      verifyComponents();
      scheduleAutoSave();
      scheduleNotificationSystem();
      getComponentLogger().info(Component.text("StorageSlots enabled successfully!")
          .color((TextColor)Constants.Colors.SUCCESS));
    } catch (Exception e) {
      getComponentLogger().error(Component.text("Failed to enable StorageSlots: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      getServer().getPluginManager().disablePlugin((Plugin)this);
    } 
  }
  
  private void initializeConfiguration() {
    try {
      saveDefaultConfig();
      this.configManager = new StorageConfig(this);
      getComponentLogger().info(Component.text("Configuration initialized successfully!")
          .color((TextColor)Constants.Colors.SUCCESS));
    } catch (Exception e) {
      getComponentLogger().error(Component.text("Failed to initialize configuration: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      throw new RuntimeException("Configuration initialization failed", e);
    } 
  }
  
  private boolean setupDependencies() {
    try {
      boolean luckPermsOk = setupLuckPerms();
      boolean worldGuardOk = setupWorldGuard();
      boolean playerPointsOk = setupPlayerPoints();
      
      if (!luckPermsOk || !worldGuardOk || !playerPointsOk) {
        getComponentLogger().warn(Component.text("Some dependencies failed to load, but plugin will continue with limited functionality")
            .color((TextColor)Constants.Colors.ERROR));
        return true; // Continue loading the plugin anyway
      }
      
      return true;
    } catch (Exception e) {
      getComponentLogger().error(Component.text("Failed to setup dependencies: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      getComponentLogger().warn(Component.text("Plugin will continue with limited functionality")
          .color((TextColor)Constants.Colors.ERROR));
      return true; // Continue loading the plugin anyway
    } 
  }
  
  private boolean setupLuckPerms() {
    try {
      if (!getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
        getComponentLogger().warn(Component.text("LuckPerms is not enabled! Permission system will be limited.")
            .color((TextColor)Constants.Colors.ERROR));
        return false;
      } 
      this.luckPerms = LuckPermsProvider.get();
      this.permissionManager = new StoragePermissionManager(this, this.luckPerms);
      getComponentLogger().info(Component.text("Successfully hooked into LuckPerms!")
          .color((TextColor)Constants.Colors.SUCCESS));
      return true;
    } catch (Exception e) {
      getComponentLogger().warn(Component.text("Failed to hook into LuckPerms: " + e.getMessage() + " - Permission system will be limited.")
          .color((TextColor)Constants.Colors.ERROR));
      return false;
    } 
  }
  
  private boolean setupWorldGuard() {
    try {
      if (!getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
        getComponentLogger().warn(Component.text("WorldGuard is not enabled! Safezone system will be limited.")
            .color((TextColor)Constants.Colors.ERROR));
        return false;
      } 
      getComponentLogger().info(Component.text("Successfully detected WorldGuard!")
          .color((TextColor)Constants.Colors.SUCCESS));
      return true;
    } catch (Exception e) {
      getComponentLogger().warn(Component.text("Failed to setup WorldGuard: " + e.getMessage() + " - Safezone system will be limited.")
          .color((TextColor)Constants.Colors.ERROR));
      return false;
    } 
  }
  
  private boolean setupPlayerPoints() {
    try {
      // Check if any operation uses Vault
      boolean useVaultForAny = this.configManager.useVaultForSlotPurchase() || 
                               this.configManager.useVaultForWithdrawalFees() || 
                               this.configManager.useVaultForDonorSlots();
      
      if (useVaultForAny) {
        getComponentLogger().info(Component.text("Vault economy is enabled for some operations - PlayerPoints will be used as fallback")
            .color((TextColor)Constants.Colors.INFO));
        // Create economy manager with null PlayerPoints API - it will use Vault where configured
        this.economyManager = new StorageEconomyManager(this, this.configManager, null);
        return true;
      }
      
      // Use PlayerPoints as primary economy
      if (!getServer().getPluginManager().isPluginEnabled("PlayerPoints")) {
        getComponentLogger().warn(Component.text("PlayerPoints is not enabled! Economy system will be limited.")
            .color((TextColor)Constants.Colors.ERROR));
        return false;
      } 
      PlayerPoints playerPoints = PlayerPoints.getInstance();
      if (playerPoints == null) {
        getComponentLogger().warn(Component.text("PlayerPoints instance is null! Economy system will be limited.")
            .color((TextColor)Constants.Colors.ERROR));
        return false;
      } 
      PlayerPointsAPI pointsAPI = playerPoints.getAPI();
      if (pointsAPI == null) {
        getComponentLogger().warn(Component.text("PlayerPoints API is null! Economy system will be limited.")
            .color((TextColor)Constants.Colors.ERROR));
        return false;
      } 
      try {
        pointsAPI.look(UUID.randomUUID());
        getComponentLogger().info(Component.text("PlayerPoints API test successful!")
            .color((TextColor)Constants.Colors.SUCCESS));
      } catch (Exception e) {
        getComponentLogger().warn(Component.text("PlayerPoints API test failed: " + e.getMessage() + " - Economy system will be limited.")
            .color((TextColor)Constants.Colors.ERROR));
        return false;
      } 
      this.economyManager = new StorageEconomyManager(this, this.configManager, pointsAPI);
      getComponentLogger().info(Component.text("Successfully hooked into PlayerPoints!")
          .color((TextColor)Constants.Colors.SUCCESS));
      return true;
    } catch (Exception e) {
      getComponentLogger().warn(Component.text("Failed to hook into PlayerPoints: " + e.getMessage() + " - Economy system will be limited.")
          .color((TextColor)Constants.Colors.ERROR));
      return false;
    } 
  }
  
  private void initializeManagers() {
    try {
      this.storageManager = new StorageManager(this, this.configManager);
      this.safezoneManager = new SafezoneManager(this, this.configManager);
      this.inventoryManager = this.storageManager.getInventoryManager();
      
      // Create fallback economy manager if no economy system is available
      if (this.economyManager == null) {
        getComponentLogger().warn(Component.text("Creating fallback economy manager - no economy system available")
            .color((TextColor)Constants.Colors.ERROR));
        // We'll create a dummy economy manager that always fails transactions
        this.economyManager = new StorageEconomyManager(this, this.configManager, null) {
          @Override
          public CompletableFuture<Boolean> takeMoney(Player player, double amount) {
            getLogger().warning("Economy transaction attempted but no economy system is available!");
            return CompletableFuture.completedFuture(false);
          }
          
          @Override
          public CompletableFuture<Double> getBalance(Player player) {
            return CompletableFuture.completedFuture(0.0);
          }
        };
      }
      
      this.commandExecutor = new StorageCommandExecutor(this, this.storageManager, this.configManager, this.permissionManager);
      getComponentLogger().info(Component.text("All managers initialized successfully!")
          .color((TextColor)Constants.Colors.SUCCESS));
    } catch (Exception e) {
      getComponentLogger().error(Component.text("Failed to initialize managers: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      throw new RuntimeException("Manager initialization failed", e);
    } 
  }
  
  private void registerEvents() {
    try {
      getServer().getPluginManager().registerEvents(this.storageManager, (Plugin)this);
      getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent event) {
              StorageSlots.this.getServer().getScheduler().runTaskLater((Plugin)StorageSlots.this, () -> {
                    try {
                      StorageSlots.this.checkPlayerForNewSlots(event.getPlayer());
                    } catch (Exception e) {
                      StorageSlots.this.getComponentLogger().error(Component.text("Error checking notifications for " + event.getPlayer().getName() + " on join: " + e.getMessage()).color((TextColor)Constants.Colors.ERROR));
                    } 
                  }, 100L);
            }
          }, (Plugin)this);
      getComponentLogger().info(Component.text("Event listeners registered!")
          .color((TextColor)Constants.Colors.SUCCESS));
    } catch (Exception e) {
      getComponentLogger().error(Component.text("Failed to register events: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      throw new RuntimeException("Event registration failed", e);
    } 
  }
  
  private void registerCommands() {
    try {
      registerCommand("storage", this.commandExecutor);
      registerCommand("buystorage", this.commandExecutor);
      registerCommand("storagecost", this.commandExecutor);
      registerCommand("storagereload", this.commandExecutor);
      registerCommand("storagedelete", this.commandExecutor);
      registerCommand("storageadmin", this.commandExecutor);
      registerCommand("viewstorage", this.commandExecutor);
      registerCommand("removeslot", this.commandExecutor);
      registerCommand("giveslot", this.commandExecutor);
      registerCommand("listslots", this.commandExecutor);
      registerCommand("togglecooldown", this.commandExecutor);
      registerCommand("testeconomy", this.commandExecutor);
      registerCommand("testfallback", this.commandExecutor);
      registerCommand("resetapi", this.commandExecutor);
      registerCommand("testnotifications", this.commandExecutor);
      registerCommand("debugsafezone", this.commandExecutor);
      getComponentLogger().info(Component.text("Commands registered successfully!")
          .color((TextColor)Constants.Colors.SUCCESS));
    } catch (Exception e) {
      getComponentLogger().error(Component.text("Failed to register commands: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      throw new RuntimeException("Command registration failed", e);
    } 
  }
  
  private void registerCommand(String name, CommandExecutor executor) {
    PluginCommand command = getCommand(name);
    if (command != null) {
      command.setExecutor(executor);
      if (executor instanceof TabCompleter)
        command.setTabCompleter((TabCompleter)executor); 
    } else {
      getComponentLogger().warn(Component.text("Command '" + name + "' not found in plugin.yml!")
          .color((TextColor)Constants.Colors.ERROR));
    } 
  }
  
  private void verifyComponents() {
    List<String> failures = new ArrayList<>();
    if (this.configManager == null)
      failures.add("Configuration manager"); 
    if (this.economyManager == null)
      failures.add("Economy manager"); 
    if (this.permissionManager == null)
      failures.add("Permission manager"); 
    if (this.safezoneManager == null)
      failures.add("Safezone manager"); 
    if (this.storageManager == null)
      failures.add("Storage manager"); 
    if (this.inventoryManager == null)
      failures.add("Inventory manager"); 
    if (this.commandExecutor == null)
      failures.add("Command executor"); 
    if (!failures.isEmpty()) {
      getComponentLogger().error(Component.text("Failed to initialize: " + String.join(", ", (Iterable)failures))
          .color((TextColor)Constants.Colors.ERROR));
      throw new RuntimeException("Component verification failed");
    } 
    getComponentLogger().info(Component.text("All components verified successfully!")
        .color((TextColor)Constants.Colors.SUCCESS));
  }
  
  private void scheduleAutoSave() {
    if (!this.configManager.isAutoSaveEnabled())
      return; 
    try {
      long intervalTicks = this.configManager.getAutoSaveInterval() * 20L;
      getServer().getScheduler().runTaskTimer((Plugin)this, () -> {
            try {
              if (this.storageManager != null)
                this.storageManager.saveAllData(); 
            } catch (Exception e) {
              getComponentLogger().error(Component.text("Error during auto-save: " + e.getMessage()).color((TextColor)Constants.Colors.ERROR));
            } 
          }, intervalTicks, intervalTicks);
      getComponentLogger().info(Component.text("Auto-save scheduled every " + this.configManager.getAutoSaveInterval() + " seconds")
          .color((TextColor)Constants.Colors.INFO));
    } catch (Exception e) {
      getComponentLogger().error(Component.text("Failed to schedule auto-save: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
    } 
  }
  
  private void scheduleNotificationSystem() {
    try {
      getServer().getScheduler().runTaskTimer((Plugin)this, () -> {
            try {
              checkForNewSlotNotifications();
            } catch (Exception e) {
              getComponentLogger().error(Component.text("Error during notification check: " + e.getMessage()).color((TextColor)Constants.Colors.ERROR));
            } 
          }, 1200L, 1200L);
      getComponentLogger().info(Component.text("Notification system scheduled every 1 minute")
          .color((TextColor)Constants.Colors.INFO));
    } catch (Exception e) {
      getComponentLogger().error(Component.text("Failed to schedule notification system: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
    } 
  }
  
  private void checkForNewSlotNotifications() {
    for (Player player : getServer().getOnlinePlayers()) {
      try {
        checkPlayerForNewSlots(player);
      } catch (Exception e) {
        getComponentLogger().error(Component.text("Error checking notifications for " + player.getName() + ": " + e.getMessage())
            .color((TextColor)Constants.Colors.ERROR));
      } 
    } 
  }
  
  public void checkPlayerForNewSlots(Player player) {
    if (this.storageManager == null || this.configManager == null)
      return; 
    PlayerStorageData data = this.storageManager.getDataManager().getPlayerData(player.getUniqueId());
    int nextSlot = findNextUnlockableSlot(player, data);
    if (nextSlot == -1)
      return; 
    String requiredRank = this.configManager.getRequiredRank(nextSlot);
    if (requiredRank == null)
      return; 
    if (!this.configManager.hasRankRequirement(player, requiredRank))
      return; 
    long currentTime = System.currentTimeMillis();
    boolean isNewRank = !requiredRank.equals(data.getLastNotifiedRank());
    if (isNewRank) {
      if (this.configManager.logTransactions())
        getLogger().info("Player " + player.getName() + " gained access to new slot " + nextSlot + 1 + " with rank " + 
            requiredRank + " - sending immediate notification and resetting 'seen storage' flag"); 
      sendNewSlotNotification(player, nextSlot + 1);
      data.setLastNotifiedRank(requiredRank);
      data.setSeenNewSlotNotification(false);
      data.setLastReminderTime(currentTime);
      this.storageManager.getDataManager().markDirty();
    } else if (!data.hasSeenNewSlotNotification()) {
      long timeSinceLastReminder = currentTime - data.getLastReminderTime();
      if (timeSinceLastReminder >= 7200000L) {
        if (this.configManager.logTransactions())
          getLogger().info("Player " + player.getName() + " hasn't opened storage in 2+ hours - sending reminder for slot " + nextSlot + 1); 
        sendSlotReminder(player, nextSlot + 1);
        data.setLastReminderTime(currentTime);
        this.storageManager.getDataManager().markDirty();
      } 
    } 
  }
  
  private int findNextUnlockableSlot(Player player, PlayerStorageData data) {
    for (int i = 0; i < this.configManager.getStorageSlots(); i++) {
      if (!data.hasSlotUnlocked(i))
        if (!this.configManager.isProgressionRequired() || i <= 0 || data.hasSlotUnlocked(i - 1))
          return i;  
    } 
    return -1;
  }
  
  private void sendNewSlotNotification(Player player, int slotNumber) {
    Component message = this.configManager.getMessage("backpack-slot-unlocked");
    player.sendMessage(message);
  }
  
  private void sendSlotReminder(Player player, int slotNumber) {
    Component message = this.configManager.getMessage("backpack-reminder");
    player.sendMessage(message);
  }
  
  public void markPlayerSeenStorage(Player player) {
    if (this.storageManager == null)
      return; 
    PlayerStorageData data = this.storageManager.getDataManager().getPlayerData(player.getUniqueId());
    data.setSeenNewSlotNotification(true);
    this.storageManager.getDataManager().markDirty();
  }
  
  public void onDisable() {
    getComponentLogger().info(Component.text("StorageSlots is shutting down...")
        .color((TextColor)Constants.Colors.INFO));
    try {
      if (this.storageManager != null) {
        this.storageManager.saveAllData();
        getComponentLogger().info(Component.text("Storage data saved!")
            .color((TextColor)Constants.Colors.SUCCESS));
      } 
      if (this.inventoryManager != null)
        this.inventoryManager.cleanup(); 
      for (Player player : Bukkit.getOnlinePlayers()) {
        if (this.inventoryManager != null && this.inventoryManager.hasStorageOpen(player))
          this.inventoryManager.closeStorage(player); 
      } 
      getServer().getScheduler().cancelTasks((Plugin)this);
      getComponentLogger().info(Component.text("StorageSlots disabled successfully!")
          .color((TextColor)Constants.Colors.SUCCESS));
    } catch (Exception e) {
      getComponentLogger().error(Component.text("Error during plugin shutdown: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
    } finally {
      instance = null;
    } 
  }
  
  public static StorageSlots getInstance() {
    return instance;
  }
  
  public StorageConfig getConfigManager() {
    return this.configManager;
  }
  
  public StorageEconomyManager getEconomyManager() {
    return this.economyManager;
  }
  
  public StoragePermissionManager getPermissionManager() {
    return this.permissionManager;
  }
  
  public LuckPerms getLuckPerms() {
    return this.luckPerms;
  }
  
  public SafezoneManager getSafezoneManager() {
    return this.safezoneManager;
  }
  
  public StorageManager getStorageManager() {
    return this.storageManager;
  }
  
  public StorageInventoryManager getInventoryManager() {
    return this.inventoryManager;
  }
  
  public void reloadConfiguration() {
    try {
      if (this.configManager != null) {
        this.configManager.reload();
        // Ensure all config-dependent data is refreshed
        this.configManager.loadConfiguration();
        getComponentLogger().info(Component.text("Configuration reloaded!")
            .color((TextColor)Constants.Colors.SUCCESS));
      } 
    } catch (Exception e) {
      getComponentLogger().error(Component.text("Failed to reload configuration: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      throw new RuntimeException("Configuration reload failed", e);
    } 
  }
  
  public boolean isPlayerInSafezone(Player player) {
    return (this.safezoneManager != null && this.safezoneManager.isInSafezone(player));
  }
  
  public boolean hasPlayerUnlockedSlot(Player player, int slot) {
    if (this.storageManager == null)
      return false; 
    try {
      PlayerStorageData data = this.storageManager.getDataManager().getPlayerData(player.getUniqueId());
      return data.hasSlotUnlocked(slot);
    } catch (Exception e) {
      getComponentLogger().error(Component.text("Error checking slot unlock status: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      return false;
    } 
  }
  
  public CompletableFuture<Boolean> givePlayerSlot(Player player, int slot) {
    if (this.storageManager == null)
      return CompletableFuture.completedFuture(Boolean.valueOf(false)); 
    return CompletableFuture.supplyAsync(() -> {
          try {
            this.storageManager.giveSlot(player, player.getName(), slot);
            return Boolean.valueOf(true);
          } catch (Exception e) {
            getComponentLogger().error(Component.text("Error giving slot to player: " + e.getMessage()).color((TextColor)Constants.Colors.ERROR));
            return Boolean.valueOf(false);
          } 
        });
  }
}
