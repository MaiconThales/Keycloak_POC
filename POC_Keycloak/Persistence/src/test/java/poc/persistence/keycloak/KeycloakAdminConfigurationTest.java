package poc.persistence.keycloak;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class KeycloakAdminConfigurationTest {
    @AfterEach
    void clearProperties() {
        System.clearProperty(KeycloakAdminConfiguration.CLIENT_ID_PROPERTY);
        System.clearProperty(KeycloakAdminConfiguration.CLIENT_SECRET_PROPERTY);
    }

    @Test
    void readsCredentialsFromRuntimeProperties() {
        System.setProperty(KeycloakAdminConfiguration.CLIENT_ID_PROPERTY, " admin-client ");
        System.setProperty(KeycloakAdminConfiguration.CLIENT_SECRET_PROPERTY, " secret ");

        assertEquals("admin-client", KeycloakAdminConfiguration.clientId());
        assertEquals("secret", KeycloakAdminConfiguration.clientSecret());
        assertDoesNotThrow(KeycloakAdminConfiguration::requireConfigured);
    }

    @Test
    void ignoresBlankPropertiesAndFailsClosedWithoutSecret() {
        System.setProperty(KeycloakAdminConfiguration.CLIENT_ID_PROPERTY, " ");
        System.setProperty(KeycloakAdminConfiguration.CLIENT_SECRET_PROPERTY, " ");

        assertEquals("", KeycloakAdminConfiguration.clientId());
        assertEquals("", KeycloakAdminConfiguration.clientSecret());
        assertThrows(IllegalStateException.class, KeycloakAdminConfiguration::requireConfigured);
    }
}
