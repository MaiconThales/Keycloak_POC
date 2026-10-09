package poc.persistence.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.TypedQuery;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import org.junit.jupiter.api.Test;

import poc.persistence.entity.UserEntity;
import poc.persistence.keycloak.KeycloakAdminClient.AdminApiResponse;
import poc.persistence.keycloak.KeycloakAdminService;
import poc.persistence.keycloak.KeycloakUserProfile;

import org.mockito.Mockito;

class UserServiceBeanTest {
    @Test
    void listsWithCountAndOffset() {
        EntityManager em = Mockito.mock(EntityManager.class);
        TypedQuery<Long> count = Mockito.mock(TypedQuery.class);
        TypedQuery<UserEntity> query = Mockito.mock(TypedQuery.class);
        when(em.createQuery("SELECT COUNT(u) FROM UserEntity u", Long.class)).thenReturn(count);
        when(count.getSingleResult()).thenReturn(3L);
        when(em.createQuery("SELECT u FROM UserEntity u ORDER BY u.id", UserEntity.class)).thenReturn(query);
        when(query.setFirstResult(2)).thenReturn(query);
        when(query.setMaxResults(2)).thenReturn(query);
        when(query.getResultList()).thenReturn(java.util.Collections.<UserEntity>emptyList());

        UserPage result = new UserServiceBean(em, Mockito.mock(KeycloakAdminService.class)).listUsers(1, 2);
        assertEquals(3L, result.getTotal());
        verify(query).setFirstResult(2);
    }

    @Test
    void createsInKeycloakThenPersistsLocalProjection() {
        EntityManager em = Mockito.mock(EntityManager.class);
        KeycloakAdminService admin = Mockito.mock(KeycloakAdminService.class);
        when(admin.userManagement(org.mockito.ArgumentMatchers.eq("POST"), org.mockito.ArgumentMatchers.eq("/users"), anyString()))
                .thenReturn(new AdminApiResponse(201, "kc-1"));
        UserEntity result = new UserServiceBean(em, admin).createUser("john", "j@x.test", "John", "Doe", "secret");
        assertEquals("kc-1", result.getKeycloakId());
        verify(em).persist(result);
    }

    @Test
    void updatesBothRepresentations() {
        EntityManager em = Mockito.mock(EntityManager.class);
        KeycloakAdminService admin = Mockito.mock(KeycloakAdminService.class);
        UserEntity user = new UserEntity("kc-1", "old@x.test", "Old", "Name", true);
        when(em.find(UserEntity.class, 7L)).thenReturn(user);
        when(admin.userManagement(org.mockito.ArgumentMatchers.eq("PUT"), org.mockito.ArgumentMatchers.eq("/users/kc-1"), anyString()))
                .thenReturn(new AdminApiResponse(204, ""));
        UserEntity result = new UserServiceBean(em, admin).updateUser(7L, "new@x.test", "New", "Name");
        assertEquals("new@x.test", result.getEmail());
        assertEquals("New", result.getFirstName());
    }

    @Test
    void deactivationIsLogicalAfterKeycloakUpdate() {
        EntityManager em = Mockito.mock(EntityManager.class);
        KeycloakAdminService admin = Mockito.mock(KeycloakAdminService.class);
        UserEntity user = new UserEntity("kc-1", "a@x.test", "A", "B", true);
        when(em.find(UserEntity.class, 4L)).thenReturn(user);
        when(admin.userManagement("PUT", "/users/kc-1", "{\"enabled\":false}"))
                .thenReturn(new AdminApiResponse(204, ""));
        new UserServiceBean(em, admin).deactivateUser(4L);
        assertEquals(Boolean.FALSE, user.getActive());
        verify(em, Mockito.never()).remove(user);
    }

    @Test
    void rejectsInvalidInputAndMissingUsers() {
        UserServiceBean service = new UserServiceBean(Mockito.mock(EntityManager.class), Mockito.mock(KeycloakAdminService.class));
        assertThrows(IllegalArgumentException.class, () -> service.listUsers(-1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.createUser("", "a", "A", "B", "p"));
        EntityManager em = Mockito.mock(EntityManager.class);
        when(em.find(UserEntity.class, 9L)).thenReturn(null);
        assertThrows(EntityNotFoundException.class,
                () -> new UserServiceBean(em, Mockito.mock(KeycloakAdminService.class)).deactivateUser(9L));
    }

    @Test
    void authorizesOwnerByNormalizedUsernameAndRejectsAnotherUsername() {
        EntityManager em = Mockito.mock(EntityManager.class);
        UserEntity user = new UserEntity("kc-1", "John.Doe", "john@x.test", "John", "Doe", true);
        when(em.find(UserEntity.class, 7L)).thenReturn(user);
        UserServiceBean service = new UserServiceBean(em, Mockito.mock(KeycloakAdminService.class));

        assertEquals(true, service.isOwner(7L, "  JOHN.DOE "));
        assertEquals(false, service.isOwner(7L, "jane.doe"));
    }

    @Test
    void rejectsFailedAdministrativeCall() {
        EntityManager em = Mockito.mock(EntityManager.class);
        KeycloakAdminService admin = Mockito.mock(KeycloakAdminService.class);
        when(admin.userManagement(org.mockito.ArgumentMatchers.eq("POST"), org.mockito.ArgumentMatchers.eq("/users"), anyString()))
                .thenReturn(new AdminApiResponse(409, ""));
        assertThrows(RuntimeException.class,
                () -> new UserServiceBean(em, admin).createUser("john", "j@x.test", "John", "Doe", "secret"));
    }

    @Test
    void completesPendingProfileAndRemovesRequiredActions() {
        KeycloakAdminService admin = Mockito.mock(KeycloakAdminService.class);
        when(admin.findUser("john")).thenReturn(new KeycloakUserProfile("kc-1", "", "", "",
                java.util.Arrays.asList("UPDATE_PROFILE")));
        when(admin.userManagement("PUT", "/users/kc-1", "{\"email\":\"j@x.test\",\"firstName\":\"John\",\"lastName\":\"Doe\",\"requiredActions\":[]}"))
                .thenReturn(new AdminApiResponse(204, ""));
        KeycloakUserProfile result = new UserServiceBean(Mockito.mock(EntityManager.class), admin)
                .completeRegistration("john", "j@x.test", "John", "Doe");
        assertEquals("j@x.test", result.getEmail());
        assertEquals(0, result.getRequiredActions().size());
        verify(admin).userManagement("PUT", "/users/kc-1", "{\"email\":\"j@x.test\",\"firstName\":\"John\",\"lastName\":\"Doe\",\"requiredActions\":[]}");
    }

    @Test
    void rejectsUnknownUserAndFailedAdminUpdate() {
        KeycloakAdminService admin = Mockito.mock(KeycloakAdminService.class);
        when(admin.findUser("unknown")).thenReturn(null);
        assertThrows(EntityNotFoundException.class, () -> new UserServiceBean(Mockito.mock(EntityManager.class), admin)
                .completeRegistration("unknown", "a@x.test", "A", "B"));
        when(admin.findUser("john")).thenReturn(new KeycloakUserProfile("kc-1", "", "", "",
                java.util.Arrays.asList("UPDATE_PROFILE")));
        when(admin.userManagement("PUT", "/users/kc-1", "{\"email\":\"a@x.test\",\"firstName\":\"A\",\"lastName\":\"B\",\"requiredActions\":[]}"))
                .thenReturn(new AdminApiResponse(500, ""));
        assertThrows(RuntimeException.class, () -> new UserServiceBean(Mockito.mock(EntityManager.class), admin)
                .completeRegistration("john", "a@x.test", "A", "B"));
    }

    @Test
    void rejectsFailedAdminUpdateAndDeactivationWithoutChangingLocalState() {
        EntityManager em = Mockito.mock(EntityManager.class);
        KeycloakAdminService admin = Mockito.mock(KeycloakAdminService.class);
        UserEntity user = new UserEntity("kc-1", "john", "old@x.test", "Old", "Name", true);
        when(em.find(UserEntity.class, 7L)).thenReturn(user);
        when(admin.userManagement("PUT", "/users/kc-1",
                "{\"username\":\"john\",\"email\":\"new@x.test\",\"firstName\":\"New\",\"lastName\":\"Name\"}"))
                .thenReturn(new AdminApiResponse(500, ""));
        assertThrows(RuntimeException.class, () -> new UserServiceBean(em, admin)
                .updateUser(7L, "john", "new@x.test", "New", "Name"));
        assertEquals("old@x.test", user.getEmail());

        when(admin.userManagement("PUT", "/users/kc-1", "{\"enabled\":false}"))
                .thenReturn(new AdminApiResponse(503, ""));
        assertThrows(RuntimeException.class, () -> new UserServiceBean(em, admin).deactivateUser(7L));
        assertEquals(Boolean.TRUE, user.getActive());
    }

    @Test
    void declaresContainerAndAuthorizationContract() throws Exception {
        assertNotNull(UserServiceBean.class.getAnnotation(Stateless.class));
        assertEquals(TransactionAttributeType.REQUIRED,
                UserServiceBean.class.getAnnotation(TransactionAttribute.class).value());
        assertArrayEquals(new String[] {"Admin", "Admin-Read"}, UserServiceBean.class
                .getMethod("listUsers", int.class, int.class).getAnnotation(RolesAllowed.class).value());
        assertArrayEquals(new String[] {"Admin", "Admin-Write"}, UserServiceBean.class
                .getMethod("createUser", String.class, String.class, String.class, String.class, String.class)
                .getAnnotation(RolesAllowed.class).value());
        assertArrayEquals(new String[] {"Admin", "Admin-Write", "Admin-Delete"}, UserServiceBean.class
                .getMethod("deactivateUser", Long.class).getAnnotation(RolesAllowed.class).value());
    }
}
