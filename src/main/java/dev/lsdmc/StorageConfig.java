package dev.lsdmc;

import dev.lsdmc.utils.Constants;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class StorageConfig {
  private final StorageSlots plugin;
  
  private final FileConfiguration config;
  
  private final FileConfiguration messages;
  
  private final MiniMessage miniMessage;
  

  
  private Map<String, Component> cachedMessages;
  
  private Map<Integer, Double> slotCosts;
  
  private Map<Integer, String> slotRanks;
  
  private Map<String, String> rankDisplayNames;
  
  private List<String> prohibitedItems;
  
  private Map<String, DonorRank> donorRanks;
  
  private String messagePrefix;
  
  private Map<String, WithdrawalFee> rankGroupFees;
  
  private Map<String, WithdrawalFee> individualRankFees;
  
  private Map<String, WithdrawalFee> donorRankFees;
  
  private WithdrawalFee defaultWithdrawalFee;
  
  // Maintains the evaluation order for rank groups as defined in config.yml
  private List<String> rankGroupOrder;
  
  public StorageConfig(StorageSlots plugin) {
    this.plugin = plugin;
    this.config = plugin.getConfig();
    this.miniMessage = MiniMessage.miniMessage();

    this.cachedMessages = new HashMap<>();
    this.slotCosts = new HashMap<>();
    this.slotRanks = new HashMap<>();
    this.rankDisplayNames = new HashMap<>();
    this.prohibitedItems = new ArrayList<>();
    this.donorRanks = new HashMap<>();
    this.rankGroupFees = new HashMap<>();
    this.individualRankFees = new HashMap<>();
    this.donorRankFees = new HashMap<>();
    this.defaultWithdrawalFee = new WithdrawalFee(10, 100.0D);
    this.rankGroupOrder = new ArrayList<>();
    this.messages = loadMessagesConfig();
    loadConfiguration();
  }
  
  private FileConfiguration loadMessagesConfig() {
    File messagesFile = new File(this.plugin.getDataFolder(), "messages.yml");
    if (!messagesFile.exists())
      this.plugin.saveResource("messages.yml", false); 
    return (FileConfiguration)YamlConfiguration.loadConfiguration(messagesFile);
  }
  
  public void reload() {
    this.plugin.getLogger().info("StorageConfig.reload() called");
    
    this.plugin.reloadConfig();
    this.plugin.getLogger().info("Main config reloaded");
    
    File messagesFile = new File(this.plugin.getDataFolder(), "messages.yml");
    this.plugin.getLogger().info("Messages file path: " + messagesFile.getAbsolutePath());
    this.plugin.getLogger().info("Messages file exists: " + messagesFile.exists());
    
    if (messagesFile.exists())
      try {
        ((YamlConfiguration)this.messages).load(messagesFile);
        this.plugin.getLogger().info("Messages.yml reloaded successfully");
      } catch (Exception e) {
        this.plugin.getLogger().severe("Failed to reload messages.yml: " + e.getMessage());
        e.printStackTrace();
        this.plugin.getComponentLogger().error(Component.text("Failed to reload messages.yml: " + e.getMessage())
            .color((TextColor)Constants.Colors.ERROR));
      }  
    
    this.plugin.getLogger().info("Clearing all caches...");
    // Clear all caches
    this.cachedMessages.clear();
    this.slotCosts.clear();
    this.slotRanks.clear();
    this.rankDisplayNames.clear();
    this.prohibitedItems.clear();
    this.donorRanks.clear();
    this.rankGroupFees.clear();
    this.individualRankFees.clear();
    this.donorRankFees.clear();
    this.rankGroupOrder.clear();
    
    this.plugin.getLogger().info("Loading configuration...");
    loadConfiguration(); // <-- Ensure all config values are reloaded
    
    this.plugin.getLogger().info("Reload completed. Cached messages count: " + this.cachedMessages.size());
    this.plugin.getComponentLogger().info(Component.text("Configuration and messages reloaded!")
        .color((TextColor)Constants.Colors.SUCCESS));
  }
  
  public void loadConfiguration() {
    validateConfiguration();
    loadSlotCosts();
    loadSlotRanks();
    loadRankDisplayNames();
    loadProhibitedItems();
    loadDonorRanks();
    loadWithdrawalFees();
    loadMessages();
    this.plugin.getComponentLogger().info(Component.text("Configuration loaded successfully!")
        .color((TextColor)Constants.Colors.SUCCESS));
  }
  
  private void validateConfiguration() {
    boolean hasChanges = false;
    
    // Validate storage slots
    int slots = this.config.getInt("storage.slots", 9);
    if (slots < 1 || slots > 54) {
      this.plugin.getComponentLogger().warn((Component)Component.text("Invalid storage slots value: " + slots + ". Using default: 9"));
      this.config.set("storage.slots", Integer.valueOf(9));
      hasChanges = true;
    }
    
    // Validate auto-save interval
    int interval = this.config.getInt("auto-save.interval", 300);
    if (interval < 60) {
      this.plugin.getComponentLogger().warn((Component)Component.text("Auto-save interval too low: " + interval + ". Using minimum: 60"));
      this.config.set("auto-save.interval", Integer.valueOf(60));
      hasChanges = true;
    }
    
    // Validate max items per slot  
    int maxItems = this.config.getInt("storage.max-items-per-slot", 64);
    if (maxItems < 1 || maxItems > 64) {
      this.plugin.getComponentLogger().warn((Component)Component.text("Invalid max items per slot: " + maxItems + ". Using default: 64"));
      this.config.set("storage.max-items-per-slot", Integer.valueOf(64));
      hasChanges = true;
    }
    
    // Validate donor cost multiplier
    double multiplier = this.config.getDouble("donor.slots.cost-multiplier", 2.0D);
    if (multiplier <= 0.0D) {
      this.plugin.getComponentLogger().warn((Component)Component.text("Invalid donor cost multiplier: " + multiplier + ". Using default: 2.0"));
      this.config.set("donor.slots.cost-multiplier", Double.valueOf(2.0D));
      hasChanges = true;
    }
    
    // Validate withdrawal cooldown
    long cooldownMs = this.config.getLong("withdrawal-cooldown.duration-ms", 1500L);
    if (cooldownMs < 100L || cooldownMs > 30000L) {
      this.plugin.getComponentLogger().warn((Component)Component.text("Invalid withdrawal cooldown: " + cooldownMs + "ms. Recommended: 100-30000ms"));
    }
    
    // Validate donor slot counts
    if (isDonorEnabled()) {
      ConfigurationSection donorSection = this.config.getConfigurationSection("donor.ranks");
      if (donorSection != null) {
        for (String rankName : donorSection.getKeys(false)) {
          int rankSlots = donorSection.getInt(rankName + ".slots", 1);
          if (rankSlots < 1 || rankSlots > Constants.Slots.DONOR_SLOT_COUNT) {
            this.plugin.getComponentLogger().warn((Component)Component.text("Donor rank '" + rankName + "' has invalid slot count: " + rankSlots + " (valid: 1-" + Constants.Slots.DONOR_SLOT_COUNT + ")"));
          }
        }
      }
    }
    
    if (hasChanges)
      this.plugin.saveConfig(); 
  }
  
  private void loadSlotCosts() {
    ConfigurationSection costsSection = this.config.getConfigurationSection("storage.costs");
    if (costsSection != null)
      for (String key : costsSection.getKeys(false)) {
        try {
          int slot = Integer.parseInt(key.replace("slot-", "")) - 1;
          double cost = costsSection.getDouble(key);
          if (cost >= 0.0D) {
            this.slotCosts.put(Integer.valueOf(slot), Double.valueOf(cost));
            continue;
          } 
          this.plugin.getComponentLogger().warn((Component)Component.text("Invalid negative cost for slot " + (slot + 1)));
        } catch (NumberFormatException e) {
          this.plugin.getComponentLogger().warn((Component)Component.text("Invalid slot number in costs configuration: " + key));
        } 
      }  
  }
  
  private void loadSlotRanks() {
    ConfigurationSection ranksSection = this.config.getConfigurationSection("ranks.slot-requirements");
    if (ranksSection != null)
      for (String key : ranksSection.getKeys(false)) {
        try {
          int slot = Integer.parseInt(key.replace("slot-", "")) - 1;
          String rank = ranksSection.getString(key);
          if (rank != null && !rank.trim().isEmpty())
            this.slotRanks.put(Integer.valueOf(slot), rank); 
        } catch (NumberFormatException e) {
          this.plugin.getComponentLogger().warn((Component)Component.text("Invalid slot number in rank requirements: " + key));
        } 
      }  
  }
  
  private void loadRankDisplayNames() {
    ConfigurationSection displaySection = this.config.getConfigurationSection("ranks.display-names");
    if (displaySection != null)
      for (String key : displaySection.getKeys(false)) {
        String displayName = displaySection.getString(key);
        if (displayName != null)
          this.rankDisplayNames.put(key, displayName); 
      }  
  }
  
  private void loadProhibitedItems() {
    this.prohibitedItems = this.config.getStringList("storage.prohibited-items");
    if (this.prohibitedItems.isEmpty())
      this.prohibitedItems = Arrays.asList(new String[] { "BEDROCK", "BARRIER", "COMMAND_BLOCK", "STRUCTURE_BLOCK" }); 
  }
  
  private void loadDonorRanks() {
    ConfigurationSection donorSection = this.config.getConfigurationSection("donor.ranks");
    if (donorSection != null)
      for (String rankName : donorSection.getKeys(false)) {
        ConfigurationSection rankSection = donorSection.getConfigurationSection(rankName);
        if (rankSection != null) {
          DonorRank donorRank = new DonorRank(
              rankName, 
              rankSection.getString("display-name", rankName), 
              rankSection.getString("permission", "storageslots.donor." + rankName), 
              Math.max(0, rankSection.getInt("slots", 0)));
          this.donorRanks.put(rankName, donorRank);
        } 
      }  
  }
  
  private void loadWithdrawalFees() {
    this.rankGroupFees.clear();
    this.individualRankFees.clear();
    this.donorRankFees.clear();
    ConfigurationSection defaultSection = this.config.getConfigurationSection("withdrawal-fees.default");
    if (defaultSection != null) {
      int points = defaultSection.getInt("points", 10);
      double money = defaultSection.getDouble("money", 100.0D);
      this.defaultWithdrawalFee = new WithdrawalFee(points, money);
    } 
    ConfigurationSection groupsSection = this.config.getConfigurationSection("withdrawal-fees.rank-groups");
    if (groupsSection != null) {
      // Preserve YAML order for groups so priority is respected (first match wins)
      this.rankGroupOrder = new ArrayList<>(groupsSection.getKeys(false));
      for (String groupName : this.rankGroupOrder) {
        ConfigurationSection groupSection = groupsSection.getConfigurationSection(groupName);
        if (groupSection != null) {
          int points = groupSection.getInt("points", 0);
          double money = groupSection.getDouble("money", 0.0D);
          this.rankGroupFees.put(groupName, new WithdrawalFee(points, money));
        }
      }
    }
    ConfigurationSection individualSection = this.config.getConfigurationSection("withdrawal-fees.individual-ranks");
    if (individualSection != null)
      for (String rankName : individualSection.getKeys(false)) {
        ConfigurationSection rankSection = individualSection.getConfigurationSection(rankName);
        if (rankSection != null) {
          int points = rankSection.getInt("points", 0);
          double money = rankSection.getDouble("money", 0.0D);
          this.individualRankFees.put(rankName, new WithdrawalFee(points, money));
        } 
      }  
    ConfigurationSection donorSection = this.config.getConfigurationSection("withdrawal-fees.donor");
    if (donorSection != null)
      for (String donorRank : donorSection.getKeys(false)) {
        ConfigurationSection donorRankSection = donorSection.getConfigurationSection(donorRank);
        if (donorRankSection != null) {
          int points = donorRankSection.getInt("points", 0);
          double money = donorRankSection.getDouble("money", 0.0D);
          this.donorRankFees.put(donorRank, new WithdrawalFee(points, money));
        } 
      }  
  }
  
  private void loadMessages() {
    this.cachedMessages.clear();
    this.messagePrefix = this.messages.getString("prefix", "<gradient:#ff5555:#ffaa00>[StorageSlots]</gradient> ");
    
    this.plugin.getLogger().info("Loading messages from messages.yml...");
    
    // Debug: Check if the YAML file can see the donor-slot-purchased key
    this.plugin.getLogger().info("YAML root keys: " + this.messages.getKeys(false));
    this.plugin.getLogger().info("donor-slot-purchased raw value: '" + this.messages.getString("donor-slot-purchased") + "'");
    this.plugin.getLogger().info("donor-feature-unavailable raw value: '" + this.messages.getString("donor-feature-unavailable") + "'");
    
    loadMessagesFromSection("", (ConfigurationSection)this.messages);
    
    this.plugin.getLogger().info("Loaded " + this.cachedMessages.size() + " messages:");
    for (String key : this.cachedMessages.keySet()) {
      if (key.contains("donor") || key.contains("inventory-full")) {
        this.plugin.getLogger().info("  - " + key);
      }
    }
    
    // Specifically check for the new message
    if (this.cachedMessages.containsKey("inventory-full-item-dropped")) {
      this.plugin.getLogger().info("✓ inventory-full-item-dropped message loaded successfully");
    } else {
      this.plugin.getLogger().warning("✗ inventory-full-item-dropped message NOT FOUND in cache");
    }
    
    // Specifically check for donor-slot-purchased
    if (this.cachedMessages.containsKey("donor-slot-purchased")) {
      this.plugin.getLogger().info("✓ donor-slot-purchased message loaded successfully");
    } else {
      this.plugin.getLogger().warning("✗ donor-slot-purchased message NOT FOUND - attempting manual load");
      
      // Try to manually load the missing message
      String rawMessage = this.messages.getString("donor-slot-purchased");
      if (rawMessage != null) {
        this.plugin.getLogger().info("Found raw donor-slot-purchased in YAML: '" + rawMessage + "'");
        try {
          if (rawMessage.contains("{prefix}")) {
            Component prefix = this.miniMessage.deserialize(this.messagePrefix);
            rawMessage = rawMessage.replace("{prefix}", (CharSequence)this.miniMessage.serialize(prefix));
          }
          this.cachedMessages.put("donor-slot-purchased", this.miniMessage.deserialize(rawMessage));
          this.plugin.getLogger().info("✓ Manually loaded donor-slot-purchased message successfully");
        } catch (Exception e) {
          this.plugin.getLogger().severe("Failed to manually load donor-slot-purchased: " + e.getMessage());
          // Fallback message
          this.cachedMessages.put("donor-slot-purchased", 
              this.miniMessage.deserialize("{prefix}<green>Unlocked donor slot {slot} for {cost} {currency}!</green>"));
        }
      } else {
        this.plugin.getLogger().severe("donor-slot-purchased not found in YAML at all!");
        // Fallback message
        this.cachedMessages.put("donor-slot-purchased", 
            this.miniMessage.deserialize("{prefix}<green>Unlocked donor slot {slot} for {cost} {currency}!</green>"));
      }
    }
  }
  
  private void loadMessagesFromSection(String path, ConfigurationSection section) {
    for (String key : section.getKeys(false)) {
      String fullKey = path.isEmpty() ? key : (path + "." + key);
      if (section.isConfigurationSection(key)) {
        loadMessagesFromSection(fullKey, section.getConfigurationSection(key));
        continue;
      } 
      String message = section.getString(key);
      
      // Debug logging for donor-slot-purchased specifically
      if (key.equals("donor-slot-purchased") || fullKey.equals("donor-slot-purchased")) {
        this.plugin.getLogger().info("Found donor-slot-purchased key: '" + key + "', fullKey: '" + fullKey + "', message: '" + message + "'");
      }
      
      if (message != null) {
        try {
          if (message.contains("{prefix}")) {
            Component prefix = this.miniMessage.deserialize(this.messagePrefix);
            message = message.replace("{prefix}", (CharSequence)this.miniMessage.serialize(prefix));
          } 
          this.cachedMessages.put(fullKey, this.miniMessage.deserialize(message));
          
          // Debug logging for successful message loading
          if (key.equals("donor-slot-purchased") || fullKey.equals("donor-slot-purchased")) {
            this.plugin.getLogger().info("Successfully loaded donor-slot-purchased message into cache with key: '" + fullKey + "'");
          }
        } catch (Exception e) {
          this.plugin.getComponentLogger().error(Component.text("Failed to parse message: " + fullKey + " - " + e.getMessage())
              .color((TextColor)Constants.Colors.ERROR));
          this.cachedMessages.put(fullKey, Component.text(message));
        }
      } else {
        // Debug logging for null messages
        if (key.equals("donor-slot-purchased") || fullKey.equals("donor-slot-purchased")) {
          this.plugin.getLogger().warning("donor-slot-purchased message is NULL from YAML!");
        }
      }
    } 
  }
  
  public int getStorageSlots() {
    return Math.min(Math.max(1, this.config.getInt("storage.slots", 9)), 54);
  }
  
  public Component getStorageTitle() {
    return getMessage("gui.storage-title", Map.of("player", "{player}"));
  }
  
  public Component getStorageTitle(Player player) {
    return getMessage("gui.storage-title", Map.of("player", player.getName()));
  }
  
  public boolean isProgressionRequired() {
    return this.config.getBoolean("storage.require-progression", true);
  }
  
  public int getMaxItemsPerSlot() {
    return Math.max(1, this.config.getInt("storage.max-items-per-slot", 64));
  }
  
  public double getDefaultSlotCost() {
    return Math.max(0.0D, this.config.getDouble("storage.default-cost", 1000.0D));
  }
  
  public double getSlotCost(int slot) {
    return ((Double)this.slotCosts.getOrDefault(Integer.valueOf(slot), Double.valueOf(getDefaultSlotCost()))).doubleValue();
  }
  
  public void setSlotCost(int slot, double cost) {
    if (cost < 0.0D)
      throw new IllegalArgumentException("Cost cannot be negative"); 
    this.slotCosts.put(Integer.valueOf(slot), Double.valueOf(cost));
    this.config.set("storage.costs.slot-" + (slot + 1), Double.valueOf(cost));
    this.plugin.saveConfig();
  }
  
  public List<String> getProhibitedItems() {
    return new ArrayList<>(this.prohibitedItems);
  }
  
  public boolean isProhibitedItem(ItemStack item) {
    if (item == null || item.getType().isAir())
      return false; 
    return this.prohibitedItems.contains(item.getType().name());
  }
  
  public String getRequiredRank(int slot) {
    return this.slotRanks.get(Integer.valueOf(slot));
  }
  
  public String getRankDisplayName(String permission) {
    if (permission == null)
      return ""; 
    String rawDisplayName = this.rankDisplayNames.getOrDefault(permission, permission);
    String plainText = rawDisplayName.replaceAll("<[^>]*>", "");
    return plainText.isEmpty() ? permission : plainText;
  }
  
  public String getStyledRankDisplayName(String permission) {
    if (permission == null)
      return ""; 
    String rawDisplayName = this.rankDisplayNames.getOrDefault(permission, permission);
    return rawDisplayName.isEmpty() ? permission : rawDisplayName;
  }
  
  public boolean hasRankRequirement(Player player, String requiredRank) {
    if (player == null)
      return false; 
    if (player.hasPermission("storageslots.bypass.rank"))
      return true; 
    if (requiredRank == null || requiredRank.isEmpty())
      return true; 
    return player.hasPermission(requiredRank);
  }
  
  public boolean isSafezoneEnabled() {
    return this.config.getBoolean("safezone.enabled", true);
  }
  
  public String getSafezoneDetectionMethod() {
    String method = this.config.getString("safezone.detection-method", "region");
    return (method != null) ? method.toLowerCase() : "region";
  }
  
  public String getSafezoneRegionName() {
    return this.config.getString("safezone.region-name", "safezone");
  }
  
  public int getSafezonePvPPriority() {
    return this.config.getInt("safezone.pvp-priority", 0);
  }
  
  public List<String> getSafezoneWorlds() {
    return this.config.getStringList("safezone.worlds");
  }
  
  public Component getSafezoneMessage() {
    return getMessage("safezone-required");
  }
  
  public boolean isDonorEnabled() {
    return this.config.getBoolean("donor.enabled", true);
  }
  
  public boolean areDonorSlotsSeparate() {
    return this.config.getBoolean("donor.slots.separate", true);
  }
  
  public boolean doDonorSlotsPersist() {
    return this.config.getBoolean("donor.slots.persist", true);
  }
  
  public boolean areDonorSlotsPurchasable() {
    return this.config.getBoolean("donor.slots.purchasable", false);
  }
  
  public double getDonorSlotCostMultiplier() {
    return Math.max(0.1D, this.config.getDouble("donor.slots.cost-multiplier", 2.0D));
  }
  
  public Collection<DonorRank> getDonorRanks() {
    return this.donorRanks.values();
  }
  
  public Map<String, WithdrawalFee> getDonorRankFees() {
    return this.donorRankFees;
  }
  
  public DonorRank getDonorRank(String rankName) {
    return this.donorRanks.get(rankName);
  }
  
  public Optional<DonorRank> getHighestDonorRank(Player player) {
    if (player == null || this.donorRanks.isEmpty())
      return this.donorRanks.values().stream()
        .max(Comparator.comparingInt(DonorRank::slots)); 
    return this.donorRanks.values().stream()
      .filter(rank -> player.hasPermission(rank.permission()))
      .max(Comparator.comparingInt(DonorRank::slots));
  }
  
  public int getMaxSlotsPerPlayer() {
    return Math.max(1, this.config.getInt("security.max-slots-per-player", 54));
  }
  
  public boolean preventItemDuplication() {
    return this.config.getBoolean("security.prevent-item-duplication", true);
  }
  
  public boolean logTransactions() {
    return this.config.getBoolean("security.log-transactions", true);
  }
  
  public boolean isAutoSaveEnabled() {
    return this.config.getBoolean("auto-save.enabled", true);
  }
  
  public int getAutoSaveInterval() {
    return Math.max(60, this.config.getInt("auto-save.interval", 300));
  }
  
  public String getCurrencyName() {
    // Legacy support - check old config format first
    if (this.config.contains("economy.currency-name")) {
      return this.config.getString("economy.currency-name", "points");
    }
    // New format - default to slot purchase currency
    return this.config.getString("economy.slot-purchase.currency", "points");
  }
  
  public boolean useVault() {
    // Legacy support - check old config format first
    if (this.config.contains("economy.use-vault")) {
      return this.config.getBoolean("economy.use-vault", false);
    }
    // New format - default to slot purchase vault setting
    return this.config.getBoolean("economy.slot-purchase.use-vault", false);
  }
  
  // New methods for specific operation currencies
  public String getSlotPurchaseCurrency() {
    return this.config.getString("economy.slot-purchase.currency", "points");
  }
  
  public boolean useVaultForSlotPurchase() {
    return this.config.getBoolean("economy.slot-purchase.use-vault", false);
  }
  
  public String getWithdrawalFeeCurrency() {
    return this.config.getString("economy.withdrawal-fees.currency", "points");
  }
  
  public boolean useVaultForWithdrawalFees() {
    return this.config.getBoolean("economy.withdrawal-fees.use-vault", false);
  }
  
  public String getDonorSlotCurrency() {
    return this.config.getString("economy.donor-slots.currency", "points");
  }
  
  public boolean useVaultForDonorSlots() {
    return this.config.getBoolean("economy.donor-slots.use-vault", false);
  }
  
  public boolean isWithdrawalFeesEnabled() {
    return this.config.getBoolean("withdrawal-fees.enabled", true);
  }
  
  public WithdrawalFee getWithdrawalFee(Player player) {
    if (!isWithdrawalFeesEnabled())
      return new WithdrawalFee(0, 0.0D); 
    if (player == null)
      return this.defaultWithdrawalFee; 
    Optional<DonorRank> highestDonorRank = getHighestDonorRank(player);
    if (highestDonorRank.isPresent()) {
      String donorRankName = ((DonorRank)highestDonorRank.get()).name();
      WithdrawalFee donorFee = this.donorRankFees.get(donorRankName);
      if (donorFee != null) {
        if (this.config.getBoolean("debug.log-withdrawal-fees", false))
          this.plugin.getLogger().info("Player " + player.getName() + " has donor rank " + donorRankName + " with withdrawal fee: " + 
              donorFee.points() + " points, " + donorFee.money() + " money"); 
        return donorFee;
      } 
    } 
    for (String rankPermission : this.individualRankFees.keySet()) {
      if (player.hasPermission(rankPermission)) {
        WithdrawalFee rankFee = this.individualRankFees.get(rankPermission);
        if (this.config.getBoolean("debug.log-withdrawal-fees", false))
          this.plugin.getLogger().info("Player " + player.getName() + " has individual rank " + rankPermission + " with withdrawal fee: " + 
              rankFee.points() + " points, " + rankFee.money() + " money"); 
        return rankFee;
      } 
    } 
    // Evaluate rank groups in configured order (YAML order). Fallback to map order if not available
    List<String> groupsToCheck = this.rankGroupOrder.isEmpty() ? new ArrayList<>(this.rankGroupFees.keySet()) : this.rankGroupOrder;
    for (String groupName : groupsToCheck) {
      ConfigurationSection groupSection = this.config.getConfigurationSection("withdrawal-fees.rank-groups." + groupName);
      if (groupSection != null) {
        List<String> ranks = groupSection.getStringList("ranks");
        for (String rank : ranks) {
          if (player.hasPermission(rank)) {
            WithdrawalFee groupFee = this.rankGroupFees.get(groupName);
            if (this.config.getBoolean("debug.log-withdrawal-fees", false))
              this.plugin.getLogger().info("Player " + player.getName() + " has rank " + rank + " in group " + 
                  groupName + " with withdrawal fee: " + groupFee.points() + " points, " + 
                  groupFee.money() + " money"); 
            return groupFee;
          } 
        } 
      } 
    } 
    if (this.config.getBoolean("debug.log-withdrawal-fees", false))
      this.plugin.getLogger().info("Player " + player.getName() + " using default withdrawal fee: " + 
          this.defaultWithdrawalFee.points() + " points, " + this.defaultWithdrawalFee.money() + " money"); 
    return this.defaultWithdrawalFee;
  }
  
  public int getWithdrawalFeePoints(Player player) {
    return getWithdrawalFee(player).points();
  }
  
  public double getWithdrawalFeeMoney(Player player) {
    return getWithdrawalFee(player).money();
  }
  
  @Deprecated
  public double getWithdrawalFeePoints(String slotType) {
    return this.defaultWithdrawalFee.points();
  }
  
  @Deprecated
  public double getWithdrawalFeeMoney(String slotType) {
    return this.defaultWithdrawalFee.money();
  }
  
  public ItemStack createLockedSlotItem(int slot, Player player) {
    Component nameComponent;
    List<String> baseLore;
    String requiredRank = getRequiredRank(slot);
    boolean hasRank = hasRankRequirement(player, requiredRank);
    boolean canBuyNext = !(isProgressionRequired() && slot != 0 && !hasSlotUnlocked(player, slot - 1));
    boolean hasRequirements = (hasRank && canBuyNext);
    Material material = getSlotMaterial(slot, hasRequirements, player);
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    if (meta == null)
      return item; 
    String slotSpecificKey = "gui.locked-slot.names.slot-" + (slot + 1);
    if (this.messages.contains(slotSpecificKey)) {
      nameComponent = this.miniMessage.deserialize(this.messages.getString(slotSpecificKey));
    } else {
      nameComponent = getMessage("gui.locked-slot.name", Map.of("slot", String.valueOf(slot + 1)));
    } 
    meta.displayName(nameComponent);
    if (hasRequirements) {
      baseLore = this.messages.getStringList("gui.locked-slot.lore-has-requirements");
    } else {
      baseLore = this.messages.getStringList("gui.locked-slot.lore-no-requirements");
    } 
    if (baseLore.isEmpty())
      if (hasRequirements) {
        baseLore = Arrays.asList(new String[] { "<gray>Cost: <yellow>{cost} {currency}</yellow>", 
              "", 
              "<yellow>Click to purchase!</yellow>" });
      } else {
        baseLore = Arrays.asList(new String[] { "<gray>Storage slot locked until {rank}" });
      }  
    double cost = getSlotCost(slot);
    String currency = getCurrencyName();
    String styledRankName = getStyledRankDisplayName(requiredRank);
    List<Component> loreComponents = new ArrayList<>();
    for (String line : baseLore) {
      if (line.trim().isEmpty()) {
        loreComponents.add(Component.empty());
        continue;
      } 
      String processedLine = line.replace("{cost}", String.format("%,.0f", new Object[] { Double.valueOf(cost) })).replace("{currency}", currency)
        .replace("{rank}", styledRankName);
      loreComponents.add(this.miniMessage.deserialize(processedLine));
    } 
    meta.lore(loreComponents);
    item.setItemMeta(meta);
    return item;
  }
  
  private Material getSlotMaterial(int slot, boolean hasRequirements, Player player) {
    // Donor slots use their own logic - don't change them
    if (Constants.Slots.isDonorSlot(slot)) {
      return hasRequirements ? 
        Material.valueOf("RED_STAINED_GLASS_PANE") : (
        (player != null && player.hasPermission("storageslots.admin")) ? 
        Material.valueOf("GRAY_STAINED_GLASS_PANE") : 
        Material.valueOf("BLACK_STAINED_GLASS_PANE"));
    }
    
    // First determine the base material based on slot number (for visual consistency)
    Material baseMaterial;
    switch (slot) {
      case 0:
        baseMaterial = Material.YELLOW_STAINED_GLASS_PANE;
        break;
      case 1:
        baseMaterial = Material.LIME_STAINED_GLASS_PANE;
        break;
      case 2:
        baseMaterial = Material.GREEN_STAINED_GLASS_PANE;
        break;
      case 3:
        baseMaterial = Material.LIGHT_BLUE_STAINED_GLASS_PANE;
        break;
      case 4:
        baseMaterial = Material.BLUE_STAINED_GLASS_PANE;
        break;
      case 5:
        baseMaterial = Material.PURPLE_STAINED_GLASS_PANE;
        break;
      case 6:
        baseMaterial = Material.MAGENTA_STAINED_GLASS_PANE;
        break;
      case 7:
        baseMaterial = Material.PINK_STAINED_GLASS_PANE;
        break;
      case 8:
        baseMaterial = Material.RED_STAINED_GLASS_PANE;
        break;
      default:
        baseMaterial = Material.GRAY_STAINED_GLASS_PANE;
        break;
    }
    
    // If player doesn't have requirements met (rank + progression), show red glass
    if (!hasRequirements) {
      return Material.RED_STAINED_GLASS_PANE;
    }
    
    // If player is admin viewing, use gray glass pane
    if (player != null && player.hasPermission("storageslots.admin")) {
      return Material.GRAY_STAINED_GLASS_PANE;
    }
    
    // Player has requirements met - show the colored glass
    // The actual affordability check will happen during purchase
    return baseMaterial;
  }
  
  public Component getMessage(String key) {
    return this.cachedMessages.getOrDefault(key, Component.text("Message not found: " + key)
        .color((TextColor)Constants.Colors.ERROR));
  }
  
  public Component getMessage(String key, Map<String, String> placeholders) {
    // Check if message exists first
    if (!this.cachedMessages.containsKey(key)) {
      this.plugin.getLogger().warning("Message not found in cache: " + key + ". Available keys: " + this.cachedMessages.keySet());
      return Component.text("Message not found: " + key)
          .color((TextColor)Constants.Colors.ERROR);
    }
    
    Component message = getMessage(key);
    String messageString = (String)this.miniMessage.serialize(message);
    
    // Debug logging for donor slot message
    if (key.equals("donor-slot-purchased")) {
      this.plugin.getLogger().info("Processing donor-slot-purchased message: " + messageString);
      this.plugin.getLogger().info("Placeholders: " + placeholders);
    }
    
    for (Map.Entry<String, String> entry : placeholders.entrySet()) {
      String placeholder = "{" + (String)entry.getKey() + "}";
      String value = entry.getValue();
      if (((String)entry.getKey()).equals("rank"))
        value = getStyledRankDisplayName(value); 
      messageString = messageString.replace(placeholder, value);
    } 
    return this.miniMessage.deserialize(messageString);
  }
  
  public Component getMessage(String key, String placeholder, String value) {
    return getMessage(key, Map.of(placeholder, value));
  }
  
  public boolean isValidSlot(int slot) {
    if (slot >= 0 && slot < getStorageSlots())
      return true; 
    if (isDonorEnabled() && Constants.Slots.isDonorSlot(slot))
      return true; 
    return false;
  }
  
  private boolean hasSlotUnlocked(Player player, int slot) {
    if (player == null)
      return false; 
    return (slot == 0);
  }
  
  public boolean checkRankRequirement(Player player, String requiredRank) {
    return hasRankRequirement(player, requiredRank);
  }
  
  public FileConfiguration getMessages() {
    return this.messages;
  }
  
  public static final class DonorRank {
    private final String name;
    
    private final String displayName;
    
    private final String permission;
    
    private final int slots;
    
    public DonorRank(String name, String displayName, String permission, int slots) {
      this.name = name;
      this.displayName = displayName;
      this.permission = permission;
      this.slots = slots;
    }
    
    public int slots() {
      return this.slots;
    }
    
    public String permission() {
      return this.permission;
    }
    
    public String displayName() {
      return this.displayName;
    }
    
    public String name() {
      return this.name;
    }
  }
  
  public static final class WithdrawalFee {
    private final int points;
    
    private final double money;
    
    public WithdrawalFee(int points, double money) {
      this.points = points;
      this.money = money;
    }
    
    public double money() {
      return this.money;
    }
    
    public int points() {
      return this.points;
    }
  }
  
  public boolean isWithdrawalCooldownEnabled() {
    return this.config.getBoolean("withdrawal-cooldown.enabled", true);
  }
  
  public long getWithdrawalCooldownMs() {
    return this.config.getLong("withdrawal-cooldown.duration-ms", 1500L);
  }
  
  public boolean canBypassWithdrawalCooldown(Player player) {
    return player.hasPermission("storageslots.bypass.cooldown");
  }
}
