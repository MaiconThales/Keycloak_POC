package poc.rest.dto;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/** User write payload. Password is accepted only for creation and is never echoed. */
public class UserInput {
    private String username;
    @NotNull @Size(min = 1, max = 320) private String email;
    @NotNull @Size(min = 1, max = 255) private String firstName;
    @NotNull @Size(min = 1, max = 255) private String lastName;
    private String password;
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
