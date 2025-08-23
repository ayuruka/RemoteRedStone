package com.example.remoteredstone;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RemoteRedstone extends JavaPlugin {

    private WebServer webServer;
    public LocationManager locationManager;

    @Override
    public void onEnable() {
        this.locationManager = new LocationManager(this);
        saveDefaultConfig();
        int port = getConfig().getInt("web-port", 8080);

        List<String> worldNames = Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());

        try {
            webServer = new WebServer(port, this, worldNames);
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

    public void setGroupState(String groupId, boolean isON) {
        locationManager.getAllLocations().entrySet().stream()
                .filter(entry -> groupId.equals(entry.getValue().get("group")))
                .forEach(entry -> {
                    String locName = entry.getKey();
                    Map<String, Object> locData = entry.getValue();
                    setSwitchBlock(
                            locData.get("world").toString(),
                            locData.get("x").toString(),
                            locData.get("y").toString(),
                            locData.get("z").toString(),
                            isON
                    );
                    locationManager.updateLocationState(locName, isON ? "ON" : "OFF");
                });
    }

    public void setSwitchBlock(String worldName, String xStr, String yStr, String zStr, final boolean isON) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;
        try {
            final int x = Integer.parseInt(xStr);
            final int y = Integer.parseInt(yStr);
            final int z = Integer.parseInt(zStr);
            final Material materialToSet = isON ? Material.REDSTONE_BLOCK : Material.GLASS;

            Bukkit.getScheduler().runTask(this, () -> {
                Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
                if (!chunk.isLoaded()) chunk.load();
                new Location(world, x, y, z).getBlock().setType(materialToSet);
            });
        } catch (NumberFormatException e) {
            getLogger().warning("Invalid coordinates provided to setSwitchBlock.");
        }
    }

    public void removeSwitchBlock(String worldName, String xStr, String yStr, String zStr) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;
        try {
            final int x = Integer.parseInt(xStr);
            final int y = Integer.parseInt(yStr);
            final int z = Integer.parseInt(zStr);

            Bukkit.getScheduler().runTask(this, () -> {
                Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
                if (!chunk.isLoaded()) chunk.load();
                new Location(world, x, y, z).getBlock().setType(Material.AIR);
            });
        } catch (NumberFormatException e) {
            getLogger().warning("Invalid coordinates provided to removeSwitchBlock.");
        }
    }
}