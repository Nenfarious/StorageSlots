package dev.lsdmc;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import dev.lsdmc.utils.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Map;

public final class StorageCommandExecutor implements CommandExecutor, TabCompleter {
    private final StorageSlots plugin;
    private final StorageManager storageManager;
    private final StorageConfig config;
    private final StoragePermissionManager permissionManager;

    public StorageCommandExecutor(StorageSlots plugin, StorageManager storageManager, 
                                StorageConfig config, StoragePermissionManager permissionManager) {
        this.plugin = plugin;
        this.storageManager = storageManager;
        this.config = config;
        this.permissionManager = permissionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            return handleCommand(sender, command, label, args);
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error executing command '" + command.getName() + "': " + e.getMessage())
                .color(Constants.Colors.ERROR));
            sender.sendMessage(config.getMessage("errors.command-execution-failed", 
                Map.of("error", e.getMessage())));
            return true;
        }
    }
    
    private boolean handleCommand(CommandSender sender, Command command, String label, String[] args) {
        // Non-player commands
        if (command.getName().equalsIgnoreCase("storagereload")) {
            return handleReloadCommand(sender);
        }
        
        // Player-only commands
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players!")
                .color(Constants.Colors.ERROR));
            return true;
        }

        return switch (command.getName().toLowerCase()) {
            case "storage" -> handleStorageCommand(player);
            case "buystorage" -> handleBuyStorageCommand(player, args);
            case "storagecost" -> handleStorageCostCommand(player, args);
            case "storagedelete" -> handleStorageDeleteCommand(player, args);
            case "storageadmin" -> handleStorageAdminCommand(player, args);
            case "viewstorage" -> handleViewStorageCommand(player, args);
            case "removeslot" -> handleRemoveSlotCommand(player, args);
            case "giveslot" -> handleGiveSlotCommand(player, args);
            case "listslots" -> handleListSlotsCommand(player, args);
            case "togglecooldown" -> handleToggleCooldownCommand(player, args);
            case "testeconomy" -> handleTestEconomyCommand(player);
            case "testfallback" -> handleTestFallbackCommand(player);
            case "resetapi" -> handleResetApiCommand(player);
            case "testnotifications" -> handleTestNotificationsCommand(player);
            case "debugsafezone" -> handleDebugSafezoneCommand(player, args);
            default -> {
                player.sendMessage(config.getMessage("errors.unknown-command"));
                yield false;
            }
        };
    }
    
    private boolean handleStorageCommand(Player player) {
        if (!permissionManager.hasPermission(player, Constants.Permissions.USE)) {
            player.sendMessage(config.getMessage(Constants.Messages.NO_PERMISSION));
            return true;
        }
        
        if (!plugin.getSafezoneManager().isInSafezone(player)) {
            player.sendMessage(config.getMessage(Constants.Messages.SAFEZONE_REQUIRED));
            return true;
        }
        
        storageManager.openStorage(player);
        return true;
    }
    
    private boolean handleBuyStorageCommand(Player player, String[] args) {
        if (!permissionManager.hasPermission(player, Constants.Permissions.USE)) {
            player.sendMessage(config.getMessage(Constants.Messages.NO_PERMISSION));
            return true;
        }
        
        if (args.length != 1) {
            player.sendMessage(config.getMessage(Constants.Messages.USAGE_BUYSTORAGE));
            return true;
        }
        
        try {
            int slot = Integer.parseInt(args[0]) - 1;
            if (!config.isValidSlot(slot)) {
                player.sendMessage(config.getMessage(Constants.Messages.INVALID_SLOT));
                return true;
            }
            
            storageManager.purchaseSlot(player, slot);
        } catch (NumberFormatException e) {
            player.sendMessage(config.getMessage(Constants.Messages.INVALID_NUMBER));
        }
        
        return true;
    }
    
    private boolean handleStorageCostCommand(Player player, String[] args) {
        if (!permissionManager.hasPermission(player, Constants.Permissions.ADMIN)) {
            player.sendMessage(config.getMessage(Constants.Messages.NO_PERMISSION));
            return true;
        }
        
        if (args.length != 2) {
            player.sendMessage(config.getMessage(Constants.Messages.USAGE_STORAGECOST));
            return true;
        }
        
        try {
            int slot = Integer.parseInt(args[0]) - 1;
            double cost = Double.parseDouble(args[1]);
            
            if (!config.isValidSlot(slot)) {
                player.sendMessage(config.getMessage(Constants.Messages.INVALID_SLOT));
                return true;
            }
            
            if (cost < 0) {
                player.sendMessage(Component.text("Cost cannot be negative!")
                    .color(Constants.Colors.ERROR));
                return true;
            }
            
            config.setSlotCost(slot, cost);
            player.sendMessage(config.getMessage(Constants.Messages.COST_SET, Map.of(
                "slot", String.valueOf(slot + 1),
                "cost", plugin.getEconomyManager().formatCurrency(cost),
                "currency", plugin.getEconomyManager().getCurrencyName()
            )));
        } catch (NumberFormatException e) {
            player.sendMessage(config.getMessage(Constants.Messages.INVALID_NUMBER));
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error setting slot cost: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            player.sendMessage(config.getMessage("errors.config-save-failed"));
        }
        
        return true;
    }
    
    private boolean handleReloadCommand(CommandSender sender) {
        if (!permissionManager.hasPermission(sender, Constants.Permissions.ADMIN)) {
            sender.sendMessage(config.getMessage(Constants.Messages.NO_PERMISSION));
            return true;
        }
        
        try {
            config.reload();
            sender.sendMessage(config.getMessage(Constants.Messages.CONFIG_RELOADED));
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error reloading configuration: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            sender.sendMessage(config.getMessage("errors.config-load-failed", 
                Map.of("error", e.getMessage())));
        }
        
        return true;
    }
    
    private boolean handleStorageDeleteCommand(Player player, String[] args) {
        if (!permissionManager.hasPermission(player, Constants.Permissions.ADMIN)) {
            player.sendMessage(config.getMessage(Constants.Messages.NO_PERMISSION));
            return true;
        }
        
        if (args.length != 1) {
            player.sendMessage(config.getMessage(Constants.Messages.USAGE_STORAGEDELETE));
            return true;
        }
        
        try {
            if (args[0].equalsIgnoreCase("all")) {
                storageManager.resetAllStorage();
                player.sendMessage(config.getMessage(Constants.Messages.STORAGE_RESET));
            } else {
                UUID targetId = storageManager.findPlayerUUID(args[0]);
                if (targetId == null) {
                    player.sendMessage(config.getMessage(Constants.Messages.PLAYER_NOT_FOUND));
                    return true;
                }
                
                storageManager.resetPlayerStorage(targetId);
                player.sendMessage(config.getMessage(Constants.Messages.PLAYER_STORAGE_RESET, 
                    Map.of("player", args[0])));
            }
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error deleting storage: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            player.sendMessage(config.getMessage("errors.data-operation-failed"));
        }
        
        return true;
    }
    
    private boolean handleStorageAdminCommand(Player player, String[] args) {
        if (!permissionManager.hasPermission(player, Constants.Permissions.ADMIN)) {
            player.sendMessage(config.getMessage(Constants.Messages.NO_PERMISSION));
            return true;
        }
        
        if (args.length != 1) {
            player.sendMessage(config.getMessage(Constants.Messages.USAGE_STORAGEADMIN));
            return true;
        }
        
        if (!plugin.getSafezoneManager().isInSafezone(player)) {
            player.sendMessage(config.getMessage(Constants.Messages.SAFEZONE_REQUIRED));
            return true;
        }
        
        try {
            UUID targetId = storageManager.findPlayerUUID(args[0]);
            if (targetId == null) {
                player.sendMessage(config.getMessage(Constants.Messages.PLAYER_NOT_FOUND));
                return true;
            }
            
            storageManager.openPlayerStorage(player, targetId);
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error opening admin storage: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            player.sendMessage(config.getMessage("errors.inventory-creation-failed"));
        }
        
        return true;
    }
    
    private boolean handleViewStorageCommand(Player player, String[] args) {
        if (!permissionManager.hasPermission(player, Constants.Permissions.ADMIN)) {
            player.sendMessage(config.getMessage(Constants.Messages.NO_PERMISSION));
            return true;
        }
        
        if (args.length != 1) {
            player.sendMessage(config.getMessage(Constants.Messages.USAGE_VIEWSTORAGE));
            return true;
        }
        
        if (!plugin.getSafezoneManager().isInSafezone(player)) {
            player.sendMessage(config.getMessage(Constants.Messages.SAFEZONE_REQUIRED));
            return true;
        }
        
        try {
            UUID targetId = storageManager.findPlayerUUID(args[0]);
            if (targetId == null) {
                player.sendMessage(config.getMessage(Constants.Messages.PLAYER_NOT_FOUND));
                return true;
            }
            
            storageManager.openPlayerStorage(player, targetId);
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error viewing storage: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            player.sendMessage(config.getMessage("errors.inventory-creation-failed"));
        }
        
        return true;
    }
    
    private boolean handleRemoveSlotCommand(Player player, String[] args) {
        if (!permissionManager.hasPermission(player, Constants.Permissions.ADMIN)) {
            player.sendMessage(config.getMessage(Constants.Messages.NO_PERMISSION));
            return true;
        }
        
        if (args.length != 2) {
            player.sendMessage(config.getMessage(Constants.Messages.USAGE_REMOVESLOT));
            return true;
        }
        
        try {
            UUID targetId = storageManager.findPlayerUUID(args[0]);
            if (targetId == null) {
                player.sendMessage(config.getMessage(Constants.Messages.PLAYER_NOT_FOUND));
                return true;
            }
            
            int slot = Integer.parseInt(args[1]) - 1;
            if (!config.isValidSlot(slot)) {
                player.sendMessage(config.getMessage(Constants.Messages.INVALID_SLOT));
                return true;
            }
            
            storageManager.removeSlot(player, args[0], slot);
        } catch (NumberFormatException e) {
            player.sendMessage(config.getMessage(Constants.Messages.INVALID_NUMBER));
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error removing slot: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            player.sendMessage(config.getMessage("errors.data-operation-failed"));
        }
        
        return true;
    }
    
    private boolean handleGiveSlotCommand(Player player, String[] args) {
        if (!permissionManager.hasPermission(player, Constants.Permissions.ADMIN)) {
            player.sendMessage(config.getMessage(Constants.Messages.NO_PERMISSION));
            return true;
        }
        
        if (args.length != 2) {
            player.sendMessage(config.getMessage(Constants.Messages.USAGE_GIVESLOT));
            return true;
        }
        
        try {
            UUID targetId = storageManager.findPlayerUUID(args[0]);
            if (targetId == null) {
                player.sendMessage(config.getMessage(Constants.Messages.PLAYER_NOT_FOUND));
                return true;
            }
            
            int slot = Integer.parseInt(args[1]) - 1;
            if (!config.isValidSlot(slot)) {
                player.sendMessage(config.getMessage(Constants.Messages.INVALID_SLOT));
                return true;
            }
            
            storageManager.giveSlot(player, args[0], slot);
        } catch (NumberFormatException e) {
            player.sendMessage(config.getMessage(Constants.Messages.INVALID_NUMBER));
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error giving slot: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            player.sendMessage(config.getMessage("errors.data-operation-failed"));
        }
        
        return true;
    }
    
    private boolean handleListSlotsCommand(Player player, String[] args) {
        if (!permissionManager.hasPermission(player, Constants.Permissions.ADMIN)) {
            player.sendMessage(config.getMessage(Constants.Messages.NO_PERMISSION));
            return true;
        }
        
        if (args.length != 1) {
            player.sendMessage(config.getMessage(Constants.Messages.USAGE_LISTSLOTS));
            return true;
        }
        
        try {
            UUID targetId = storageManager.findPlayerUUID(args[0]);
            if (targetId == null) {
                player.sendMessage(config.getMessage(Constants.Messages.PLAYER_NOT_FOUND));
                return true;
            }
            
            storageManager.listSlots(player, args[0]);
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error listing slots: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            player.sendMessage(config.getMessage("errors.data-operation-failed"));
        }
        
        return true;
    }

    private boolean handleToggleCooldownCommand(Player player, String[] args) {
        if (!permissionManager.hasPermission(player, Constants.Permissions.ADMIN)) {
            player.sendMessage(config.getMessage(Constants.Messages.NO_PERMISSION));
            return true;
        }
        
        Player target = player; // Default to self
        
        if (args.length == 1) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(config.getMessage(Constants.Messages.PLAYER_NOT_FOUND));
                return true;
            }
        } else if (args.length > 1) {
            player.sendMessage(Component.text("Usage: /togglecooldown [player]")
                .color(Constants.Colors.ERROR));
            return true;
        }
        
        try {
            boolean currentBypass = target.hasPermission(Constants.Permissions.BYPASS_COOLDOWN);
            
            if (currentBypass) {
                // Remove bypass permission
                target.removeAttachment(target.addAttachment(plugin, Constants.Permissions.BYPASS_COOLDOWN, false));
                player.sendMessage(Component.text("Removed withdrawal cooldown bypass from " + target.getName())
                    .color(Constants.Colors.SUCCESS));
                if (!target.equals(player)) {
                    target.sendMessage(Component.text("Your withdrawal cooldown bypass has been removed by " + player.getName())
                        .color(Constants.Colors.INFO));
                }
            } else {
                // Add bypass permission
                target.addAttachment(plugin, Constants.Permissions.BYPASS_COOLDOWN, true);
                player.sendMessage(Component.text("Granted withdrawal cooldown bypass to " + target.getName())
                    .color(Constants.Colors.SUCCESS));
                if (!target.equals(player)) {
                    target.sendMessage(Component.text("You have been granted withdrawal cooldown bypass by " + player.getName())
                        .color(Constants.Colors.SUCCESS));
                }
            }
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error toggling cooldown bypass: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            player.sendMessage(Component.text("Failed to toggle cooldown bypass!")
                .color(Constants.Colors.ERROR));
        }
        
        return true;
    }

    private boolean handleTestEconomyCommand(Player player) {
        if (!permissionManager.hasPermission(player, Constants.Permissions.ADMIN)) {
            player.sendMessage(config.getMessage(Constants.Messages.NO_PERMISSION));
            return true;
        }
        
        try {
            // Test PlayerPoints integration
            boolean integrationTest = plugin.getEconomyManager().testPlayerPointsIntegration();
            
            // Test balance retrieval
            plugin.getEconomyManager().getBalance(player).thenAccept(balance -> {
                player.sendMessage(Component.text("PlayerPoints Integration Test Results:")
                    .color(Constants.Colors.HEADER));
                player.sendMessage(Component.text("Integration Test: " + (integrationTest ? "PASSED" : "FAILED"))
                    .color(integrationTest ? Constants.Colors.SUCCESS : Constants.Colors.ERROR));
                player.sendMessage(Component.text("Your Balance: " + plugin.getEconomyManager().formatCurrency(balance) + " points")
                    .color(Constants.Colors.INFO));
                
                if (integrationTest) {
                    player.sendMessage(Component.text("PlayerPoints integration is working correctly!")
                        .color(Constants.Colors.SUCCESS));
                } else {
                    player.sendMessage(Component.text("PlayerPoints integration has issues. Check console for details.")
                        .color(Constants.Colors.ERROR));
                }
            });
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error testing economy integration: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            player.sendMessage(Component.text("Failed to test economy integration!")
                .color(Constants.Colors.ERROR));
        }
        
        return true;
    }

    private boolean handleTestFallbackCommand(Player player) {
        if (!permissionManager.hasPermission(player, Constants.Permissions.ADMIN)) {
            player.sendMessage(config.getMessage(Constants.Messages.NO_PERMISSION));
            return true;
        }
        
        try {
            player.sendMessage(Component.text("Testing PlayerPoints Fallback Mechanism:")
                .color(Constants.Colors.HEADER));
            
            // Test API status
            boolean apiTest = plugin.getEconomyManager().testPlayerPointsIntegration();
            player.sendMessage(Component.text("API Test: " + (apiTest ? "PASSED" : "FAILED"))
                .color(apiTest ? Constants.Colors.SUCCESS : Constants.Colors.ERROR));
            
            // Test balance retrieval
            plugin.getEconomyManager().getBalance(player).thenAccept(balance -> {
                player.sendMessage(Component.text("Balance Retrieval: " + plugin.getEconomyManager().formatCurrency(balance) + " points")
                    .color(Constants.Colors.INFO));
                
                // Test taking a small amount (1 point) to test the fallback
                plugin.getEconomyManager().takePoints(player, 1).thenAccept(success -> {
                    if (success) {
                        player.sendMessage(Component.text("Points Transaction Test: PASSED")
                            .color(Constants.Colors.SUCCESS));
                        
                        // Give the point back
                        plugin.getEconomyManager().takeMoney(player, -1).thenAccept(giveBack -> {
                            player.sendMessage(Component.text("Fallback mechanism is working correctly!")
                                .color(Constants.Colors.SUCCESS));
                        });
                    } else {
                        player.sendMessage(Component.text("Points Transaction Test: FAILED")
                            .color(Constants.Colors.ERROR));
                        player.sendMessage(Component.text("Check console for detailed error messages.")
                            .color(Constants.Colors.ERROR));
                    }
                });
            });
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error testing fallback mechanism: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            player.sendMessage(Component.text("Failed to test fallback mechanism!")
                .color(Constants.Colors.ERROR));
        }
        
        return true;
    }

    private boolean handleResetApiCommand(Player player) {
        if (!permissionManager.hasPermission(player, Constants.Permissions.ADMIN)) {
            player.sendMessage(config.getMessage(Constants.Messages.NO_PERMISSION));
            return true;
        }
        
        try {
            // Reset the API status
            plugin.getEconomyManager().resetApiStatus();
            
            player.sendMessage(Component.text("PlayerPoints API status has been reset to working.")
                .color(Constants.Colors.SUCCESS));
            player.sendMessage(Component.text("The system will now try the API first before using command fallback.")
                .color(Constants.Colors.INFO));
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error resetting API status: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            player.sendMessage(Component.text("Failed to reset API status!")
                .color(Constants.Colors.ERROR));
        }
        
        return true;
    }

    private boolean handleTestNotificationsCommand(Player player) {
        if (!permissionManager.hasPermission(player, Constants.Permissions.ADMIN)) {
            player.sendMessage(config.getMessage(Constants.Messages.NO_PERMISSION));
            return true;
        }
        
        try {
            player.sendMessage(Component.text("=== Testing Notification System ===")
                .color(Constants.Colors.HEADER));
            
            // Get player data
            PlayerStorageData data = storageManager.getDataManager().getPlayerData(player.getUniqueId());
            
            // Show current notification status
            player.sendMessage(Component.text("Last notified rank: " + (data.getLastNotifiedRank() != null ? data.getLastNotifiedRank() : "None"))
                .color(Constants.Colors.INFO));
            player.sendMessage(Component.text("Has seen storage: " + data.hasSeenNewSlotNotification())
                .color(Constants.Colors.INFO));
            player.sendMessage(Component.text("Last reminder time: " + new java.util.Date(data.getLastReminderTime()))
                .color(Constants.Colors.INFO));
            
            // Find next unlockable slot
            int nextSlot = -1;
            for (int i = 0; i < config.getStorageSlots(); i++) {
                if (!data.hasSlotUnlocked(i)) {
                    // Check progression requirement
                    if (config.isProgressionRequired() && i > 0 && !data.hasSlotUnlocked(i - 1)) {
                        continue; // Can't unlock this slot yet due to progression
                    }
                    nextSlot = i;
                    break;
                }
            }
            
            if (nextSlot == -1) {
                player.sendMessage(Component.text("No unlockable slots found - all slots already unlocked!")
                    .color(Constants.Colors.SUCCESS));
            } else {
                String requiredRank = config.getRequiredRank(nextSlot);
                boolean hasRank = config.hasRankRequirement(player, requiredRank);
                
                player.sendMessage(Component.text("Next unlockable slot: " + (nextSlot + 1))
                    .color(Constants.Colors.INFO));
                player.sendMessage(Component.text("Required rank: " + (requiredRank != null ? requiredRank : "None"))
                    .color(Constants.Colors.INFO));
                player.sendMessage(Component.text("Has required rank: " + hasRank)
                    .color(hasRank ? Constants.Colors.SUCCESS : Constants.Colors.ERROR));
                
                if (hasRank) {
                    player.sendMessage(Component.text("Manually triggering notification check...")
                        .color(Constants.Colors.HIGHLIGHT));
                    
                    // Manually trigger notification check
                    plugin.checkPlayerForNewSlots(player);
                    
                    player.sendMessage(Component.text("Notification check completed!")
                        .color(Constants.Colors.SUCCESS));
                } else {
                    player.sendMessage(Component.text("Cannot send notification - player doesn't have required rank")
                        .color(Constants.Colors.ERROR));
                }
            }
            
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error testing notifications: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            player.sendMessage(Component.text("Failed to test notifications: " + e.getMessage())
                .color(Constants.Colors.ERROR));
        }
        
        return true;
    }

    private boolean handleDebugSafezoneCommand(Player player, String[] args) {
        if (!permissionManager.hasPermission(player, Constants.Permissions.ADMIN)) {
            player.sendMessage(config.getMessage(Constants.Messages.NO_PERMISSION));
            return true;
        }
        
        try {
            // Use the existing debug method from SafezoneManager
            plugin.getSafezoneManager().debugSafezoneInfo(player, player);
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error checking safezone: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            player.sendMessage(config.getMessage("errors.data-operation-failed"));
        }
        
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!permissionManager.hasPermission(sender, Constants.Permissions.USE)) {
            return new ArrayList<>();
        }

        try {
            return switch (command.getName().toLowerCase()) {
                case "buystorage" -> {
                    if (args.length == 1) {
                        yield getSlotNumbers(args[0]);
                    }
                    yield new ArrayList<>();
                }
                case "storagecost" -> {
                    if (!permissionManager.hasPermission(sender, Constants.Permissions.ADMIN)) {
                        yield new ArrayList<>();
                    }
                    if (args.length == 1) {
                        yield getSlotNumbers(args[0]);
                    } else if (args.length == 2) {
                        yield Arrays.asList("100", "500", "1000", "5000", "10000");
                    }
                    yield new ArrayList<>();
                }
                case "storagedelete" -> {
                    if (!permissionManager.hasPermission(sender, Constants.Permissions.ADMIN)) {
                        yield new ArrayList<>();
                    }
                    if (args.length == 1) {
                        List<String> completions = new ArrayList<>(getPlayerNames(args[0]));
                        if ("all".startsWith(args[0].toLowerCase())) {
                            completions.add("all");
                        }
                        yield completions;
                    }
                    yield new ArrayList<>();
                }
                case "storageadmin", "viewstorage", "listslots" -> {
                    if (!permissionManager.hasPermission(sender, Constants.Permissions.ADMIN)) {
                        yield new ArrayList<>();
                    }
                    if (args.length == 1) {
                        yield getPlayerNames(args[0]);
                    }
                    yield new ArrayList<>();
                }
                case "removeslot", "giveslot" -> {
                    if (!permissionManager.hasPermission(sender, Constants.Permissions.ADMIN)) {
                        yield new ArrayList<>();
                    }
                    if (args.length == 1) {
                        yield getPlayerNames(args[0]);
                    } else if (args.length == 2) {
                        yield getSlotNumbers(args[1]);
                    }
                    yield new ArrayList<>();
                }
                case "togglecooldown" -> {
                    if (!permissionManager.hasPermission(sender, Constants.Permissions.ADMIN)) {
                        yield new ArrayList<>();
                    }
                    if (args.length == 1) {
                        yield getPlayerNames(args[0]);
                    }
                    yield new ArrayList<>();
                }
                case "testeconomy" -> {
                    if (!permissionManager.hasPermission(sender, Constants.Permissions.ADMIN)) {
                        yield new ArrayList<>();
                    }
                    yield new ArrayList<>();
                }
                case "testfallback" -> {
                    if (!permissionManager.hasPermission(sender, Constants.Permissions.ADMIN)) {
                        yield new ArrayList<>();
                    }
                    yield new ArrayList<>();
                }
                case "resetapi" -> {
                    if (!permissionManager.hasPermission(sender, Constants.Permissions.ADMIN)) {
                        yield new ArrayList<>();
                    }
                    yield new ArrayList<>();
                }
                case "testnotifications" -> {
                    if (!permissionManager.hasPermission(sender, Constants.Permissions.ADMIN)) {
                        yield new ArrayList<>();
                    }
                    yield new ArrayList<>();
                }
                case "debugsafezone" -> {
                    if (!permissionManager.hasPermission(sender, Constants.Permissions.ADMIN)) {
                        yield new ArrayList<>();
                    }
                    yield new ArrayList<>();
                }
                default -> new ArrayList<>();
            };
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error in tab completion: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            return new ArrayList<>();
        }
    }

    private List<String> getPlayerNames(String partial) {
        try {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial.toLowerCase()))
                    .sorted()
                    .limit(10) // Limit to prevent spam
                    .collect(Collectors.toList());
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error getting player names: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            return new ArrayList<>();
        }
    }

    private List<String> getSlotNumbers(String partial) {
        try {
            List<String> slots = new ArrayList<>();
            int maxSlots = Math.min(config.getStorageSlots(), 54);
            
            for (int i = 1; i <= maxSlots; i++) {
                String slot = String.valueOf(i);
                if (slot.startsWith(partial)) {
                    slots.add(slot);
                }
            }
            
            return slots;
        } catch (Exception e) {
            plugin.getComponentLogger().error(Component.text("Error getting slot numbers: " + e.getMessage())
                .color(Constants.Colors.ERROR));
            return new ArrayList<>();
        }
    }
} 