package dev.lsdmc;

import dev.lsdmc.utils.Constants;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
// import org.bukkit.event.inventory.InventoryCreativeEvent; // not used
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class StorageManager implements Listener {
  private final StorageSlots plugin;
  
  private final StorageConfig config;
  
  private final StorageDataManager dataManager;
  
  private final StorageInventoryManager inventoryManager;
  
  private final Map<UUID, Map<Integer, Long>> withdrawalCooldowns = new ConcurrentHashMap<>();
  
  private long getWithdrawalCooldownMs() {
    return this.config.getWithdrawalCooldownMs();
  }
  
  private boolean shouldApplyWithdrawalCooldown(Player player) {
    if (!this.config.isWithdrawalCooldownEnabled())
      return false; 
    if (this.config.canBypassWithdrawalCooldown(player))
      return false; 
    if (player.isOp() && !this.plugin.getConfig().getBoolean("withdrawal-cooldown.apply-to-ops", true))
      return false; 
    return true;
  }
  
  public StorageManager(StorageSlots plugin, StorageConfig config) {
    this.plugin = plugin;
    this.config = config;
    this.dataManager = new StorageDataManager(plugin);
    this.inventoryManager = new StorageInventoryManager(plugin, config, this.dataManager);
    setupAutoSave();
  }
  
  private void setupAutoSave() {
    if (!this.config.isAutoSaveEnabled())
      return; 
    (new BukkitRunnable() {
        public void run() {
          if (StorageManager.this.dataManager.isSavePending())
            StorageManager.this.dataManager.saveData(); 
        }
      }).runTaskTimer((Plugin)this.plugin, this.config.getAutoSaveInterval() * 20L, this.config.getAutoSaveInterval() * 20L);
  }
  
  public void openStorage(Player player) {
    if (!this.plugin.getSafezoneManager().isInSafezone(player)) {
      player.sendMessage(this.config.getSafezoneMessage());
      return;
    } 
    this.plugin.markPlayerSeenStorage(player);
    this.inventoryManager.openStorage(player);
  }
  
  public void openPlayerStorage(Player admin, UUID targetPlayerId) {
    if (!this.plugin.getSafezoneManager().isInSafezone(admin)) {
      admin.sendMessage(this.config.getSafezoneMessage());
      return;
    } 
    this.inventoryManager.openPlayerStorage(admin, targetPlayerId);
  }
  
  public UUID findPlayerUUID(String name) {
    if (name == null || name.trim().isEmpty() || name.length() > 16)
      return null; 
    String cleanName = name.trim().replaceAll("[^a-zA-Z0-9_]", "");
    if (cleanName.isEmpty())
      return null; 
    Player target = Bukkit.getPlayer(cleanName);
    if (target != null)
      return target.getUniqueId(); 
    for (UUID id : this.dataManager.getAllStoredPlayerIds()) {
      String offlineName = Bukkit.getOfflinePlayer(id).getName();
      if (cleanName.equalsIgnoreCase(offlineName))
        return id; 
    } 
    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(cleanName);
    if (offlinePlayer.hasPlayedBefore())
      return offlinePlayer.getUniqueId(); 
    return null;
  }
  
  public void giveSlot(Player admin, String targetName, int slot) {
    if (!this.config.isValidSlot(slot)) {
      admin.sendMessage(this.config.getMessage("invalid-slot"));
      return;
    } 
    UUID targetId = findPlayerUUID(targetName);
    if (targetId == null) {
      admin.sendMessage(this.config.getMessage("player-not-found"));
      return;
    } 
    PlayerStorageData data = this.dataManager.getPlayerData(targetId);
    if (data.hasSlotUnlocked(slot)) {
      admin.sendMessage(this.config.getMessage("slot-already-owned", Map.of(
              "player", targetName, 
              "slot", String.valueOf(slot + 1))));
      return;
    } 
    data.unlockSlot(slot);
    this.dataManager.markDirty();
    admin.sendMessage(this.config.getMessage("slot-given", Map.of(
            "player", targetName, 
            "slot", String.valueOf(slot + 1))));
  }
  
  public void removeSlot(Player admin, String targetName, int slot) {
    if (!this.config.isValidSlot(slot)) {
      admin.sendMessage(this.config.getMessage("invalid-slot"));
      return;
    } 
    UUID targetId = findPlayerUUID(targetName);
    if (targetId == null) {
      admin.sendMessage(this.config.getMessage("player-not-found"));
      return;
    } 
    PlayerStorageData data = this.dataManager.getPlayerData(targetId);
    if (!data.hasSlotUnlocked(slot)) {
      admin.sendMessage(this.config.getMessage("slot-not-owned", Map.of(
              "player", targetName, 
              "slot", String.valueOf(slot + 1))));
      return;
    } 
    if (data.getItem(slot) != null) {
      admin.sendMessage(this.config.getMessage("slot-not-empty", Map.of(
              "player", targetName, 
              "slot", String.valueOf(slot + 1))));
      return;
    } 
    data.lockSlot(slot);
    this.dataManager.markDirty();
    admin.sendMessage(this.config.getMessage("slot-removed", Map.of(
            "player", targetName, 
            "slot", String.valueOf(slot + 1))));
  }
  
  public void listSlots(Player admin, String targetName) {
    UUID targetId = findPlayerUUID(targetName);
    if (targetId == null) {
      admin.sendMessage(this.config.getMessage("player-not-found"));
      return;
    } 
    PlayerStorageData data = this.dataManager.getPlayerData(targetId);
    Set<Integer> slots = data.getUnlockedSlots();
    if (slots.isEmpty()) {
      admin.sendMessage(this.config.getMessage("no-slots-owned", Map.of(
              "player", targetName)));
      return;
    } 
    admin.sendMessage(this.config.getMessage("slots-list-header", Map.of(
            "player", targetName)));
    for (Iterator<Integer> iterator = slots.iterator(); iterator.hasNext(); ) {
      int slot = ((Integer)iterator.next()).intValue();
      ItemStack item = data.getItem(slot);
      String status = (item != null) ? item.getType().toString() : "Empty";
      admin.sendMessage(this.config.getMessage("slots-list-format", Map.of(
              "slot", String.valueOf(slot + 1), 
              "status", status)));
    } 
  }
  
  public void purchaseSlot(Player player, int slot) {
    if (!this.config.isValidSlot(slot)) {
      player.sendMessage(this.config.getMessage("invalid-slot"));
      return;
    } 
    if (!player.isOnline())
      return; 
    PlayerStorageData data = this.dataManager.getPlayerData(player.getUniqueId());
    if (data.getUnlockedSlotCount() >= this.config.getMaxSlotsPerPlayer()) {
      player.sendMessage(this.config.getMessage("max-slots-reached"));
      return;
    } 
    if (data.hasSlotUnlocked(slot)) {
      player.sendMessage(this.config.getMessage("already-unlocked"));
      return;
    } 
    boolean isDonorSlot = isDonorSlot(player, slot);
    if (isDonorSlot) {
      handleDonorSlotPurchase(player, slot, data);
      return;
    } 
    if (this.config.isProgressionRequired() && !canPlayerUnlockSlot(player, slot, data)) {
      player.sendMessage(this.config.getMessage("previous-slot-required"));
      return;
    } 
    String requiredRank = this.config.getRequiredRank(slot);
    if (!this.config.hasRankRequirement(player, requiredRank)) {
      player.sendMessage(this.config.getMessage("rank-required", Map.of(
              "rank", requiredRank)));
      return;
    } 
    double cost = this.config.getSlotCost(slot);
    if (this.config.logTransactions())
      this.plugin.getLogger().info("Player " + player.getName() + " attempting to purchase slot " + (slot + 1) + " for " + cost + " " + this.plugin.getEconomyManager().getCurrencyName("slot-purchase"));
    this.plugin.getEconomyManager().takeMoney(player, cost, "slot-purchase").thenAccept(success -> {
          if (this.config.logTransactions())
            this.plugin.getLogger().info("Player " + player.getName() + " slot purchase result: " + String.valueOf(success)); 
          if (!success.booleanValue() || !player.isOnline()) {
            if (player.isOnline()) {
              // Get the actual balance for the error message
              this.plugin.getEconomyManager().getBalance(player, "slot-purchase").thenAccept(balance -> {
                Map<String, String> placeholders = Map.of(
                  "cost", this.plugin.getEconomyManager().formatCurrency(cost, "slot-purchase"),
                  "currency", this.plugin.getEconomyManager().getCurrencyName("slot-purchase"),
                  "balance", this.plugin.getEconomyManager().formatCurrency(balance, "slot-purchase")
                );
                player.sendMessage(this.config.getMessage("insufficient-funds", placeholders)); 
              });
            }
            return;
          } 
          Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
            data.unlockSlot(slot);
            this.dataManager.markDirty();
            Map<String, String> placeholders = Map.of(
              "slot", String.valueOf(slot + 1),
              "cost", this.plugin.getEconomyManager().formatCurrency(cost, "slot-purchase"),
              "currency", this.plugin.getEconomyManager().getCurrencyName("slot-purchase")
            );
            player.sendMessage(this.config.getMessage("slot-purchased", placeholders));
            if (this.inventoryManager.hasStorageOpen(player))
              this.inventoryManager.updateSlotInOpenInventory(player, slot);
          });
        });
  }
  
  private boolean isDonorSlot(Player player, int slot) {
    if (!this.config.isDonorEnabled())
      return false; 
    if (!Constants.Slots.isDonorSlot(slot))
      return false; 
    Optional<StorageConfig.DonorRank> highestRankOpt = this.config.getHighestDonorRank(player);
    if (highestRankOpt.isEmpty())
      return false; 
    StorageConfig.DonorRank highestRank = highestRankOpt.get();
    int availableDonorSlots = Math.min(highestRank.slots(), Constants.Slots.DONOR_SLOT_COUNT);
    int donorSlotIndex = Constants.Slots.getDonorSlotIndex(slot);
    return (donorSlotIndex < availableDonorSlots);
  }
  
  private void handleDonorSlotPurchase(Player player, int slot, PlayerStorageData data) {
    if (!this.config.areDonorSlotsPurchasable()) {
      player.sendMessage(this.config.getMessage("donor-feature-unavailable"));
      return;
    } 
    StorageConfig.DonorRank highestRank = null;
    int maxSlots = 0;
    if (player.isOp() || player.hasPermission("storageslots.donor.*")) {
      Optional<StorageConfig.DonorRank> highestRankOpt = this.config.getHighestDonorRank(player);
      if (highestRankOpt.isPresent()) {
        highestRank = highestRankOpt.get();
        maxSlots = highestRank.slots();
      } 
    } else {
      for (StorageConfig.DonorRank rank : this.config.getDonorRanks()) {
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
      player.sendMessage(this.config.getMessage("donor-feature-unavailable"));
      return;
    } 
    StorageConfig.DonorRank donorRankToUse = highestRank;
    double baseCost = this.config.getSlotCost(slot);
    double cost = baseCost * this.config.getDonorSlotCostMultiplier();
    if (this.config.logTransactions())
      this.plugin.getLogger().info("Player " + player.getName() + " attempting to purchase donor slot " + (slot + 1) + " for " + 
          cost + " " + this.plugin.getEconomyManager().getCurrencyName("donor-slots") + " (base: " + baseCost + ", multiplier: " + this.config.getDonorSlotCostMultiplier() + ")");
    this.plugin.getEconomyManager().takeMoney(player, cost, "donor-slots").thenAccept(success -> {
          if (this.config.logTransactions())
            this.plugin.getLogger().info("Player " + player.getName() + " donor slot purchase result: " + String.valueOf(success)); 
          if (!success.booleanValue() || !player.isOnline()) {
            if (player.isOnline()) {
              // Get the actual balance for the error message
              this.plugin.getEconomyManager().getBalance(player, "donor-slots").thenAccept(balance -> {
                Map<String, String> placeholders = Map.of(
                  "cost", String.valueOf((int)cost),
                  "currency", this.plugin.getEconomyManager().getCurrencyName("donor-slots"),
                  "balance", this.plugin.getEconomyManager().formatCurrency(balance, "donor-slots")
                );
                player.sendMessage(this.config.getMessage("insufficient-funds", placeholders)); 
              });
            }
            return;
          } 
          Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
            data.unlockSlot(slot);
            this.dataManager.markDirty();
            data.setCurrentDonorRank(donorRankToUse.name());
            Map<String, String> placeholders = Map.of(
              "slot", String.valueOf(slot + 1),
              "cost", String.valueOf((int)cost),
              "currency", this.plugin.getEconomyManager().getCurrencyName("donor-slots")
            );
            player.sendMessage(this.config.getMessage("donor-slot-purchased", placeholders));
            if (this.inventoryManager.hasStorageOpen(player))
              this.inventoryManager.updateSlotInOpenInventory(player, slot);
          });
        });
  }
  
  @EventHandler
  public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player))
      return; 
    Player player = (Player)event.getWhoClicked();
    String title = event.getView().getTitle();
    if (!this.inventoryManager.isValidStorageInventory(title))
      return; 
    // Extra safety: only process clicks when our storage GUI is actually tracked as open
    if (!this.inventoryManager.hasStorageOpen(player))
      return;
    Inventory clickedInventory = event.getClickedInventory();
    if (clickedInventory == null)
      return; 
    int slot = event.getSlot();
    ItemStack clickedItem = event.getCurrentItem();
    ItemStack cursorItem = event.getCursor();
    PlayerStorageData data = this.dataManager.getPlayerData(player.getUniqueId());
    if (data == null)
      return; 
    if (clickedInventory.equals(event.getView().getTopInventory())) {
      if (clickedItem != null && isLockedSlotItem(clickedItem)) {
        event.setCancelled(true);
        handleSlotPurchase(player, slot, data);
        return;
      } 
      if (clickedItem != null && clickedItem.getType() == Material.BEDROCK) {
        event.setCancelled(true);
        return;
      } 
      if (!data.hasSlotUnlocked(slot)) {
        event.setCancelled(true);
        return;
      } 
      if (cursorItem != null && !cursorItem.getType().isAir()) {
        if (this.config.isProhibitedItem(cursorItem)) {
          event.setCancelled(true);
          player.sendMessage(this.config.getMessage("prohibited-item"));
          return;
        } 
        if (cursorItem.getAmount() > this.config.getMaxItemsPerSlot()) {
          event.setCancelled(true);
          player.sendMessage(this.config.getMessage("max-items-per-slot", Map.of(
                  "max", String.valueOf(this.config.getMaxItemsPerSlot()))));
          return;
        } 
      }
      // Determine whether this slot currently holds a PERSISTED storage item
      // Only persisted items should trigger withdrawals/fees. If the player just
      // placed an item and hasn't closed the GUI yet (unsaved), allow normal
      // pickup without fees.
      ItemStack persistedItem = data.getItem(slot);
      boolean hasPersistedItem = (persistedItem != null && !persistedItem.getType().isAir());

      // Intercept any action that attempts to move the display item (with fee lore)
      // into the player's inventory from the top storage inventory. We will cancel
      // the vanilla behavior and perform a proper withdrawal that gives the
      // original clean item from storage data.
      if (clickedItem != null && !clickedItem.getType().isAir()) {
        InventoryAction action = event.getAction();
        boolean isShiftMove = (action == InventoryAction.MOVE_TO_OTHER_INVENTORY); // typical shift-click
        boolean isHotbarSwap = (action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD);
        boolean isPickupVariants = (action == InventoryAction.PICKUP_ALL || action == InventoryAction.PICKUP_HALF || action == InventoryAction.PICKUP_ONE || action == InventoryAction.PICKUP_SOME);
        boolean isCollectToCursor = (action == InventoryAction.COLLECT_TO_CURSOR);
        boolean isDrop = (action == InventoryAction.DROP_ALL_SLOT || action == InventoryAction.DROP_ONE_SLOT);
        ClickType clickType = event.getClick();
        boolean isMiddleClick = (clickType == ClickType.MIDDLE);

        // In creative mode, prevent risky bulk moves entirely for unsaved items to avoid dupes
        if (player.getGameMode() == GameMode.CREATIVE) {
          boolean risky = isShiftMove || isHotbarSwap || isCollectToCursor || isMiddleClick;
          if (risky && !hasPersistedItem) {
            event.setCancelled(true);
            return;
          }
        }

        // Handle different types of item interactions
        boolean isPureWithdrawal = (cursorItem == null || cursorItem.getType().isAir()) && 
                                  (isPickupVariants || isShiftMove || isDrop || isMiddleClick);
        boolean isItemSwap = (cursorItem != null && !cursorItem.getType().isAir()) && 
                           (isHotbarSwap || isCollectToCursor);
        
        // For pure withdrawals (no item being placed), apply fees and withdrawal logic
        if (hasPersistedItem && isPureWithdrawal) {
          boolean applyCooldown = shouldApplyWithdrawalCooldown(player);
          if (applyCooldown) {
            clearExpiredCooldowns(player);
            if (isOnWithdrawalCooldown(player, slot)) {
              event.setCancelled(true);
              long remainingMs = ((Long)((Map)this.withdrawalCooldowns.get(player.getUniqueId())).get(Integer.valueOf(slot))).longValue() - System.currentTimeMillis();
              double remainingSeconds = remainingMs / 1000.0D;
              player.sendMessage(Component.text("Please wait " + String.format("%.1f", new Object[] { Double.valueOf(remainingSeconds) }) + " seconds before withdrawing from this slot.").color((TextColor)Constants.Colors.ERROR));
              return;
            }
            setWithdrawalCooldown(player, slot);
          }

          boolean isDonorSlot = Constants.Slots.isDonorSlot(slot);
          
          // Check if donor has specific fees configured, otherwise use regular rank-based fees
          boolean hasDonorSpecificFees = false;
          boolean hasFreeWithdrawal = false;
          if (isDonorSlot) {
            Optional<StorageConfig.DonorRank> highestRankOpt = this.config.getHighestDonorRank(player);
            if (highestRankOpt.isPresent()) {
              String donorRankName = highestRankOpt.get().name();
              StorageConfig.WithdrawalFee donorFee = this.config.getDonorRankFees().get(donorRankName);
              hasDonorSpecificFees = (donorFee != null);
              if (hasDonorSpecificFees) {
                double fee = this.config.useVaultForWithdrawalFees() ? donorFee.money() : donorFee.points();
                hasFreeWithdrawal = (fee <= 0);
              }
            }
          }
          
          event.setCancelled(true);
          if (!hasFreeWithdrawal) {
            handleItemWithdrawal(player, slot, clickedItem, data);
            return;
          }
          completeWithdrawal(player, slot, clickedItem, data);
          return;
        }
        
        // For item swaps, handle them properly without bypassing fees
        if (hasPersistedItem && isItemSwap) {
          event.setCancelled(true);
          
          // Validate the item being placed
          if (this.config.isProhibitedItem(cursorItem)) {
            player.sendMessage(this.config.getMessage("prohibited-item"));
            return;
          }
          if (cursorItem.getAmount() > this.config.getMaxItemsPerSlot()) {
            player.sendMessage(this.config.getMessage("max-items-per-slot", Map.of(
                    "max", String.valueOf(this.config.getMaxItemsPerSlot()))));
            return;
          }
          
          // Apply withdrawal fee for the item being taken out
          boolean applyCooldown = shouldApplyWithdrawalCooldown(player);
          if (applyCooldown) {
            clearExpiredCooldowns(player);
            if (isOnWithdrawalCooldown(player, slot)) {
              long remainingMs = ((Long)((Map)this.withdrawalCooldowns.get(player.getUniqueId())).get(Integer.valueOf(slot))).longValue() - System.currentTimeMillis();
              double remainingSeconds = remainingMs / 1000.0D;
              player.sendMessage(Component.text("Please wait " + String.format("%.1f", new Object[] { Double.valueOf(remainingSeconds) }) + " seconds before withdrawing from this slot.").color((TextColor)Constants.Colors.ERROR));
              return;
            }
            setWithdrawalCooldown(player, slot);
          }

          boolean isDonorSlot = Constants.Slots.isDonorSlot(slot);
          
          // Check if donor has specific fees configured, otherwise use regular rank-based fees
          boolean hasDonorSpecificFees = false;
          boolean hasFreeWithdrawal = false;
          if (isDonorSlot) {
            Optional<StorageConfig.DonorRank> highestRankOpt = this.config.getHighestDonorRank(player);
            if (highestRankOpt.isPresent()) {
              String donorRankName = highestRankOpt.get().name();
              StorageConfig.WithdrawalFee donorFee = this.config.getDonorRankFees().get(donorRankName);
              hasDonorSpecificFees = (donorFee != null);
              if (hasDonorSpecificFees) {
                double fee = this.config.useVaultForWithdrawalFees() ? donorFee.money() : donorFee.points();
                hasFreeWithdrawal = (fee <= 0);
              }
            }
          }
          
          if (!hasFreeWithdrawal) {
            handleItemSwapWithFee(player, slot, clickedItem, cursorItem, data);
            return;
          }
          
          // Free withdrawal - complete the swap
          completeItemSwap(player, slot, clickedItem, cursorItem, data);
          return;
        }
      }
      if (clickedItem != null && !clickedItem.getType().isAir() && hasPersistedItem && (
        cursorItem == null || cursorItem.getType().isAir()) && 
        event.getAction() == InventoryAction.PICKUP_ALL) {
        boolean applyCooldown = shouldApplyWithdrawalCooldown(player);
        if (applyCooldown) {
          clearExpiredCooldowns(player);
          if (isOnWithdrawalCooldown(player, slot)) {
            event.setCancelled(true);
            long remainingMs = ((Long)((Map)this.withdrawalCooldowns.get(player.getUniqueId())).get(Integer.valueOf(slot))).longValue() - System.currentTimeMillis();
            double remainingSeconds = remainingMs / 1000.0D;
            player.sendMessage(Component.text("Please wait " + String.format("%.1f", new Object[] { Double.valueOf(remainingSeconds) }) + " seconds before withdrawing from this slot.").color((TextColor)Constants.Colors.ERROR));
            return;
          } 
          setWithdrawalCooldown(player, slot);
        } 
        boolean isDonorSlot = Constants.Slots.isDonorSlot(slot);
        
        // Check if donor has specific fees configured, otherwise use regular rank-based fees
        boolean hasDonorSpecificFees = false;
        boolean hasFreeWithdrawal = false;
        if (isDonorSlot) {
          Optional<StorageConfig.DonorRank> highestRankOpt = this.config.getHighestDonorRank(player);
          if (highestRankOpt.isPresent()) {
            String donorRankName = highestRankOpt.get().name();
            StorageConfig.WithdrawalFee donorFee = this.config.getDonorRankFees().get(donorRankName);
            hasDonorSpecificFees = (donorFee != null);
            if (hasDonorSpecificFees) {
              double fee = this.config.useVaultForWithdrawalFees() ? donorFee.money() : donorFee.points();
              hasFreeWithdrawal = (fee <= 0);
            }
          }
        }
        
        if (!hasFreeWithdrawal) {
          event.setCancelled(true);
          handleItemWithdrawal(player, slot, clickedItem, data);
          return;
        } 
        event.setCancelled(true);
        completeWithdrawal(player, slot, clickedItem, data);
        return;
      } 
    } else if (clickedInventory.equals(event.getView().getBottomInventory())) {
      // Prevent known creative-mode dupes by blocking shift-click moves from player inventory
      // into the storage GUI. Players can still drag/drop normally.
      if (player.getGameMode() == GameMode.CREATIVE && event.isShiftClick()) {
        event.setCancelled(true);
        return;
      }
      if (event.isShiftClick() && clickedItem != null && !clickedItem.getType().isAir()) {
        if (this.config.isProhibitedItem(clickedItem)) {
          event.setCancelled(true);
          player.sendMessage(this.config.getMessage("prohibited-item"));
          return;
        } 
        if (clickedItem.getAmount() > this.config.getMaxItemsPerSlot()) {
          event.setCancelled(true);
          player.sendMessage(this.config.getMessage("max-items-per-slot", Map.of(
                  "max", String.valueOf(this.config.getMaxItemsPerSlot()))));
          return;
        } 
      } 
    } 
  }
  
  private boolean isLockedSlotItem(ItemStack item) {
    if (item == null || item.getType().isAir())
      return false; 
    return !(!item.getType().name().contains("GLASS_PANE") && 
      item.getType() != Material.BARRIER && 
      item.getType() != Material.GOLD_BLOCK);
  }
  
  private void handleSlotPurchase(Player player, int slot, PlayerStorageData data) {
    if (data.hasSlotUnlocked(slot))
      return; 
    if (this.config.isProgressionRequired() && !canPlayerUnlockSlot(player, slot, data)) {
      player.sendMessage(this.config.getMessage("previous-slot-required"));
      return;
    } 
    String requiredRank = this.config.getRequiredRank(slot);
    if (!this.config.hasRankRequirement(player, requiredRank)) {
      player.sendMessage(this.config.getMessage("rank-required", Map.of(
              "rank", requiredRank)));
      return;
    } 
    purchaseSlot(player, slot);
  }
  
  private boolean canPlayerUnlockSlot(Player player, int slot, PlayerStorageData data) {
    if (!this.config.isProgressionRequired())
      return true; 
    if (slot == 0)
      return true; 
    if (slot < 9)
      return data.hasSlotUnlocked(slot - 1); 
    if (Constants.Slots.isDonorSlot(slot)) {
      Optional<StorageConfig.DonorRank> donorRank = this.config.getHighestDonorRank(player);
      if (donorRank.isEmpty())
        return false; 
      int donorSlotIndex = Constants.Slots.getDonorSlotIndex(slot);
      return (donorSlotIndex < ((StorageConfig.DonorRank)donorRank.get()).slots());
    } 
    return false;
  }
  
  private boolean isOnWithdrawalCooldown(Player player, int slot) {
    Map<Integer, Long> playerCooldowns = this.withdrawalCooldowns.get(player.getUniqueId());
    if (playerCooldowns == null)
      return false; 
    Long cooldownEnd = playerCooldowns.get(Integer.valueOf(slot));
    if (cooldownEnd == null)
      return false; 
    return (System.currentTimeMillis() < cooldownEnd.longValue());
  }
  
  private void setWithdrawalCooldown(Player player, int slot) {
    ((Map<Integer, Long>)this.withdrawalCooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()))
      .put(Integer.valueOf(slot), Long.valueOf(System.currentTimeMillis() + getWithdrawalCooldownMs()));
  }
  
  private void clearExpiredCooldowns(Player player) {
    Map<Integer, Long> playerCooldowns = this.withdrawalCooldowns.get(player.getUniqueId());
    if (playerCooldowns != null) {
      long currentTime = System.currentTimeMillis();
      playerCooldowns.entrySet().removeIf(entry -> (((Long)entry.getValue()).longValue() < currentTime));
      if (playerCooldowns.isEmpty())
        this.withdrawalCooldowns.remove(player.getUniqueId()); 
    } 
  }
  
  private void handleItemWithdrawal(Player player, int slot, ItemStack item, PlayerStorageData data) {
    if (item == null || item.getType().isAir())
      return; 
    boolean isDonorSlot = data.isDonorSlot(slot);
    
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
    
    boolean hasFreeWithdrawal = false;
    if (isDonorSlot && hasDonorSpecificFees) {
      // Use donor-specific fees if configured
      Optional<StorageConfig.DonorRank> highestRankOpt = this.config.getHighestDonorRank(player);
      if (highestRankOpt.isPresent()) {
        String donorRankName = highestRankOpt.get().name();
        StorageConfig.WithdrawalFee donorFee = this.config.getDonorRankFees().get(donorRankName);
        double fee = this.config.useVaultForWithdrawalFees() ? donorFee.money() : donorFee.points();
        hasFreeWithdrawal = (fee <= 0);
      }
    }
    
    if (this.config.isWithdrawalFeesEnabled() && !hasFreeWithdrawal) {
      StorageConfig.WithdrawalFee fee = this.config.getWithdrawalFee(player);
      // Use the configured currency for withdrawal fees
      String withdrawalCurrency = this.plugin.getEconomyManager().getCurrencyName("withdrawal-fees");
      boolean useVaultForWithdrawal = this.config.useVaultForWithdrawalFees();
      
      if (useVaultForWithdrawal && fee.money() > 0.0D) {
        if (this.config.logTransactions())
          this.plugin.getLogger().info("Player " + player.getName() + " attempting to withdraw item from slot " + (slot + 1) + " with fee: " + fee.money() + " " + withdrawalCurrency);
        executeWithdrawal(player, slot, item, data, fee.money(), false);
        return;
      } else if (!useVaultForWithdrawal && fee.points() > 0) {
        if (this.config.logTransactions())
          this.plugin.getLogger().info("Player " + player.getName() + " attempting to withdraw item from slot " + (slot + 1) + " with fee: " + fee.points() + " " + withdrawalCurrency);
        executeWithdrawal(player, slot, item, data, fee.points(), true);
        return;
      }
    } 
    completeWithdrawal(player, slot, item, data);
  }
  
  private void executeWithdrawal(Player player, int slot, ItemStack item, PlayerStorageData data, double fee, boolean usePoints) {
    if (usePoints) {
      if (this.config.logTransactions())
        this.plugin.getLogger().info("Player " + player.getName() + " executing withdrawal with " + (int)fee + " " + this.plugin.getEconomyManager().getCurrencyName("withdrawal-fees")); 
      this.plugin.getEconomyManager().takeMoney(player, fee, "withdrawal-fees").thenAccept(success -> {
            if (this.config.logTransactions())
              this.plugin.getLogger().info("Player " + player.getName() + " withdrawal result: " + String.valueOf(success)); 
            if (!success.booleanValue() || !player.isOnline()) {
              if (player.isOnline()) {
                this.plugin.getEconomyManager().getBalance(player, "withdrawal-fees").thenAccept(balance -> {
                  Map<String, String> placeholders = Map.of(
                    "points", String.valueOf((int)fee),
                    "currency", this.plugin.getEconomyManager().getCurrencyName("withdrawal-fees"),
                    "balance", this.plugin.getEconomyManager().formatCurrency(balance, "withdrawal-fees")
                  );
                  player.sendMessage(this.config.getMessage("insufficient-points", placeholders)); 
                });
              }
              return;
            } 
            completeWithdrawal(player, slot, item, data);
          });
    } else {
      if (this.config.logTransactions())
        this.plugin.getLogger().info("Player " + player.getName() + " executing withdrawal with " + fee + " " + this.plugin.getEconomyManager().getCurrencyName("withdrawal-fees")); 
      this.plugin.getEconomyManager().takeMoney(player, fee, "withdrawal-fees").thenAccept(success -> {
            if (this.config.logTransactions())
              this.plugin.getLogger().info("Player " + player.getName() + " money withdrawal result: " + String.valueOf(success)); 
            if (!success.booleanValue() || !player.isOnline()) {
              if (player.isOnline()) {
                Map<String, String> placeholders = Map.of(
                  "fee", this.plugin.getEconomyManager().formatCurrency(fee, "withdrawal-fees"),
                  "currency", this.plugin.getEconomyManager().getCurrencyName("withdrawal-fees")
                );
                player.sendMessage(this.config.getMessage("insufficient-withdrawal-funds", placeholders)); 
              }
              return;
            } 
            completeWithdrawal(player, slot, item, data);
          });
    } 
  }
  
  private void completeWithdrawal(Player player, int slot, ItemStack item, PlayerStorageData data) {
    if (player == null || !player.isOnline())
      return; 
    
    // Get the ORIGINAL item from storage data, not the display item with lore
    ItemStack originalItem = data.getItem(slot);
    if (originalItem == null || originalItem.getType().isAir()) {
      this.plugin.getLogger().warning("Player " + player.getName() + " tried to withdraw from empty slot " + (slot + 1));
      return;
    }
    
    if (this.config.logTransactions())
      this.plugin.getLogger().info("Player " + player.getName() + " completing withdrawal from slot " + (slot + 1)); 
    
    // Remove item from storage BEFORE giving it to player
    data.setItem(slot, null);
    this.dataManager.markDirty();
    
    // Give the ORIGINAL item (without withdrawal lore) to the player
    // Check if inventory is full and handle overflow properly
    HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack[] { originalItem.clone() });
    if (!leftover.isEmpty()) {
      // Inventory was full, drop the leftover items on the ground
      if (this.config.logTransactions()) {
        this.plugin.getLogger().info("Player " + player.getName() + " inventory was full during withdrawal, dropping " + leftover.size() + " item(s) on ground");
      }
      for (ItemStack dropItem : leftover.values()) {
        player.getWorld().dropItemNaturally(player.getLocation(), dropItem);
      }
      String itemName = originalItem.getType().name().toLowerCase().replace("_", " ");
      // Try to get the message from config, fallback to hardcoded if not found
      Component message;
      try {
        message = this.config.getMessage("inventory-full-item-dropped", Map.of("item", itemName));
      } catch (Exception e) {
        // Fallback message if the message key is not found
        message = Component.text("Your inventory is full! The " + itemName + " has been dropped on the ground.")
            .color((TextColor)Constants.Colors.HIGHLIGHT);
      }
      player.sendMessage(message);
    }
    
    // Send appropriate message
    boolean isDonorSlot = data.isDonorSlot(slot);
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
      // Check if donor has free withdrawals
      Optional<StorageConfig.DonorRank> highestRankOpt = this.config.getHighestDonorRank(player);
      if (highestRankOpt.isPresent()) {
        String donorRankName = highestRankOpt.get().name();
        StorageConfig.WithdrawalFee donorFee = this.config.getDonorRankFees().get(donorRankName);
        double fee = this.config.useVaultForWithdrawalFees() ? donorFee.money() : donorFee.points();
        if (fee <= 0) {
          player.sendMessage(this.config.getMessage("free-withdrawal"));
        } else {
          Map<String, String> placeholders = Map.of("slot", String.valueOf(slot + 1));
          player.sendMessage(this.config.getMessage("item-withdrawn", placeholders));
        }
      }
    } else {
      Map<String, String> placeholders = Map.of("slot", String.valueOf(slot + 1));
      player.sendMessage(this.config.getMessage("item-withdrawn", placeholders));
    }
    
    // Update GUI if player has storage open
    if (this.inventoryManager.hasStorageOpen(player))
      this.inventoryManager.updateSlotInOpenInventory(player, slot); 
  }
  
  @EventHandler
  public void onInventoryDrag(InventoryDragEvent event) {
    if (!(event.getWhoClicked() instanceof Player))
      return; 
    Player player = (Player)event.getWhoClicked();
    String title = event.getView().getTitle();
    if (!this.inventoryManager.isValidStorageInventory(title))
      return; 
    if (!this.plugin.getSafezoneManager().isInSafezone(player)) {
      event.setCancelled(true);
      player.closeInventory();
      player.sendMessage(this.config.getSafezoneMessage());
      return;
    } 
    UUID storageOwner = this.inventoryManager.getStorageOwner(title, player);
    PlayerStorageData data = this.dataManager.getPlayerData(storageOwner);
    for (Iterator<Integer> iterator = event.getRawSlots().iterator(); iterator.hasNext(); ) {
      int slot = ((Integer)iterator.next()).intValue();
      if (slot < this.config.getStorageSlots()) {
        if (!data.hasSlotUnlocked(slot)) {
          event.setCancelled(true);
          return;
        } 
        for (ItemStack item : event.getNewItems().values()) {
          if (item != null && !item.getType().isAir()) {
            if (this.config.isProhibitedItem(item)) {
              event.setCancelled(true);
              player.sendMessage(this.config.getMessage("prohibited-item"));
              return;
            } 
            if (item.getAmount() > this.config.getMaxItemsPerSlot()) {
              event.setCancelled(true);
              player.sendMessage(this.config.getMessage("max-items-per-slot", Map.of(
                      "max", String.valueOf(this.config.getMaxItemsPerSlot()))));
              return;
            } 
          } 
        } 
      } 
    } 
    if (!event.isCancelled())
      this.dataManager.markDirty(); 
  }
  
  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    if (!(event.getPlayer() instanceof Player))
      return; 
    Player player = (Player)event.getPlayer();
    if (!this.inventoryManager.hasStorageOpen(player))
      return; 
    String title = event.getView().getTitle();
    if (!this.inventoryManager.isValidStorageInventory(title))
      return; 
    UUID storageOwner = this.inventoryManager.getStorageOwner(title, player);
    this.inventoryManager.saveInventoryContents(event.getInventory(), storageOwner);
    this.inventoryManager.closeStorage(player);
  }
  
  public void saveAllData() {
    this.dataManager.saveData();
  }
  
  public void resetAllStorage() {
    this.dataManager.resetAllData();
  }
  
  public void resetPlayerStorage(UUID playerId) {
    this.dataManager.resetPlayerData(playerId);
  }
  
  public StorageDataManager getDataManager() {
    return this.dataManager;
  }
  
  public StorageInventoryManager getInventoryManager() {
    return this.inventoryManager;
  }
  
  /**
   * Handles item swap with withdrawal fee
   */
  private void handleItemSwapWithFee(Player player, int slot, ItemStack storageItem, ItemStack playerItem, PlayerStorageData data) {
    if (player == null || !player.isOnline())
      return;
    
    // Get the original item from storage data (without lore)
    ItemStack originalStorageItem = data.getItem(slot);
    if (originalStorageItem == null || originalStorageItem.getType().isAir()) {
      this.plugin.getLogger().warning("Player " + player.getName() + " tried to swap from empty slot " + (slot + 1));
      return;
    }
    
    boolean isDonorSlot = Constants.Slots.isDonorSlot(slot);
    
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
    
    if (this.config.isWithdrawalFeesEnabled()) {
      StorageConfig.WithdrawalFee fee = this.config.getWithdrawalFee(player);
      // Use the configured currency for withdrawal fees
      String withdrawalCurrency = this.plugin.getEconomyManager().getCurrencyName("withdrawal-fees");
      boolean useVaultForWithdrawal = this.config.useVaultForWithdrawalFees();
      
      if (useVaultForWithdrawal && fee.money() > 0.0D) {
        if (this.config.logTransactions())
          this.plugin.getLogger().info("Player " + player.getName() + " attempting to swap item from slot " + (slot + 1) + " with fee: " + fee.money() + " " + withdrawalCurrency);
        executeItemSwapWithFee(player, slot, originalStorageItem, playerItem, data, fee.money(), false);
        return;
      } else if (!useVaultForWithdrawal && fee.points() > 0) {
        if (this.config.logTransactions())
          this.plugin.getLogger().info("Player " + player.getName() + " attempting to swap item from slot " + (slot + 1) + " with fee: " + fee.points() + " " + withdrawalCurrency);
        executeItemSwapWithFee(player, slot, originalStorageItem, playerItem, data, fee.points(), true);
        return;
      }
    }
    
    // No fee required, complete the swap
    completeItemSwap(player, slot, originalStorageItem, playerItem, data);
  }
  
  /**
   * Executes item swap with fee payment
   */
  private void executeItemSwapWithFee(Player player, int slot, ItemStack storageItem, ItemStack playerItem, PlayerStorageData data, double fee, boolean usePoints) {
    if (player == null || !player.isOnline())
      return;
    
    String currencyName = this.plugin.getEconomyManager().getCurrencyName("withdrawal-fees");
    
    // Check if player has enough balance
    CompletableFuture<Double> balanceCheck = this.plugin.getEconomyManager().getBalance(player, "withdrawal-fees");
    balanceCheck.thenAccept(balance -> {
      if (balance < fee) {
        Map<String, String> placeholders = usePoints ? 
          Map.of("points", String.valueOf(fee), "currency", currencyName) :
          Map.of("money", String.valueOf(fee), "currency", currencyName);
        String messageKey = usePoints ? "insufficient-points" : "insufficient-money";
        player.sendMessage(this.config.getMessage(messageKey, placeholders));
        return;
      }
      
      // Take the fee
      CompletableFuture<Boolean> feePayment = this.plugin.getEconomyManager().takeMoney(player, fee, "withdrawal-fees");
      feePayment.thenAccept(success -> {
        if (!success) {
          player.sendMessage(this.config.getMessage("payment-failed"));
          return;
        }
        
        // Complete the swap
        completeItemSwap(player, slot, storageItem, playerItem, data);
        
        // Send fee message
        Map<String, String> feePlaceholders = usePoints ? 
          Map.of("points", String.valueOf(fee), "currency", currencyName) :
          Map.of("money", String.valueOf(fee), "currency", currencyName);
        player.sendMessage(this.config.getMessage("withdrawal-fee-paid", feePlaceholders));
      });
    });
  }
  
  /**
   * Completes an item swap (withdraws storage item and deposits player item)
   */
  private void completeItemSwap(Player player, int slot, ItemStack storageItem, ItemStack playerItem, PlayerStorageData data) {
    if (player == null || !player.isOnline())
      return;
    
    if (this.config.logTransactions())
      this.plugin.getLogger().info("Player " + player.getName() + " completing item swap in slot " + (slot + 1));
    
    // Remove the storage item and give it to player
    data.setItem(slot, null);
    
    // Give the original storage item (without lore) to the player
    HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack[] { storageItem.clone() });
    if (!leftover.isEmpty()) {
      // Inventory was full, drop the leftover items on the ground
      if (this.config.logTransactions()) {
        this.plugin.getLogger().info("Player " + player.getName() + " inventory was full during swap, dropping " + leftover.size() + " item(s) on ground");
      }
      for (ItemStack dropItem : leftover.values()) {
        player.getWorld().dropItemNaturally(player.getLocation(), dropItem);
      }
      String itemName = storageItem.getType().name().toLowerCase().replace("_", " ");
      Component message = Component.text("Your inventory is full! The " + itemName + " has been dropped on the ground.")
          .color((TextColor)Constants.Colors.HIGHLIGHT);
      player.sendMessage(message);
    }
    
    // Store the player's item in the slot (remove any lore first)
    ItemStack cleanPlayerItem = this.inventoryManager.removeWithdrawalLore(playerItem.clone());
    data.setItem(slot, cleanPlayerItem);
    this.dataManager.markDirty();
    
    // Send swap message
    Map<String, String> placeholders = Map.of("slot", String.valueOf(slot + 1));
    player.sendMessage(this.config.getMessage("item-swapped", placeholders));
    
    // Update GUI if player has storage open
    if (this.inventoryManager.hasStorageOpen(player))
      this.inventoryManager.updateSlotInOpenInventory(player, slot);
  }
}
