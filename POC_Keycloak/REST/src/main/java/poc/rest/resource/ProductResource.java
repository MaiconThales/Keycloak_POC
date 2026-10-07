package poc.rest.resource;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import javax.ejb.EJB;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import poc.persistence.entity.Product;
import poc.persistence.service.ProductServiceLocal;
import poc.rest.dto.ErrorResponse;
import poc.rest.dto.ProductInput;
import poc.rest.dto.ProductResponse;

@Path("products")
@Produces(MediaType.APPLICATION_JSON)
public class ProductResource {
    @EJB
    private ProductServiceLocal productService;

    @GET
    public List<ProductResponse> findAll() {
        return productService.findAll().stream()
                .map(ProductResponse::from)
                .collect(Collectors.toList());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@Valid ProductInput input, @Context UriInfo uriInfo) {
        if (input == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(new ErrorResponse(Response.Status.BAD_REQUEST.getStatusCode(),
                            "Product input is required."))
                    .build();
        }
        Product product = productService.create(input.getName(), input.getPrice(), input.getSku());
        URI location = uriInfo.getAbsolutePathBuilder().path(product.getId().toString()).build();
        return Response.created(location).entity(ProductResponse.from(product)).build();
    }
}
