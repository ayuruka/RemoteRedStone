package com.example.remoteredstone;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
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

    public void addGroup(String groupName, String memo, String parentId) {
        String groupId = "group_" + System.currentTimeMillis();
        String path = "groups." + groupId;
        dataConfig.set(path + ".name", groupName);
        dataConfig.set(path + ".memo", memo);
        if (parentId != null && !parentId.isEmpty()) {
            dataConfig.set(path + ".parent", parentId);
        }
        saveConfig();
    }

    public void updateGroup(String groupId, String newName, String newMemo) {
        String path = "groups." + groupId;
        if (dataConfig.isConfigurationSection(path)) {
            dataConfig.set(path + ".name", newName);
            dataConfig.set(path + ".memo", newMemo);
            saveConfig();
        }
    }

    public void removeGroup(String groupId) {
        List<String> groupsToDelete = getDescendantGroups(groupId);
        groupsToDelete.add(groupId);

        getAllLocations().entrySet().stream()
                .filter(entry -> groupsToDelete.contains(entry.getValue().get("group")))
                .forEach(entry -> removeLocation(entry.getKey()));

        for (String id : groupsToDelete) {
            dataConfig.set("groups." + id, null);
        }
        saveConfig();
    }

    private List<String> getDescendantGroups(String parentId) {
        return getAllGroups().entrySet().stream()
                .filter(entry -> parentId.equals(entry.getValue().get("parent")))
                .flatMap(entry -> {
                    List<String> descendants = getDescendantGroups(entry.getKey());
                    descendants.add(entry.getKey());
                    return descendants.stream();
                })
                .collect(Collectors.toList());
    }

    public Map<String, Map<String, Object>> getAllGroups() {
        ConfigurationSection section = dataConfig.getConfigurationSection("groups");
        if (section == null) return Collections.emptyMap();
        return section.getKeys(false).stream()
                .collect(Collectors.toMap(key -> key, key -> section.getConfigurationSection(key).getValues(false)));
    }

    public void addLocation(String name, String world, int x, int y, int z, String groupId) {
        String switchId = "switch_" + System.currentTimeMillis();
        String path = "locations." + switchId;
        dataConfig.set(path + ".name", name);
        dataConfig.set(path + ".world", world);
        dataConfig.set(path + ".x", x);
        dataConfig.set(path + ".y", y);
        dataConfig.set(path + ".z", z);
        dataConfig.set(path + ".state", "OFF");
        dataConfig.set(path + ".group", groupId);
        saveConfig();
    }

    public void updateSwitch(String switchId, String newName) {
        String path = "locations." + switchId;
        if (dataConfig.isConfigurationSection(path)) {
            dataConfig.set(path + ".name", newName);
            saveConfig();
        }
    }

    public void updateLocationState(String switchId, String state) {
        dataConfig.set("locations." + switchId + ".state", state);
        saveConfig();
    }

    public void removeLocation(String switchId) {
        dataConfig.set("locations." + switchId, null);
        saveConfig();
    }

    public Map<String, Map<String, Object>> getAllLocations() {
        ConfigurationSection section = dataConfig.getConfigurationSection("locations");
        if (section == null) return Collections.emptyMap();
        return section.getKeys(false).stream()
                .collect(Collectors.toMap(key -> key, key -> section.getConfigurationSection(key).getValues(false)));
    }
}