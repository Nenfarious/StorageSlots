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
        new BukkitRunnable() {
            @Override
            public void run() {
                if (dataManager.isSavePending()) {
                    dataManager.saveData();
                }
            }
        }.runTaskTimer(plugin, 6000L, 6000L);
    }

    public void openStorage(Player player) {
        inventoryManager.openStorage(player);
    }

    public void openPlayerStorage(Player admin, UUID targetPlayerId) {
        inventoryManager.openPlayerStorage(admin, targetPlayerId);
    }

    public UUID findPlayerUUID(String name) {
        // Try online players first
        Player target = Bukkit.getPlayer(name);
        if (target != null) {
            return target.getUniqueId();
        }

        // Try offline players from our data
        for (UUID id : dataManager.getAllStoredPlayerIds()) {
            String offlineName = Bukkit.getOfflinePlayer(id).getName();
            if (name.equalsIgnoreCase(offlineName)) {
                return id;
            }
        }

        // Try to load from offline player data
        @SuppressWarnings("deprecation")
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(name);
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

        PlayerStorageData data = dataManager.getPlayerData(player.getUniqueId());
        if (data.hasSlotUnlocked(slot)) {
            player.sendMessage(config.getMessage("already-unlocked"));
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
        plugin.takeMoney(player, cost).thenAccept(success -> {
            if (success) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        data.unlockSlot(slot);
                        dataManager.markDirty();

                        // Clear any locked slot items from the inventory
                        if (player.getOpenInventory() != null &&
                                inventoryManager.isValidStorageInventory(player.getOpenInventory().getTitle())) {
                            Inventory topInventory = player.getOpenInventory().getTopInventory();
                            // Clear any remaining locked slot items
                            for (int i = 0; i <= slot; i++) {
                                ItemStack item = topInventory.getItem(i);
                                if (item != null && (
                                        item.isSimilar(config.getLockedSlotItem(i, true, player, plugin.getCurrencyName(), config.getSlotCost(i))) ||
                                                item.isSimilar(config.getLockedSlotItem(i, false, player, plugin.getCurrencyName(), config.getSlotCost(i))) ||
                                                item.isSimilar(config.getLockedSlotDisplayItem(i))
                                )) {
                                    topInventory.setItem(i, null);
                                }
                            }
                        }

                        String message = config.getMessage("slot-purchased")
                                .replace("%slot%", String.valueOf(slot + 1))
                                .replace("%cost%", plugin.formatCurrency(cost))
                                .replace("%currency%", plugin.getCurrencyName());
                        player.sendMessage(message);

                        // Close inventory after purchase
                        if (player.getOpenInventory() != null) {
                            player.closeInventory();
                        }
                    }
                }.runTask(plugin);
            } else {
                String message = config.getMessage("insufficient-funds")
                        .replace("%cost%", plugin.formatCurrency(cost))
                        .replace("%currency%", plugin.getCurrencyName())
                        .replace("%balance%", plugin.formatCurrency(plugin.getPlayerBalance(player)));
                player.sendMessage(message);
            }
        });
    }

    public void resetAllStorage() {
        dataManager.resetAllData();
    }

    public void resetPlayerStorage(UUID playerId) {
        dataManager.resetPlayerData(playerId);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (!inventoryManager.isValidStorageInventory(title)) return;

        UUID storageOwner = inventoryManager.getStorageOwner(title, player);
        PlayerStorageData data = dataManager.getPlayerData(storageOwner);
        boolean isAdminView = !storageOwner.equals(player.getUniqueId());

        int slot = event.getRawSlot();
        if (slot >= 0 && slot < config.getStorageSlots()) {
            if (!data.hasSlotUnlocked(slot)) {
                event.setCancelled(true);
                // Only process purchase attempts for non-admin views on direct clicks
                if (!isAdminView && event.getClickedInventory() != null &&
                        event.getClickedInventory().equals(event.getView().getTopInventory()) &&
                        event.getClick() == ClickType.LEFT) {
                    purchaseSlot(player, slot);
                }
                return;
            }

            // Handle item placement
            if (event.getClickedInventory() != null &&
                    event.getClickedInventory().equals(event.getView().getTopInventory())) {
                ItemStack cursor = event.getCursor();
                if (cursor != null && !cursor.getType().isAir() &&
                        config.isProhibitedItem(cursor)) {
                    event.setCancelled(true);
                    player.sendMessage(config.getMessage("prohibited-item"));
                    return;
                }
            }

            dataManager.markDirty();
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (!inventoryManager.isValidStorageInventory(title)) return;

        UUID storageOwner = inventoryManager.getStorageOwner(title, player);
        PlayerStorageData data = dataManager.getPlayerData(storageOwner);

        for (int slot : event.getRawSlots()) {
            if (slot < config.getStorageSlots()) {
                if (!data.hasSlotUnlocked(slot)) {
                    event.setCancelled(true);
                    return;
                }

                for (ItemStack item : event.getNewItems().values()) {
                    if (config.isProhibitedItem(item)) {
                        event.setCancelled(true);
                        player.sendMessage(config.getMessage("prohibited-item"));
                        return;
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
}