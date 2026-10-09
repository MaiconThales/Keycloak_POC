package poc.persistence.keycloak;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.StringReader;

/** Java 8/WildFly 10 compatible implementation of the OAuth client-credentials flow. */
public class KeycloakAdminHttpClient implements KeycloakAdminClient {
    private volatile String token;

    public KeycloakAdminHttpClient() { }

    @Override
    public String accessToken() {
        if (token != null) return token;
        KeycloakAdminConfiguration.requireConfigured();
        try {
            String form = form("grant_type", "client_credentials", "client_id", KeycloakAdminConfiguration.clientId(),
                    "client_secret", KeycloakAdminConfiguration.clientSecret());
            Response response = request("POST", "/realms/" + enc(KeycloakAdminConfiguration.adminRealm())
                    + "/protocol/openid-connect/token", form, "application/x-www-form-urlencoded", null);
            if (response.status < 200 || response.status >= 300) throw new KeycloakAdminException("Keycloak token request failed (HTTP " + response.status + ").");
            String value = jsonString(response.body, "access_token");
            if (value.isEmpty()) throw new KeycloakAdminException("Keycloak token response did not contain access_token.");
            token = value;
            return value;
        } catch (KeycloakAdminException e) { throw e; }
        catch (Exception e) { throw new KeycloakAdminException("Could not contact Keycloak token endpoint.", e); }
    }

    @Override
    public void validateManageUsers() {
        AdminApiResponse response = userManagement("GET", "/users?max=1", null);
        if (response.getStatus() == 403) throw new KeycloakAdminException("Configured service account lacks realm-management/manage-users in target realm.");
        if (response.getStatus() < 200 || response.getStatus() >= 300) throw new KeycloakAdminException("Keycloak Admin API permission check failed (HTTP " + response.getStatus() + ").");
    }

    @Override
    public AdminApiResponse userManagement(String method, String path, String body) {
        if (path == null || !path.startsWith("/users")) throw new IllegalArgumentException("Only target realm user-management paths are allowed.");
        try {
            Response response = request(method, "/admin/realms/" + enc(KeycloakAdminConfiguration.targetRealm()) + path,
                    body, "application/json", "Bearer " + accessToken());
            if (response.status == 401) token = null;
            return new AdminApiResponse(response.status, response.body, response.location);
        } catch (KeycloakAdminException e) { throw e; }
        catch (Exception e) { throw new KeycloakAdminException("Could not contact Keycloak Admin API.", e); }
    }

    @Override
    public KeycloakUserProfile findUser(String username) {
        if (username == null || username.trim().isEmpty()) return null;
        try {
            AdminApiResponse response = userManagement("GET", "/users?username=" + enc(username.trim()), null);
            if (response.getStatus() < 200 || response.getStatus() >= 300) return null;
            try (JsonReader reader = Json.createReader(new StringReader(response.getBody()))) {
                javax.json.JsonArray users = reader.readArray();
                if (users.isEmpty()) return null;
                JsonObject user = users.getJsonObject(0);
                List<String> actions = new ArrayList<String>();
                JsonArray actionArray = user.getJsonArray("requiredActions");
                if (actionArray != null) for (javax.json.JsonValue value : actionArray) actions.add(value.toString().replace("\"", ""));
                return new KeycloakUserProfile(user.getString("id", ""), user.getString("email", ""),
                        user.getString("firstName", ""), user.getString("lastName", ""), actions);
            }
        } catch (Exception e) { throw new KeycloakAdminException("Could not read the Keycloak user profile.", e); }
    }

    private Response request(String method, String path, String body, String contentType, String authorization) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(KeycloakAdminConfiguration.serverUrl() + path).openConnection();
        connection.setRequestMethod(method); connection.setConnectTimeout(5000); connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/json");
        if (authorization != null) connection.setRequestProperty("Authorization", authorization);
        if (body != null) { connection.setDoOutput(true); connection.setRequestProperty("Content-Type", contentType); byte[] bytes = body.getBytes("UTF-8"); connection.setFixedLengthStreamingMode(bytes.length); OutputStream out = connection.getOutputStream(); out.write(bytes); out.close(); }
        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        return new Response(status, read(stream), connection.getHeaderField("Location"));
    }
    private static String read(InputStream stream) throws Exception { if (stream == null) return ""; BufferedReader r = new BufferedReader(new InputStreamReader(stream, "UTF-8")); StringBuilder b = new StringBuilder(); String line; while ((line = r.readLine()) != null) b.append(line); r.close(); return b.toString(); }
    private static String jsonString(String json, String key) { String marker = "\"" + key + "\""; int i = json.indexOf(marker); if (i < 0) return ""; i = json.indexOf(':', i + marker.length()); if (i < 0) return ""; i = json.indexOf('"', i); if (i < 0) return ""; int end = json.indexOf('"', i + 1); return end < 0 ? "" : json.substring(i + 1, end); }
    private static String enc(String value) throws Exception { return URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
    private static String form(String... values) throws Exception { Map<String,String> map = new LinkedHashMap<String,String>(); for (int i=0;i<values.length;i+=2) map.put(values[i], values[i+1]); StringBuilder b=new StringBuilder(); for(Map.Entry<String,String> e:map.entrySet()){if(b.length()>0)b.append('&');b.append(enc(e.getKey())).append('=').append(enc(e.getValue()));} return b.toString(); }
    private static final class Response { final int status; final String body; final String location; Response(int s,String b,String l){status=s;body=b;location=l;} }
}
