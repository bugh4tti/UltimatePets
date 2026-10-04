package com.ultimatepets;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PetsCommand implements CommandExecutor, TabCompleter {

    private final UltimatePets plugin;
    private final PetManager manager;
    private final PetsMenu menu;

    public PetsCommand(UltimatePets plugin, PetManager manager, PetsMenu menu) {
        this.plugin = plugin;
        this.manager = manager;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0) {
            if (!(s instanceof Player p)) {
                s.sendMessage("Solo jugadores.");
                return true;
            }
            if (!p.hasPermission("ultimatepets.use")) {
                manager.send(p, "no-permission");
                return true;
            }
            menu.open(p);
            return true;
        }

        if (!s.hasPermission("ultimatepets.admin")) {
            manager.send(s, "no-permission");
            return true;
        }

        switch (a[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                manager.send(s, "reload");
            }
            case "list" -> manager.send(s, "list", "{list}", String.join(", ", manager.getTypes().keySet()));
            case "give" -> give(s, a);
            default -> manager.send(s, "usage");
        }
        return true;
    }

    private void give(CommandSender s, String[] a) {
        if (a.length < 3) {
            manager.send(s, "usage-give");
            return;
        }
        Player target = Bukkit.getPlayerExact(a[1]);
        if (target == null) {
            manager.send(s, "player-offline");
            return;
        }
        PetType type = manager.getType(a[2]);
        if (type == null) {
            manager.send(s, "unknown-pet", "{list}", String.join(", ", manager.getTypes().keySet()));
            return;
        }
        int level = 1;
        if (a.length > 3) {
            try {
                level = Integer.parseInt(a[3]);
            } catch (NumberFormatException ex) {
                manager.send(s, "usage-give");
                return;
            }
        }
        level = Math.max(1, Math.min(level, type.getMaxLevel()));

        if (!manager.addPet(target.getUniqueId(), type.getId(), level)) {
            manager.send(s, "storage-full");
            return;
        }
        manager.send(s, "given", "{pet}", type.getName(), "{level}", String.valueOf(level), "{player}", target.getName());
        manager.send(target, "received", "{pet}", type.getName());
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission("ultimatepets.admin")) return out;

        if (a.length == 1) {
            out.addAll(List.of("give", "list", "reload"));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("give")) {
            out.addAll(manager.getTypes().keySet());
        } else if (a.length == 4 && a[0].equalsIgnoreCase("give")) {
            out.add("1");
        }

        String cur = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(cur));
        return out;
    }
        }
