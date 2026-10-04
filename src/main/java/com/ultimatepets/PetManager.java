package com.ultimatepets;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class PetManager {

    private static final String STAND_TAG = "ultimatepets";
    private static final DecimalFormat DF = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));

    private final UltimatePets plugin;
    private final Map<String, PetType> types = new LinkedHashMap<>();
    private final Map<UUID, List<PetData>> data = new HashMap<>();
    private final Map<UUID, ArmorStand> stands = new HashMap<>();
    private final Map<String, ItemStack> headCache = new HashMap<>();
    private final File dataFile;
    private BukkitTask followTask;
    private double tick = 0;

    public PetManager(UltimatePets plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    // ---------- Carga y guardado ----------

    public void loadTypes() {
        types.clear();
        headCache.clear();
        ConfigurationSection sec = plugin.getPetsConfig().getConfigurationSection("pets");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s != null) types.put(id.toLowerCase(), new PetType(id.toLowerCase(), s));
        }
    }

    public void loadData() {
        data.clear();
        if (!dataFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection players = y.getConfigurationSection("players");
        if (players == null) return;
        for (String uuidStr : players.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            List<PetData> list = new ArrayList<>();
            ConfigurationSection ps = players.getConfigurationSection(uuidStr + ".pets");
            if (ps != null) {
                for (String pid : ps.getKeys(false)) {
                    ConfigurationSection s = ps.getConfigurationSection(pid);
                    if (s == null) continue;
                    try {
                        PetData d = new PetData(UUID.fromString(pid), s.getString("type", ""), s.getInt("level", 1));
                        d.setActive(s.getBoolean("active", false));
                        list.add(d);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            data.put(uuid, list);
        }
    }

    public void saveData() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, List<PetData>> e : data.entrySet()) {
            for (PetData d : e.getValue()) {
                String base = "players." + e.getKey() + ".pets." + d.getId();
                y.set(base + ".type", d.getType());
                y.set(base + ".level", d.getLevel());
                y.set(base + ".active", d.isActive());
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar data.yml: " + ex.getMessage());
        }
    }

    // ---------- Acceso a datos ----------

    public Map<String, PetType> getTypes() {
        return types;
    }

    public PetType getType(String id) {
        return id == null ? null : types.get(id.toLowerCase());
    }

    public List<PetData> getPets(UUID u) {
        return data.computeIfAbsent(u, k -> new ArrayList<>());
    }

    /** Pets cuyo tipo todavía existe en pets.yml. */
    public List<PetData> visiblePets(UUID u) {
        List<PetData> out = new ArrayList<>();
        for (PetData d : getPets(u)) {
            if (types.containsKey(d.getType())) out.add(d);
        }
        return out;
    }

    public List<PetData> getActive(UUID u) {
        List<PetData> out = new ArrayList<>();
        List<PetData> list = data.get(u);
        if (list == null) return out;
        for (PetData d : list) {
            if (d.isActive() && types.containsKey(d.getType())) out.add(d);
        }
        return out;
    }

    public int allowedSlots(Player p) {
        int base = Math.min(5, Math.max(1, plugin.getConfig().getInt("settings.default-slots", 1)));
        for (int n = 5; n > base; n--) {
            if (p.hasPermission("ultimatepets.slots." + n)) return n;
        }
        return base;
    }

    public boolean addPet(UUID u, String typeId, int level) {
        List<PetData> list = getPets(u);
        if (list.size() >= plugin.getConfig().getInt("settings.max-stored", 50)) return false;
        list.add(new PetData(UUID.randomUUID(), typeId.toLowerCase(), level));
        saveData();
        return true;
    }

    public void removePet(Player p, PetData d) {
        despawn(d);
        getPets(p.getUniqueId()).remove(d);
        saveData();
    }

    public boolean activate(Player p, PetData d) {
        if (d.isActive()) return true;
        if (getActive(p.getUniqueId()).size() >= allowedSlots(p)) return false;
        d.setActive(true);
        spawn(p, d);
        saveData();
        return true;
    }

    public void deactivate(Player p, PetData d) {
        d.setActive(false);
        despawn(d);
        saveData();
    }

    // ---------- Boosts (API pública) ----------

    /** Multiplicador total de las pets activas para un tipo de boost. 1.0 = sin boost. */
    public double getMultiplier(Player p, String boostType) {
        List<PetData> list = data.get(p.getUniqueId());
        if (list == null) return 1.0;
        double total = 1.0;
        for (PetData d : list) {
            if (!d.isActive()) continue;
            PetType t = types.get(d.getType());
            if (t != null && t.getBoostType().equalsIgnoreCase(boostType)) {
                total += Math.max(0.0, t.multiplier(d.getLevel()) - 1.0);
            }
        }
        return total;
    }

    // ---------- Mensajes ----------

    public void send(CommandSender to, String key, String... kv) {
        String msg = plugin.getConfig().getString("messages." + key, "");
        if (msg.isEmpty()) return;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            msg = msg.replace(kv[i], kv[i + 1]);
        }
        to.sendMessage(color(plugin.getConfig().getString("messages.prefix", "") + msg));
    }

    // ---------- Items ----------

    public String displayName(PetType t, PetData d) {
        return color(plugin.getConfig().getString("settings.name-format", "{name} &7({level})")
                .replace("{name}", t.getName())
                .replace("{level}", String.valueOf(d.getLevel())));
    }

    public String boostText(PetType t, PetData d) {
        return "x" + DF.format(t.multiplier(d.getLevel())) + " " + t.getBoostLabel();
    }

    public ItemStack petItem(PetType t, PetData d, String statusKey) {
        ItemStack it = buildHead(t).clone();
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(displayName(t, d));
        List<String> lore = new ArrayList<>();
        String status = plugin.getConfig().getString(statusKey, "");
        for (String line : plugin.getConfig().getStringList("pet-lore")) {
            lore.add(color(line
                    .replace("{rarity}", t.getRarity())
                    .replace("{level}", String.valueOf(d.getLevel()))
                    .replace("{max}", String.valueOf(t.getMaxLevel()))
                    .replace("{boost}", boostText(t, d))
                    .replace("{status}", status)));
        }
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack buildHead(PetType t) {
        ItemStack cached = headCache.get(t.getId());
        if (cached != null) return cached;

        ItemStack item = null;
        if (!t.getTexture().isEmpty()) {
            try {
                String json = new String(Base64.getDecoder().decode(t.getTexture()), StandardCharsets.UTF_8);
                int s = json.indexOf("\"url\":\"");
                if (s != -1) {
                    s += 7;
                    String url = json.substring(s, json.indexOf('"', s));
                    PlayerProfile profile = Bukkit.createPlayerProfile(
                            UUID.nameUUIDFromBytes(t.getId().getBytes(StandardCharsets.UTF_8)), "pet");
                    PlayerTextures tex = profile.getTextures();
                    tex.setSkin(new URL(url));
                    profile.setTextures(tex);
                    item = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta meta = (SkullMeta) item.getItemMeta();
                    meta.setOwnerProfile(profile);
                    item.setItemMeta(meta);
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Textura inválida en la pet '" + t.getId() + "': " + ex.getMessage());
                item = null;
            }
        }
        if (item == null) {
            Material m = Material.matchMaterial(t.getMaterial());
            item = new ItemStack(m != null ? m : Material.PLAYER_HEAD);
        }
        headCache.put(t.getId(), item);
        return item;
    }

    // ---------- Pets en el mundo ----------

    public void spawnActive(Player p) {
        for (PetData d : getActive(p.getUniqueId())) {
            spawn(p, d);
        }
    }

    private void spawn(Player p, PetData d) {
        despawn(d);
        PetType t = types.get(d.getType());
        if (t == null || !p.isOnline() || p.isDead()) return;

        Location loc = p.getLocation();
        ItemStack head = buildHead(t).clone();
        String name = displayName(t, d);

        ArmorStand as = loc.getWorld().spawn(loc, ArmorStand.class, s -> {
            s.setInvisible(true);
            s.setSmall(true);
            s.setGravity(false);
            s.setMarker(true);
            s.setInvulnerable(true);
            s.setBasePlate(false);
            s.setArms(false);
            s.setCollidable(false);
            s.setPersistent(false);
            s.setCustomName(name);
            s.setCustomNameVisible(true);
            s.addScoreboardTag(STAND_TAG);
            s.getEquipment().setHelmet(head);
        });
        stands.put(d.getId(), as);
    }

    private void despawn(PetData d) {
        ArmorStand as = stands.remove(d.getId());
        if (as != null && as.isValid()) as.remove();
    }

    public void despawnAll(Player p) {
        List<PetData> list = data.get(p.getUniqueId());
        if (list == null) return;
        for (PetData d : list) despawn(d);
    }

    public void despawnEverything() {
        for (ArmorStand as : stands.values()) {
            if (as != null && as.isValid()) as.remove();
        }
        stands.clear();
    }

    public void cleanupStrayStands() {
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            for (ArmorStand as : w.getEntitiesByClass(ArmorStand.class)) {
                if (as.getScoreboardTags().contains(STAND_TAG)) as.remove();
            }
        }
    }

    // ---------- Seguimiento ----------

    public void startTask() {
        stopTask();
        final int interval = Math.max(1, plugin.getConfig().getInt("settings.follow-interval-ticks", 2));
        followTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick += interval;
            for (Player p : Bukkit.getOnlinePlayers()) {
                List<PetData> active = getActive(p.getUniqueId());
                if (active.isEmpty()) continue;
                if (p.isDead()) {
                    for (PetData d : active) despawn(d);
                    continue;
                }
                int i = 0;
                for (PetData d : active) {
                    ArmorStand as = stands.get(d.getId());
                    if (as == null || !as.isValid()) {
                        spawn(p, d);
                        as = stands.get(d.getId());
                    }
                    if (as != null) as.teleport(target(p, i));
                    i++;
                }
            }
        }, 1L, interval);
    }

    public void stopTask() {
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }
    }

    private Location target(Player p, int index) {
        Location base = p.getLocation();
        Vector dir = base.getDirection().setY(0);
        if (dir.lengthSquared() < 0.0001) dir = new Vector(0, 0, 1);
        dir.normalize();
        Vector right = new Vector(-dir.getZ(), 0, dir.getX());

        double side = (index % 2 == 0) ? 1.0 : -1.0;
        double back = (index / 2) * 0.7;
        double bob = Math.sin((tick + index * 7) / 6.0) * 0.1;

        Location t = base.clone()
                .add(right.multiply(side * 0.9))
                .add(dir.clone().multiply(-back))
                .add(0, 0.9 + bob, 0);
        t.setYaw(base.getYaw());
        t.setPitch(0);
        return t;
    }
    }
