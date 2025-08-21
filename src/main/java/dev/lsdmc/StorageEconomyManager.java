package dev.lsdmc;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitRunnable;
import net.milkbowl.vault.economy.Economy;

public class StorageEconomyManager {
  private final StorageSlots plugin;
  
  private final PlayerPointsAPI pointsAPI;
  private final Economy vaultEconomy;
  
  private final StorageConfig config;
  
  private boolean apiWorking = true;
  
  public StorageEconomyManager(StorageSlots plugin, StorageConfig config, PlayerPointsAPI pointsAPI) {
    this.plugin = plugin;
    this.config = config;
    this.pointsAPI = pointsAPI;
    
    // Initialize Vault economy if any operation uses it
    Economy tempVaultEconomy = null;
    if (config.useVaultForSlotPurchase() || config.useVaultForWithdrawalFees() || config.useVaultForDonorSlots()) {
      tempVaultEconomy = setupVaultEconomy();
      if (tempVaultEconomy != null) {
        plugin.getLogger().info("StorageEconomyManager initialized with Vault economy");
      } else {
        plugin.getLogger().warning("Vault economy setup failed - falling back to PlayerPoints");
      }
    }
    this.vaultEconomy = tempVaultEconomy;
    
    if (pointsAPI == null) {
      plugin.getLogger().warning("PlayerPointsAPI is null - economy system will be limited");
      this.apiWorking = false;
    } else {
      plugin.getLogger().info("StorageEconomyManager initialized with PlayerPoints API");
    }
  }
  
  private Economy setupVaultEconomy() {
    if (!plugin.getServer().getPluginManager().isPluginEnabled("Vault")) {
      plugin.getLogger().warning("Vault is not enabled!");
      return null;
    }
    
    RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
    if (rsp == null) {
      plugin.getLogger().warning("No economy service found via Vault!");
      return null;
    }
    
    Economy economy = rsp.getProvider();
    if (economy == null) {
      plugin.getLogger().warning("Economy provider is null!");
      return null;
    }
    
    plugin.getLogger().info("Successfully hooked into Vault economy: " + economy.getName());
    return economy;
  }
  
  public CompletableFuture<Boolean> takeMoney(final Player player, double amount) {
    return takeMoney(player, amount, "slot-purchase"); // Default to slot purchase
  }
  
  public CompletableFuture<Boolean> takeMoney(final Player player, double amount, String operation) {
    final CompletableFuture<Boolean> future = new CompletableFuture<>();
    if (player == null || !player.isOnline()) {
      future.complete(Boolean.valueOf(false));
      return future;
    } 
    if (player.hasPermission("storageslots.bypass.cost")) {
      this.plugin.getLogger().info("Player " + player.getName() + " bypassed cost requirement");
      future.complete(Boolean.valueOf(true));
      return future;
    } 
    if (amount <= 0) {
      future.complete(Boolean.valueOf(true));
      return future;
    } 
    
    // Determine which currency to use based on operation
    boolean useVault = false;
    String currencyName = "points";
    
    switch (operation) {
      case "slot-purchase":
        useVault = this.config.useVaultForSlotPurchase();
        currencyName = this.config.getSlotPurchaseCurrency();
        break;
      case "withdrawal-fees":
        useVault = this.config.useVaultForWithdrawalFees();
        currencyName = this.config.getWithdrawalFeeCurrency();
        break;
      case "donor-slots":
        useVault = this.config.useVaultForDonorSlots();
        currencyName = this.config.getDonorSlotCurrency();
        break;
      default:
        useVault = this.config.useVaultForSlotPurchase();
        currencyName = this.config.getSlotPurchaseCurrency();
    }
    
    // Use Vault economy if enabled and available
    if (useVault && this.vaultEconomy != null) {
      try {
        if (this.vaultEconomy.has(player, amount)) {
          boolean success = this.vaultEconomy.withdrawPlayer(player, amount).transactionSuccess();
          if (success) {
            this.plugin.getLogger().info("Successfully took " + amount + " " + currencyName + " from " + player.getName() + " via Vault");
            future.complete(Boolean.valueOf(true));
          } else {
            this.plugin.getLogger().warning("Vault transaction failed for " + player.getName());
            future.complete(Boolean.valueOf(false));
          }
        } else {
          this.plugin.getLogger().info("Insufficient " + currencyName + " for " + player.getName() + ": " + this.vaultEconomy.getBalance(player) + "/" + amount);
          future.complete(Boolean.valueOf(false));
        }
      } catch (Exception e) {
        this.plugin.getLogger().log(Level.SEVERE, "Error with Vault economy for " + player.getName(), e);
        future.complete(Boolean.valueOf(false));
      }
      return future;
    }
    
    // Fallback to PlayerPoints
    final int points = (int)amount;
    
    // If API is null, try command fallback directly
    if (this.pointsAPI == null) {
      this.plugin.getLogger().warning("PlayerPoints API is null, using command fallback for " + player.getName());
      boolean success = executePointsCommand(player.getName(), points);
      future.complete(Boolean.valueOf(success));
      return future;
    }
    
    (new BukkitRunnable() {
        public void run() {
          try {
            if (StorageEconomyManager.this.apiWorking) {
              int currentPoints = StorageEconomyManager.this.pointsAPI.look(player.getUniqueId());
              if (currentPoints >= points) {
                boolean bool = StorageEconomyManager.this.pointsAPI.take(player.getUniqueId(), points);
                if (bool) {
                  StorageEconomyManager.this.plugin.getLogger().info("Successfully took " + points + " points from " + player.getName() + " via API");
                  future.complete(Boolean.valueOf(true));
                  return;
                } 
                StorageEconomyManager.this.plugin.getLogger().warning("API failed to take points from " + player.getName() + ", trying command fallback");
                StorageEconomyManager.this.apiWorking = false;
              } else {
                StorageEconomyManager.this.plugin.getLogger().info("Insufficient points for " + player.getName() + ": " + currentPoints + "/" + points);
                future.complete(Boolean.valueOf(false));
                return;
              } 
            } 
            boolean success = StorageEconomyManager.this.executePointsCommand(player.getName(), points);
            if (success) {
              StorageEconomyManager.this.plugin.getLogger().info("Successfully took " + points + " points from " + player.getName() + " via command fallback");
              future.complete(Boolean.valueOf(true));
            } else {
              StorageEconomyManager.this.plugin.getLogger().severe("Both API and command fallback failed for " + player.getName());
              future.complete(Boolean.valueOf(false));
            } 
          } catch (Exception e) {
            StorageEconomyManager.this.plugin.getLogger().log(Level.SEVERE, "Error taking points from " + player.getName(), e);
            boolean success = StorageEconomyManager.this.executePointsCommand(player.getName(), points);
            future.complete(Boolean.valueOf(success));
          } 
        }
      }).runTaskAsynchronously((Plugin)this.plugin);
    return future;
  }
  
  public CompletableFuture<Double> getBalance(final Player player) {
    return getBalance(player, "slot-purchase"); // Default to slot purchase
  }
  
  public CompletableFuture<Double> getBalance(final Player player, String operation) {
    final CompletableFuture<Double> future = new CompletableFuture<>();
    if (player == null || !player.isOnline()) {
      future.complete(Double.valueOf(0.0D));
      return future;
    } 
    
    // Determine which currency to use based on operation
    boolean useVault = false;
    
    switch (operation) {
      case "slot-purchase":
        useVault = this.config.useVaultForSlotPurchase();
        break;
      case "withdrawal-fees":
        useVault = this.config.useVaultForWithdrawalFees();
        break;
      case "donor-slots":
        useVault = this.config.useVaultForDonorSlots();
        break;
      default:
        useVault = this.config.useVaultForSlotPurchase();
    }
    
    // Use Vault economy if enabled and available
    if (useVault && this.vaultEconomy != null) {
      try {
        double balance = this.vaultEconomy.getBalance(player);
        future.complete(Double.valueOf(balance));
        return future;
      } catch (Exception e) {
        this.plugin.getLogger().log(Level.SEVERE, "Error getting Vault balance for " + player.getName(), e);
        future.complete(Double.valueOf(0.0D));
        return future;
      }
    }
    
    // Fallback to PlayerPoints
    // If API is null, try command fallback directly
    if (this.pointsAPI == null) {
      this.plugin.getLogger().warning("PlayerPoints API is null, using command fallback for balance check");
      int balance = getBalanceViaCommand(player.getName());
      future.complete(Double.valueOf(balance));
      return future;
    }
    
    (new BukkitRunnable() {
        public void run() {
          try {
            if (StorageEconomyManager.this.apiWorking) {
              int i = StorageEconomyManager.this.pointsAPI.look(player.getUniqueId());
              future.complete(Double.valueOf(i));
              return;
            } 
            int balance = StorageEconomyManager.this.getBalanceViaCommand(player.getName());
            future.complete(Double.valueOf(balance));
          } catch (Exception e) {
            StorageEconomyManager.this.plugin.getLogger().log(Level.SEVERE, "Error getting balance for " + player.getName(), e);
            int balance = StorageEconomyManager.this.getBalanceViaCommand(player.getName());
            future.complete(Double.valueOf(balance));
          } 
        }
      }).runTaskAsynchronously((Plugin)this.plugin);
    return future;
  }
  
  public String formatCurrency(double amount) {
    return formatCurrency(amount, "slot-purchase"); // Default to slot purchase
  }
  
  public String formatCurrency(double amount, String operation) {
    boolean useVault = false;
    
    switch (operation) {
      case "slot-purchase":
        useVault = this.config.useVaultForSlotPurchase();
        break;
      case "withdrawal-fees":
        useVault = this.config.useVaultForWithdrawalFees();
        break;
      case "donor-slots":
        useVault = this.config.useVaultForDonorSlots();
        break;
      default:
        useVault = this.config.useVaultForSlotPurchase();
    }
    
    if (useVault && this.vaultEconomy != null) {
      return this.vaultEconomy.format(amount);
    } else {
      // For PlayerPoints, format as integer
      return String.format("%,d", new Object[] { Integer.valueOf((int)amount) });
    }
  }
  
  public String getCurrencyName() {
    return getCurrencyName("slot-purchase"); // Default to slot purchase
  }
  
  public String getCurrencyName(String operation) {
    switch (operation) {
      case "slot-purchase":
        return this.config.getSlotPurchaseCurrency();
      case "withdrawal-fees":
        return this.config.getWithdrawalFeeCurrency();
      case "donor-slots":
        return this.config.getDonorSlotCurrency();
      default:
        return this.config.getSlotPurchaseCurrency();
    }
  }
  
  public CompletableFuture<Boolean> takePoints(final Player player, final int amount) {
    if (player == null || !player.isOnline() || amount <= 0)
      return CompletableFuture.completedFuture(false);
    final CompletableFuture<Boolean> future = new CompletableFuture<>();
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      try {
        int before = pointsAPI.look(player.getUniqueId());
        if (before < amount) {
          future.complete(false);
          return;
        }
        boolean success = Bukkit.getScheduler().callSyncMethod(plugin, () ->
          Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "points take " + player.getName() + " " + amount)
        ).get();
        int after = pointsAPI.look(player.getUniqueId());
        if (success && (after == before - amount)) {
          plugin.getLogger().info("Successfully took " + amount + " points from " + player.getName() + " via command");
          future.complete(true);
        } else {
          plugin.getLogger().warning("Failed to deduct points from " + player.getName() + ". Before: " + before + ", After: " + after);
          future.complete(false);
        }
      } catch (Exception e) {
        plugin.getLogger().log(Level.SEVERE, "Error deducting points for " + player.getName(), e);
        future.complete(false);
      }
    });
    return future;
  }
  
  private boolean executePointsCommand(String playerName, int amount) {
    try {
      // If we are already on the main server thread, execute the command directly to avoid
      // deadlocking via callSyncMethod().  Otherwise, schedule a synchronous task and wait
      // for it to complete.  This prevents the server from freezing when fallback
      // commands are issued on the main thread.
      if (Bukkit.isPrimaryThread()) {
        String command = "points take " + playerName + " " + amount;
        boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        if (!success) {
          this.plugin.getLogger().warning("Command execution failed: " + command);
        }
        return success;
      } else {
        return Bukkit.getScheduler().callSyncMethod((Plugin)this.plugin, () -> {
          String command = "points take " + playerName + " " + amount;
          try {
            boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            if (!success) {
              this.plugin.getLogger().warning("Command execution failed: " + command);
            }
            return success;
          } catch (Exception ex) {
            this.plugin.getLogger().log(Level.SEVERE, "Error executing points command", ex);
            return false;
          }
        }).get();
      }
    } catch (Exception e) {
      this.plugin.getLogger().log(Level.SEVERE, "Error in command fallback for " + playerName, e);
      return false;
    }
  }
  
  private int getBalanceViaCommand(String playerName) {
    try {
      // If we are on the main thread, execute the command synchronously without callSyncMethod to avoid deadlock.
      if (Bukkit.isPrimaryThread()) {
        String command = "points look " + playerName;
        boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        if (!success) {
          this.plugin.getLogger().warning("Balance command execution failed: " + command);
          return 0;
        }
        // Attempt to retrieve the balance via the API if available.  If the API is
        // unavailable or an error occurs, default to 0.
        try {
          if (this.pointsAPI != null) {
            Player player = Bukkit.getPlayer(playerName);
            if (player != null) {
              int balance = this.pointsAPI.look(player.getUniqueId());
              this.plugin.getLogger().info("Retrieved balance for " + playerName + " via API fallback: " + balance);
              return balance;
            }
          }
        } catch (Exception e) {
          this.plugin.getLogger().warning("Could not get balance via API fallback for " + playerName + ": " + e.getMessage());
        }
        this.plugin.getLogger().warning("Could not determine balance for " + playerName + ", assuming 0");
        return 0;
      } else {
        return Bukkit.getScheduler().callSyncMethod((Plugin)this.plugin, () -> {
          String command = "points look " + playerName;
          try {
            boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            if (!success) {
              this.plugin.getLogger().warning("Balance command execution failed: " + command);
              return 0;
            }
            if (this.pointsAPI != null) {
              Player player = Bukkit.getPlayer(playerName);
              if (player != null) {
                int balance = this.pointsAPI.look(player.getUniqueId());
                this.plugin.getLogger().info("Retrieved balance for " + playerName + " via API fallback: " + balance);
                return balance;
              }
            }
            this.plugin.getLogger().warning("Could not determine balance for " + playerName + ", assuming 0");
            return 0;
          } catch (Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "Error executing balance command", e);
            return 0;
          }
        }).get();
      }
    } catch (Exception e) {
      this.plugin.getLogger().log(Level.SEVERE, "Error in balance command fallback for " + playerName, e);
      return 0;
    }
  }
  
  private static class BalanceCommandExecutor implements CommandExecutor {
    private int capturedBalance;
    
    private BalanceCommandExecutor() {
      this.capturedBalance = 0;
    }
    
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (args.length > 0)
        try {
          this.capturedBalance = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
          this.capturedBalance = 0;
        }  
      return true;
    }
    
    public int getCapturedBalance() {
      return this.capturedBalance;
    }
  }
  
  public void resetApiStatus() {
    this.apiWorking = true;
    this.plugin.getLogger().info("PlayerPoints API status reset to working");
  }
  
  public boolean testPlayerPointsIntegration() {
    // If using Vault for any operation, test Vault instead
    if ((this.config.useVaultForSlotPurchase() || this.config.useVaultForWithdrawalFees() || this.config.useVaultForDonorSlots()) && this.vaultEconomy != null) {
      try {
        this.plugin.getLogger().info("Vault economy test passed - using " + this.vaultEconomy.getName());
        return true;
      } catch (Exception e) {
        this.plugin.getLogger().severe("Vault economy test failed: " + e.getMessage());
        return false;
      }
    }
    
    // Test PlayerPoints
    try {
      PlayerPoints playerPoints = PlayerPoints.getInstance();
      if (playerPoints == null) {
        this.plugin.getLogger().severe("PlayerPoints instance is null!");
        return false;
      } 
      PlayerPointsAPI api = playerPoints.getAPI();
      if (api == null) {
        this.plugin.getLogger().severe("PlayerPoints API is null!");
        return false;
      } 
      try {
        api.look(UUID.randomUUID());
        this.apiWorking = true;
        this.plugin.getLogger().info("PlayerPoints API test passed - API is working");
        return true;
      } catch (Exception e) {
        this.plugin.getLogger().warning("PlayerPoints API test failed, will use command fallback: " + e.getMessage());
        this.apiWorking = false;
        return true;
      } 
    } catch (Exception e) {
      this.plugin.getLogger().log(Level.SEVERE, "PlayerPoints integration test failed", e);
      this.apiWorking = false;
      return false;
    } 
  }
}
