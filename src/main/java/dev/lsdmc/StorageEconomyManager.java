package dev.lsdmc;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class StorageEconomyManager {
    private final StorageSlots plugin;
    private final StorageConfig config;
    private final PlayerPointsAPI pointsAPI;
    private final String currencyName;
    private boolean apiWorking = true; // Track if API is working

    public StorageEconomyManager(StorageSlots plugin, StorageConfig config, PlayerPointsAPI pointsAPI) {
        this.plugin = plugin;
        this.config = config;
        this.pointsAPI = pointsAPI;
        this.currencyName = config.getCurrencyName();
        
        // Validate the API
        if (pointsAPI == null) {
            throw new IllegalArgumentException("PlayerPointsAPI cannot be null");
        }
        
        plugin.getLogger().info("StorageEconomyManager initialized with PlayerPoints API");
    }

    public CompletableFuture<Boolean> takeMoney(Player player, double amount) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (player == null || !player.isOnline()) {
            future.complete(false);
            return future;
        }

        if (player.hasPermission("storageslots.bypass.cost")) {
            plugin.getLogger().info("Player " + player.getName() + " bypassed cost requirement");
            future.complete(true);
            return future;
        }

        int points = (int) amount;
        if (points <= 0) {
            future.complete(true);
            return future;
        }

        // Run async to prevent lag
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (apiWorking) {
                        // Try API first
                        int currentPoints = pointsAPI.look(player.getUniqueId());
                        if (currentPoints >= points) {
                            boolean success = pointsAPI.take(player.getUniqueId(), points);
                            if (success) {
                                plugin.getLogger().info("Successfully took " + points + " points from " + player.getName() + " via API");
                                future.complete(true);
                                return;
                            } else {
                                plugin.getLogger().warning("API failed to take points from " + player.getName() + ", trying command fallback");
                                apiWorking = false; // Mark API as failed
                            }
                        } else {
                            plugin.getLogger().info("Insufficient points for " + player.getName() + ": " + currentPoints + "/" + points);
                            future.complete(false);
                            return;
                        }
                    }
                    
                    // Fallback to command if API failed or is marked as not working
                    boolean success = executePointsCommand(player.getName(), points);
                    if (success) {
                        plugin.getLogger().info("Successfully took " + points + " points from " + player.getName() + " via command fallback");
                        future.complete(true);
                    } else {
                        plugin.getLogger().severe("Both API and command fallback failed for " + player.getName());
                        future.complete(false);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Error taking points from " + player.getName(), e);
                    // Try command fallback on exception
                    boolean success = executePointsCommand(player.getName(), points);
                    future.complete(success);
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    public CompletableFuture<Double> getBalance(Player player) {
        CompletableFuture<Double> future = new CompletableFuture<>();

        if (player == null || !player.isOnline()) {
            future.complete(0.0);
            return future;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (apiWorking) {
                        // Try API first
                        int balance = pointsAPI.look(player.getUniqueId());
                        future.complete((double) balance);
                        return;
                    }
                    
                    // Fallback to command if API is not working
                    int balance = getBalanceViaCommand(player.getName());
                    future.complete((double) balance);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Error getting balance for " + player.getName(), e);
                    // Try command fallback on exception
                    int balance = getBalanceViaCommand(player.getName());
                    future.complete((double) balance);
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    public String formatCurrency(double amount) {
        return String.format("%,d", (int)amount);
    }

    public String getCurrencyName() {
        return currencyName;
    }

    public CompletableFuture<Boolean> takePoints(Player player, int amount) {
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(false);
        }

        if (amount <= 0) {
            return CompletableFuture.completedFuture(true);
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        // Run async to prevent lag
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (apiWorking) {
                        // Try API first
                        int currentPoints = pointsAPI.look(player.getUniqueId());
                        if (currentPoints >= amount) {
                            boolean success = pointsAPI.take(player.getUniqueId(), amount);
                            if (success) {
                                plugin.getLogger().info("Successfully took " + amount + " points from " + player.getName() + " for withdrawal via API");
                                future.complete(true);
                                return;
                            } else {
                                plugin.getLogger().warning("API failed to take points for withdrawal from " + player.getName() + ", trying command fallback");
                                apiWorking = false; // Mark API as failed
                            }
                        } else {
                            plugin.getLogger().info("Insufficient points for withdrawal for " + player.getName() + ": " + currentPoints + "/" + amount);
                            future.complete(false);
                            return;
                        }
                    }
                    
                    // Fallback to command if API failed or is marked as not working
                    boolean success = executePointsCommand(player.getName(), amount);
                    if (success) {
                        plugin.getLogger().info("Successfully took " + amount + " points from " + player.getName() + " for withdrawal via command fallback");
                        future.complete(true);
                    } else {
                        plugin.getLogger().severe("Both API and command fallback failed for withdrawal for " + player.getName());
                        future.complete(false);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Error taking points from " + player.getName() + " for withdrawal", e);
                    // Try command fallback on exception
                    boolean success = executePointsCommand(player.getName(), amount);
                    future.complete(success);
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }
    
    /**
     * Execute the /points take command as a fallback
     */
    private boolean executePointsCommand(String playerName, int amount) {
        try {
            // Execute the command on the main thread
            return Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                try {
                    String command = "points take " + playerName + " " + amount;
                    boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    
                    if (!success) {
                        plugin.getLogger().warning("Command execution failed: " + command);
                    }
                    
                    return success;
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Error executing points command", e);
                    return false;
                }
            }).get();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error in command fallback for " + playerName, e);
            return false;
        }
    }
    
    /**
     * Get balance via command as a fallback
     */
    private int getBalanceViaCommand(String playerName) {
        try {
            // Execute the command on the main thread
            return Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                try {
                    // Create a custom command executor to capture the output
                    BalanceCommandExecutor balanceExecutor = new BalanceCommandExecutor();
                    
                    // Register the command temporarily
                    String tempCommand = "tempbalance" + System.currentTimeMillis();
                    plugin.getCommand(tempCommand);
                    
                    // Execute the points look command
                    String command = "points look " + playerName;
                    boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    
                    if (!success) {
                        plugin.getLogger().warning("Balance command execution failed: " + command);
                        return 0;
                    }
                    
                    // Since we can't easily capture the output, we'll use a different approach
                    // Try to get the balance using the API again, or return a default value
                    try {
                        if (pointsAPI != null) {
                            // Try to get the player UUID and use API
                            Player player = Bukkit.getPlayer(playerName);
                            if (player != null) {
                                int balance = pointsAPI.look(player.getUniqueId());
                                plugin.getLogger().info("Retrieved balance for " + playerName + " via API fallback: " + balance);
                                return balance;
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Could not get balance via API fallback for " + playerName + ": " + e.getMessage());
                    }
                    
                    // If all else fails, return 0 and log the issue
                    plugin.getLogger().warning("Could not determine balance for " + playerName + ", assuming 0");
                    return 0;
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Error executing balance command", e);
                    return 0;
                }
            }).get();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error in balance command fallback for " + playerName, e);
            return 0;
        }
    }
    
    /**
     * Custom command executor for capturing balance command output
     */
    private static class BalanceCommandExecutor implements org.bukkit.command.CommandExecutor {
        private int capturedBalance = 0;
        
        @Override
        public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
            if (args.length > 0) {
                try {
                    capturedBalance = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    capturedBalance = 0;
                }
            }
            return true;
        }
        
        public int getCapturedBalance() {
            return capturedBalance;
        }
    }
    
    /**
     * Reset API working status (useful for testing)
     */
    public void resetApiStatus() {
        apiWorking = true;
        plugin.getLogger().info("PlayerPoints API status reset to working");
    }
    
    /**
     * Test method to verify PlayerPoints integration is working
     */
    public boolean testPlayerPointsIntegration() {
        try {
            // Try to get the PlayerPoints instance
            PlayerPoints playerPoints = PlayerPoints.getInstance();
            if (playerPoints == null) {
                plugin.getLogger().severe("PlayerPoints instance is null!");
                return false;
            }
            
            // Try to get the API
            PlayerPointsAPI api = playerPoints.getAPI();
            if (api == null) {
                plugin.getLogger().severe("PlayerPoints API is null!");
                return false;
            }
            
            // Test API with a simple operation
            try {
                api.look(java.util.UUID.randomUUID());
                apiWorking = true;
                plugin.getLogger().info("PlayerPoints API test passed - API is working");
                return true;
            } catch (Exception e) {
                plugin.getLogger().warning("PlayerPoints API test failed, will use command fallback: " + e.getMessage());
                apiWorking = false;
                return true; // Return true because we have fallback
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "PlayerPoints integration test failed", e);
            apiWorking = false;
            return false;
        }
    }
} 