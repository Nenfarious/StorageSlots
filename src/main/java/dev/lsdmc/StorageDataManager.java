package dev.lsdmc;

import dev.lsdmc.utils.Constants;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public class StorageDataManager {
  private final StorageSlots plugin;
  
  private final File storageFile;
  
  private final YamlConfiguration storageData;
  
  private final Map<UUID, PlayerStorageData> playerData;
  
  private boolean savePending;
  
  public StorageDataManager(StorageSlots plugin) {
    this.plugin = plugin;
    this.storageFile = new File(plugin.getDataFolder(), "storage.yml");
    this.storageData = YamlConfiguration.loadConfiguration(this.storageFile);
    this.playerData = new ConcurrentHashMap<>();
    loadData();
  }
  
  public PlayerStorageData getPlayerData(UUID playerId) {
    return this.playerData.computeIfAbsent(playerId, PlayerStorageData::new);
  }
  
  public Set<UUID> getAllStoredPlayerIds() {
    return new HashSet<>(this.playerData.keySet());
  }
  
  public void markDirty() {
    this.savePending = true;
  }
  
  public boolean isSavePending() {
    return this.savePending;
  }
  
  public void saveData() {
    if (!this.savePending)
      return; 
    try {
      if (this.storageData.contains("unlocked-slots"))
        this.storageData.set("unlocked-slots", null); 
      if (this.storageData.contains("stored-items"))
        this.storageData.set("stored-items", null); 
      if (this.storageData.contains("donor-slots"))
        this.storageData.set("donor-slots", null); 
      if (this.storageData.contains("donor-ranks"))
        this.storageData.set("donor-ranks", null); 
      for (Map.Entry<UUID, PlayerStorageData> entry : this.playerData.entrySet()) {
        String uuidStr = ((UUID)entry.getKey()).toString();
        PlayerStorageData data = entry.getValue();
        List<Integer> allUnlockedSlots = new ArrayList<>(data.getUnlockedSlots());
        this.storageData.set("unlocked-slots." + uuidStr, allUnlockedSlots);
        List<Integer> donorSlotsList = new ArrayList<>(data.getDonorSlots());
        if (!donorSlotsList.isEmpty())
          this.storageData.set("donor-slots." + uuidStr, donorSlotsList); 
        if (data.getCurrentDonorRank() != null)
          this.storageData.set("donor-ranks." + uuidStr, data.getCurrentDonorRank()); 
        for (Iterator<Integer> iterator = data.getUnlockedSlots().iterator(); iterator.hasNext(); ) {
          int slot = ((Integer)iterator.next()).intValue();
          ItemStack item = data.getItem(slot);
          if (item != null && !item.getType().isAir())
            this.storageData.set("stored-items." + uuidStr + "." + slot, item); 
        } 
      } 
      this.storageData.save(this.storageFile);
      this.savePending = false;
      if (this.plugin.getConfig().getBoolean("debug.enabled", false))
        this.plugin.getLogger().info("Successfully saved storage data for " + this.playerData.size() + " players"); 
    } catch (IOException e) {
      this.plugin.getLogger().severe("Failed to save storage data: " + e.getMessage());
    } 
  }
  
  private void loadData() {
    if (!this.storageFile.exists())
      return; 
    if (this.storageData.contains("unlocked-slots"))
      loadUnlockedSlots(); 
    if (this.storageData.contains("donor-slots"))
      loadDonorSlots(); 
    if (this.storageData.contains("donor-ranks"))
      loadDonorRanks(); 
    if (this.storageData.contains("stored-items"))
      loadStoredItems(); 
    if (this.plugin.getConfig().getBoolean("debug.enabled", false))
      this.plugin.getLogger().info("Successfully loaded storage data for " + this.playerData.size() + " players"); 
  }
  
  private void loadUnlockedSlots() {
    ConfigurationSection unlockedSection = this.storageData.getConfigurationSection("unlocked-slots");
    if (unlockedSection == null)
      return; 
    for (String uuidStr : unlockedSection.getKeys(false)) {
      try {
        UUID uuid = UUID.fromString(uuidStr);
        PlayerStorageData data = getPlayerData(uuid);
        List<Integer> slots = this.storageData.getIntegerList("unlocked-slots." + uuidStr);
        slots.stream()
          .filter(slot -> (slot.intValue() >= 0 && slot.intValue() < 54))
          .forEach(slot -> {
              if (Constants.Slots.isDonorSlot(slot.intValue())) {
                data.unlockDonorSlot(slot.intValue());
              } else {
                data.unlockSlot(slot.intValue());
              } 
            });
      } catch (IllegalArgumentException e) {
        this.plugin.getLogger().warning("Invalid UUID in storage data: " + uuidStr);
      } 
    } 
  }
  
  private void loadDonorSlots() {
    ConfigurationSection donorSection = this.storageData.getConfigurationSection("donor-slots");
    if (donorSection == null)
      return; 
    for (String uuidStr : donorSection.getKeys(false)) {
      try {
        UUID uuid = UUID.fromString(uuidStr);
        PlayerStorageData data = getPlayerData(uuid);
        List<Integer> donorSlots = this.storageData.getIntegerList("donor-slots." + uuidStr);
        donorSlots.stream()
          .filter(slot -> Constants.Slots.isDonorSlot(slot.intValue()))
          .forEach(data::unlockDonorSlot);
      } catch (IllegalArgumentException e) {
        this.plugin.getLogger().warning("Invalid UUID in donor slots data: " + uuidStr);
      } 
    } 
  }
  
  private void loadDonorRanks() {
    ConfigurationSection donorRankSection = this.storageData.getConfigurationSection("donor-ranks");
    if (donorRankSection == null)
      return; 
    for (String uuidStr : donorRankSection.getKeys(false)) {
      try {
        UUID uuid = UUID.fromString(uuidStr);
        PlayerStorageData data = getPlayerData(uuid);
        String donorRank = this.storageData.getString("donor-ranks." + uuidStr);
        if (donorRank != null)
          data.setCurrentDonorRank(donorRank); 
      } catch (IllegalArgumentException e) {
        this.plugin.getLogger().warning("Invalid UUID in donor ranks data: " + uuidStr);
      } 
    } 
  }
  
  private void loadStoredItems() {
    ConfigurationSection itemsSection = this.storageData.getConfigurationSection("stored-items");
    if (itemsSection == null)
      return; 
    for (String uuidStr : itemsSection.getKeys(false)) {
      try {
        UUID uuid = UUID.fromString(uuidStr);
        PlayerStorageData data = getPlayerData(uuid);
        ConfigurationSection playerItemsSection = itemsSection.getConfigurationSection(uuidStr);
        if (playerItemsSection != null)
          for (String slotStr : playerItemsSection.getKeys(false)) {
            try {
              int slot = Integer.parseInt(slotStr);
              if (slot >= 0 && slot < 54 && data.hasSlotUnlocked(slot)) {
                ItemStack item = this.storageData.getItemStack("stored-items." + uuidStr + "." + slotStr);
                if (item != null && !this.plugin.getConfigManager().isProhibitedItem(item))
                  data.setItem(slot, item); 
              } 
            } catch (NumberFormatException e) {
              this.plugin.getLogger().warning("Invalid slot number in storage data: " + slotStr);
            } 
          }  
      } catch (IllegalArgumentException e) {
        this.plugin.getLogger().warning("Invalid UUID in storage data: " + uuidStr);
      } 
    } 
  }
  
  public void resetAllData() {
    this.playerData.clear();
    this.savePending = true;
    saveData();
  }
  
  public void resetPlayerData(UUID playerId) {
    this.playerData.remove(playerId);
    this.savePending = true;
    saveData();
  }
}
