package poc.rest.exception;

import javax.ejb.EJBAccessException;
import javax.persistence.EntityNotFoundException;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import poc.rest.dto.ErrorResponse;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {
    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof WebApplicationException) {
            Response original = ((WebApplicationException) exception).getResponse();
            Response.StatusType status = original == null ? null : original.getStatusInfo();
            int statusCode = status == null
                    ? Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()
                    : original.getStatus();
            return response(statusCode, safeMessage(statusCode));
        }
        if (exception instanceof ConstraintViolationException) {
            return response(Response.Status.BAD_REQUEST.getStatusCode(),
                    "Request validation failed.");
        }
        if (exception instanceof IllegalArgumentException) {
            return response(Response.Status.BAD_REQUEST.getStatusCode(), "Invalid request.");
        }
        if (exception instanceof EntityNotFoundException) {
            return response(Response.Status.NOT_FOUND.getStatusCode(), "Resource not found.");
        }
        if (isAccessDenied(exception)) {
            return response(Response.Status.FORBIDDEN.getStatusCode(),
                    "Insufficient permissions.");
        }
        return response(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                "An unexpected error occurred.");
    }

    /**
     * EJB authorization failures are normally delivered as EJBAccessException,
     * but an invocation boundary may wrap them in EJBException.  Inspect only
     * the cause chain here so that the response remains safe and no container
     * implementation detail is exposed to the client.
     */
    private boolean isAccessDenied(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof EJBAccessException || current instanceof SecurityException) {
                return true;
            }
            if (current == current.getCause()) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    private Response response(int statusCode, String message) {
        return Response.status(statusCode)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorResponse(statusCode, message))
                .build();
    }

    private String safeMessage(int statusCode) {
        switch (statusCode) {
            case 400:
                return "Invalid request.";
            case 401:
                return "Authentication is required.";
            case 403:
                return "Insufficient permissions.";
            case 404:
                return "Resource not found.";
            case 405:
                return "HTTP method is not supported for this resource.";
            case 406:
                return "Requested representation is not available.";
            case 409:
                return "Request conflicts with the current resource state.";
            case 415:
                return "Unsupported request content type.";
            case 503:
                return "Service is temporarily unavailable.";
            default:
                return "An unexpected error occurred.";
        }
    }
}
