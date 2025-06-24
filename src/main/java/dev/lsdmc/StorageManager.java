package dev.lsdmc;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.UUID;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import dev.lsdmc.utils.Constants;
import net.kyori.adventure.text.Component;

public class StorageManager implements Listener {
    private final StorageSlots plugin;
    private final StorageConfig config;
    private final StorageDataManager dataManager;
    private final StorageInventoryManager inventoryManager;
    
    // Track withdrawal cooldowns (player UUID -> slot -> cooldown end time)
    private final Map<UUID, Map<Integer, Long>> withdrawalCooldowns = new ConcurrentHashMap<>();
    
    /**
     * Get withdrawal cooldown duration from config
     */
    private long getWithdrawalCooldownMs() {
        return config.getWithdrawalCooldownMs();
    }
    
    /**
     * Check if withdrawal cooldown should apply to this player
     */
    private boolean shouldApplyWithdrawalCooldown(Player player) {
        if (!config.isWithdrawalCooldownEnabled()) {
            return false;
        }
        
        // Check bypass permission
        if (config.canBypassWithdrawalCooldown(player)) {
            return false;
        }
        
        // Check if OPs should be affected (via plugin config)
        if (player.isOp() && !plugin.getConfig().getBoolean("withdrawal-cooldown.apply-to-ops", true)) {
            return false;
        }
        
        return true;
    }

    public StorageManager(StorageSlots plugin, StorageConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.dataManager = new StorageDataManager(plugin);
        this.inventoryManager = new StorageInventoryManager(plugin, config, dataManager);

        setupAutoSave();
    }

    private void setupAutoSave() {
        if (!config.isAutoSaveEnabled()) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (dataManager.isSavePending()) {
                    dataManager.saveData();
                }
            }
        }.runTaskTimer(plugin, config.getAutoSaveInterval() * 20L, config.getAutoSaveInterval() * 20L);
    }

    public void openStorage(Player player) {
        if (!plugin.getSafezoneManager().isInSafezone(player)) {
            player.sendMessage(config.getSafezoneMessage());
            return;
        }
        
        // Mark that player has seen storage (for notification system)
        plugin.markPlayerSeenStorage(player);
        
        inventoryManager.openStorage(player);
    }

    public void openPlayerStorage(Player admin, UUID targetPlayerId) {
        if (!plugin.getSafezoneManager().isInSafezone(admin)) {
            admin.sendMessage(config.getSafezoneMessage());
            return;
        }
        inventoryManager.openPlayerStorage(admin, targetPlayerId);
    }

    public UUID findPlayerUUID(String name) {
        // Input validation
        if (name == null || name.trim().isEmpty() || name.length() > 16) {
            return null;
        }

        // Sanitize input - only allow alphanumeric and underscore
        String cleanName = name.trim().replaceAll("[^a-zA-Z0-9_]", "");
        if (cleanName.isEmpty()) {
            return null;
        }

        // Try online players first - most efficient
        Player target = Bukkit.getPlayer(cleanName);
        if (target != null) {
            return target.getUniqueId();
        }

        // Try offline players from our data - more efficient than Bukkit's offline player lookup
        for (UUID id : dataManager.getAllStoredPlayerIds()) {
            String offlineName = Bukkit.getOfflinePlayer(id).getName();
            if (cleanName.equalsIgnoreCase(offlineName)) {
                return id;
            }
        }

        // Last resort: try Bukkit's offline player data
        @SuppressWarnings("deprecation")
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(cleanName);
        if (offlinePlayer.hasPlayedBefore()) {
            return offlinePlayer.getUniqueId();
        }

        return null;
    }

    public void giveSlot(Player admin, String targetName, int slot) {
        if (!config.isValidSlot(slot)) {
            admin.sendMessage(config.getMessage("invalid-slot"));
            return;
        }

        UUID targetId = findPlayerUUID(targetName);
        if (targetId == null) {
            admin.sendMessage(config.getMessage("player-not-found"));
            return;
        }

        PlayerStorageData data = dataManager.getPlayerData(targetId);
        if (data.hasSlotUnlocked(slot)) {
            admin.sendMessage(config.getMessage("slot-already-owned", Map.of(
                "player", targetName,
                "slot", String.valueOf(slot + 1)
            )));
            return;
        }

        data.unlockSlot(slot);
        dataManager.markDirty();
        admin.sendMessage(config.getMessage("slot-given", Map.of(
            "player", targetName,
            "slot", String.valueOf(slot + 1)
        )));
    }

    public void removeSlot(Player admin, String targetName, int slot) {
        if (!config.isValidSlot(slot)) {
            admin.sendMessage(config.getMessage("invalid-slot"));
            return;
        }

        UUID targetId = findPlayerUUID(targetName);
        if (targetId == null) {
            admin.sendMessage(config.getMessage("player-not-found"));
            return;
        }

        PlayerStorageData data = dataManager.getPlayerData(targetId);
        if (!data.hasSlotUnlocked(slot)) {
            admin.sendMessage(config.getMessage("slot-not-owned", Map.of(
                "player", targetName,
                "slot", String.valueOf(slot + 1)
            )));
            return;
        }

        if (data.getItem(slot) != null) {
            admin.sendMessage(config.getMessage("slot-not-empty", Map.of(
                "player", targetName,
                "slot", String.valueOf(slot + 1)
            )));
            return;
        }

        data.lockSlot(slot);
        dataManager.markDirty();
        admin.sendMessage(config.getMessage("slot-removed", Map.of(
            "player", targetName,
            "slot", String.valueOf(slot + 1)
        )));
    }

    public void listSlots(Player admin, String targetName) {
        UUID targetId = findPlayerUUID(targetName);
        if (targetId == null) {
            admin.sendMessage(config.getMessage("player-not-found"));
            return;
        }

        PlayerStorageData data = dataManager.getPlayerData(targetId);
        Set<Integer> slots = data.getUnlockedSlots();

        if (slots.isEmpty()) {
            admin.sendMessage(config.getMessage("no-slots-owned", Map.of(
                "player", targetName
            )));
            return;
        }

        admin.sendMessage(config.getMessage("slots-list-header", Map.of(
            "player", targetName
        )));

        for (int slot : slots) {
            ItemStack item = data.getItem(slot);
            String status = item != null ? item.getType().toString() : "Empty";
            admin.sendMessage(config.getMessage("slots-list-format", Map.of(
                "slot", String.valueOf(slot + 1),
                "status", status
            )));
        }
    }

    public void purchaseSlot(Player player, int slot) {
        if (!config.isValidSlot(slot)) {
            player.sendMessage(config.getMessage("invalid-slot"));
            return;
        }

        if (!player.isOnline()) {
            return;
        }

        PlayerStorageData data = dataManager.getPlayerData(player.getUniqueId());

        if (data.getUnlockedSlotCount() >= config.getMaxSlotsPerPlayer()) {
            player.sendMessage(config.getMessage("max-slots-reached"));
            return;
        }

        if (data.hasSlotUnlocked(slot)) {
            player.sendMessage(config.getMessage("already-unlocked"));
            return;
        }

        // Check if this is a donor slot
        boolean isDonorSlot = isDonorSlot(player, slot);
        if (isDonorSlot) {
            handleDonorSlotPurchase(player, slot, data);
            return;
        }

        // Check progression requirement with proper logic
        if (config.isProgressionRequired() && !canPlayerUnlockSlot(player, slot, data)) {
            player.sendMessage(config.getMessage("previous-slot-required"));
            return;
        }

        String requiredRank = config.getRequiredRank(slot);
        if (!config.hasRankRequirement(player, requiredRank)) {
            player.sendMessage(config.getMessage("rank-required", Map.of(
                "rank", requiredRank
            )));
            return;
        }

        double baseCost = config.getSlotCost(slot);
        
        // Apply donor discount if applicable
        final double cost = data.hasDonorFeature("slot_discount") ? baseCost * 0.9 : baseCost;
        
        if (config.logTransactions()) {
            plugin.getLogger().info("Player " + player.getName() + " attempting to purchase slot " + (slot + 1) + 
                " for " + cost + " points (base: " + baseCost + ", discount: " + data.hasDonorFeature("slot_discount") + ")");
        }
        
        plugin.getEconomyManager().takeMoney(player, cost).thenAccept(success -> {
            if (config.logTransactions()) {
                plugin.getLogger().info("Player " + player.getName() + " slot purchase result: " + success);
            }
            if (!success || !player.isOnline()) {
                if (player.isOnline()) {
                    plugin.getEconomyManager().getBalance(player).thenAccept(balance -> {
                        player.sendMessage(config.getMessage("insufficient-funds", Map.of(
                            "cost", plugin.getEconomyManager().formatCurrency(cost),
                            "currency", plugin.getEconomyManager().getCurrencyName(),
                            "balance", plugin.getEconomyManager().formatCurrency(balance)
                        )));
                    });
                }
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }

                data.unlockSlot(slot);
                dataManager.markDirty();

                if (config.logTransactions()) {
                    plugin.getLogger().info(String.format("Player %s purchased slot %d for %s",
                            player.getName(), slot + 1, plugin.getEconomyManager().formatCurrency(cost)));
                }

                if (player.getOpenInventory() != null &&
                        inventoryManager.isValidStorageInventory(player.getOpenInventory().getTitle())) {
                    inventoryManager.updateSlotInOpenInventory(player, slot);
                }

                player.sendMessage(config.getMessage("slot-purchased", Map.of(
                    "slot", String.valueOf(slot + 1),
                    "cost", plugin.getEconomyManager().formatCurrency(cost),
                    "currency", plugin.getEconomyManager().getCurrencyName()
                )));
            });
        });
    }

    private boolean isDonorSlot(Player player, int slot) {
        if (!config.isDonorEnabled()) {
            return false;
        }
        
        // Donor slots are now slots 11-15 (positions 3-7 of second row)
        if (slot < 11 || slot > 15) {
            return false;
        }
        
        // Check if player has any donor rank
        var highestRankOpt = config.getHighestDonorRank(player);
        if (highestRankOpt.isEmpty()) {
            return false;
        }
        
        StorageConfig.DonorRank highestRank = highestRankOpt.get();
        int availableDonorSlots = Math.min(highestRank.slots(), 5); // Max 5 donor slots
        int donorSlotIndex = slot - 11; // Convert to 0-based donor slot index (0-4)
        
        // Check if this specific donor slot is available to the player's rank
        return donorSlotIndex < availableDonorSlots;
    }

    private void handleDonorSlotPurchase(Player player, int slot, PlayerStorageData data) {
        if (!config.areDonorSlotsPurchasable()) {
            player.sendMessage(config.getMessage("donor-feature-unavailable"));
            return;
        }
        
        // Find the highest donor rank the player has
        StorageConfig.DonorRank highestRank = null;
        int maxSlots = 0;
        
        // Check if player is OP or has donor.* permission
        if (player.isOp() || player.hasPermission("storageslots.donor.*")) {
            var highestRankOpt = config.getHighestDonorRank(player);
            if (highestRankOpt.isPresent()) {
                highestRank = highestRankOpt.get();
                maxSlots = highestRank.slots();
            }
        } else {
            // Check individual donor ranks
            for (StorageConfig.DonorRank rank : config.getDonorRanks()) {
                if (player.hasPermission(rank.permission())) {
                    int slots = rank.slots();
                    if (slots > maxSlots) {
                        maxSlots = slots;
                        highestRank = rank;
                    }
                }
            }
        }
        
        if (highestRank == null) {
            player.sendMessage(config.getMessage("donor-feature-unavailable"));
            return;
        }
        
        final StorageConfig.DonorRank donorRankToUse = highestRank;
        double baseCost = config.getSlotCost(slot);
        double cost = baseCost * config.getDonorSlotCostMultiplier();
        
        if (config.logTransactions()) {
            plugin.getLogger().info("Player " + player.getName() + " attempting to purchase donor slot " + (slot + 1) + 
                " for " + cost + " points (base: " + baseCost + ", multiplier: " + config.getDonorSlotCostMultiplier() + ")");
        }
        
        plugin.getEconomyManager().takeMoney(player, cost).thenAccept(success -> {
            if (config.logTransactions()) {
                plugin.getLogger().info("Player " + player.getName() + " donor slot purchase result: " + success);
            }
            if (!success || !player.isOnline()) {
                if (player.isOnline()) {
                    plugin.getEconomyManager().getBalance(player).thenAccept(balance -> {
                        player.sendMessage(config.getMessage("insufficient-funds", Map.of(
                            "cost", plugin.getEconomyManager().formatCurrency(cost),
                            "currency", plugin.getEconomyManager().getCurrencyName(),
                            "balance", plugin.getEconomyManager().formatCurrency(balance)
                        )));
                    });
                }
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                data.unlockDonorSlot(slot);
                data.setCurrentDonorRank(donorRankToUse.name());
                for (String feature : donorRankToUse.features()) {
                    data.addDonorFeature(feature);
                }
                dataManager.markDirty();
                if (config.logTransactions()) {
                    plugin.getLogger().info(String.format("Player %s purchased donor slot %d for %s",
                            player.getName(), slot + 1, plugin.getEconomyManager().formatCurrency(cost)));
                }
                if (player.getOpenInventory() != null &&
                    inventoryManager.isValidStorageInventory(player.getOpenInventory().getTitle())) {
                    inventoryManager.updateSlotInOpenInventory(player, slot);
                }
                player.sendMessage(config.getMessage("slot-purchased", Map.of(
                    "slot", String.valueOf(slot + 1),
                    "cost", plugin.getEconomyManager().formatCurrency(cost),
                    "currency", plugin.getEconomyManager().getCurrencyName()
                )));
            });
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (!inventoryManager.isValidStorageInventory(title)) return;

        // Don't cancel the event by default - let normal inventory operations work
        
        // Get clicked inventory and slot
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) return;
        
        int slot = event.getSlot();
        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();
        
        PlayerStorageData data = dataManager.getPlayerData(player.getUniqueId());
        if (data == null) return;

        // If clicking in the storage inventory (top inventory)
        if (clickedInventory.equals(event.getView().getTopInventory())) {
            // Check if clicking on a locked slot item (glass pane, gold block, bedrock)
            if (clickedItem != null && isLockedSlotItem(clickedItem)) {
                event.setCancelled(true);
                handleSlotPurchase(player, slot, data);
                return;
            }
            
            // Check if clicking on bedrock (unavailable donor slot)
            if (clickedItem != null && clickedItem.getType() == Material.BEDROCK) {
                event.setCancelled(true);
                return; // Do nothing for bedrock clicks
            }
            
            // Check if the slot is unlocked for item operations
            if (!data.hasSlotUnlocked(slot)) {
                event.setCancelled(true);
                return;
            }
            
            // Check for prohibited items being placed into storage
            if (cursorItem != null && !cursorItem.getType().isAir()) {
                if (config.isProhibitedItem(cursorItem)) {
                    event.setCancelled(true);
                    player.sendMessage(config.getMessage(Constants.Messages.PROHIBITED_ITEM));
                    return;
                }
                
                // Check max items per slot
                if (cursorItem.getAmount() > config.getMaxItemsPerSlot()) {
                    event.setCancelled(true);
                    player.sendMessage(config.getMessage(Constants.Messages.MAX_ITEMS_PER_SLOT, Map.of(
                        "max", String.valueOf(config.getMaxItemsPerSlot())
                    )));
                    return;
                }
            }
            
            // Handle withdrawal fees and cooldowns for taking items out
            if (clickedItem != null && !clickedItem.getType().isAir() && 
                (cursorItem == null || cursorItem.getType().isAir()) && 
                event.getAction() == InventoryAction.PICKUP_ALL) {
                
                // Check if cooldown should apply to this player
                boolean applyCooldown = shouldApplyWithdrawalCooldown(player);
                
                if (applyCooldown) {
                    // Clear expired cooldowns first
                    clearExpiredCooldowns(player);
                    
                    // Check if player is on cooldown for this slot
                    if (isOnWithdrawalCooldown(player, slot)) {
                        event.setCancelled(true);
                        long remainingMs = withdrawalCooldowns.get(player.getUniqueId()).get(slot) - System.currentTimeMillis();
                        double remainingSeconds = remainingMs / 1000.0;
                        player.sendMessage(Component.text("Please wait " + String.format("%.1f", remainingSeconds) + " seconds before withdrawing from this slot.")
                            .color(Constants.Colors.ERROR));
                        return;
                    }
                    
                    // Set cooldown for this withdrawal (applies to all withdrawals, free or paid)
                    setWithdrawalCooldown(player, slot);
                }
                
                // Check if this is a donor slot or player has free withdrawal
                boolean isDonorSlot = slot >= 11 && slot <= 15;
                boolean hasFreeWithdrawal = data.hasDonorFeature("free_withdrawal") || isDonorSlot;
                
                if (!hasFreeWithdrawal) {
                    // Paid withdrawal - cancel event and handle through fee system
                    event.setCancelled(true);
                    handleItemWithdrawal(player, slot, clickedItem, data);
                    return;
                } else {
                    // Free withdrawal - cancel event and handle directly
                    event.setCancelled(true);
                    completeWithdrawal(player, slot, clickedItem, data);
                    return;
                }
            }
        } else if (clickedInventory.equals(event.getView().getBottomInventory())) {
            // Player is clicking in their own inventory - check for shift-click to storage
            if (event.isShiftClick() && clickedItem != null && !clickedItem.getType().isAir()) {
                // Check for prohibited items being shift-clicked into storage
                if (config.isProhibitedItem(clickedItem)) {
                    event.setCancelled(true);
                    player.sendMessage(config.getMessage(Constants.Messages.PROHIBITED_ITEM));
                    return;
                }
                
                // Check max items per slot for shift-click
                if (clickedItem.getAmount() > config.getMaxItemsPerSlot()) {
                    event.setCancelled(true);
                    player.sendMessage(config.getMessage(Constants.Messages.MAX_ITEMS_PER_SLOT, Map.of(
                        "max", String.valueOf(config.getMaxItemsPerSlot())
                    )));
                    return;
                }
            }
        }
        
        // For all other valid clicks, allow normal operations
        // The inventory will auto-save when closed
    }
    
    private boolean isLockedSlotItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        
        // Check if it's one of our locked slot materials
        return item.getType().name().contains("GLASS_PANE") || 
               item.getType() == org.bukkit.Material.BARRIER ||
               item.getType() == org.bukkit.Material.GOLD_BLOCK;
        // Note: BEDROCK is intentionally excluded - clicking it should do nothing
    }
    
    private void handleSlotPurchase(Player player, int slot, PlayerStorageData data) {
        // Check if slot is already unlocked
        if (data.hasSlotUnlocked(slot)) {
            return;
        }
        
        // Check progression requirement with proper logic
        if (config.isProgressionRequired() && !canPlayerUnlockSlot(player, slot, data)) {
            player.sendMessage(config.getMessage("previous-slot-required"));
            return;
        }
        
        // Check rank requirement
        String requiredRank = config.getRequiredRank(slot);
        if (!config.hasRankRequirement(player, requiredRank)) {
            player.sendMessage(config.getMessage("rank-required", Map.of(
                "rank", requiredRank
            )));
            return;
        }
        
        // Proceed with purchase
        purchaseSlot(player, slot);
    }
    
    /**
     * Check if a player can unlock a specific slot based on progression requirements
     */
    private boolean canPlayerUnlockSlot(Player player, int slot, PlayerStorageData data) {
        if (!config.isProgressionRequired()) {
            return true;
        }
        
        if (slot == 0) {
            return true; // First slot can always be unlocked
        }
        
        // For regular slots (0-8), check if previous slot is unlocked
        if (slot < 9) {
            return data.hasSlotUnlocked(slot - 1);
        }
        
        // For donor slots (11-15), check if player has donor access
        if (slot >= 11 && slot <= 15) {
            var donorRank = config.getHighestDonorRank(player);
            if (donorRank.isEmpty()) {
                return false;
            }
            
            int donorSlotIndex = slot - 11; // Convert to 0-based index (0-4)
            return donorSlotIndex < donorRank.get().slots();
        }
        
        return false;
    }
    
    /**
     * Check if a player is on withdrawal cooldown for a specific slot
     */
    private boolean isOnWithdrawalCooldown(Player player, int slot) {
        Map<Integer, Long> playerCooldowns = withdrawalCooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) {
            return false;
        }
        
        Long cooldownEnd = playerCooldowns.get(slot);
        if (cooldownEnd == null) {
            return false;
        }
        
        return System.currentTimeMillis() < cooldownEnd;
    }
    
    /**
     * Set withdrawal cooldown for a player and slot
     */
    private void setWithdrawalCooldown(Player player, int slot) {
        withdrawalCooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
            .put(slot, System.currentTimeMillis() + getWithdrawalCooldownMs());
    }
    
    /**
     * Clear expired cooldowns for a player
     */
    private void clearExpiredCooldowns(Player player) {
        Map<Integer, Long> playerCooldowns = withdrawalCooldowns.get(player.getUniqueId());
        if (playerCooldowns != null) {
            long currentTime = System.currentTimeMillis();
            playerCooldowns.entrySet().removeIf(entry -> entry.getValue() < currentTime);
            
            if (playerCooldowns.isEmpty()) {
                withdrawalCooldowns.remove(player.getUniqueId());
            }
        }
    }

    private void handleItemWithdrawal(Player player, int slot, ItemStack item, PlayerStorageData data) {
        // Check if player has free withdrawal
        if (data.hasDonorFeature("free_withdrawal")) {
            if (config.logTransactions()) {
                plugin.getLogger().info("Player " + player.getName() + " has free withdrawal (donor feature)");
            }
            completeWithdrawal(player, slot, item, data);
            return;
        }
        
        // Get withdrawal fees based on player's rank and donor status
        StorageConfig.WithdrawalFee withdrawalFee = config.getWithdrawalFee(player);
        int pointsFee = withdrawalFee.points();
        double moneyFee = withdrawalFee.money();
        
        if (config.logTransactions()) {
            plugin.getLogger().info("Player " + player.getName() + " withdrawal fee: " + pointsFee + " points, " + moneyFee + " money");
        }
        
        // If both fees are 0, allow free withdrawal
        if (pointsFee == 0 && moneyFee == 0) {
            if (config.logTransactions()) {
                plugin.getLogger().info("Player " + player.getName() + " has free withdrawal (no fees)");
            }
            completeWithdrawal(player, slot, item, data);
            return;
        }
        
        // Show withdrawal fee message
        if (pointsFee > 0 && moneyFee > 0) {
            player.sendMessage(config.getMessage("withdrawal-fee", Map.of(
                "fee", plugin.getEconomyManager().formatCurrency(moneyFee),
                "currency", plugin.getEconomyManager().getCurrencyName(),
                "points", String.valueOf(pointsFee)
            )));
        } else if (pointsFee > 0) {
            player.sendMessage(config.getMessage("withdrawal-fee", Map.of(
                "fee", String.valueOf(pointsFee),
                "currency", "points",
                "points", String.valueOf(pointsFee)
            )));
        } else if (moneyFee > 0) {
            player.sendMessage(config.getMessage("withdrawal-fee", Map.of(
                "fee", plugin.getEconomyManager().formatCurrency(moneyFee),
                "currency", plugin.getEconomyManager().getCurrencyName(),
                "points", "0"
            )));
        }
        
        final int finalPointsFee = pointsFee;
        final double finalMoneyFee = moneyFee;
        
        // Try points first if available
        if (pointsFee > 0) {
            plugin.getEconomyManager().getBalance(player).thenAccept(balance -> {
                if (config.logTransactions()) {
                    plugin.getLogger().info("Player " + player.getName() + " balance: " + balance + ", required: " + finalPointsFee);
                }
                if (balance >= finalPointsFee) {
                    executeWithdrawal(player, slot, item, data, finalPointsFee, true);
                } else if (finalMoneyFee > 0) {
                    // Try money if points insufficient
                    plugin.getEconomyManager().getBalance(player).thenAccept(moneyBalance -> {
                        if (config.logTransactions()) {
                            plugin.getLogger().info("Player " + player.getName() + " money balance: " + moneyBalance + ", required: " + finalMoneyFee);
                        }
                        if (moneyBalance >= finalMoneyFee) {
                            executeWithdrawal(player, slot, item, data, finalMoneyFee, false);
                        } else {
                            player.sendMessage(config.getMessage("insufficient-withdrawal-funds", Map.of(
                                "fee", plugin.getEconomyManager().formatCurrency(finalMoneyFee),
                                "currency", plugin.getEconomyManager().getCurrencyName(),
                                "points", String.valueOf(finalPointsFee)
                            )));
                        }
                    });
                } else {
                    player.sendMessage(config.getMessage("insufficient-points", Map.of(
                        "points", String.valueOf(finalPointsFee),
                        "balance", String.valueOf(balance.intValue())
                    )));
                }
            });
        } else if (moneyFee > 0) {
            // Only money fee
            plugin.getEconomyManager().getBalance(player).thenAccept(moneyBalance -> {
                if (config.logTransactions()) {
                    plugin.getLogger().info("Player " + player.getName() + " money balance: " + moneyBalance + ", required: " + finalMoneyFee);
                }
                if (moneyBalance >= finalMoneyFee) {
                    executeWithdrawal(player, slot, item, data, finalMoneyFee, false);
                } else {
                    player.sendMessage(config.getMessage("insufficient-withdrawal-funds", Map.of(
                        "fee", plugin.getEconomyManager().formatCurrency(finalMoneyFee),
                        "currency", plugin.getEconomyManager().getCurrencyName(),
                        "balance", plugin.getEconomyManager().formatCurrency(moneyBalance)
                    )));
                }
            });
        }
    }

    private void executeWithdrawal(Player player, int slot, ItemStack item, PlayerStorageData data, double fee, boolean usePoints) {
        if (usePoints) {
            if (config.logTransactions()) {
                plugin.getLogger().info("Player " + player.getName() + " executing withdrawal with " + (int)fee + " points");
            }
            plugin.getEconomyManager().takePoints(player, (int)fee).thenAccept(success -> {
                if (config.logTransactions()) {
                    plugin.getLogger().info("Player " + player.getName() + " points withdrawal result: " + success);
                }
                if (!success || !player.isOnline()) {
                    if (player.isOnline()) {
                        plugin.getEconomyManager().getBalance(player).thenAccept(balance -> {
                            player.sendMessage(config.getMessage("insufficient-points", Map.of(
                                "points", String.valueOf((int)fee),
                                "balance", String.valueOf(balance.intValue())
                            )));
                        });
                    }
                    return;
                }
                completeWithdrawal(player, slot, item, data);
            });
        } else {
            if (config.logTransactions()) {
                plugin.getLogger().info("Player " + player.getName() + " executing withdrawal with " + fee + " money");
            }
            plugin.getEconomyManager().takeMoney(player, fee).thenAccept(success -> {
                if (config.logTransactions()) {
                    plugin.getLogger().info("Player " + player.getName() + " money withdrawal result: " + success);
                }
                if (!success || !player.isOnline()) {
                    if (player.isOnline()) {
                        plugin.getEconomyManager().getBalance(player).thenAccept(balance -> {
                            player.sendMessage(config.getMessage("insufficient-funds", Map.of(
                                "cost", plugin.getEconomyManager().formatCurrency(fee),
                                "currency", plugin.getEconomyManager().getCurrencyName(),
                                "balance", plugin.getEconomyManager().formatCurrency(balance)
                            )));
                        });
                    }
                    return;
                }
                completeWithdrawal(player, slot, item, data);
            });
        }
    }

    private void completeWithdrawal(Player player, int slot, ItemStack item, PlayerStorageData data) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            
            // Get the original item from storage (without withdrawal fee lore)
            ItemStack originalItem = data.getItem(slot);
            if (originalItem == null) {
                originalItem = item; // Fallback to the clicked item
            }
            
            // Clear the slot in storage
            data.setItem(slot, null);
            dataManager.markDirty();
            
            // Give the original item (without lore modifications) to the player
            player.getInventory().addItem(originalItem.clone());
            
            if (config.logTransactions()) {
                plugin.getLogger().info(String.format("Player %s withdrew item from slot %d",
                        player.getName(), slot + 1));
            }
            if (player.getOpenInventory() != null &&
                inventoryManager.isValidStorageInventory(player.getOpenInventory().getTitle())) {
                inventoryManager.updateSlotInOpenInventory(player, slot);
            }
            player.sendMessage(config.getMessage("item-withdrawn", Map.of(
                "slot", String.valueOf(slot + 1)
            )));
        });
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (!inventoryManager.isValidStorageInventory(title)) return;

        // Check if player is in safezone
        if (!plugin.getSafezoneManager().isInSafezone(player)) {
            event.setCancelled(true);
            player.closeInventory();
            player.sendMessage(config.getSafezoneMessage());
            return;
        }

        UUID storageOwner = inventoryManager.getStorageOwner(title, player);
        PlayerStorageData data = dataManager.getPlayerData(storageOwner);

        for (int slot : event.getRawSlots()) {
            if (slot < config.getStorageSlots()) {
                if (!data.hasSlotUnlocked(slot)) {
                    event.setCancelled(true);
                    return;
                }

                for (ItemStack item : event.getNewItems().values()) {
                    if (item != null && !item.getType().isAir()) {
                        // Check prohibited items (no donor bypass anymore)
                        if (config.isProhibitedItem(item)) {
                            event.setCancelled(true);
                            player.sendMessage(config.getMessage(Constants.Messages.PROHIBITED_ITEM));
                            return;
                        }

                        // Check max items per slot (no donor bypass anymore)
                        if (item.getAmount() > config.getMaxItemsPerSlot()) {
                            event.setCancelled(true);
                            player.sendMessage(config.getMessage(Constants.Messages.MAX_ITEMS_PER_SLOT, Map.of(
                                "max", String.valueOf(config.getMaxItemsPerSlot())
                            )));
                            return;
                        }
                    }
                }
            }
        }

        if (!event.isCancelled()) {
            dataManager.markDirty();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        
        // Only process if the player has storage tracked as open
        if (!inventoryManager.hasStorageOpen(player)) {
            return;
        }
        
        String title = event.getView().getTitle();
        
        // Verify this is actually a storage inventory being closed
        if (!inventoryManager.isValidStorageInventory(title)) {
            return;
        }

        // Save the storage contents and clean up tracking
        UUID storageOwner = inventoryManager.getStorageOwner(title, player);
        inventoryManager.saveInventoryContents(event.getInventory(), storageOwner);
        inventoryManager.closeStorage(player);
    }

    public void saveAllData() {
        dataManager.saveData();
    }

    public void resetAllStorage() {
        dataManager.resetAllData();
    }

    public void resetPlayerStorage(UUID playerId) {
        dataManager.resetPlayerData(playerId);
    }
    
    public StorageDataManager getDataManager() {
        return dataManager;
    }
    
    public StorageInventoryManager getInventoryManager() {
        return inventoryManager;
    }
}