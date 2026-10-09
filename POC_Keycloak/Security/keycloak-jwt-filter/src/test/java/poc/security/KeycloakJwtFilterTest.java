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

        filter.filter(request("GET", "/api/v1/products", null));
        filter.filter(request("POST", "/api/v1/auth/login", null));
        filter.filter(request("POST", "/api/v1/products/12", null));

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
    void acceptsRealmAndClientAdminRolesAndCachesJwks() throws Exception {
        KeycloakJwtFilter filter = new KeycloakJwtFilter();
        ContainerRequestContext realmAdmin = request("POST", "/api/v1/products",
                "Bearer " + token(signingKey, issuer, "realm", "admin", currentTime() + 300, null, "RS256"));
        filter.filter(realmAdmin);
        verify(realmAdmin, never()).abortWith(any(Response.class));
        assertAdminIdentity();
        filter.filter(realmAdmin, mock(ContainerResponseContext.class));
        assertNoAdminIdentity();

        ContainerRequestContext clientAdmin = request("POST", "/products",
                "bearer " + token(signingKey, issuer, "client", "admin", currentTime() + 300, null, "RS256"));
        filter.filter(clientAdmin);
        verify(clientAdmin, never()).abortWith(any(Response.class));
        assertEquals(1, jwksRequests);
        assertAdminIdentity();
        filter.filter(clientAdmin, mock(ContainerResponseContext.class));
        assertNoAdminIdentity();
    }

    @Test
    void exposesGranularRolesAndGroupsInBothSecurityContexts() throws Exception {
        ContainerRequestContext request = request("POST", "/api/v1/products",
                "Bearer " + tokenWithGroups(signingKey, issuer, currentTime() + 300));

        new KeycloakJwtFilter().filter(request);

        SecurityContext context = SecurityContextAssociation.getSecurityContext();
        assertTrue(context.getUtil().getRoles().containsRole(
                new org.jboss.security.identity.plugins.SimpleRole("Admin-Write")));
        assertTrue(context.getUtil().getRoles().containsRole(
                new org.jboss.security.identity.plugins.SimpleRole("/Admin")));
        assertTrue(context.getUtil().getRoles().containsRole(
                new org.jboss.security.identity.plugins.SimpleRole("Admin/Sub-Admin")));
        assertTrue(context.getUtil().getRoles().containsRole(
                new org.jboss.security.identity.plugins.SimpleRole("/Admin/Sub-Admin")));
        assertTrue(context.getUtil().getRoles().containsRole(
                new org.jboss.security.identity.plugins.SimpleRole("Admin/Sub-Admin/User")));
        assertTrue(context.getUtil().getRoles().containsRole(
                new org.jboss.security.identity.plugins.SimpleRole("/Admin/Sub-Admin/User")));
        assertTrue(context.getUtil().getRoles().containsRole(
                new org.jboss.security.identity.plugins.SimpleRole("Admin")));
        assertEquals("test-user", request.getSecurityContext().getUserPrincipal().getName());
        assertTrue(request.getSecurityContext().isUserInRole("Admin-Write"));
        assertTrue(request.getSecurityContext().isUserInRole("Admin"));
    }

    @Test
    void acceptsWritePermissionsButDoesNotPromoteOtherGranularPermissions() throws Exception {
        KeycloakJwtFilter filter = new KeycloakJwtFilter();
        ContainerRequestContext readOnly = request("POST", "/api/v1/products",
                "Bearer " + tokenWithRole(signingKey, issuer, "Admin-Read", currentTime() + 300));
        filter.filter(readOnly);
        assertAbortedWith(readOnly, 403);

        ContainerRequestContext writer = request("POST", "/api/v1/products",
                "Bearer " + tokenWithRole(signingKey, issuer, "Admin-Write", currentTime() + 300));
        filter.filter(writer);
        verify(writer, never()).abortWith(any(Response.class));
        assertTrue(writer.getSecurityContext().isUserInRole("Admin-Write"));
        filter.filter(writer, mock(ContainerResponseContext.class));
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

        assertUnauthorized(filter, token(unrelatedKey, issuer, "realm", "admin",
                currentTime() + 300, null, "RS256"));
        assertUnauthorized(filter, token(signingKey, issuer, "realm", "admin",
                currentTime() + 300, null, "HS256"));
        assertUnauthorized(filter, token(signingKey, issuer + "/wrong", "realm", "admin",
                currentTime() + 300, null, "RS256"));
        assertUnauthorized(filter, token(signingKey, issuer, "realm", "admin",
                currentTime() - 1, null, "RS256"));
        assertUnauthorized(filter, token(signingKey, issuer, "realm", "admin",
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
                "Bearer " + token(signingKey, issuer, "realm", "admin",
                        currentTime() + 300, null, "RS256"));
        new KeycloakJwtFilter().filter(noUsableKeys);
        assertAbortedWith(noUsableKeys, 503);

        ContainerRequestContext upstreamFailure = request("POST", "products",
                "Bearer " + token(signingKey, issuer, "realm", "admin",
                        currentTime() + 300, null, "RS256"));
        server.stop(0);
        new KeycloakJwtFilter().filter(upstreamFailure);
        assertAbortedWith(upstreamFailure, 503);
    }

    private ContainerRequestContext request(String method, String path, String authorization) {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        Map<String, Object> properties = new HashMap<>();
        javax.ws.rs.core.SecurityContext[] securityContext = new javax.ws.rs.core.SecurityContext[1];
        when(request.getMethod()).thenReturn(method);
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn(path);
        when(request.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(authorization);
        when(request.getSecurityContext()).thenAnswer(invocation -> securityContext[0]);
        doAnswer(invocation -> {
            securityContext[0] = invocation.getArgument(0);
            return null;
        }).when(request).setSecurityContext(any(javax.ws.rs.core.SecurityContext.class));
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
        SecurityContext context = SecurityContextAssociation.getSecurityContext();
        assertNotNull(context);
        assertTrue(context.getUtil().getRoles().containsRole(
                new org.jboss.security.identity.plugins.SimpleRole("admin")));
    }

    private void assertNoAdminIdentity() {
        SecurityContext context = SecurityContextAssociation.getSecurityContext();
        assertTrue(context == null || context.getUtil().getRoles() == null
                || !context.getUtil().getRoles().containsRole(
                        new org.jboss.security.identity.plugins.SimpleRole("admin")));
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

    private String tokenWithGroups(KeyPair keyPair, String tokenIssuer, long expiresAt) throws Exception {
        String header = "{\"alg\":\"RS256\",\"kid\":\"test-key\"}";
        String claims = "{\"iss\":\"" + tokenIssuer + "\",\"sub\":\"test-user\",\"exp\":"
                + expiresAt + ",\"realm_access\":{\"roles\":[\"admin\",\"Admin-Write\"]}"
                + ",\"groups\":[\"/Admin/Sub-Admin/User\"]}";
        String unsigned = encode(header) + "." + encode(claims);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));
        return unsigned + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(signature.sign());
    }

    private String tokenWithRole(KeyPair keyPair, String tokenIssuer, String role, long expiresAt)
            throws Exception {
        String header = "{\"alg\":\"RS256\",\"kid\":\"test-key\"}";
        String claims = "{\"iss\":\"" + tokenIssuer + "\",\"sub\":\"test-user\",\"exp\":"
                + expiresAt + ",\"realm_access\":{\"roles\":[\"" + role + "\"]}}";
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
