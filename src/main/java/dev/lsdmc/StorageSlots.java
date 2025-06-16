package dev.lsdmc;



import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.command.*;
import org.bukkit.ChatColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;

public class StorageSlots extends JavaPlugin {
    private StorageManager storageManager;
    private Config configManager;
    private StorageEconomyManager economyManager;
    private StoragePermissionManager permissionManager;
    private StorageCommandExecutor commandExecutor;
    private LuckPerms luckPerms;

    private static StorageSlots instance;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize configuration
        configManager = new Config(this);
        configManager.loadConfig();

        // Setup LuckPerms
        try {
            luckPerms = LuckPermsProvider.get();
            permissionManager = new StoragePermissionManager(this, luckPerms);
        } catch (Exception e) {
            getLogger().severe("LuckPerms not found! Plugin disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Setup WorldGuard
        if (!setupWorldGuard()) {
            getLogger().severe("WorldGuard not found! Plugin disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }


        // Setup PlayerPoints
        if (!setupPoints()) {
            getLogger().severe("PlayerPoints not found! Plugin disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize storage manager with new structure
        storageManager = new StorageManager(this, configManager);

        // Initialize command executor
        commandExecutor = new StorageCommandExecutor(this, storageManager, configManager, permissionManager);

        // Register events and commands
        getServer().getPluginManager().registerEvents(storageManager, this);
        registerCommands();

        getLogger().info("StorageSlots enabled!");

        // Verify PlayerPoints API is working
        verifyPlayerPoints();
    }

    private boolean setupWorldGuard() {
        try {
            if (getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
                return true;
            }
        } catch (Exception e) {
            getLogger().severe("Failed to hook into WorldGuard: " + e.getMessage());
        }
        return false;
    }

    private void verifyPlayerPoints() {
        if (economyManager == null) {
            getLogger().severe("PlayerPoints API failed to initialize!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Successfully hooked into PlayerPoints!");
    }

    private boolean setupPoints() {
        try {
            if (getServer().getPluginManager().isPluginEnabled("PlayerPoints")) {
                PlayerPoints playerPoints = PlayerPoints.getInstance();
                if (playerPoints != null) {
                    PlayerPointsAPI pointsAPI = playerPoints.getAPI();
                    if (pointsAPI != null) {
                        economyManager = new StorageEconomyManager(this, pointsAPI);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            getLogger().severe("Failed to hook into PlayerPoints: " + e.getMessage());
        }
        return false;
    }

    private void registerCommands() {
        Objects.requireNonNull(getCommand("storage")).setExecutor(commandExecutor);
        Objects.requireNonNull(getCommand("buystorage")).setExecutor(commandExecutor);
        Objects.requireNonNull(getCommand("storagecost")).setExecutor(commandExecutor);
        Objects.requireNonNull(getCommand("storagereload")).setExecutor(commandExecutor);
        Objects.requireNonNull(getCommand("storagedelete")).setExecutor(commandExecutor);
        Objects.requireNonNull(getCommand("storageadmin")).setExecutor(commandExecutor);
        Objects.requireNonNull(getCommand("viewstorage")).setExecutor(commandExecutor);
        Objects.requireNonNull(getCommand("removeslot")).setExecutor(commandExecutor);
        Objects.requireNonNull(getCommand("giveslot")).setExecutor(commandExecutor);
        Objects.requireNonNull(getCommand("listslots")).setExecutor(commandExecutor);

        // Register tab completers
        Objects.requireNonNull(getCommand("storage")).setTabCompleter(commandExecutor);
        Objects.requireNonNull(getCommand("buystorage")).setTabCompleter(commandExecutor);
        Objects.requireNonNull(getCommand("storagecost")).setTabCompleter(commandExecutor);
        Objects.requireNonNull(getCommand("storagedelete")).setTabCompleter(commandExecutor);
        Objects.requireNonNull(getCommand("storageadmin")).setTabCompleter(commandExecutor);
        Objects.requireNonNull(getCommand("viewstorage")).setTabCompleter(commandExecutor);
        Objects.requireNonNull(getCommand("removeslot")).setTabCompleter(commandExecutor);
        Objects.requireNonNull(getCommand("giveslot")).setTabCompleter(commandExecutor);
        Objects.requireNonNull(getCommand("listslots")).setTabCompleter(commandExecutor);
    }

    @Override
    public void onDisable() {
        if (storageManager != null) {
            storageManager.saveAllData();
        }
        instance = null;
    }

    public static StorageSlots getInstance() {
        return instance;
    }

    public Config getConfigManager() {
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

}