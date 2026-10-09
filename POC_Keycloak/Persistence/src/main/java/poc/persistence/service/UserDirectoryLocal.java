package poc.persistence.service;

import java.util.List;

import javax.ejb.Local;

/** Adapter boundary for Keycloak administration; no local users table exists.*/
@Local
public interface UserDirectoryLocal {
    List<UserProfile> findAll();
    UserProfile create(String username, String email);
    UserProfile update(String id, String username, String email);
    void delete(String id);
}