package poc.persistence.service;

import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.annotation.security.RolesAllowed;

/**
 * Transactional facade for the external Keycloak user directory.  User data
 * is deliberately not persisted locally (the 0.2 contract has no users
 * table); the adapter owns the remote call while this bean owns authorization
 * and the use-case boundary.
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class UserServiceBean implements UserServiceLocal {
    @EJB
    private UserDirectoryLocal directory;

    @Override
    @RolesAllowed({"Admin", "Admin-Read"})
    public List<UserProfile> findAll() {
        return directory.findAll();
    }

    @Override
    @RolesAllowed({"Admin", "Admin-Write"})
    public UserProfile create(String username, String email) {
        return directory.create(required(username, "Username"), required(email, "Email"));
    }

    @Override
    @RolesAllowed({"Admin", "Admin-Update"})
    public UserProfile update(String id, String username, String email) {
        return directory.update(required(id, "User id"), required(username, "Username"),
                required(email, "Email"));
    }

    @Override
    @RolesAllowed({"Admin", "Admin-Delete"})
    public void delete(String id) {
        directory.delete(required(id, "User id"));
    }

    private String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.trim();
    }
}
