package dev.lsdmc.utils;

import org.bukkit.NamespacedKey;
import net.kyori.adventure.text.format.NamedTextColor;

public final class Constants {
    
    public static final class Permissions {
        public static final String USE = "storageslots.use";
        public static final String ADMIN = "storageslots.admin";
        public static final String BYPASS_RANK = "storageslots.bypass.rank";
        public static final String BYPASS_COST = "storageslots.bypass.cost";
        public static final String BYPASS_COOLDOWN = "storageslots.bypass.cooldown";
        public static final String ALL = "storageslots.*";
        
        // Donor permissions
        public static final String DONOR_VIP = "storageslots.donor.vip";
        public static final String DONOR_MVP = "storageslots.donor.mvp";
        public static final String DONOR_ELITE = "storageslots.donor.elite";
        public static final String DONOR_ALL = "storageslots.donor.*";
    }
    
    public static final class Config {
        // Storage settings
        public static final String STORAGE_SLOTS = "storage.slots";
        public static final String STORAGE_TITLE = "storage.title";
        public static final String STORAGE_DEFAULT_COST = "storage.default-cost";
        public static final String STORAGE_REQUIRE_PROGRESSION = "storage.require-progression";
        public static final String STORAGE_MAX_ITEMS_PER_SLOT = "storage.max-items-per-slot";
        
        // Safezone settings
        public static final String SAFEZONE_ENABLED = "safezone.enabled";
        public static final String SAFEZONE_DETECTION_METHOD = "safezone.detection-method";
        public static final String SAFEZONE_REGION_NAME = "safezone.region-name";
        public static final String SAFEZONE_PVP_PRIORITY = "safezone.pvp-priority";
        public static final String SAFEZONE_WORLDS = "safezone.worlds";
        
        // Donor settings
        public static final String DONOR_ENABLED = "donor.enabled";
        public static final String DONOR_RANKS = "donor.ranks";
        public static final String DONOR_SLOTS_SEPARATE = "donor.slots.separate";
        public static final String DONOR_SLOTS_PERSIST = "donor.slots.persist";
        public static final String DONOR_SLOTS_PURCHASABLE = "donor.slots.purchasable";
        public static final String DONOR_SLOTS_COST_MULTIPLIER = "donor.slots.cost-multiplier";
        
        // Security settings
        public static final String SECURITY_MAX_SLOTS_PER_PLAYER = "security.max-slots-per-player";
        public static final String SECURITY_PREVENT_DUPLICATION = "security.prevent-item-duplication";
        public static final String SECURITY_LOG_TRANSACTIONS = "security.log-transactions";
        
        // Auto-save settings
        public static final String AUTO_SAVE_ENABLED = "auto-save.enabled";
        public static final String AUTO_SAVE_INTERVAL = "auto-save.interval";
        
        // Economy settings
        public static final String ECONOMY_CURRENCY_NAME = "economy.currency-name";
        public static final String ECONOMY_USE_VAULT = "economy.use-vault";
    }
    
    public static final class Keys {
        private static org.bukkit.plugin.Plugin plugin;
        
        public static void initialize(org.bukkit.plugin.Plugin pluginInstance) {
            plugin = pluginInstance;
        }
        
        public static NamespacedKey getStorageData() {
            return new NamespacedKey(plugin, "storage_data");
        }
    }
    
    public static final class Defaults {
        public static final int STORAGE_SLOTS = 9;
        public static final String STORAGE_TITLE = "gui.storage-title";
        public static final double DEFAULT_COST = 1000.0;
        public static final boolean REQUIRE_PROGRESSION = true;
        public static final int MAX_ITEMS_PER_SLOT = 64;
        public static final int MAX_SLOTS_PER_PLAYER = 54;
        public static final boolean AUTO_SAVE_ENABLED = true;
        public static final int AUTO_SAVE_INTERVAL = 300; // 5 minutes
        public static final String CURRENCY_NAME = "points";
        public static final boolean USE_VAULT = false;
        
        // Safezone defaults
        public static final boolean SAFEZONE_ENABLED = true;
        public static final String SAFEZONE_DETECTION_METHOD = "region";
        public static final String SAFEZONE_REGION_NAME = "safezone";
        public static final int SAFEZONE_PVP_PRIORITY = 0;
        
        // Donor defaults
        public static final boolean DONOR_ENABLED = true;
        public static final boolean DONOR_SLOTS_SEPARATE = true;
        public static final boolean DONOR_SLOTS_PERSIST = true;
        public static final boolean DONOR_SLOTS_PURCHASABLE = false;
        public static final double DONOR_SLOTS_COST_MULTIPLIER = 2.0;
    }
    
    public static final class GUI {
        public static final String LOCKED_SLOT_NAME = "gui.locked-slot.name";
        public static final String DONOR_SLOT_NAME = "gui.donor-slot.name";
        public static final String LOCKED_SLOT_MATERIAL_HAS_RANK = "RED_STAINED_GLASS_PANE";
        public static final String LOCKED_SLOT_MATERIAL_NO_RANK = "BLACK_STAINED_GLASS_PANE";
        public static final String LOCKED_SLOT_MATERIAL_ADMIN = "GRAY_STAINED_GLASS_PANE";
        public static final String DONOR_SLOT_MATERIAL = "GOLD_BLOCK";
    }
    
    public static final class DetectionMethods {
        public static final String REGION = "region";
        public static final String PVP = "pvp";
        public static final String WORLD = "world";
    }
    
    public static final class DonorFeatures {
        public static final String REDUCED_WITHDRAWAL_FEES = "reduced_withdrawal_fees";
        public static final String FREE_WITHDRAWAL = "free_withdrawal";
        public static final String FASTER_SLOT_ACCESS = "faster_slot_access";
        public static final String SLOT_DISCOUNT = "slot_discount";
        public static final String PRIORITY_SUPPORT = "priority_support";
    }
    
    public static final class SlotTypes {
        public static final String REGULAR = "regular";
        public static final String DONOR = "donor";
        public static final String JG_RANK = "jg";
        public static final String FD_RANK = "fd";
        public static final String CA_RANK = "ca";
    }
    
    public static final class Messages {
        // General Messages
        public static final String NO_PERMISSION = "no-permission";
        public static final String INVALID_SLOT = "invalid-slot";
        public static final String INVALID_NUMBER = "invalid-number";
        public static final String PLAYER_NOT_FOUND = "player-not-found";
        public static final String CONFIG_RELOADED = "config-reloaded";
        
        // Storage Messages
        public static final String STORAGE_OPENED = "storage-opened";
        public static final String ALREADY_UNLOCKED = "already-unlocked";
        public static final String SLOT_PURCHASED = "slot-purchased";
        public static final String INSUFFICIENT_FUNDS = "insufficient-funds";
        public static final String PROHIBITED_ITEM = "prohibited-item";
        public static final String PROHIBITED_ITEM_RETURNED = "prohibited-item-returned";
        public static final String RANK_REQUIRED = "rank-required";
        public static final String PREVIOUS_SLOT_REQUIRED = "previous-slot-required";
        public static final String MAX_SLOTS_REACHED = "max-slots-reached";
        public static final String MAX_ITEMS_PER_SLOT = "max-items-per-slot";
        
        // Safezone Messages
        public static final String SAFEZONE_REQUIRED = "safezone-required";
        public static final String NOT_IN_SAFEZONE = "not-in-safezone";
        
        // Admin Messages
        public static final String STORAGE_RESET = "storage-reset";
        public static final String PLAYER_STORAGE_RESET = "player-storage-reset";
        public static final String SLOT_ALREADY_OWNED = "slot-already-owned";
        public static final String SLOT_NOT_OWNED = "slot-not-owned";
        public static final String SLOT_NOT_EMPTY = "slot-not-empty";
        public static final String SLOT_REMOVED = "slot-removed";
        public static final String SLOT_GIVEN = "slot-given";
        public static final String NO_SLOTS_OWNED = "no-slots-owned";
        public static final String SLOTS_LIST_HEADER = "slots-list-header";
        public static final String SLOTS_LIST_FORMAT = "slots-list-format";
        public static final String COST_SET = "cost-set";
        
        // Donor Messages
        public static final String DONOR_FEATURE_UNAVAILABLE = "donor-feature-unavailable";
        
        // Withdrawal Messages
        public static final String WITHDRAWAL_FEE = "withdrawal-fee";
        public static final String INSUFFICIENT_WITHDRAWAL_FUNDS = "insufficient-withdrawal-funds";
        public static final String INSUFFICIENT_POINTS = "insufficient-points";
        public static final String ITEM_WITHDRAWN = "item-withdrawn";
        public static final String FREE_WITHDRAWAL = "free-withdrawal";
        
        // Usage Messages
        public static final String USAGE_STORAGE = "usage-storage";
        public static final String USAGE_BUYSTORAGE = "usage-buystorage";
        public static final String USAGE_STORAGECOST = "usage-storagecost";
        public static final String USAGE_STORAGEDELETE = "usage-storagedelete";
        public static final String USAGE_STORAGEADMIN = "usage-storageadmin";
        public static final String USAGE_VIEWSTORAGE = "usage-viewstorage";
        public static final String USAGE_REMOVESLOT = "usage-removeslot";
        public static final String USAGE_GIVESLOT = "usage-giveslot";
        public static final String USAGE_LISTSLOTS = "usage-listslots";
        public static final String USAGE_DEBUGSAFEZONE = "usage-debugsafezone";
    }
    
    public static final class Colors {
        public static final NamedTextColor ERROR = NamedTextColor.RED;
        public static final NamedTextColor SUCCESS = NamedTextColor.GREEN;
        public static final NamedTextColor INFO = NamedTextColor.GRAY;
        public static final NamedTextColor HEADER = NamedTextColor.GOLD;
        public static final NamedTextColor HIGHLIGHT = NamedTextColor.YELLOW;
    }
} 