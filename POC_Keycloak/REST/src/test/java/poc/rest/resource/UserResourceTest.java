package poc.rest.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Arrays;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import poc.persistence.service.UserProfile;
import poc.persistence.service.UserServiceLocal;
import poc.rest.dto.UserInput;
import poc.rest.dto.UserResponse;

class UserResourceTest {
    private UserResource resource;
    private UserServiceLocal service;
    private UriInfo uriInfo;

    @BeforeEach
    void setUp() throws Exception {
        resource = new UserResource();
        service = mock(UserServiceLocal.class);
        uriInfo = mock(UriInfo.class);
        set(resource, "userService", service);
    }

    @Test
    void listsUsersThroughTheEjb() {
        when(service.findAll()).thenReturn(Arrays.asList(user("1", "alice", "a@example.com")));
        assertEquals("alice", resource.findAll().get(0).getUsername());
        verify(service).findAll();
    }

    @Test
    void createsAndReturnsLocation() {
        UserInput input = input("alice", "a@example.com");
        when(service.create("alice", "a@example.com")).thenReturn(user("1", "alice", "a@example.com"));
        UriBuilder builder = mock(UriBuilder.class);
        when(uriInfo.getAbsolutePathBuilder()).thenReturn(builder);
        when(builder.path("1")).thenReturn(builder);
        when(builder.build()).thenReturn(URI.create("http://localhost/api/v1/users/1"));

        Response response = resource.create(input, uriInfo);

        assertEquals(201, response.getStatus());
        assertEquals(URI.create("http://localhost/api/v1/users/1"), response.getLocation());
        assertEquals("1", ((UserResponse) response.getEntity()).getId());
        verify(service).create("alice", "a@example.com");
    }

    @Test
    void updatesAndDeletesUsingPathId() {
        UserInput input = input("bob", "b@example.com");
        when(service.update("1", "bob", "b@example.com")).thenReturn(user("1", "bob", "b@example.com"));
        assertEquals(200, resource.update("1", input).getStatus());
        assertEquals(204, resource.delete("1").getStatus());
        verify(service).update("1", "bob", "b@example.com");
        verify(service).delete("1");
    }

    @Test
    void rejectsMissingPayloadWithoutCallingEjb() {
        assertEquals(400, resource.create(null, uriInfo).getStatus());
        assertEquals(400, resource.update("1", null).getStatus());
        verifyNoInteractions(service);
    }

    @Test
    void facadeUsesEjbAndDoesNotDeclareEntityManager() {
        assertFalse(Arrays.stream(UserResource.class.getDeclaredFields())
                .anyMatch(field -> "javax.persistence.EntityManager".equals(field.getType().getName())));
        assertEquals(UserServiceLocal.class, findServiceField().getType());
        assertEquals(javax.ejb.EJB.class, findServiceField().getAnnotation(javax.ejb.EJB.class).annotationType());
    }

    private Field findServiceField() {
        try {
            return UserResource.class.getDeclaredField("userService");
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }

    private UserInput input(String username, String email) {
        UserInput input = new UserInput();
        input.setUsername(username);
        input.setEmail(email);
        return input;
    }

    private UserProfile user(String id, String username, String email) {
        return new UserProfile(id, username, email);
    }

    private void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
