# Plano de Implementação Técnica (Plan) - Versão 0.2.0

Este documento estabelece o plano sequencial para a execução da Versão 0.2.0 do projeto, cobrindo o novo modelo de controle de acesso (RBAC com Grupos e Roles Granulares), o fluxo customizado de ações obrigatórias do Keycloak (*Required Actions*), os novos domínios de negócio (Usuários e Avaliações) e as expansões da interface AngularJS 1.8.x.

Local do arquivo `constitution.md`: `.spec/constitution.md`.

---

## 1. Fase de Infraestrutura Keycloak e Modelo RBAC (Versão 0.2.0)
Reestruturação das permissões no Keycloak 26.8.0 e descontinuação da role genérica `admin`.

* **Criação de Grupos no Realm `poc-keycloak`:**
    * `Admin`: Controle total e irrestrito.
    * `Sub-Admin`: Operações de CRUD em recursos operacionais (produtos, avaliações), sem permissão para gestão de usuários.
    * `User`: Acesso restrito a operações de leitura e criação de avaliações próprias.
* **Mapeamento de Roles Granulares (`{Group-name}-{Action}`):**
    * Criar roles no Keycloak para cada grupo com sufixos `-Read`, `-Write`, `-Update`, `-Delete` (ex: `Admin-Read`, `Sub-Admin-Write`, `User-Read`, etc.).
    * Associar as roles granulares aos respectivos grupos de forma hierárquica/herdada.
* **Keycloak Admin Client / Service Account:**
    * Configurar credenciais do Client Admin (ou habilitar *Service Account* no `poc-keycloak-api` com `realm-management` / `manage-users`) para que a API Java EE possa consultar e atualizar usuários via REST Admin API do Keycloak.

---

## 2. Fase do Core Back-End (JPA & EJBs - Novos Domínios)
Expansão do modelo relacional e inclusão da lógica de negócios controlada por CMT e RBAC refinado.

### Camada de Persistência (JPA)
* **Manutenção da Entidade `Product.java`:** Ajustada para suportar atualizações e remoção lógica/física.
* **Criação da Entidade `UserEntity.java`:** Mapeamento da tabela `users` no PostgreSQL (`minha_app_db`), armazenando ID externo Keycloak (`keycloak_id`), e-mail, nome, CPF e status.
* **Criação da Entidade `Review.java`:** Mapeamento da tabela `reviews`, estabelecendo relacionamento `@ManyToOne` com `Product` e vínculo com o autor (`user_id` / `keycloak_id`), contendo nota (`Integer`), comentário (`String`) e timestamp.

### Camada de Negócios (EJB 3.x)
* **Descontinuação do `@RolesAllowed("admin")`:** Atualizar anotações para aceitar os novos Grupos e Roles (`@RolesAllowed({"Admin", "Sub-Admin", "Admin-Write", "Sub-Admin-Write"})`, etc.).
* **EJB `ProductServiceBean`:** Implementação dos métodos `update` e `delete` com anotações de segurança e controle CMT.
* **EJB `UserServiceBean`:** Implementação das operações de listagem paginada, cadastro, atualização de perfil e desativação lógica/exclusão.
* **EJB `ReviewServiceBean`:** Implementação do CRUD de avaliações com validação de propriedade (o usuário só pode editar/deletar a própria avaliação, exceto perfis `Admin` que possuem poder de moderação).

---

## 3. Fase de Segurança JWT & Fluxo de Required Actions
Aprimoramento do filtro de segurança e interceptação de pendências cadastrais do Keycloak.

* **Upgrade do `KeycloakJwtFilter` (ContainerRequestFilter):**
    * Extração de roles de realm e de grupos a partir das *claims* do JWT (`realm_access.roles`, `groups`).
    * Injeção do `SecurityContext` atualizado no JAX-RS para permitir validação programática ou via `@RolesAllowed`.
* **Interceptação do Flow de Autenticação (`/auth/login`):**
    * Captura do erro `invalid_grant` emitido pelo Keycloak quando há ações obrigatórias pendentes (`Account required actions list not empty`).
    * Invocação do Keycloak Admin Client pela API para identificar quais campos obrigatórios estão ausentes no perfil do usuário.
    * Retorno do status de negócio `PENDING_REGISTRATION` (HTTP 403 Forbidden ou 200 OK com payload estruturado) contendo o mapa de `missing_fields`.
* **Endpoint de Conclusão de Cadastro (`POST /users/complete-registration`):**
    * Rota dedicada para receber os dados pendentes enviados pelo frontend, atualizar o usuário no Keycloak via Admin API, remover as `requiredActions` e autorizar o acesso.

---

## 4. Fase da Fachada REST (JAX-RS)
Criação e atualização dos recursos REST mapeados para o contrato da Versão 0.2.0.

* **`ProductResource` (`/products`):**
    * `GET /products`: Protegido por roles/grupos Read (`Admin`, `Sub-Admin`, `User`, `Admin-Read`, etc.).
    * `POST /products`: Protegido por roles/grupos Write (`Admin`, `Sub-Admin`, `Admin-Write`, `Sub-Admin-Write`).
    * `PUT /products/{id}`: Atualização de produto.
    * `DELETE /products/{id}`: Remoção de produto (Restrito a `Admin` / `Admin-Write`).
* **`UserResource` (`/users`):**
    * `GET /users`: Listagem paginada de usuários.
    * `POST /users`: Cadastro de usuário.
    * `PUT /users/{id}`: Atualização de perfil com validação de propriedade (próprio ID) ou perfil `Admin`.
    * `DELETE /users/{id}`: Remoção/Desativação de usuário.
* **`ReviewResource` (`/reviews`):**
    * `GET /reviews`: Listagem de avaliações com filtros por produto/nota.
    * `POST /reviews`: Criação de avaliação.
    * `PUT /reviews/{id}`: Atualização de avaliação com verificação de autoria/moderação.
    * `DELETE /reviews/{id}`: Remoção de avaliação com verificação de autoria/moderação.

---

## 5. Fase do Front-End (AngularJS 1.8.x SPA)
Reestruturação do fluxo de navegação e novas views responsivas com Bootstrap 5.3.8.

* **Ajuste do Roteamento e Segurança Frontend:**
    * A tela de login (`/login`) passa a ser a **única rota pública** da aplicação.
    * Rotas protegidas verificam permissões antes da renderização.
* **View de Complementação Cadastral (`/complete-registration`):**
    * Renderização dinâmica de formulário com base nos `missing_fields` retornados após a tentativa de login.
    * Envio para `POST /users/complete-registration` e redirecionamento automático pós-sucesso.
* **Evolução da View de Produtos (`/products`):**
    * Adição de botões de Ação (Editar e Excluir) na tabela responsiva.
    * Exibição condicional dos botões de acordo com as roles do usuário logado (ex: botão Excluir visível apenas para `Admin`).
    * Modal/Formulário para edição de produto (`PUT`).
* **Nova View de Avaliações (`/reviews`):**
    * Interface dedicada exibindo a correlação de Produtos com suas Avaliações e Notas (estrelas/pontuação).
    * Formulário para inclusão e edição de comentário e nota.
    * Botões de moderação/exclusão com visibilidade baseada na autoria ou papel de administração.

---

## 6. Fase de Testes, Cobertura e Build
* **Testes Unitários (JUnit 5):**
    * Cobertura dos novos EJBs (`UserServiceBean`, `ReviewServiceBean`, `ProductServiceBean`), DTOs e interceptores.
    * Validação da regra de cobertura mínima de **80% de linhas** mantida pelo JaCoCo.
* **Build e Deploy Automático (WildFly 10):**
    * Reator Maven gerando `poc-keycloak-api.war` com os novos recursos e copiando para `standalone/deployments`.
    * Homologação de deploy no servidor local.