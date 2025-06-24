package dev.lsdmc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.lsdmc.utils.Constants;

import org.bukkit.entity.Player;
import org.bukkit.command.*;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class StorageSlots extends JavaPlugin {
    private StorageManager storageManager;
    private StorageConfig configManager;
    private StorageEconomyManager economyManager;
    private StoragePermissionManager permissionManager;
    private StorageCommandExecutor commandExecutor;
    private SafezoneManager safezoneManager;
    private StorageInventoryManager inventoryManager;
    private LuckPerms luckPerms;
    private MiniMessage miniMessage;

    private static StorageSlots instance;

    @Override
    public void onEnable() {
        instance = this;
        
        // Initialize MiniMessage
        miniMessage = MiniMessage.miniMessage();
        
        getComponentLogger().info(Component.text("Starting StorageSlots...")
            .color(Constants.Colors.INFO));

        try {
            // Initialize Constants with plugin instance
            Constants.Keys.initialize(this);

            // Initialize configuration first
            initializeConfiguration();

            // Setup dependencies
            if (!setupDependencies()) {
                return;
            }

            // Initialize managers in correct order
            initializeManagers();

            // Register events and commands
            registerEvents();
            registerCommands();

            // Verify components
            verifyComponents();
            
            // Schedule auto-save if enabled
            scheduleAutoSave();

            // Schedule notification system
            scheduleNotificationSystem();

            getComponentLogger().info(Component.text("StorageSlots enabled successfully!")
                .color(Constants.Colors.SUCCESS));
                
        } catch (Exception e) {
            getComponentLogger().error(Component.text("Failed to enable StorageSlots: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    private void initializeConfiguration() {
        try {
            // Save default config if it doesn't exist
            saveDefaultConfig();
            
            // Initialize configuration manager
            configManager = new StorageConfig(this);
            
            getComponentLogger().info(Component.text("Configuration initialized successfully!")
                .color(Constants.Colors.SUCCESS));
        } catch (Exception e) {
            getComponentLogger().error(Component.text("Failed to initialize configuration: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            throw new RuntimeException("Configuration initialization failed", e);
        }
    }
    
    private boolean setupDependencies() {
        try {
            return setupLuckPerms() && setupWorldGuard() && setupPlayerPoints();
        } catch (Exception e) {
            getComponentLogger().error(Component.text("Failed to setup dependencies: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            return false;
        }
    }

    private boolean setupLuckPerms() {
        try {
            if (!getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
                getComponentLogger().error(Component.text("LuckPerms is not enabled! Plugin disabling...")
                    .color(Constants.Colors.ERROR));
                getServer().getPluginManager().disablePlugin(this);
                return false;
            }
            
            luckPerms = LuckPermsProvider.get();
            permissionManager = new StoragePermissionManager(this, luckPerms);
            
            getComponentLogger().info(Component.text("Successfully hooked into LuckPerms!")
                .color(Constants.Colors.SUCCESS));
            return true;
        } catch (Exception e) {
            getComponentLogger().error(Component.text("Failed to hook into LuckPerms: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    private boolean setupWorldGuard() {
        try {
            if (!getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
                getComponentLogger().error(Component.text("WorldGuard is not enabled! Plugin disabling...")
                    .color(Constants.Colors.ERROR));
                getServer().getPluginManager().disablePlugin(this);
                return false;
            }
            
            getComponentLogger().info(Component.text("Successfully detected WorldGuard!")
                .color(Constants.Colors.SUCCESS));
            return true;
        } catch (Exception e) {
            getComponentLogger().error(Component.text("Failed to setup WorldGuard: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    private boolean setupPlayerPoints() {
        try {
            if (!getServer().getPluginManager().isPluginEnabled("PlayerPoints")) {
                getComponentLogger().error(Component.text("PlayerPoints is not enabled! Plugin disabling...")
                    .color(Constants.Colors.ERROR));
                getServer().getPluginManager().disablePlugin(this);
                return false;
            }
            
            PlayerPoints playerPoints = PlayerPoints.getInstance();
            if (playerPoints == null) {
                getComponentLogger().error(Component.text("PlayerPoints instance is null! Plugin disabling...")
                    .color(Constants.Colors.ERROR));
                getServer().getPluginManager().disablePlugin(this);
                return false;
            }
            
            PlayerPointsAPI pointsAPI = playerPoints.getAPI();
            if (pointsAPI == null) {
                getComponentLogger().error(Component.text("PlayerPoints API is null! Plugin disabling...")
                    .color(Constants.Colors.ERROR));
                getServer().getPluginManager().disablePlugin(this);
                return false;
            }
            
            // Test the API with a simple operation
            try {
                // Try to get balance for a test UUID (this should not fail)
                pointsAPI.look(java.util.UUID.randomUUID());
                getComponentLogger().info(Component.text("PlayerPoints API test successful!")
                    .color(Constants.Colors.SUCCESS));
            } catch (Exception e) {
                getComponentLogger().error(Component.text("PlayerPoints API test failed: " + e.getMessage())
                    .color(Constants.Colors.ERROR));
                getServer().getPluginManager().disablePlugin(this);
                return false;
            }
            
            economyManager = new StorageEconomyManager(this, configManager, pointsAPI);
            
            getComponentLogger().info(Component.text("Successfully hooked into PlayerPoints!")
                .color(Constants.Colors.SUCCESS));
            return true;
        } catch (Exception e) {
            getComponentLogger().error(Component.text("Failed to hook into PlayerPoints: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    private void initializeManagers() {
        try {
            // Initialize storage manager (which also creates inventory manager)
            storageManager = new StorageManager(this, configManager);
            
            // Initialize safezone manager
            safezoneManager = new SafezoneManager(this, configManager);
            
            // Get inventory manager from storage manager (avoid duplicate creation)
            inventoryManager = storageManager.getInventoryManager();
            
            // Initialize command executor
            commandExecutor = new StorageCommandExecutor(this, storageManager, configManager, permissionManager);
            
            getComponentLogger().info(Component.text("All managers initialized successfully!")
                .color(Constants.Colors.SUCCESS));
        } catch (Exception e) {
            getComponentLogger().error(Component.text("Failed to initialize managers: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            throw new RuntimeException("Manager initialization failed", e);
        }
    }

    private void registerEvents() {
        try {
            getServer().getPluginManager().registerEvents(storageManager, this);
            
            // Register player join event for immediate notification checks
            getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
                @org.bukkit.event.EventHandler
                public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                    // Check for notifications 5 seconds after join (give time for permissions to load)
                    getServer().getScheduler().runTaskLater(StorageSlots.this, () -> {
                        try {
                            checkPlayerForNewSlots(event.getPlayer());
                        } catch (Exception e) {
                            getComponentLogger().error(Component.text("Error checking notifications for " + event.getPlayer().getName() + " on join: " + e.getMessage())
                                .color(Constants.Colors.ERROR));
                        }
                    }, 100L); // 5 seconds = 100 ticks
                }
            }, this);
            
            getComponentLogger().info(Component.text("Event listeners registered!")
                .color(Constants.Colors.SUCCESS));
        } catch (Exception e) {
            getComponentLogger().error(Component.text("Failed to register events: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            throw new RuntimeException("Event registration failed", e);
        }
    }

    private void registerCommands() {
        try {
            // Main commands
            registerCommand("storage", commandExecutor);
            registerCommand("buystorage", commandExecutor);
            
            // Admin commands
            registerCommand("storagecost", commandExecutor);
            registerCommand("storagereload", commandExecutor);
            registerCommand("storagedelete", commandExecutor);
            registerCommand("storageadmin", commandExecutor);
            registerCommand("viewstorage", commandExecutor);
            registerCommand("removeslot", commandExecutor);
            registerCommand("giveslot", commandExecutor);
            registerCommand("listslots", commandExecutor);
            registerCommand("togglecooldown", commandExecutor);
            registerCommand("testeconomy", commandExecutor);
            registerCommand("testfallback", commandExecutor);
            registerCommand("resetapi", commandExecutor);
            registerCommand("debugsafezone", commandExecutor);
            
            getComponentLogger().info(Component.text("Commands registered successfully!")
                .color(Constants.Colors.SUCCESS));
        } catch (Exception e) {
            getComponentLogger().error(Component.text("Failed to register commands: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            throw new RuntimeException("Command registration failed", e);
        }
    }

    private void registerCommand(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            if (executor instanceof TabCompleter) {
                command.setTabCompleter((TabCompleter) executor);
            }
        } else {
            getComponentLogger().warn(Component.text("Command '" + name + "' not found in plugin.yml!")
                .color(Constants.Colors.ERROR));
        }
    }

    private void verifyComponents() {
        List<String> failures = new ArrayList<>();
        
        if (configManager == null) {
            failures.add("Configuration manager");
        }
        if (economyManager == null) {
            failures.add("Economy manager");
        }
        if (permissionManager == null) {
            failures.add("Permission manager");
        }
        if (safezoneManager == null) {
            failures.add("Safezone manager");
        }
        if (storageManager == null) {
            failures.add("Storage manager");
        }
        if (inventoryManager == null) {
            failures.add("Inventory manager");
        }
        if (commandExecutor == null) {
            failures.add("Command executor");
        }
        
        if (!failures.isEmpty()) {
            getComponentLogger().error(Component.text("Failed to initialize: " + String.join(", ", failures))
                .color(Constants.Colors.ERROR));
            throw new RuntimeException("Component verification failed");
        }
        
        getComponentLogger().info(Component.text("All components verified successfully!")
            .color(Constants.Colors.SUCCESS));
    }
    
    private void scheduleAutoSave() {
        if (!configManager.isAutoSaveEnabled()) {
            return;
        }
        
        try {
            long intervalTicks = configManager.getAutoSaveInterval() * 20L;
            
            getServer().getScheduler().runTaskTimer(this, () -> {
                try {
                    if (storageManager != null) {
                        storageManager.saveAllData();
                    }
                } catch (Exception e) {
                    getComponentLogger().error(Component.text("Error during auto-save: " + e.getMessage())
                        .color(Constants.Colors.ERROR));
                }
            }, intervalTicks, intervalTicks);
            
            getComponentLogger().info(Component.text("Auto-save scheduled every " + configManager.getAutoSaveInterval() + " seconds")
                .color(Constants.Colors.INFO));
        } catch (Exception e) {
            getComponentLogger().error(Component.text("Failed to schedule auto-save: " + e.getMessage())
                .color(Constants.Colors.ERROR));
        }
    }
    
    private void scheduleNotificationSystem() {
        try {
            // Check for new slot notifications every 1 minute for better responsiveness
            getServer().getScheduler().runTaskTimer(this, () -> {
                try {
                    checkForNewSlotNotifications();
                } catch (Exception e) {
                    getComponentLogger().error(Component.text("Error during notification check: " + e.getMessage())
                        .color(Constants.Colors.ERROR));
                }
            }, 1200L, 1200L); // 1 minute = 1200 ticks
            
            getComponentLogger().info(Component.text("Notification system scheduled every 1 minute")
                .color(Constants.Colors.INFO));
        } catch (Exception e) {
            getComponentLogger().error(Component.text("Failed to schedule notification system: " + e.getMessage())
                .color(Constants.Colors.ERROR));
        }
    }
    
    private void checkForNewSlotNotifications() {
        for (Player player : getServer().getOnlinePlayers()) {
            try {
                checkPlayerForNewSlots(player);
            } catch (Exception e) {
                getComponentLogger().error(Component.text("Error checking notifications for " + player.getName() + ": " + e.getMessage())
                    .color(Constants.Colors.ERROR));
            }
        }
    }
    
    public void checkPlayerForNewSlots(Player player) {
        if (storageManager == null || configManager == null) return;
        
        PlayerStorageData data = storageManager.getDataManager().getPlayerData(player.getUniqueId());
        
        // Find the next unlockable slot
        int nextSlot = findNextUnlockableSlot(player, data);
        if (nextSlot == -1) return; // No unlockable slots
        
        String requiredRank = configManager.getRequiredRank(nextSlot);
        if (requiredRank == null) return;
        
        // Check if player has the required rank
        if (!configManager.hasRankRequirement(player, requiredRank)) return;
        
        // Get current time
        long currentTime = System.currentTimeMillis();
        
        // Check if this is a new rank they just gained access to
        boolean isNewRank = !requiredRank.equals(data.getLastNotifiedRank());
        
        if (isNewRank) {
            // Player just gained access to a new slot - send immediate notification
            if (configManager.logTransactions()) {
                this.getLogger().info("Player " + player.getName() + " gained access to new slot " + (nextSlot + 1) + 
                    " with rank " + requiredRank + " - sending immediate notification and resetting 'seen storage' flag");
            }
            
            sendNewSlotNotification(player, nextSlot + 1);
            
            // Update tracking - reset the "seen storage" flag for the new slot
            data.setLastNotifiedRank(requiredRank);
            data.setSeenNewSlotNotification(false); // Reset - they need to see storage for this NEW slot
            data.setLastReminderTime(currentTime);
            storageManager.getDataManager().markDirty();
            
        } else {
            // Same rank as before - check if they need a reminder
            if (!data.hasSeenNewSlotNotification()) {
                // They haven't opened storage since the last notification
                long timeSinceLastReminder = currentTime - data.getLastReminderTime();
                
                if (timeSinceLastReminder >= 7200000) { // 2 hours in milliseconds
                    if (configManager.logTransactions()) {
                        this.getLogger().info("Player " + player.getName() + " hasn't opened storage in 2+ hours - sending reminder for slot " + (nextSlot + 1));
                    }
                    
                    sendSlotReminder(player, nextSlot + 1);
                    data.setLastReminderTime(currentTime);
                    storageManager.getDataManager().markDirty();
                }
            }
        }
    }
    
    private int findNextUnlockableSlot(Player player, PlayerStorageData data) {
        for (int i = 0; i < configManager.getStorageSlots(); i++) {
            if (!data.hasSlotUnlocked(i)) {
                // Check progression requirement
                if (configManager.isProgressionRequired() && i > 0 && !data.hasSlotUnlocked(i - 1)) {
                    continue; // Can't unlock this slot yet due to progression
                }
                return i;
            }
        }
        return -1; // All slots unlocked
    }
    
    private void sendNewSlotNotification(Player player, int slotNumber) {
        // Create interactive /bp command with hover tooltip
        Component bpCommand = createCommandComponent("/bp", 
            "Available commands:", 
            Arrays.asList("/bp", "/storage", "/slots", "/backpack"),
            "Click to open storage");
        
        // Get base message and replace placeholder
        String baseMessage = configManager.getMessages().getString("backpack-slot-unlocked", "");
        baseMessage = baseMessage.replace("{prefix}", configManager.getMessages().getString("prefix", ""));
        baseMessage = baseMessage.replace("{bp_command}", "<COMMAND_PLACEHOLDER>");
        
        // Parse the message and replace the placeholder with our interactive component
        Component message = miniMessage.deserialize(baseMessage);
        Component finalMessage = replaceCommandPlaceholder(message, bpCommand);
        
        player.sendMessage(finalMessage);
    }
    
    private void sendSlotReminder(Player player, int slotNumber) {
        // Create interactive /storage command with hover tooltip
        Component storageCommand = createCommandComponent("/storage", 
            "Available commands:", 
            Arrays.asList("/storage", "/bp", "/slots", "/backpack"),
            "Click to open storage");
        
        // Get base message and replace placeholder
        String baseMessage = configManager.getMessages().getString("backpack-reminder", "");
        baseMessage = baseMessage.replace("{prefix}", configManager.getMessages().getString("prefix", ""));
        baseMessage = baseMessage.replace("{storage_command}", "<COMMAND_PLACEHOLDER>");
        
        // Parse the message and replace the placeholder with our interactive component
        Component message = miniMessage.deserialize(baseMessage);
        Component finalMessage = replaceCommandPlaceholder(message, storageCommand);
        
        player.sendMessage(finalMessage);
    }
    
    /**
     * Create an interactive command component with hover tooltip and click action
     */
    private Component createCommandComponent(String mainCommand, String hoverTitle, List<String> availableCommands, String clickHint) {
        // Create hover content
        List<Component> hoverLines = new ArrayList<>();
        hoverLines.add(Component.text(hoverTitle).color(Constants.Colors.HEADER).decorate(TextDecoration.BOLD));
        hoverLines.add(Component.empty());
        
        for (String cmd : availableCommands) {
            hoverLines.add(Component.text("• " + cmd).color(Constants.Colors.HIGHLIGHT));
        }
        
        hoverLines.add(Component.empty());
        hoverLines.add(Component.text(clickHint).color(Constants.Colors.INFO).decorate(TextDecoration.ITALIC));
        
        // Join hover lines
        Component hoverText = Component.join(Component.newline(), hoverLines);
        
        // Create the interactive command component
        return Component.text(mainCommand)
            .color(Constants.Colors.HIGHLIGHT)
            .decorate(TextDecoration.BOLD)
            .hoverEvent(HoverEvent.showText(hoverText))
            .clickEvent(ClickEvent.runCommand("/storage"));
    }
    
    /**
     * Replace command placeholder in a component with the interactive component
     */
    private Component replaceCommandPlaceholder(Component message, Component commandComponent) {
        // Convert to string for replacement
        String messageStr = miniMessage.serialize(message);
        
        if (messageStr.contains("<COMMAND_PLACEHOLDER>")) {
            // Split the message at the placeholder
            String[] parts = messageStr.split("<COMMAND_PLACEHOLDER>", 2);
            
            List<Component> components = new ArrayList<>();
            
            // Add the part before the placeholder
            if (!parts[0].isEmpty()) {
                components.add(miniMessage.deserialize(parts[0]));
            }
            
            // Add the interactive command component
            components.add(commandComponent);
            
            // Add the part after the placeholder
            if (parts.length > 1 && !parts[1].isEmpty()) {
                components.add(miniMessage.deserialize(parts[1]));
            }
            
            return Component.join(Component.empty(), components);
        }
        
        // If no placeholder found, return original message
        return message;
    }
    
    public void markPlayerSeenStorage(Player player) {
        if (storageManager == null) return;
        
        PlayerStorageData data = storageManager.getDataManager().getPlayerData(player.getUniqueId());
        data.setSeenNewSlotNotification(true);
        storageManager.getDataManager().markDirty();
    }

    @Override
    public void onDisable() {
        getComponentLogger().info(Component.text("StorageSlots is shutting down...")
            .color(Constants.Colors.INFO));
        
        try {
            // Save all data
            if (storageManager != null) {
                storageManager.saveAllData();
                getComponentLogger().info(Component.text("Storage data saved!")
                    .color(Constants.Colors.SUCCESS));
            }
            
            // Cleanup inventory manager
            if (inventoryManager != null) {
                inventoryManager.cleanup();
            }
            
            // Close any open inventories
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (inventoryManager != null && inventoryManager.hasStorageOpen(player)) {
                    inventoryManager.closeStorage(player);
                }
            }
            
            // Cancel all tasks
            getServer().getScheduler().cancelTasks(this);
            
            getComponentLogger().info(Component.text("StorageSlots disabled successfully!")
                .color(Constants.Colors.SUCCESS));
        } catch (Exception e) {
            getComponentLogger().error(Component.text("Error during plugin shutdown: " + e.getMessage())
                .color(Constants.Colors.ERROR));
        } finally {
            instance = null;
        }
    }

    // Getters for managers
    public static StorageSlots getInstance() {
        return instance;
    }

    public StorageConfig getConfigManager() {
        return configManager;
    }

    public StorageEconomyManager getEconomyManager() {
        return economyManager;
    }

    public StoragePermissionManager getPermissionManager() {
        return permissionManager;
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    public SafezoneManager getSafezoneManager() {
        return safezoneManager;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }
    
    public StorageInventoryManager getInventoryManager() {
        return inventoryManager;
    }

    // Utility methods
    public void reloadConfiguration() {
        try {
            if (configManager != null) {
                configManager.reload();
                getComponentLogger().info(Component.text("Configuration reloaded!")
                    .color(Constants.Colors.SUCCESS));
            }
        } catch (Exception e) {
            getComponentLogger().error(Component.text("Failed to reload configuration: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            throw new RuntimeException("Configuration reload failed", e);
        }
    }
    
    // API methods for other plugins
    public boolean isPlayerInSafezone(Player player) {
        return safezoneManager != null && safezoneManager.isInSafezone(player);
    }
    
    public boolean hasPlayerUnlockedSlot(Player player, int slot) {
        if (storageManager == null) return false;
        
        try {
            PlayerStorageData data = storageManager.getDataManager().getPlayerData(player.getUniqueId());
            return data.hasSlotUnlocked(slot);
        } catch (Exception e) {
            getComponentLogger().error(Component.text("Error checking slot unlock status: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            return false;
        }
    }
    
    public CompletableFuture<Boolean> givePlayerSlot(Player player, int slot) {
        if (storageManager == null) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                storageManager.giveSlot(player, player.getName(), slot);
                return true;
            } catch (Exception e) {
                getComponentLogger().error(Component.text("Error giving slot to player: " + e.getMessage())
                    .color(Constants.Colors.ERROR));
                return false;
            }
        });
    }
}