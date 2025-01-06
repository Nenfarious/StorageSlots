package dev.lsdmc;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.UUID;

public class StorageInventoryManager {
    private final StorageSlots plugin;
    private final Config config;
    private final StorageDataManager dataManager;

    public StorageInventoryManager(StorageSlots plugin, Config config, StorageDataManager dataManager) {
        this.plugin = plugin;
        this.config = config;
        this.dataManager = dataManager;
    }

    public void openStorage(Player player) {
        Inventory inv = createInventory(player, player.getUniqueId(), false);
        player.openInventory(inv);
    }

    public void openPlayerStorage(Player admin, UUID targetPlayerId) {
        Inventory inv = createInventory(admin, targetPlayerId, true);
        admin.openInventory(inv);
    }

    private Inventory createInventory(Player viewer, UUID storageOwner, boolean isAdminView) {
        int totalSlots = config.getStorageSlots();
        int rows = Math.min(6, (int) Math.ceil(totalSlots / 9.0));

        String title = config.getStorageTitle(viewer);
        if (isAdminView) {
            String ownerName = Bukkit.getOfflinePlayer(storageOwner).getName();
            title += " - " + (ownerName != null ? ownerName : storageOwner.toString());
        }

        Inventory inv = Bukkit.createInventory(null, rows * 9, title);
        PlayerStorageData data = dataManager.getPlayerData(storageOwner);

        for (int i = 0; i < totalSlots; i++) {
            if (data.hasSlotUnlocked(i)) {
                ItemStack item = data.getItem(i);
                if (item != null) {
                    inv.setItem(i, item.clone());
                }
            } else if (!isAdminView) {
                boolean canBuyNext = !config.isProgressionRequired() || i == 0 || data.hasSlotUnlocked(i - 1);
                inv.setItem(i, createLockedSlotItem(i, viewer, canBuyNext));
            } else {
                inv.setItem(i, config.getLockedSlotDisplayItem(i));
            }
        }

        return inv;
    }

    private ItemStack createLockedSlotItem(int slot, Player player, boolean canBuyNext) {
        String requiredRank = config.getRequiredRank(slot);
        boolean hasRank = config.checkRankRequirement(player, requiredRank);
        boolean hasRequirements = hasRank && canBuyNext;
        double cost = config.getSlotCost(slot);

        return config.getLockedSlotItem(slot, hasRequirements, player, plugin.getCurrencyName(), cost);
    }

    public void saveInventoryContents(Inventory inv, UUID storageOwner) {
        PlayerStorageData data = dataManager.getPlayerData(storageOwner);

        for (int i = 0; i < config.getStorageSlots(); i++) {
            if (data.hasSlotUnlocked(i)) {
                ItemStack item = inv.getItem(i);
                if (item != null && !item.getType().isAir()) {
                    if (!config.isProhibitedItem(item)) {
                        data.setItem(i, item.clone());
                    }
                } else {
                    data.setItem(i, null);
                }
            }
        }

        dataManager.markDirty();
    }

    public boolean isValidStorageInventory(String title) {
        return title != null && title.startsWith(config.getStorageTitle(null));
    }

    public UUID getStorageOwner(String title, Player viewer) {
        if (viewer.hasPermission("storageslots.admin") && title.contains(" - ")) {
            String targetName = title.split(" - ")[1];
            Player target = Bukkit.getPlayer(targetName);
            if (target != null) {
                return target.getUniqueId();
            }
            // Try to find offline player
            for (UUID id : dataManager.getAllStoredPlayerIds()) {
                String offlineName = Bukkit.getOfflinePlayer(id).getName();
                if (targetName.equals(offlineName)) {
                    return id;
                }
            }
        }
        return viewer.getUniqueId();
    }

    public void refreshInventory(Player player) {
        if (player.getOpenInventory() != null &&
                isValidStorageInventory(player.getOpenInventory().getTitle())) {
            UUID storageOwner = getStorageOwner(player.getOpenInventory().getTitle(), player);
            boolean isAdminView = !storageOwner.equals(player.getUniqueId());
            Inventory newInv = createInventory(player, storageOwner, isAdminView);
            player.getOpenInventory().getTopInventory().setContents(newInv.getContents());
        }
    }
}