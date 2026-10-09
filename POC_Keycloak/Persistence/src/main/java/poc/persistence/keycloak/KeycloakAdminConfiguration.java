package poc.persistence.keycloak;

/** Runtime-only configuration for the Keycloak Admin API client.
 *
 * <p>No credential is bundled in the application.  WildFly may expose the
 * values as system properties (the names below), while environment variables
 * provide a safe alternative for local scripts and containers.</p>
 */
public final class KeycloakAdminConfiguration {
    public static final String CLIENT_ID_PROPERTY = "keycloak.admin.client-id";
    public static final String CLIENT_SECRET_PROPERTY = "keycloak.admin.client-secret";
    public static final String CLIENT_ID_ENVIRONMENT = "KEYCLOAK_ADMIN_CLIENT_ID";
    public static final String CLIENT_SECRET_ENVIRONMENT = "KEYCLOAK_ADMIN_CLIENT_SECRET";
    public static final String SERVER_URL_PROPERTY = "keycloak.admin.server-url";
    public static final String SERVER_URL_ENVIRONMENT = "KEYCLOAK_ADMIN_SERVER_URL";
    public static final String ADMIN_REALM_PROPERTY = "keycloak.admin.realm";
    public static final String TARGET_REALM_PROPERTY = "keycloak.admin.target-realm";
    public static final String DEFAULT_SERVER_URL = "http://localhost:8081";
    public static final String DEFAULT_ADMIN_REALM = "master";
    public static final String DEFAULT_TARGET_REALM = "poc-keycloak";

    private KeycloakAdminConfiguration() {
        // Utility class.
    }

    public static String clientId() {
        return runtimeValue(CLIENT_ID_PROPERTY, CLIENT_ID_ENVIRONMENT);
    }

    public static String clientSecret() {
        return runtimeValue(CLIENT_SECRET_PROPERTY, CLIENT_SECRET_ENVIRONMENT);
    }

    public static String serverUrl() {
        String value = runtimeValue(SERVER_URL_PROPERTY, SERVER_URL_ENVIRONMENT);
        return value.isEmpty() ? DEFAULT_SERVER_URL : value.replaceAll("/+$", "");
    }

    public static String adminRealm() {
        return valueOrDefault(ADMIN_REALM_PROPERTY, DEFAULT_ADMIN_REALM);
    }

    public static String targetRealm() {
        return valueOrDefault(TARGET_REALM_PROPERTY, DEFAULT_TARGET_REALM);
    }

    /** Fails closed when a future Admin API operation is attempted. */
    public static void requireConfigured() {
        if (clientId().isEmpty() || clientSecret().isEmpty()) {
            throw new IllegalStateException("Keycloak Admin API credentials are not configured at runtime.");
        }
    }

    private static String runtimeValue(String propertyName, String environmentName) {
        String value = System.getProperty(propertyName);
        if (value == null || value.trim().isEmpty()) {
            value = System.getenv(environmentName);
        }
        return value == null ? "" : value.trim();
    }

    private static String valueOrDefault(String propertyName, String defaultValue) {
        String value = System.getProperty(propertyName);
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }
}
