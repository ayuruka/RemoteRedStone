package com.example.remoteredstone;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.ResponseException;
import org.bukkit.Location;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WebServer extends NanoHTTPD {

    private final RemoteRedstone plugin;
    private final List<String> worldNames;
    private final String pluginVersion;
    private final Gson gson = new Gson();

    public WebServer(int port, RemoteRedstone plugin, List<String> worldNames, String pluginVersion) {
        super(port);
        this.plugin = plugin;
        this.worldNames = worldNames;
        this.pluginVersion = pluginVersion;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        if (uri.startsWith("/api/")) {
            return handleApiRequest(uri, session);
        }
        return newFixedLengthResponse(generateDashboard());
    }

    private String decodeParam(String value) {
        if (value == null) return "";
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    private Response handleApiRequest(String uri, IHTTPSession session) {
        String action = uri.substring(5);
        Map<String, String> params = new HashMap<>();
        try {
            session.parseBody(new HashMap<>());
            params.putAll(session.getParms());
        } catch (IOException | ResponseException e) {
            params.putAll(session.getParms());
        }

        try {
            if ("get-live-states".equals(action) && session.getMethod() == Method.POST) {
                Map<String, String> files = new HashMap<>();
                try {
                    session.parseBody(files);
                    String body = files.get("postData");
                    if (body != null) {
                        List<String> switchIds = gson.fromJson(body, new TypeToken<List<String>>(){}.getType());
                        Map<String, Boolean> liveStates = plugin.getLiveBlockStatesIfLoaded(switchIds);
                        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", gson.toJson(liveStates));
                    }
                } catch (IOException | ResponseException e) { /* Fails silently */ }
                return jsonResponse(400, "error", "Missing or invalid POST body for live-states.");
            }

            if ("update-group".equals(action)) { plugin.locationManager.updateGroup(decodeParam(params.get("groupId")), decodeParam(params.get("newName")), decodeParam(params.get("newMemo"))); return jsonResponse(200, "success", "Group updated."); }
            if ("update-switch".equals(action)) { plugin.locationManager.updateSwitch(decodeParam(params.get("switchId")), decodeParam(params.get("newName"))); return jsonResponse(200, "success", "Switch updated."); }
            if ("toggle-switch".equals(action)) { String switchId = decodeParam(params.get("switchId")); boolean isON = "set".equals(decodeParam(params.get("state"))); Map<String, Object> loc = plugin.locationManager.getAllLocations().get(switchId); if (loc != null) { plugin.setSwitchBlock(loc.get("world").toString(), loc.get("x").toString(), loc.get("y").toString(), loc.get("z").toString(), isON); plugin.locationManager.updateLocationState(switchId, isON ? "ON" : "OFF"); return jsonResponse(200, "success", "Toggling switch..."); } }
            if ("remove-switch".equals(action)) { String switchId = decodeParam(params.get("switchId")); Map<String, Object> loc = plugin.locationManager.getAllLocations().get(switchId); if (loc != null) { plugin.removeSwitchBlock(loc.get("world").toString(), loc.get("x").toString(), loc.get("y").toString(), loc.get("z").toString()); plugin.locationManager.removeLocation(switchId); return jsonResponse(200, "success", "Removed switch."); } }
            if ("request-wand".equals(action)) { String playerName = decodeParam(params.get("player")); boolean success = plugin.giveSelectionWand(playerName); if (success) { return jsonResponse(200, "success", "Wand given to player " + playerName); } else { return jsonResponse(400, "error", "Player " + playerName + " not found or offline."); } }
            if ("poll-selection".equals(action)) { String playerName = decodeParam(params.get("player")); Location loc = plugin.pollSelectedLocation(playerName); Map<String, Object> responseData = new HashMap<>(); if (loc != null) { responseData.put("status", "found"); responseData.put("world", loc.getWorld().getName()); responseData.put("x", loc.getBlockX()); responseData.put("y", loc.getBlockY()); responseData.put("z", loc.getBlockZ()); } else { responseData.put("status", "waiting"); } return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(responseData)); }
            if ("add-group".equals(action)) { plugin.locationManager.addGroup(decodeParam(params.get("groupName")), decodeParam(params.get("memo")), decodeParam(params.get("parentId"))); return jsonResponse(200, "success", "Group '" + decodeParam(params.get("groupName")) + "' added."); }
            if ("remove-group".equals(action)) { plugin.locationManager.removeGroup(decodeParam(params.get("groupId"))); return jsonResponse(200, "success", "Group and its switches removed."); }
            if ("toggle-group".equals(action)) { plugin.setGroupState(decodeParam(params.get("groupId")), "set".equals(decodeParam(params.get("state")))); return jsonResponse(200, "success", "Toggling group..."); }
            if ("add-switch".equals(action)) { plugin.locationManager.addLocation(decodeParam(params.get("name")), decodeParam(params.get("world")), Integer.parseInt(params.get("x")), Integer.parseInt(params.get("y")), Integer.parseInt(params.get("z")), decodeParam(params.get("group"))); plugin.setSwitchBlock(decodeParam(params.get("world")), params.get("x"), params.get("y"), params.get("z"), false); return jsonResponse(200, "success", "Added switch '" + decodeParam(params.get("name")) + "'."); }

        } catch (Exception e) {
            plugin.getLogger().warning("An error occurred while handling API request: " + e.getMessage());
            e.printStackTrace();
            return jsonResponse(400, "error", "Invalid request parameters.");
        }
        return jsonResponse(404, "error", "API endpoint not found.");
    }

    private Response jsonResponse(int status, String result, String message) { Response.IStatus responseStatus = Response.Status.INTERNAL_ERROR; if(status == 200) responseStatus = Response.Status.OK; if(status == 400) responseStatus = Response.Status.BAD_REQUEST; if(status == 404) responseStatus = Response.Status.NOT_FOUND; return newFixedLengthResponse(responseStatus, "application/json; charset=utf-8", "{\"status\":\"" + result + "\", \"message\":\"" + message + "\"}"); }

    private String generateDashboard() {
        Map<String, Map<String, Object>> allGroups = plugin.locationManager.getAllGroups();
        Map<String, Map<String, Object>> locations = plugin.locationManager.getAllLocations();
        String worldOptions = worldNames.stream().map(name -> "<option value='" + name + "'>" + name + "</option>").collect(Collectors.joining());

        StringBuilder html = new StringBuilder("<!DOCTYPE html><html lang='ja'><head><meta charset='UTF-8'><title>Remote Redstone Dashboard</title><meta name='viewport' content='width=device-width, initial-scale=1'><style>");
        html.append("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background-color:#1e1e1e;color:#e0e0e0;margin:0;padding:15px;} .container{max-width:960px;margin:0 auto;} h1,h2,h3{color:#4fc3f7;border-bottom:1px solid #444;padding-bottom:10px;margin-top:1.5em;} .search-bar{width:100%;padding:10px;margin-bottom:20px;background-color:#333;border:1px solid #555;color:#fff;border-radius:5px;box-sizing:border-box;} .group{background-color:#2a2a2a;padding:20px;border-radius:10px;margin-bottom:20px;box-shadow:0 4px 8px rgba(0,0,0,0.3);} .group-header, .switch-name-cell{display:flex;align-items:center;justify-content:space-between;gap:10px;} .group-title{display:flex;align-items:center;gap:8px;} .group-toggle{cursor:pointer;font-size:1.2em;user-select:none;width:20px;} .header-actions{display:flex;align-items:center;gap:10px;} .sub-group{margin-left:25px;margin-top:15px;padding-top:15px;border-top:1px dashed #555;}");
        // 【変更点】group-header-memoのCSSを追加
        html.append(".group-header-memo{color:#aaa;font-size:0.9em;font-style:italic;margin-left:10px;}");
        html.append(".edit-actions button{margin-left:10px;} table{width:100%;border-collapse:collapse;margin:20px 0;} th,td{padding:12px 15px;text-align:left;border-bottom:1px solid #444;} thead{background-color:#333;} .btn{padding:8px 15px;text-decoration:none;color:white;border-radius:5px;border:none;font-size:14px;cursor:pointer;transition:background-color 0.2s;} .btn-on{background-color:#43a047;} .btn-off{background-color:#d32f2f;} .btn-del{background-color:#616161;} .btn-edit{background-color:#2196f3;} .btn-save{background-color:#8bc34a;} .btn-cancel{background-color:#f44336;} .btn:hover{opacity:0.8;} .btn:disabled{background-color:#555;color:#999;cursor:not-allowed;} form{display:grid;gap:10px;} .form-grid{grid-template-columns:repeat(auto-fit,minmax(120px,1fr));} .coord-selector{grid-column:1/-1;display:flex;gap:10px;} form input,form select{width:100%;padding:10px;background-color:#333;border:1px solid #555;color:#fff;border-radius:5px;box-sizing:border-box;} form button{background-color:#0288d1;padding:12px;font-size:16px;grid-column:1/-1;} .msg{padding:12px;border-radius:5px;margin-bottom:15px;border-left:5px solid #0288d1;display:none;} footer{text-align:center;margin-top:30px;padding-top:15px;border-top:1px solid #444;color:#888;} .edit-form, .collapsed{display:none;}");
        html.append("</style></head><body><div class='container'><h1>Redstone Dashboard</h1><div id='message-box' class='msg'></div>");
        html.append("<h2>Search Groups</h2><input type='search' id='group-search' class='search-bar' placeholder='グループ名で検索...'>");
        html.append("<h2>Add New Top-Level Group</h2><form data-action='add-group'><input type='hidden' name='parentId' value=''><input name='groupName' placeholder='New Group Name' required><input name='memo' placeholder='Memo (optional)'><button type='submit' class='btn'>Create Group</button></form>");

        Map<String, List<Map.Entry<String, Map<String, Object>>>> childGroupsMap = allGroups.entrySet().stream().filter(e -> e.getValue().containsKey("parent")).collect(Collectors.groupingBy(e -> e.getValue().get("parent").toString()));
        allGroups.entrySet().stream().filter(e -> !e.getValue().containsKey("parent")).forEach(parentEntry -> html.append(renderGroup(parentEntry.getKey(), parentEntry.getValue(), locations, worldOptions, childGroupsMap)));

        html.append("<footer><p>RemoteRedstone Plugin Version: ").append(pluginVersion).append("</p></footer>");
        html.append("<script>");
        html.append("const msgBox=document.getElementById('message-box');let pollInterval=null;");
        html.append("function showMsg(txt,isErr){msgBox.textContent=txt;msgBox.style.backgroundColor=isErr?'#c0392b':'rgba(2,136,209,0.5)';msgBox.style.display='block';setTimeout(()=>msgBox.style.display='none',5000);}");
        html.append("function toggleEdit(container, state) { container.querySelector('.display-view').style.display = state ? 'none' : 'flex'; container.querySelector('.edit-form').style.display = state ? 'flex' : 'none'; }");
        html.append("function startPolling(playerName,form){if(pollInterval)clearInterval(pollInterval);let attempts=0;pollInterval=setInterval(async()=>{const res=await fetch(`/api/poll-selection?player=${encodeURIComponent(playerName)}`);const data=await res.json();if(data.status==='found'){clearInterval(pollInterval);form.querySelector('[name=world]').value=data.world;form.querySelector('[name=x]').value=data.x;form.querySelector('[name=y]').value=data.y;form.querySelector('[name=z]').value=data.z;showMsg('Coordinates received!')}attempts++;if(attempts>60){clearInterval(pollInterval);showMsg('Selection timed out.',true)}},1000)}");
        html.append("document.body.addEventListener('click', async e => { const btn = e.target; const action = btn.dataset.action; if (!action) return; e.preventDefault();");
        html.append("if (action === 'toggle-visibility') { const group = btn.closest('.group'); const content = group.querySelector('.group-content'); content.classList.toggle('collapsed'); btn.textContent = content.classList.contains('collapsed') ? '▶' : '▼'; return; }");
        html.append("if (action === 'edit-item') { const container = btn.closest('[data-editable]'); toggleEdit(container, true); return; }");
        html.append("if (action === 'cancel-edit') { const container = btn.closest('[data-editable]'); toggleEdit(container, false); return; }");
        html.append("let params = new URLSearchParams(); let url, confirmMsg;");
        html.append("if (action === 'save-group') { const container = btn.closest('[data-editable]'); params.append('groupId', container.dataset.groupId); params.append('newName', container.querySelector('[name=newName]').value); params.append('newMemo', container.querySelector('[name=newMemo]').value); url = '/api/update-group'; }");
        html.append("else if (action === 'save-switch') { const container = btn.closest('[data-editable]'); params.append('switchId', container.dataset.switchId); params.append('newName', container.querySelector('[name=newName]').value); url = '/api/update-switch'; }");
        html.append("else if(action==='request-wand'){ const form=btn.closest('form');const input=form.querySelector('[name=playerName]');if(!input.value){showMsg('Please enter your player name.',true);return}const res=await fetch(`/api/request-wand?player=${encodeURIComponent(input.value)}`);const data=await res.json();if(data.status==='success'){showMsg('Wand sent! Right-click a block in-game.');startPolling(input.value,form)}else{showMsg(data.message,true)}return}");
        html.append("else if(action==='toggle-switch'){params.append('switchId', btn.dataset.switchId); params.append('state', btn.dataset.state); url=`/api/toggle-switch`;}");
        html.append("else if(action==='remove-switch'){confirmMsg=`Delete switch '${btn.dataset.switchName}'?`; params.append('switchId', btn.dataset.switchId); url=`/api/remove-switch`;}");
        html.append("else if(action==='toggle-group'){params.append('groupId', btn.dataset.groupId); params.append('state', btn.dataset.state); url=`/api/toggle-group`;}");
        html.append("else if(action==='remove-group'){confirmMsg=`Delete group and ALL its sub-groups and switches?`; params.append('groupId', btn.dataset.groupId); url=`/api/remove-group`;}");
        html.append("else return;if(confirmMsg&&!confirm(confirmMsg))return;const res=await fetch(url,{method:'POST',body:params});const data=await res.json();if(data.status==='success'){ showMsg(data.message || 'Action successful!'); if (action.startsWith('save')) { toggleEdit(btn.closest('[data-editable]'), false); } if (action !== 'toggle-switch' && action !== 'toggle-group') { setTimeout(() => window.location.reload(), 500); } } else { showMsg(data.message,true); }});");
        html.append("document.body.addEventListener('submit',async e=>{e.preventDefault();const form=e.target;const action=form.dataset.action;if(!action)return;const formData=new FormData(form);const params=new URLSearchParams();for(const pair of formData.entries()){params.append(pair[0],pair[1])}const res=await fetch(`/api/${action}`,{method:'POST',body:params});const data=await res.json();if(data.status==='success'){window.location.reload()}else{showMsg(data.message,true)}});");
        html.append("document.getElementById('group-search').addEventListener('input', e => { const query = e.target.value.toLowerCase(); document.querySelectorAll('.group[data-group-name]').forEach(group => { const title = group.dataset.groupName.toLowerCase(); group.style.display = title.includes(query) ? '' : 'none'; }); });");
        html.append("setInterval(async () => { const switchRows = document.querySelectorAll('tr[data-switch-id]'); if (switchRows.length === 0) return; const switchIds = Array.from(switchRows).map(row => row.dataset.switchId); try { const res = await fetch('/api/get-live-states', { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(switchIds) }); if (!res.ok) return; const liveStates = await res.json(); for (const [id, isON] of Object.entries(liveStates)) { const row = document.querySelector(`tr[data-switch-id='${id}']`); if (!row) continue; const onBtn = row.querySelector('.btn-on'); const offBtn = row.querySelector('.btn-off'); if (onBtn) onBtn.disabled = isON; if (offBtn) offBtn.disabled = !isON; } } catch (error) { /* Do nothing on error */ } }, 1000);");
        html.append("</script></div></body></html>");
        return html.toString();
    }

    private String renderGroup(String groupId, Map<String, Object> groupData, Map<String, Map<String, Object>> locations, String worldOptions, Map<String, List<Map.Entry<String, Map<String, Object>>>> childGroupsMap) {
        StringBuilder h = new StringBuilder();
        String groupName = groupData.get("name").toString();
        String groupMemo = groupData.getOrDefault("memo", "").toString();
        boolean isSubGroup = groupData.containsKey("parent");
        String groupClass = isSubGroup ? "group sub-group" : "group";

        h.append("<div class='").append(groupClass).append("' id='group-").append(groupId).append("' data-group-name='").append(groupName).append("'>");
        h.append("<div data-editable data-group-id='").append(groupId).append("'>");

        h.append("<div class='group-header display-view'>");
        h.append("<div class='group-title'><span class='group-toggle' data-action='toggle-visibility'>▶</span><h3>").append(groupName).append("</h3>");
        if (!groupMemo.isEmpty()) {
            h.append("<span class='group-header-memo'> - ").append(groupMemo).append("</span>");
        }
        h.append("</div>");
        h.append("<div class='header-actions'>");
        h.append("<button class='btn btn-on' data-action='toggle-group' data-group-id='").append(groupId).append("' data-state='set'>All ON</button>");
        h.append("<button class='btn btn-off' data-action='toggle-group' data-group-id='").append(groupId).append("' data-state='clear'>All OFF</button>");
        h.append("<button class='btn btn-del' data-action='remove-group' data-group-id='").append(groupId).append("'>Delete Group</button>");
        h.append("<button class='btn btn-edit' data-action='edit-item'>Edit</button>");
        h.append("</div></div>");

        h.append("<div class='group-header edit-form'><div><input type='text' name='newName' value='").append(groupName).append("'><input type='text' name='newMemo' value='").append(groupMemo).append("' placeholder='Memo'></div><div class='edit-actions'><button class='btn btn-save' data-action='save-group'>Save</button><button class='btn btn-cancel' data-action='cancel-edit'>Cancel</button></div></div></div>");
        h.append("<div class='group-content collapsed'>");

        h.append("<table><thead><tr><th>Name</th><th>Location</th><th>Actions</th></tr></thead><tbody>");
        boolean hasSwitches = false;
        for (Map.Entry<String, Map<String, Object>> locEntry : locations.entrySet()) {
            if (groupId.equals(locEntry.getValue().get("group"))) {
                hasSwitches = true;
                String switchId = locEntry.getKey();
                Map<String, Object> loc = locEntry.getValue();
                String switchName = loc.get("name").toString();
                String storedState = (String) loc.getOrDefault("state", "OFF");
                boolean isInitiallyON = "ON".equals(storedState);
                String locStr = String.format("%s @ %s, %s, %s", loc.get("world"), loc.get("x"), loc.get("y"), loc.get("z"));
                h.append("<tr data-switch-id='").append(switchId).append("'><td data-editable data-switch-id='").append(switchId).append("'><div class='switch-name-cell display-view'><span>").append(switchName).append("</span><button class='btn btn-edit btn-sm' data-action='edit-item'>Edit</button></div><div class='edit-form'><input type='text' name='newName' value='").append(switchName).append("'><div class='edit-actions'><button class='btn btn-save' data-action='save-switch'>Save</button><button class='btn btn-cancel' data-action='cancel-edit'>Cancel</button></div></div></td><td>").append(locStr).append("</td><td>");
                h.append("<button class='btn btn-on' data-action='toggle-switch' data-switch-id='").append(switchId).append("' data-state='set' ").append(isInitiallyON ? "disabled" : "").append(">ON</button>");
                h.append("<button class='btn btn-off' data-action='toggle-switch' data-switch-id='").append(switchId).append("' data-state='clear' ").append(!isInitiallyON ? "disabled" : "").append(">OFF</button>");
                h.append("<button class='btn btn-del' data-action='remove-switch' data-switch-id='").append(switchId).append("' data-switch-name='").append(switchName).append("'>Delete</button>");
                h.append("</td></tr>");
            }
        }
        if (!hasSwitches) h.append("<tr><td colspan='3'>No switches in this group yet.</td></tr>");
        h.append("</tbody></table>");

        childGroupsMap.getOrDefault(groupId, new ArrayList<>()).forEach(childEntry -> h.append(renderGroup(childEntry.getKey(), childEntry.getValue(), locations, worldOptions, childGroupsMap)));

        h.append("<h4>Add Sub-Group to '").append(groupName).append("'</h4><form data-action='add-group'><input type='hidden' name='parentId' value='").append(groupId).append("'><input name='groupName' placeholder='New Sub-Group Name' required><input name='memo' placeholder='Memo (optional)'><button type='submit' class='btn'>Create Sub-Group</button></form>");
        h.append("<h4>Add Switch to '").append(groupName).append("'</h4><form data-action='add-switch'><input type='hidden' name='group' value='").append(groupId).append("'><input name='name' placeholder='Switch Name (e.g. メイン照明)' required><select name='world' required>").append(worldOptions).append("</select><input type='number' name='x' placeholder='X' required><input type='number' name='y' placeholder='Y' required><input type='number' name='z' placeholder='Z' required><div class='coord-selector'><input name='playerName' placeholder='Your IGN for Wand'><button type='button' class='btn' data-action='request-wand'>Select Block</button></div><button type='submit' class='btn'>Add Switch</button></form>");

        h.append("</div></div>");
        return h.toString();
    }
}