package poc.rest.dto;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/** Fields allowed during the pending-registration flow; it intentionally has no id. */
public class CompleteRegistrationInput {
    @NotNull @Size(min = 40, max = 128) private String registrationTicket;
    @NotNull @Size(min = 1, max = 255) private String username;
    @NotNull @Size(min = 1, max = 255) private String firstName;
    @NotNull @Size(min = 1, max = 255) private String lastName;
    @NotNull @Size(min = 3, max = 320) private String email;
    public String getUsername() { return username; }
    public void setUsername(String value) { username = value; }
    public String getRegistrationTicket() { return registrationTicket; }
    public void setRegistrationTicket(String value) { registrationTicket = value; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String value) { firstName = value; }
    public String getLastName() { return lastName; }
    public void setLastName(String value) { lastName = value; }
    public String getEmail() { return email; }
    public void setEmail(String value) { email = value; }
}
