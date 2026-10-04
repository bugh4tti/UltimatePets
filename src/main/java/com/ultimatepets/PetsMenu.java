package com.ultimatepets;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PetsMenu implements Listener {

    private static final int[] EQUIP = {2, 3, 4, 5, 6};
    // Slots reservados para Mejoras (0), Fusión (8) y Reciclar (53): se agregan en la parte 2 y 3
    private static final int[] FILLER = {0, 1, 7, 8, 46, 47, 51, 52, 53};
    private static final int PET_START = 9;
    private static final int PET_COUNT = 35;
    private static final int DELETE = 45;
    private static final int PREV = 48;
    private static final int INFO = 49;
    private static final int NEXT = 50;

    private final UltimatePets plugin;
    private final PetManager manager;

    public PetsMenu(UltimatePets plugin, PetManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public static class MenuHolder implements InventoryHolder {
        int page = 0;
        boolean deleteMode = false;
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public void open(Player p) {
        MenuHolder h = new MenuHolder();
        h.inv = Bukkit.createInventory(h, 54, PetManager.color(plugin.getConfig().getString("menu.title", "Menú de Mascotas")));
        render(p, h);
        p.openInventory(h.inv);
    }

    private void render(Player p, MenuHolder h) {
        Inventory inv = h.inv;
        inv.clear();
        UUID u = p.getUniqueId();

        List<PetData> pets = manager.visiblePets(u);
        List<PetData> active = manager.getActive(u);
        int allowed = manager.allowedSlots(p);
        int maxStored = plugin.getConfig().getInt("settings.max-stored", 50);

        int maxPage = Math.max(0, (pets.size() - 1) / PET_COUNT);
        if (h.page > maxPage) h.page = maxPage;

        ItemStack filler = build("menu.filler");
        for (int s : FILLER) inv.setItem(s, filler);

        // Slots equipados
        for (int i = 0; i < EQUIP.length; i++) {
            ItemStack it;
            if (i < active.size()) {
                PetData d = active.get(i);
                it = manager.petItem(manager.getType(d.getType()), d, "status-equipped");
            } else if (i < allowed) {
                it = build("menu.slot-unlocked");
            } else {
                it = build("menu.slot-locked");
            }
            inv.setItem(EQUIP[i], it);
        }

        // Pets guardadas
        int start = h.page * PET_COUNT;
        for (int s = 0; s < PET_COUNT; s++) {
            int idx = start + s;
            if (idx >= pets.size()) break;
            PetData d = pets.get(idx);
            String status = h.deleteMode ? "status-delete" : (d.isActive() ? "status-active" : "status-inactive");
            inv.setItem(PET_START + s, manager.petItem(manager.getType(d.getType()), d, status));
        }

        // Botones
        inv.setItem(DELETE, build(h.deleteMode ? "menu.delete-button-active" : "menu.delete-button"));
        inv.setItem(INFO, build("menu.info-button",
                "{equipped}", String.valueOf(active.size()),
                "{slots}", String.valueOf(allowed),
                "{stored}", String.valueOf(pets.size()),
                "{max}", String.valueOf(maxStored)));
        if (h.page > 0) inv.setItem(PREV, build("menu.prev-button"));
        if (h.page < maxPage) inv.setItem(NEXT, build("menu.next-button"));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuHolder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;

        int slot = e.getRawSlot();
        UUID u = p.getUniqueId();

        // Slots equipados
        for (int i = 0; i < EQUIP.length; i++) {
            if (EQUIP[i] != slot) continue;
            List<PetData> active = manager.getActive(u);
            if (i < active.size()) {
                PetData d = active.get(i);
                manager.deactivate(p, d);
                manager.send(p, "deactivated", "{pet}", manager.getType(d.getType()).getName());
            } else if (i >= manager.allowedSlots(p)) {
                manager.send(p, "slot-locked");
            }
            render(p, h);
            return;
        }

        if (slot == DELETE) {
            h.deleteMode = !h.deleteMode;
            render(p, h);
            return;
        }
        if (slot == PREV && h.page > 0) {
            h.page--;
            render(p, h);
            return;
        }
        if (slot == NEXT) {
            h.page++;
            render(p, h);
            return;
        }

        // Pets guardadas
        if (slot >= PET_START && slot < PET_START + PET_COUNT) {
            List<PetData> pets = manager.visiblePets(u);
            int idx = h.page * PET_COUNT + (slot - PET_START);
            if (idx >= pets.size()) return;
            PetData d = pets.get(idx);
            String petName = manager.getType(d.getType()).getName();

            if (h.deleteMode) {
                manager.removePet(p, d);
                manager.send(p, "deleted", "{pet}", petName);
            } else if (d.isActive()) {
                manager.deactivate(p, d);
                manager.send(p, "deactivated", "{pet}", petName);
            } else if (manager.activate(p, d)) {
                manager.send(p, "activated", "{pet}", petName);
            } else {
                manager.send(p, "no-slots");
            }
            render(p, h);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MenuHolder) e.setCancelled(true);
    }

    // ---------- Utilidades ----------

    private ItemStack build(String path, String... kv) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection(path);
        Material mat = Material.matchMaterial(s.getString("material", "STONE"));
        if (mat == null) mat = Material.STONE;
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(rep(s.getString("name", " "), kv));
        List<String> lore = new ArrayList<>();
        for (String line : s.getStringList("lore")) lore.add(rep(line, kv));
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    private String rep(String s, String... kv) {
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace(kv[i], kv[i + 1]);
        return PetManager.color(s);
    }
                                }
