package com.example.remoteredstone;

import com.google.gson.Gson;
import fi.iki.elonen.NanoHTTPD;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WebServer extends NanoHTTPD {

    private final RemoteRedstone plugin;
    private final List<String> worldNames;
    private final Gson gson = new Gson();

    public WebServer(int port, RemoteRedstone plugin, List<String> worldNames) {
        super(port);
        this.plugin = plugin;
        this.worldNames = worldNames;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        if (uri.startsWith("/api/")) {
            return handleApiRequest(uri, session.getParameters());
        }
        return newFixedLengthResponse(generateDashboard());
    }

    private Response handleApiRequest(String uri, Map<String, List<String>> params) {
        String action = uri.substring(5);

        try {
            if ("request-wand".equals(action)) {
                String playerName = params.get("player").get(0);
                boolean success = plugin.giveSelectionWand(playerName);
                if (success) {
                    return jsonResponse(200, "success", "Wand given to player " + playerName);
                } else {
                    return jsonResponse(400, "error", "Player " + playerName + " not found or offline.");
                }
            }
            if ("poll-selection".equals(action)) {
                String playerName = params.get("player").get(0);
                Location loc = plugin.pollSelectedLocation(playerName);
                Map<String, Object> responseData = new HashMap<>();
                if (loc != null) {
                    responseData.put("status", "found");
                    responseData.put("world", loc.getWorld().getName());
                    responseData.put("x", loc.getBlockX());
                    responseData.put("y", loc.getBlockY());
                    responseData.put("z", loc.getBlockZ());
                } else {
                    responseData.put("status", "waiting");
                }
                return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(responseData));
            }
            if ("add-group".equals(action)) {
                String groupName = params.get("groupName").get(0);
                List<String> memoList = params.get("memo");
                String memo = (memoList != null && !memoList.isEmpty()) ? memoList.get(0) : "";
                String groupId = groupName.toLowerCase().replaceAll("\\s+", "-").replaceAll("[^a-z0-9-]", "");
                plugin.locationManager.addGroup(groupId, groupName, memo);
                return jsonResponse(200, "success", "Group '" + groupName + "' added.");
            }
            if ("remove-group".equals(action)) {
                String groupId = params.get("groupId").get(0);
                plugin.locationManager.removeGroup(groupId);
                return jsonResponse(200, "success", "Group and its switches removed.");
            }
            if ("toggle-group".equals(action)) {
                String groupId = params.get("groupId").get(0);
                boolean isON = "set".equals(params.get("state").get(0));
                plugin.setGroupState(groupId, isON);
                return jsonResponse(200, "success", "Group state changed.");
            }
            if ("add-switch".equals(action)) {
                String name = params.get("name").get(0).replaceAll("[^a-zA-Z0-9_-]", "");
                String world = params.get("world").get(0);
                String x = params.get("x").get(0);
                String y = params.get("y").get(0);
                String z = params.get("z").get(0);
                String groupId = params.get("group").get(0);
                plugin.locationManager.addLocation(name, world, Integer.parseInt(x), Integer.parseInt(y), Integer.parseInt(z), groupId);
                plugin.setSwitchBlock(world, x, y, z, false);
                return jsonResponse(200, "success", "Added switch '" + name + "'.");
            }
            if ("remove-switch".equals(action)) {
                String name = params.get("name").get(0);
                Map<String, Object> loc = plugin.locationManager.getAllLocations().get(name);
                if (loc != null) {
                    plugin.removeSwitchBlock(loc.get("world").toString(), loc.get("x").toString(), loc.get("y").toString(), loc.get("z").toString());
                    plugin.locationManager.removeLocation(name);
                    return jsonResponse(200, "success", "Removed switch '" + name + "'.");
                }
            }
            if ("toggle-switch".equals(action)) {
                String name = params.get("name").get(0);
                boolean isON = "set".equals(params.get("state").get(0));
                Map<String, Object> loc = plugin.locationManager.getAllLocations().get(name);
                if (loc != null) {
                    plugin.setSwitchBlock(loc.get("world").toString(), loc.get("x").toString(), loc.get("y").toString(), loc.get("z").toString(), isON);
                    plugin.locationManager.updateLocationState(name, isON ? "ON" : "OFF");
                    return jsonResponse(200, "success", "Toggled switch '" + name + "'.");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("An error occurred while handling API request: " + e.getMessage());
            return jsonResponse(400, "error", "Invalid request parameters.");
        }
        return jsonResponse(404, "error", "API endpoint not found.");
    }

    private Response jsonResponse(int status, String result, String message) {
        Response.IStatus responseStatus = Response.Status.INTERNAL_ERROR;
        if(status == 200) responseStatus = Response.Status.OK;
        if(status == 400) responseStatus = Response.Status.BAD_REQUEST;
        if(status == 404) responseStatus = Response.Status.NOT_FOUND;

        return newFixedLengthResponse(responseStatus, "application/json",
                "{\"status\":\"" + result + "\", \"message\":\"" + message + "\"}");
    }

    private String generateDashboard() {
        Map<String, Map<String, Object>> groups = plugin.locationManager.getAllGroups();
        Map<String, Map<String, Object>> locations = plugin.locationManager.getAllLocations();
        String worldOptions = worldNames.stream().map(name -> "<option value='" + name + "'>" + name + "</option>").collect(Collectors.joining());

        StringBuilder html = new StringBuilder("<!DOCTYPE html><html lang='en'><head><title>Remote Redstone Dashboard</title><meta name='viewport' content='width=device-width, initial-scale=1'><style>");
        html.append("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background-color:#1e1e1e;color:#e0e0e0;margin:0;padding:15px;}");
        html.append(".container{max-width:960px;margin:0 auto;} h1,h2,h3{color:#4fc3f7;border-bottom:1px solid #444;padding-bottom:10px;margin-top:1.5em;}");
        html.append(".group{background-color:#2a2a2a;padding:20px;border-radius:10px;margin-bottom:20px;box-shadow:0 4px 8px rgba(0,0,0,0.3);} .group-header{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;}");
        html.append(".group-memo{color:#aaa;font-style:italic;margin-top:5px;flex-basis:100%;} .actions button, .actions a {margin-left:10px;}");
        html.append("table{width:100%;border-collapse:collapse;margin:20px 0;} th,td{padding:12px 15px;text-align:left;border-bottom:1px solid #444;} thead{background-color:#333;}");
        html.append(".btn{padding:8px 15px;text-decoration:none;color:white;border-radius:5px;border:none;font-size:14px;cursor:pointer;transition:background-color 0.2s;}");
        html.append(".btn-on{background-color:#43a047;} .btn-off{background-color:#d32f2f;} .btn-del{background-color:#616161;} .btn:hover{opacity:0.8;} .btn:disabled{background-color:#555;color:#999;cursor:not-allowed;}");
        html.append("form{display:grid;gap:10px;} .form-grid{grid-template-columns:repeat(auto-fit,minmax(120px,1fr));} .coord-selector{grid-column:1/-1;display:flex;gap:10px;}");
        html.append("form input,form select{width:100%;padding:10px;background-color:#333;border:1px solid #555;color:#fff;border-radius:5px;box-sizing:border-box;}");
        html.append("form button{background-color:#0288d1;padding:12px;font-size:16px;grid-column:1/-1;} .msg{padding:12px;border-radius:5px;margin-bottom:15px;border-left:5px solid #0288d1;display:none;}");
        html.append("</style></head><body><div class='container'><h1>Redstone Dashboard</h1><div id='message-box' class='msg'></div>");

        html.append("<h2>Add New Group</h2><form data-action='add-group' class='form-grid'><input name='groupName' placeholder='New Group Name' required><input name='memo' placeholder='Memo (optional)'><button type='submit' class='btn'>Create Group</button></form>");

        for (Map.Entry<String, Map<String, Object>> groupEntry : groups.entrySet()) {
            String groupId = groupEntry.getKey();
            Map<String, Object> groupData = groupEntry.getValue();
            html.append("<div class='group' id='group-").append(groupId).append("'><div class='group-header'><h3>").append(groupData.get("name")).append("</h3>");
            html.append("<div class='actions'><button class='btn btn-on' data-action='toggle-group' data-group-id='").append(groupId).append("' data-state='set'>All ON</button>");
            html.append("<button class='btn btn-off' data-action='toggle-group' data-group-id='").append(groupId).append("' data-state='clear'>All OFF</button>");
            html.append("<button class='btn btn-del' data-action='remove-group' data-group-id='").append(groupId).append("'>Delete Group</button></div>");
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
                    html.append("<tr data-name='").append(name).append("'><td>").append(name).append("</td><td>").append(locStr).append("</td><td>");
                    html.append("<button class='btn btn-on' data-action='toggle-switch' data-name='").append(name).append("' data-state='set' ").append(isON ? "disabled" : "").append(">ON</button>");
                    html.append("<button class='btn btn-off' data-action='toggle-switch' data-name='").append(name).append("' data-state='clear' ").append(!isON ? "disabled" : "").append(">OFF</button>");
                    html.append("<button class='btn btn-del' data-action='remove-switch' data-name='").append(name).append("'>Delete</button>");
                    html.append("</td></tr>");
                }
            }
            if (!hasSwitches) html.append("<tr><td colspan='3'>No switches in this group yet.</td></tr>");
            html.append("</tbody></table>");

            html.append("<h4>Add Switch to Group</h4><form data-action='add-switch' class='form-grid'><input type='hidden' name='group' value='").append(groupId).append("'>");
            html.append("<input name='name' placeholder='Switch Name' required><select name='world' required>").append(worldOptions).append("</select>");
            html.append("<input type='number' name='x' placeholder='X' required><input type='number' name='y' placeholder='Y' required><input type='number' name='z' placeholder='Z' required>");
            html.append("<div class='coord-selector'><input name='playerName' placeholder='Your IGN for Wand'><button type='button' class='btn' data-action='request-wand'>Select Block</button></div>");
            html.append("<button type='submit' class='btn'>Add Switch</button></form></div>");
        }

        html.append("<script>");
        html.append("const msgBox=document.getElementById('message-box');let pollInterval=null;");
        html.append("function showMsg(txt,isErr){msgBox.textContent=txt;msgBox.style.backgroundColor=isErr?'#c0392b':'rgba(2,136,209,0.5)';msgBox.style.display='block';setTimeout(()=>msgBox.style.display='none',5000);}");
        html.append("function startPolling(playerName,form){if(pollInterval)clearInterval(pollInterval);let attempts=0;pollInterval=setInterval(async()=>{const res=await fetch(`/api/poll-selection?player=${playerName}`);const data=await res.json();if(data.status==='found'){clearInterval(pollInterval);form.querySelector('[name=world]').value=data.world;form.querySelector('[name=x]').value=data.x;form.querySelector('[name=y]').value=data.y;form.querySelector('[name=z]').value=data.z;showMsg('Coordinates received!')}attempts++;if(attempts>60){clearInterval(pollInterval);showMsg('Selection timed out.',true)}},1000)}");
        html.append("document.body.addEventListener('submit',async e=>{e.preventDefault();const form=e.target;const action=form.dataset.action;if(!action)return;const formData=new FormData(form);const params=new URLSearchParams(formData);const res=await fetch(`/api/${action}?${params.toString()}`);const data=await res.json();if(data.status==='success'){window.location.reload()}else{showMsg(data.message,true)}});");
        html.append("document.body.addEventListener('click',async e=>{const btn=e.target;const action=btn.dataset.action;if(!action||btn.tagName!=='BUTTON')return;e.preventDefault();let url,confirmMsg;");
        html.append("if(action==='request-wand'){const form=btn.closest('form');const input=form.querySelector('[name=playerName]');if(!input.value){showMsg('Please enter your player name.',true);return}const res=await fetch(`/api/request-wand?player=${input.value}`);const data=await res.json();if(data.status==='success'){showMsg('Wand sent! Right-click a block in-game.');startPolling(input.value,form)}else{showMsg(data.message,true)}return}");
        html.append("if(action==='toggle-switch'){url=`/api/toggle-switch?name=${btn.dataset.name}&state=${btn.dataset.state}`}");
        html.append("else if(action==='remove-switch'){confirmMsg=`Delete switch '${btn.dataset.name}'?`;url=`/api/remove-switch?name=${btn.dataset.name}`}");
        html.append("else if(action==='toggle-group'){url=`/api/toggle-group?groupId=${btn.dataset.groupId}&state=${btn.dataset.state}`}");
        html.append("else if(action==='remove-group'){confirmMsg=`Delete group and ALL its switches?`;url=`/api/remove-group?groupId=${btn.dataset.groupId}`}");
        html.append("else return;if(confirmMsg&&!confirm(confirmMsg))return;const res=await fetch(url);const data=await res.json();if(data.status==='success'){window.location.reload()}else{showMsg(data.message,true)}});");
        html.append("</script></div></body></html>");
        return html.toString();
    }
}