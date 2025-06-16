package dev.lsdmc;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.List;

public class StorageConfig {
    private final FileConfiguration config;

    public StorageConfig(FileConfiguration config) {
        this.config = config;
    }

    public String getCurrencyName() {
        return config.getString("currency.name", "points");
    }

    public String getPointsRemoveCommand() {
        return config.getString("points.remove-command", "points take %player% %amount%");
    }

    public String getMessage(String key) {
        return config.getString("messages." + key, "Message not found: " + key);
    }

    public String getSafezoneMessage() {
        return getMessage("safezone");
    }

    public boolean logTransactions() {
        return config.getBoolean("log-transactions", true);
    }

    public boolean doDonorSlotsPersist() {
        return config.getBoolean("donor-slots.persist", true);
    }

    public List<String> getDonorRanks() {
        return config.getStringList("donor-ranks");
    }

    public String getDonorRankPermission(String rank) {
        return config.getString("donor-ranks." + rank + ".permission", "storageslots.donor." + rank);
    }

    public int getDonorRankSlots(String rank) {
        return config.getInt("donor-ranks." + rank + ".slots", 0);
    }

    public List<String> getDonorRankFeatures(String rank) {
        return config.getStringList("donor-ranks." + rank + ".features");
    }

    public double getDonorSlotCostMultiplier() {
        return config.getDouble("donor-slots.cost-multiplier", 1.0);
    }

    public double getSlotCost(int slot) {
        return config.getDouble("slot-costs." + slot, 0.0);
    }

    public String getSlotType(int slot) {
        return config.getString("slot-types." + slot, "default");
    }

    public int getWithdrawalFeePoints(String slotType) {
        return config.getInt("withdrawal-fees." + slotType + ".points", 0);
    }

    public double getWithdrawalFeeMoney(String slotType) {
        return config.getDouble("withdrawal-fees." + slotType + ".money", 0.0);
    }

    public boolean areDonorSlotsPurchasable() {
        return config.getBoolean("donor-slots.purchasable", true);
    }

    public String getInventoryTitle() {
        return config.getString("inventory.title", "Storage Slots");
    }
} 