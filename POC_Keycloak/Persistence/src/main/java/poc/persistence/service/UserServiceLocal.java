package poc.persistence.service;

import java.util.List;

import javax.ejb.Local;

@Local
public interface UserServiceLocal {
    List<UserProfile> findAll();
    UserProfile create(String username, String email);
    UserProfile update(String id, String username, String email);
    void delete(String id);
}
