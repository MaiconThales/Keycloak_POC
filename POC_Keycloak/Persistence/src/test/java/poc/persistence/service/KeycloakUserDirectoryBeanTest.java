package poc.persistence.service;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.security.RolesAllowed;
import javax.ejb.ApplicationException;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.ws.rs.WebApplicationException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeycloakUserDirectoryBeanTest {
    private HttpServer server;
    private String issuer;
    private AtomicReference<String> lastPath;
    private AtomicReference<String> lastBody;
    private AtomicBoolean rejectToken;
    private AtomicBoolean notFound;

    @BeforeEach
    void setUp() throws IOException {
        lastPath = new AtomicReference<String>();
        lastBody = new AtomicReference<String>();
        rejectToken = new AtomicBoolean();
        notFound = new AtomicBoolean();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        issuer = "http://localhost:" + server.getAddress().getPort() + "/realms/poc-keycloak";
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void registersConcreteStatelessLocalBeanForEjbInjection() throws Exception {
        assertNotNull(KeycloakUserDirectoryBean.class.getAnnotation(Stateless.class));
        assertArrayEquals(new Class<?>[]{UserDirectoryLocal.class},
                KeycloakUserDirectoryBean.class.getAnnotation(Local.class).value());
        assertArrayEquals(new String[]{"Admin", "Admin-Read"},
                KeycloakUserDirectoryBean.class.getMethod("findAll")
                        .getAnnotation(RolesAllowed.class).value());
        assertArrayEquals(new String[]{"Admin", "Admin-Write"},
                KeycloakUserDirectoryBean.class.getMethod("create", String.class, String.class)
                        .getAnnotation(RolesAllowed.class).value());
        assertArrayEquals(new String[]{"Admin", "Admin-Update"},
                KeycloakUserDirectoryBean.class
                        .getMethod("update", String.class, String.class, String.class)
                        .getAnnotation(RolesAllowed.class).value());
        assertArrayEquals(new String[]{"Admin", "Admin-Delete"},
                KeycloakUserDirectoryBean.class.getMethod("delete", String.class)
                        .getAnnotation(RolesAllowed.class).value());
        assertTrue(KeycloakDirectoryException.class
                .getAnnotation(ApplicationException.class).rollback());
    }

    @Test
    void listsAllPagesAndPerformsUserCrudAgainstKeycloakAdminApi() {
        KeycloakAdminApiClient client = client();

        List<UserProfile> users = client.findAll();
        assertEquals(101, users.size());
        assertEquals("user-0", users.get(0).getUsername());
        assertEquals("user-100", users.get(100).getUsername());

        UserProfile created = client.create("new-user", "new@example.test");
        assertEquals("user id", created.getId());
        assertEquals("new-user", created.getUsername());
        assertTrue(lastBody.get().contains("\"enabled\":true"));

        UserProfile updated = client.update("user/id", "renamed", "new@example.test");
        assertEquals("user/id", updated.getId());
        assertEquals("renamed", updated.getUsername());
        assertTrue(lastPath.get().endsWith("/users/user%2Fid"));

        client.delete("user id");
        assertTrue(lastPath.get().endsWith("/users/user%20id"));
    }

    @Test
    void translatesKeycloakAndConfigurationFailuresToSafeHttpStatuses() {
        notFound.set(true);
        WebApplicationException missing = assertThrows(WebApplicationException.class,
                () -> client().findAll());
        assertEquals(404, missing.getResponse().getStatus());

        notFound.set(false);
        rejectToken.set(true);
        WebApplicationException credentials = assertThrows(WebApplicationException.class,
                () -> client().findAll());
        assertEquals(502, credentials.getResponse().getStatus());

        WebApplicationException missingConfig = assertThrows(WebApplicationException.class,
                () -> new KeycloakAdminApiClient(issuer, "", "secret"));
        assertEquals(503, missingConfig.getResponse().getStatus());
        WebApplicationException insecureRemote = assertThrows(WebApplicationException.class,
                () -> new KeycloakAdminApiClient("http://keycloak.example/realms/test",
                        "client", "secret"));
        assertEquals(503, insecureRemote.getResponse().getStatus());
    }

    private KeycloakAdminApiClient client() {
        return new KeycloakAdminApiClient(issuer, "service-account", "runtime-secret");
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        lastPath.set(exchange.getRequestURI().getRawPath());
        lastBody.set(readBody(exchange));

        if (path.endsWith("/protocol/openid-connect/token")) {
            if (rejectToken.get()) {
                respond(exchange, 401, "{}");
            } else {
                respond(exchange, 200, "{\"access_token\":\"test-token\"}");
            }
            return;
        }
        if (notFound.get()) {
            respond(exchange, 404, "{}");
            return;
        }

        assertEquals("Bearer test-token", exchange.getRequestHeaders().getFirst("Authorization"));
        if ("GET".equals(method)) {
            int first = Integer.parseInt(exchange.getRequestURI().getQuery()
                    .replaceFirst("first=", "").replaceFirst("&max=.*", ""));
            JsonArrayBuilder page = Json.createArrayBuilder();
            int end = first == 0 ? 100 : first + 1;
            for (int i = first; i < end; i++) {
                page.add(Json.createObjectBuilder().add("id", "id-" + i)
                        .add("username", "user-" + i).add("email", "u" + i + "@example.test"));
            }
            respond(exchange, 200, page.build().toString());
        } else if ("POST".equals(method)) {
            exchange.getResponseHeaders().add("Location",
                    "/admin/realms/poc-keycloak/users/user%20id");
            respond(exchange, 201, "");
        } else if ("PUT".equals(method) || "DELETE".equals(method)) {
            respond(exchange, 204, "");
        } else {
            respond(exchange, 405, "{}");
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (status == 204) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
        exchange.close();
    }
}
