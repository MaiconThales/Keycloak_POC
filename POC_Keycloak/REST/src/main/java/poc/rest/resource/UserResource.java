package poc.rest.resource;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import javax.ejb.EJB;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import poc.persistence.service.UserProfile;
import poc.persistence.service.UserServiceLocal;
import poc.rest.dto.ErrorResponse;
import poc.rest.dto.UserInput;
import poc.rest.dto.UserResponse;

/**
 * Administrative HTTP facade. Authorization and transactions are deliberately
 * kept in UserServiceBean; this class only maps HTTP data to the EJB contract.
 */
@Path("users")
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {
    @EJB
    private UserServiceLocal userService;

    @GET
    public List<UserResponse> findAll() {
        return userService.findAll().stream().map(UserResponse::from)
                .collect(Collectors.toList());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@Valid UserInput input, @Context UriInfo uriInfo) {
        if (input == null) {
            return badRequest("User input is required.");
        }
        UserProfile user = userService.create(input.getUsername(), input.getEmail());
        URI location = uriInfo.getAbsolutePathBuilder().path(user.getId()).build();
        return Response.created(location).entity(UserResponse.from(user)).build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(@PathParam("id") String id, @Valid UserInput input) {
        if (input == null) {
            return badRequest("User input is required.");
        }
        UserProfile user = userService.update(id, input.getUsername(), input.getEmail());
        return Response.ok(UserResponse.from(user)).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        userService.delete(id);
        return Response.noContent().build();
    }

    private Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorResponse(Response.Status.BAD_REQUEST.getStatusCode(), message))
                .build();
    }
}
