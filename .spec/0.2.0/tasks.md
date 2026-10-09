# Cronograma de Tarefas (Tasks) - Versão 0.2.0

Este documento organiza a execução técnica da Versão 0.2.0 em tarefas acionáveis e rastreáveis. Todas as atividades mantêm o rigor de separação modular de arquivos e meta mínima de 80% de cobertura de testes.

---

## Histórico de Conclusão (Versão 0.1.0)
- [x] **[TASK-01]** Integração do Ambiente Existente e Configurações Base (WildFly 10, PostgreSQL, Keycloak)
- [x] **[TASK-02]** Camada de Persistência (JPA) e Modelo de Domínio (`Product`)
- [x] **[TASK-03]** Camada de Negócios (EJB 3.x - `ProductServiceBean`)
- [x] **[TASK-04]** Camada de Fachada REST (JAX-RS - Auth e Products)
- [x] **[TASK-05]** Camada de Apresentação (AngularJS 1.8.x SPA & Bootstrap 5.3.8)
- [x] **[TASK-06]** Qualidade e Cobertura de Testes (JUnit 5 & JaCoCo >= 80%)
- [x] **[TASK-07]** Build Maven e Deploy no WildFly 10 (`poc-keycloak-api.war`)

---

## Novas Tarefas - Versão 0.2.0

### [TASK-08] Reestruturação do Keycloak & Integração Admin API

- [x] **[TASK-08.01] Configuração de Grupos e Roles no Keycloak:** Criar os grupos `Admin`, `Sub-Admin` e `User` no realm `poc-keycloak`. Criar as roles granulares com sufixos `-Read`, `-Write`, `-Update`, `-Delete` para cada grupo e mapeá-las na hierarquia do Keycloak.
- [x] **[TASK-08.02] Habilitação da Admin API do Keycloak no Backend:** Configurar as credenciais do Service Account ou Client Admin no WildFly (`keycloak.admin.client-id`, `keycloak.admin.client-secret`) para permitir requisições administrativas de gerenciamento de usuários a partir dos EJBs.

---

### [TASK-09] Camada de Persistência (JPA) - Novos Domínios

- [x] **[TASK-09.01] Entidade `UserEntity`:** Criar `POC_Keycloak/Persistence/src/main/java/poc/persistence/entity/UserEntity.java` mapeando a tabela `users` (campos: `id`, `keycloak_id`, `email`, `first_name`, `last_name`, `active`).
- [x] **[TASK-09.02] Entidade `Review`:** Criar `POC_Keycloak/Persistence/src/main/java/poc/persistence/entity/Review.java` mapeando a tabela `reviews` (campos: `id`, relacionamento `@ManyToOne` com `Product`, `author_id`, `rating`, `comment`, `created_at`).

---

### [TASK-10] Camada de Negócios (EJB 3.x) - Lógica RBAC & CRUDs Expandidos

- [x] **[TASK-10.01] Atualização do `ProductServiceBean`:** Adicionar métodos `updateProduct` e `deleteProduct`. Atualizar as anotações `@RolesAllowed` para suportar os novos grupos/roles (`Admin`, `Sub-Admin`, `Admin-Write`, `Sub-Admin-Write`, `Admin-Delete`).
- [x] **[TASK-10.02] EJB `UserServiceBean`:** Criar a interface local e implementação EJB do serviço de usuários, provendo listagem paginada, cadastro local/Keycloak, atualização e desativação lógica.
- [x] **[TASK-10.03] EJB `ReviewServiceBean`:** Criar a interface local e implementação EJB para consulta com filtros, inclusão, edição e exclusão de avaliações, incluindo regra de negócio para verificação de autoria/propriedade ou papel de moderação.

---

### [TASK-11] Upgrade do Filtro JWT & Fluxo de Required Actions

- [x] **[TASK-11.01] Upgrade do `KeycloakJwtFilter`:** Atualizar o filtro JAX-RS para extrair e preencher no `SecurityContext` tanto os grupos quanto as roles granulares presentes nas claims do token JWT.
- [x] **[TASK-11.02] Interceptação de Required Actions no `AuthResource`:** Atualizar o método de login `POST /auth/login` para capturar `invalid_grant` do Keycloak. Invocar o Keycloak Admin Client para mapear os `missing_fields` e retornar a resposta estruturada `PendingRegistrationResponse` (HTTP 403).
- [x] **[TASK-11.03] Endpoint de Conclusão Cadastral:** Criar a rota REST `POST /users/complete-registration` para receber os dados informados, atualizar o perfil via Keycloak Admin REST API, remover as `requiredActions` e autorizar a emissão do token.

---

### [TASK-12] Camada REST (JAX-RS) - Novas Rotas e Contratos

- [x] **[TASK-12.01] Expansão do `ProductResource`:** Adicionar as rotas `PUT /products/{id}` e `DELETE /products/{id}` com validação de payload e tratamento de erros via `GlobalExceptionMapper`.
- [x] **[TASK-12.02] Criar `UserResource`:** Implementar as rotas `/users` (`GET`, `POST`, `PUT /{id}`, `DELETE /{id}`) associadas aos DTOs `UserInput` e `UserResponse`.
- [x] **[TASK-12.03] Criar `ReviewResource`:** Implementar as rotas `/reviews` (`GET`, `POST`, `PUT /{id}`, `DELETE /{id}`) com suporte a filtros por `productId` e `rating`, associadas aos DTOs `ReviewInput` e `Review`.

---

### [TASK-13] Camada de Apresentação (AngularJS 1.8.x SPA)

- [x] **[TASK-13.01] Centralização da Rota Pública de Login:** Configurar a rota `/login` como a única acessível sem autenticação. Adicionar verificação global no `$routeProvider` / interceptor para redirecionar usuários não autenticados.
- [x] **[TASK-13.02] View e Controller de Conclusão Cadastral (`/complete-registration`):** Criar a tela para formulário dinâmico de preenchimento dos campos retornados em `missing_fields`.
- [x] **[TASK-13.03] Evolução da Tela de Produtos:** Atualizar `products.html` e `productsController.js` para incluir botões de edição (`PUT`) e exclusão (`DELETE`), renderizando-os condicionalmente conforme as roles do usuário logado.
- [x] **[TASK-13.04] Nova Tela do Módulo de Avaliações (`/reviews`):** Implementar `reviews.html`, `reviewsController.js` e `reviewsService.js` com interface responsiva para exibição correlacionada de produtos e comentários/notas, com suporte a criação, edição e exclusão.

---

### [TASK-14] Qualidade, Cobertura e Homologação

- [x] **[TASK-14.01] Testes Unitários dos Novos Módulos (JUnit 5):** Escrever testes para `UserServiceBean`, `ReviewServiceBean`, `ProductServiceBean` (update/delete) e DTOs de entrada/saída.
- [x] **[TASK-14.02] Homologação da Métrica JaCoCo (>= 80%):** Executar `mvn clean verify` e validar se todos os módulos do reator mantêm cobertura mínima de 80% de linhas.
- [x] **[TASK-14.03] Build Maven e Deploy Integrado no WildFly 10:** Executar o build agregador `mvn clean package`, verificar a substituição de `poc-keycloak-api.war` no diretório de deployments do WildFly 10 e realizar a homologação funcional dos novos fluxos.
