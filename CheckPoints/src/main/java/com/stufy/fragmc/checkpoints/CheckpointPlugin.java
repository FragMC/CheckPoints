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
    private final Map<UUID, Long> lastSetTime = new HashMap<>();
    private final Map<UUID, Integer> messageCount = new HashMap<>();
    private final Map<UUID, Long> rateLimitWindowStart = new HashMap<>();
    private static final long SPAM_THRESHOLD_MS = 100; // Prevent spam within 100ms
    private static final long RATE_LIMIT_WINDOW_MS = 1000; // 1 second window for rate limiting

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
            sender.sendMessage(ChatColor.GRAY + "/cp set --nocoords - Set checkpoint without showing coordinates");
            sender.sendMessage(ChatColor.GRAY + "/cp set --no-message - Set checkpoint silently");
            sender.sendMessage(ChatColor.GRAY + "/cp set --rate-limit=<num> - Limit messages to <num> per second");
            sender.sendMessage(ChatColor.GRAY + "/cp go [--no-message] [--rate-limit=<num>] - Teleport to your checkpoint");
            sender.sendMessage(ChatColor.GRAY + "/cp tp <player|@a[tag=...]> [--no-message] [--rate-limit=<num>] - Teleport player(s) to their checkpoint");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "set":
                // Parse flags from args
                boolean noCoords = hasFlag(args, "--nocoords");
                boolean noMessage = hasFlag(args, "--no-message");
                int rateLimit = getRateLimitValue(args);
                String[] filteredArgs = removeFlags(args);

                // /cp set [flags]
                if (filteredArgs.length == 1) {
                    return handleSet(sender, noCoords, noMessage, rateLimit);
                }

                // /cp set <x> <y> <z> [flags]
                if (filteredArgs.length == 4 && isNumber(filteredArgs[1])) {
                    return handleSetWithCoordinates(sender, filteredArgs, noCoords, noMessage, rateLimit);
                }

                // /cp set <player|@a> [x y z] [flags]
                return handleSetTarget(sender, filteredArgs[1], filteredArgs, noCoords, noMessage, rateLimit);

            case "go":
                boolean goNoMessage = hasFlag(args, "--no-message");
                int goRateLimit = getRateLimitValue(args);
                return handleGo(sender, goNoMessage, goRateLimit);

            case "tp":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /cp tp <player|@a[tag=...]>");
                    return true;
                }
                boolean tpNoMessage = hasFlag(args, "--no-message");
                int tpRateLimit = getRateLimitValue(args);
                String[] tpFilteredArgs = removeFlags(args);
                return handleTeleport(sender, tpFilteredArgs.length >= 2 ? tpFilteredArgs[1] : args[1], tpNoMessage, tpRateLimit);

            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /cp for help.");
                return true;
        }
    }

    private boolean handleSet(CommandSender sender, boolean noCoords, boolean noMessage, int rateLimit) {
        Player player = getPlayerFromSender(sender);

        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Only players can set checkpoints! Use: /execute as <player> run cp set");
            return true;
        }

        // Check for spam prevention
        if (isSpamming(player)) {
            return true;
        }

        // Check rate limit
        if (!checkRateLimit(player, rateLimit)) {
            return true;
        }

        Location loc = player.getLocation();
        checkpoints.put(player.getUniqueId(), loc);
        updateLastSetTime(player);

        if (!noMessage) {
            if (noCoords) {
                player.sendMessage(ChatColor.GREEN + "Checkpoint set!");
            } else {
                player.sendMessage(ChatColor.GREEN + "Checkpoint set at: " +
                        ChatColor.GRAY + String.format("X: %.1f, Y: %.1f, Z: %.1f",
                        loc.getX(), loc.getY(), loc.getZ()));
            }
        }
        return true;
    }

    private boolean handleSetWithCoordinates(CommandSender sender, String[] args, boolean noCoords, boolean noMessage, int rateLimit) {
        Player player = getPlayerFromSender(sender);

        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Only players can set checkpoints! Use: /execute as <player> run cp set <x> <y> <z>");
            return true;
        }

        // Check for spam prevention
        if (isSpamming(player)) {
            return true;
        }

        // Check rate limit
        if (!checkRateLimit(player, rateLimit)) {
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
            updateLastSetTime(player);

            if (!noMessage) {
                if (noCoords) {
                    player.sendMessage(ChatColor.GREEN + "Checkpoint set!");
                } else {
                    player.sendMessage(ChatColor.GREEN + "Checkpoint set at: " +
                            ChatColor.GRAY + String.format("X: %.1f, Y: %.1f, Z: %.1f", x, y, z));
                }
            }
            return true;
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid coordinates! Usage: /cp set <x> <y> <z>");
            return true;
        }
    }

    private boolean handleGo(CommandSender sender, boolean noMessage, int rateLimit) {
        Player player = getPlayerFromSender(sender);

        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Only players can teleport to checkpoints! Use: /execute as <player> run cp go");
            return true;
        }

        // Check rate limit
        if (!checkRateLimit(player, rateLimit)) {
            return true;
        }

        Location checkpoint = checkpoints.get(player.getUniqueId());

        if (checkpoint == null) {
            if (!noMessage) {
                player.sendMessage(ChatColor.RED + "You don't have a checkpoint set! Use /cp set first.");
            }
            return true;
        }

        player.teleport(checkpoint);
        if (!noMessage) {
            player.sendMessage(ChatColor.GREEN + "Teleported to your checkpoint!");
        }
        return true;
    }

    private boolean handleTeleport(CommandSender sender, String target, boolean noMessage, int rateLimit) {
        if (!sender.hasPermission("checkpoint.tp")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        // Handle selector syntax @a[tag=...]
        if (target.startsWith("@a")) {
            return handleSelectorTeleport(sender, target, noMessage, rateLimit);
        }

        // Handle single player teleport
        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer == null) {
            if (!noMessage) {
                sender.sendMessage(ChatColor.RED + "Player '" + target + "' not found!");
            }
            return true;
        }

        return teleportPlayerToCheckpoint(sender, targetPlayer, noMessage, rateLimit);
    }

    private boolean isNumber(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean handleSelectorTeleport(CommandSender sender, String selector, boolean noMessage, int rateLimit) {
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
            if (!noMessage) {
                sender.sendMessage(ChatColor.RED + "No players match the selector!");
            }
            return true;
        }

        int successCount = 0;
        int failCount = 0;

        for (Player player : players) {
            // Check rate limit for each player
            if (!checkRateLimit(player, rateLimit)) {
                continue;
            }

            Location checkpoint = checkpoints.get(player.getUniqueId());
            if (checkpoint != null) {
                player.teleport(checkpoint);
                if (!noMessage) {
                    player.sendMessage(ChatColor.GREEN + "You were teleported to your checkpoint!");
                }
                successCount++;
            } else {
                failCount++;
            }
        }

        if (!noMessage) {
            sender.sendMessage(ChatColor.GREEN + "Teleported " + successCount + " player(s) to their checkpoints.");
            if (failCount > 0) {
                sender.sendMessage(ChatColor.YELLOW + "" + failCount + " player(s) had no checkpoint set.");
            }
        }

        return true;
    }

    private boolean teleportPlayerToCheckpoint(CommandSender sender, Player target, boolean noMessage, int rateLimit) {
        // Check rate limit
        if (!checkRateLimit(target, rateLimit)) {
            return true;
        }

        Location checkpoint = checkpoints.get(target.getUniqueId());

        if (checkpoint == null) {
            if (!noMessage) {
                sender.sendMessage(ChatColor.RED + target.getName() + " doesn't have a checkpoint set!");
            }
            return true;
        }

        target.teleport(checkpoint);
        if (!noMessage) {
            target.sendMessage(ChatColor.GREEN + "You were teleported to your checkpoint!");
            sender.sendMessage(ChatColor.GREEN + "Teleported " + target.getName() + " to their checkpoint!");
        }
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
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("set")) {
            // Add flags for /cp set
            if (!hasFlag(args, "--nocoords")) {
                completions.add("--nocoords");
            }
            if (!hasFlag(args, "--no-message")) {
                completions.add("--no-message");
            }
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("go")) {
            // Add flag for /cp go
            if (!hasFlag(args, "--no-message")) {
                completions.add("--no-message");
            }
        } else if (args[0].equalsIgnoreCase("tp")) {
            if (args.length == 2) {
                // Add online player names for /cp tp
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
                completions.add("@a");
            } else if (args.length >= 3) {
                // Add flag for /cp tp
                if (!hasFlag(args, "--no-message")) {
                    completions.add("--no-message");
                }
            }
        }

        return completions;
    }

    private boolean handleSetTarget(CommandSender sender, String target, String[] args, boolean noCoords, boolean noMessage, int rateLimit) {
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
            // Check spam for each player
            if (isSpamming(player)) {
                continue;
            }

            // Check rate limit for each player
            if (!checkRateLimit(player, rateLimit)) {
                continue;
            }

            Location setLoc = loc != null ? loc.clone() : player.getLocation();
            setLoc.setYaw(player.getLocation().getYaw());
            setLoc.setPitch(player.getLocation().getPitch());

            checkpoints.put(player.getUniqueId(), setLoc);
            updateLastSetTime(player);

            if (!noMessage) {
                if (noCoords) {
                    player.sendMessage(ChatColor.GREEN + "Checkpoint set!");
                } else {
                    player.sendMessage(ChatColor.GREEN + "Checkpoint set!");
                }
            }
            count++;
        }

        if (!noMessage) {
            sender.sendMessage(ChatColor.GREEN + "Set checkpoint for " + count + " player(s).");
        }
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

        // Try to extract the player from proxied command senders (from /execute, /sudo, etc.)
        try {
            // Use reflection to get the callee from proxied senders
            if (sender.getClass().getSimpleName().contains("ProxiedCommandSender") ||
                    sender.getClass().getSimpleName().contains("ProxiedNativeCommandSender")) {

                java.lang.reflect.Method getCallee = sender.getClass().getMethod("getCallee");
                Object callee = getCallee.invoke(sender);

                if (callee instanceof Player) {
                    return (Player) callee;
                }
            }
        } catch (Exception e) {
            // Reflection failed, return null
        }

        return null;
    }

    private boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equalsIgnoreCase(flag)) {
                return true;
            }
        }
        return false;
    }

    private String[] removeFlags(String[] args) {
        List<String> filtered = new ArrayList<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                filtered.add(arg);
            }
        }
        return filtered.toArray(new String[0]);
    }

    private int getRateLimitValue(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--rate-limit=")) {
                try {
                    String value = arg.substring("--rate-limit=".length());
                    int limit = Integer.parseInt(value);
                    return limit > 0 ? limit : -1;
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1; // No rate limit
    }

    private boolean checkRateLimit(Player player, int maxMessagesPerSecond) {
        if (maxMessagesPerSecond <= 0) {
            return true; // No rate limit
        }

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        Long windowStart = rateLimitWindowStart.get(playerId);
        Integer currentCount = messageCount.get(playerId);

        // Start new window if needed
        if (windowStart == null || (currentTime - windowStart) >= RATE_LIMIT_WINDOW_MS) {
            rateLimitWindowStart.put(playerId, currentTime);
            messageCount.put(playerId, 1);
            return true;
        }

        // Check if within limit
        if (currentCount == null) {
            currentCount = 0;
        }

        if (currentCount >= maxMessagesPerSecond) {
            return false; // Rate limit exceeded
        }

        // Increment counter
        messageCount.put(playerId, currentCount + 1);
        return true;
    }

    private boolean isSpamming(Player player) {
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastSetTime.get(player.getUniqueId());

        if (lastTime != null && (currentTime - lastTime) < SPAM_THRESHOLD_MS) {
            return true;
        }

        return false;
    }

    private void updateLastSetTime(Player player) {
        lastSetTime.put(player.getUniqueId(), System.currentTimeMillis());
    }
}