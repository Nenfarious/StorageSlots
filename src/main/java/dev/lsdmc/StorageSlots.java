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
    private LuckPerms luckPerms;
    private PlayerPointsAPI pointsAPI;

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
        } catch (Exception e) {
            getLogger().severe("LuckPerms not found! Plugin disabling...");
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

        // Register events and commands
        getServer().getPluginManager().registerEvents(storageManager, this);
        registerCommands();

        getLogger().info("StorageSlots enabled!");

        // Verify PlayerPoints API is working
        verifyPlayerPoints();
    }

    private void verifyPlayerPoints() {
        if (pointsAPI == null) {
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
                    pointsAPI = playerPoints.getAPI();
                    return pointsAPI != null;
                }
            }
        } catch (Exception e) {
            getLogger().severe("Failed to hook into PlayerPoints: " + e.getMessage());
        }
        return false;
    }

    private void registerCommands() {
        Objects.requireNonNull(getCommand("storage")).setExecutor(this);
        Objects.requireNonNull(getCommand("buystorage")).setExecutor(this);
        Objects.requireNonNull(getCommand("storagecost")).setExecutor(this);
        Objects.requireNonNull(getCommand("storagereload")).setExecutor(this);
        Objects.requireNonNull(getCommand("storagedelete")).setExecutor(this);
        Objects.requireNonNull(getCommand("storageadmin")).setExecutor(this);
        Objects.requireNonNull(getCommand("viewstorage")).setExecutor(this);
        Objects.requireNonNull(getCommand("removeslot")).setExecutor(this);
        Objects.requireNonNull(getCommand("giveslot")).setExecutor(this);
        Objects.requireNonNull(getCommand("listslots")).setExecutor(this);
    }

    public CompletableFuture<Boolean> takeMoney(Player player, double amount) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (player.hasPermission("storageslots.bypass.cost")) {
            future.complete(true);
            return future;
        }

        int points = (int) amount;

        // Run async to prevent lag
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                int currentPoints = pointsAPI.look(player.getUniqueId());
                if (currentPoints >= points) {
                    boolean success = pointsAPI.take(player.getUniqueId(), points);
                    if (success) {
                        getLogger().info("Successfully took " + points + " points from " + player.getName());
                    } else {
                        getLogger().warning("Failed to take points from " + player.getName());
                    }
                    future.complete(success);
                } else {
                    future.complete(false);
                }
            } catch (Exception e) {
                getLogger().severe("Error taking points from " + player.getName() + ": " + e.getMessage());
                future.complete(false);
            }
        });

        return future;
    }

    public double getPlayerBalance(Player player) {
        try {
            return pointsAPI.look(player.getUniqueId());
        } catch (Exception e) {
            getLogger().severe("Error getting balance for " + player.getName() + ": " + e.getMessage());
            return 0;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        switch (cmd.getName().toLowerCase()) {
            case "storage":
                if (!player.hasPermission("storageslots.use")) {
                    player.sendMessage(configManager.getMessage("no-permission"));
                    return true;
                }
                storageManager.openStorage(player);
                break;

            case "buystorage":
                if (!player.hasPermission("storageslots.use")) {
                    player.sendMessage(configManager.getMessage("no-permission"));
                    return true;
                }
                if (args.length != 1) {
                    player.sendMessage(configManager.getMessage("invalid-slot"));
                    return true;
                }
                try {
                    int slot = Integer.parseInt(args[0]) - 1;
                    if (slot < 0 || slot >= configManager.getStorageSlots()) {
                        player.sendMessage(configManager.getMessage("invalid-slot"));
                        return true;
                    }
                    storageManager.purchaseSlot(player, slot);
                } catch (NumberFormatException e) {
                    player.sendMessage(configManager.getMessage("invalid-slot"));
                }
                break;

            case "storagecost":
                if (!player.hasPermission("storageslots.admin")) {
                    player.sendMessage(configManager.getMessage("no-permission"));
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /storagecost <slot> <cost>");
                    return true;
                }
                try {
                    int slot = Integer.parseInt(args[0]) - 1;
                    double cost = Double.parseDouble(args[1]);
                    if (slot < 0 || slot >= configManager.getStorageSlots()) {
                        player.sendMessage(configManager.getMessage("invalid-slot"));
                        return true;
                    }
                    configManager.setSlotCost(slot, cost);
                    player.sendMessage(ChatColor.GREEN + "Storage slot " + (slot + 1) + " cost set to " +
                            String.format("%,d", (int)cost) + " points");
                } catch (NumberFormatException e) {
                    player.sendMessage(configManager.getMessage("invalid-number"));
                }
                break;

            case "storagereload":
                if (!player.hasPermission("storageslots.admin")) {
                    player.sendMessage(configManager.getMessage("no-permission"));
                    return true;
                }
                configManager.reloadConfig();
                player.sendMessage(ChatColor.GREEN + "Storage configuration reloaded!");
                break;

            case "storagedelete":
                if (!player.hasPermission("storageslots.admin")) {
                    player.sendMessage(configManager.getMessage("no-permission"));
                    return true;
                }
                if (args.length == 0) {
                    player.sendMessage(ChatColor.RED + "Usage: /storagedelete <player|all>");
                    return true;
                }
                if (args[0].equalsIgnoreCase("all")) {
                    storageManager.resetAllStorage();
                    player.sendMessage(configManager.getMessage("storage-reset"));
                } else {
                    UUID targetId = storageManager.findPlayerUUID(args[0]);
                    if (targetId == null) {
                        player.sendMessage(configManager.getMessage("player-not-found"));
                        return true;
                    }
                    storageManager.resetPlayerStorage(targetId);
                    player.sendMessage(configManager.getMessage("player-storage-reset")
                            .replace("%player%", args[0]));
                }
                break;

            case "storageadmin":
            case "viewstorage":
                if (!player.hasPermission("storageslots.admin")) {
                    player.sendMessage(configManager.getMessage("no-permission"));
                    return true;
                }
                if (args.length != 1) {
                    player.sendMessage(ChatColor.RED + "Usage: /" + cmd.getName() + " <player>");
                    return true;
                }
                UUID targetId = storageManager.findPlayerUUID(args[0]);
                if (targetId == null) {
                    player.sendMessage(configManager.getMessage("player-not-found"));
                    return true;
                }
                storageManager.openPlayerStorage(player, targetId);
                break;

            case "giveslot":
                if (!player.hasPermission("storageslots.admin")) {
                    player.sendMessage(configManager.getMessage("no-permission"));
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /giveslot <player> <slot>");
                    return true;
                }
                try {
                    int slot = Integer.parseInt(args[1]) - 1;
                    storageManager.giveSlot(player, args[0], slot);
                } catch (NumberFormatException e) {
                    player.sendMessage(configManager.getMessage("invalid-slot"));
                }
                break;

            case "removeslot":
                if (!player.hasPermission("storageslots.admin")) {
                    player.sendMessage(configManager.getMessage("no-permission"));
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /removeslot <player> <slot>");
                    return true;
                }
                try {
                    int slot = Integer.parseInt(args[1]) - 1;
                    storageManager.removeSlot(player, args[0], slot);
                } catch (NumberFormatException e) {
                    player.sendMessage(configManager.getMessage("invalid-slot"));
                }
                break;

            case "listslots":
                if (!player.hasPermission("storageslots.admin")) {
                    player.sendMessage(configManager.getMessage("no-permission"));
                    return true;
                }
                if (args.length != 1) {
                    player.sendMessage(ChatColor.RED + "Usage: /listslots <player>");
                    return true;
                }
                storageManager.listSlots(player, args[0]);
                break;
        }
        return true;
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

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    public String getCurrencyName() {
        return "points";
    }

    public String formatCurrency(double amount) {
        return String.format("%,d", (int)amount);
    }

    public Config getConfigManager() {
        return configManager;
    }

    public PlayerPointsAPI getPointsAPI() {
        return pointsAPI;
    }
}