package dev.lsdmc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.lsdmc.utils.Constants;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public final class StorageConfig {
    private final StorageSlots plugin;
    private final FileConfiguration config;
    private final FileConfiguration messages;
    private final MiniMessage miniMessage;
    private final LegacyComponentSerializer legacySerializer;
    
    // Cached values for performance
    private Map<String, Component> cachedMessages;
    private Map<Integer, Double> slotCosts;
    private Map<Integer, String> slotRanks;
    private Map<String, String> rankDisplayNames;
    private List<String> prohibitedItems;
    private Map<String, DonorRank> donorRanks;
    private String messagePrefix;
    
    // Withdrawal fee data structures
    private Map<String, WithdrawalFee> rankGroupFees;
    private Map<String, WithdrawalFee> individualRankFees;
    private Map<String, WithdrawalFee> donorRankFees;
    private WithdrawalFee defaultWithdrawalFee;
    
    public StorageConfig(StorageSlots plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.miniMessage = MiniMessage.miniMessage();
        this.legacySerializer = LegacyComponentSerializer.legacyAmpersand();
        this.cachedMessages = new HashMap<>();
        this.slotCosts = new HashMap<>();
        this.slotRanks = new HashMap<>();
        this.rankDisplayNames = new HashMap<>();
        this.prohibitedItems = new ArrayList<>();
        this.donorRanks = new HashMap<>();
        
        // Withdrawal fee data structures
        this.rankGroupFees = new HashMap<>();
        this.individualRankFees = new HashMap<>();
        this.donorRankFees = new HashMap<>();
        this.defaultWithdrawalFee = new WithdrawalFee(10, 100.0);
        
        // Load messages configuration
        this.messages = loadMessagesConfig();
        
        loadConfiguration();
    }
    
    private FileConfiguration loadMessagesConfig() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        return YamlConfiguration.loadConfiguration(messagesFile);
    }
    
    public void reload() {
        plugin.reloadConfig();
        
        // Reload the main config reference
        FileConfiguration newConfig = plugin.getConfig();
        
        // Reload messages properly
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (messagesFile.exists()) {
            try {
                ((YamlConfiguration) messages).load(messagesFile);
            } catch (Exception e) {
                plugin.getComponentLogger().error(Component.text("Failed to reload messages.yml: " + e.getMessage())
                    .color(Constants.Colors.ERROR));
            }
        }
        
        // Clear caches
        cachedMessages.clear();
        slotCosts.clear();
        slotRanks.clear();
        rankDisplayNames.clear();
        prohibitedItems.clear();
        donorRanks.clear();
        rankGroupFees.clear();
        individualRankFees.clear();
        donorRankFees.clear();
        
        loadConfiguration();
        
        plugin.getComponentLogger().info(Component.text("Configuration and messages reloaded!")
            .color(Constants.Colors.SUCCESS));
    }
    
    private void loadConfiguration() {
        // Validate configuration first
        validateConfiguration();
        
        // Load all sections
        loadSlotCosts();
        loadSlotRanks();
        loadRankDisplayNames();
        loadProhibitedItems();
        loadDonorRanks();
        loadWithdrawalFees();
        loadMessages();
        
        plugin.getComponentLogger().info(Component.text("Configuration loaded successfully!")
            .color(Constants.Colors.SUCCESS));
    }
    
    private void validateConfiguration() {
        boolean hasChanges = false;
        
        // Validate storage slots
        int slots = config.getInt(Constants.Config.STORAGE_SLOTS, Constants.Defaults.STORAGE_SLOTS);
        if (slots < 1 || slots > 54) {
            plugin.getComponentLogger().warn(Component.text("Invalid storage slots value: " + slots + 
                ". Using default: " + Constants.Defaults.STORAGE_SLOTS));
            config.set(Constants.Config.STORAGE_SLOTS, Constants.Defaults.STORAGE_SLOTS);
            hasChanges = true;
        }
        
        // Validate auto-save interval
        int interval = config.getInt(Constants.Config.AUTO_SAVE_INTERVAL, Constants.Defaults.AUTO_SAVE_INTERVAL);
        if (interval < 60) {
            plugin.getComponentLogger().warn(Component.text("Auto-save interval too low: " + interval + 
                ". Using minimum: 60"));
            config.set(Constants.Config.AUTO_SAVE_INTERVAL, 60);
            hasChanges = true;
        }
        
        // Validate max items per slot
        int maxItems = config.getInt(Constants.Config.STORAGE_MAX_ITEMS_PER_SLOT, Constants.Defaults.MAX_ITEMS_PER_SLOT);
        if (maxItems < 1) {
            plugin.getComponentLogger().warn(Component.text("Invalid max items per slot: " + maxItems + 
                ". Using default: " + Constants.Defaults.MAX_ITEMS_PER_SLOT));
            config.set(Constants.Config.STORAGE_MAX_ITEMS_PER_SLOT, Constants.Defaults.MAX_ITEMS_PER_SLOT);
            hasChanges = true;
        }
        
        // Validate donor cost multiplier
        double multiplier = config.getDouble(Constants.Config.DONOR_SLOTS_COST_MULTIPLIER, Constants.Defaults.DONOR_SLOTS_COST_MULTIPLIER);
        if (multiplier <= 0) {
            plugin.getComponentLogger().warn(Component.text("Invalid donor cost multiplier: " + multiplier + 
                ". Using default: " + Constants.Defaults.DONOR_SLOTS_COST_MULTIPLIER));
            config.set(Constants.Config.DONOR_SLOTS_COST_MULTIPLIER, Constants.Defaults.DONOR_SLOTS_COST_MULTIPLIER);
            hasChanges = true;
        }
        
        if (hasChanges) {
            plugin.saveConfig();
        }
    }
    
    private void loadSlotCosts() {
        ConfigurationSection costsSection = config.getConfigurationSection("storage.costs");
        if (costsSection != null) {
            for (String key : costsSection.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key.replace("slot-", "")) - 1;
                    double cost = costsSection.getDouble(key);
                    if (cost >= 0) {
                        slotCosts.put(slot, cost);
                    } else {
                        plugin.getComponentLogger().warn(Component.text("Invalid negative cost for slot " + (slot + 1)));
                    }
                } catch (NumberFormatException e) {
                    plugin.getComponentLogger().warn(Component.text("Invalid slot number in costs configuration: " + key));
                }
            }
        }
    }
    
    private void loadSlotRanks() {
        ConfigurationSection ranksSection = config.getConfigurationSection("ranks.slot-requirements");
        if (ranksSection != null) {
            for (String key : ranksSection.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key.replace("slot-", "")) - 1;
                    String rank = ranksSection.getString(key);
                    if (rank != null && !rank.trim().isEmpty()) {
                        slotRanks.put(slot, rank);
                    }
                } catch (NumberFormatException e) {
                    plugin.getComponentLogger().warn(Component.text("Invalid slot number in rank requirements: " + key));
                }
            }
        }
    }
    
    private void loadRankDisplayNames() {
        ConfigurationSection displaySection = config.getConfigurationSection("ranks.display-names");
        if (displaySection != null) {
            for (String key : displaySection.getKeys(false)) {
                String displayName = displaySection.getString(key);
                if (displayName != null) {
                    rankDisplayNames.put(key, displayName);
                }
            }
        }
    }
    
    private void loadProhibitedItems() {
        prohibitedItems = config.getStringList("storage.prohibited-items");
        if (prohibitedItems.isEmpty()) {
            // Add some sensible defaults
            prohibitedItems = Arrays.asList("BEDROCK", "BARRIER", "COMMAND_BLOCK", "STRUCTURE_BLOCK");
        }
    }
    
    private void loadDonorRanks() {
        ConfigurationSection donorSection = config.getConfigurationSection("donor.ranks");
        if (donorSection != null) {
            for (String rankName : donorSection.getKeys(false)) {
                ConfigurationSection rankSection = donorSection.getConfigurationSection(rankName);
                if (rankSection != null) {
                    DonorRank donorRank = new DonorRank(
                        rankName,
                        rankSection.getString("display-name", rankName),
                        rankSection.getString("permission", "storageslots.donor." + rankName),
                        Math.max(0, rankSection.getInt("slots", 0)),
                        rankSection.getStringList("features")
                    );
                    donorRanks.put(rankName, donorRank);
                }
            }
        }
    }
    
    private void loadWithdrawalFees() {
        // Clear existing data
        rankGroupFees.clear();
        individualRankFees.clear();
        donorRankFees.clear();
        
        // Load default withdrawal fee
        ConfigurationSection defaultSection = config.getConfigurationSection("withdrawal-fees.default");
        if (defaultSection != null) {
            int points = defaultSection.getInt("points", 10);
            double money = defaultSection.getDouble("money", 100.0);
            defaultWithdrawalFee = new WithdrawalFee(points, money);
        }
        
        // Load rank group fees
        ConfigurationSection groupsSection = config.getConfigurationSection("withdrawal-fees.rank-groups");
        if (groupsSection != null) {
            for (String groupName : groupsSection.getKeys(false)) {
                ConfigurationSection groupSection = groupsSection.getConfigurationSection(groupName);
                if (groupSection != null) {
                    int points = groupSection.getInt("points", 0);
                    double money = groupSection.getDouble("money", 0.0);
                    rankGroupFees.put(groupName, new WithdrawalFee(points, money));
                }
            }
        }
        
        // Load individual rank fees
        ConfigurationSection individualSection = config.getConfigurationSection("withdrawal-fees.individual-ranks");
        if (individualSection != null) {
            for (String rankName : individualSection.getKeys(false)) {
                ConfigurationSection rankSection = individualSection.getConfigurationSection(rankName);
                if (rankSection != null) {
                    int points = rankSection.getInt("points", 0);
                    double money = rankSection.getDouble("money", 0.0);
                    individualRankFees.put(rankName, new WithdrawalFee(points, money));
                }
            }
        }
        
        // Load donor rank fees
        ConfigurationSection donorSection = config.getConfigurationSection("withdrawal-fees.donor");
        if (donorSection != null) {
            for (String donorRank : donorSection.getKeys(false)) {
                ConfigurationSection donorRankSection = donorSection.getConfigurationSection(donorRank);
                if (donorRankSection != null) {
                    int points = donorRankSection.getInt("points", 0);
                    double money = donorRankSection.getDouble("money", 0.0);
                    donorRankFees.put(donorRank, new WithdrawalFee(points, money));
                }
            }
        }
    }
    
    private void loadMessages() {
        messagePrefix = messages.getString("prefix", "<gradient:#ff5555:#ffaa00>[StorageSlots]</gradient> ");
        loadMessagesFromSection("", messages);
    }
    
    private void loadMessagesFromSection(String path, ConfigurationSection section) {
        for (String key : section.getKeys(false)) {
            String fullKey = path.isEmpty() ? key : path + "." + key;
            
            if (section.isConfigurationSection(key)) {
                loadMessagesFromSection(fullKey, section.getConfigurationSection(key));
            } else {
                String message = section.getString(key);
                if (message != null) {
                    try {
                        // Replace {prefix} placeholder
                        if (message.contains("{prefix}")) {
                            Component prefix = miniMessage.deserialize(messagePrefix);
                            message = message.replace("{prefix}", miniMessage.serialize(prefix));
                        }
                        
                        cachedMessages.put(fullKey, miniMessage.deserialize(message));
                    } catch (Exception e) {
                        plugin.getComponentLogger().error(Component.text("Failed to parse message: " + fullKey + " - " + e.getMessage())
                            .color(Constants.Colors.ERROR));
                        // Fallback to plain text
                        cachedMessages.put(fullKey, Component.text(message));
                    }
                }
            }
        }
    }
    
    // Storage Settings
    public int getStorageSlots() {
        return Math.min(Math.max(1, config.getInt(Constants.Config.STORAGE_SLOTS, Constants.Defaults.STORAGE_SLOTS)), 54);
    }
    
    public Component getStorageTitle() {
        return getMessage("gui.storage-title", Map.of("player", "{player}"));
    }
    
    public Component getStorageTitle(Player player) {
        return getMessage("gui.storage-title", Map.of("player", player.getName()));
    }
    
    public boolean isProgressionRequired() {
        return config.getBoolean(Constants.Config.STORAGE_REQUIRE_PROGRESSION, Constants.Defaults.REQUIRE_PROGRESSION);
    }
    
    public int getMaxItemsPerSlot() {
        return Math.max(1, config.getInt(Constants.Config.STORAGE_MAX_ITEMS_PER_SLOT, Constants.Defaults.MAX_ITEMS_PER_SLOT));
    }
    
    public double getDefaultSlotCost() {
        return Math.max(0, config.getDouble(Constants.Config.STORAGE_DEFAULT_COST, Constants.Defaults.DEFAULT_COST));
    }
    
    public double getSlotCost(int slot) {
        return slotCosts.getOrDefault(slot, getDefaultSlotCost());
    }
    
    public void setSlotCost(int slot, double cost) {
        if (cost < 0) {
            throw new IllegalArgumentException("Cost cannot be negative");
        }
        slotCosts.put(slot, cost);
        config.set("storage.costs.slot-" + (slot + 1), cost);
        plugin.saveConfig();
    }
    
    public List<String> getProhibitedItems() {
        return new ArrayList<>(prohibitedItems);
    }
    
    public boolean isProhibitedItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return prohibitedItems.contains(item.getType().name());
    }
    
    // Rank Settings
    public String getRequiredRank(int slot) {
        return slotRanks.get(slot);
    }
    
    public String getRankDisplayName(String permission) {
        if (permission == null) return "";
        String rawDisplayName = rankDisplayNames.getOrDefault(permission, permission);
        
        // Strip MiniMessage tags to get plain text for use in message placeholders
        String plainText = rawDisplayName.replaceAll("<[^>]*>", "");
        return plainText.isEmpty() ? permission : plainText;
    }
    
    public String getStyledRankDisplayName(String permission) {
        if (permission == null) return "";
        String rawDisplayName = rankDisplayNames.getOrDefault(permission, permission);
        
        // Return the full styled display name with MiniMessage formatting
        return rawDisplayName.isEmpty() ? permission : rawDisplayName;
    }
    
    public boolean hasRankRequirement(Player player, String requiredRank) {
        if (player == null) return false;
        if (player.hasPermission(Constants.Permissions.BYPASS_RANK)) return true;
        if (requiredRank == null || requiredRank.isEmpty()) return true;
        return player.hasPermission(requiredRank);
    }
    
    // Safezone Settings
    public boolean isSafezoneEnabled() {
        return config.getBoolean(Constants.Config.SAFEZONE_ENABLED, Constants.Defaults.SAFEZONE_ENABLED);
    }
    
    public String getSafezoneDetectionMethod() {
        String method = config.getString(Constants.Config.SAFEZONE_DETECTION_METHOD, Constants.Defaults.SAFEZONE_DETECTION_METHOD);
        return method != null ? method.toLowerCase() : Constants.DetectionMethods.REGION;
    }
    
    public String getSafezoneRegionName() {
        return config.getString(Constants.Config.SAFEZONE_REGION_NAME, Constants.Defaults.SAFEZONE_REGION_NAME);
    }
    
    public int getSafezonePvPPriority() {
        return config.getInt(Constants.Config.SAFEZONE_PVP_PRIORITY, Constants.Defaults.SAFEZONE_PVP_PRIORITY);
    }
    
    public List<String> getSafezoneWorlds() {
        return config.getStringList(Constants.Config.SAFEZONE_WORLDS);
    }
    
    public Component getSafezoneMessage() {
        return getMessage(Constants.Messages.SAFEZONE_REQUIRED);
    }
    
    // Donor Settings
    public boolean isDonorEnabled() {
        return config.getBoolean(Constants.Config.DONOR_ENABLED, Constants.Defaults.DONOR_ENABLED);
    }
    
    public boolean areDonorSlotsSeparate() {
        return config.getBoolean(Constants.Config.DONOR_SLOTS_SEPARATE, Constants.Defaults.DONOR_SLOTS_SEPARATE);
    }
    
    public boolean doDonorSlotsPersist() {
        return config.getBoolean(Constants.Config.DONOR_SLOTS_PERSIST, Constants.Defaults.DONOR_SLOTS_PERSIST);
    }
    
    public boolean areDonorSlotsPurchasable() {
        return config.getBoolean(Constants.Config.DONOR_SLOTS_PURCHASABLE, Constants.Defaults.DONOR_SLOTS_PURCHASABLE);
    }
    
    public double getDonorSlotCostMultiplier() {
        return Math.max(0.1, config.getDouble(Constants.Config.DONOR_SLOTS_COST_MULTIPLIER, Constants.Defaults.DONOR_SLOTS_COST_MULTIPLIER));
    }
    
    public Collection<DonorRank> getDonorRanks() {
        return donorRanks.values();
    }
    
    public DonorRank getDonorRank(String rankName) {
        return donorRanks.get(rankName);
    }
    
    public Optional<DonorRank> getHighestDonorRank(Player player) {
        if (player == null) return Optional.empty();
        
        // Check if player is OP and has donor.* permission
        if (player.isOp() || player.hasPermission("storageslots.donor.*")) {
            // Return the highest rank available (King)
            return donorRanks.values().stream()
                .max(Comparator.comparingInt(DonorRank::slots));
        }
        
        return donorRanks.values().stream()
            .filter(rank -> player.hasPermission(rank.permission()))
            .max(Comparator.comparingInt(DonorRank::slots));
    }
    
    // Security Settings
    public int getMaxSlotsPerPlayer() {
        return Math.max(1, config.getInt(Constants.Config.SECURITY_MAX_SLOTS_PER_PLAYER, Constants.Defaults.MAX_SLOTS_PER_PLAYER));
    }
    
    public boolean preventItemDuplication() {
        return config.getBoolean(Constants.Config.SECURITY_PREVENT_DUPLICATION, true);
    }
    
    public boolean logTransactions() {
        return config.getBoolean(Constants.Config.SECURITY_LOG_TRANSACTIONS, true);
    }
    
    // Auto-save Settings
    public boolean isAutoSaveEnabled() {
        return config.getBoolean(Constants.Config.AUTO_SAVE_ENABLED, Constants.Defaults.AUTO_SAVE_ENABLED);
    }
    
    public int getAutoSaveInterval() {
        return Math.max(60, config.getInt(Constants.Config.AUTO_SAVE_INTERVAL, Constants.Defaults.AUTO_SAVE_INTERVAL));
    }
    
    // Economy Settings
    public String getCurrencyName() {
        return config.getString(Constants.Config.ECONOMY_CURRENCY_NAME, Constants.Defaults.CURRENCY_NAME);
    }
    
    public boolean useVault() {
        return config.getBoolean(Constants.Config.ECONOMY_USE_VAULT, Constants.Defaults.USE_VAULT);
    }
    
    // Withdrawal Fees
    public boolean isWithdrawalFeesEnabled() {
        return config.getBoolean("withdrawal-fees.enabled", true);
    }
    
    public WithdrawalFee getWithdrawalFee(Player player) {
        if (!isWithdrawalFeesEnabled()) {
            return new WithdrawalFee(0, 0.0);
        }
        
        if (player == null) {
            return defaultWithdrawalFee;
        }
        
        // Check for donor ranks first (highest priority)
        var highestDonorRank = getHighestDonorRank(player);
        if (highestDonorRank.isPresent()) {
            String donorRankName = highestDonorRank.get().name();
            WithdrawalFee donorFee = donorRankFees.get(donorRankName);
            if (donorFee != null) {
                if (plugin.getConfig().getBoolean("debug.enabled", false)) {
                    plugin.getLogger().info("Player " + player.getName() + " has donor rank " + donorRankName + 
                        " with withdrawal fee: " + donorFee.points() + " points, " + donorFee.money() + " money");
                }
                return donorFee;
            }
        }
        
        // Check for individual rank overrides (second priority)
        for (String rankPermission : individualRankFees.keySet()) {
            if (player.hasPermission(rankPermission)) {
                WithdrawalFee rankFee = individualRankFees.get(rankPermission);
                if (plugin.getConfig().getBoolean("debug.enabled", false)) {
                    plugin.getLogger().info("Player " + player.getName() + " has individual rank " + rankPermission + 
                        " with withdrawal fee: " + rankFee.points() + " points, " + rankFee.money() + " money");
                }
                return rankFee;
            }
        }
        
        // Check for rank group fees (third priority)
        for (String groupName : rankGroupFees.keySet()) {
            ConfigurationSection groupSection = config.getConfigurationSection("withdrawal-fees.rank-groups." + groupName);
            if (groupSection != null) {
                List<String> ranks = groupSection.getStringList("ranks");
                for (String rank : ranks) {
                    if (player.hasPermission(rank)) {
                        WithdrawalFee groupFee = rankGroupFees.get(groupName);
                        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
                            plugin.getLogger().info("Player " + player.getName() + " has rank " + rank + 
                                " in group " + groupName + " with withdrawal fee: " + groupFee.points() + 
                                " points, " + groupFee.money() + " money");
                        }
                        return groupFee;
                    }
                }
            }
        }
        
        // Return default fee if no matches found
        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("Player " + player.getName() + " using default withdrawal fee: " + 
                defaultWithdrawalFee.points() + " points, " + defaultWithdrawalFee.money() + " money");
        }
        return defaultWithdrawalFee;
    }
    
    public int getWithdrawalFeePoints(Player player) {
        return getWithdrawalFee(player).points();
    }
    
    public double getWithdrawalFeeMoney(Player player) {
        return getWithdrawalFee(player).money();
    }
    
    // Legacy methods for backward compatibility (deprecated)
    @Deprecated
    public double getWithdrawalFeePoints(String slotType) {
        return defaultWithdrawalFee.points();
    }
    
    @Deprecated
    public double getWithdrawalFeeMoney(String slotType) {
        return defaultWithdrawalFee.money();
    }
    
    // GUI Settings
    public ItemStack createLockedSlotItem(int slot, Player player) {
        String requiredRank = getRequiredRank(slot);
        boolean hasRank = hasRankRequirement(player, requiredRank);
        boolean canBuyNext = !isProgressionRequired() || slot == 0 || hasSlotUnlocked(player, slot - 1);
        boolean hasRequirements = hasRank && canBuyNext;
        
        Material material = getSlotMaterial(slot, hasRequirements, player);
            
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        
        // Set display name using slot-specific colored names from messages.yml
        Component nameComponent;
        String slotSpecificKey = "gui.locked-slot.names.slot-" + (slot + 1);
        
        // Try to get slot-specific name first, fallback to generic name
        if (messages.contains(slotSpecificKey)) {
            nameComponent = miniMessage.deserialize(messages.getString(slotSpecificKey));
        } else {
            nameComponent = getMessage("gui.locked-slot.name", Map.of("slot", String.valueOf(slot + 1)));
        }
        meta.displayName(nameComponent);
        
        // Set lore based on whether player meets requirements
        List<String> baseLore;
        if (hasRequirements) {
            baseLore = messages.getStringList("gui.locked-slot.lore-has-requirements");
        } else {
            baseLore = messages.getStringList("gui.locked-slot.lore-no-requirements");
        }
        
        // Fallback lore if not found in messages.yml
        if (baseLore.isEmpty()) {
            if (hasRequirements) {
                baseLore = Arrays.asList(
                    "<gray>Cost: <yellow>{cost} {currency}</yellow>",
                    "",
                    "<yellow>Click to purchase!</yellow>"
                );
            } else {
                baseLore = Arrays.asList(
                    "<gray>Storage slot locked until {rank}"
                );
            }
        }
        
        double cost = getSlotCost(slot);
        String currency = getCurrencyName();
        
        // Get the styled rank display name with proper colors
        String styledRankName = getStyledRankDisplayName(requiredRank);
        
        List<Component> loreComponents = new ArrayList<>();
        for (String line : baseLore) {
            if (line.trim().isEmpty()) {
                loreComponents.add(Component.empty());
                continue;
            }
            
            String processedLine = line.replace("{cost}", String.format("%,.0f", cost))
                                     .replace("{currency}", currency)
                                     .replace("{rank}", styledRankName);
            
            // Parse with MiniMessage to preserve the rank styling
            loreComponents.add(miniMessage.deserialize(processedLine));
        }
        meta.lore(loreComponents);
        
        item.setItemMeta(meta);
        return item;
    }
    
    private Material getSlotMaterial(int slot, boolean hasRequirements, Player player) {
        // Return colored glass panes based on slot number
        return switch (slot) {
            case 0 -> Material.YELLOW_STAINED_GLASS_PANE;      // I Rank
            case 1 -> Material.LIME_STAINED_GLASS_PANE;        // H Rank
            case 2 -> Material.GREEN_STAINED_GLASS_PANE;       // G Rank
            case 3 -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;  // F Rank
            case 4 -> Material.BLUE_STAINED_GLASS_PANE;        // E Rank
            case 5 -> Material.PURPLE_STAINED_GLASS_PANE;      // D Rank
            case 6 -> Material.MAGENTA_STAINED_GLASS_PANE;     // C Rank
            case 7 -> Material.PINK_STAINED_GLASS_PANE;        // B Rank
            case 8 -> Material.RED_STAINED_GLASS_PANE;         // A Rank
            default -> hasRequirements ? 
                Material.valueOf(Constants.GUI.LOCKED_SLOT_MATERIAL_HAS_RANK) :
                (player != null && player.hasPermission(Constants.Permissions.ADMIN) ?
                    Material.valueOf(Constants.GUI.LOCKED_SLOT_MATERIAL_ADMIN) :
                    Material.valueOf(Constants.GUI.LOCKED_SLOT_MATERIAL_NO_RANK));
        };
    }
    
    // Message Methods
    public Component getMessage(String key) {
        return cachedMessages.getOrDefault(key, Component.text("Message not found: " + key)
            .color(Constants.Colors.ERROR));
    }
    
    public Component getMessage(String key, Map<String, String> placeholders) {
        Component message = getMessage(key);
        
        // Convert to MiniMessage string for placeholder replacement
        String messageString = miniMessage.serialize(message);
        
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue();
            
            // Special handling for rank placeholders to preserve styling
            if (entry.getKey().equals("rank")) {
                // The value should be the rank permission (e.g., "rank.a")
                // Look up its styled display name
                value = getStyledRankDisplayName(value);
            }
            
            messageString = messageString.replace(placeholder, value);
        }
        
        // Parse back to Component with all styling preserved
        return miniMessage.deserialize(messageString);
    }
    
    public Component getMessage(String key, String placeholder, String value) {
        return getMessage(key, Map.of(placeholder, value));
    }
    
    // Utility Methods
    public boolean isValidSlot(int slot) {
        // Regular slots (0 to getStorageSlots()-1, typically 0-8)
        if (slot >= 0 && slot < getStorageSlots()) {
            return true;
        }
        
        // Donor slots (11-15) if donor system is enabled
        if (isDonorEnabled() && slot >= 11 && slot <= 15) {
            return true;
        }
        
        return false;
    }
    
    private boolean hasSlotUnlocked(Player player, int slot) {
        if (player == null) return false;
        // This would need to check with the storage manager
        // For now, we'll assume it's implemented elsewhere
        return slot == 0; // Always allow first slot check
    }
    
    public boolean checkRankRequirement(Player player, String requiredRank) {
        return hasRankRequirement(player, requiredRank);
    }
    
    public FileConfiguration getMessages() {
        return messages;
    }
    
    public record DonorRank(String name, String displayName, String permission, int slots, List<String> features) {}
    
    public record WithdrawalFee(int points, double money) {}
    
    // Withdrawal cooldown settings
    public boolean isWithdrawalCooldownEnabled() {
        return config.getBoolean("withdrawal-cooldown.enabled", true);
    }
    
    public long getWithdrawalCooldownMs() {
        return config.getLong("withdrawal-cooldown.duration-ms", 1500);
    }
    
    public boolean canBypassWithdrawalCooldown(Player player) {
        return player.hasPermission("storageslots.bypass.cooldown");
    }
} 