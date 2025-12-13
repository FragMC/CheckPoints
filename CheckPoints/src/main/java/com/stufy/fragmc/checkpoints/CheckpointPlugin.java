package com.stufy.fragmc.checkpoints;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class CheckpointPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private final Map<UUID, Location> checkpoints = new HashMap<>();

    @Override
    public void onEnable() {
        this.getCommand("cp").setExecutor(this);
        this.getCommand("cp").setTabCompleter(this);
        getLogger().info("Checkpoint Plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Checkpoint Plugin has been disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("cp")) {
            return false;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Checkpoint Commands:");
            sender.sendMessage(ChatColor.GRAY + "/cp set - Set your checkpoint");
            sender.sendMessage(ChatColor.GRAY + "/cp go - Teleport to your checkpoint");
            sender.sendMessage(ChatColor.GRAY + "/cp tp <player|@a[tag=...]> - Teleport player(s) to their checkpoint");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "set":
                return handleSet(sender);

            case "go":
                return handleGo(sender);

            case "tp":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /cp tp <player|@a[tag=...]>");
                    return true;
                }
                return handleTeleport(sender, args[1]);

            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /cp for help.");
                return true;
        }
    }

    private boolean handleSet(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can set checkpoints!");
            return true;
        }

        Player player = (Player) sender;
        Location loc = player.getLocation();
        checkpoints.put(player.getUniqueId(), loc);

        player.sendMessage(ChatColor.GREEN + "Checkpoint set at: " +
                ChatColor.GRAY + String.format("X: %.1f, Y: %.1f, Z: %.1f",
                loc.getX(), loc.getY(), loc.getZ()));
        return true;
    }

    private boolean handleGo(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can teleport to checkpoints!");
            return true;
        }

        Player player = (Player) sender;
        Location checkpoint = checkpoints.get(player.getUniqueId());

        if (checkpoint == null) {
            player.sendMessage(ChatColor.RED + "You don't have a checkpoint set! Use /cp set first.");
            return true;
        }

        player.teleport(checkpoint);
        player.sendMessage(ChatColor.GREEN + "Teleported to your checkpoint!");
        return true;
    }

    private boolean handleTeleport(CommandSender sender, String target) {
        if (!sender.hasPermission("checkpoint.tp")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        // Handle selector syntax @a[tag=...]
        if (target.startsWith("@a")) {
            return handleSelectorTeleport(sender, target);
        }

        // Handle single player teleport
        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Player '" + target + "' not found!");
            return true;
        }

        return teleportPlayerToCheckpoint(sender, targetPlayer);
    }

    private boolean handleSelectorTeleport(CommandSender sender, String selector) {
        // Parse tag from selector like @a[tag=mytag]
        String tag = null;
        if (selector.contains("[tag=") && selector.contains("]")) {
            int start = selector.indexOf("tag=") + 4;
            int end = selector.indexOf("]", start);
            tag = selector.substring(start, end);
        }

        Collection<? extends Player> players;
        if (tag != null) {
            // Filter players by scoreboard tag
            final String finalTag = tag;
            players = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.getScoreboardTags().contains(finalTag))
                    .toList();
        } else {
            players = Bukkit.getOnlinePlayers();
        }

        if (players.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No players match the selector!");
            return true;
        }

        int successCount = 0;
        int failCount = 0;

        for (Player player : players) {
            Location checkpoint = checkpoints.get(player.getUniqueId());
            if (checkpoint != null) {
                player.teleport(checkpoint);
                player.sendMessage(ChatColor.GREEN + "You were teleported to your checkpoint!");
                successCount++;
            } else {
                failCount++;
            }
        }

        sender.sendMessage(ChatColor.GREEN + "Teleported " + successCount + " player(s) to their checkpoints.");
        if (failCount > 0) {
            sender.sendMessage(ChatColor.YELLOW + "" + failCount + " player(s) had no checkpoint set.");
        }

        return true;
    }

    private boolean teleportPlayerToCheckpoint(CommandSender sender, Player target) {
        Location checkpoint = checkpoints.get(target.getUniqueId());

        if (checkpoint == null) {
            sender.sendMessage(ChatColor.RED + target.getName() + " doesn't have a checkpoint set!");
            return true;
        }

        target.teleport(checkpoint);
        target.sendMessage(ChatColor.GREEN + "You were teleported to your checkpoint!");
        sender.sendMessage(ChatColor.GREEN + "Teleported " + target.getName() + " to their checkpoint!");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("set");
            completions.add("go");
            if (sender.hasPermission("checkpoint.tp")) {
                completions.add("tp");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("tp")) {
            // Add online player names
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
            // Add selector
            completions.add("@a");
        }

        return completions;
    }
}