package poc.rest.dto;

import javax.xml.bind.annotation.XmlRootElement;

import poc.persistence.service.UserProfile;

/** Public projection of a Keycloak user; credentials are never exposed. */
@XmlRootElement
public class UserResponse {
    private String id;
    private String username;
    private String email;

    public UserResponse() {
    }

    public static UserResponse from(UserProfile profile) {
        UserResponse response = new UserResponse();
        response.id = profile.getId();
        response.username = profile.getUsername();
        response.email = profile.getEmail();
        return response;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
