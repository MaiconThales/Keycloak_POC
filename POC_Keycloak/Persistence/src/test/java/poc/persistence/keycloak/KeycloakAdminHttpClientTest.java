package poc.persistence.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class KeycloakAdminHttpClientTest {
    private HttpServer server;

    @AfterEach
    void cleanup() {
        if (server != null) server.stop(0);
        System.clearProperty(KeycloakAdminConfiguration.CLIENT_ID_PROPERTY);
        System.clearProperty(KeycloakAdminConfiguration.CLIENT_SECRET_PROPERTY);
        System.clearProperty(KeycloakAdminConfiguration.SERVER_URL_PROPERTY);
    }

    @Test
    void obtainsClientCredentialsAndCallsTargetRealm() throws Exception {
        System.setProperty(KeycloakAdminConfiguration.CLIENT_ID_PROPERTY, "svc");
        System.setProperty(KeycloakAdminConfiguration.CLIENT_SECRET_PROPERTY, "not-logged");
        start(exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/token")) reply(exchange, 200, "{\"access_token\":\"token-value\"}");
            else { assertEquals("Bearer token-value", exchange.getRequestHeaders().getFirst("Authorization")); reply(exchange, 200, "[]"); }
        });
        KeycloakAdminHttpClient client = new KeycloakAdminHttpClient();
        client.validateManageUsers();
    }

    @Test
    void reportsMissingManageUsersWithoutExposingSecret() throws Exception {
        System.setProperty(KeycloakAdminConfiguration.CLIENT_ID_PROPERTY, "svc");
        System.setProperty(KeycloakAdminConfiguration.CLIENT_SECRET_PROPERTY, "secret-value");
        start(exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/token")) reply(exchange, 200, "{\"access_token\":\"token\"}");
            else reply(exchange, 403, "forbidden");
        });
        KeycloakAdminException error = assertThrows(KeycloakAdminException.class,
                () -> new KeycloakAdminHttpClient().validateManageUsers());
        assertEquals("Configured service account lacks realm-management/manage-users in target realm.", error.getMessage());
    }

    @Test
    void failsClosedWhenCredentialsAreAbsent() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new KeycloakAdminHttpClient().accessToken());
        assertEquals("Keycloak Admin API credentials are not configured at runtime.", error.getMessage());
    }

    private void start(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", handler);
        server.start();
        System.setProperty(KeycloakAdminConfiguration.SERVER_URL_PROPERTY, "http://localhost:" + server.getAddress().getPort());
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
