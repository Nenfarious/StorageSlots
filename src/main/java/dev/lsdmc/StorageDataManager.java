package dev.lsdmc;

import org.bukkit.configuration.file.YamlConfiguration;
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

    private void loadData() {
        if (!storageFile.exists()) return;

        // Load unlocked slots first
        if (storageData.contains("unlocked-slots")) {
            loadUnlockedSlots();
        }

        // Then load items for those slots
        if (storageData.contains("stored-items")) {
            loadStoredItems();
        }

        // Verify data integrity
        verifyDataIntegrity();
    }

    private void loadUnlockedSlots() {
        for (String uuidStr : storageData.getConfigurationSection("unlocked-slots").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                PlayerStorageData data = getPlayerData(uuid);
                List<Integer> slots = storageData.getIntegerList("unlocked-slots." + uuidStr);
                slots.stream()
                        .filter(slot -> slot >= 0 && slot < plugin.getConfigManager().getStorageSlots())
                        .forEach(data::unlockSlot);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in storage data: " + uuidStr);
            }
        }
    }

    private void loadStoredItems() {
        for (String uuidStr : storageData.getConfigurationSection("stored-items").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                PlayerStorageData data = getPlayerData(uuid);

                for (String slotStr : storageData.getConfigurationSection("stored-items." + uuidStr).getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(slotStr);
                        if (slot >= 0 && slot < plugin.getConfigManager().getStorageSlots() && data.hasSlotUnlocked(slot)) {
                            ItemStack item = storageData.getItemStack("stored-items." + uuidStr + "." + slotStr);
                            if (item != null && !plugin.getConfigManager().isProhibitedItem(item)) {
                                data.setItem(slot, item);
                            }
                        }
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid slot number in storage data: " + slotStr);
                    }
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in storage data: " + uuidStr);
            }
        }
    }

    private void verifyDataIntegrity() {
        boolean needsSave = false;
        Iterator<Map.Entry<UUID, PlayerStorageData>> iterator = playerData.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, PlayerStorageData> entry = iterator.next();
            PlayerStorageData data = entry.getValue();

            if (!data.verifyDataIntegrity()) {
                plugin.getLogger().warning("Fixing corrupted storage data for player: " + entry.getKey());
                data.clear();
                needsSave = true;
            }

            // Remove empty player data
            if (!data.hasAnyUnlockedSlots()) {
                iterator.remove();
            }
        }

        if (needsSave) {
            savePending = true;
        }
    }

    public void saveData() {
        storageData.set("unlocked-slots", null);
        storageData.set("stored-items", null);

        for (Map.Entry<UUID, PlayerStorageData> entry : playerData.entrySet()) {
            PlayerStorageData data = entry.getValue();
            if (data.hasAnyUnlockedSlots()) {
                savePlayerData(entry.getKey(), data);
            }
        }

        try {
            storageData.save(storageFile);
            savePending = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save storage data: " + e.getMessage());
        }
    }

    private void savePlayerData(UUID playerId, PlayerStorageData data) {
        // Save unlocked slots
        storageData.set("unlocked-slots." + playerId.toString(),
                new ArrayList<>(data.getUnlockedSlots()));

        // Save items
        Map<Integer, ItemStack> items = data.getItems();
        for (Map.Entry<Integer, ItemStack> itemEntry : items.entrySet()) {
            if (itemEntry.getValue() != null && !itemEntry.getValue().getType().isAir()) {
                storageData.set("stored-items." + playerId.toString() + "." + itemEntry.getKey(),
                        itemEntry.getValue());
            }
        }
    }

    public void resetAllData() {
        playerData.clear();
        if (storageFile.exists()) {
            storageFile.delete();
        }
        storageData.getKeys(false).forEach(key -> storageData.set(key, null));
        try {
            storageData.save(storageFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save empty storage data: " + e.getMessage());
        }
    }

    public void resetPlayerData(UUID playerId) {
        playerData.remove(playerId);
        if (storageFile.exists()) {
            storageData.set("unlocked-slots." + playerId.toString(), null);
            storageData.set("stored-items." + playerId.toString(), null);
            try {
                storageData.save(storageFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save storage data after player reset: " + e.getMessage());
            }
        }
        savePending = true;
    }

    public void markDirty() {
        savePending = true;
    }

    public boolean isSavePending() {
        return savePending;
    }
}