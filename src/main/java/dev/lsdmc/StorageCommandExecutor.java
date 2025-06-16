package dev.lsdmc;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class StorageCommandExecutor implements CommandExecutor, TabCompleter {
    private final StorageSlots plugin;
    private final StorageManager storageManager;
    private final Config config;
    private final StoragePermissionManager permissionManager;

    public StorageCommandExecutor(StorageSlots plugin, StorageManager storageManager, Config config, StoragePermissionManager permissionManager) {
        this.plugin = plugin;
        this.storageManager = storageManager;
        this.config = config;
        this.permissionManager = permissionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player) && !command.getName().equals("storagereload")) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        Player player = sender instanceof Player ? (Player) sender : null;

        switch (command.getName().toLowerCase()) {
            case "storage":
                if (!permissionManager.hasPermission(player, "storageslots.use")) {
                    player.sendMessage(config.getMessage("no-permission"));
                    return true;
                }
                storageManager.openStorage(player);
                break;

            case "buystorage":
                if (!permissionManager.hasPermission(player, "storageslots.use")) {
                    player.sendMessage(config.getMessage("no-permission"));
                    return true;
                }
                if (args.length != 1) {
                    player.sendMessage("§cUsage: /buystorage <slot>");
                    return true;
                }
                try {
                    int slot = Integer.parseInt(args[0]) - 1;
                    storageManager.purchaseSlot(player, slot);
                } catch (NumberFormatException e) {
                    player.sendMessage(config.getMessage("invalid-number"));
                }
                break;

            case "storagecost":
                if (!permissionManager.hasPermission(player, "storageslots.admin")) {
                    player.sendMessage(config.getMessage("no-permission"));
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage("§cUsage: /storagecost <slot> <cost>");
                    return true;
                }
                try {
                    int slot = Integer.parseInt(args[0]) - 1;
                    double cost = Double.parseDouble(args[1]);
                    if (cost < 0) {
                        player.sendMessage("§cCost cannot be negative!");
                        return true;
                    }
                    config.setSlotCost(slot, cost);
                    player.sendMessage("§aSet cost for slot " + (slot + 1) + " to " + plugin.getEconomyManager().formatCurrency(cost));
                } catch (NumberFormatException e) {
                    player.sendMessage(config.getMessage("invalid-number"));
                }
                break;

            case "storagereload":
                if (!permissionManager.hasPermission(sender, "storageslots.admin")) {
                    sender.sendMessage(config.getMessage("no-permission"));
                    return true;
                }
                config.reloadConfig();
                sender.sendMessage("§aConfiguration reloaded!");
                break;

            case "storagedelete":
                if (!permissionManager.hasPermission(player, "storageslots.admin")) {
                    player.sendMessage(config.getMessage("no-permission"));
                    return true;
                }
                if (args.length != 1) {
                    player.sendMessage("§cUsage: /storagedelete <player|all>");
                    return true;
                }
                if (args[0].equalsIgnoreCase("all")) {
                    storageManager.resetAllStorage();
                    player.sendMessage(config.getMessage("storage-reset"));
                } else {
                    UUID targetId = storageManager.findPlayerUUID(args[0]);
                    if (targetId == null) {
                        player.sendMessage(config.getMessage("player-not-found"));
                        return true;
                    }
                    storageManager.resetPlayerStorage(targetId);
                    player.sendMessage(config.getMessage("player-storage-reset")
                            .replace("%player%", args[0]));
                }
                break;

            case "storageadmin":
                if (!permissionManager.hasPermission(player, "storageslots.admin")) {
                    player.sendMessage(config.getMessage("no-permission"));
                    return true;
                }
                if (args.length != 1) {
                    player.sendMessage("§cUsage: /storageadmin <player>");
                    return true;
                }
                UUID targetId = storageManager.findPlayerUUID(args[0]);
                if (targetId == null) {
                    player.sendMessage(config.getMessage("player-not-found"));
                    return true;
                }
                storageManager.openPlayerStorage(player, targetId);
                break;

            case "viewstorage":
                if (!permissionManager.hasPermission(player, "storageslots.admin")) {
                    player.sendMessage(config.getMessage("no-permission"));
                    return true;
                }
                if (args.length != 1) {
                    player.sendMessage("§cUsage: /viewstorage <player>");
                    return true;
                }
                targetId = storageManager.findPlayerUUID(args[0]);
                if (targetId == null) {
                    player.sendMessage(config.getMessage("player-not-found"));
                    return true;
                }
                storageManager.openPlayerStorage(player, targetId);
                break;

            case "removeslot":
                if (!permissionManager.hasPermission(player, "storageslots.admin")) {
                    player.sendMessage(config.getMessage("no-permission"));
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage("§cUsage: /removeslot <player> <slot>");
                    return true;
                }
                try {
                    targetId = storageManager.findPlayerUUID(args[0]);
                    if (targetId == null) {
                        player.sendMessage(config.getMessage("player-not-found"));
                        return true;
                    }
                    int slot = Integer.parseInt(args[1]) - 1;
                    storageManager.removeSlot(player, args[0], slot);
                } catch (NumberFormatException e) {
                    player.sendMessage(config.getMessage("invalid-number"));
                }
                break;

            case "giveslot":
                if (!permissionManager.hasPermission(player, "storageslots.admin")) {
                    player.sendMessage(config.getMessage("no-permission"));
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage("§cUsage: /giveslot <player> <slot>");
                    return true;
                }
                try {
                    targetId = storageManager.findPlayerUUID(args[0]);
                    if (targetId == null) {
                        player.sendMessage(config.getMessage("player-not-found"));
                        return true;
                    }
                    int slot = Integer.parseInt(args[1]) - 1;
                    storageManager.giveSlot(player, args[0], slot);
                } catch (NumberFormatException e) {
                    player.sendMessage(config.getMessage("invalid-number"));
                }
                break;

            case "listslots":
                if (!permissionManager.hasPermission(player, "storageslots.admin")) {
                    player.sendMessage(config.getMessage("no-permission"));
                    return true;
                }
                if (args.length != 1) {
                    player.sendMessage("§cUsage: /listslots <player>");
                    return true;
                }
                targetId = storageManager.findPlayerUUID(args[0]);
                if (targetId == null) {
                    player.sendMessage(config.getMessage("player-not-found"));
                    return true;
                }
                storageManager.listSlots(player, args[0]);
                break;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!permissionManager.hasPermission(sender, "storageslots.admin")) {
            return new ArrayList<>();
        }

        switch (command.getName().toLowerCase()) {
            case "storagecost":
                if (args.length == 1) {
                    return getSlotNumbers(args[0]);
                }
                break;

            case "storagedelete":
            case "storageadmin":
            case "viewstorage":
            case "listslots":
                if (args.length == 1) {
                    return getPlayerNames(args[0]);
                }
                break;

            case "removeslot":
            case "giveslot":
                if (args.length == 1) {
                    return getPlayerNames(args[0]);
                } else if (args.length == 2) {
                    return getSlotNumbers(args[1]);
                }
                break;
        }
        return new ArrayList<>();
    }

    private List<String> getPlayerNames(String partial) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(partial.toLowerCase()))
                .collect(Collectors.toList());
    }

    private List<String> getSlotNumbers(String partial) {
        List<String> slots = new ArrayList<>();
        for (int i = 1; i <= config.getStorageSlots(); i++) {
            String slot = String.valueOf(i);
            if (slot.startsWith(partial)) {
                slots.add(slot);
            }
        }
        return slots;
    }
} 