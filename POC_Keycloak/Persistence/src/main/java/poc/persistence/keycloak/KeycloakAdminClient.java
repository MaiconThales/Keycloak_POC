package poc.persistence.keycloak;

/** Narrow boundary used by EJBs; HTTP and credentials stay out of REST resources. */
public interface KeycloakAdminClient {
    String accessToken();
    void validateManageUsers();
    AdminApiResponse userManagement(String method, String path, String body);
    KeycloakUserProfile findUser(String username);

    final class AdminApiResponse {
        private final int status;
        private final String body;
        private final String location;

        public AdminApiResponse(int status, String body) {
            this(status, body, null);
        }
        public AdminApiResponse(int status, String body, String location) {
            this.status = status;
            this.body = body == null ? "" : body;
            this.location = location;
        }
        public int getStatus() { return status; }
        public String getBody() { return body; }
        public String getLocation() { return location; }
    }
}
