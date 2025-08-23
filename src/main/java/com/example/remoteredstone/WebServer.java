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

        if ("/add".equals(uri)) {
            try {
                String name = params.get("name").get(0).replaceAll("[^a-zA-Z0-9_-]", "");
                String world = params.get("world").get(0);
                int x = Integer.parseInt(params.get("x").get(0));
                int y = Integer.parseInt(params.get("y").get(0));
                int z = Integer.parseInt(params.get("z").get(0));

                plugin.locationManager.addLocation(name, world, x, y, z);
                return redirect("/?message=Added '" + name + "' successfully!");
            } catch (Exception e) {
                return redirect("/?message=Error: Invalid input.");
            }
        }
        if ("/remove".equals(uri)) {
            String name = params.get("name").get(0); // ← .get(0) を追加
            plugin.locationManager.removeLocation(name);
            return redirect("/?message=Removed '" + name + "' successfully!");
        }
        if ("/toggle".equals(uri)) {
            String name = params.get("name").get(0); // ← .get(0) を追加
            boolean set = "set".equals(params.get("action").get(0)); // ← .get(0) を追加
            Map<String, Object> loc = plugin.locationManager.getAllLocations().get(name);
            if (loc != null) {
                plugin.setRedstoneBlock(loc.get("world").toString(), loc.get("x").toString(), loc.get("y").toString(), loc.get("z").toString(), set);
                return redirect("/?message=Toggled '" + name + "' to " + (set ? "ON" : "OFF"));
            }
            return redirect("/?message=Error: Location not found.");
        }

        List<String> messageList = params.get("message");
        String message = (messageList != null && !messageList.isEmpty()) ? messageList.get(0) : null;
        return newFixedLengthResponse(generateDashboard(message));
    }

    private Response redirect(String url) {
        Response res = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "");
        res.addHeader("Location", url);
        return res;
    }

    private String generateDashboard(String message) {
        if (message == null) message = "";
        StringBuilder html = new StringBuilder("<!DOCTYPE html><html><head><title>Remote Redstone Dashboard</title><meta name='viewport' content='width=device-width, initial-scale=1'><style>");
        html.append("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background:#1e1e1e;color:#e0e0e0;margin:0;padding:15px;}");
        html.append(".header{display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;} .header h1{color:#4fc3f7;margin:0;}");
        html.append(".container{max-width:900px;margin:0 auto;padding:20px;background:#2a2a2a;border-radius:10px;box-shadow:0 0 20px rgba(0,0,0,0.5);} ");
        html.append("table{width:100%;border-collapse:collapse;margin-bottom:20px;} th,td{padding:12px 15px;text-align:left;border-bottom:1px solid #444;} th{background:#333;}");
        html.append(".btn{padding:6px 12px;text-decoration:none;color:white;border-radius:5px;margin-right:5px;border:none;font-size:14px;cursor:pointer;} .on{background:#43a047;} .off{background:#d32f2f;} .del{background:#616161;} .btn:hover{opacity:0.8;}");
        html.append("h2{border-bottom:2px solid #4fc3f7;padding-bottom:10px;margin-top:30px;}");
        html.append(".form-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(120px,1fr));gap:10px;align-items:center;} form input{width:100%;padding:10px;background:#333;border:1px solid #555;color:#fff;border-radius:5px;box-sizing:border-box;}");
        html.append(".form-grid button{grid-column:1/-1;background:#0288d1;color:#fff;padding:12px;font-size:16px;}");
        html.append(".msg{background:rgba(2,136,209,0.5);padding:12px;border-radius:5px;margin-bottom:15px;border-left:5px solid #0288d1;}");
        html.append("</style></head><body><div class='container'><div class='header'><h1>Redstone Dashboard</h1></div>");
        if (!message.isEmpty()) html.append("<div class='msg'>").append(message).append("</div>");

        html.append("<h2>Registered Switches</h2><table><tr><th>Name</th><th>Location</th><th>Actions</th></tr>");
        Map<String, Map<String, Object>> locations = plugin.locationManager.getAllLocations();
        if (locations.isEmpty()) {
            html.append("<tr><td colspan='3'>No switches registered yet.</td></tr>");
        } else {
            for (Map.Entry<String, Map<String, Object>> entry : locations.entrySet()) {
                String name = entry.getKey();
                Map<String, Object> loc = entry.getValue();
                String locStr = String.format("%s @ %s,%s,%s", loc.get("world"), loc.get("x"), loc.get("y"), loc.get("z"));
                html.append("<tr><td>").append(name).append("</td><td>").append(locStr).append("</td><td>");
                html.append("<a href='/toggle?name=").append(name).append("&action=set' class='btn on'>ON</a>");
                html.append("<a href='/toggle?name=").append(name).append("&action=clear' class='btn off'>OFF</a>");
                html.append("<a href='/remove?name=").append(name).append("' class='btn del' onclick='return confirm(\"Delete \\'").append(name).append("\\' permanently?\")'>Delete</a>");
                html.append("</td></tr>");
            }
        }
        html.append("</table>");

        html.append("<h2>Add New Switch</h2><form action='/add' method='get' class='form-grid'>");
        html.append("<input type='text' name='name' placeholder='Switch Name' required>");
        html.append("<input type='text' name='world' placeholder='World' required>");
        html.append("<input type='number' name='x' placeholder='X' required>");
        html.append("<input type='number' name='y' placeholder='Y' required>");
        html.append("<input type='number' name='z' placeholder='Z' required>");
        html.append("<button type='submit' class='btn'>Add Switch</button></form>");
        html.append("</div></body></html>");
        return html.toString();
    }
}