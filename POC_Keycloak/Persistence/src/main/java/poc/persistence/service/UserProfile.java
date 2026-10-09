package poc.persistence.service;

/** Safe metadata projection of a Keycloak user; it is not a JPA entity. */
public final class UserProfile {
    private final String id;
    private final String username;
    private final String email;

    public UserProfile(String id, String username, String email) {
        this.id = id;
        this.username = username;
        this.email = email;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
}
