package com.example.remoteredstone;

import fi.iki.elonen.NanoHTTPD;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WebServer extends NanoHTTPD {

    private final RemoteRedstone plugin;
    private final List<String> worldNames;

    public WebServer(int port, RemoteRedstone plugin, List<String> worldNames) {
        super(port);
        this.plugin = plugin;
        this.worldNames = worldNames;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Map<String, List<String>> params = session.getParameters();
        if ("/favicon.ico".equalsIgnoreCase(uri)) {
            return newFixedLengthResponse(Response.Status.NO_CONTENT, "image/x-icon", "");
        }
        if (params.containsKey("action")) {
            return handleActionRequest(params);
        }
        return newFixedLengthResponse(generateDashboard(params.containsKey("error")));
    }

    private Response handleActionRequest(Map<String, List<String>> params) {
        try {
            String action = params.get("action").get(0);

            if ("add-group".equals(action)) {
                String groupName = params.get("groupName").get(0);
                String memo = params.get("memo").get(0);
                String groupId = groupName.toLowerCase().replaceAll("\\s+", "-").replaceAll("[^a-zA-Z0-9-]", "");
                plugin.locationManager.addGroup(groupId, groupName, memo);
            }
            if ("remove-group".equals(action)) {
                String groupId = params.get("groupId").get(0);
                Map<String, Object> groupData = plugin.locationManager.getAllGroups().get(groupId);
                if (groupData != null) {
                    plugin.locationManager.getAllLocations().entrySet().stream()
                            .filter(entry -> groupId.equals(entry.getValue().get("group")))
                            .forEach(entry -> {
                                Map<String, Object> loc = entry.getValue();
                                plugin.removeSwitchBlock(loc.get("world").toString(), loc.get("x").toString(), loc.get("y").toString(), loc.get("z").toString());
                            });
                    plugin.locationManager.removeGroup(groupId);
                }
            }
            if ("toggle-group".equals(action)) {
                String groupId = params.get("groupId").get(0);
                boolean isON = "set".equals(params.get("state").get(0));
                plugin.setGroupState(groupId, isON);
            }
            if ("add-switch".equals(action)) {
                String name = params.get("name").get(0);
                String world = params.get("world").get(0);
                int x = Integer.parseInt(params.get("x").get(0));
                int y = Integer.parseInt(params.get("y").get(0));
                int z = Integer.parseInt(params.get("z").get(0));
                String groupId = params.get("group").get(0);
                plugin.locationManager.addLocation(name, world, x, y, z, groupId);
                plugin.setSwitchBlock(world, String.valueOf(x), String.valueOf(y), String.valueOf(z), false);
            }
            if ("remove-switch".equals(action)) {
                String name = params.get("name").get(0);
                Map<String, Object> loc = plugin.locationManager.getAllLocations().get(name);
                if (loc != null) {
                    plugin.removeSwitchBlock(loc.get("world").toString(), loc.get("x").toString(), loc.get("y").toString(), loc.get("z").toString());
                    plugin.locationManager.removeLocation(name);
                }
            }
            if ("toggle-switch".equals(action)) {
                String name = params.get("name").get(0);
                boolean isON = "set".equals(params.get("state").get(0));
                Map<String, Object> loc = plugin.locationManager.getAllLocations().get(name);
                if (loc != null) {
                    plugin.setSwitchBlock(loc.get("world").toString(), loc.get("x").toString(), loc.get("y").toString(), loc.get("z").toString(), isON);
                    plugin.locationManager.updateLocationState(name, isON ? "ON" : "OFF");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Web UI Action Error: " + e.getMessage());
            return redirect("/?error=true");
        }
        return redirect("/");
    }

    private Response redirect(String url) {
        Response res = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "");
        res.addHeader("Location", url);
        return res;
    }

    private String generateDashboard(boolean hasError) {
        Map<String, Map<String, Object>> groups = plugin.locationManager.getAllGroups();
        Map<String, Map<String, Object>> locations = plugin.locationManager.getAllLocations();
        String worldOptions = worldNames.stream().map(name -> "<option value='" + name + "'>" + name + "</option>").collect(Collectors.joining());

        StringBuilder html = new StringBuilder("<!DOCTYPE html><html lang='ja'><head><title>Remote Redstone</title><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1'><style>");
        html.append("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background-color:#1e1e1e;color:#e0e0e0;margin:0;padding:15px;}");
        html.append(".container{max-width:960px;margin:0 auto;} h1,h2,h3{color:#4fc3f7;border-bottom:1px solid #444;padding-bottom:10px;margin-top:1.5em;}");
        html.append(".group{background-color:#2a2a2a;padding:20px;border-radius:10px;margin-bottom:20px;box-shadow:0 4px 8px rgba(0,0,0,0.3);} .group-header{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;}");
        html.append(".group-memo{color:#aaa;font-style:italic;margin-top:5px;flex-basis:100%;} .actions a {margin-left:10px;}");
        html.append("table{width:100%;border-collapse:collapse;margin:20px 0;} th,td{padding:12px 15px;text-align:left;border-bottom:1px solid #444;} thead{background-color:#333;}");
        html.append(".btn{padding:8px 15px;text-decoration:none;color:white;border-radius:5px;border:none;font-size:14px;cursor:pointer;transition:background-color 0.2s;}");
        html.append(".btn-on{background-color:#43a047;} .btn-off{background-color:#d32f2f;} .btn-del{background-color:#616161;} .btn:hover{opacity:0.8;} a.btn-disabled{background-color:#555;color:#999;cursor:not-allowed;pointer-events:none;}");
        html.append("form{display:grid;gap:10px;} .form-grid{grid-template-columns:repeat(auto-fit,minmax(120px,1fr));} form input,form select{width:100%;padding:10px;background-color:#333;border:1px solid #555;color:#fff;border-radius:5px;box-sizing:border-box;}");
        html.append("form button{background-color:#0288d1;padding:12px;font-size:16px;grid-column:1/-1;} .error{padding:12px;border-radius:5px;margin-bottom:15px;border-left:5px solid #c0392b;background:rgba(192,57,43,0.5);}");
        html.append("</style></head><body><div class='container'><h1>Redstone Dashboard</h1>");
        if(hasError) html.append("<div class='error'>An error occurred with your last request.</div>");

        html.append("<h2>Add New Group</h2><form method='get' class='form-grid'><input type='hidden' name='action' value='add-group'><input name='groupName' placeholder='新しいグループ名' required><input name='memo' placeholder='メモ (任意)'><button type='submit' class='btn'>Create Group</button></form>");

        for (Map.Entry<String, Map<String, Object>> groupEntry : groups.entrySet()) {
            String groupId = groupEntry.getKey();
            Map<String, Object> groupData = groupEntry.getValue();
            html.append("<div class='group' id='group-").append(groupId).append("'><div class='group-header'><h3>").append(groupData.get("name")).append("</h3>");
            html.append("<div class='actions'><a href='/?action=toggle-group&groupId=").append(groupId).append("&state=set' class='btn btn-on'>All ON</a>");
            html.append("<a href='/?action=toggle-group&groupId=").append(groupId).append("&state=clear' class='btn btn-off'>All OFF</a>");
            html.append("<a href='/?action=remove-group&groupId=").append(groupId).append("' class='btn btn-del' onclick='return confirm(\"Delete group and ALL its switches?\")'>Delete Group</a></div>");
            html.append("<p class='group-memo'>").append(groupData.get("memo")).append("</p></div>");
            html.append("<table><thead><tr><th>Name</th><th>Location</th><th>Actions</th></tr></thead><tbody>");
            boolean hasSwitches = false;
            for (Map.Entry<String, Map<String, Object>> locEntry : locations.entrySet()) {
                if (groupId.equals(locEntry.getValue().get("group"))) {
                    hasSwitches = true;
                    String name = locEntry.getKey();
                    Map<String, Object> loc = locEntry.getValue();
                    boolean isON = "ON".equals(loc.getOrDefault("state", "OFF"));
                    String locStr = String.format("%s @ %s, %s, %s", loc.get("world"), loc.get("x"), loc.get("y"), loc.get("z"));
                    html.append("<tr><td>").append(name).append("</td><td>").append(locStr).append("</td><td>");
                    html.append("<a href='/?action=toggle-switch&name=").append(name).append("&state=set' class='btn btn-on ").append(isON ? "btn-disabled" : "").append("'>ON</a>");
                    html.append("<a href='/?action=toggle-switch&name=").append(name).append("&state=clear' class='btn btn-off ").append(!isON ? "btn-disabled" : "").append("'>OFF</a>");
                    html.append("<a href='/?action=remove-switch&name=").append(name).append("' class='btn btn-del' onclick='return confirm(\"Delete switch \\'").append(name).append("\\'?\")'>Delete</a>");
                    html.append("</td></tr>");
                }
            }
            if (!hasSwitches) html.append("<tr><td colspan='3'>No switches in this group yet.</td></tr>");
            html.append("</tbody></table>");
            html.append("<h4>Add Switch to Group</h4><form method='get' class='form-grid'><input type='hidden' name='action' value='add-switch'><input type='hidden' name='group' value='").append(groupId).append("'>");
            html.append("<input name='name' placeholder='スイッチ名' required><select name='world' required>").append(worldOptions).append("</select>");
            html.append("<input type='number' name='x' placeholder='X' required><input type='number' name='y' placeholder='Y' required><input type='number' name='z' placeholder='Z' required>");
            html.append("<button type='submit' class='btn'>Add Switch</button></form></div>");
        }

        html.append("</div></body></html>");
        return html.toString();
    }
}