package com.ultimatepets;

import java.util.UUID;

public class PetData {

    private final UUID id;
    private final String type;
    private int level;
    private boolean active;

    public PetData(UUID id, String type, int level) {
        this.id = id;
        this.type = type;
        this.level = level;
    }

    public UUID getId() { return id; }
    public String getType() { return type; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
