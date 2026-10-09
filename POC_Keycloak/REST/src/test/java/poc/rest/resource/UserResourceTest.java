package poc.rest.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.security.Principal;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.Response;
import poc.persistence.entity.UserEntity;
import poc.persistence.service.UserPage;
import poc.rest.dto.UserInput;
import poc.rest.dto.UserPageResponse;
import poc.rest.dto.UserResponse;

import org.junit.jupiter.api.Test;

import poc.persistence.service.UserServiceLocal;
import poc.rest.dto.CompleteRegistrationInput;
import poc.rest.dto.CompleteRegistrationResponse;
import poc.rest.dto.ErrorResponse;

class UserResourceTest {
    private final UserEntity user = user();

    @Test void listsUsersWithoutExposingEntity() {
        UserServiceLocal service = mock(UserServiceLocal.class);
        when(service.listUsers(1, 2)).thenReturn(new UserPage(1, 2, 1, java.util.Arrays.asList(user)));
        Response response = new UserResource(service).list(1, 2);
        UserPageResponse page = (UserPageResponse) response.getEntity();
        assertEquals(200, response.getStatus()); assertEquals(1, page.getUsers().size());
        assertEquals("j@x.test", page.getUsers().get(0).getEmail());
    }

    @Test void createsUserAndDoesNotReturnPassword() {
        UserServiceLocal service = mock(UserServiceLocal.class);
        when(service.createUser("john", "j@x.test", "John", "Doe", "secret")).thenReturn(user);
        UserInput input = input("john", "j@x.test", "John", "Doe", "secret");
        Response response = new UserResource(service).create(input);
        assertEquals(201, response.getStatus()); assertEquals("j@x.test", ((UserResponse) response.getEntity()).getEmail());
        verify(service).createUser("john", "j@x.test", "John", "Doe", "secret");
    }

    @Test void updatesOwnUserAndDeletesOnlyAdmin() {
        UserServiceLocal service = mock(UserServiceLocal.class);
        when(service.isOwner(7L, "john")).thenReturn(true); when(service.updateUser(7L, "j@x.test", "John", "Doe")).thenReturn(user);
        SecurityContext context = context("john", false);
        UserResource resource = new UserResource(service, context);
        assertEquals(200, resource.update(7L, input("john", "j@x.test", "John", "Doe", null)).getStatus());
        assertEquals(403, resource.delete(7L).getStatus()); verify(service).updateUser(7L, "j@x.test", "John", "Doe");
    }

    @Test void adminCanDeleteAndInvalidPayloadNeverCallsService() {
        UserServiceLocal service = mock(UserServiceLocal.class);
        SecurityContext context = context("admin", true); UserResource resource = new UserResource(service, context);
        assertEquals(204, resource.delete(7L).getStatus()); verify(service).deactivateUser(7L);
        assertEquals(400, resource.create(null).getStatus());
    }
    @Test
    void completesUsingUsernameAndReturnsSafeResult() {
        UserServiceLocal service = mock(UserServiceLocal.class);
        UserResource resource = new UserResource(service);
        CompleteRegistrationInput input = input("john", "j@x.test", "John", "Doe");
        javax.ws.rs.core.Response response = resource.completeRegistration(input);
        assertEquals(200, response.getStatus());
        assertEquals("REGISTRATION_COMPLETED", ((CompleteRegistrationResponse) response.getEntity()).getStatus());
        verify(service).completeRegistration("john", "j@x.test", "John", "Doe");
    }

    @Test
    void rejectsInvalidDataBeforeCallingBusinessLayer() {
        UserServiceLocal service = mock(UserServiceLocal.class);
        javax.ws.rs.core.Response response = new UserResource(service).completeRegistration(null);
        assertEquals(400, response.getStatus());
        assertEquals(400, ((ErrorResponse) response.getEntity()).getStatusCode());
    }

    private CompleteRegistrationInput input(String username, String email, String firstName, String lastName) {
        CompleteRegistrationInput input = new CompleteRegistrationInput();
        input.setUsername(username); input.setEmail(email); input.setFirstName(firstName); input.setLastName(lastName);
        return input;
    }

    private UserInput input(String username, String email, String firstName, String lastName, String password) {
        UserInput input = new UserInput(); input.setUsername(username); input.setEmail(email);
        input.setFirstName(firstName); input.setLastName(lastName); input.setPassword(password); return input;
    }
    private UserEntity user() { UserEntity result = new UserEntity("kc", "j@x.test", "John", "Doe", true); result.setId(7L); return result; }
    private SecurityContext context(String name, boolean admin) {
        SecurityContext context = mock(SecurityContext.class); when(context.getUserPrincipal()).thenReturn(new Principal() { public String getName() { return name; } });
        when(context.isUserInRole("Admin")).thenReturn(admin); return context;
    }
}
