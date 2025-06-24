package dev.lsdmc;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import dev.lsdmc.StorageDataManager;
import dev.lsdmc.utils.Constants;
import dev.lsdmc.PlayerStorageData;

import java.util.ArrayList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Comparator;
import java.util.stream.Collectors;

public final class StorageInventoryManager {
    private final StorageSlots plugin;
    private final StorageConfig config;
    private final StorageDataManager dataManager;
    private final MiniMessage miniMessage;
    
    // Track open inventories to prevent conflicts
    private final Map<UUID, UUID> openInventories = new ConcurrentHashMap<>(); // Player UUID -> Storage Owner UUID

    public StorageInventoryManager(StorageSlots plugin, StorageConfig config, StorageDataManager dataManager) {
        this.plugin = plugin;
        this.config = config;
        this.dataManager = dataManager;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void openStorage(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        
        // Check if player already has storage open
        if (openInventories.containsKey(player.getUniqueId())) {
            return;
        }
        
        try {
            Inventory inv = createInventory(player, player.getUniqueId(), false);
            if (inv != null) {
                openInventories.put(player.getUniqueId(), player.getUniqueId());
                player.openInventory(inv);
                player.sendMessage(config.getMessage(Constants.Messages.STORAGE_OPENED));
            } else {
                player.sendMessage(config.getMessage("errors.inventory-creation-failed"));
            }
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Failed to open storage for " + player.getName() + ": " + e.getMessage())
                .color(Constants.Colors.ERROR));
            player.sendMessage(config.getMessage("errors.inventory-creation-failed"));
        }
    }

    public void openPlayerStorage(Player admin, UUID targetPlayerId) {
        if (admin == null || !admin.isOnline() || targetPlayerId == null) {
            return;
        }
        
        if (!admin.hasPermission(Constants.Permissions.ADMIN)) {
            admin.sendMessage(config.getMessage(Constants.Messages.NO_PERMISSION));
            return;
        }
        
        // Check if admin already has storage open
        if (openInventories.containsKey(admin.getUniqueId())) {
            return;
        }
        
        try {
            Inventory inv = createInventory(admin, targetPlayerId, true);
            if (inv != null) {
                openInventories.put(admin.getUniqueId(), targetPlayerId);
                admin.openInventory(inv);
                
                String targetName = Bukkit.getOfflinePlayer(targetPlayerId).getName();
                admin.sendMessage(config.getMessage(Constants.Messages.STORAGE_OPENED, 
                    Map.of("target", targetName != null ? targetName : targetPlayerId.toString())));
            } else {
                admin.sendMessage(config.getMessage("errors.inventory-creation-failed"));
            }
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Failed to open admin storage for " + admin.getName() + ": " + e.getMessage())
                .color(Constants.Colors.ERROR));
            admin.sendMessage(config.getMessage("errors.inventory-creation-failed"));
        }
    }

    private Inventory createInventory(Player viewer, UUID storageOwner, boolean isAdminView) {
        if (viewer == null || storageOwner == null) {
            return null;
        }
        
        try {
            int totalSlots = config.getStorageSlots();
            PlayerStorageData data = dataManager.getPlayerData(storageOwner);
            
            // Check if viewer has donor rank - only show second row if they do
            boolean isDonor = config.getHighestDonorRank(viewer).isPresent() || 
                             viewer.isOp() || 
                             viewer.hasPermission("storageslots.donor.*");
            
            // Use 1 row for non-donors, 2 rows for donors
            int rows = isDonor ? 2 : 1;
            int inventorySize = rows * 9;

            // Use proper MiniMessage formatting for title
            Component titleComponent;
            if (isAdminView) {
                String ownerName = Bukkit.getOfflinePlayer(storageOwner).getName();
                titleComponent = config.getMessage("gui.admin-storage-title", 
                    Map.of("target", ownerName != null ? ownerName : storageOwner.toString()));
            } else {
                titleComponent = config.getMessage("gui.storage-title", 
                    Map.of("player", viewer.getName()));
            }

            Inventory inv = Bukkit.createInventory(null, inventorySize, titleComponent);

            // Add regular slots (first 9 slots)
            for (int i = 0; i < Math.min(totalSlots, 9) && i < inv.getSize(); i++) {
                if (data.hasSlotUnlocked(i)) {
                    ItemStack item = data.getItem(i);
                    if (item != null && !item.getType().isAir()) {
                        // Add withdrawal fee information to item lore if not a donor slot
                        ItemStack displayItem = addWithdrawalFeeToItem(item.clone(), viewer, i, false);
                        inv.setItem(i, displayItem);
                    }
                } else if (!isAdminView) {
                    boolean canBuyNext = canUnlockSlot(viewer, i, data);
                    ItemStack lockedItem = createLockedSlotItem(i, viewer, canBuyNext);
                    if (lockedItem != null) {
                        inv.setItem(i, lockedItem);
                    }
                } else {
                    ItemStack adminLockedItem = config.createLockedSlotItem(i, null);
                    if (adminLockedItem != null) {
                        inv.setItem(i, adminLockedItem);
                    }
                }
            }

            // Add donor slots only if the viewer is a donor
            if (isDonor) {
                addDonorSlots(inv, viewer, data, isAdminView);
            }

            return inv;
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error creating inventory: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            return null;
        }
    }
    
    /**
     * Check if a player can unlock a specific slot based on progression requirements
     */
    private boolean canUnlockSlot(Player player, int slot, PlayerStorageData data) {
        if (!config.isProgressionRequired()) {
            return true;
        }
        
        if (slot == 0) {
            return true; // First slot can always be unlocked
        }
        
        // Check if the previous slot is unlocked
        return data.hasSlotUnlocked(slot - 1);
    }
    
    /**
     * Add withdrawal fee information to an item's lore
     */
    private ItemStack addWithdrawalFeeToItem(ItemStack item, Player player, int slot, boolean isDonorSlot) {
        if (item == null || item.getType().isAir()) {
            return item;
        }
        
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return item;
            }
            
            List<Component> lore = meta.lore();
            if (lore == null) {
                lore = new ArrayList<>();
            }
            
            // Add withdrawal fee information
            if (isDonorSlot) {
                // Donor slots are always free
                lore.add(Component.empty());
                lore.add(miniMessage.deserialize("<gray>Withdrawal: <green><bold>FREE</bold></green>"));
            } else {
                // Get the actual withdrawal fee for this player (includes donor discounts)
                StorageConfig.WithdrawalFee withdrawalFee = config.getWithdrawalFee(player);
                int pointsFee = withdrawalFee.points();
                double moneyFee = withdrawalFee.money();
                
                lore.add(Component.empty());
                if (pointsFee > 0 && moneyFee > 0) {
                    // Both fees available - show points since it's preferred
                    lore.add(miniMessage.deserialize("<gray>Withdrawal: <yellow>" + pointsFee + " points</yellow>"));
                } else if (pointsFee > 0) {
                    lore.add(miniMessage.deserialize("<gray>Withdrawal: <yellow>" + pointsFee + " points</yellow>"));
                } else if (moneyFee > 0) {
                    lore.add(miniMessage.deserialize("<gray>Withdrawal: <yellow>" + String.format("%.0f", moneyFee) + " " + config.getCurrencyName() + "</yellow>"));
                } else {
                    // Free withdrawal (e.g., MVP/King donors)
                    lore.add(miniMessage.deserialize("<gray>Withdrawal: <green><bold>FREE</bold></green>"));
                }
            }
            
            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error adding withdrawal fee to item: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            return item;
        }
    }
    
    /**
     * Get the specific donor rank that unlocks a given donor slot
     * Slot 11 = Supporter (1st donor slot)
     * Slot 12 = VIP (2nd donor slot) 
     * Slots 13-14 = MVP (3rd-4th donor slots)
     * Slot 15 = King (5th donor slot)
     */
    private StorageConfig.DonorRank getDonorRankForSlot(int slot) {
        if (slot < 11 || slot > 15) {
            return null;
        }
        
        // Get all donor ranks sorted by slot count (ascending)
        List<StorageConfig.DonorRank> sortedRanks = config.getDonorRanks().stream()
            .sorted(Comparator.comparingInt(StorageConfig.DonorRank::slots))
            .collect(Collectors.toList());
        
        if (sortedRanks.isEmpty()) {
            return null;
        }
        
        int donorSlotIndex = slot - 11; // Convert to 0-based index (0-4)
        int slotsNeeded = donorSlotIndex + 1; // How many donor slots needed to unlock this slot
        
        // Find the rank that first provides enough slots for this slot
        for (StorageConfig.DonorRank rank : sortedRanks) {
            if (rank.slots() >= slotsNeeded) {
                if (config.getMessages().getBoolean("debug.enabled", false)) {
                    plugin.getComponentLogger().info(Component.text("Slot " + slot + " (donor slot #" + (donorSlotIndex + 1) + 
                        ") requires " + slotsNeeded + " donor slots, matched with rank: " + rank.name() + 
                        " (" + rank.slots() + " slots)"));
                }
                return rank;
            }
        }
        
        // Fallback to highest rank if no exact match
        StorageConfig.DonorRank fallback = sortedRanks.get(sortedRanks.size() - 1);
        if (config.getMessages().getBoolean("debug.enabled", false)) {
            plugin.getComponentLogger().warn(Component.text("No exact match for slot " + slot + 
                ", using fallback rank: " + fallback.name()));
        }
        return fallback;
    }

    private void addDonorSlots(Inventory inv, Player viewer, PlayerStorageData data, boolean isAdminView) {
        try {
            // Fill the entire second row with decorative items and donor slots
            
            // Slots 9-10: Simple decorative glass panes (remove withdrawal fee info)
            for (int i = 9; i <= 10; i++) {
                ItemStack decorativeItem = createSimpleDecorativeItem();
                if (decorativeItem != null) {
                    inv.setItem(i, decorativeItem);
                }
            }
            
            // Slots 11-15: Donor slots (5 total)
            var highestRankOpt = config.getHighestDonorRank(viewer);
            int availableDonorSlots = 0;
            StorageConfig.DonorRank highestRank = null;
            
            if (highestRankOpt.isPresent()) {
                highestRank = highestRankOpt.get();
                availableDonorSlots = Math.min(highestRank.slots(), 5); // Max 5 donor slots
                
                // Ensure OP players or those with donor.* get donor features
                if (viewer.isOp() || viewer.hasPermission("storageslots.donor.*")) {
                    data.setCurrentDonorRank(highestRank.name());
                    for (String feature : highestRank.features()) {
                        data.addDonorFeature(feature);
                    }
                }
            }
            
            // Fill donor slots (positions 11-15)
            for (int i = 0; i < 5; i++) {
                int slotIndex = 11 + i; // Slots 11, 12, 13, 14, 15
                
                // Get the specific donor rank that unlocks this slot
                StorageConfig.DonorRank slotSpecificRank = getDonorRankForSlot(slotIndex);
                
                if (i < availableDonorSlots && highestRank != null && slotSpecificRank != null) {
                    // Available donor slot - check if unlocked or show gold block
                    if (data.hasSlotUnlocked(slotIndex)) {
                        ItemStack item = data.getItem(slotIndex);
                        if (item != null && !item.getType().isAir()) {
                            // Add withdrawal fee info (free for donors)
                            ItemStack displayItem = addWithdrawalFeeToItem(item.clone(), viewer, slotIndex, true);
                            inv.setItem(slotIndex, displayItem);
                        }
                    } else if (!isAdminView) {
                        // Show gold block for available but locked donor slot - use the specific rank for this slot
                        ItemStack donorItem = createDonorSlotItem(slotIndex, slotSpecificRank, viewer);
                        if (donorItem != null) {
                            inv.setItem(slotIndex, donorItem);
                        }
                    }
                } else if (!isAdminView) {
                    // Unavailable donor slot - show bedrock with the specific rank info
                    ItemStack bedrockItem = createUnavailableDonorSlotItem(slotIndex, slotSpecificRank);
                    if (bedrockItem != null) {
                        inv.setItem(slotIndex, bedrockItem);
                    }
                }
            }
            
            // Slots 16-17: More simple decorative glass panes
            for (int i = 16; i <= 17; i++) {
                ItemStack decorativeItem = createSimpleDecorativeItem();
                if (decorativeItem != null) {
                    inv.setItem(i, decorativeItem);
                }
            }
            
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error adding donor slots: " + e.getMessage())
                .color(Constants.Colors.ERROR));
        }
    }

    private ItemStack createLockedSlotItem(int slot, Player player, boolean canBuyNext) {
        try {
            String requiredRank = config.getRequiredRank(slot);
            boolean hasRank = config.checkRankRequirement(player, requiredRank);
            boolean hasRequirements = hasRank && canBuyNext;
            
            return config.createLockedSlotItem(slot, player);
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error creating locked slot item: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            return new ItemStack(Material.BARRIER); // Fallback item
        }
    }

    private ItemStack createDonorSlotItem(int slot, StorageConfig.DonorRank donorRank, Player player) {
        try {
            Material material = Material.GOLD_BLOCK;
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return item;

            // Set display name with professional styling
            Component nameComponent = miniMessage.deserialize("<gold><bold>✦ Donor Slot #" + (slot - 10) + " ✦</bold></gold>");
            meta.displayName(nameComponent);

            // Set lore with enhanced professional styling
            List<Component> loreComponents = new ArrayList<>();
            loreComponents.add(miniMessage.deserialize("<gray>Donor Rank: " + donorRank.displayName()));
            loreComponents.add(Component.empty());
            loreComponents.add(miniMessage.deserialize("<yellow><bold>Features:</bold></yellow>"));
            
            // Add donor features with better formatting
            List<String> features = donorRank.features();
                    if (!features.isEmpty()) {
                for (String feature : features) {
                    String formattedFeature = formatFeatureName(feature);
                    loreComponents.add(miniMessage.deserialize("<gray>• <green>" + formattedFeature + "</green>"));
                        }
                    }
            
            loreComponents.add(Component.empty());
            double cost = config.getSlotCost(slot) * config.getDonorSlotCostMultiplier();
            loreComponents.add(miniMessage.deserialize("<gray>Cost: <yellow>" + String.format("%.0f", cost) + " " + config.getCurrencyName() + "</yellow>"));
            loreComponents.add(Component.empty());
            loreComponents.add(miniMessage.deserialize("<gold><bold>▶ Click to purchase! ◀</bold></gold>"));
            
            meta.lore(loreComponents);
            item.setItemMeta(meta);
            return item;
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error creating donor slot item: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            return new ItemStack(Material.GOLD_BLOCK); // Fallback item
        }
    }

    private ItemStack createUnavailableDonorSlotItem(int slot, StorageConfig.DonorRank requiredRank) {
        try {
            Material material = Material.BEDROCK;
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return item;

            // Set display name with professional styling
            Component nameComponent = miniMessage.deserialize("<dark_red><bold>Donor Slot #" + (slot - 10) + "</bold></dark_red>");
            meta.displayName(nameComponent);

            // Set lore with professional appearance showing the specific rank required
            List<Component> loreComponents = new ArrayList<>();
            if (requiredRank != null) {
                loreComponents.add(miniMessage.deserialize("<gray>Required Rank: " + requiredRank.displayName()));
                loreComponents.add(Component.empty());
                loreComponents.add(miniMessage.deserialize("<gray>This donor slot requires</gray>"));
                loreComponents.add(miniMessage.deserialize("<gray>" + requiredRank.displayName() + " <gray>rank or higher.</gray>"));
            } else {
                loreComponents.add(miniMessage.deserialize("<gray>This donor slot requires a higher"));
                loreComponents.add(miniMessage.deserialize("<gray>donor rank to unlock."));
            }
            loreComponents.add(Component.empty());
            loreComponents.add(miniMessage.deserialize("<gold><bold>Upgrade your donor rank</bold></gold>"));
            loreComponents.add(miniMessage.deserialize("<gold>to access this slot!</gold>"));
            meta.lore(loreComponents);
            
            item.setItemMeta(meta);
            return item;
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error creating unavailable donor slot item: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            return new ItemStack(Material.BEDROCK); // Fallback item
        }
    }
    
    // Overloaded method for backward compatibility
    private ItemStack createUnavailableDonorSlotItem(int slot) {
        return createUnavailableDonorSlotItem(slot, null);
    }

    private ItemStack createSimpleDecorativeItem() {
        try {
            ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return item;

            // Simple decorative name
            Component nameComponent = miniMessage.deserialize("<dark_gray>•</dark_gray>");
            meta.displayName(nameComponent);

            // No lore - keep it clean
            meta.lore(new ArrayList<>());
            
            item.setItemMeta(meta);
            return item;
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error creating simple decorative item: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            return new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        }
    }

    private String formatFeatureName(String feature) {
        return switch (feature) {
            case Constants.DonorFeatures.REDUCED_WITHDRAWAL_FEES -> "Reduced withdrawal fees";
            case Constants.DonorFeatures.FREE_WITHDRAWAL -> "Free item withdrawal";
            case Constants.DonorFeatures.FASTER_SLOT_ACCESS -> "Instant slot opening";
            case Constants.DonorFeatures.SLOT_DISCOUNT -> "10% discount on slot purchases";
            case Constants.DonorFeatures.PRIORITY_SUPPORT -> "Priority customer support";
            default -> feature.replace("_", " ").toLowerCase();
        };
    }

    public void saveInventoryContents(Inventory inv, UUID storageOwner) {
        if (inv == null || storageOwner == null) {
            return;
        }
        
        try {
            PlayerStorageData data = dataManager.getPlayerData(storageOwner);
            boolean hasChanges = false;
            Player ownerPlayer = Bukkit.getPlayer(storageOwner);
            
            // Track prohibited items to return to player
            List<ItemStack> prohibitedItems = new ArrayList<>();

            // Check regular storage slots (0-8) and donor slots (11-15)
            for (int i = 0; i < inv.getSize(); i++) {
                // Skip decorative slots (9-10, 16-17)
                if ((i >= 9 && i <= 10) || (i >= 16 && i <= 17)) {
                    continue;
                }
                
                // Only process unlocked slots
                if (data.hasSlotUnlocked(i)) {
                    ItemStack item = inv.getItem(i);
                    ItemStack currentItem = data.getItem(i);
                    
                    if (item != null && !item.getType().isAir()) {
                        if (config.isProhibitedItem(item)) {
                            // Don't save prohibited items - add them to return list
                            prohibitedItems.add(item.clone());
                            
                            // Clear the current stored item if it exists
                            if (currentItem != null) {
                                data.setItem(i, null);
                                hasChanges = true;
                            }
                            
                            plugin.getComponentLogger().warn(Component.text("Prohibited item detected in storage for player " + 
                                (ownerPlayer != null ? ownerPlayer.getName() : storageOwner.toString()) + 
                                ": " + item.getType() + " - returning to player"));
                        } else {
                            if (!itemsEqual(item, currentItem)) {
                                data.setItem(i, item.clone());
                                hasChanges = true;
                            }
                        }
                    } else {
                        if (currentItem != null) {
                            data.setItem(i, null);
                            hasChanges = true;
                        }
                    }
                }
            }
            
            // Return prohibited items to player if they're online
            if (!prohibitedItems.isEmpty() && ownerPlayer != null && ownerPlayer.isOnline()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (ItemStack prohibitedItem : prohibitedItems) {
                        // Try to add to player inventory, drop if full
                        var leftover = ownerPlayer.getInventory().addItem(prohibitedItem);
                        if (!leftover.isEmpty()) {
                            for (ItemStack drop : leftover.values()) {
                                ownerPlayer.getWorld().dropItemNaturally(ownerPlayer.getLocation(), drop);
                            }
                        }
                    }
                    
                    ownerPlayer.sendMessage(config.getMessage(Constants.Messages.PROHIBITED_ITEM_RETURNED, Map.of(
                        "count", String.valueOf(prohibitedItems.size())
                    )));
                });
            }

            if (hasChanges) {
                dataManager.markDirty();
                
                if (config.logTransactions()) {
                    String ownerName = ownerPlayer != null ? ownerPlayer.getName() : 
                        Bukkit.getOfflinePlayer(storageOwner).getName();
                    plugin.getComponentLogger().info(Component.text("Saved storage contents for " + 
                        (ownerName != null ? ownerName : storageOwner.toString())));
                }
            }
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error saving inventory contents: " + e.getMessage())
                .color(Constants.Colors.ERROR));
        }
    }

    public boolean isValidStorageInventory(String title) {
        if (title == null) return false;
        
        try {
            // Convert title to plain text for comparison
            String plainTitle = title.toLowerCase();
            
            // Check if the title contains storage-related keywords
            return plainTitle.contains("storage") || 
                   plainTitle.contains("admin view") ||
                   plainTitle.contains("backpack") ||
                   plainTitle.contains("slots");
        } catch (Exception e) {
            // Fallback to simple string matching
            return title.toLowerCase().contains("storage");
        }
    }

    public UUID getStorageOwner(String title, Player viewer) {
        if (viewer == null || title == null) {
            return null;
        }
        
        // Check our tracking map first
        UUID trackedOwner = openInventories.get(viewer.getUniqueId());
        if (trackedOwner != null) {
            return trackedOwner;
        }
        
        // Admin view detection
        if (viewer.hasPermission(Constants.Permissions.ADMIN) && title.contains(" - ")) {
            try {
                String[] parts = title.split(" - ");
                if (parts.length >= 2) {
                    String targetName = parts[1].replace("(Admin View)", "").trim();
                    
                    // Try online players first
                    Player target = Bukkit.getPlayer(targetName);
                    if (target != null) {
                        return target.getUniqueId();
                    }
                    
                    // Try offline players from our data
                    for (UUID id : dataManager.getAllStoredPlayerIds()) {
                        String offlineName = Bukkit.getOfflinePlayer(id).getName();
                        if (targetName.equals(offlineName)) {
                            return id;
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getComponentLogger().error(Component.text("Error parsing storage owner from title: " + e.getMessage())
                    .color(Constants.Colors.ERROR));
            }
        }
        
        return viewer.getUniqueId();
    }

    public void closeStorage(Player player) {
        if (player == null) return;
        
        // Simply remove the player from tracking - saving is handled by the close event
        openInventories.remove(player.getUniqueId());
    }

    public boolean hasStorageOpen(Player player) {
        return player != null && openInventories.containsKey(player.getUniqueId());
    }

    public void refreshInventory(Player player) {
        if (player == null || !hasStorageOpen(player)) {
            return;
        }

        try {
            UUID storageOwner = openInventories.get(player.getUniqueId());
            if (storageOwner == null) return;
            
            boolean isAdminView = !storageOwner.equals(player.getUniqueId());
            Inventory topInventory = player.getOpenInventory().getTopInventory();
            if (topInventory == null) return;
            
            PlayerStorageData data = dataManager.getPlayerData(storageOwner);

            // Check if current inventory size matches expected size
            boolean isDonor = config.getHighestDonorRank(player).isPresent() || 
                             player.isOp() || 
                             player.hasPermission("storageslots.donor.*");
            int expectedSize = isDonor ? 18 : 9;
            
            // If inventory size doesn't match, we need to recreate it
            if (topInventory.getSize() != expectedSize) {
                player.closeInventory();
                // Reopen with correct size
                if (isAdminView) {
                    openPlayerStorage(player, storageOwner);
                } else {
                    openStorage(player);
                }
                return;
            }

            // Update regular slots (0-8)
            for (int i = 0; i < Math.min(config.getStorageSlots(), 9) && i < topInventory.getSize(); i++) {
                ItemStack currentItem = topInventory.getItem(i);
                ItemStack newItem = null;

                if (data.hasSlotUnlocked(i)) {
                    ItemStack storedItem = data.getItem(i);
                    if (storedItem != null && !storedItem.getType().isAir()) {
                        newItem = addWithdrawalFeeToItem(storedItem.clone(), player, i, false);
                    }
                } else if (!isAdminView) {
                    boolean canBuyNext = canUnlockSlot(player, i, data);
                    newItem = createLockedSlotItem(i, player, canBuyNext);
                } else {
                    newItem = config.createLockedSlotItem(i, null);
                }

                // Only update if the item has changed
                if (!itemsEqual(currentItem, newItem)) {
                    topInventory.setItem(i, newItem);
                }
            }
            
            // Update donor slots if they exist (inventory size is 18)
            if (isDonor && topInventory.getSize() >= 18) {
                // Update donor slot area (11-15)
                var highestRankOpt = config.getHighestDonorRank(player);
                if (highestRankOpt.isPresent()) {
                    StorageConfig.DonorRank highestRank = highestRankOpt.get();
                    int availableDonorSlots = Math.min(highestRank.slots(), 5);
                    
                    for (int i = 0; i < 5; i++) {
                        int slotIndex = 11 + i;
                        ItemStack currentItem = topInventory.getItem(slotIndex);
                        ItemStack newItem = null;
                        
                        // Get the specific donor rank that unlocks this slot
                        StorageConfig.DonorRank slotSpecificRank = getDonorRankForSlot(slotIndex);
                        
                        if (i < availableDonorSlots && slotSpecificRank != null) {
                            if (data.hasSlotUnlocked(slotIndex)) {
                                ItemStack storedItem = data.getItem(slotIndex);
                                if (storedItem != null && !storedItem.getType().isAir()) {
                                    newItem = addWithdrawalFeeToItem(storedItem.clone(), player, slotIndex, true);
                                }
                            } else if (!isAdminView) {
                                // Use the specific rank for this slot
                                newItem = createDonorSlotItem(slotIndex, slotSpecificRank, player);
                            }
                        } else if (!isAdminView) {
                            newItem = createUnavailableDonorSlotItem(slotIndex, slotSpecificRank);
                        }
                        
                        if (!itemsEqual(currentItem, newItem)) {
                            topInventory.setItem(slotIndex, newItem);
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error refreshing inventory: " + e.getMessage())
                .color(Constants.Colors.ERROR));
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
            ItemMeta meta1 = item1.getItemMeta();
            ItemMeta meta2 = item2.getItemMeta();
            
            if (meta1 == null || meta2 == null) return false;
            
            if (meta1.hasDisplayName() != meta2.hasDisplayName()) return false;
            if (meta1.hasDisplayName() && 
                !meta1.getDisplayName().equals(meta2.getDisplayName())) {
                return false;
            }
        }

        return true;
    }

    public void updateSlotInOpenInventory(Player player, int slot) {
        if (player == null || !hasStorageOpen(player)) {
            return;
        }

        try {
            UUID storageOwner = openInventories.get(player.getUniqueId());
            if (storageOwner == null) return;
            
            boolean isAdminView = !storageOwner.equals(player.getUniqueId());
            Inventory topInventory = player.getOpenInventory().getTopInventory();
            if (topInventory == null || slot >= topInventory.getSize()) return;
            
            PlayerStorageData data = dataManager.getPlayerData(storageOwner);

            ItemStack newItem = null;
            if (data.hasSlotUnlocked(slot)) {
                ItemStack storedItem = data.getItem(slot);
                if (storedItem != null && !storedItem.getType().isAir()) {
                    // Check if this is a donor slot
                    boolean isDonorSlot = slot >= 11 && slot <= 15;
                    newItem = addWithdrawalFeeToItem(storedItem.clone(), player, slot, isDonorSlot);
                }
            } else if (!isAdminView) {
                // Handle different slot types
                if (slot >= 11 && slot <= 15) {
                    // Donor slot - use the specific rank that unlocks this slot
                    StorageConfig.DonorRank slotSpecificRank = getDonorRankForSlot(slot);
                    var highestDonorRank = config.getHighestDonorRank(player);
                    
                    if (highestDonorRank.isPresent() && slotSpecificRank != null) {
                        int donorSlotIndex = slot - 11;
                        if (donorSlotIndex < highestDonorRank.get().slots()) {
                            // Player has access to this slot - show it with the specific rank info
                            newItem = createDonorSlotItem(slot, slotSpecificRank, player);
                        } else {
                            // Player doesn't have access - show unavailable with specific rank required
                            newItem = createUnavailableDonorSlotItem(slot, slotSpecificRank);
                        }
                    } else {
                        // No donor rank or no specific rank found
                        newItem = createUnavailableDonorSlotItem(slot, slotSpecificRank);
                    }
                } else {
                    // Regular slot
                    boolean canBuyNext = canUnlockSlot(player, slot, data);
                newItem = createLockedSlotItem(slot, player, canBuyNext);
                }
            } else {
                newItem = config.createLockedSlotItem(slot, null);
            }

            topInventory.setItem(slot, newItem);
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error updating slot in inventory: " + e.getMessage())
                .color(Constants.Colors.ERROR));
        }
    }
    
    // Cleanup method to be called on plugin disable
    public void cleanup() {
        openInventories.clear();
    }
}
