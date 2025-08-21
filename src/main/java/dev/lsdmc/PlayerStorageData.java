package dev.lsdmc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public class PlayerStorageData implements Serializable, JsonSerializable {
  private static final long serialVersionUID = 1L;
  
  private final UUID playerId;
  
  private final Map<Integer, ItemStack> items;
  
  private final Set<Integer> unlockedSlots;
  
  private final Set<Integer> donorSlots;
  
  private String currentDonorRank;
  

  
  private boolean hasSeenNewSlotNotification;
  
  private long lastReminderTime;
  
  private String lastNotifiedRank;
  
  public PlayerStorageData(UUID playerId) {
    this.playerId = playerId;
    this.items = new HashMap<>();
    this.unlockedSlots = new HashSet<>();
    this.donorSlots = new HashSet<>();
    this.currentDonorRank = null;
    this.hasSeenNewSlotNotification = false;
    this.lastReminderTime = 0L;
    this.lastNotifiedRank = null;
  }
  
  public UUID getPlayerId() {
    return this.playerId;
  }
  
  public Map<Integer, ItemStack> getItems() {
    return new HashMap<>(this.items);
  }
  
  public Set<Integer> getUnlockedSlots() {
    return new HashSet<>(this.unlockedSlots);
  }
  
  public Set<Integer> getDonorSlots() {
    return new HashSet<>(this.donorSlots);
  }
  
  public boolean hasSlotUnlocked(int slot) {
    return !(!this.unlockedSlots.contains(Integer.valueOf(slot)) && !this.donorSlots.contains(Integer.valueOf(slot)));
  }
  
  public boolean isDonorSlot(int slot) {
    return this.donorSlots.contains(Integer.valueOf(slot));
  }
  
  public void unlockSlot(int slot) {
    this.unlockedSlots.add(Integer.valueOf(slot));
  }
  
  public void unlockDonorSlot(int slot) {
    this.donorSlots.add(Integer.valueOf(slot));
  }
  
  public ItemStack getItem(int slot) {
    return this.items.get(Integer.valueOf(slot));
  }
  
  public void setItem(int slot, ItemStack item) {
    if (item == null) {
      this.items.remove(Integer.valueOf(slot));
    } else if (hasSlotUnlocked(slot)) {
      this.items.put(Integer.valueOf(slot), item.clone());
    } 
  }
  
  public int getHighestUnlockedSlot() {
    if (this.unlockedSlots.isEmpty() && this.donorSlots.isEmpty())
      return -1; 
    int regularMax = this.unlockedSlots.isEmpty() ? -1 : ((Integer)Collections.<Integer>max(this.unlockedSlots)).intValue();
    int donorMax = this.donorSlots.isEmpty() ? -1 : ((Integer)Collections.<Integer>max(this.donorSlots)).intValue();
    return Math.max(regularMax, donorMax);
  }
  
    public boolean hasNextSlotUnlocked(int slot) {
    // Check if the next slot (slot + 1) is unlocked in either regular or donor slots
    return this.unlockedSlots.contains(Integer.valueOf(slot + 1)) || this.donorSlots.contains(Integer.valueOf(slot + 1));
  }

  public boolean hasPreviousSlotUnlocked(int slot) {
    // For slot 0, there's no previous slot
    if (slot == 0) return true;
    // Check if the previous slot (slot - 1) is unlocked in either regular or donor slots
    return this.unlockedSlots.contains(Integer.valueOf(slot - 1)) || this.donorSlots.contains(Integer.valueOf(slot - 1));
  }
  
  public void lockSlot(int slot) {
    this.unlockedSlots.remove(Integer.valueOf(slot));
    this.donorSlots.remove(Integer.valueOf(slot));
    this.items.remove(Integer.valueOf(slot));
  }
  
  public void clear() {
    this.items.clear();
    this.unlockedSlots.clear();
    this.donorSlots.clear();
    this.currentDonorRank = null;
  }
  
  public boolean hasAnyUnlockedSlots() {
    return !(this.unlockedSlots.isEmpty() && this.donorSlots.isEmpty());
  }
  
  public int getUnlockedSlotCount() {
    return this.unlockedSlots.size() + this.donorSlots.size();
  }
  
  public List<ItemStack> getAllItems() {
    List<ItemStack> allItems = new ArrayList<>();
    Iterator<Integer> iterator;
    for (iterator = this.unlockedSlots.iterator(); iterator.hasNext(); ) {
      int slot = ((Integer)iterator.next()).intValue();
      ItemStack item = this.items.get(Integer.valueOf(slot));
      if (item != null)
        allItems.add(item.clone()); 
    } 
    for (iterator = this.donorSlots.iterator(); iterator.hasNext(); ) {
      int slot = ((Integer)iterator.next()).intValue();
      ItemStack item = this.items.get(Integer.valueOf(slot));
      if (item != null)
        allItems.add(item.clone()); 
    } 
    return allItems;
  }
  
  public void dropItems() {
    this.items.clear();
  }
  
  public String getCurrentDonorRank() {
    return this.currentDonorRank;
  }
  
  public void setCurrentDonorRank(String rank) {
    this.currentDonorRank = rank;
  }
  
  public boolean hasSeenNewSlotNotification() {
    return this.hasSeenNewSlotNotification;
  }
  
  public void setSeenNewSlotNotification(boolean seen) {
    this.hasSeenNewSlotNotification = seen;
  }
  
  public long getLastReminderTime() {
    return this.lastReminderTime;
  }
  
  public void setLastReminderTime(long time) {
    this.lastReminderTime = time;
  }
  
  public String getLastNotifiedRank() {
    return this.lastNotifiedRank;
  }
  
  public void setLastNotifiedRank(String rank) {
    this.lastNotifiedRank = rank;
  }
  
  public void resetNotificationStatus() {
    this.hasSeenNewSlotNotification = false;
    this.lastReminderTime = 0L;
  }
  
  public void migrateSlot(int oldSlot, int newSlot) {
    if (hasSlotUnlocked(oldSlot)) {
      ItemStack item = this.items.remove(Integer.valueOf(oldSlot));
      boolean wasDonorSlot = this.donorSlots.remove(Integer.valueOf(oldSlot));
      this.unlockedSlots.remove(Integer.valueOf(oldSlot));
      if (item != null)
        this.items.put(Integer.valueOf(newSlot), item); 
      if (wasDonorSlot) {
        this.donorSlots.add(Integer.valueOf(newSlot));
      } else {
        this.unlockedSlots.add(Integer.valueOf(newSlot));
      } 
    } 
  }
  
  public boolean verifyDataIntegrity() {
    for (Iterator<Integer> iterator = this.items.keySet().iterator(); iterator.hasNext(); ) {
      int slot = ((Integer)iterator.next()).intValue();
      if (!this.unlockedSlots.contains(Integer.valueOf(slot)) && !this.donorSlots.contains(Integer.valueOf(slot)))
        return false; 
    } 
    return true;
  }
  
  public boolean equals(Object o) {
    if (this == o)
      return true; 
    if (o == null || getClass() != o.getClass())
      return false; 
    PlayerStorageData that = (PlayerStorageData)o;
    return Objects.equals(this.playerId, that.playerId);
  }
  
  public int hashCode() {
    return Objects.hash(new Object[] { this.playerId });
  }
  
  public void serialize(JsonObject json) {
    JsonArray donorSlotsArray = new JsonArray();
    for (Iterator<Integer> iterator = this.donorSlots.iterator(); iterator.hasNext(); ) {
      int slot = ((Integer)iterator.next()).intValue();
      donorSlotsArray.add(Integer.valueOf(slot));
    } 
    json.add("donorSlots", (JsonElement)donorSlotsArray);
  }
  
  public void deserialize(JsonObject json) {
    if (json.has("donorSlots")) {
      JsonArray donorSlotsArray = json.getAsJsonArray("donorSlots");
      this.donorSlots.clear();
      for (JsonElement element : donorSlotsArray)
        this.donorSlots.add(Integer.valueOf(element.getAsInt())); 
    } 
  }
}
