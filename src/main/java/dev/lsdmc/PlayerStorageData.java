package dev.lsdmc;

import org.bukkit.inventory.ItemStack;
import java.util.*;

public class PlayerStorageData {
    private final UUID playerId;
    private final Map<Integer, ItemStack> items;
    private final Set<Integer> unlockedSlots;

    public PlayerStorageData(UUID playerId) {
        this.playerId = playerId;
        this.items = new HashMap<>();
        this.unlockedSlots = new TreeSet<>(); // Using TreeSet for ordered slots
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

    public boolean hasSlotUnlocked(int slot) {
        return unlockedSlots.contains(slot);
    }

    public void unlockSlot(int slot) {
        unlockedSlots.add(slot);
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
        if (unlockedSlots.isEmpty()) {
            return -1;
        }
        return Collections.max(unlockedSlots);
    }

    public boolean hasNextSlotUnlocked(int slot) {
        return unlockedSlots.contains(slot + 1);
    }

    public boolean hasPreviousSlotUnlocked(int slot) {
        return slot == 0 || unlockedSlots.contains(slot - 1);
    }

    public void lockSlot(int slot) {
        unlockedSlots.remove(slot);
        items.remove(slot);
    }

    public void clear() {
        items.clear();
        unlockedSlots.clear();
    }

    public boolean hasAnyUnlockedSlots() {
        return !unlockedSlots.isEmpty();
    }

    public int getUnlockedSlotCount() {
        return unlockedSlots.size();
    }

    public List<ItemStack> getAllItems() {
        List<ItemStack> allItems = new ArrayList<>();
        for (int slot : unlockedSlots) {
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

    // For data migration or version upgrades
    public void migrateSlot(int oldSlot, int newSlot) {
        if (hasSlotUnlocked(oldSlot)) {
            ItemStack item = items.remove(oldSlot);
            unlockedSlots.remove(oldSlot);
            if (item != null) {
                items.put(newSlot, item);
            }
            unlockedSlots.add(newSlot);
        }
    }

    // Verification method to ensure data consistency
    public boolean verifyDataIntegrity() {
        // Ensure no items exist in locked slots
        for (int slot : items.keySet()) {
            if (!unlockedSlots.contains(slot)) {
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
}