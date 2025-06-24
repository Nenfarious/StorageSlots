package dev.lsdmc;

import org.bukkit.inventory.ItemStack;
import java.util.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.Serializable;

public class PlayerStorageData implements Serializable, JsonSerializable {
    private static final long serialVersionUID = 1L;
    private final UUID playerId;
    private final Map<Integer, ItemStack> items;
    private final Set<Integer> unlockedSlots;
    private final Set<Integer> donorSlots;
    private String currentDonorRank;
    private final Set<String> activeDonorFeatures;
    private final Set<String> donorFeatures;
    private boolean dirty;
    private boolean hasSeenNewSlotNotification;
    private long lastReminderTime;
    private String lastNotifiedRank;

    public PlayerStorageData(UUID playerId) {
        this.playerId = playerId;
        this.items = new HashMap<>();
        this.unlockedSlots = new HashSet<>();
        this.donorSlots = new HashSet<>();
        this.currentDonorRank = null;
        this.activeDonorFeatures = new HashSet<>();
        this.donorFeatures = new HashSet<>();
        this.hasSeenNewSlotNotification = false;
        this.lastReminderTime = 0;
        this.lastNotifiedRank = null;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Map<Integer, ItemStack> getItems() {
        return new HashMap<>(items);
    }

    public Set<Integer> getUnlockedSlots() {
        return new HashSet<>(unlockedSlots);
    }

    public Set<Integer> getDonorSlots() {
        return new HashSet<>(donorSlots);
    }

    public boolean hasSlotUnlocked(int slot) {
        return unlockedSlots.contains(slot) || donorSlots.contains(slot);
    }

    public boolean isDonorSlot(int slot) {
        return donorSlots.contains(slot);
    }

    public void unlockSlot(int slot) {
        unlockedSlots.add(slot);
    }

    public void unlockDonorSlot(int slot) {
        donorSlots.add(slot);
    }

    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    public void setItem(int slot, ItemStack item) {
        if (item == null) {
            items.remove(slot);
        } else if (hasSlotUnlocked(slot)) {
            items.put(slot, item.clone());
        }
    }

    public int getHighestUnlockedSlot() {
        if (unlockedSlots.isEmpty() && donorSlots.isEmpty()) {
            return -1;
        }
        int regularMax = unlockedSlots.isEmpty() ? -1 : Collections.max(unlockedSlots);
        int donorMax = donorSlots.isEmpty() ? -1 : Collections.max(donorSlots);
        return Math.max(regularMax, donorMax);
    }

    public boolean hasNextSlotUnlocked(int slot) {
        return unlockedSlots.contains(slot + 1) || donorSlots.contains(slot + 1);
    }

    public boolean hasPreviousSlotUnlocked(int slot) {
        return slot == 0 || unlockedSlots.contains(slot - 1) || donorSlots.contains(slot - 1);
    }

    public void lockSlot(int slot) {
        unlockedSlots.remove(slot);
        donorSlots.remove(slot);
        items.remove(slot);
    }

    public void clear() {
        items.clear();
        unlockedSlots.clear();
        donorSlots.clear();
        currentDonorRank = null;
        activeDonorFeatures.clear();
        donorFeatures.clear();
    }

    public boolean hasAnyUnlockedSlots() {
        return !unlockedSlots.isEmpty() || !donorSlots.isEmpty();
    }

    public int getUnlockedSlotCount() {
        return unlockedSlots.size() + donorSlots.size();
    }

    public List<ItemStack> getAllItems() {
        List<ItemStack> allItems = new ArrayList<>();
        for (int slot : unlockedSlots) {
            ItemStack item = items.get(slot);
            if (item != null) {
                allItems.add(item.clone());
            }
        }
        for (int slot : donorSlots) {
            ItemStack item = items.get(slot);
            if (item != null) {
                allItems.add(item.clone());
            }
        }
        return allItems;
    }

    public void dropItems() {
        items.clear();
    }

    public String getCurrentDonorRank() {
        return currentDonorRank;
    }

    public void setCurrentDonorRank(String rank) {
        this.currentDonorRank = rank;
    }

    public Set<String> getActiveDonorFeatures() {
        return new HashSet<>(activeDonorFeatures);
    }

    public void addDonorFeature(String feature) {
        activeDonorFeatures.add(feature);
        donorFeatures.add(feature);
    }

    public void removeDonorFeature(String feature) {
        activeDonorFeatures.remove(feature);
        donorFeatures.remove(feature);
    }

    public boolean hasDonorFeature(String feature) {
        return donorFeatures.contains(feature);
    }

    public Set<String> getDonorFeatures() {
        return new HashSet<>(donorFeatures);
    }

    // Notification tracking methods
    public boolean hasSeenNewSlotNotification() {
        return hasSeenNewSlotNotification;
    }

    public void setSeenNewSlotNotification(boolean seen) {
        this.hasSeenNewSlotNotification = seen;
    }

    public long getLastReminderTime() {
        return lastReminderTime;
    }

    public void setLastReminderTime(long time) {
        this.lastReminderTime = time;
    }

    public String getLastNotifiedRank() {
        return lastNotifiedRank;
    }

    public void setLastNotifiedRank(String rank) {
        this.lastNotifiedRank = rank;
    }

    public void resetNotificationStatus() {
        this.hasSeenNewSlotNotification = false;
        this.lastReminderTime = 0;
    }

    // For data migration or version upgrades
    public void migrateSlot(int oldSlot, int newSlot) {
        if (hasSlotUnlocked(oldSlot)) {
            ItemStack item = items.remove(oldSlot);
            boolean wasDonorSlot = donorSlots.remove(oldSlot);
            unlockedSlots.remove(oldSlot);
            if (item != null) {
                items.put(newSlot, item);
            }
            if (wasDonorSlot) {
                donorSlots.add(newSlot);
            } else {
                unlockedSlots.add(newSlot);
            }
        }
    }

    // Verification method to ensure data consistency
    public boolean verifyDataIntegrity() {
        // Ensure no items exist in locked slots
        for (int slot : items.keySet()) {
            if (!unlockedSlots.contains(slot) && !donorSlots.contains(slot)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerStorageData that = (PlayerStorageData) o;
        return Objects.equals(playerId, that.playerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId);
    }

    @Override
    public void serialize(JsonObject json) {
        JsonArray donorSlotsArray = new JsonArray();
        for (int slot : donorSlots) {
            donorSlotsArray.add(slot);
        }
        json.add("donorSlots", donorSlotsArray);

        JsonArray donorFeaturesArray = new JsonArray();
        for (String feature : donorFeatures) {
            donorFeaturesArray.add(feature);
        }
        json.add("donorFeatures", donorFeaturesArray);
    }

    @Override
    public void deserialize(JsonObject json) {
        if (json.has("donorSlots")) {
            JsonArray donorSlotsArray = json.getAsJsonArray("donorSlots");
            donorSlots.clear();
            for (JsonElement element : donorSlotsArray) {
                donorSlots.add(element.getAsInt());
            }
        }

        if (json.has("donorFeatures")) {
            JsonArray donorFeaturesArray = json.getAsJsonArray("donorFeatures");
            donorFeatures.clear();
            for (JsonElement element : donorFeaturesArray) {
                donorFeatures.add(element.getAsString());
            }
        }
    }
}