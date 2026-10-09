package poc.rest.dto;

public class CompleteRegistrationResponse {
    private final String status = "REGISTRATION_COMPLETED";
    private final String message = "Registration completed. Authenticate to receive an access token.";
    public String getStatus() { return status; }
    public String getMessage() { return message; }
}
