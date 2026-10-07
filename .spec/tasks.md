# Cronograma de Tarefas (Tasks)

Este documento divide a execução técnica do projeto em tarefas acionáveis e rastreáveis. Cada item deve respeitar rigorosamente a separação de arquivos e a cobertura de testes de 80%.

---

## Atualização de escopo: Bootstrap (06/10/2026)

Bootstrap 5.3.8 aplicado à SPA, com CSS versionado via jsDelivr e SRI. TASK-05.04, TASK-05.05 e TASK-07.04 foram concluídas após homologação visual desktop/mobile e dos fluxos de interface e runtime. As demais tarefas mantêm seu status porque a arquitetura, a lógica e os contratos não mudaram.

## [TASK-01] Integração do Ambiente Existente e Configurações Base

### Subtarefas de Infraestrutura:
- [x] **[TASK-01.01] Serviços Docker existentes:** Utilizar os serviços PostgreSQL e Keycloak definidos em `Docker/docker-compose.yml`; não instalar novas instâncias. Containers `postgres_db` e `keycloak_auth` estão ativos, os bancos `minha_app_db` e `keycloak_db` existem, e o Keycloak responde em `http://localhost:8081`.
- [x] **[TASK-01.02] Driver JDBC e DataSource:** Na distribuição WildFly 10.0.0.Final da raiz, registrar o driver PostgreSQL JDBC 42.7.5 em `modules/system/layers/base/org/postgresql/main` e configurar no `standalone.xml` o DataSource JTA `java:jboss/datasources/MinhaAppDS` para `minha_app_db` via `localhost:5432`. XML e conexão JDBC validados.
- [x] **[TASK-01.03] Configuração do Keycloak Docker para a aplicação:** Configurada a instância já em execução em `http://localhost:8081`: realm `poc-keycloak`, client confidencial `poc-keycloak-api` com Direct Access Grants e sem Standard Flow, role de realm `admin` atribuída ao usuário `system-admin` (com ação obrigatória de definir senha). O `standalone.xml` usa o issuer `http://localhost:8081/realms/poc-keycloak` e `keycloak.client-id=poc-keycloak-api`; `AuthResource` recebe o secret exclusivamente pela variável de ambiente runtime `KEYCLOAK_CLIENT_SECRET`, sem armazená-lo no repositório. JWKS, configuração do client, grant e atribuição da role verificados no Keycloak ativo. A validação ponta a ponta HTTP foi concluída em TASK-07.04.
- [x] **[TASK-01.04] Configuração do JPA:** Criado `POC_Keycloak/Persistence/src/main/resources/META-INF/persistence.xml` com a unidade `MinhaAppPU`, Hibernate 5 (provedor do WildFly 10), DataSource JTA `java:jboss/datasources/MinhaAppDS` e dialeto PostgreSQL 9.4 compatível com o Hibernate instalado. XML validado.

---

## [TASK-02] Camada de Persistência (JPA) e Modelo de Domínio

### Subtarefas de Persistência:
- [x] **[TASK-02.01] Entidade Product:** Criada `POC_Keycloak/Persistence/src/main/java/poc/persistence/entity/Product.java`, entidade JPA mapeada à tabela `products`, com ID `Long` gerado pelo banco, campos obrigatórios e preço `BigDecimal` para preservar precisão monetária. Persistência exclusivamente por JPA.

---

## [TASK-03] Camada de Negócios (EJB 3.x)

### Subtarefas de Lógica de Negócio:
- [x] **[TASK-03.01] Interface do EJB de Produtos:** Criada a interface local `POC_Keycloak/Persistence/src/main/java/poc/persistence/service/ProductServiceLocal.java`, definindo operações tipadas para listar produtos e criar um produto a partir de nome, preço `BigDecimal` e SKU.
- [x] **[TASK-03.02] Implementação do EJB de Produtos:** Criada a classe bean `@Stateless` `ProductServiceBean.java`.
    - Injeção do `EntityManager` via `@PersistenceContext(unitName = "MinhaAppPU")`.
    - Transações CMT `REQUIRED` para os métodos do bean.
    - Validação de segurança com `@RolesAllowed("admin")` no método de criação.

---

## [TASK-04] Camada de Fachada REST (JAX-RS)

### Subtarefas de Controle e Contrato:
- [x] **[TASK-04.01] Ativação do JAX-RS:** Criada `POC_Keycloak/REST/src/main/java/poc/rest/ApiApplication.java`, que estende `Application` e define o path base `/api/v1` via `@ApplicationPath`.
- [x] **[TASK-04.02] DTOs de Entrada e Saída:** Criados `ProductInput`, `LoginRequest` e `LoginResponse` em `POC_Keycloak/REST/src/main/java/poc/rest/dto`, isolando os contratos HTTP das entidades JPA. Entradas incluem validação Bean Validation, e os DTOs possuem construtores sem argumentos para desserialização.
- [x] **[TASK-04.03] Endpoint de Autenticação:** Criado `POC_Keycloak/REST/src/main/java/poc/rest/resource/AuthResource.java` com `POST /auth/login`, integrando o token endpoint OIDC do Keycloak, validando entradas e retornando `LoginResponse`/erros HTTP apropriados sem registrar credenciais. A emissão de token e o tratamento de credenciais inválidas foram exercitados contra o Keycloak ativo. A configuração de realm/client permanece pendente em TASK-01.03; o endpoint requer `keycloak.client-id`, Direct Access Grants e o client secret em runtime.
- [x] **[TASK-04.04] Endpoint de Produtos:** Criado `POC_Keycloak/REST/src/main/java/poc/rest/resource/ProductResource.java` com injeção estrita via `@EJB`, rota pública `GET /products` e `POST /products` validado e delegando ao EJB. As entidades são convertidas a `ProductResponse` para não expor diretamente o modelo JPA; a criação retorna HTTP 201 com URI `Location`.
- [x] **[TASK-04.05] Tratamento Global de Erros:** Criado `GlobalExceptionMapper` (`ExceptionMapper<Throwable>`) e o DTO `ErrorResponse` com `status_code`, mensagem segura e timestamp ISO-8601. Mapeia erros de validação para HTTP 400, de autorização para 403, preserva status de `WebApplicationException` com mensagens controladas e oculta detalhes internos/stack traces. Os erros gerados pelos recursos de autenticação e produtos também usam `ErrorResponse`.

---

## [TASK-05] Camada de Apresentação (AngularJS 1.8.x SPA)

### Subtarefas de Front-End:
- [x] **[TASK-05.01] Estrutura de Módulos:** Criado o ponto de entrada `POC_Keycloak/Frontend/src/main/webapp/index.html` e inicializado o módulo AngularJS `pocKeycloakApp` em `app/app.module.js`, usando AngularJS 1.8.3.
- [x] **[TASK-05.02] Divisão Arquitetural de Arquivos:** Criadas as pastas `POC_Keycloak/Frontend/src/main/webapp/app/auth` e `app/products`, com módulos, controllers, services e views HTML separados. Submódulos registrados no módulo principal e carregados no `index.html`. A lógica de login e produtos permanece nas TASK-05.04 e TASK-05.05.
- [x] **[TASK-05.03] Interceptor HTTP de Segurança:** Criado `app/auth/authInterceptor.js`, registrado via `auth.config.js`. Injeta o token Bearer em chamadas para a API da mesma origem, sem enviá-lo a sites externos ou templates. Respostas 401/403 da API limpam a sessão e redirecionam para a view de login; as rejeições continuam propagadas. Adicionados armazenamento de token em memória no `authService` e navegação entre as views públicas.
    - Injetar dinamicamente o cabeçalho `Authorization: Bearer <TOKEN>` em todas as requisições de saída.
    - Interceptá-las globalmente para escutar erros `401` e `403`, forçando o redirecionamento automático para a tela pública.
- [x] **[TASK-05.04] Tela de Login:** Implementados `login.html`, `loginController.js` e `authService.js` com Bootstrap, formulário responsivo/acessível, chamada a `POST api/v1/auth/login`, token em memória, estados de carregamento/erro e navegação para produtos após autenticação. Senha limpa ao concluir a chamada; interceptor usa injeção tardia do serviço para evitar dependência circular com `$http`. Homologados visual desktop/mobile, validação de formulário vazio, rejeição de credenciais inválidas e login bem-sucedido no Keycloak.
- [x] **[TASK-05.05] Tela de Produtos:** Implementados `products.html`, `productsController.js` e `productsService.js` com Bootstrap, listagem responsiva via `GET api/v1/products`, atualização manual, formulário de cadastro para usuários autenticados via `POST api/v1/products`, validação, estados de carregamento/erro/sucesso e recarga após cadastro. A autorização administrativa permanece no backend e o interceptor trata respostas 401/403. Homologados em desktop/mobile sem overflow, incluindo listagem e atualização reais, estados vazio/erro, validação do formulário e feedback de sucesso do cadastro.

## [TASK-06] Qualidade e Cobertura de Testes (JUnit 5)

### Subtarefas de Testes:
- [x] **[TASK-06.01] Ambiente de Testes:** Configurado `POC_Keycloak/pom.xml` base com JUnit Jupiter 5.10.3 (API, testes parametrizados e engine) no escopo `test`, Maven Surefire 3.2.5 e compilação Java 8. Os módulos do reator herdam essa configuração; testes dos EJBs e cobertura foram concluídos nas TASK-06.02 e TASK-06.03. Instruções em `POC_Keycloak/README.md`.
- [x] **[TASK-06.02] Testes dos EJBs:** Criados testes JUnit 5 para `ProductServiceBean` e `Product` em `POC_Keycloak/Persistence/src/test/java`, isolando o EntityManager com Mockito 4.11.0. Cobrem listagem, criação, ID gerado, precisão monetária, propagação de falhas e configuração declarada de persistência/transações/autorização. Não existem classes utilitárias de negócio separadas atualmente. O POM do módulo permite executar `mvn -f Persistence/pom.xml test` a partir de `POC_Keycloak`, sem banco ou WildFly ativos.
- [x] **[TASK-06.03] Homologação da Métrica:** Configurado JaCoCo com geração de relatórios HTML/XML e falha do `mvn clean verify` quando qualquer módulo fica abaixo de **80% de cobertura de linhas**. A execução completa cobriu Persistence 100% (25/25 linhas), Security/keycloak-jwt-filter 91,61% (142/155) e REST 97,94% (190/194), sem excluir classes de produção. Relatórios em `Persistence/target/site/jacoco/`, `Security/keycloak-jwt-filter/target/site/jacoco/` e `REST/target/site/jacoco/`; comando e detalhes em `POC_Keycloak/README.md`.

---

## [TASK-07] Build Maven e Deploy no WildFly

### Subtarefas de Empacotamento e Implantação:
- [x] **[TASK-07.01] Reator Maven:** Configurado `POC_Keycloak/pom.xml` como agregador de `Persistence`, `Security/keycloak-jwt-filter` e `REST`, com versões centralizadas, APIs Java EE 7 no escopo `provided` e compilação `release 8`. REST depende de ambos os módulos Java, garantindo a ordem do reator. Empacotamento WAR e deploy permanecem nas TASK-07.02 a TASK-07.04.
- [x] **[TASK-07.02] WAR implantável:** Configurados `Persistence` e `Security/keycloak-jwt-filter` como JAR e `REST` como WAR `REST/target/poc-keycloak-api.war`. O Maven WAR Plugin inclui os dois JARs e todos os recursos de `Frontend/src/main/webapp`; APIs Java EE permanecem `provided` e dependências de teste não são empacotadas. `WEB-INF/jboss-web.xml` define contexto `/` para SPA e API `/api/v1` na mesma origem. Deploy em runtime permanece na TASK-07.04.
- [x] **[TASK-07.03] Cópia para deployments:** Vinculada a cópia de `poc-keycloak-api.war` ao ciclo Maven `package`, após a geração do WAR, com destino configurável por `wildfly.deployments.dir`. O padrão resolve para `wildfly-10.0.0.Final/standalone/deployments` na raiz do workspace. Arquivo existente é sobrescrito e erros de geração/cópia interrompem o build. Confirmadas cópia padrão, igualdade dos arquivos, destino alternativo e falha com destino inválido.
- [x] **[TASK-07.04] Validação de deploy:** Reconstruído com Java 8 via `mvn clean package` e implantado o WAR atualizado pelo deployment scanner do WildFly 10 existente; cópia em `standalone/deployments` idêntica ao artefato Maven e scanner confirmou `.deployed`, sem `.failed`. Confirmados no runtime `GET /` e `GET /api/v1/products` em HTTP 200; CSS Bootstrap 5.3.8 com SRI carregado e aplicado no navegador, listagem dinâmica de produtos e navegação responsiva homologadas em desktop e mobile sem overflow horizontal. Login vazio retorna 400 e credenciais inexistentes retornam 401 com mensagem segura; formulário limpa a senha. `POST /products` sem token ou com JWT malformado retorna 401, sem expor stack trace. O fluxo autorizado de criação com token admin e persistência JPA, assim como o 403 para usuário sem role, já havia sido validado ponta a ponta: os dados temporários de homologação foram removidos. O WAR inclui SPA, persistência e filtro JWT, não inclui APIs Java EE e não contém secrets.
