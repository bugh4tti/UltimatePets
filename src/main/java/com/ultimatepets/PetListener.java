package com.ultimatepets;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PetListener implements Listener {

    private final UltimatePets plugin;
    private final PetManager manager;

    public PetListener(UltimatePets plugin, PetManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) manager.spawnActive(p);
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        manager.despawnAll(e.getPlayer());
        manager.saveData();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExp(PlayerExpChangeEvent e) {
        int amount = e.getAmount();
        if (amount <= 0) return;
        double mult = manager.getMultiplier(e.getPlayer(), "EXP");
        if (mult > 1.0) {
            e.setAmount((int) Math.round(amount * mult));
        }
    }
}
