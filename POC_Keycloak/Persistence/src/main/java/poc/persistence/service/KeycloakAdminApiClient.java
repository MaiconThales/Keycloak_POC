package poc.persistence.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

final class KeycloakAdminApiClient {
    private static final Logger LOGGER = Logger.getLogger(KeycloakAdminApiClient.class.getName());
    private static final int CONNECT_TIMEOUT_MILLIS = 3000;
    private static final int READ_TIMEOUT_MILLIS = 5000;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final int PAGE_SIZE = 100;
    private static final java.lang.String USERNAME = "username";
    private static final java.lang.String EMAIL = "email";
    private static final java.lang.String USERS = "/users";
    private static final java.lang.String USERS1 = "/users/";
    private static final java.lang.String ADMIN_REALMS = "/admin/realms/";
    private static final java.lang.String UTF_8 = "UTF-8";

    private final String adminApi;
    private final String clientId;
    private final String clientSecret;

    KeycloakAdminApiClient() {
        this(System.getProperty("keycloak.issuer", ""),
                System.getenv("KEYCLOAK_ADMIN_CLIENT_ID"),
                System.getenv("KEYCLOAK_ADMIN_CLIENT_SECRET"));
    }

    KeycloakAdminApiClient(String issuer, String clientId, String clientSecret) {
        this.adminApi = adminApiFromIssuer(issuer);
        this.clientId = requiredConfiguration(clientId);
        this.clientSecret = requiredConfiguration(clientSecret);
    }

    List<UserProfile> findAll() {
        List<UserProfile> users = new ArrayList<UserProfile>();
        int first = 0;
        while (true) {
            JsonArray page = get("/?first=" + first + "&max=" + PAGE_SIZE);
            for (JsonValue value : page) {
                if (!(value instanceof JsonObject)) {
                    throw failure(Response.Status.BAD_GATEWAY);
                }
                JsonObject user = (JsonObject) value;
                users.add(new UserProfile(stringField(user, "id"),
                        stringField(user, USERNAME), stringField(user, EMAIL)));
            }
            if (page.size() < PAGE_SIZE) {
                LOGGER.log(Level.INFO, "Loaded {0} users from Keycloak.", users.size());
                return users;
            }
            first += page.size();
        }
    }

    UserProfile create(String username, String email) {
        JsonObject user = Json.createObjectBuilder()
                .add(USERNAME, username)
                .add(EMAIL, email)
                .add("enabled", true)
                .build();
        HttpResponse response = request("POST", USERS, user, 201);
        String id = identifierFromLocation(response.location);
        LOGGER.info("Created a Keycloak user.");
        return new UserProfile(id, username, email);
    }

    UserProfile update(String id, String username, String email) {
        JsonObject user = Json.createObjectBuilder()
                .add(USERNAME, username)
                .add(EMAIL, email)
                .build();
        request("PUT", USERS1 + encodePathSegment(id), user, 204);
        LOGGER.info("Updated a Keycloak user.");
        return new UserProfile(id, username, email);
    }

    void delete(String id) {
        request("DELETE", USERS1 + encodePathSegment(id), null, 204);
        LOGGER.info("Deleted a Keycloak user.");
    }

    private JsonArray get(String path) {
        HttpResponse response = request("GET", path, null, 200);
        try (JsonReader reader = Json.createReader(
                new java.io.ByteArrayInputStream(response.body))) {
            return reader.readArray();
        } catch (javax.json.JsonException | ClassCastException e) {
            throw failure(Response.Status.BAD_GATEWAY);
        }
    }

    private HttpResponse request(String method, String path, JsonObject body, int expectedStatus) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URI(adminApi + path).toURL().openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + requestAccessToken());
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 400
                    ? connection.getErrorStream() : connection.getInputStream();
            byte[] responseBody = stream == null ? new byte[0] : readResponse(stream);
            if (status != expectedStatus) {
                LOGGER.log(Level.WARNING, "Keycloak Admin API {0} {1} returned HTTP {2}.",
                        new Object[]{method, logPath(path), status});
                throw new KeycloakDirectoryException(statusFor(status));
            }
            LOGGER.log(Level.FINE, "Keycloak Admin API {0} {1} succeeded with HTTP {2}.",
                    new Object[]{method, logPath(path), status});
            return new HttpResponse(responseBody, connection.getHeaderField("Location"));
        } catch (WebApplicationException e) {
            throw e;
        } catch (IOException | java.net.URISyntaxException e) {
            LOGGER.log(Level.WARNING,
                    "Keycloak Admin API request failed due to a connection or I/O error.", e);
            throw new KeycloakDirectoryException(Response.Status.SERVICE_UNAVAILABLE);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String requestAccessToken() {
        HttpURLConnection connection = null;
        try {
            String endpoint = adminApi.substring(0, adminApi.indexOf("/admin/"))
                    + tokenPath();
            connection = (HttpURLConnection) new URI(endpoint).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded; charset=UTF-8");

            byte[] form = ("grant_type=client_credentials&client_id=" + encodeForm(clientId)
                    + "&client_secret=" + encodeForm(clientSecret)).getBytes(StandardCharsets.UTF_8);

            try (OutputStream output = connection.getOutputStream()) {
                output.write(form);
            }

            int status = connection.getResponseCode();

            byte[] response = readResponse(status >= 400
                    ? connection.getErrorStream() : connection.getInputStream());

            if (status != 200) {
                LOGGER.log(Level.WARNING,
                        "Keycloak service-account token request returned HTTP {0}.", status);
                throw new KeycloakDirectoryException(Response.Status.BAD_GATEWAY);
            }

            try (JsonReader reader = Json.createReader(
                    new java.io.ByteArrayInputStream(response))) {
                String token = reader.readObject().getString("access_token", "");
                if (token.isEmpty()) {
                    throw failure(Response.Status.BAD_GATEWAY);
                }
                return token;
            } catch (javax.json.JsonException | ClassCastException e) {
                throw failure(Response.Status.BAD_GATEWAY);
            }
        } catch (WebApplicationException e) {
            throw e;
        } catch (IOException | java.net.URISyntaxException e) {
            LOGGER.log(Level.WARNING,
                    "Keycloak service-account token request failed due to a connection or I/O error.", e);
            throw new KeycloakDirectoryException(Response.Status.SERVICE_UNAVAILABLE);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String tokenPath() {
        String realmPath = adminApi.substring(adminApi.indexOf(ADMIN_REALMS));
        String realm = realmPath.substring(ADMIN_REALMS.length(),
                realmPath.lastIndexOf(USERS));
        return "/realms/" + realm + "/protocol/openid-connect/token";
    }

    private String adminApiFromIssuer(String issuer) {
        if (issuer == null || issuer.trim().isEmpty()) {
            throw failure(Response.Status.SERVICE_UNAVAILABLE);
        }
        try {
            URI uri = new URI(issuer.trim());
            String path = uri.getPath();
            int realmMarker = path == null ? -1 : path.lastIndexOf("/realms/");
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || realmMarker < 0 || realmMarker + 8 >= path.length()
                    || path.substring(realmMarker + 8).contains("/")
                    || !("https".equalsIgnoreCase(uri.getScheme())
                            || ("http".equalsIgnoreCase(uri.getScheme()) && isLoopback(uri.getHost())))) {
                throw failure(Response.Status.SERVICE_UNAVAILABLE);
            }
            String serverBase = uri.getScheme() + "://" + uri.getRawAuthority()
                    + path.substring(0, realmMarker);
            String realm = path.substring(realmMarker + 8);
            return serverBase + ADMIN_REALMS + encodePathSegment(realm) + USERS;
        } catch (java.net.URISyntaxException e) {
            LOGGER.log(Level.WARNING, "Keycloak issuer configuration is not a valid URI.", e);
            throw new KeycloakDirectoryException(Response.Status.SERVICE_UNAVAILABLE);
        }
    }

    private boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "::1".equals(host) || "[::1]".equals(host);
    }

    private String requiredConfiguration(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw failure(Response.Status.SERVICE_UNAVAILABLE);
        }
        return value.trim();
    }

    private byte[] readResponse(InputStream stream) throws IOException {
        if (stream == null) {
            return new byte[0];
        }
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) {
                    LOGGER.warning("Keycloak response exceeded the configured size limit.");
                    throw failure(Response.Status.BAD_GATEWAY);
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private String identifierFromLocation(String location) {
        if (location == null || location.trim().isEmpty()) {
            throw failure(Response.Status.BAD_GATEWAY);
        }
        try {
            String path = new URI(location).getPath();
            int slash = path == null ? -1 : path.lastIndexOf('/');
            if (slash < 0 || slash == path.length() - 1) {
                throw failure(Response.Status.BAD_GATEWAY);
            }
            return java.net.URLDecoder.decode(path.substring(slash + 1), UTF_8);
        } catch (java.net.URISyntaxException e) {
            throw failure(Response.Status.BAD_GATEWAY);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is required by the Java runtime.", e);
        }
    }

    private String stringField(JsonObject object, String name) {
        JsonValue value = object.get(name);
        if (value == null || value.getValueType() == JsonValue.ValueType.NULL) {
            return "";
        }
        if (!(value instanceof JsonString)) {
            throw failure(Response.Status.BAD_GATEWAY);
        }
        return ((JsonString) value).getString();
    }

    private String encodeForm(String value) throws IOException {
        return URLEncoder.encode(value, UTF_8);
    }

    private String encodePathSegment(String value) {
        try {
            return URLEncoder.encode(value, UTF_8).replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is required by the Java runtime.", e);
        }
    }

    private Response.Status statusFor(int status) {
        if (status == 400) return Response.Status.BAD_REQUEST;
        if (status == 404) return Response.Status.NOT_FOUND;
        if (status == 409) return Response.Status.CONFLICT;
        if (status == 401 || status == 403) return Response.Status.BAD_GATEWAY;
        return Response.Status.BAD_GATEWAY;
    }

    private String logPath(String path) {
        int queryIndex = path.indexOf('?');
        String pathWithoutQuery = queryIndex < 0 ? path : path.substring(0, queryIndex);
        String userPrefix = USERS1;
        int userIdIndex = pathWithoutQuery.indexOf(userPrefix);
        if (userIdIndex >= 0) {
            return pathWithoutQuery.substring(0, userIdIndex + userPrefix.length()) + "{id}";
        }
        return pathWithoutQuery;
    }

    private WebApplicationException failure(Response.Status status) {
        LOGGER.log(Level.WARNING, "Keycloak user-directory operation failed with HTTP {0}.",
                status.getStatusCode());
        return new KeycloakDirectoryException(status);
    }

    private static final class HttpResponse {
        private final byte[] body;
        private final String location;

        private HttpResponse(byte[] body, String location) {
            this.body = body;
            this.location = location;
        }
    }
}
