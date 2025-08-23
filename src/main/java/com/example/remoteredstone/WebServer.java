package com.example.remoteredstone;

import fi.iki.elonen.NanoHTTPD;
import java.util.List;
import java.util.Map;

public class WebServer extends NanoHTTPD {

    private final RemoteRedstone plugin;

    public WebServer(int port, RemoteRedstone plugin) {
        super(port);
        this.plugin = plugin;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Map<String, List<String>> params = session.getParameters();
        if (uri.startsWith("/api/")) {
            String action = uri.substring(5);
            if ("add".equals(action)) {
                try {
                    String name = params.get("name").get(0).replaceAll("[^a-zA-Z0-9_-]", "");
                    String world = params.get("world").get(0);
                    String x = params.get("x").get(0);
                    String y = params.get("y").get(0);
                    String z = params.get("z").get(0);

                    plugin.locationManager.addLocation(name, world, Integer.parseInt(x), Integer.parseInt(y), Integer.parseInt(z));
                    plugin.setSwitchBlock(world, x, y, z, false);
                    return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\":\"success\", \"message\":\"Added '" + name + "' successfully!\"}");
                } catch (Exception e) {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"status\":\"error\", \"message\":\"Invalid input.\"}");
                }
            }
            if ("remove".equals(action)) {
                String name = params.get("name").get(0);
                Map<String, Object> loc = plugin.locationManager.getAllLocations().get(name);
                if (loc != null) {
                    plugin.removeSwitchBlock(loc.get("world").toString(), loc.get("x").toString(), loc.get("y").toString(), loc.get("z").toString());
                    plugin.locationManager.removeLocation(name);
                    return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\":\"success\", \"message\":\"Removed '" + name + "' successfully!\"}");
                }
            }
            if ("toggle".equals(action)) {
                String name = params.get("name").get(0);
                boolean newIsOnState = "set".equals(params.get("action").get(0));
                Map<String, Object> loc = plugin.locationManager.getAllLocations().get(name);
                if (loc != null) {
                    plugin.setSwitchBlock(loc.get("world").toString(), loc.get("x").toString(), loc.get("y").toString(), loc.get("z").toString(), newIsOnState);
                    plugin.locationManager.updateLocationState(name, newIsOnState ? "ON" : "OFF");
                    return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\":\"success\", \"newState\":\"" + (newIsOnState ? "ON" : "OFF") + "\"}");
                }
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{\"status\":\"error\", \"message\":\"API endpoint not found.\"}");
        }
        return newFixedLengthResponse(generateDashboard());
    }

    private String generateDashboard() {
        StringBuilder html = new StringBuilder("<!DOCTYPE html><html><head><title>Remote Redstone Dashboard</title><meta name='viewport' content='width=device-width, initial-scale=1'><style>");
        html.append("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background:#1e1e1e;color:#e0e0e0;margin:0;padding:15px;}");
        html.append(".header h1{color:#4fc3f7;margin:0;}");
        html.append(".container{max-width:900px;margin:0 auto;padding:20px;background:#2a2a2a;border-radius:10px;box-shadow:0 0 20px rgba(0,0,0,0.5);} ");
        html.append("table{width:100%;border-collapse:collapse;margin-bottom:20px;} th,td{padding:12px 15px;text-align:left;border-bottom:1px solid #444;} th{background:#333;}");
        html.append(".btn{padding:6px 12px;text-decoration:none;color:white;border-radius:5px;margin-right:5px;border:none;font-size:14px;cursor:pointer;} .on{background:#43a047;} .off{background:#d32f2f;} .del{background:#616161;} .btn:hover{opacity:0.8;}");
        html.append(".btn:disabled{background:#555;color:#999;cursor:not-allowed;}");
        html.append("h2{border-bottom:2px solid #4fc3f7;padding-bottom:10px;margin-top:30px;}");
        html.append(".form-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(120px,1fr));gap:10px;align-items:center;} form input{width:100%;padding:10px;background:#333;border:1px solid #555;color:#fff;border-radius:5px;box-sizing:border-box;}");
        html.append(".form-grid button{grid-column:1/-1;background:#0288d1;color:#fff;padding:12px;font-size:16px;}");
        html.append(".msg{background:rgba(2,136,209,0.5);padding:12px;border-radius:5px;margin-bottom:15px;border-left:5px solid #0288d1;display:none;}"); // 初期状態は非表示
        html.append("</style></head><body><div class='container'><div class='header'><h1>Redstone Dashboard</h1></div>");
        html.append("<div id='message-box' class='msg'></div>"); // メッセージ表示用のボックス

        html.append("<h2>Registered Switches</h2><table id='switch-table'><thead><tr><th>Name</th><th>Location</th><th>Actions</th></tr></thead><tbody>");
        Map<String, Map<String, Object>> locations = plugin.locationManager.getAllLocations();
        if (locations.isEmpty()) {
            html.append("<tr><td colspan='3'>No switches registered yet.</td></tr>");
        } else {
            for (Map.Entry<String, Map<String, Object>> entry : locations.entrySet()) {
                String name = entry.getKey();
                Map<String, Object> loc = entry.getValue();
                String currentState = loc.getOrDefault("state", "OFF").toString();
                boolean isON = currentState.equals("ON");
                String locStr = String.format("%s @ %s,%s,%s", loc.get("world"), loc.get("x"), loc.get("y"), loc.get("z"));
                html.append("<tr data-name='").append(name).append("'><td>").append(name).append("</td><td>").append(locStr).append("</td><td>");
                html.append("<button data-action='set' class='btn on' ").append(isON ? "disabled" : "").append(">ON</button>");
                html.append("<button data-action='clear' class='btn off' ").append(!isON ? "disabled" : "").append(">OFF</button>");
                html.append("<button data-action='remove' class='btn del'>Delete</button>");
                html.append("</td></tr>");
            }
        }
        html.append("</tbody></table>");

        html.append("<h2>Add New Switch</h2><form id='add-form' class='form-grid'>");
        html.append("<input type='text' name='name' placeholder='Switch Name' required>");
        html.append("<input type='text' name='world' placeholder='World' required>");
        html.append("<input type='number' name='x' placeholder='X' required>");
        html.append("<input type='number' name='y' placeholder='Y' required>");
        html.append("<input type='number' name='z' placeholder='Z' required>");
        html.append("<button type='submit' class='btn'>Add Switch</button></form>");

        html.append("<script>");
        html.append("const table = document.getElementById('switch-table');");
        html.append("const addForm = document.getElementById('add-form');");
        html.append("const msgBox = document.getElementById('message-box');");

        html.append("function showMessage(text, isError = false) { msgBox.textContent = text; msgBox.style.display = 'block'; msgBox.style.backgroundColor = isError ? '#c0392b' : 'rgba(2,136,209,0.5)'; setTimeout(() => { msgBox.style.display = 'none'; }, 3000); }");

        html.append("table.addEventListener('click', async (e) => {");
        html.append("  if (e.target.tagName !== 'BUTTON') return;");
        html.append("  e.preventDefault();");
        html.append("  const button = e.target;");
        html.append("  const row = button.closest('tr');");
        html.append("  const name = row.dataset.name;");
        html.append("  const action = button.dataset.action;");

        html.append("  if (action === 'remove' && !confirm(`Delete '${name}' permanently?`)) return;");

        html.append("  let url = (action === 'remove') ? `/api/remove?name=${name}` : `/api/toggle?name=${name}&action=${action}`;");
        html.append("  const response = await fetch(url);");
        html.append("  const data = await response.json();");

        html.append("  if (data.status === 'success') {");
        html.append("    if (action === 'remove') { row.remove(); }");
        html.append("    else {");
        html.append("      const isNowOn = data.newState === 'ON';");
        html.append("      row.querySelector('.on').disabled = isNowOn;");
        html.append("      row.querySelector('.off').disabled = !isNowOn;");
        html.append("    }");
        html.append("  }");
        html.append("});");

        html.append("addForm.addEventListener('submit', async (e) => {");
        html.append("  e.preventDefault();");
        html.append("  const formData = new FormData(addForm);");
        html.append("  const params = new URLSearchParams(formData);");
        html.append("  const response = await fetch(`/api/add?${params.toString()}`);");
        html.append("  const data = await response.json();");

        html.append("  if (data.status === 'success') { window.location.reload(); }");
        html.append("  else { showMessage(data.message, true); }");
        html.append("});");

        html.append("</script>");

        html.append("</div></body></html>");
        return html.toString();
    }
}