package poc.rest.dto;

import poc.persistence.entity.UserEntity;

/** Public user representation; credentials and Keycloak internals are excluded. */
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private Boolean active;
    public static UserResponse from(UserEntity user) {
        UserResponse result = new UserResponse();
        result.id = user.getId(); result.username = user.getUsername(); result.email = user.getEmail();
        result.firstName = user.getFirstName(); result.lastName = user.getLastName();
        result.active = user.getActive();
        return result;
    }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public Boolean getActive() { return active; }
}
