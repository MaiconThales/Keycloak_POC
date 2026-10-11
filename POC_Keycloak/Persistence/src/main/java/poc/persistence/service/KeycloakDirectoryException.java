package poc.persistence.service;

import javax.ejb.ApplicationException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

@ApplicationException(rollback = true)
public class KeycloakDirectoryException extends WebApplicationException {
    private static final long serialVersionUID = 1L;

    KeycloakDirectoryException(Response.Status status) {
        super(Response.status(status).build());
    }
}
