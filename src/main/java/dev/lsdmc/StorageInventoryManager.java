package dev.lsdmc;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.UUID;
import java.util.List;
import org.bukkit.Material;

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

        // Add regular slots
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

        // Add donor slots if enabled and viewer has donor rank
        if (config.isDonorEnabled() && !isAdminView) {
            String highestRank = null;
            int maxSlots = 0;
            for (String rank : config.getDonorRanks()) {
                if (viewer.hasPermission(config.getDonorRankPermission(rank))) {
                    int slots = config.getDonorRankSlots(rank);
                    if (slots > maxSlots) {
                        maxSlots = slots;
                        highestRank = rank;
                    }
                }
            }

            if (highestRank != null) {
                int donorSlots = config.getDonorRankSlots(highestRank);
                int startSlot = config.areDonorSlotsSeparate() ? totalSlots : 0;
                int endSlot = config.areDonorSlotsSeparate() ? totalSlots + donorSlots : donorSlots;

                for (int i = startSlot; i < endSlot; i++) {
                    if (data.hasSlotUnlocked(i)) {
                        ItemStack item = data.getItem(i);
                        if (item != null) {
                            inv.setItem(i, item.clone());
                        }
                    } else {
                        inv.setItem(i, createDonorSlotItem(i, highestRank, viewer));
                    }
                }
            }
        }

        return inv;
    }

    private ItemStack createLockedSlotItem(int slot, Player player, boolean canBuyNext) {
        String requiredRank = config.getRequiredRank(slot);
        boolean hasRank = config.checkRankRequirement(player, requiredRank);
        boolean hasRequirements = hasRank && canBuyNext;
        double cost = config.getSlotCost(slot);

        return config.getLockedSlotItem(slot, hasRequirements, player, plugin.getEconomyManager().getCurrencyName(), cost);
    }

    private ItemStack createDonorSlotItem(int slot, String donorRank, Player player) {
        Material material = config.getDonorSlotMaterial();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(config.getDonorSlotName(slot));

        List<String> features = config.getDonorRankFeatures(donorRank);
        List<String> lore = config.getDonorSlotLore(donorRank, features);

        // Add purchase information if slots are purchasable
        if (config.areDonorSlotsPurchasable()) {
            double cost = config.getSlotCost(slot) * config.getDonorSlotCostMultiplier();
            lore.add("");
            lore.add("&7Cost: &e" + plugin.getEconomyManager().formatCurrency(cost) + " " + 
                    plugin.getEconomyManager().getCurrencyName());
            lore.add("&eClick to purchase!");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
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
        return title != null && title.equals(config.getStorageTitle(null));
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
        if (player.getOpenInventory() == null || 
            !isValidStorageInventory(player.getOpenInventory().getTitle())) {
            return;
        }

        UUID storageOwner = getStorageOwner(player.getOpenInventory().getTitle(), player);
        boolean isAdminView = !storageOwner.equals(player.getUniqueId());
        Inventory topInventory = player.getOpenInventory().getTopInventory();
        PlayerStorageData data = dataManager.getPlayerData(storageOwner);

        // Only update slots that need updating
        for (int i = 0; i < config.getStorageSlots(); i++) {
            ItemStack currentItem = topInventory.getItem(i);
            ItemStack newItem = null;

            if (data.hasSlotUnlocked(i)) {
                newItem = data.getItem(i);
                if (newItem != null) {
                    newItem = newItem.clone();
                }
            } else if (!isAdminView) {
                boolean canBuyNext = !config.isProgressionRequired() || i == 0 || data.hasSlotUnlocked(i - 1);
                newItem = createLockedSlotItem(i, player, canBuyNext);
            } else {
                newItem = config.getLockedSlotDisplayItem(i);
            }

            // Only update if the item has changed
            if (!itemsEqual(currentItem, newItem)) {
                topInventory.setItem(i, newItem);
            }
        }
    }

    private boolean itemsEqual(ItemStack item1, ItemStack item2) {
        if (item1 == item2) return true;
        if (item1 == null || item2 == null) return false;
        
        // Quick check for type and amount
        if (item1.getType() != item2.getType() || item1.getAmount() != item2.getAmount()) {
            return false;
        }

        // Check for custom name
        if (item1.hasItemMeta() != item2.hasItemMeta()) return false;
        if (item1.hasItemMeta()) {
            if (!item1.getItemMeta().hasDisplayName() != !item2.getItemMeta().hasDisplayName()) return false;
            if (item1.getItemMeta().hasDisplayName() && 
                !item1.getItemMeta().getDisplayName().equals(item2.getItemMeta().getDisplayName())) {
                return false;
            }
        }

        return true;
    }

    public void updateSlotInOpenInventory(Player player, int slot) {
        if (player.getOpenInventory() == null || 
            !isValidStorageInventory(player.getOpenInventory().getTitle())) {
            return;
        }

        UUID storageOwner = getStorageOwner(player.getOpenInventory().getTitle(), player);
        boolean isAdminView = !storageOwner.equals(player.getUniqueId());
        Inventory topInventory = player.getOpenInventory().getTopInventory();
        PlayerStorageData data = dataManager.getPlayerData(storageOwner);

        ItemStack newItem = null;
        if (data.hasSlotUnlocked(slot)) {
            newItem = data.getItem(slot);
            if (newItem != null) {
                newItem = newItem.clone();
            }
        } else if (!isAdminView) {
            boolean canBuyNext = !config.isProgressionRequired() || slot == 0 || data.hasSlotUnlocked(slot - 1);
            newItem = createLockedSlotItem(slot, player, canBuyNext);
        } else {
            newItem = config.getLockedSlotDisplayItem(slot);
        }

        topInventory.setItem(slot, newItem);
    }
}