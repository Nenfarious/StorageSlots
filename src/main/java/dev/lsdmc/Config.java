package dev.lsdmc;

import org.bukkit.configuration.file.FileConfiguration;
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
    }

    private void setDefaultValues() {
        // Basic storage settings
        if (!config.contains("storage.slots")) config.set("storage.slots", 9);
        if (!config.contains("storage.title")) config.set("storage.title", "&8Storage");
        if (!config.contains("storage.default-cost")) config.set("storage.default-cost", 1000);
        if (!config.contains("storage.require_progression")) config.set("storage.require_progression", true);

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
}