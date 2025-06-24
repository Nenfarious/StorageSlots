package dev.lsdmc;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StorageDataManager {
    private final StorageSlots plugin;
    private final File storageFile;
    private final YamlConfiguration storageData;
    private final Map<UUID, PlayerStorageData> playerData;
    private boolean savePending;

    public StorageDataManager(StorageSlots plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "storage.yml");
        this.storageData = YamlConfiguration.loadConfiguration(storageFile);
        this.playerData = new ConcurrentHashMap<>();
        loadData();
    }

    public PlayerStorageData getPlayerData(UUID playerId) {
        return playerData.computeIfAbsent(playerId, PlayerStorageData::new);
    }

    public Set<UUID> getAllStoredPlayerIds() {
        return new HashSet<>(playerData.keySet());
    }

    public void markDirty() {
        savePending = true;
    }

    public boolean isSavePending() {
        return savePending;
    }

    public void saveData() {
        if (!savePending) return;

        try {
            // Clear existing data first to prevent conflicts
            if (storageData.contains("unlocked-slots")) {
                storageData.set("unlocked-slots", null);
            }
            if (storageData.contains("stored-items")) {
                storageData.set("stored-items", null);
            }
            if (storageData.contains("donor-slots")) {
                storageData.set("donor-slots", null);
            }
            if (storageData.contains("donor-features")) {
                storageData.set("donor-features", null);
            }
            if (storageData.contains("donor-ranks")) {
                storageData.set("donor-ranks", null);
            }
            
            for (Map.Entry<UUID, PlayerStorageData> entry : playerData.entrySet()) {
                String uuidStr = entry.getKey().toString();
                PlayerStorageData data = entry.getValue();

                // Save unlocked slots (both regular and donor)
                List<Integer> allUnlockedSlots = new ArrayList<>(data.getUnlockedSlots());
                storageData.set("unlocked-slots." + uuidStr, allUnlockedSlots);
                
                // Save donor slots specifically
                List<Integer> donorSlotsList = new ArrayList<>(data.getDonorSlots());
                if (!donorSlotsList.isEmpty()) {
                    storageData.set("donor-slots." + uuidStr, donorSlotsList);
                }
                
                // Save current donor rank
                if (data.getCurrentDonorRank() != null) {
                    storageData.set("donor-ranks." + uuidStr, data.getCurrentDonorRank());
                }
                
                // Save donor features
                List<String> donorFeatures = new ArrayList<>(data.getDonorFeatures());
                if (!donorFeatures.isEmpty()) {
                    storageData.set("donor-features." + uuidStr, donorFeatures);
                }

                // Save items for all unlocked slots
                for (int slot : data.getUnlockedSlots()) {
                    ItemStack item = data.getItem(slot);
                    if (item != null && !item.getType().isAir()) {
                        storageData.set("stored-items." + uuidStr + "." + slot, item);
                    }
                }
            }

            storageData.save(storageFile);
            savePending = false;
            
            if (plugin.getConfig().getBoolean("debug.enabled", false)) {
                plugin.getLogger().info("Successfully saved storage data for " + playerData.size() + " players");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save storage data: " + e.getMessage());
        }
    }

    private void loadData() {
        if (!storageFile.exists()) return;

        // Load unlocked slots first
        if (storageData.contains("unlocked-slots")) {
            loadUnlockedSlots();
        }
        
        // Load donor slots
        if (storageData.contains("donor-slots")) {
            loadDonorSlots();
        }
        
        // Load donor ranks
        if (storageData.contains("donor-ranks")) {
            loadDonorRanks();
        }
        
        // Load donor features
        if (storageData.contains("donor-features")) {
            loadDonorFeatures();
        }

        // Then load items for those slots
        if (storageData.contains("stored-items")) {
            loadStoredItems();
        }
        
        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("Successfully loaded storage data for " + playerData.size() + " players");
        }
    }

    private void loadUnlockedSlots() {
        ConfigurationSection unlockedSection = storageData.getConfigurationSection("unlocked-slots");
        if (unlockedSection == null) return;
        
        for (String uuidStr : unlockedSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                PlayerStorageData data = getPlayerData(uuid);
                List<Integer> slots = storageData.getIntegerList("unlocked-slots." + uuidStr);
                
                // Validate slots and unlock them
                slots.stream()
                        .filter(slot -> slot >= 0 && slot < 54) // Allow all possible slots including donor slots
                        .forEach(slot -> {
                            if (slot >= 11 && slot <= 15) {
                                // This is a donor slot
                                data.unlockDonorSlot(slot);
                            } else {
                                // Regular slot
                                data.unlockSlot(slot);
                            }
                        });
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in storage data: " + uuidStr);
            }
        }
    }
    
    private void loadDonorSlots() {
        ConfigurationSection donorSection = storageData.getConfigurationSection("donor-slots");
        if (donorSection == null) return;
        
        for (String uuidStr : donorSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                PlayerStorageData data = getPlayerData(uuid);
                List<Integer> donorSlots = storageData.getIntegerList("donor-slots." + uuidStr);
                
                donorSlots.stream()
                        .filter(slot -> slot >= 11 && slot <= 15)
                        .forEach(data::unlockDonorSlot);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in donor slots data: " + uuidStr);
            }
        }
    }
    
    private void loadDonorRanks() {
        ConfigurationSection donorRankSection = storageData.getConfigurationSection("donor-ranks");
        if (donorRankSection == null) return;
        
        for (String uuidStr : donorRankSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                PlayerStorageData data = getPlayerData(uuid);
                String donorRank = storageData.getString("donor-ranks." + uuidStr);
                
                if (donorRank != null) {
                    data.setCurrentDonorRank(donorRank);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in donor ranks data: " + uuidStr);
            }
        }
    }
    
    private void loadDonorFeatures() {
        ConfigurationSection donorFeatureSection = storageData.getConfigurationSection("donor-features");
        if (donorFeatureSection == null) return;
        
        for (String uuidStr : donorFeatureSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                PlayerStorageData data = getPlayerData(uuid);
                List<String> features = storageData.getStringList("donor-features." + uuidStr);
                
                features.forEach(data::addDonorFeature);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in donor features data: " + uuidStr);
            }
        }
    }

    private void loadStoredItems() {
        ConfigurationSection itemsSection = storageData.getConfigurationSection("stored-items");
        if (itemsSection == null) return;
        
        for (String uuidStr : itemsSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                PlayerStorageData data = getPlayerData(uuid);
                ConfigurationSection playerItemsSection = itemsSection.getConfigurationSection(uuidStr);
                
                if (playerItemsSection != null) {
                    for (String slotStr : playerItemsSection.getKeys(false)) {
                        try {
                            int slot = Integer.parseInt(slotStr);
                            if (slot >= 0 && slot < 54 && data.hasSlotUnlocked(slot)) {
                                ItemStack item = storageData.getItemStack("stored-items." + uuidStr + "." + slotStr);
                                if (item != null && !plugin.getConfigManager().isProhibitedItem(item)) {
                                    data.setItem(slot, item);
                                }
                            }
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Invalid slot number in storage data: " + slotStr);
                        }
                    }
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in storage data: " + uuidStr);
            }
        }
    }

    public void resetAllData() {
        playerData.clear();
        savePending = true;
        saveData();
    }

    public void resetPlayerData(UUID playerId) {
        playerData.remove(playerId);
        savePending = true;
        saveData();
    }
} 