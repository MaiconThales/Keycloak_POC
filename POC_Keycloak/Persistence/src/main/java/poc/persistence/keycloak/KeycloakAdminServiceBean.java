package poc.persistence.keycloak;

import javax.ejb.Stateless;

/** Transaction boundary for administrative Keycloak calls; no REST resource calls Keycloak directly. */
@Stateless
public class KeycloakAdminServiceBean implements KeycloakAdminService {
    private final KeycloakAdminClient client;

    public KeycloakAdminServiceBean() { this(new KeycloakAdminHttpClient()); }
    KeycloakAdminServiceBean(KeycloakAdminClient client) { this.client = client; }

    @Override
    public void validateUserManagementAccess() { client.validateManageUsers(); }

    @Override
    public KeycloakAdminClient.AdminApiResponse userManagement(String method, String path, String body) {
        return client.userManagement(method, path, body);
    }

    @Override
    public KeycloakUserProfile findUser(String username) { return client.findUser(username); }
}
