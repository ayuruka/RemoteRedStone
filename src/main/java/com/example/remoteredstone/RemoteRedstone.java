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