package poc.rest;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;

import javax.ejb.EJBAccessException;
import javax.ejb.EJBException;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.junit.jupiter.api.Test;

import poc.persistence.entity.Product;
import poc.persistence.service.ProductServiceLocal;
import poc.rest.dto.ErrorResponse;
import poc.rest.dto.LoginRequest;
import poc.rest.dto.LoginResponse;
import poc.rest.dto.ProductInput;
import poc.rest.dto.ProductResponse;
import poc.rest.exception.GlobalExceptionMapper;
import poc.rest.resource.ProductResource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RestComponentsTest {
    @Test
    void configuresApiApplicationPath() {
        ApiApplication application = new ApiApplication();

        assertEquals("/api/v1", application.getClass()
                .getAnnotation(javax.ws.rs.ApplicationPath.class).value());
    }

    @Test
    void mapsAndRoundTripsProductDtos() {
        Product product = new Product("Notebook", new BigDecimal("4500.25"), "NTB");
        product.setId(12L);
        ProductResponse mapped = ProductResponse.from(product);
        assertEquals(Long.valueOf(12L), mapped.getId());
        assertEquals("Notebook", mapped.getName());
        assertEquals(new BigDecimal("4500.25"), mapped.getPrice());
        assertEquals("NTB", mapped.getSku());

        ProductResponse response = new ProductResponse();
        response.setId(13L);
        response.setName("Mouse");
        response.setPrice(new BigDecimal("49.99"));
        response.setSku("MSE");
        assertEquals(Long.valueOf(13L), response.getId());
        assertEquals("Mouse", response.getName());
        assertEquals(new BigDecimal("49.99"), response.getPrice());
        assertEquals("MSE", response.getSku());

        ProductInput input = new ProductInput();
        input.setName("Keyboard");
        input.setPrice(new BigDecimal("89.50"));
        input.setSku("KBD");
        assertEquals("Keyboard", input.getName());
        assertEquals(new BigDecimal("89.50"), input.getPrice());
        assertEquals("KBD", input.getSku());

        LoginRequest login = new LoginRequest();
        login.setUsername("user");
        login.setPassword("password");
        assertEquals("user", login.getUsername());
        assertEquals("password", login.getPassword());
    }

    @Test
    void mapsAndRoundTripsResponseDtos() {
        LoginResponse login = new LoginResponse("token", 20L, "Bearer");
        assertEquals("token", login.getAccessToken());
        assertEquals(20L, login.getExpiresIn());
        assertEquals("Bearer", login.getTokenType());
        login.setAccessToken("updated");
        login.setExpiresIn(30L);
        login.setTokenType("Custom");
        assertEquals("updated", login.getAccessToken());
        assertEquals(30L, login.getExpiresIn());
        assertEquals("Custom", login.getTokenType());
        assertNull(new LoginResponse().getAccessToken());

        ErrorResponse error = new ErrorResponse(400, "bad request");
        assertEquals(400, error.getStatusCode());
        assertEquals("bad request", error.getMessage());
        assertNotNull(error.getTimestamp());
        error.setStatusCode(500);
        error.setMessage("failure");
        error.setTimestamp("now");
        assertEquals(500, error.getStatusCode());
        assertEquals("failure", error.getMessage());
        assertEquals("now", error.getTimestamp());
        assertEquals(0, new ErrorResponse().getStatusCode());
    }

    @Test
    void listsProductsThroughTheServiceFacade() throws Exception {
        ProductResource resource = new ProductResource();
        ProductServiceLocal service = mock(ProductServiceLocal.class);
        injectService(resource, service);
        when(service.findAll()).thenReturn(Arrays.asList(
                new Product("Notebook", new BigDecimal("10.00"), "NTB"),
                new Product("Mouse", new BigDecimal("5.00"), "MSE")));

        java.util.List<ProductResponse> products = resource.findAll();
        assertEquals(Arrays.asList("NTB", "MSE"), Arrays.asList(
                products.get(0).getSku(), products.get(1).getSku()));
        verify(service).findAll();
    }

    @Test
    void rejectsNullProductInput() throws Exception {
        ProductResource resource = new ProductResource();
        ProductServiceLocal service = mock(ProductServiceLocal.class);
        injectService(resource, service);

        Response response = resource.create(null, mock(UriInfo.class));

        assertEquals(400, response.getStatus());
        assertEquals(400, ((ErrorResponse) response.getEntity()).getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    void createsProductAndReturnsItsLocation() throws Exception {
        ProductResource resource = new ProductResource();
        ProductServiceLocal service = mock(ProductServiceLocal.class);
        injectService(resource, service);
        Product product = new Product("Notebook", new BigDecimal("10.00"), "NTB");
        product.setId(42L);
        when(service.create("Notebook", new BigDecimal("10.00"), "NTB")).thenReturn(product);
        ProductInput input = new ProductInput();
        input.setName("Notebook");
        input.setPrice(new BigDecimal("10.00"));
        input.setSku("NTB");
        UriInfo uriInfo = mock(UriInfo.class);
        UriBuilder uriBuilder = mock(UriBuilder.class);
        when(uriInfo.getAbsolutePathBuilder()).thenReturn(uriBuilder);
        when(uriBuilder.path("42")).thenReturn(uriBuilder);
        when(uriBuilder.build()).thenReturn(URI.create("http://localhost/api/v1/products/42"));

        Response response = resource.create(input, uriInfo);

        assertEquals(201, response.getStatus());
        assertEquals(URI.create("http://localhost/api/v1/products/42"), response.getLocation());
        assertEquals(Long.valueOf(42L), ((ProductResponse) response.getEntity()).getId());
        verify(service).create("Notebook", new BigDecimal("10.00"), "NTB");
    }

    @Test
    void mapsWebAndValidationErrorsWithoutExposingExceptionDetails() {
        GlobalExceptionMapper mapper = new GlobalExceptionMapper();

        assertMapped(mapper.toResponse(new NotFoundException("private detail")), 404,
                "Resource not found.");
        assertMapped(mapper.toResponse(new ConstraintViolationException("invalid",
                Collections.emptySet())), 400, "Request validation failed.");
        assertMapped(mapper.toResponse(new EJBAccessException("private detail")), 403,
                "Insufficient permissions.");
        assertMapped(mapper.toResponse(new SecurityException("private detail")), 403,
                "Insufficient permissions.");
        assertMapped(mapper.toResponse(new EJBException(
                new EJBAccessException("private container detail"))), 403,
                "Insufficient permissions.");
        assertMapped(mapper.toResponse(new IllegalStateException("private detail")), 500,
                "An unexpected error occurred.");
    }

    @Test
    void formatsAccessDeniedAsJsonErrorResponseWithoutStackTrace() {
        Response response = new GlobalExceptionMapper().toResponse(
                new EJBAccessException("role Admin-Write is required"));

        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
        assertEquals(javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertEquals(403, error.getStatusCode());
        assertEquals("Insufficient permissions.", error.getMessage());
        assertNotNull(error.getTimestamp());
        assertFalse(error.getMessage().contains("Admin-Write"));
    }

    @Test
    void mapsCommonHttpErrorsToSafeMessages() {
        GlobalExceptionMapper mapper = new GlobalExceptionMapper();
        int[] statuses = {400, 401, 403, 404, 405, 406, 409, 415, 503, 418};
        String[] messages = {
                "Invalid request.", "Authentication is required.", "Insufficient permissions.",
                "Resource not found.", "HTTP method is not supported for this resource.",
                "Requested representation is not available.",
                "Request conflicts with the current resource state.",
                "Unsupported request content type.", "Service is temporarily unavailable.",
                "An unexpected error occurred."
        };
        for (int i = 0; i < statuses.length; i++) {
            assertMapped(mapper.toResponse(new javax.ws.rs.WebApplicationException(statuses[i])),
                    statuses[i], messages[i]);
        }
    }

    private void assertMapped(Response response, int status, String message) {
        assertEquals(status, response.getStatus());
        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertEquals(status, error.getStatusCode());
        assertEquals(message, error.getMessage());
        assertFalse(error.getMessage().contains("private detail"));
    }

    private void injectService(ProductResource resource, ProductServiceLocal service)
            throws ReflectiveOperationException {
        java.lang.reflect.Field field = ProductResource.class.getDeclaredField("productService");
        field.setAccessible(true);
        field.set(resource, service);
    }
}
