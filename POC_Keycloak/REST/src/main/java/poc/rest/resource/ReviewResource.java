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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import poc.persistence.entity.Review;
import poc.persistence.service.ReviewServiceLocal;
import poc.rest.dto.ReviewInput;
import poc.rest.dto.ReviewResponse;

/** HTTP facade only; authorization, ownership and transactions belong to the EJB. */
@Path("reviews")
@Produces(MediaType.APPLICATION_JSON)
public class ReviewResource {
    @EJB
    private ReviewServiceLocal reviewService;

    @GET
    public List<ReviewResponse> findByProduct(@QueryParam("productId") Long productId) {
        return reviewService.findByProduct(productId).stream()
                .map(ReviewResponse::from).collect(Collectors.toList());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@Valid ReviewInput input, @Context UriInfo uriInfo) {
        if (input == null) {
            return badRequest("Review input is required.");
        }
        Review review = reviewService.create(input.getProductId(), input.getComment());
        URI location = uriInfo.getAbsolutePathBuilder().path(review.getId().toString()).build();
        return Response.created(location).entity(ReviewResponse.from(review)).build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(@PathParam("id") Long id, @Valid ReviewInput input) {
        if (input == null) {
            return badRequest("Review input is required.");
        }
        Review review = reviewService.update(id, input.getComment());
        return Response.ok(ReviewResponse.from(review)).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        reviewService.delete(id);
        return Response.noContent().build();
    }

    private Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new poc.rest.dto.ErrorResponse(400, message)).build();
    }
}
