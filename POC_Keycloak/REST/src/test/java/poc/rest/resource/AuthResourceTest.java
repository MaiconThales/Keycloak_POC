package poc.rest.resource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import poc.rest.dto.ErrorResponse;
import poc.rest.dto.LoginRequest;
import poc.rest.dto.LoginResponse;

import static org.junit.jupiter.api.Assertions.*;

class AuthResourceTest {
    private static final String ISSUER_PROPERTY = "keycloak.issuer";
    private static final String CLIENT_ID_PROPERTY = "keycloak.client-id";

    private AuthResource resource;
    private HttpServer server;
    private int responseStatus;
    private String responseBody;
    private String receivedForm;
    private String originalIssuer;
    private String originalClientId;

    @BeforeEach
    void startTokenEndpoint() throws IOException {
        originalIssuer = System.getProperty(ISSUER_PROPERTY);
        originalClientId = System.getProperty(CLIENT_ID_PROPERTY);
        resource = new AuthResource(() -> "test-secret");
        responseStatus = 200;
        responseBody = "{\"access_token\":\"test-token\",\"expires_in\":300,\"token_type\":\"Bearer\"}";
        receivedForm = null;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/protocol/openid-connect/token", exchange -> {
            try (InputStream input = exchange.getRequestBody();
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                receivedForm = new String(output.toByteArray(), StandardCharsets.UTF_8);
                byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(responseStatus, body.length);
                exchange.getResponseBody().write(body);
            } finally {
                exchange.close();
            }
        });
        server.start();
        System.setProperty(ISSUER_PROPERTY, issuer() + "/");
        System.setProperty(CLIENT_ID_PROPERTY, "test client");
    }

    @AfterEach
    void stopTokenEndpoint() {
        server.stop(0);
        restoreProperty(ISSUER_PROPERTY, originalIssuer);
        restoreProperty(CLIENT_ID_PROPERTY, originalClientId);
    }

    @Test
    void rejectsMissingOrBlankCredentialsWithoutCallingKeycloak() {
        assertError(resource.login(null), 400);
        assertError(resource.login(request(" ", "password")), 400);
        assertError(resource.login(request("user", "")), 400);
        assertNull(receivedForm);
    }

    @Test
    void rejectsMissingKeycloakConfiguration() {
        System.clearProperty(ISSUER_PROPERTY);
        assertError(resource.login(request("user", "password")), 503);
        assertNull(receivedForm);

        System.setProperty(ISSUER_PROPERTY, issuer());
        System.setProperty(CLIENT_ID_PROPERTY, "  ");
        assertError(resource.login(request("user", "password")), 503);

        System.setProperty(CLIENT_ID_PROPERTY, "test client");
        assertError(new AuthResource(() -> null).login(request("user", "password")), 503);
    }

    @Test
    void exchangesCredentialsAndReturnsTokenDetails() {
        javax.ws.rs.core.Response response = resource.login(request("test user", "test password"));

        assertEquals(200, response.getStatus());
        LoginResponse token = (LoginResponse) response.getEntity();
        assertEquals("test-token", token.getAccessToken());
        assertEquals(300L, token.getExpiresIn());
        assertEquals("Bearer", token.getTokenType());
        assertTrue(receivedForm.contains("grant_type=password"));
        assertTrue(receivedForm.contains("client_id=test+client"));
        assertTrue(receivedForm.contains("username=test+user"));
        assertTrue(receivedForm.contains("client_secret=test-secret"));
        assertTrue(receivedForm.contains("password=test+password"));
    }

    @Test
    void defaultsMissingOptionalTokenFields() {
        responseBody = "{\"access_token\":\"test-token\"}";

        LoginResponse token = (LoginResponse) resource.login(request("user", "password")).getEntity();

        assertEquals(0L, token.getExpiresIn());
        assertEquals("Bearer", token.getTokenType());
    }

    @Test
    void mapsCredentialRejectionAndClientConfigurationErrors() {
        responseStatus = 400;
        responseBody = "{\"error\":\"invalid_grant\"}";
        assertError(resource.login(request("user", "password")), 401);

        responseStatus = 403;
        responseBody = "{\"error\":\"access_denied\"}";
        assertError(resource.login(request("user", "password")), 502);
    }

    @Test
    void mapsUpstreamAndMalformedResponsesToGatewayErrors() {
        responseStatus = 500;
        assertError(resource.login(request("user", "password")), 503);

        responseStatus = 200;
        responseBody = "{}";
        assertError(resource.login(request("user", "password")), 502);

        responseBody = "not-json";
        assertError(resource.login(request("user", "password")), 502);
    }

    @Test
    void rejectsWrongTokenFieldTypes() {
        responseBody = "{\"access_token\":\"test-token\",\"expires_in\":\"later\"}";

        assertError(resource.login(request("user", "password")), 502);
    }

    private LoginRequest request(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private String issuer() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void assertError(javax.ws.rs.core.Response response, int status) {
        assertEquals(status, response.getStatus());
        assertTrue(response.getEntity() instanceof ErrorResponse);
        assertEquals(status, ((ErrorResponse) response.getEntity()).getStatusCode());
    }

    private void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
