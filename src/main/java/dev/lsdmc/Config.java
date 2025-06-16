package dev.lsdmc;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import java.util.*;

public class Config {
    private final StorageSlots plugin;
    private FileConfiguration config;
    private Map<String, String> cachedMessages;
    private final int MAX_SLOTS = 54;
    private final Map<String, Double> withdrawalFeesPoints = new HashMap<>();
    private final Map<String, Double> withdrawalFeesMoney = new HashMap<>();
    private final Map<String, List<String>> commandTriggers = new HashMap<>();

    public Config(StorageSlots plugin) {
        this.plugin = plugin;
        this.cachedMessages = new HashMap<>();
        createDefaultConfig();
    }

    private void createDefaultConfig() {
        plugin.saveDefaultConfig();
        loadConfig();
    }

    public void loadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        cachedMessages.clear();
        setDefaultValues();

        // Load withdrawal fees
        ConfigurationSection feesSection = config.getConfigurationSection("withdrawal-fees");
        if (feesSection != null) {
            for (String type : feesSection.getKeys(false)) {
                ConfigurationSection typeSection = feesSection.getConfigurationSection(type);
                if (typeSection != null) {
                    withdrawalFeesPoints.put(type, typeSection.getDouble("points", 0.0));
                    withdrawalFeesMoney.put(type, typeSection.getDouble("money", 0.0));
                }
            }
        }

        // Load command triggers
        ConfigurationSection triggersSection = config.getConfigurationSection("command-triggers");
        if (triggersSection != null) {
            for (String type : triggersSection.getKeys(false)) {
                List<String> commands = triggersSection.getStringList(type);
                commandTriggers.put(type, commands);
            }
        }
    }

    private void setDefaultValues() {
        // Basic storage settings
        if (!config.contains("storage.slots")) config.set("storage.slots", 9);
        if (!config.contains("storage.title")) config.set("storage.title", "&8Storage");
        if (!config.contains("storage.default-cost")) config.set("storage.default-cost", 1000);
        if (!config.contains("storage.require_progression")) config.set("storage.require_progression", true);
        
        // Auto-save settings
        if (!config.contains("storage.auto-save")) {
            config.set("storage.auto-save.enabled", true);
            config.set("storage.auto-save.interval", 300); // 5 minutes in seconds
        }

        // GUI settings
        if (!config.contains("gui.locked-slot")) {
            config.set("gui.locked-slot.has-rank-material", "RED_STAINED_GLASS_PANE");
            config.set("gui.locked-slot.no-rank-material", "BLACK_STAINED_GLASS_PANE");
            config.set("gui.locked-slot.admin-view-material", "GRAY_STAINED_GLASS_PANE");
            config.set("gui.locked-slot.name", "&c&lLocked Slot #%slot%");
            List<String> lore = Arrays.asList(
                    "&7Cost: &e%cost% %currency%",
                    "&7Required Rank: &e%rank%",
                    "&7Progress: %progress%",
                    "&eClick to purchase!"
            );
            config.set("gui.locked-slot.lore", lore);
        }

        // Security settings
        if (!config.contains("security")) {
            config.set("security.max-slots-per-player", 54);
            config.set("security.max-items-per-slot", 1);
            config.set("security.prevent-item-duplication", true);
            config.set("security.log-transactions", true);
        }

        // Messages
        setDefaultMessages();

        // Validate configuration
        validateConfig();

        plugin.saveConfig();
    }

    private void setDefaultMessages() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("no-permission", "&cYou don't have permission to do this!");
        defaults.put("invalid-slot", "&cInvalid slot number!");
        defaults.put("invalid-number", "&cPlease enter a valid number!");
        defaults.put("already-unlocked", "&cYou've already unlocked this slot!");
        defaults.put("slot-purchased", "&aUnlocked slot %slot% for %cost% %currency%!");
        defaults.put("insufficient-funds", "&cYou need %cost% %currency% to unlock this slot! &7(Balance: %balance%)");
        defaults.put("prohibited-item", "&cYou cannot store this item!");
        defaults.put("rank-required", "&cYou need %rank% to unlock this slot!");
        defaults.put("slot-available", "&eThis slot costs %cost% %currency% to unlock!");
        defaults.put("storage-reset", "&aAll storage data has been reset!");
        defaults.put("player-storage-reset", "&aStorage data has been reset for %player%!");
        defaults.put("player-not-found", "&cPlayer not found!");
        defaults.put("previous-slot-required", "&cYou must unlock the previous slot first!");
        defaults.put("cant-access-slot", "&cYou cannot access this slot yet!");
        defaults.put("max-slots-reached", "&cYou have reached the maximum number of storage slots!");
        defaults.put("max-items-per-slot", "&cYou can only store %max% item(s) per slot!");

        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            if (!config.contains("messages." + entry.getKey())) {
                config.set("messages." + entry.getKey(), entry.getValue());
            }
        }
    }

    private void validateConfig() {
        int slots = config.getInt("storage.slots", 9);
        if (slots < 1 || slots > MAX_SLOTS) {
            plugin.getLogger().warning("Invalid slot count configured. Setting to default (9).");
            config.set("storage.slots", 9);
        }

        // Validate auto-save settings
        int autoSaveInterval = config.getInt("storage.auto-save.interval", 300);
        if (autoSaveInterval < 60) { // Minimum 1 minute
            plugin.getLogger().warning("Auto-save interval too low. Setting to minimum (60 seconds).");
            config.set("storage.auto-save.interval", 60);
        }

        // Validate security settings
        int maxSlotsPerPlayer = config.getInt("security.max-slots-per-player", 54);
        if (maxSlotsPerPlayer < 1 || maxSlotsPerPlayer > MAX_SLOTS) {
            plugin.getLogger().warning("Invalid max slots per player. Setting to default (54).");
            config.set("security.max-slots-per-player", 54);
        }

        int maxItemsPerSlot = config.getInt("security.max-items-per-slot", 1);
        if (maxItemsPerSlot < 1) {
            plugin.getLogger().warning("Invalid max items per slot. Setting to default (1).");
            config.set("security.max-items-per-slot", 1);
        }

        validateMaterial("gui.locked-slot.has-rank-material", "RED_STAINED_GLASS_PANE");
        validateMaterial("gui.locked-slot.no-rank-material", "BLACK_STAINED_GLASS_PANE");
        validateMaterial("gui.locked-slot.admin-view-material", "GRAY_STAINED_GLASS_PANE");
    }

    private void validateMaterial(String path, String defaultMaterial) {
        try {
            Material.valueOf(config.getString(path, defaultMaterial).toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material at " + path + ". Setting to default.");
            config.set(path, defaultMaterial);
        }
    }

    public ItemStack getLockedSlotItem(int slot, boolean hasRequirements, Player player, String currencyName, double cost) {
        String materialPath = hasRequirements ?
                "gui.locked-slot.has-rank-material" : "gui.locked-slot.no-rank-material";
        Material material = getMaterial(materialPath);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Handle cost bypass
        if (player.hasPermission("storageslots.bypass.cost")) {
            cost = 0;
        }

        meta.setDisplayName(formatText(getString("gui.locked-slot.name", "&c&lLocked Slot #%slot%")
                .replace("%slot%", String.valueOf(slot + 1))));

        List<String> lore = new ArrayList<>();
        for (String line : getStringList("gui.locked-slot.lore")) {
            // Handle special cases
            if (line.contains("%cost%") && player.hasPermission("storageslots.bypass.cost")) {
                line = "&aFree (Admin)";
            } else if (line.contains("%rank%") && player.hasPermission("storageslots.bypass.rank")) {
                continue;
            } else if (line.contains("%progress%")) {
                line = hasRequirements ? "&aAvailable to purchase!" : "&cRequires previous slot!";
            }

            line = formatText(line
                    .replace("%slot%", String.valueOf(slot + 1))
                    .replace("%cost%", String.format("%,d", (int)cost))
                    .replace("%currency%", currencyName)
                    .replace("%rank%", getRankDisplay(getRequiredRank(slot))));

            lore.add(line);
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack getLockedSlotDisplayItem(int slot) {
        Material material = getMaterial("gui.locked-slot.admin-view-material");
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(formatText("&c&lLocked Slot #" + (slot + 1)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material getMaterial(String path) {
        try {
            return Material.valueOf(getString(path, "GRAY_STAINED_GLASS_PANE").toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.GRAY_STAINED_GLASS_PANE;
        }
    }

    private String formatText(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    // Basic getters and utility methods
    public int getStorageSlots() {
        return Math.min(Math.max(1, config.getInt("storage.slots", 9)), MAX_SLOTS);
    }

    public String getStorageTitle(Player player) {
        String title = getString("storage.title", "&8Storage");
        if (player != null) {
            title = title.replace("%player%", player.getName());
        }
        return formatText(title);
    }

    public double getSlotCost(int slot) {
        if (!isValidSlot(slot)) return 0.0;
        return config.getDouble("storage.costs.slot-" + (slot + 1),
                config.getDouble("storage.default-cost", 1000.0));
    }

    public void setSlotCost(int slot, double cost) {
        if (!isValidSlot(slot)) return;
        config.set("storage.costs.slot-" + (slot + 1), cost);
        plugin.saveConfig();
    }

    public String getRequiredRank(int slot) {
        if (!isValidSlot(slot)) return "";
        return getString("ranks.slot-requirements.slot-" + (slot + 1), "");
    }

    public String getRankDisplay(String permission) {
        if (permission == null || permission.isEmpty()) return "";
        return formatText(getString("ranks.display-names." + permission, permission));
    }

    public boolean checkRankRequirement(Player player, String requiredRank) {
        if (player.hasPermission("storageslots.bypass.rank")) return true;
        if (requiredRank == null || requiredRank.isEmpty()) return true;
        return plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId())
                .getCachedData().getPermissionData().checkPermission(requiredRank).asBoolean();
    }

    public boolean isProhibitedItem(ItemStack item) {
        if (item == null) return false;
        List<String> prohibited = getStringList("storage.prohibited-items");
        return prohibited.contains(item.getType().name());
    }

    public boolean isProgressionRequired() {
        return config.getBoolean("storage.require_progression", true);
    }

    public String getMessage(String key) {
        return cachedMessages.computeIfAbsent(key, k ->
                formatText(getString("messages." + k, "Missing message: " + k)));
    }

    public String getString(String path, String def) {
        return config.getString(path, def);
    }

    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }

    public boolean isValidSlot(int slot) {
        return slot >= 0 && slot < getStorageSlots();
    }

    public void reloadConfig() {
        loadConfig();
    }

    // New getters for security settings
    public boolean isAutoSaveEnabled() {
        return config.getBoolean("storage.auto-save.enabled", true);
    }

    public int getAutoSaveInterval() {
        return config.getInt("storage.auto-save.interval", 300);
    }

    public int getMaxSlotsPerPlayer() {
        return Math.min(config.getInt("security.max-slots-per-player", 54), MAX_SLOTS);
    }

    public int getMaxItemsPerSlot() {
        return config.getInt("security.max-items-per-slot", 1);
    }

    public boolean preventItemDuplication() {
        return config.getBoolean("security.prevent-item-duplication", true);
    }

    public boolean logTransactions() {
        return config.getBoolean("security.log-transactions", true);
    }

    // Withdrawal fee methods
    public double getWithdrawalFeePoints(String slotType) {
        return withdrawalFeesPoints.getOrDefault(slotType, 0.0);
    }

    public double getWithdrawalFeeMoney(String slotType) {
        return withdrawalFeesMoney.getOrDefault(slotType, 0.0);
    }

    public String getSlotType(int slot) {
        if (slot < 0 || slot >= getStorageSlots()) {
            return "regular";
        }

        // Check donor ranks first
        for (String rank : getDonorRanks()) {
            ConfigurationSection rankSection = config.getConfigurationSection("donor-ranks." + rank);
            if (rankSection != null) {
                List<Integer> slots = rankSection.getIntegerList("slots");
                if (slots.contains(slot)) {
                    return rank;
                }
            }
        }

        // Check regular ranks
        for (String rank : getRanks()) {
            ConfigurationSection rankSection = config.getConfigurationSection("ranks." + rank);
            if (rankSection != null) {
                List<Integer> slots = rankSection.getIntegerList("slots");
                if (slots.contains(slot)) {
                    return rank;
                }
            }
        }

        return "regular";
    }

    public List<String> getCommandTriggers(String type) {
        return commandTriggers.getOrDefault(type, new ArrayList<>());
    }

    public String formatCommandTrigger(String command, String player, double cost, int points) {
        return command
                .replace("%player%", player)
                .replace("%cost%", String.valueOf(cost))
                .replace("%points%", String.valueOf(points));
    }

    // Donor-related methods
    public boolean isDonorEnabled() {
        return config.getBoolean("donor.enabled", true);
    }

    public Set<String> getDonorRanks() {
        return new HashSet<>(config.getConfigurationSection("donor.ranks").getKeys(false));
    }

    public Set<String> getRanks() {
        return new HashSet<>(config.getConfigurationSection("ranks.slot-requirements").getKeys(false));
    }

    public String getDonorRankDisplayName(String rank) {
        return formatText(config.getString("donor.ranks." + rank + ".display-name", rank));
    }

    public String getDonorRankPermission(String rank) {
        return config.getString("donor.ranks." + rank + ".permission", "storageslots.donor." + rank);
    }

    public int getDonorRankSlots(String rank) {
        return config.getInt("donor.ranks." + rank + ".slots", 0);
    }

    public List<String> getDonorRankFeatures(String rank) {
        return config.getStringList("donor.ranks." + rank + ".features");
    }

    public boolean areDonorSlotsSeparate() {
        return config.getBoolean("donor.slots.separate", true);
    }

    public boolean doDonorSlotsPersist() {
        return config.getBoolean("donor.slots.persist", true);
    }

    public boolean areDonorSlotsPurchasable() {
        return config.getBoolean("donor.slots.purchasable", false);
    }

    public double getDonorSlotCostMultiplier() {
        return config.getDouble("donor.slots.cost-multiplier", 2.0);
    }

    public Material getDonorSlotMaterial() {
        try {
            return Material.valueOf(config.getString("gui.donor-slot.material", "GOLD_BLOCK").toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.GOLD_BLOCK;
        }
    }

    public String getDonorSlotName(int slot) {
        return formatText(config.getString("gui.donor-slot.name", "&6&lDonor Slot #%slot%")
                .replace("%slot%", String.valueOf(slot + 1)));
    }

    public List<String> getDonorSlotLore(String donorRank, List<String> features) {
        List<String> lore = new ArrayList<>();
        for (String line : config.getStringList("gui.donor-slot.lore")) {
            if (line.contains("%donor_rank%")) {
                line = line.replace("%donor_rank%", getDonorRankDisplayName(donorRank));
            } else if (line.contains("%features%")) {
                for (String feature : features) {
                    lore.add("&7- " + formatFeatureName(feature));
                }
                continue;
            }
            lore.add(formatText(line));
        }
        return lore;
    }

    private String formatFeatureName(String feature) {
        switch (feature) {
            case "unlimited_items_per_slot":
                return "&eUnlimited items per slot";
            case "bypass_prohibited_items":
                return "&eCan store prohibited items";
            case "instant_purchase":
                return "&eInstant slot purchase";
            case "bypass_rank_requirements":
                return "&eBypass rank requirements";
            default:
                return "&e" + feature.replace("_", " ");
        }
    }

    public boolean isSafezoneEnabled() {
        return config.getBoolean("storage.safezone.enabled", true);
    }

    public String getSafezoneDetectionMethod() {
        return config.getString("storage.safezone.detection-method", "region");
    }

    public String getSafezoneRegionName() {
        return config.getString("storage.safezone.region-name", "safezone");
    }

    public int getSafezonePvPPriority() {
        return config.getInt("storage.safezone.pvp-priority", 0);
    }

    public String getSafezoneMessage() {
        return getMessage("storage.safezone.message");
    }
}