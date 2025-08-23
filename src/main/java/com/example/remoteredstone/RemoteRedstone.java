package com.example.remoteredstone;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RemoteRedstone extends JavaPlugin implements Listener {

    private WebServer webServer;
    public LocationManager locationManager;

    private final Map<String, Location> selectedLocations = new ConcurrentHashMap<>();
    private static final String WAND_NAME = ChatColor.AQUA + "Remote Redstone Wand";

    @Override
    public void onEnable() {
        this.locationManager = new LocationManager(this);
        saveDefaultConfig();
        int port = getConfig().getInt("web-port", 8080);

        getServer().getPluginManager().registerEvents(this, this);

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

    public boolean giveSelectionWand(String playerName) {
        Player player = Bukkit.getPlayer(playerName);
        if (player == null || !player.isOnline()) {
            return false;
        }
        ItemStack wand = new ItemStack(Material.STICK, 1);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(WAND_NAME);
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Right-click a block to select its coordinates."));
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);
        player.sendMessage(ChatColor.GREEN + "You have received the Remote Redstone Wand!");
        return true;
    }

    public Location pollSelectedLocation(String playerName) {
        return selectedLocations.remove(playerName.toLowerCase());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getItemInHand();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && itemInHand != null && itemInHand.hasItemMeta()) {
            if (WAND_NAME.equals(itemInHand.getItemMeta().getDisplayName())) {
                event.setCancelled(true);
                Location selectedLoc = event.getClickedBlock().getLocation();

                selectedLocations.put(player.getName().toLowerCase(), selectedLoc);

                player.sendMessage(ChatColor.GOLD + "Selected block at: " +
                        selectedLoc.getWorld().getName() + ", " +
                        selectedLoc.getBlockX() + ", " +
                        selectedLoc.getBlockY() + ", " +
                        selectedLoc.getBlockZ());

                if (itemInHand.getAmount() > 1) {
                    itemInHand.setAmount(itemInHand.getAmount() - 1);
                } else {
                    player.setItemInHand(null);
                }
            }
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
                org.bukkit.Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
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
                org.bukkit.Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
                if (!chunk.isLoaded()) chunk.load();
                new Location(world, x, y, z).getBlock().setType(Material.AIR);
            });
        } catch (NumberFormatException e) {
            getLogger().warning("Invalid coordinates provided to removeSwitchBlock.");
        }
    }
}