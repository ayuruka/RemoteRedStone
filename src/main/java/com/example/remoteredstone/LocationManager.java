/*
 * Copyright [2025] [ayuruka]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.remoteredstone;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
            plugin.getLogger().severe("Could not save locations.yml!");
        }
    }

    public void addLocation(String name, String world, int x, int y, int z) {
        String path = "locations." + name;
        dataConfig.set(path + ".world", world);
        dataConfig.set(path + ".x", x);
        dataConfig.set(path + ".y", y);
        dataConfig.set(path + ".z", z);
        saveConfig();
    }

    public void removeLocation(String name) {
        dataConfig.set("locations." + name, null);
        saveConfig();
    }

    public Map<String, Map<String, Object>> getAllLocations() {
        Map<String, Map<String, Object>> locations = new HashMap<>();
        ConfigurationSection section = dataConfig.getConfigurationSection("locations");
        if (section != null) {
            Set<String> keys = section.getKeys(false);
            for (String key : keys) {
                locations.put(key, section.getConfigurationSection(key).getValues(false));
            }
        }
        return locations;
    }
}