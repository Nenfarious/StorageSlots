package dev.lsdmc;

import dev.lsdmc.utils.Constants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class StorageInventoryManager {
  private final StorageSlots plugin;
  
  private final StorageConfig config;
  
  private final StorageDataManager dataManager;
  
  private final MiniMessage miniMessage;
  
  private final Map<UUID, UUID> openInventories = new ConcurrentHashMap<>();
  
  public StorageInventoryManager(StorageSlots plugin, StorageConfig config, StorageDataManager dataManager) {
    this.plugin = plugin;
    this.config = config;
    this.dataManager = dataManager;
    this.miniMessage = MiniMessage.miniMessage();
  }
  
  public void openStorage(Player player) {
    if (player == null || !player.isOnline())
      return; 
    if (this.openInventories.containsKey(player.getUniqueId()))
      return; 
    try {
      Inventory inv = createInventory(player, player.getUniqueId(), false);
      if (inv != null) {
        this.openInventories.put(player.getUniqueId(), player.getUniqueId());
        player.openInventory(inv);
        player.sendMessage(this.config.getMessage("storage-opened"));
      } else {
        player.sendMessage(this.config.getMessage("errors.inventory-creation-failed"));
      } 
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Failed to open storage for " + player.getName() + ": " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      player.sendMessage(this.config.getMessage("errors.inventory-creation-failed"));
    } 
  }
  
  public void openPlayerStorage(Player admin, UUID targetPlayerId) {
    if (admin == null || !admin.isOnline() || targetPlayerId == null)
      return; 
    if (!admin.hasPermission("storageslots.admin")) {
      admin.sendMessage(this.config.getMessage("no-permission"));
      return;
    } 
    if (this.openInventories.containsKey(admin.getUniqueId()))
      return; 
    try {
      Inventory inv = createInventory(admin, targetPlayerId, true);
      if (inv != null) {
        this.openInventories.put(admin.getUniqueId(), targetPlayerId);
        admin.openInventory(inv);
        String targetName = Bukkit.getOfflinePlayer(targetPlayerId).getName();
        admin.sendMessage(this.config.getMessage("storage-opened", 
              Map.of("target", (targetName != null) ? targetName : targetPlayerId.toString())));
      } else {
        admin.sendMessage(this.config.getMessage("errors.inventory-creation-failed"));
      } 
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Failed to open admin storage for " + admin.getName() + ": " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      admin.sendMessage(this.config.getMessage("errors.inventory-creation-failed"));
    } 
  }
  
  private Inventory createInventory(Player viewer, UUID storageOwner, boolean isAdminView) {
    if (viewer == null || storageOwner == null)
      return null; 
    try {
      Component titleComponent;
      int totalSlots = this.config.getStorageSlots();
      PlayerStorageData data = this.dataManager.getPlayerData(storageOwner);
      boolean isDonor = !(!this.config.getHighestDonorRank(viewer).isPresent() && 
        !viewer.isOp() && 
        !viewer.hasPermission("storageslots.donor.*"));
      int rows = isDonor ? 2 : 1;
      int inventorySize = rows * 9;
      if (isAdminView) {
        String ownerName = Bukkit.getOfflinePlayer(storageOwner).getName();
        titleComponent = this.config.getMessage("gui.admin-storage-title", 
            Map.of("target", (ownerName != null) ? ownerName : storageOwner.toString()));
      } else {
        titleComponent = this.config.getMessage("gui.storage-title", 
            Map.of("player", viewer.getName()));
      } 
      Inventory inv = Bukkit.createInventory(null, inventorySize, titleComponent);
      for (int i = 0; i < Math.min(totalSlots, 9) && i < inv.getSize(); i++) {
        if (data.hasSlotUnlocked(i)) {
          ItemStack item = data.getItem(i);
          if (item != null && !item.getType().isAir()) {
            ItemStack displayItem = addWithdrawalFeeToItem(item.clone(), viewer, i, false);
            inv.setItem(i, displayItem);
          } 
        } else if (!isAdminView) {
          boolean canBuyNext = canUnlockSlot(viewer, i, data);
          ItemStack lockedItem = createLockedSlotItem(i, viewer, canBuyNext);
          if (lockedItem != null)
            inv.setItem(i, lockedItem); 
        } else {
          ItemStack adminLockedItem = this.config.createLockedSlotItem(i, null);
          if (adminLockedItem != null)
            inv.setItem(i, adminLockedItem); 
        } 
      } 
      if (isDonor)
        addDonorSlots(inv, viewer, data, isAdminView); 
      return inv;
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error creating inventory: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      return null;
    } 
  }
  
  private boolean canUnlockSlot(Player player, int slot, PlayerStorageData data) {
    if (!this.config.isProgressionRequired())
      return true; 
    if (slot == 0)
      return true; 
    return data.hasSlotUnlocked(slot - 1);
  }
  
  private ItemStack addWithdrawalFeeToItem(ItemStack item, Player player, int slot, boolean isDonorSlot) {
    if (item == null || item.getType().isAir())
      return item; 
    try {
      // IMPORTANT: Clone the item so we don't modify the original stored item
      ItemStack displayItem = item.clone();
      ItemMeta meta = displayItem.getItemMeta();
      if (meta == null)
        return displayItem; 
      List<Component> lore = meta.lore();
      if (lore == null)
        lore = new ArrayList<>(); 
      
      // Check if donor has specific fees configured, otherwise use regular rank-based fees
      boolean hasDonorSpecificFees = false;
      if (isDonorSlot) {
        Optional<StorageConfig.DonorRank> highestRankOpt = this.config.getHighestDonorRank(player);
        if (highestRankOpt.isPresent()) {
          String donorRankName = highestRankOpt.get().name();
          StorageConfig.WithdrawalFee donorFee = this.config.getDonorRankFees().get(donorRankName);
          hasDonorSpecificFees = (donorFee != null);
        }
      }
      
      if (isDonorSlot && hasDonorSpecificFees) {
        // Use donor-specific fees if configured
        Optional<StorageConfig.DonorRank> highestRankOpt = this.config.getHighestDonorRank(player);
        if (highestRankOpt.isPresent()) {
          String donorRankName = highestRankOpt.get().name();
          StorageConfig.WithdrawalFee donorFee = this.config.getDonorRankFees().get(donorRankName);
          lore.add(Component.empty());
          
          // Use the configured currency for withdrawal fees
          String withdrawalCurrency = this.plugin.getEconomyManager().getCurrencyName("withdrawal-fees");
          double fee = this.config.useVaultForWithdrawalFees() ? donorFee.money() : donorFee.points();
          
          if (fee > 0) {
            lore.add(this.miniMessage.deserialize("<gray>Withdrawal: <yellow>" + this.plugin.getEconomyManager().formatCurrency(fee, "withdrawal-fees") + " " + withdrawalCurrency + "</yellow>"));
          } else {
            lore.add(this.miniMessage.deserialize("<gray>Withdrawal: <green><bold>FREE</bold></green>"));
          }
        }
      } else {
        // Use regular rank-based fees (for both regular slots and donor slots without specific fees)
        StorageConfig.WithdrawalFee withdrawalFee = this.config.getWithdrawalFee(player);
        int pointsFee = withdrawalFee.points();
        double moneyFee = withdrawalFee.money();
        lore.add(Component.empty());
        
        // Use the configured currency for withdrawal fees
        String withdrawalCurrency = this.plugin.getEconomyManager().getCurrencyName("withdrawal-fees");
        double fee = this.config.useVaultForWithdrawalFees() ? moneyFee : pointsFee;
        
        if (fee > 0) {
          lore.add(this.miniMessage.deserialize("<gray>Withdrawal: <yellow>" + this.plugin.getEconomyManager().formatCurrency(fee, "withdrawal-fees") + " " + withdrawalCurrency + "</yellow>"));
        } else {
          lore.add(this.miniMessage.deserialize("<gray>Withdrawal: <green><bold>FREE</bold></green>"));
        } 
      } 
      meta.lore(lore);
      displayItem.setItemMeta(meta);
      return displayItem; // Return the cloned item with lore, original item unchanged
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error adding withdrawal fee to item: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      return item; // Return original item if there's an error
    } 
  }
  
  private StorageConfig.DonorRank getDonorRankForSlot(int slot) {
    if (!Constants.Slots.isDonorSlot(slot))
      return null; 
    List<StorageConfig.DonorRank> sortedRanks = (List<StorageConfig.DonorRank>)this.config.getDonorRanks().stream()
      .sorted(Comparator.comparingInt(StorageConfig.DonorRank::slots))
      .collect(Collectors.toList());
    if (sortedRanks.isEmpty())
      return null; 
    int donorSlotIndex = Constants.Slots.getDonorSlotIndex(slot);
    int slotsNeeded = donorSlotIndex + 1;
    for (StorageConfig.DonorRank rank : sortedRanks) {
      if (rank.slots() >= slotsNeeded) {
        if (this.config.getMessages().getBoolean("debug.enabled", false))
          this.plugin.getComponentLogger().info((Component)Component.text("Slot " + slot + " (donor slot #" + donorSlotIndex + 1 + ") requires " + 
                slotsNeeded + " donor slots, matched with rank: " + rank.name() + " (" + 
                rank.slots() + " slots)")); 
        return rank;
      } 
    } 
    StorageConfig.DonorRank fallback = sortedRanks.get(sortedRanks.size() - 1);
    if (this.config.getMessages().getBoolean("debug.enabled", false))
      this.plugin.getComponentLogger().warn((Component)Component.text("No exact match for slot " + slot + ", using fallback rank: " + 
            fallback.name())); 
    return fallback;
  }
  
  private void addDonorSlots(Inventory inv, Player viewer, PlayerStorageData data, boolean isAdminView) {
    try {
      for (int i = 9; i <= 10; i++) {
        ItemStack decorativeItem = createSimpleDecorativeItem();
        if (decorativeItem != null)
          inv.setItem(i, decorativeItem); 
      } 
      Optional<StorageConfig.DonorRank> highestRankOpt = this.config.getHighestDonorRank(viewer);
      int availableDonorSlots = 0;
      StorageConfig.DonorRank highestRank = null;
      if (highestRankOpt.isPresent()) {
        highestRank = highestRankOpt.get();
        availableDonorSlots = Math.min(highestRank.slots(), Constants.Slots.DONOR_SLOT_COUNT);
        if (viewer.isOp() || viewer.hasPermission("storageslots.donor.*")) {
          data.setCurrentDonorRank(highestRank.name());
        } 
      } 
      int j;
      for (j = 0; j < Constants.Slots.DONOR_SLOT_COUNT; j++) {
        int slotIndex = Constants.Slots.DONOR_SLOT_MIN + j;
        StorageConfig.DonorRank slotSpecificRank = getDonorRankForSlot(slotIndex);
        if (j < availableDonorSlots && highestRank != null && slotSpecificRank != null) {
          if (data.hasSlotUnlocked(slotIndex)) {
            ItemStack item = data.getItem(slotIndex);
            if (item != null && !item.getType().isAir()) {
              ItemStack displayItem = addWithdrawalFeeToItem(item.clone(), viewer, slotIndex, true);
              inv.setItem(slotIndex, displayItem);
            } 
          } else if (!isAdminView) {
            ItemStack donorItem = createDonorSlotItem(slotIndex, slotSpecificRank, viewer);
            if (donorItem != null)
              inv.setItem(slotIndex, donorItem); 
          } 
        } else if (!isAdminView) {
          ItemStack bedrockItem = createUnavailableDonorSlotItem(slotIndex, slotSpecificRank);
          if (bedrockItem != null)
            inv.setItem(slotIndex, bedrockItem); 
        } 
      } 
      for (j = 16; j <= 17; j++) {
        ItemStack decorativeItem = createSimpleDecorativeItem();
        if (decorativeItem != null)
          inv.setItem(j, decorativeItem); 
      } 
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error adding donor slots: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
    } 
  }
  
  private ItemStack createLockedSlotItem(int slot, Player player, boolean canBuyNext) {
    try {
      return this.config.createLockedSlotItem(slot, player);
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error creating locked slot item: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      return new ItemStack(Material.BARRIER);
    } 
  }
  
  private ItemStack createDonorSlotItem(int slot, StorageConfig.DonorRank donorRank, Player player) {
    try {
      Material material = Material.GOLD_BLOCK;
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta == null)
        return item; 
      Component nameComponent = this.miniMessage.deserialize("<gold><bold>✦ Donor Slot #" + (Constants.Slots.getDonorSlotIndex(slot) + 1) + " ✦</bold></gold>");
      meta.displayName(nameComponent);
      List<Component> loreComponents = new ArrayList<>();
      loreComponents.add(this.miniMessage.deserialize("<gray>Donor Rank: " + donorRank.displayName()));
      loreComponents.add(Component.empty());
      double cost = this.config.getSlotCost(slot) * this.config.getDonorSlotCostMultiplier();
      loreComponents.add(this.miniMessage.deserialize("<gray>Cost: <yellow>" + String.format("%.0f", new Object[] { Double.valueOf(cost) }) + " " + this.plugin.getEconomyManager().getCurrencyName("donor-slots") + "</yellow>"));
      loreComponents.add(Component.empty());
      loreComponents.add(this.miniMessage.deserialize("<gold><bold>▶ Click to purchase! ◀</bold></gold>"));
      meta.lore(loreComponents);
      item.setItemMeta(meta);
      return item;
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error creating donor slot item: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      return new ItemStack(Material.GOLD_BLOCK);
    } 
  }
  
  private ItemStack createUnavailableDonorSlotItem(int slot, StorageConfig.DonorRank requiredRank) {
    try {
      Material material = Material.BEDROCK;
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta == null)
        return item; 
      Component nameComponent = this.miniMessage.deserialize("<dark_red><bold>Donor Slot #" + (Constants.Slots.getDonorSlotIndex(slot) + 1) + "</bold></dark_red>");
      meta.displayName(nameComponent);
      List<Component> loreComponents = new ArrayList<>();
      if (requiredRank != null) {
        loreComponents.add(this.miniMessage.deserialize("<gray>Required Rank: " + requiredRank.displayName()));
        loreComponents.add(Component.empty());
        loreComponents.add(this.miniMessage.deserialize("<gray>This donor slot requires</gray>"));
        loreComponents.add(this.miniMessage.deserialize("<gray>" + requiredRank.displayName() + " <gray>rank or higher.</gray>"));
      } else {
        loreComponents.add(this.miniMessage.deserialize("<gray>This donor slot requires a higher"));
        loreComponents.add(this.miniMessage.deserialize("<gray>donor rank to unlock."));
      } 
      loreComponents.add(Component.empty());
      loreComponents.add(this.miniMessage.deserialize("<gold><bold>Upgrade your donor rank</bold></gold>"));
      loreComponents.add(this.miniMessage.deserialize("<gold>to access this slot!</gold>"));
      meta.lore(loreComponents);
      item.setItemMeta(meta);
      return item;
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error creating unavailable donor slot item: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      return new ItemStack(Material.BEDROCK);
    } 
  }
  
  private ItemStack createUnavailableDonorSlotItem(int slot) {
    return createUnavailableDonorSlotItem(slot, null);
  }
  
  private ItemStack createSimpleDecorativeItem() {
    try {
      Material material = Material.LIGHT_BLUE_STAINED_GLASS_PANE;
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta == null)
        return item; 
      Component nameComponent = this.miniMessage.deserialize("<aqua><bold>✦ Storage System ✦</bold></aqua>");
      meta.displayName(nameComponent);
      List<Component> loreComponents = new ArrayList<>();
      loreComponents.add(this.miniMessage.deserialize("<gray>Secure item storage"));
      loreComponents.add(this.miniMessage.deserialize("<gray>Access your items anytime"));
      meta.lore(loreComponents);
      item.setItemMeta(meta);
      return item;
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error creating decorative item: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      return new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
    } 
  }
  
  public void saveInventoryContents(Inventory inv, UUID storageOwner) {
    if (inv == null || storageOwner == null)
      return; 
    try {
      PlayerStorageData data = this.dataManager.getPlayerData(storageOwner);
      boolean hasChanges = false;
      Player ownerPlayer = Bukkit.getPlayer(storageOwner);
      List<ItemStack> prohibitedItems = new ArrayList<>();
      for (int i = 0; i < inv.getSize(); i++) {
        if ((i < 9 || i > 10) && (i < 16 || i > 17))
          if (data.hasSlotUnlocked(i)) {
            ItemStack item = inv.getItem(i);
            ItemStack currentItem = data.getItem(i);
            if (item != null && !item.getType().isAir()) {
              if (this.config.isProhibitedItem(item)) {
                prohibitedItems.add(item.clone());
                if (currentItem != null) {
                  data.setItem(i, null);
                  hasChanges = true;
                } 
                this.plugin.getComponentLogger().warn((Component)Component.text("Prohibited item detected in storage for player " + (
                      (ownerPlayer != null) ? ownerPlayer.getName() : storageOwner.toString()) + ": " + String.valueOf(
                        item.getType()) + " - returning to player"));
              } else if (!itemsEqual(item, currentItem)) {
                // Remove withdrawal lore before storing the item to prevent lore from being saved permanently
                ItemStack cleanItem = removeWithdrawalLore(item.clone());
                data.setItem(i, cleanItem);
                hasChanges = true;
              } 
            } else if (currentItem != null) {
              data.setItem(i, null);
              hasChanges = true;
            } 
          }  
      } 
      if (!prohibitedItems.isEmpty() && ownerPlayer != null && ownerPlayer.isOnline())
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
              for (ItemStack prohibitedItem : prohibitedItems) {
                HashMap<Integer, ItemStack> leftover = ownerPlayer.getInventory().addItem(new ItemStack[] { prohibitedItem });
                if (!leftover.isEmpty())
                  for (ItemStack drop : leftover.values())
                    ownerPlayer.getWorld().dropItemNaturally(ownerPlayer.getLocation(), drop);  
              } 
              ownerPlayer.sendMessage(this.config.getMessage("prohibited-item-returned", Map.of("count", String.valueOf(prohibitedItems.size()))));
            }); 
      if (hasChanges) {
        this.dataManager.markDirty();
        if (this.config.logTransactions()) {
          String ownerName = (ownerPlayer != null) ? ownerPlayer.getName() : 
            Bukkit.getOfflinePlayer(storageOwner).getName();
          this.plugin.getComponentLogger().info((Component)Component.text("Saved storage contents for " + (
                (ownerName != null) ? ownerName : storageOwner.toString())));
        } 
      } 
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error saving inventory contents: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
    } 
  }
  
  public boolean isValidStorageInventory(String title) {
    if (title == null)
      return false; 
    try {
      String plainTitle = title.toLowerCase();
      return !(!plainTitle.contains("storage") && 
        !plainTitle.contains("admin view") && 
        !plainTitle.contains("backpack") && 
        !plainTitle.contains("slots"));
    } catch (Exception e) {
      return title.toLowerCase().contains("storage");
    } 
  }
  
  public UUID getStorageOwner(String title, Player viewer) {
    if (viewer == null || title == null)
      return null; 
    UUID trackedOwner = this.openInventories.get(viewer.getUniqueId());
    if (trackedOwner != null)
      return trackedOwner; 
    if (viewer.hasPermission("storageslots.admin") && title.contains(" - "))
      try {
        String[] parts = title.split(" - ");
        if (parts.length >= 2) {
          String targetName = parts[1].replace("(Admin View)", "").trim();
          Player target = Bukkit.getPlayer(targetName);
          if (target != null)
            return target.getUniqueId(); 
          for (UUID id : this.dataManager.getAllStoredPlayerIds()) {
            String offlineName = Bukkit.getOfflinePlayer(id).getName();
            if (targetName.equals(offlineName))
              return id; 
          } 
        } 
      } catch (Exception e) {
        this.plugin.getComponentLogger().error(Component.text("Error parsing storage owner from title: " + e.getMessage())
            .color((TextColor)Constants.Colors.ERROR));
      }  
    return viewer.getUniqueId();
  }
  
  public void closeStorage(Player player) {
    if (player == null)
      return; 
    this.openInventories.remove(player.getUniqueId());
  }
  
  public boolean hasStorageOpen(Player player) {
    return (player != null && this.openInventories.containsKey(player.getUniqueId()));
  }
  
  public void refreshInventory(Player player) {
    if (player == null || !hasStorageOpen(player))
      return; 
    try {
      UUID storageOwner = this.openInventories.get(player.getUniqueId());
      if (storageOwner == null)
        return; 
      boolean isAdminView = !storageOwner.equals(player.getUniqueId());
      Inventory topInventory = player.getOpenInventory().getTopInventory();
      if (topInventory == null)
        return; 
      PlayerStorageData data = this.dataManager.getPlayerData(storageOwner);
      boolean isDonor = !(!this.config.getHighestDonorRank(player).isPresent() && 
        !player.isOp() && 
        !player.hasPermission("storageslots.donor.*"));
      int expectedSize = isDonor ? 18 : 9;
      if (topInventory.getSize() != expectedSize) {
        player.closeInventory();
        if (isAdminView) {
          openPlayerStorage(player, storageOwner);
        } else {
          openStorage(player);
        } 
        return;
      } 
      for (int i = 0; i < Math.min(this.config.getStorageSlots(), 9) && i < topInventory.getSize(); i++) {
        ItemStack currentItem = topInventory.getItem(i);
        ItemStack newItem = null;
        if (data.hasSlotUnlocked(i)) {
          ItemStack storedItem = data.getItem(i);
          if (storedItem != null && !storedItem.getType().isAir())
            newItem = addWithdrawalFeeToItem(storedItem.clone(), player, i, false); 
        } else if (!isAdminView) {
          boolean canBuyNext = canUnlockSlot(player, i, data);
          newItem = createLockedSlotItem(i, player, canBuyNext);
        } else {
          newItem = this.config.createLockedSlotItem(i, null);
        } 
        if (!itemsEqual(currentItem, newItem))
          topInventory.setItem(i, newItem); 
      } 
      if (isDonor && topInventory.getSize() >= 18) {
        Optional<StorageConfig.DonorRank> highestRankOpt = this.config.getHighestDonorRank(player);
        if (highestRankOpt.isPresent()) {
          StorageConfig.DonorRank highestRank = highestRankOpt.get();
          int availableDonorSlots = Math.min(highestRank.slots(), 5);
          for (int j = 0; j < 5; j++) {
            int slotIndex = 11 + j;
            ItemStack currentItem = topInventory.getItem(slotIndex);
            ItemStack newItem = null;
            StorageConfig.DonorRank slotSpecificRank = getDonorRankForSlot(slotIndex);
            if (j < availableDonorSlots && slotSpecificRank != null) {
              if (data.hasSlotUnlocked(slotIndex)) {
                ItemStack storedItem = data.getItem(slotIndex);
                if (storedItem != null && !storedItem.getType().isAir())
                  newItem = addWithdrawalFeeToItem(storedItem.clone(), player, slotIndex, true); 
              } else if (!isAdminView) {
                newItem = createDonorSlotItem(slotIndex, slotSpecificRank, player);
              } 
            } else if (!isAdminView) {
              newItem = createUnavailableDonorSlotItem(slotIndex, slotSpecificRank);
            } 
            if (!itemsEqual(currentItem, newItem))
              topInventory.setItem(slotIndex, newItem); 
          } 
        } 
      } 
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error refreshing inventory: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
    } 
  }
  
  /**
   * Removes withdrawal lore from an item to get the clean version for storage.
   * This prevents withdrawal display lore from being permanently saved.
   */
  public ItemStack removeWithdrawalLore(ItemStack item) {
    if (item == null || item.getType().isAir())
      return item;
      
    try {
      ItemMeta meta = item.getItemMeta();
      if (meta == null)
        return item;
        
      List<Component> lore = meta.lore();
      if (lore == null || lore.isEmpty())
        return item;
        
      // Remove withdrawal-related lore lines and the empty line before them
      List<Component> cleanLore = new ArrayList<>();

      
      for (int i = 0; i < lore.size(); i++) {
        Component component = lore.get(i);
        
        // Convert component to plain text for checking
        String loreText = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
        
        // Skip withdrawal lore lines
        if (loreText.toLowerCase().contains("withdrawal:")) {
          continue;
        }
        
        // Check if this is an empty line followed by withdrawal info
        if (component.equals(Component.empty()) && i + 1 < lore.size()) {
          Component nextComponent = lore.get(i + 1);
          String nextText = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(nextComponent);
          if (nextText.toLowerCase().contains("withdrawal:")) {
            continue; // Skip this empty line as it's before withdrawal info
          }
        }
        
        // Keep all other lore lines
        cleanLore.add(component);
      }
      
      // Set the cleaned lore
      meta.lore(cleanLore.isEmpty() ? null : cleanLore);
      item.setItemMeta(meta);
      return item;
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error removing withdrawal lore: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
      return item; // Return original item if there's an error
    }
  }

  private boolean itemsEqual(ItemStack item1, ItemStack item2) {
    if (item1 == item2)
      return true; 
    if (item1 == null || item2 == null)
      return false; 
    if (item1.getType() != item2.getType() || item1.getAmount() != item2.getAmount())
      return false; 
    if (item1.hasItemMeta() != item2.hasItemMeta())
      return false; 
    if (item1.hasItemMeta()) {
      ItemMeta meta1 = item1.getItemMeta();
      ItemMeta meta2 = item2.getItemMeta();
      if (meta1 == null || meta2 == null)
        return false; 
      if (meta1.hasDisplayName() != meta2.hasDisplayName())
        return false; 
      if (meta1.hasDisplayName() && 
        !meta1.displayName().equals(meta2.displayName()))
        return false; 
    } 
    return true;
  }
  
  public void updateSlotInOpenInventory(Player player, int slot) {
    if (player == null || !hasStorageOpen(player))
      return; 
    try {
      UUID storageOwner = this.openInventories.get(player.getUniqueId());
      if (storageOwner == null)
        return; 
      boolean isAdminView = !storageOwner.equals(player.getUniqueId());
      Inventory topInventory = player.getOpenInventory().getTopInventory();
      if (topInventory == null || slot >= topInventory.getSize())
        return; 
      PlayerStorageData data = this.dataManager.getPlayerData(storageOwner);
      ItemStack newItem = null;
      if (data.hasSlotUnlocked(slot)) {
        ItemStack storedItem = data.getItem(slot);
        if (storedItem != null && !storedItem.getType().isAir()) {
          boolean isDonorSlot = Constants.Slots.isDonorSlot(slot);
          newItem = addWithdrawalFeeToItem(storedItem.clone(), player, slot, isDonorSlot);
        } 
      } else if (!isAdminView) {
        if (Constants.Slots.isDonorSlot(slot)) {
          StorageConfig.DonorRank slotSpecificRank = getDonorRankForSlot(slot);
          Optional<StorageConfig.DonorRank> highestDonorRank = this.config.getHighestDonorRank(player);
          if (highestDonorRank.isPresent() && slotSpecificRank != null) {
            int donorSlotIndex = Constants.Slots.getDonorSlotIndex(slot);
            if (donorSlotIndex < ((StorageConfig.DonorRank)highestDonorRank.get()).slots()) {
              newItem = createDonorSlotItem(slot, slotSpecificRank, player);
            } else {
              newItem = createUnavailableDonorSlotItem(slot, slotSpecificRank);
            } 
          } else {
            newItem = createUnavailableDonorSlotItem(slot, slotSpecificRank);
          } 
        } else {
          boolean canBuyNext = canUnlockSlot(player, slot, data);
          newItem = createLockedSlotItem(slot, player, canBuyNext);
        } 
      } else {
        newItem = this.config.createLockedSlotItem(slot, null);
      } 
      topInventory.setItem(slot, newItem);
    } catch (Exception e) {
      this.plugin.getComponentLogger().error(Component.text("Error updating slot in inventory: " + e.getMessage())
          .color((TextColor)Constants.Colors.ERROR));
    } 
  }
  
  public void cleanup() {
    this.openInventories.clear();
  }
}
