package com.ultimatepets;

import org.bukkit.configuration.ConfigurationSection;

public class PetType {

    private final String id;
    private final String name;
    private final String rarity;
    private final String material;
    private final String texture;
    private final int maxLevel;
    private final String boostType;
    private final String boostLabel;
    private final double boostBase;
    private final double boostPerLevel;

    public PetType(String id, ConfigurationSection s) {
        this.id = id;
        this.name = s.getString("name", id);
        this.rarity = s.getString("rarity", "&7Común");
        this.material = s.getString("material", "PLAYER_HEAD");
        this.texture = s.getString("texture", "");
        this.maxLevel = s.getInt("max-level", 100);
        this.boostType = s.getString("boost.type", "NONE").toUpperCase();
        this.boostLabel = s.getString("boost.label", boostType);
        this.boostBase = s.getDouble("boost.base", 1.0);
        this.boostPerLevel = s.getDouble("boost.per-level", 0.0);
    }

    public double multiplier(int level) {
        return boostBase + boostPerLevel * (level - 1);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getRarity() { return rarity; }
    public String getMaterial() { return material; }
    public String getTexture() { return texture; }
    public int getMaxLevel() { return maxLevel; }
    public String getBoostType() { return boostType; }
    public String getBoostLabel() { return boostLabel; }
}
