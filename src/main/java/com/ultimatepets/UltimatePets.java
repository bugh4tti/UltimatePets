package com.ultimatepets;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class UltimatePets extends JavaPlugin {

    private static UltimatePets instance;
    private YamlConfiguration petsConfig;
    private PetManager petManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        if (!new File(getDataFolder(), "pets.yml").exists()) {
            saveResource("pets.yml", false);
        }
        loadPetsConfig();

        petManager = new PetManager(this);
        petManager.loadTypes();
        petManager.loadData();
        petManager.cleanupStrayStands();

        PetsMenu menu = new PetsMenu(this, petManager);
        getServer().getPluginManager().registerEvents(menu, this);
        getServer().getPluginManager().registerEvents(new PetListener(this, petManager), this);

        PetsCommand command = new PetsCommand(this, petManager, menu);
        getCommand("pets").setExecutor(command);
        getCommand("pets").setTabCompleter(command);

        petManager.startTask();
        for (Player p : Bukkit.getOnlinePlayers()) {
            petManager.spawnActive(p);
        }
    }

    @Override
    public void onDisable() {
        if (petManager != null) {
            petManager.stopTask();
            petManager.despawnEverything();
            petManager.saveData();
        }
    }

    public void reload() {
        reloadConfig();
        loadPetsConfig();
        petManager.loadTypes();
        for (Player p : Bukkit.getOnlinePlayers()) {
            petManager.despawnAll(p);
            petManager.spawnActive(p);
        }
        petManager.startTask();
    }

    private void loadPetsConfig() {
        petsConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "pets.yml"));
    }

    public YamlConfiguration getPetsConfig() {
        return petsConfig;
    }

    public PetManager getPetManager() {
        return petManager;
    }

    public static UltimatePets getInstance() {
        return instance;
    }
}
