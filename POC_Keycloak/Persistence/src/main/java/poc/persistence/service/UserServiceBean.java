package poc.persistence.service;

import java.util.List;
import java.util.Locale;

import javax.annotation.security.RolesAllowed;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceContext;

import poc.persistence.entity.UserEntity;
import poc.persistence.keycloak.KeycloakAdminClient.AdminApiResponse;
import poc.persistence.keycloak.KeycloakAdminException;
import poc.persistence.keycloak.KeycloakAdminService;
import poc.persistence.keycloak.KeycloakUserProfile;

/** Transactional user use cases. HTTP details remain behind KeycloakAdminService. */
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class UserServiceBean implements UserServiceLocal {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    @PersistenceContext(unitName = "MinhaAppPU")
    private EntityManager entityManager;

    @EJB
    private KeycloakAdminService keycloakAdminService;

    public UserServiceBean() {
    }

    /* Package constructor is useful for isolated unit tests and keeps the EJB API small. */
    UserServiceBean(EntityManager entityManager, KeycloakAdminService keycloakAdminService) {
        this.entityManager = entityManager;
        this.keycloakAdminService = keycloakAdminService;
    }

    @Override
    @RolesAllowed({"Admin", "Admin-Read"})
    public UserPage listUsers(int page, int size) {
        validatePage(page, size);
        long total = entityManager.createQuery("SELECT COUNT(u) FROM UserEntity u", Long.class)
                .getSingleResult();
        List<UserEntity> users = entityManager.createQuery(
                "SELECT u FROM UserEntity u ORDER BY u.id", UserEntity.class)
                .setFirstResult(page * size).setMaxResults(size).getResultList();
        return new UserPage(page, size, total, users);
    }

    @Override
    @RolesAllowed({"Admin", "Admin-Write"})
    public UserEntity createUser(String username, String email, String firstName, String lastName,
            String password) {
        validateRequired(username, "Username");
        validateProfile(email, firstName, lastName);
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }
        requireKeycloak();
        AdminApiResponse response = keycloakAdminService.userManagement("POST", "/users",
                "{\"username\":\"" + json(username) + "\",\"email\":\"" + json(email)
                + "\",\"firstName\":\"" + json(firstName) + "\",\"lastName\":\""
                + json(lastName) + "\",\"enabled\":true,\"credentials\":[{\"type\":\"password\",\"value\":\""
                + json(password) + "\",\"temporary\":false}]}");
        ensureSuccess(response, "Unable to create user in Keycloak.");
        String keycloakId = response.getBody().trim();
        if (keycloakId.isEmpty() && response.getLocation() != null) {
            String location = response.getLocation();
            keycloakId = location.substring(location.lastIndexOf('/') + 1);
        }
        if (keycloakId.startsWith("{")) {
            int marker = keycloakId.indexOf("\"id\"");
            int start = marker < 0 ? -1 : keycloakId.indexOf('"', keycloakId.indexOf(':', marker) + 1);
            int end = start < 0 ? -1 : keycloakId.indexOf('"', start + 1);
            keycloakId = start < 0 || end < 0 ? null : keycloakId.substring(start + 1, end);
        }
        if (keycloakId == null || keycloakId.trim().isEmpty()) {
            throw new KeycloakAdminException("Keycloak did not return a user identifier.");
        }
        UserEntity user = new UserEntity(keycloakId, username, email, firstName, lastName, true);
        entityManager.persist(user);
        return user;
    }

    @Override
    @RolesAllowed({"Admin", "Admin-Write", "Admin-Update", "User-Write"})
    public UserEntity updateUser(Long id, String username, String email, String firstName, String lastName) {
        validateId(id);
        validateRequired(username, "Username");
        validateProfile(email, firstName, lastName);
        UserEntity user = findRequired(id);
        requireKeycloak();
        AdminApiResponse response = keycloakAdminService.userManagement("PUT",
                "/users/" + user.getKeycloakId(), "{\"username\":\"" + json(username)
                + "\",\"email\":\"" + json(email)
                + "\",\"firstName\":\"" + json(firstName) + "\",\"lastName\":\""
                + json(lastName) + "\"}");
        ensureSuccess(response, "Unable to update user in Keycloak.");
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        return user;
    }

    @Override
    @RolesAllowed({"Admin", "Admin-Write", "Admin-Update", "User-Write"})
    public UserEntity updateUser(Long id, String email, String firstName, String lastName) {
        UserEntity user = findRequired(id);
        return updateUser(id, user.getUsername() == null ? email : user.getUsername(), email, firstName, lastName);
    }

    @Override
    @RolesAllowed({"Admin", "Admin-Write", "Admin-Delete"})
    public void deactivateUser(Long id) {
        validateId(id);
        UserEntity user = findRequired(id);
        requireKeycloak();
        AdminApiResponse response = keycloakAdminService.userManagement("PUT",
                "/users/" + user.getKeycloakId(), "{\"enabled\":false}");
        ensureSuccess(response, "Unable to deactivate user in Keycloak.");
        user.setActive(false);
    }

    @Override
    @RolesAllowed({"Admin", "Admin-Update", "User-Write"})
    public boolean isOwner(Long id, String principal) {
        if (id == null || principal == null || principal.trim().isEmpty()) return false;
        UserEntity user = entityManager.find(UserEntity.class, id);
        if (user == null) return false;
        String normalizedPrincipal = normalize(principal);
        return normalizedPrincipal.equals(normalize(user.getUsername()))
                || normalizedPrincipal.equals(normalize(user.getEmail()))
                || normalizedPrincipal.equals(normalize(user.getKeycloakId()));
    }

    @Override
    @RolesAllowed({"Admin", "Sub-Admin", "User", "Admin-Read", "Sub-Admin-Read", "User-Read"})
    public Long findUserIdByPrincipal(String principal) {
        if (principal == null || principal.trim().isEmpty()) return null;
        String value = principal.trim();
        List<UserEntity> matches = entityManager.createQuery(
                "SELECT u FROM UserEntity u WHERE lower(u.username) = lower(:principal)", UserEntity.class)
                .setParameter("principal", value).setMaxResults(1).getResultList();
        return matches.isEmpty() ? null : matches.get(0).getId();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public KeycloakUserProfile completeRegistration(String username, String email, String firstName,
            String lastName) {
        validateRequired(username, "Username");
        validateProfile(email, firstName, lastName);
        requireKeycloak();

        // The username is resolved server-side.  No caller supplied internal or
        // Keycloak id is ever accepted, preventing completion of another account.
        UserProfileLookup profile = new UserProfileLookup(keycloakAdminService.findUser(username));
        if (profile.value == null || profile.value.getId().trim().isEmpty()) {
            throw new EntityNotFoundException("Pending user was not found.");
        }
        if (profile.value.getRequiredActions().isEmpty()) {
            throw new IllegalStateException("Registration is not pending.");
        }

        String body = "{\"email\":\"" + json(email) + "\",\"firstName\":\""
                + json(firstName) + "\",\"lastName\":\"" + json(lastName)
                + "\",\"requiredActions\":[]}";
        AdminApiResponse response = keycloakAdminService.userManagement("PUT",
                "/users/" + jsonPath(profile.value.getId()), body);
        ensureSuccess(response, "Unable to complete registration in Keycloak.");
        return new KeycloakUserProfile(profile.value.getId(), email, firstName, lastName,
                java.util.Collections.<String>emptyList());
    }

    private UserEntity findRequired(Long id) {
        UserEntity user = entityManager.find(UserEntity.class, id);
        if (user == null) throw new EntityNotFoundException("User not found: " + id);
        return user;
    }
    private void requireKeycloak() {
        if (keycloakAdminService == null) throw new KeycloakAdminException("Keycloak administration is unavailable.");
    }
    private void ensureSuccess(AdminApiResponse response, String message) {
        if (response == null || response.getStatus() < 200 || response.getStatus() >= 300)
            throw new KeycloakAdminException(message);
    }
    private void validateId(Long id) { if (id == null) throw new IllegalArgumentException("User id is required."); }
    private void validatePage(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("Page must not be negative.");
        if (size < 1 || size > MAX_PAGE_SIZE) throw new IllegalArgumentException("Page size must be between 1 and 100.");
    }
    private void validateProfile(String email, String firstName, String lastName) {
        validateRequired(email, "Email"); validateRequired(firstName, "First name"); validateRequired(lastName, "Last name");
    }
    private void validateRequired(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " is required.");
    }
    private String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    private String jsonPath(String value) {
        // IDs returned by Keycloak are opaque, but reject path separators rather
        // than allowing a caller-controlled path to reach the Admin API.
        if (value.indexOf('/') >= 0 || value.indexOf('\\') >= 0 || value.indexOf("..") >= 0)
            throw new KeycloakAdminException("Invalid Keycloak user identifier.");
        return value;
    }
    private static final class UserProfileLookup {
        private final poc.persistence.keycloak.KeycloakUserProfile value;
        UserProfileLookup(poc.persistence.keycloak.KeycloakUserProfile value) { this.value = value; }
    }
}
