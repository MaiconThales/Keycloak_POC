# REST API setup

The public `POST /api/v1/auth/login` resource exchanges username and password
with the configured Keycloak realm. WildFly uses the system properties
`keycloak.issuer=http://localhost:8081/realms/poc-keycloak` and
`keycloak.client-id=poc-keycloak-api` from the root-level
`wildfly-10.0.0.Final/standalone/configuration/standalone.xml`.

The existing Docker Keycloak must contain the `poc-keycloak` realm and the
confidential `poc-keycloak-api` client, with Direct Access Grants enabled.
Supply the client secret only through the `KEYCLOAK_CLIENT_SECRET` environment
variable in the environment that starts WildFly. The Maven build does not need
the secret. Do not pass it as a system property or save it in source,
configuration, documentation, or the WAR. The secret is available in the
Keycloak Admin Console under **Clients > poc-keycloak-api > Credentials**.

The user-management EJB uses the Keycloak Admin REST API through the existing
confidential `poc-keycloak-api` client. Enable **Service accounts roles** and
its client-credentials grant, and grant its service account the
`realm-management` client roles `query-users`, `view-users`, and `manage-users`
in the `poc-keycloak` realm. The `deploy.init` launcher sets
`KEYCLOAK_ADMIN_CLIENT_ID=poc-keycloak-api` and reuses the retrieved client
secret as `KEYCLOAK_ADMIN_CLIENT_SECRET`; both variables are available to
WildFly only at runtime, not in the repository, WAR, or Maven process.

WildFly 10 requires Java 8 at runtime. Set `JAVA_HOME` to an installed JDK 8
and provide `KEYCLOAK_CLIENT_SECRET` from a protected local runtime source
before launching `wildfly-10.0.0.Final/bin/standalone.bat`. Without the
environment variable, login returns HTTP 503; the public product GET and
single-origin SPA remain available.

Do not use the `admin-cli` client for the application. The API does not persist
or log user credentials; it forwards them over the configured issuer URL and
returns only the access token, expiration, and token type. Invalid credentials
map to HTTP 401, client setup errors to HTTP 502, and Keycloak connectivity
failures to HTTP 503. Use HTTPS for any non-local Keycloak deployment.
