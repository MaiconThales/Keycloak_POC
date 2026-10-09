package poc.persistence.keycloak;

/** EJB-facing facade. Future user use cases depend on this contract, not HTTP. */
public interface KeycloakAdminService {
    void validateUserManagementAccess();
    KeycloakAdminClient.AdminApiResponse userManagement(String method, String path, String body);
    KeycloakUserProfile findUser(String username);
}
