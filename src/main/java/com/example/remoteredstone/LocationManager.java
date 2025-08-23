package com.example.remoteredstone;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public class LocationManager {

    private final RemoteRedstone plugin;
    private File configFile;
    private FileConfiguration dataConfig;

    public LocationManager(RemoteRedstone plugin) {
        this.plugin = plugin;
        setup();
    }

    public void setup() {
        configFile = new File(plugin.getDataFolder(), "locations.yml");
        if (!configFile.exists()) {
            plugin.saveResource("locations.yml", false);
        }
        dataConfig = YamlConfiguration.loadConfiguration(configFile);
    }

    public void saveConfig() {
        try {
            dataConfig.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save locations.yml! " + e.getMessage());
        }
    }

    public void addGroup(String groupId, String groupName, String memo) {
        String path = "groups." + groupId;
        dataConfig.set(path + ".name", groupName);
        dataConfig.set(path + ".memo", memo);
        saveConfig();
    }

    public void removeGroup(String groupId) {
        dataConfig.set("groups." + groupId, null);
        getAllLocations().entrySet().stream()
                .filter(entry -> groupId.equals(entry.getValue().get("group")))
                .forEach(entry -> removeLocation(entry.getKey()));
        saveConfig();
    }

    public Map<String, Map<String, Object>> getAllGroups() {
        ConfigurationSection section = dataConfig.getConfigurationSection("groups");
        if (section == null) return Collections.emptyMap();
        return section.getKeys(false).stream()
                .filter(section::isConfigurationSection)
                .collect(Collectors.toMap(
                        key -> key,
                        key -> section.getConfigurationSection(key).getValues(false)
                ));
    }

    public void addLocation(String name, String world, int x, int y, int z, String groupId) {
        String path = "locations." + name;
        dataConfig.set(path + ".world", world);
        dataConfig.set(path + ".x", x);
        dataConfig.set(path + ".y", y);
        dataConfig.set(path + ".z", z);
        dataConfig.set(path + ".state", "OFF");
        dataConfig.set(path + ".group", groupId);
        saveConfig();
    }

    public void updateLocationState(String name, String state) {
        dataConfig.set("locations." + name + ".state", state);
        saveConfig();
    }

    public void removeLocation(String name) {
        dataConfig.set("locations." + name, null);
        saveConfig();
    }

    public Map<String, Map<String, Object>> getAllLocations() {
        ConfigurationSection section = dataConfig.getConfigurationSection("locations");
        if (section == null) return Collections.emptyMap();
        return section.getKeys(false).stream()
                .filter(section::isConfigurationSection)
                .collect(Collectors.toMap(
                        key -> key,
                        key -> section.getConfigurationSection(key).getValues(false)
                ));
    }
}