# Keycloak JWT filter for WildFly 10

This JAX-RS 2.0 `ContainerRequestFilter` protects `POST /products` and permits
only requests with a valid Keycloak JWT containing the `admin` realm role or a
client role named `admin`. Other API routes remain public, matching `.spec/spec.md`.

The root-level WildFly configuration at
`../../../wildfly-10.0.0.Final/standalone/configuration/standalone.xml` is configured with the Keycloak
application realm issuer (`http://localhost:8081/realms/poc-keycloak`). Keep this value
identical to the token's `iss` claim. For another realm, edit the
`keycloak.issuer` system property in `standalone.xml`, or override it on startup:

```bat
standalone.bat -Dkeycloak.issuer=http://localhost:8081/realms/poc-keycloak
```

When WildFly runs in a container, use a URL reachable from that container
instead of `localhost`.

Assign the `admin` role in the configured realm to users authorized to create
products. Tokens from users without that role receive HTTP 403.

Package this class in the API deployment (for example, under `WEB-INF/classes`
or in `WEB-INF/lib`) so JAX-RS provider scanning discovers `@Provider`. WildFly
10 supplies JAX-RS 2.0, JSON-P, Java 8 cryptography, and the PicketBox module;
no Keycloak adapter or private copy of those server libraries is needed. The filter retrieves the realm JWKS from
`<issuer>/protocol/openid-connect/certs`, validates RS256 signatures and token
issuer/expiration, and caches signing keys for five minutes. Key endpoint
failures return HTTP 503; invalid tokens return HTTP 401 and valid tokens
without the required role return HTTP 403.

For EJB enforcement, a valid admin JWT is mapped to a temporary PicketBox
security identity for the request. The response filter restores the previous
WildFly identity after the request; users without the role never receive this
identity. This lets `ProductServiceBean.create` retain its `@RolesAllowed`
check instead of relying solely on the REST filter. The WAR declares the
WildFly 10 `org.picketbox` module in
`WEB-INF/jboss-deployment-structure.xml`; no server module is copied or
modified. `ProductServiceBean.findAll` is explicitly `@PermitAll` because this
WildFly configuration denies EJB methods without explicit permission
annotations.
