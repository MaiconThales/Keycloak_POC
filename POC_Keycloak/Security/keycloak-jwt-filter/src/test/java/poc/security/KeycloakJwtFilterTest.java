package poc.security;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.jboss.security.SecurityContext;
import org.jboss.security.SecurityContextAssociation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KeycloakJwtFilterTest {
    private static final String ISSUER_PROPERTY = "keycloak.issuer";
    private static final String ISSUER = "http://127.0.0.1";

    private HttpServer server;
    private KeyPair signingKey;
    private String issuer;
    private String originalIssuer;
    private String jwksBody;
    private int jwksRequests;

    @BeforeEach
    void startJwksEndpoint() throws Exception {
        originalIssuer = System.getProperty(ISSUER_PROPERTY);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        signingKey = generator.generateKeyPair();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/realms/test/protocol/openid-connect/certs", exchange -> {
            jwksRequests++;
            byte[] body = jwksBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        issuer = ISSUER + ":" + server.getAddress().getPort() + "/realms/test";
        System.setProperty(ISSUER_PROPERTY, issuer);
        jwksBody = jwks(signingKey);
        jwksRequests = 0;
    }

    @AfterEach
    void stopJwksEndpoint() {
        server.stop(0);
        SecurityContextAssociation.clearSecurityContext();
        if (originalIssuer == null) {
            System.clearProperty(ISSUER_PROPERTY);
        } else {
            System.setProperty(ISSUER_PROPERTY, originalIssuer);
        }
    }

    @Test
    void leavesPublicRoutesAndNonPostMethodsUnaffected() throws IOException {
        System.clearProperty(ISSUER_PROPERTY);
        KeycloakJwtFilter filter = new KeycloakJwtFilter();

        filter.filter(request("GET", "/api/v1/auth/login", null));
        filter.filter(request("POST", "/api/v1/auth/login", null));
        filter.filter(request("OPTIONS", "/api/v1/products/12", null));

        assertEquals(0, jwksRequests);
    }

    @Test
    void requiresConfiguredIssuerAndBearerTokenForProductCreation() throws IOException {
        KeycloakJwtFilter filter = new KeycloakJwtFilter();
        ContainerRequestContext missingIssuer = request("POST", "api/v1/products", null);
        System.clearProperty(ISSUER_PROPERTY);
        filter.filter(missingIssuer);
        assertAbortedWith(missingIssuer, 503);

        System.setProperty(ISSUER_PROPERTY, issuer);
        ContainerRequestContext missingToken = request("POST", "api/v1/products", null);
        filter.filter(missingToken);
        assertAbortedWith(missingToken, 401);

        ContainerRequestContext malformed = request("POST", "api/v1/products", "Bearer malformed");
        filter.filter(malformed);
        assertAbortedWith(malformed, 401);
        assertEquals(0, jwksRequests);

        ArgumentCaptor<Response> response = ArgumentCaptor.forClass(Response.class);
        verify(missingToken).abortWith(response.capture());
        assertEquals("Bearer", response.getValue().getHeaderString(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void requiresBearerTokenAndReadRoleForProductListing() throws Exception {
        KeycloakJwtFilter filter = new KeycloakJwtFilter();
        ContainerRequestContext missingToken = request("GET", "/api/v1/products", null);
        filter.filter(missingToken);
        assertAbortedWith(missingToken, 401);

        ContainerRequestContext writeOnly = request("GET", "/api/v1/products",
                "Bearer " + token(signingKey, issuer, "realm", "User-Write",
                        currentTime() + 300, null, "RS256"));
        filter.filter(writeOnly);
        assertAbortedWith(writeOnly, 403);

        ContainerRequestContext reader = request("GET", "/api/v1/products",
                "Bearer " + token(signingKey, issuer, "realm", "User-Read",
                        currentTime() + 300, null, "RS256"));
        filter.filter(reader);
        verify(reader, never()).abortWith(any(Response.class));
        filter.filter(reader, mock(ContainerResponseContext.class));
        assertNoAdminIdentity();
    }

    @Test
    void acceptsAllProductReadRoles() throws Exception {
        for (String role : new String[] {"Admin-Read", "Sub-Admin-Read"}) {
            ContainerRequestContext listing = request("GET", "/products",
                    "Bearer " + token(signingKey, issuer, "realm", role,
                            currentTime() + 300, null, "RS256"));
            new KeycloakJwtFilter().filter(listing);
            verify(listing, never()).abortWith(any(Response.class));
            new KeycloakJwtFilter().filter(listing, mock(ContainerResponseContext.class));
        }
    }

    @Test
    void acceptsGranularAdminRolesAndCachesJwks() throws Exception {
        KeycloakJwtFilter filter = new KeycloakJwtFilter();
        ContainerRequestContext realmAdmin = request("POST", "/api/v1/products",
                "Bearer " + token(signingKey, issuer, "realm", "Admin-Write", currentTime() + 300, null, "RS256"));
        filter.filter(realmAdmin);
        verify(realmAdmin, never()).abortWith(any(Response.class));
        assertAdminIdentity();
        filter.filter(realmAdmin, mock(ContainerResponseContext.class));
        assertNoAdminIdentity();

        ContainerRequestContext clientAdmin = request("POST", "/products",
                "bearer " + token(signingKey, issuer, "client", "Sub-Admin-Write", currentTime() + 300, null, "RS256"));
        filter.filter(clientAdmin);
        verify(clientAdmin, never()).abortWith(any(Response.class));
        assertEquals(1, jwksRequests);
        assertRole("Sub-Admin-Write");
        filter.filter(clientAdmin, mock(ContainerResponseContext.class));
        assertNoAdminIdentity();
    }

    @Test
    void productCreationRequiresWriteRoleAndAcceptsAdministrativeWriteRoles() throws Exception {
        for (String role : new String[] {"User-Write", "Admin-Read", "Sub-Admin-Read"}) {
            ContainerRequestContext denied = request("POST", "/products",
                    "Bearer " + token(signingKey, issuer, "realm", role,
                            currentTime() + 300, null, "RS256"));
            new KeycloakJwtFilter().filter(denied);
            assertAbortedWith(denied, 403);
        }

        for (String role : new String[] {"Admin-Write", "Sub-Admin-Write"}) {
            ContainerRequestContext allowed = request("POST", "/products",
                    "Bearer " + token(signingKey, issuer, "realm", role,
                            currentTime() + 300, null, "RS256"));
            KeycloakJwtFilter filter = new KeycloakJwtFilter();
            filter.filter(allowed);
            verify(allowed, never()).abortWith(any(Response.class));
            filter.filter(allowed, mock(ContainerResponseContext.class));
        }
    }

    @Test
    void productUpdateAndDeleteUseTheirOwnAdministrativePermission() throws Exception {
        assertProductOperation("PUT", "Update", "User-Update");
        assertProductOperation("DELETE", "Delete", "User-Delete");
    }

    private void assertProductOperation(String method, String action, String userRole) throws Exception {
        for (String role : new String[] {"User-" + action, "Admin-Read"}) {
            ContainerRequestContext denied = request(method, "/products",
                    "Bearer " + token(signingKey, issuer, "realm", role,
                            currentTime() + 300, null, "RS256"));
            new KeycloakJwtFilter().filter(denied);
            assertAbortedWith(denied, 403);
        }
        for (String role : new String[] {"Admin-" + action, "Sub-Admin-" + action,
                "Sub-Admin-" + action}) {
            ContainerRequestContext allowed = request(method, "/products",
                    "Bearer " + token(signingKey, issuer, "realm", role,
                            currentTime() + 300, null, "RS256"));
            KeycloakJwtFilter filter = new KeycloakJwtFilter();
            filter.filter(allowed);
            verify(allowed, never()).abortWith(any(Response.class));
            filter.filter(allowed, mock(ContainerResponseContext.class));
        }
    }

    @Test
    void rejectsAuthenticatedUsersWithoutAdminRole() throws Exception {
        ContainerRequestContext request = request("POST", "products",
                "Bearer " + token(signingKey, issuer, "realm", "reader", currentTime() + 300, null, "RS256"));

        new KeycloakJwtFilter().filter(request);

        assertAbortedWith(request, 403);
        assertNull(SecurityContextAssociation.getSecurityContext());
    }

    @Test
    void rejectsBadSignaturesAlgorithmsIssuersAndTimeClaims() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair unrelatedKey = generator.generateKeyPair();
        KeycloakJwtFilter filter = new KeycloakJwtFilter();

        assertUnauthorized(filter, token(unrelatedKey, issuer, "realm", "Admin-Write",
                currentTime() + 300, null, "RS256"));
        assertUnauthorized(filter, token(signingKey, issuer, "realm", "Admin-Write",
                currentTime() + 300, null, "HS256"));
        assertUnauthorized(filter, token(signingKey, issuer + "/wrong", "realm", "Admin-Write",
                currentTime() + 300, null, "RS256"));
        assertUnauthorized(filter, token(signingKey, issuer, "realm", "Admin-Write",
                currentTime() - 1, null, "RS256"));
        assertUnauthorized(filter, token(signingKey, issuer, "realm", "Admin-Write",
                currentTime() + 300, currentTime() + 60, "RS256"));
        ContainerRequestContext noRole = request("POST", "products",
                "Bearer " + token(signingKey, issuer, "realm", "", currentTime() + 300, null, "RS256"));
        filter.filter(noRole);
        assertAbortedWith(noRole, 403);
    }

    @Test
    void reportsSigningKeyServiceFailuresAsUnavailable() throws Exception {
        jwksBody = "{\"keys\":[]}";
        ContainerRequestContext noUsableKeys = request("POST", "products",
                "Bearer " + token(signingKey, issuer, "realm", "Admin-Write",
                        currentTime() + 300, null, "RS256"));
        new KeycloakJwtFilter().filter(noUsableKeys);
        assertAbortedWith(noUsableKeys, 503);

        ContainerRequestContext upstreamFailure = request("POST", "products",
                "Bearer " + token(signingKey, issuer, "realm", "Admin-Write",
                        currentTime() + 300, null, "RS256"));
        server.stop(0);
        new KeycloakJwtFilter().filter(upstreamFailure);
        assertAbortedWith(upstreamFailure, 503);
    }

    private ContainerRequestContext request(String method, String path, String authorization) {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        Map<String, Object> properties = new HashMap<>();
        when(request.getMethod()).thenReturn(method);
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn(path);
        when(request.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(authorization);
        when(request.getProperty(anyString())).thenAnswer(invocation ->
                properties.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            properties.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(request).setProperty(anyString(), any());
        doAnswer(invocation -> {
            properties.remove(invocation.getArgument(0));
            return null;
        }).when(request).removeProperty(anyString());
        return request;
    }

    private void assertUnauthorized(KeycloakJwtFilter filter, String jwt) throws IOException {
        ContainerRequestContext request = request("POST", "/api/v1/products", "Bearer " + jwt);
        filter.filter(request);
        assertAbortedWith(request, 401);
    }

    private void assertAbortedWith(ContainerRequestContext request, int status) {
        ArgumentCaptor<Response> response = ArgumentCaptor.forClass(Response.class);
        verify(request).abortWith(response.capture());
        assertEquals(status, response.getValue().getStatus());
    }

    private void assertAdminIdentity() {
        assertRole("Admin-Write");
    }

    private void assertRole(String role) {
        SecurityContext context = SecurityContextAssociation.getSecurityContext();
        assertNotNull(context);
        assertTrue(context.getUtil().getRoles().containsRole(
                new org.jboss.security.identity.plugins.SimpleRole(role)));
    }

    private void assertNoAdminIdentity() {
        SecurityContext context = SecurityContextAssociation.getSecurityContext();
        assertTrue(context == null || context.getUtil().getRoles() == null
                || !context.getUtil().getRoles().containsRole(
                        new org.jboss.security.identity.plugins.SimpleRole("Admin-Write")));
    }

    private String token(KeyPair keyPair, String tokenIssuer, String roleContainer, String role,
            long expiresAt, Long notBefore, String algorithm) throws Exception {
        String header = "{\"alg\":\"" + algorithm + "\",\"kid\":\"test-key\"}";
        String roleClaims = "realm".equals(roleContainer)
                ? "\"realm_access\":{\"roles\":[\"" + role + "\"]}"
                : "\"resource_access\":{\"api\":{\"roles\":[\"" + role + "\"]}}";
        String nbfClaim = notBefore == null ? "" : ",\"nbf\":" + notBefore;
        String claims = "{\"iss\":\"" + tokenIssuer + "\",\"sub\":\"test-user\",\"exp\":" + expiresAt
                + nbfClaim + "," + roleClaims + "}";
        String unsigned = encode(header) + "." + encode(claims);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));
        return unsigned + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(signature.sign());
    }

    private String jwks(KeyPair pair) {
        RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
        return "{\"keys\":["
                + "{\"kty\":\"RSA\",\"use\":\"enc\",\"alg\":\"RS256\",\"kid\":\"ignored\","
                + "\"n\":\"" + encode(publicKey.getModulus()) + "\",\"e\":\""
                + encode(publicKey.getPublicExponent()) + "\"},"
                + "{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\"test-key\","
                + "\"n\":\"" + encode(publicKey.getModulus()) + "\",\"e\":\""
                + encode(publicKey.getPublicExponent()) + "\"}]}";
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(BigInteger value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(unsignedBytes(value));
    }

    private byte[] unsignedBytes(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes[0] == 0) {
            return java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return bytes;
    }

    private long currentTime() {
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    }
}
