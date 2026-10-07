package poc.security;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Principal;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Priority;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.security.auth.Subject;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import org.jboss.security.SecurityContext;
import org.jboss.security.SecurityContextAssociation;
import org.jboss.security.SecurityContextFactory;
import org.jboss.security.SimplePrincipal;
import org.jboss.security.identity.RoleGroup;
import org.jboss.security.identity.plugins.SimpleRole;
import org.jboss.security.identity.plugins.SimpleRoleGroup;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class KeycloakJwtFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static final long KEY_CACHE_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final int CONNECT_TIMEOUT_MILLIS = 3000;
    private static final int READ_TIMEOUT_MILLIS = 3000;
    private static final int MAX_JWKS_BYTES = 1024 * 1024;
    private static final int MAX_TOKEN_CHARS = 16384;
    private static final String ISSUER_PROPERTY = "keycloak.issuer";
    private static final String PREVIOUS_SECURITY_CONTEXT =
            KeycloakJwtFilter.class.getName() + ".previousSecurityContext";

    private volatile Map<String, RSAPublicKey> cachedKeys = Collections.emptyMap();
    private volatile long keysExpireAt;

    @Override
    public void filter(ContainerRequestContext request) throws IOException {
        if (!requiresAdmin(request)) {
            return;
        }

        String issuer = System.getProperty(ISSUER_PROPERTY);
        if (issuer == null || issuer.trim().isEmpty()) {
            request.abortWith(error(Response.Status.SERVICE_UNAVAILABLE,
                    "Keycloak issuer is not configured."));
            return;
        }
        issuer = trimTrailingSlash(issuer.trim());

        String authorization = request.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            request.abortWith(unauthorized());
            return;
        }

        try {
            JsonObject claims = validateToken(authorization.substring(7).trim(), issuer);
            if (!hasAdminRole(claims)) {
                request.abortWith(error(Response.Status.FORBIDDEN, "Insufficient permissions."));
                return;
            }
            establishContainerIdentity(request, claims);
        } catch (IOException e) {
            request.abortWith(error(Response.Status.SERVICE_UNAVAILABLE,
                    "Unable to retrieve Keycloak signing keys."));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            request.abortWith(unauthorized());
        } catch (ClassCastException e) {
            request.abortWith(unauthorized());
        } catch (javax.json.JsonException e) {
            request.abortWith(unauthorized());
        }
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object previous = request.getProperty(PREVIOUS_SECURITY_CONTEXT);
        if (previous instanceof PreviousSecurityContext) {
            restoreSecurityContext(((PreviousSecurityContext) previous).context);
            request.removeProperty(PREVIOUS_SECURITY_CONTEXT);
        }
    }

    private void establishContainerIdentity(ContainerRequestContext request, JsonObject claims) {
        String name = claims.getString("preferred_username", claims.getString("sub", ""));
        if (name.isEmpty()) {
            throw new IllegalArgumentException("JWT has no subject identity.");
        }

        Principal principal = new SimplePrincipal(name);
        Subject subject = new Subject();
        subject.getPrincipals().add(principal);
        SecurityContext previous = SecurityContextAssociation.getSecurityContext();
        try {
            SecurityContext context = SecurityContextFactory.createSecurityContext(
                    principal, null, subject, "other");
            context.getUtil().createSubjectInfo(principal, null, subject);
            RoleGroup roles = new SimpleRoleGroup("Roles");
            roles.addRole(new SimpleRole("admin"));
            context.getUtil().setRoles(roles);
            request.setProperty(PREVIOUS_SECURITY_CONTEXT,
                    new PreviousSecurityContext(previous));
            SecurityContextAssociation.setSecurityContext(context);
        } catch (Exception e) {
            restoreSecurityContext(previous);
            throw new IllegalStateException("Unable to establish the validated Keycloak identity.", e);
        }
    }

    private void restoreSecurityContext(SecurityContext context) {
        if (context == null) {
            SecurityContextAssociation.clearSecurityContext();
        } else {
            SecurityContextAssociation.setSecurityContext(context);
        }
    }

    private boolean requiresAdmin(ContainerRequestContext request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getUriInfo().getPath();
        String normalizedPath = path == null ? "" : path.replaceAll("^/+|/+$", "");
        int apiPath = normalizedPath.lastIndexOf("api/v1/");
        if (apiPath >= 0) {
            normalizedPath = normalizedPath.substring(apiPath + "api/v1/".length());
        }
        return "products".equals(normalizedPath);
    }

    private JsonObject validateToken(String token, String issuer)
            throws IOException, GeneralSecurityException {
        if (token.length() > MAX_TOKEN_CHARS) {
            throw new IllegalArgumentException("JWT exceeds the size limit.");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
            throw new IllegalArgumentException("Malformed JWT.");
        }

        JsonObject header = readJson(decode(parts[0]));
        if (!"RS256".equals(header.getString("alg", ""))) {
            throw new IllegalArgumentException("Unsupported JWT algorithm.");
        }
        String keyId = header.getString("kid", "");
        RSAPublicKey key = findKey(keyId);

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        if (!verifier.verify(decode(parts[2]))) {
            throw new GeneralSecurityException("Invalid JWT signature.");
        }

        JsonObject claims = readJson(decode(parts[1]));
        if (!issuer.equals(claims.getString("iss", ""))) {
            throw new IllegalArgumentException("Unexpected JWT issuer.");
        }
        long now = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        JsonValue expiration = claims.get("exp");
        if (expiration == null || expiration.getValueType() != JsonValue.ValueType.NUMBER
                || claims.getJsonNumber("exp").longValue() <= now) {
            throw new IllegalArgumentException("Expired or missing JWT expiration.");
        }
        if (claims.containsKey("nbf")) {
            JsonValue notBefore = claims.get("nbf");
            if (notBefore.getValueType() != JsonValue.ValueType.NUMBER
                    || claims.getJsonNumber("nbf").longValue() > now) {
                throw new IllegalArgumentException("JWT is not active yet.");
            }
        }
        return claims;
    }

    private RSAPublicKey findKey(String keyId) throws IOException, GeneralSecurityException {
        Map<String, RSAPublicKey> keys = cachedKeys;
        if (System.currentTimeMillis() >= keysExpireAt || !keys.containsKey(keyId)) {
            synchronized (this) {
                keys = cachedKeys;
                if (System.currentTimeMillis() >= keysExpireAt || !keys.containsKey(keyId)) {
                    cachedKeys = keys = loadKeys();
                    keysExpireAt = System.currentTimeMillis() + KEY_CACHE_MILLIS;
                }
            }
        }
        RSAPublicKey key = keys.get(keyId);
        if (key == null) {
            throw new IllegalArgumentException("Unknown JWT signing key.");
        }
        return key;
    }

    private Map<String, RSAPublicKey> loadKeys() throws IOException, GeneralSecurityException {
        String issuer = trimTrailingSlash(System.getProperty(ISSUER_PROPERTY).trim());
        URL url = new URL(issuer + "/protocol/openid-connect/certs");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestProperty("Accept", MediaType.APPLICATION_JSON);
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Keycloak returned HTTP " + status + " for its JWKS endpoint.");
            }
            byte[] body = readLimited(connection, MAX_JWKS_BYTES);
            JsonObject jwks = readJson(body);
            Map<String, RSAPublicKey> keys = new HashMap<>();
            JsonArray entries = jwks.getJsonArray("keys");
            if (entries == null) {
                throw new IOException("Keycloak JWKS response has no keys array.");
            }
            for (JsonValue value : entries) {
                if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                    continue;
                }
                JsonObject entry = (JsonObject) value;
                if (!"RSA".equals(entry.getString("kty", ""))
                        || !"sig".equals(entry.getString("use", ""))
                        || !"RS256".equals(entry.getString("alg", ""))) {
                    continue;
                }
                String kid = entry.getString("kid", "");
                if (!kid.isEmpty()) {
                    BigInteger modulus = new BigInteger(1, decode(entry.getString("n")));
                    BigInteger exponent = new BigInteger(1, decode(entry.getString("e")));
                    RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
                    keys.put(kid, (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec));
                }
            }
            if (keys.isEmpty()) {
                throw new IOException("Keycloak JWKS response contains no supported signing keys.");
            }
            return Collections.unmodifiableMap(keys);
        } finally {
            connection.disconnect();
        }
    }

    private boolean hasAdminRole(JsonObject claims) {
        JsonObject realmAccess = claims.getJsonObject("realm_access");
        if (containsRole(realmAccess)) {
            return true;
        }
        JsonObject resources = claims.getJsonObject("resource_access");
        if (resources != null) {
            for (String client : resources.keySet()) {
                if (containsRole(resources.getJsonObject(client))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsRole(JsonObject access) {
        if (access == null) {
            return false;
        }
        JsonArray roles = access.getJsonArray("roles");
        if (roles == null) {
            return false;
        }
        for (JsonValue role : roles) {
            if (role.getValueType() == JsonValue.ValueType.STRING
                    && "admin".equals(((JsonString) role).getString())) {
                return true;
            }
        }
        return false;
    }

    private byte[] readLimited(HttpURLConnection connection, int limit) throws IOException {
        try (java.io.InputStream input = connection.getInputStream();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > limit) {
                    throw new IOException("Keycloak JWKS response exceeds the size limit.");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private JsonObject readJson(byte[] value) {
        try (JsonReader reader = Json.createReader(new ByteArrayInputStream(value))) {
            return reader.readObject();
        }
    }

    private byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity("{\"message\":\"Invalid or missing bearer token.\"}")
                .build();
    }

    private Response error(Response.Status status, String message) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity("{\"message\":\"" + message + "\"}")
                .build();
    }

    private static final class PreviousSecurityContext {
        private final SecurityContext context;

        private PreviousSecurityContext(SecurityContext context) {
            this.context = context;
        }
    }
}
