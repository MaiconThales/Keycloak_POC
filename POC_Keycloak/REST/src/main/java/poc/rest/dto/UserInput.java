package poc.rest.dto;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlRootElement;

/** Input accepted by the administrative user facade. */
@XmlRootElement
public class UserInput {
    @NotNull
    @Size(min = 1, max = 255)
    private String username;

    @NotNull
    @Size(max = 255)
    private String email;

    public UserInput() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
