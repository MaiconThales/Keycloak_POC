package poc.rest.resource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import poc.rest.dto.LoginRequest;
import poc.rest.dto.LoginResponse;
import poc.rest.dto.ErrorResponse;
import poc.rest.dto.PendingRegistrationResponse;
import poc.persistence.keycloak.KeycloakAdminService;
import poc.persistence.keycloak.KeycloakUserProfile;
import javax.ejb.EJB;
import poc.rest.security.PendingRegistrationTicketService;

@Path("auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final int CONNECT_TIMEOUT_MILLIS = 3000;
    private static final int READ_TIMEOUT_MILLIS = 5000;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private final Supplier<String> clientSecretProvider;
    @EJB
    private KeycloakAdminService adminService;
    @EJB
    private PendingRegistrationTicketService ticketService;

    public AuthResource() {
        this(() -> System.getenv("KEYCLOAK_CLIENT_SECRET"));
    }

    AuthResource(Supplier<String> clientSecretProvider) {
        this.clientSecretProvider = Objects.requireNonNull(clientSecretProvider);
    }

    AuthResource(Supplier<String> clientSecretProvider, KeycloakAdminService adminService) {
        this(clientSecretProvider);
        this.adminService = adminService;
        this.ticketService = new PendingRegistrationTicketService();
    }

    @POST
    @Path("login")
    public Response login(@Valid LoginRequest request) {
        if (request == null || isBlank(request.getUsername())
                || request.getPassword() == null || request.getPassword().isEmpty()) {
            return error(Response.Status.BAD_REQUEST, "Username and password are required.");
        }

        String issuer = trimTrailingSlash(System.getProperty("keycloak.issuer", "").trim());
        String clientId = System.getProperty("keycloak.client-id", "").trim();
        if (clientId.isEmpty()) {
            clientId = System.getenv("KEYCLOAK_CLIENT_ID");
            clientId = clientId == null ? "" : clientId.trim();
        }
        String clientSecret = clientSecretProvider.get();
        if (issuer.isEmpty() || clientId.isEmpty()
                || clientSecret == null || clientSecret.isEmpty()) {
            return error(Response.Status.SERVICE_UNAVAILABLE,
                    "Keycloak client configuration is incomplete.");
        }

        try {
            JsonObject token = requestToken(issuer, clientId, clientSecret, request);
            String accessToken = token.getString("access_token", "");
            if (accessToken.isEmpty()) {
                return error(Response.Status.BAD_GATEWAY,
                        "Keycloak returned a response without an access token.");
            }
            long expiresIn = token.containsKey("expires_in")
                    ? token.getJsonNumber("expires_in").longValue() : 0L;
            String tokenType = token.getString("token_type", "Bearer");
            return Response.ok(new LoginResponse(accessToken, expiresIn, tokenType)).build();
        } catch (InvalidCredentialsException e) {
            if (isRequiredActionError(e.response)) {
                Response pending = pendingRegistration(request.getUsername(), e.response);
                if (pending != null) return pending;
            }
            return error(Response.Status.UNAUTHORIZED, "Invalid username or password.");
        } catch (KeycloakConfigurationException e) {
            return error(Response.Status.BAD_GATEWAY, "Keycloak rejected the client configuration.");
        } catch (IOException e) {
            return error(Response.Status.SERVICE_UNAVAILABLE,
                    "Keycloak authentication service is unavailable.");
        } catch (javax.json.JsonException | ClassCastException e) {
            return error(Response.Status.BAD_GATEWAY,
                    "Keycloak returned an invalid authentication response.");
        }
    }

    private JsonObject requestToken(String issuer, String clientId, String clientSecret,
            LoginRequest request)
            throws IOException, InvalidCredentialsException, KeycloakConfigurationException {
        URL endpoint = new URL(issuer + "/protocol/openid-connect/token");
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        connection.setRequestProperty("Accept", MediaType.APPLICATION_JSON);

        StringBuilder form = new StringBuilder()
                .append("grant_type=password")
                .append("&client_id=").append(encode(clientId))
                .append("&username=").append(encode(request.getUsername()))
                .append("&password=").append(encode(request.getPassword()));
        if (clientSecret != null && !clientSecret.isEmpty()) {
            form.append("&client_secret=").append(encode(clientSecret));
        }

        try {
            byte[] body = form.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }

            int status = connection.getResponseCode();
            InputStream responseStream = status >= 400
                    ? connection.getErrorStream() : connection.getInputStream();
            JsonObject response = responseStream == null
                    ? Json.createObjectBuilder().build()
                    : readJson(responseStream);

            if (status == HttpURLConnection.HTTP_BAD_REQUEST
                    && "invalid_grant".equals(response.getString("error", ""))) {
                throw new InvalidCredentialsException(response);
            }
            if (status == HttpURLConnection.HTTP_BAD_REQUEST
                    || status == HttpURLConnection.HTTP_UNAUTHORIZED
                    || status == HttpURLConnection.HTTP_FORBIDDEN) {
                throw new KeycloakConfigurationException();
            }
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Keycloak token endpoint returned HTTP " + status + ".");
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private JsonObject readJson(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("Keycloak response exceeds the size limit.");
                }
                output.write(buffer, 0, count);
            }
            try (JsonReader reader = Json.createReader(
                    new java.io.ByteArrayInputStream(output.toByteArray()))) {
                return reader.readObject();
            }
        }
    }

    private String encode(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private Response error(Response.Status status, String message) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorResponse(status.getStatusCode(), message))
                .build();
    }

    private Response pendingRegistration(String username, JsonObject errorResponse) {
        if (adminService == null) return null;
        try {
            KeycloakUserProfile user = adminService.findUser(username);
            if (user == null || user.getRequiredActions().isEmpty()) return null;
            List<String> actions = new ArrayList<String>(user.getRequiredActions());
            java.util.Map<String, String> missing = new LinkedHashMap<String, String>();
            if (actions.contains("UPDATE_PROFILE")) {
                addMissing(missing, "firstName", user.getFirstName());
                addMissing(missing, "lastName", user.getLastName());
                addMissing(missing, "email", user.getEmail());
            }
            return Response.status(Response.Status.FORBIDDEN).type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(new PendingRegistrationResponse("User profile requires completion.", actions, missing,
                            ticketService.issue(username))).build();
        } catch (RuntimeException ignored) {
            // Do not turn an Admin API failure into a disclosure or a successful login.
            return null;
        }
    }

    private void addMissing(java.util.Map<String, String> missing, String name, String value) {
        if (isBlank(value)) missing.put(name, "Obrigatório");
    }

    private boolean isRequiredActionError(JsonObject response) {
        if (response == null) return false;
        String description = response.getString("error_description", "").toLowerCase(java.util.Locale.ENGLISH);
        return description.contains("required action") || description.contains("required actions")
                || description.contains("not fully set up") || description.contains("not fully setup");
    }

    private static final class InvalidCredentialsException extends Exception {
        private static final long serialVersionUID = 1L;
        private final JsonObject response;
        InvalidCredentialsException(JsonObject response) { this.response = response; }
    }

    private static final class KeycloakConfigurationException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
