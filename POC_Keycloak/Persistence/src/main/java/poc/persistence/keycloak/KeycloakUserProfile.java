package poc.persistence.keycloak;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Safe, non-secret projection of the fields needed by the login pending flow. */
public final class KeycloakUserProfile {
    private final String id;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final List<String> requiredActions;

    public KeycloakUserProfile(String id, String email, String firstName, String lastName,
            List<String> requiredActions) {
        this.id = id == null ? "" : id;
        this.email = email == null ? "" : email;
        this.firstName = firstName == null ? "" : firstName;
        this.lastName = lastName == null ? "" : lastName;
        this.requiredActions = Collections.unmodifiableList(new ArrayList<String>(requiredActions));
    }
    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public List<String> getRequiredActions() { return requiredActions; }
}
