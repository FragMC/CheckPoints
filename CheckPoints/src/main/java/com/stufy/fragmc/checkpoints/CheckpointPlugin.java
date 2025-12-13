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
            sender.sendMessage(ChatColor.GRAY + "/cp set - Set your checkpoint at your location");
            sender.sendMessage(ChatColor.GRAY + "/cp set <x> <y> <z> - Set your checkpoint at specific coordinates");
            sender.sendMessage(ChatColor.GRAY + "/cp go - Teleport to your checkpoint");
            sender.sendMessage(ChatColor.GRAY + "/cp tp <player|@a[tag=...]> - Teleport player(s) to their checkpoint");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "set":
                // /cp set
                if (args.length == 1) {
                    return handleSet(sender);
                }

                // /cp set <x> <y> <z>
                if (args.length == 4 && isNumber(args[1])) {
                    return handleSetWithCoordinates(sender, args);
                }

                // /cp set <player|@a> [x y z]
                return handleSetTarget(sender, args[1], args);



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
        Player player = getPlayerFromSender(sender);

        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Only players can set checkpoints! Use: /execute as <player> run cp set");
            return true;
        }

        Location loc = player.getLocation();
        checkpoints.put(player.getUniqueId(), loc);

        player.sendMessage(ChatColor.GREEN + "Checkpoint set at: " +
                ChatColor.GRAY + String.format("X: %.1f, Y: %.1f, Z: %.1f",
                loc.getX(), loc.getY(), loc.getZ()));
        return true;
    }

    private boolean handleSetWithCoordinates(CommandSender sender, String[] args) {
        Player player = getPlayerFromSender(sender);

        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Only players can set checkpoints! Use: /execute as <player> run cp set <x> <y> <z>");
            return true;
        }

        try {
            double x = Double.parseDouble(args[1]);
            double y = Double.parseDouble(args[2]);
            double z = Double.parseDouble(args[3]);

            Location loc = new Location(player.getWorld(), x, y, z);
            loc.setYaw(player.getLocation().getYaw());
            loc.setPitch(player.getLocation().getPitch());

            checkpoints.put(player.getUniqueId(), loc);

            player.sendMessage(ChatColor.GREEN + "Checkpoint set at: " +
                    ChatColor.GRAY + String.format("X: %.1f, Y: %.1f, Z: %.1f", x, y, z));
            return true;
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid coordinates! Usage: /cp set <x> <y> <z>");
            return true;
        }
    }

    private boolean handleGo(CommandSender sender) {
        Player player = getPlayerFromSender(sender);

        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Only players can teleport to checkpoints! Use: /execute as <player> run cp go");
            return true;
        }

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

    private boolean isNumber(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
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
            // Add online player names for /cp tp
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
            completions.add("@a");
        }

        return completions;
    }

    private boolean handleSetTarget(CommandSender sender, String target, String[] args) {
        Collection<Player> players = resolveTargets(target);

        if (players.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No players matched!");
            return true;
        }

        Location loc;

        if (args.length >= 5) {
            try {
                double x = Double.parseDouble(args[2]);
                double y = Double.parseDouble(args[3]);
                double z = Double.parseDouble(args[4]);
                loc = new Location(players.iterator().next().getWorld(), x, y, z);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid coordinates!");
                return true;
            }
        } else {
            loc = null; // use player location
        }

        int count = 0;

        for (Player player : players) {
            Location setLoc = loc != null ? loc.clone() : player.getLocation();
            setLoc.setYaw(player.getLocation().getYaw());
            setLoc.setPitch(player.getLocation().getPitch());

            checkpoints.put(player.getUniqueId(), setLoc);
            player.sendMessage(ChatColor.GREEN + "Checkpoint set!");
            count++;
        }

        sender.sendMessage(ChatColor.GREEN + "Set checkpoint for " + count + " player(s).");
        return true;
    }

    private Collection<Player> resolveTargets(String target) {
        List<Player> result = new ArrayList<>();

        if (target.startsWith("@")) {

            if (target.equalsIgnoreCase("@a")) {
                result.addAll(Bukkit.getOnlinePlayers());
                return result;
            }

            if (target.startsWith("@a[tag=") && target.endsWith("]")) {
                String tag = target.substring(7, target.length() - 1);

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getScoreboardTags().contains(tag)) {
                        result.add(p);
                    }
                }
                return result;
            }

            return result;
        }

        Player player = Bukkit.getPlayer(target);
        if (player != null) {
            result.add(player);
        }

        return result;
    }



    private Player getPlayerFromSender(CommandSender sender) {
        // Check if sender is a player directly
        if (sender instanceof Player) {
            return (Player) sender;
        }

        // Check if it's a proxied command sender (from /execute)

        return null;
    }
}