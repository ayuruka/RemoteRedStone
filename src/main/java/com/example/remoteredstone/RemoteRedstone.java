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

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

public class RemoteRedstone extends JavaPlugin {

    private WebServer webServer;
    public LocationManager locationManager;

    @Override
    public void onEnable() {
        this.locationManager = new LocationManager(this);

        saveDefaultConfig();
        int port = getConfig().getInt("web-port", 8080);

        try {
            webServer = new WebServer(port, this);
            webServer.start();
            getLogger().info("Web server started on port: " + port);
        } catch (IOException e) {
            getLogger().severe("Failed to start web server! " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (webServer != null) {
            webServer.stop();
            getLogger().info("Web server stopped.");
        }
    }

    public String setRedstoneBlock(String worldName, String xStr, String yStr, String zStr, final boolean placeBlock) {
        final World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return "World '" + worldName + "' not found.";
        }
        final int x, y, z;
        try {
            x = Integer.parseInt(xStr);
            y = Integer.parseInt(yStr);
            z = Integer.parseInt(zStr);
        } catch (NumberFormatException e) {
            return "Coordinates must be valid numbers.";
        }

        Bukkit.getScheduler().runTask(this, () -> {
            Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
            if (!chunk.isLoaded()) {
                chunk.load();
            }
            Location loc = new Location(world, x, y, z);
            loc.getBlock().setType(placeBlock ? Material.REDSTONE_BLOCK : Material.AIR);
        });
        if (placeBlock) {
            return "Set redstone block at " + worldName + " " + x + "," + y + "," + z;
        } else {
            return "Cleared block at " + worldName + " " + x + "," + y + "," + z;
        }
    }
}