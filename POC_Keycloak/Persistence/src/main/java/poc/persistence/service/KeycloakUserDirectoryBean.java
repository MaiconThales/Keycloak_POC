package poc.persistence.service;

import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.ejb.Local;
import javax.ejb.Stateless;

@Stateless(name = "KeycloakUserDirectoryBean")
@Local(UserDirectoryLocal.class)
public class KeycloakUserDirectoryBean implements UserDirectoryLocal {
    @Override
    @RolesAllowed({"Admin", "Admin-Read"})
    public List<UserProfile> findAll() {
        return new KeycloakAdminApiClient().findAll();
    }

    @Override
    @RolesAllowed({"Admin", "Admin-Write"})
    public UserProfile create(String username, String email) {
        return new KeycloakAdminApiClient().create(username, email);
    }

    @Override
    @RolesAllowed({"Admin", "Admin-Update"})
    public UserProfile update(String id, String username, String email) {
        return new KeycloakAdminApiClient().update(id, username, email);
    }

    @Override
    @RolesAllowed({"Admin", "Admin-Delete"})
    public void delete(String id) {
        new KeycloakAdminApiClient().delete(id);
    }
}
