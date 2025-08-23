package com.example.remoteredstone;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;

public class RemoteRedstone extends JavaPlugin implements CommandExecutor {

    private WebServer webServer;
    public LocationManager locationManager;

    @Override
    public void onEnable() {
        this.locationManager = new LocationManager(this);

        saveDefaultConfig();
        int port = getConfig().getInt("web-port", 8080);

        boolean loginEnabled = getConfig().getBoolean("login.enabled", false);
        String username = getConfig().getString("login.username", "admin");
        String password = getConfig().getString("login.password", "");

        try {
            webServer = new WebServer(port, this, loginEnabled, username, password);
            webServer.start();
            getLogger().info("Web server started on port: " + port);
            if (loginEnabled) {
                getLogger().info("Login protection is ENABLED.");
            } else {
                getLogger().info("Login protection is DISABLED.");
            }
        } catch (IOException e) {
            getLogger().severe("Failed to start web server!");
            e.printStackTrace();
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
            return ChatColor.RED + "World '" + worldName + "' not found.";
        }

        final int x, y, z;
        try {
            x = Integer.parseInt(xStr);
            y = Integer.parseInt(yStr);
            z = Integer.parseInt(zStr);
        } catch (NumberFormatException e) {
            return ChatColor.RED + "Coordinates must be valid numbers.";
        }

        Bukkit.getScheduler().runTask(this, new Runnable() {
            @Override
            public void run() {
                Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
                if (!chunk.isLoaded()) {
                    chunk.load();
                }
                Location loc = new Location(world, x, y, z);
                loc.getBlock().setType(placeBlock ? Material.REDSTONE_BLOCK : Material.AIR);
            }
        });

        if (placeBlock) {
            return "Set redstone block at " + worldName + " " + x + "," + y + "," + z;
        } else {
            return "Cleared block at " + worldName + " " + x + "," + y + "," + z;
        }
    }
}