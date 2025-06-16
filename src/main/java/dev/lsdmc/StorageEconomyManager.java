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
    private String currencyName;

    public StorageEconomyManager(StorageSlots plugin, PlayerPointsAPI pointsAPI) {
        this.plugin = plugin;
        this.config = new StorageConfig(plugin.getConfig());
        this.pointsAPI = pointsAPI;
        this.currencyName = config.getCurrencyName();
    }

    public CompletableFuture<Boolean> takeMoney(Player player, double amount) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (player.hasPermission("storageslots.bypass.cost")) {
            future.complete(true);
            return future;
        }

        int points = (int) amount;

        // Run async to prevent lag
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    int currentPoints = pointsAPI.look(player.getUniqueId());
                    if (currentPoints >= points) {
                        boolean success = pointsAPI.take(player.getUniqueId(), points);
                        if (success) {
                            plugin.getLogger().info("Successfully took " + points + " points from " + player.getName());
                        } else {
                            plugin.getLogger().warning("Failed to take points from " + player.getName());
                        }
                        future.complete(success);
                    } else {
                        future.complete(false);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Error taking points from " + player.getName(), e);
                    future.complete(false);
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    public CompletableFuture<Double> getBalance(Player player) {
        CompletableFuture<Double> future = new CompletableFuture<>();

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    int balance = pointsAPI.look(player.getUniqueId());
                    future.complete((double) balance);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Error getting balance for " + player.getName(), e);
                    future.complete(0.0);
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
        if (amount <= 0) {
            return CompletableFuture.completedFuture(true);
        }

        return getBalance(player).thenApply(balance -> {
            if (balance < amount) {
                return false;
            }

            // Execute points removal command
            String command = config.getPointsRemoveCommand()
                    .replace("%player%", player.getName())
                    .replace("%amount%", String.valueOf(amount));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            return true;
        });
    }
} 