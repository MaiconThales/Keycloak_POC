package poc.rest.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Arrays;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import poc.persistence.entity.Product;
import poc.persistence.entity.Review;
import poc.persistence.service.ReviewServiceLocal;
import poc.rest.dto.ReviewInput;

class ReviewResourceTest {
    private ReviewResource resource;
    private ReviewServiceLocal service;
    private UriInfo uriInfo;

    @BeforeEach
    void setUp() throws Exception {
        resource = new ReviewResource();
        service = mock(ReviewServiceLocal.class);
        uriInfo = mock(UriInfo.class);
        set(resource, "reviewService", service);
    }

    @Test
    void getsReviewsUsingProductId() {
        Review review = review(7L, 11L, "hello");
        org.mockito.Mockito.when(service.findByProduct(11L)).thenReturn(Arrays.asList(review));
        assertEquals(11L, resource.findByProduct(11L).get(0).getProductId());
        verify(service).findByProduct(11L);
    }

    @Test
    void createsWithoutAcceptingAnIdentityField() throws Exception {
        ReviewInput input = input(11L, "hello");
        Review review = review(7L, 11L, "hello");
        org.mockito.Mockito.when(service.create(11L, "hello")).thenReturn(review);
        org.mockito.Mockito.when(uriInfo.getAbsolutePathBuilder()).thenReturn(UriBuilder.fromUri("http://localhost/reviews"));
        Response response = resource.create(input, uriInfo);
        assertEquals(201, response.getStatus());
        verify(service).create(11L, "hello");
        assertFalse(ReviewInput.class.getDeclaredFields()[0].getName().toLowerCase().contains("user"));
    }

    @Test
    void updatesAndDeletesByPathId() {
        ReviewInput input = input(999L, "changed");
        Review review = review(7L, 11L, "changed");
        org.mockito.Mockito.when(service.update(7L, "changed")).thenReturn(review);
        assertEquals(200, resource.update(7L, input).getStatus());
        assertEquals(204, resource.delete(7L).getStatus());
        verify(service).update(7L, "changed");
        verify(service).delete(7L);
    }

    @Test
    void rejectsMissingPayload() {
        assertEquals(400, resource.create(null, uriInfo).getStatus());
        assertEquals(400, resource.update(1L, null).getStatus());
    }

    private ReviewInput input(Long productId, String comment) {
        ReviewInput input = new ReviewInput(); input.setProductId(productId); input.setComment(comment); return input;
    }
    private Review review(Long id, Long productId, String comment) {
        Product product = new Product(); product.setId(productId);
        Review review = new Review(product, "token-user", comment); review.setId(id); return review;
    }
    private void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
