package poc.rest.dto;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Public, deliberately secret-free response for an incomplete Keycloak profile. */
public final class PendingRegistrationResponse {
    private final String status = "PENDING_REGISTRATION";
    private final String message;
    private final String registrationTicket;
    private final List<String> requiredActions;
    private final Map<String, String> missingFields;

    public PendingRegistrationResponse(String message, List<String> requiredActions,
            Map<String, String> missingFields, String registrationTicket) {
        this.message = message;
        this.registrationTicket = registrationTicket;
        this.requiredActions = Collections.unmodifiableList(requiredActions);
        this.missingFields = Collections.unmodifiableMap(missingFields);
    }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    /** One-use credential for completion; never a Keycloak access or refresh token. */
    public String getRegistrationTicket() { return registrationTicket; }
    public List<String> getRequiredActions() { return requiredActions; }
    public Map<String, String> getMissingFields() { return missingFields; }
}
