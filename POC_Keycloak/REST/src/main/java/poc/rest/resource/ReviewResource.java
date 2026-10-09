package poc.rest.resource;

import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.security.RolesAllowed;
import javax.ejb.EJB;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import poc.persistence.entity.Review;
import poc.persistence.service.ReviewServiceLocal;
import poc.persistence.service.UserServiceLocal;
import poc.rest.dto.ErrorResponse;
import poc.rest.dto.ReviewInput;
import poc.rest.dto.ReviewResponse;

/** REST facade for reviews. Identity and business rules remain in EJBs. */
@Path("reviews")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ReviewResource {
    @EJB private ReviewServiceLocal reviewService;
    @EJB private UserServiceLocal userService;
    @Context private SecurityContext securityContext;

    public ReviewResource() { }
    ReviewResource(ReviewServiceLocal reviews, UserServiceLocal users, SecurityContext security) {
        reviewService = reviews; userService = users; securityContext = security;
    }

    @GET
    @RolesAllowed({"Admin", "Sub-Admin", "User", "Admin-Read", "Sub-Admin-Read", "User-Read"})
    public List<ReviewResponse> find(@QueryParam("productId") Long productId,
            @QueryParam("rating") Integer rating) {
        return reviewService.findReviews(productId, rating).stream()
                .map(ReviewResponse::from).collect(Collectors.toList());
    }

    @POST
    @RolesAllowed({"User", "User-Write"})
    public Response create(ReviewInput input) {
        if (!valid(input, true)) return error("Product id, rating and comment are required.");
        Long authorId = currentUserId();
        if (authorId == null) return error(Response.Status.FORBIDDEN, "Authenticated user was not found.");
        Review review = reviewService.createReview(input.getProductId(), authorId,
                input.getRating(), input.getComment().trim());
        return Response.status(Response.Status.CREATED).entity(ReviewResponse.from(review)).build();
    }

    @PUT
    @Path("{id}")
    @RolesAllowed({"Admin", "Sub-Admin", "User", "Admin-Update", "Sub-Admin-Update", "User-Update"})
    public Response update(@PathParam("id") Long id, ReviewInput input) {
        if (id == null || !valid(input, true)) return error("Review id, product id, rating and comment are required.");
        Long authorId = currentUserId();
        if (authorId == null) return error(Response.Status.FORBIDDEN, "Authenticated user was not found.");
        Review review = reviewService.updateReview(id, authorId, input.getRating(), input.getComment().trim());
        return Response.ok(ReviewResponse.from(review)).build();
    }

    @DELETE
    @Path("{id}")
    @RolesAllowed({"Admin", "Sub-Admin", "User", "Admin-Delete", "Sub-Admin-Delete", "User-Delete"})
    public Response delete(@PathParam("id") Long id) {
        if (id == null) return error("Review id is required.");
        Long authorId = currentUserId();
        if (authorId == null) return error(Response.Status.FORBIDDEN, "Authenticated user was not found.");
        reviewService.deleteReview(id, authorId);
        return Response.noContent().build();
    }

    private Long currentUserId() {
        if (securityContext == null || securityContext.getUserPrincipal() == null || userService == null) return null;
        return userService.findUserIdByPrincipal(securityContext.getUserPrincipal().getName());
    }
    private boolean valid(ReviewInput input, boolean productRequired) {
        return input != null && (!productRequired || input.getProductId() != null)
                && input.getRating() != null && input.getRating() >= 1 && input.getRating() <= 5
                && input.getComment() != null && !input.getComment().trim().isEmpty();
    }
    private Response error(String message) { return error(Response.Status.BAD_REQUEST, message); }
    private Response error(Response.Status status, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorResponse(status.getStatusCode(), message)).build();
    }
}
