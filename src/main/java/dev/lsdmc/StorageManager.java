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

public class StorageManager implements Listener {
    private final StorageSlots plugin;
    private final Config config;
    private final StorageDataManager dataManager;
    private final StorageInventoryManager inventoryManager;

    public StorageManager(StorageSlots plugin, Config config) {
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
            admin.sendMessage(config.getMessage("slot-already-owned")
                    .replace("%player%", targetName)
                    .replace("%slot%", String.valueOf(slot + 1)));
            return;
        }

        data.unlockSlot(slot);
        dataManager.markDirty();
        admin.sendMessage(config.getMessage("slot-given")
                .replace("%player%", targetName)
                .replace("%slot%", String.valueOf(slot + 1)));
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
            admin.sendMessage(config.getMessage("slot-not-owned")
                    .replace("%player%", targetName)
                    .replace("%slot%", String.valueOf(slot + 1)));
            return;
        }

        // Check if slot has items
        if (data.getItem(slot) != null) {
            admin.sendMessage(config.getMessage("slot-not-empty")
                    .replace("%player%", targetName)
                    .replace("%slot%", String.valueOf(slot + 1)));
            return;
        }

        data.lockSlot(slot);
        dataManager.markDirty();
        admin.sendMessage(config.getMessage("slot-removed")
                .replace("%player%", targetName)
                .replace("%slot%", String.valueOf(slot + 1)));
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
            admin.sendMessage(config.getMessage("no-slots-owned")
                    .replace("%player%", targetName));
            return;
        }

        admin.sendMessage(config.getMessage("slots-list-header")
                .replace("%player%", targetName));

        for (int slot : slots) {
            ItemStack item = data.getItem(slot);
            String status = item != null ? item.getType().toString() : "Empty";
            admin.sendMessage(config.getMessage("slots-list-format")
                    .replace("%slot%", String.valueOf(slot + 1))
                    .replace("%status%", status));
        }
    }

    public void purchaseSlot(Player player, int slot) {
        if (!config.isValidSlot(slot)) {
            player.sendMessage(config.getMessage("invalid-slot"));
            return;
        }

        // Validate player state first
        if (!player.isOnline()) {
            return;
        }

        PlayerStorageData data = dataManager.getPlayerData(player.getUniqueId());
        
        // Check max slots per player
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

        // Check progression requirement
        if (config.isProgressionRequired() && slot > 0 && !data.hasSlotUnlocked(slot - 1)) {
            player.sendMessage(config.getMessage("previous-slot-required"));
            return;
        }

        String requiredRank = config.getRequiredRank(slot);
        if (!config.checkRankRequirement(player, requiredRank)) {
            player.sendMessage(config.getMessage("rank-required")
                    .replace("%rank%", config.getRankDisplay(requiredRank)));
            return;
        }

        double cost = config.getSlotCost(slot);
        plugin.getEconomyManager().takeMoney(player, cost).thenAccept(success -> {
            if (!success || !player.isOnline()) {
                if (player.isOnline()) {
                    plugin.getEconomyManager().getBalance(player).thenAccept(balance -> {
                        String message = config.getMessage("insufficient-funds")
                                .replace("%cost%", plugin.getEconomyManager().formatCurrency(cost))
                                .replace("%currency%", plugin.getEconomyManager().getCurrencyName())
                                .replace("%balance%", plugin.getEconomyManager().formatCurrency(balance));
                        player.sendMessage(message);
                    });
                }
                return;
            }

            // Run on main thread to ensure thread safety
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }

                // Update data
                        data.unlockSlot(slot);
                        dataManager.markDirty();

                // Log transaction if enabled
                if (config.logTransactions()) {
                    plugin.getLogger().info(String.format("Player %s purchased slot %d for %s",
                            player.getName(), slot + 1, plugin.getEconomyManager().formatCurrency(cost)));
                }

                // Update inventory if open
                        if (player.getOpenInventory() != null &&
                                inventoryManager.isValidStorageInventory(player.getOpenInventory().getTitle())) {
                            Inventory topInventory = player.getOpenInventory().getTopInventory();
                    // Only update the specific slot that was purchased
                    topInventory.setItem(slot, null);
                }

                // Send success message
                String message = config.getMessage("slot-purchased")
                        .replace("%slot%", String.valueOf(slot + 1))
                        .replace("%cost%", plugin.getEconomyManager().formatCurrency(cost))
                        .replace("%currency%", plugin.getEconomyManager().getCurrencyName());
                player.sendMessage(message);
            });
        });
    }

    private boolean isDonorSlot(Player player, int slot) {
        if (!config.isDonorEnabled()) {
            return false;
        }

        // Check if player has any donor rank
        for (String rank : config.getDonorRanks()) {
            if (player.hasPermission(config.getDonorRankPermission(rank))) {
                // Check if this slot is within the donor rank's slot range
                int donorSlots = config.getDonorRankSlots(rank);
                if (config.areDonorSlotsSeparate()) {
                    // If separate, donor slots start after regular slots
                    return slot >= config.getStorageSlots() && 
                           slot < config.getStorageSlots() + donorSlots;
                } else {
                    // If not separate, donor slots are mixed with regular slots
                    return slot < donorSlots;
                }
            }
        }
        return false;
    }

    private void handleDonorSlotPurchase(Player player, int slot, PlayerStorageData data) {
        if (!config.areDonorSlotsPurchasable()) {
            player.sendMessage(config.getMessage("donor-feature-unavailable"));
            return;
        }

        // Find the highest donor rank the player has
        final String[] highestRank = {null};
        int maxSlots = 0;
        for (String rank : config.getDonorRanks()) {
            if (player.hasPermission(config.getDonorRankPermission(rank))) {
                int slots = config.getDonorRankSlots(rank);
                if (slots > maxSlots) {
                    maxSlots = slots;
                    highestRank[0] = rank;
                }
            }
        }

        if (highestRank[0] == null) {
            player.sendMessage(config.getMessage("donor-feature-unavailable"));
            return;
        }

        // Calculate cost with multiplier
        double baseCost = config.getSlotCost(slot);
        double cost = baseCost * config.getDonorSlotCostMultiplier();

        plugin.getEconomyManager().takeMoney(player, cost).thenAccept(success -> {
            if (!success || !player.isOnline()) {
                if (player.isOnline()) {
                    plugin.getEconomyManager().getBalance(player).thenAccept(balance -> {
                        String message = config.getMessage("insufficient-funds")
                                .replace("%cost%", plugin.getEconomyManager().formatCurrency(cost))
                                .replace("%currency%", plugin.getEconomyManager().getCurrencyName())
                                .replace("%balance%", plugin.getEconomyManager().formatCurrency(balance));
                        player.sendMessage(message);
                    });
                }
                return;
            }

            // Run on main thread to ensure thread safety
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }

                // Update data
                data.unlockDonorSlot(slot);
                data.setCurrentDonorRank(highestRank[0]);
                for (String feature : config.getDonorRankFeatures(highestRank[0])) {
                    data.addDonorFeature(feature);
                }
                dataManager.markDirty();

                // Log transaction if enabled
                if (config.logTransactions()) {
                    plugin.getLogger().info(String.format("Player %s purchased donor slot %d for %s",
                            player.getName(), slot + 1, plugin.getEconomyManager().formatCurrency(cost)));
                }

                // Update inventory if open
                if (player.getOpenInventory() != null && 
                    inventoryManager.isValidStorageInventory(player.getOpenInventory().getTitle())) {
                    Inventory topInventory = player.getOpenInventory().getTopInventory();
                    // Only update the specific slot that was purchased
                    topInventory.setItem(slot, null);
                }

                // Send success message
                String message = config.getMessage("slot-purchased")
                        .replace("%slot%", String.valueOf(slot + 1))
                        .replace("%cost%", plugin.getEconomyManager().formatCurrency(cost))
                        .replace("%currency%", plugin.getEconomyManager().getCurrencyName());
                player.sendMessage(message);

                // Send donor slot persist message if applicable
                if (config.doDonorSlotsPersist()) {
                    player.sendMessage(config.getMessage("donor-slot-persist"));
                }
            });
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (!inventoryManager.isValidStorageInventory(title)) return;

        event.setCancelled(true);

        if (event.getClickedInventory() == null || event.getClickedInventory().equals(player.getInventory())) {
            return;
        }

        int slot = event.getSlot();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null) {
            return;
        }

        PlayerStorageData data = dataManager.getPlayerData(player.getUniqueId());
        if (data == null) {
            return;
        }

        // Handle item withdrawal
        handleItemWithdrawal(player, slot, clickedItem, data);
    }

    private void handleItemWithdrawal(Player player, int slot, ItemStack item, PlayerStorageData data) {
        // Check if player has free withdrawal
        if (data.hasDonorFeature("free_withdrawal")) {
            player.sendMessage(config.getSafezoneMessage());
            return;
        }

        // Get withdrawal fee based on slot type
        String slotType = data.isDonorSlot(slot) ? "donor" : config.getSlotType(slot);
        int pointsFee = (int) config.getWithdrawalFeePoints(slotType);
        double moneyFee = config.getWithdrawalFeeMoney(slotType);

        // Show withdrawal fee message
        String feeMessage = config.getMessage("withdrawal-fee")
                .replace("%cost%", plugin.getEconomyManager().formatCurrency(moneyFee))
                .replace("%currency%", plugin.getEconomyManager().getCurrencyName())
                .replace("%points%", String.valueOf(pointsFee));
        player.sendMessage(feeMessage);

        // Check if player has enough points
        plugin.getEconomyManager().getBalance(player).thenAccept(balance -> {
            if (balance >= pointsFee) {
                // Execute withdrawal with points
                executeWithdrawal(player, slot, item, data, pointsFee, true);
            } else {
                // Check if player has enough money
                plugin.getEconomyManager().getBalance(player).thenAccept(moneyBalance -> {
                    if (moneyBalance >= moneyFee) {
                        // Execute withdrawal with money
                        executeWithdrawal(player, slot, item, data, moneyFee, false);
                    } else {
                        // Not enough funds
                        String message = config.getMessage("insufficient-withdrawal-funds")
                                .replace("%cost%", plugin.getEconomyManager().formatCurrency(moneyFee))
                                .replace("%currency%", plugin.getEconomyManager().getCurrencyName())
                                .replace("%points%", String.valueOf(pointsFee));
                        player.sendMessage(message);
                    }
                });
            }
        });
    }

    private void executeWithdrawal(Player player, int slot, ItemStack item, PlayerStorageData data, double fee, boolean usePoints) {
        // Check if player has enough funds
        if (usePoints) {
            plugin.getEconomyManager().takePoints(player, (int)fee).thenAccept(success -> {
                if (!success || !player.isOnline()) {
                    if (player.isOnline()) {
                        plugin.getEconomyManager().getBalance(player).thenAccept(balance -> {
                            String message = config.getMessage("insufficient-points")
                                    .replace("%cost%", String.valueOf((int)fee))
                                    .replace("%balance%", String.valueOf(balance.intValue()));
                            player.sendMessage(message);
                        });
                    }
                    return;
                }
                completeWithdrawal(player, slot, item, data);
            });
        } else {
            plugin.getEconomyManager().takeMoney(player, fee).thenAccept(success -> {
                if (!success || !player.isOnline()) {
                    if (player.isOnline()) {
                        plugin.getEconomyManager().getBalance(player).thenAccept(balance -> {
                            String message = config.getMessage("insufficient-funds")
                                    .replace("%cost%", plugin.getEconomyManager().formatCurrency(fee))
                                    .replace("%currency%", plugin.getEconomyManager().getCurrencyName())
                                    .replace("%balance%", plugin.getEconomyManager().formatCurrency(balance));
                            player.sendMessage(message);
                        });
                    }
                    return;
                }
                completeWithdrawal(player, slot, item, data);
            });
        }
    }

    private void completeWithdrawal(Player player, int slot, ItemStack item, PlayerStorageData data) {
        // Run on main thread to ensure thread safety
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            // Remove item from storage
            data.setItem(slot, null);
            dataManager.markDirty();

            // Give item to player
            player.getInventory().addItem(item);

            // Log transaction if enabled
            if (config.logTransactions()) {
                plugin.getLogger().info(String.format("Player %s withdrew item from slot %d",
                        player.getName(), slot + 1));
            }

            // Update inventory if open
            if (player.getOpenInventory() != null && 
                inventoryManager.isValidStorageInventory(player.getOpenInventory().getTitle())) {
                Inventory topInventory = player.getOpenInventory().getTopInventory();
                topInventory.setItem(slot, null);
            }

            // Send success message
            String message = config.getMessage("item-withdrawn")
                    .replace("%slot%", String.valueOf(slot + 1));
            player.sendMessage(message);
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
                        // Check prohibited items
                    if (config.isProhibitedItem(item)) {
                        event.setCancelled(true);
                        player.sendMessage(config.getMessage("prohibited-item"));
                        return;
                        }

                        // Check max items per slot
                        if (item.getAmount() > config.getMaxItemsPerSlot()) {
                            event.setCancelled(true);
                            player.sendMessage(config.getMessage("max-items-per-slot")
                                    .replace("%max%", String.valueOf(config.getMaxItemsPerSlot())));
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
        String title = event.getView().getTitle();
        if (!inventoryManager.isValidStorageInventory(title)) return;

        Player player = (Player) event.getPlayer();
        UUID storageOwner = inventoryManager.getStorageOwner(title, player);
        inventoryManager.saveInventoryContents(event.getInventory(), storageOwner);
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
}