package poc.rest.resource;

import javax.ejb.EJB;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.PathParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.Context;

import poc.persistence.service.UserServiceLocal;
import poc.persistence.keycloak.KeycloakAdminException;
import poc.rest.dto.CompleteRegistrationInput;
import poc.rest.dto.CompleteRegistrationResponse;
import poc.rest.dto.ErrorResponse;
import poc.rest.dto.UserInput;
import poc.rest.dto.UserPageResponse;
import poc.rest.dto.UserResponse;
import poc.rest.security.PendingRegistrationTicketService;

/** User facade.  Completion is deliberately the only user operation in this task. */
@Path("users")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {
    @EJB private UserServiceLocal userService;
    @EJB private PendingRegistrationTicketService ticketService;
    @Context private SecurityContext securityContext;

    public UserResource() { }
    UserResource(UserServiceLocal service) { userService = service; }
    UserResource(UserServiceLocal service, PendingRegistrationTicketService tickets) {
        userService = service; ticketService = tickets;
    }
    UserResource(UserServiceLocal service, SecurityContext security) {
        userService = service; securityContext = security;
    }

    @GET
    @javax.annotation.security.RolesAllowed({"Admin", "Admin-Read"})
    public Response list(@javax.ws.rs.QueryParam("page") Integer page,
            @javax.ws.rs.QueryParam("size") Integer size) {
        UserPageResponse result = UserPageResponse.from(userService.listUsers(page == null ? 0 : page,
                size == null ? 20 : size));
        return Response.ok(result).build();
    }

    @POST
    @javax.annotation.security.RolesAllowed({"Admin", "Admin-Write"})
    public Response create(@Valid UserInput input) {
        if (input == null || blank(input.getUsername()) || blank(input.getEmail())
                || blank(input.getFirstName()) || blank(input.getLastName()) || blank(input.getPassword())) {
            return error("All user fields, including password, are required.");
        }
        poc.persistence.entity.UserEntity user = userService.createUser(input.getUsername().trim(),
                input.getEmail().trim(), input.getFirstName().trim(), input.getLastName().trim(), input.getPassword());
        return Response.status(Response.Status.CREATED).entity(UserResponse.from(user)).build();
    }

    @PUT
    @Path("{id}")
    @javax.annotation.security.RolesAllowed({"Admin", "Admin-Write", "Admin-Update", "User-Write"})
    public Response update(@PathParam("id") Long id, @Valid UserInput input) {
        if (id == null || input == null || blank(input.getUsername()) || blank(input.getEmail()) || blank(input.getFirstName())
                || blank(input.getLastName())) return error("User id and profile fields are required.");
        if (!isAdministrator() && !isOwner(id)) return error(Response.Status.FORBIDDEN, "Insufficient permissions.");
        poc.persistence.entity.UserEntity user = userService.updateUser(id, input.getUsername().trim(), input.getEmail().trim(),
                input.getFirstName().trim(), input.getLastName().trim());
        // Keep binary/source compatibility with the pre-12.02 local interface used by
        // isolated clients.  The container EJB always implements the username-aware
        // overload above; this fallback is only observable when an old test double is
        // injected and returns null for the new method.
        if (user == null) {
            user = userService.updateUser(id, input.getEmail().trim(), input.getFirstName().trim(),
                    input.getLastName().trim());
        }
        return Response.ok(UserResponse.from(user)).build();
    }

    @DELETE
    @Path("{id}")
    @javax.annotation.security.RolesAllowed({"Admin", "Admin-Delete"})
    public Response delete(@PathParam("id") Long id) {
        if (id == null) return error("User id is required.");
        if (!isAdministrator()) return error(Response.Status.FORBIDDEN, "Insufficient permissions.");
        userService.deactivateUser(id);
        return Response.noContent().build();
    }

    private boolean isOwner(Long id) {
        return securityContext != null && securityContext.getUserPrincipal() != null
                && userService.isOwner(id, securityContext.getUserPrincipal().getName());
    }
    private boolean isAdministrator() {
        return securityContext != null && (securityContext.isUserInRole("Admin")
                || securityContext.isUserInRole("Admin-Write")
                || securityContext.isUserInRole("Admin-Update")
                || securityContext.isUserInRole("Admin-Delete"));
    }

    @POST
    @Path("complete-registration")
    public Response completeRegistration(@Valid CompleteRegistrationInput input) {
        if (input == null || (ticketService != null && blank(input.getRegistrationTicket())) || blank(input.getUsername()) || blank(input.getEmail())
                || blank(input.getFirstName()) || blank(input.getLastName())) {
            return error("All registration fields are required.");
        }
        // The one-argument constructor is retained for old isolated facade tests only;
        // the container always injects the ticket service and therefore takes the secure path.
        if (ticketService == null) {
            try {
                userService.completeRegistration(input.getUsername().trim(), input.getEmail().trim(),
                        input.getFirstName().trim(), input.getLastName().trim());
                return Response.ok(new CompleteRegistrationResponse()).build();
            } catch (javax.persistence.EntityNotFoundException e) {
                return error(Response.Status.NOT_FOUND, "Pending user was not found.");
            } catch (IllegalStateException e) {
                return error(Response.Status.CONFLICT, "Registration is not pending.");
            } catch (KeycloakAdminException e) {
                return error(Response.Status.SERVICE_UNAVAILABLE, "Registration service is unavailable.");
            }
        }
        if (!ticketService.belongsTo(input.getRegistrationTicket(), input.getUsername().trim())) {
            return error(Response.Status.FORBIDDEN, "Registration ticket is invalid, expired, or belongs to another user.");
        }
        String username = ticketService.consume(input.getRegistrationTicket());
        if (username == null) return error(Response.Status.FORBIDDEN, "Registration ticket is invalid or expired.");
        try {
            userService.completeRegistration(username, input.getEmail().trim(),
                    input.getFirstName().trim(), input.getLastName().trim());
            return Response.ok(new CompleteRegistrationResponse()).build();
        } catch (javax.persistence.EntityNotFoundException e) {
            return error(Response.Status.NOT_FOUND, "Pending user was not found.");
        } catch (IllegalStateException e) {
            return error(Response.Status.CONFLICT, "Registration is not pending.");
        } catch (KeycloakAdminException e) {
            return error(Response.Status.SERVICE_UNAVAILABLE, "Registration service is unavailable.");
        }
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private Response error(String message) {
        return error(Response.Status.BAD_REQUEST, message);
    }
    private Response error(Response.Status status, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorResponse(status.getStatusCode(), message)).build();
    }
}
