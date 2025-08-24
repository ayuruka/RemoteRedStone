package com.example.remoteredstone;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;

public class RemoteRedstone extends JavaPlugin implements Listener {

    private WebServer webServer;
    public LocationManager locationManager;
    private final Map<String, Location> selectedLocations = new ConcurrentHashMap<>();
    private static final String WAND_NAME = ChatColor.AQUA + "Remote Redstone Wand";
    private boolean consoleLoggingEnabled;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.consoleLoggingEnabled = getConfig().getBoolean("debug-console-logging", true);
        setupLogger();
        this.locationManager = new LocationManager(this);
        int port = getConfig().getInt("web-port", 8080);
        getServer().getPluginManager().registerEvents(this, this);
        List<String> worldNames = Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
        try {
            String version = this.getDescription().getVersion();
            webServer = new WebServer(port, this, worldNames, version);
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

    private void setupLogger() {
        Logger logger = this.getLogger();
        logger.setUseParentHandlers(this.consoleLoggingEnabled);
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdir();
            FileHandler fileHandler = new FileHandler(getDataFolder().getPath() + "/remoteredstone.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.info("Log file handler initialized.");
            logger.info("Console debug logging is " + (this.consoleLoggingEnabled ? "ENABLED" : "DISABLED") + ".");
        } catch (IOException e) {
            logger.severe("Failed to set up log file handler: " + e.getMessage());
        }
    }

    public void setGroupState(String groupId, boolean isON) {
        List<String> targetGroupIds = locationManager.getAllGroups().entrySet().stream()
                .filter(entry -> groupId.equals(entry.getValue().get("parent")))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        targetGroupIds.add(groupId);
        for (String childGroupId : targetGroupIds) {
            if (!childGroupId.equals(groupId)) setGroupState(childGroupId, isON);
        }
        locationManager.getAllLocations().entrySet().stream()
                .filter(entry -> groupId.equals(entry.getValue().get("group")))
                .forEach(entry -> {
                    String switchId = entry.getKey();
                    Map<String, Object> locData = entry.getValue();
                    setSwitchBlock(locData.get("world").toString(), locData.get("x").toString(), locData.get("y").toString(), locData.get("z").toString(), isON);
                    locationManager.updateLocationState(switchId, isON ? "ON" : "OFF");
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
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
                if (!chunk.isLoaded()) {
                    getLogger().info("[Action] Chunk at " + chunk.getX() + "," + chunk.getZ() + " was not loaded. Loading for block placement.");
                    chunk.load();
                }
                new Location(world, x, y, z).getBlock().setType(materialToSet);
                getLogger().info("[Action] Set block at " + worldName + ":" + x + "," + y + "," + z + " to " + materialToSet.name());
            }, 20L);
        } catch (NumberFormatException e) {
            getLogger().warning("Invalid coordinates provided to setSwitchBlock.");
        }
    }

    public Map<String, Boolean> getLiveBlockStatesIfLoaded(List<String> switchIdsToCheck) {
        Map<String, Boolean> liveStates = new HashMap<>();
        if (switchIdsToCheck == null || switchIdsToCheck.isEmpty()) return liveStates;
        Map<String, Map<String, Object>> allLocations = locationManager.getAllLocations();
        try {
            return Bukkit.getScheduler().callSyncMethod(this, () -> {
                for (String switchId : switchIdsToCheck) {
                    Map<String, Object> locData = allLocations.get(switchId);
                    if (locData == null) continue;
                    World world = Bukkit.getWorld(locData.get("world").toString());
                    if (world == null) continue;
                    int x = ((Number) locData.get("x")).intValue();
                    int y = ((Number) locData.get("y")).intValue();
                    int z = ((Number) locData.get("z")).intValue();
                    if (world.isChunkLoaded(x >> 4, z >> 4)) {
                        boolean isON = new Location(world, x, y, z).getBlock().getType() == Material.REDSTONE_BLOCK;
                        liveStates.put(switchId, isON);
                    }
                }
                return liveStates;
            }).get();
        } catch (Exception e) {
            getLogger().severe("Error during live block state check: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    public boolean giveSelectionWand(String playerName) { Player player = Bukkit.getPlayer(playerName); if (player == null || !player.isOnline()) { return false; } ItemStack wand = new ItemStack(Material.STICK, 1); ItemMeta meta = wand.getItemMeta(); meta.setDisplayName(WAND_NAME); meta.setLore(Arrays.asList(ChatColor.GRAY + "Right-click a block to select its coordinates.")); wand.setItemMeta(meta); player.getInventory().addItem(wand); player.sendMessage(ChatColor.GREEN + "You have received the Remote Redstone Wand!"); return true; }
    public Location pollSelectedLocation(String playerName) { return selectedLocations.remove(playerName.toLowerCase()); }
    @EventHandler public void onPlayerInteract(PlayerInteractEvent event) { Player player = event.getPlayer(); ItemStack itemInHand = player.getItemInHand(); if (event.getAction() == Action.RIGHT_CLICK_BLOCK && itemInHand != null && itemInHand.hasItemMeta()) { if (WAND_NAME.equals(itemInHand.getItemMeta().getDisplayName())) { event.setCancelled(true); Location selectedLoc = event.getClickedBlock().getLocation(); selectedLocations.put(player.getName().toLowerCase(), selectedLoc); player.sendMessage(ChatColor.GOLD + "Selected block at: " + selectedLoc.getWorld().getName() + ", " + selectedLoc.getBlockX() + ", " + selectedLoc.getBlockY() + ", " + selectedLoc.getBlockZ()); if (itemInHand.getAmount() > 1) { itemInHand.setAmount(itemInHand.getAmount() - 1); } else { player.setItemInHand(null); } } } }
    public void removeSwitchBlock(String worldName, String xStr, String yStr, String zStr) { World world = Bukkit.getWorld(worldName); if (world == null) return; try { final int x = Integer.parseInt(xStr); final int y = Integer.parseInt(yStr); final int z = Integer.parseInt(zStr); Bukkit.getScheduler().runTask(this, () -> { Chunk chunk = world.getChunkAt(x >> 4, z >> 4); if (!chunk.isLoaded()) { chunk.load(); } new Location(world, x, y, z).getBlock().setType(Material.AIR); }); } catch (NumberFormatException e) { getLogger().warning("Invalid coordinates provided to removeSwitchBlock."); } }
}