package poc.persistence.service;

import javax.ejb.Local;

import poc.persistence.entity.UserEntity;
import poc.persistence.keycloak.KeycloakUserProfile;

@Local
public interface UserServiceLocal {
    UserPage listUsers(int page, int size);
    UserEntity createUser(String username, String email, String firstName, String lastName,
            String password);
    UserEntity updateUser(Long id, String username, String email, String firstName, String lastName);
    /** Compatibility overload for clients compiled against the first 12.02 draft. */
    UserEntity updateUser(Long id, String email, String firstName, String lastName);
    void deactivateUser(Long id);
    boolean isOwner(Long id, String principal);
    /** Resolves the authenticated principal to the local user id. */
    Long findUserIdByPrincipal(String principal);
    /** Completes only the profile identified by the supplied Keycloak username. */
    KeycloakUserProfile completeRegistration(String username, String email, String firstName,
            String lastName);
}
