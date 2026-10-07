# POC Keycloak

## Maven reactor

Run Maven commands from `POC_Keycloak`. The parent POM builds `Persistence`,
`Security/keycloak-jwt-filter`, and `REST` in dependency order. REST depends
on both Java modules; Java EE 7 APIs remain `provided` by WildFly 10.
Dependency versions are managed centrally in the parent POM.

The compiler uses release 8, limiting bytecode and Java API usage to Java 8
even when building with a newer JDK. Maven Compiler Plugin 3.13.0 also supports
building with JDK 8.

## WAR packaging

Run `mvn clean package` from `POC_Keycloak` to generate
`REST/target/poc-keycloak-api.war`. The WAR includes the Persistence and JWT
filter JARs, REST classes, and all static assets from
`Frontend/src/main/webapp`. Java EE APIs and test dependencies are not bundled.
The persistence descriptor remains in `META-INF/persistence.xml` inside the
Persistence JAR.

`REST/src/main/webapp/WEB-INF/jboss-web.xml` sets the context root to `/`:
the SPA is served at `http://localhost:8080/` and the API at
`http://localhost:8080/api/v1`. Only one application may own this context root.
During `package`, the generated WAR is automatically copied to
`../wildfly-10.0.0.Final/standalone/deployments` (relative to `POC_Keycloak`).
An existing WAR is overwritten, and a copy error fails the build.
Override the destination with:

```powershell
mvn clean package "-Dwildfly.deployments.dir=C:\path\to\deployments"
```

`clean` only removes Maven build outputs; it does not remove the copied WAR.
If WildFly is running, its deployment scanner may deploy the copied WAR
automatically. WildFly 10 runtime requires Java 8. Provide
`KEYCLOAK_CLIENT_SECRET` in the protected environment that starts WildFly; the
Maven build does not consume or package it. The SPA is served from `/` and the
API from `/api/v1` on the same origin. `GET /api/v1/products` is public;
`POST /api/v1/products` requires a valid Keycloak bearer token with the `admin`
role, enforced by both the JWT filter and the EJB `@RolesAllowed`.

## Frontend styling

The AngularJS SPA uses Bootstrap 5.3.8, the latest stable release confirmed
on October 6, 2026. Its version-pinned CSS is loaded from jsDelivr in
`Frontend/src/main/webapp/index.html`, with Subresource Integrity and
anonymous CORS. Internet access is required to load the stylesheet, as it
is for the existing AngularJS CDN script. No Bootstrap JavaScript or jQuery
is needed: AngularJS still owns navigation, form submission, and state.

Login and products use responsive cards, forms, buttons, alerts, loading
indicators, and a responsive product table. The login, products, and updated
WildFly deployment were accepted in desktop/mobile and runtime checks;
`.spec/tasks.md` records the completed tasks.

## Interactive deployment from the workspace root

The root-level `deploy.init` is a PowerShell script. Since `.init` is not a
PowerShell executable extension, run it from the workspace root as follows:

```powershell
& ([scriptblock]::Create((Get-Content -Raw .\deploy.init))) -JavaHome 'C:\Program Files\Java\jdk1.8.0_202'
```

Supply the actual JDK 8 installation path; omit `-JavaHome` if `JAVA_HOME`
already points to JDK 8. PostgreSQL and Keycloak must already be running.
The script identifies standalone WildFly Java processes belonging to this
workspace, requests graceful shutdown through the local management port,
and waits up to 30 seconds before forcibly stopping only those server PIDs.
Other Java processes and servers are not stopped. Occupied HTTP or management
ports belonging to other processes cause deployment to abort. The target
WildFly needs the PostgreSQL driver and `MinhaAppDS` datasource configured.

The script builds the WAR with tests into a staging destination, prompts for
Keycloak administrator credentials, and uses the admin REST API to retrieve
the existing application client secret. It does not create a realm/client,
rotate the secret, or save credentials to a file. The administrator must be
allowed to retrieve client secrets. Administrator authentication uses
`admin-cli` in `master`; the application continues to use `poc-keycloak-api`.
The application client must be enabled, confidential, and allow Direct access
grants. Administrator accounts requiring interactive MFA cannot use this
password-grant flow.

After retrieving the secret and stopping the old server, the script removes
only `poc-keycloak-api.war` and its scanner markers from the deployment
directory, preserving other deployments and server data. It copies the new WAR, requests
deployment, and starts WildFly in the foreground with the secret available
only in its runtime environment. The caller's environment is restored when
the script ends. Wait for the successful deployment message in the WildFly
console; copying the WAR alone does not guarantee a successful deployment.

The Keycloak port defaults to `-KeycloakPort 8081`, and the application/EJB
HTTP port defaults to `-EJBPort 8083` (also available as `-HttpPort`).
Both ports can be overridden when invoking the script. By default, the Keycloak URL is built as
`http://localhost:<KeycloakPort>`; `-KeycloakUrl` can instead specify a
different URL. Realm, client ID, and admin realm default to `poc-keycloak`,
`poc-keycloak-api`, and `master`; the issuer and application client ID are
also passed to WildFly. Management defaults to `-ManagementPort 9990`. Set
`-ManagementPort` to the existing server's management port if it differs.
Remote Keycloak URLs require HTTPS. If invoked outside the workspace root,
provide `-ProjectRoot` with its absolute path.

## Unit test environment

The base `pom.xml` defines JUnit Jupiter 5.10.3 (API, parameterized tests and
engine) in test scope and Maven Surefire 3.2.5 for running JUnit 5 tests.
These versions support Java 8.

The POM aggregates `Persistence`, `Security/keycloak-jwt-filter`, and `REST`
for test and coverage runs. Each module inherits the test dependencies and
runner. Put each module's tests in
`src/test/java`, using `org.junit.jupiter.api.Test` and Jupiter assertions.
The Persistence module is configured and can be tested from `POC_Keycloak`
with `mvn -f Persistence/pom.xml test`. Its JUnit tests use Mockito 4.11.0
(Java 8 compatible) to isolate `ProductServiceBean` from the database and
container. They cover listing, creation, persistence failures and entity
accessors. Annotation checks verify the declared JTA and role configuration,
but do not replace container integration tests for transaction/security
enforcement. There are no separate business utility classes at present.
Run `mvn clean verify` from `POC_Keycloak` to execute the full Java test suite,
generate a JaCoCo HTML/XML report in each module's
`target/site/jacoco/` directory, and enforce at least 80% line coverage for
each module. A module below the threshold fails the build. JaCoCo is not
configured to exclude application classes. Runtime deployment and Bootstrap
acceptance are complete as recorded in TASK-07.04.

Latest full-suite coverage: Persistence 100% (25/25 lines), JWT filter 90.53%
(172/190), and REST 98.48% (194/197).
