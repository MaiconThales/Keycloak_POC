package poc.persistence.keycloak;

/** Deliberately does not include client secrets or access tokens in its message. */
public class KeycloakAdminException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public KeycloakAdminException(String message) { super(message); }
    public KeycloakAdminException(String message, Throwable cause) { super(message, cause); }
}
