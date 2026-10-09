# Especificação Técnica da API - Versão 0.2.0

## 1. Informações Gerais e Metadados
* **Título da API:** API do Projeto EJB (WildFly 10 & Keycloak) - Módulo RBAC & Gestão Expandida
* **Descrição:** Contrato técnico da camada REST (facade) para a Versão 0.2.0. Apresenta suporte ao controle de acesso baseado em Grupos e Roles Granulares, interceptação de ações pendentes do Keycloak, e gestão completa de Produtos, Usuários e Avaliações.
* **Versão do Contrato:** 2.0.0
* **Servidor de Desenvolvimento:** `http://localhost:8080/api/v1`

---

## 2. Estrutura de Segurança e Permissões (RBAC)

### Esquema de Autenticação
* **KeycloakBearerAuth:** Token HTTP Bearer JWT emitido pelo Keycloak 26.8.0 (`realm: poc-keycloak`).

### Mapeamento de Grupos e Roles
* **Grupos:**
    * `Admin`: Acesso irrestrito a todos os recursos.
    * `Sub-Admin`: Leitura e escrita em Produtos e Avaliações.
    * `User`: Leitura de recursos e gerenciamento de próprias avaliações/perfil.
* **Roles Granulares (`{Group-name}-{Action}`):**
    * `Admin-Read`, `Admin-Write`, `Admin-Update`, `Admin-Delete`
    * `Sub-Admin-Read`, `Sub-Admin-Write`, `Sub-Admin-Update`, `Sub-Admin-Delete`
    * `User-Read`, `User-Write`, `User-Update`, `User-Delete`

---

## 3. Endpoints e Rotas (Paths)

### 3.1 Autenticação e Ações Pendentes (`/auth`)

#### `[POST] /auth/login`
* **Acesso:** Público (Única rota pública de autenticação).
* **Descrição:** Autentica o usuário contra o Keycloak. Caso o usuário possua *Required Actions* pendentes, intercepta a resposta `invalid_grant` e retorna a lista de campos faltantes.
* **Corpo da Requisição:** `LoginRequest` (Obrigatório)
* **Respostas:**
    * `200 OK`: Login concluído com sucesso. Retorna `LoginResponse`.
    * `400 Bad Request`: Parâmetros obrigatórios ausentes. Retorna `ErrorResponse`.
    * `401 Unauthorized`: Credenciais inválidas. Retorna `ErrorResponse`.
    * `403 Forbidden`: Pendência de cadastro identificada no Keycloak. Retorna `PendingRegistrationResponse`.

---

### 3.2 Recursos de Produtos (`/products`)

#### `[GET] /products`
* **Acesso:** Protegido (`Admin`, `Sub-Admin`, `User` OU `Admin-Read`, `Sub-Admin-Read`, `User-Read`).
* **Descrição:** Retorna a listagem de todos os produtos cadastrados.
* **Respostas:**
    * `200 OK`: Retorna `Array<Product>`.
    * `401 Unauthorized`: Token ausente ou inválido. Retorna `ErrorResponse`.
    * `500 Internal Server Error`: Erro interno capturado pelo `ExceptionMapper`. Retorna `ErrorResponse`.

#### `[POST] /products`
* **Acesso:** Protegido (`Admin`, `Sub-Admin` OU `Admin-Write`, `Sub-Admin-Write`).
* **Descrição:** Criação de um novo produto. Validado via `@RolesAllowed` no EJB.
* **Corpo da Requisição:** `ProductInput` (Obrigatório).
* **Respostas:**
    * `201 Created`: Produto criado. Retorna `Product`.
    * `400 Bad Request`: Dados de entrada inválidos. Retorna `ErrorResponse`.
    * `401 Unauthorized`: Não autenticado. Retorna `ErrorResponse`.
    * `403 Forbidden`: Permissão insuficiente. Retorna `ErrorResponse`.

#### `[PUT] /products/{id}`
* **Acesso:** Protegido (`Admin`, `Sub-Admin` OU `Admin-Write`, `Sub-Admin-Write`, `Admin-Update`, `Sub-Admin-Update`).
* **Descrição:** Atualização integral ou parcial de um produto existente.
* **Parâmetros de Path:** `id` (Long, Obrigatório)
* **Corpo da Requisição:** `ProductInput` (Obrigatório)
* **Respostas:**
    * `200 OK`: Produto atualizado. Retorna `Product`.
    * `400 Bad Request`: Inconsistência entre ID do path e corpo. Retorna `ErrorResponse`.
    * `403 Forbidden`: Permissão insuficiente. Retorna `ErrorResponse`.
    * `404 Not Found`: Produto não localizado. Retorna `ErrorResponse`.

#### `[DELETE] /products/{id}`
* **Acesso:** Protegido (`Admin` OU `Admin-Write`, `Admin-Delete`).
* **Descrição:** Remoção de um produto do sistema.
* **Parâmetros de Path:** `id` (Long, Obrigatório)
* **Respostas:**
    * `204 No Content`: Produto removido com sucesso.
    * `403 Forbidden`: Permissão negada para o perfil. Retorna `ErrorResponse`.
    * `404 Not Found`: Produto não localizado. Retorna `ErrorResponse`.

---

### 3.3 Recursos de Usuários (`/users`)

#### `[GET] /users`
* **Acesso:** Protegido (`Admin` OU `Admin-Read`).
* **Descrição:** Retorna a listagem paginada de usuários cadastrados no sistema.
* **Parâmetros de Query:** `page` (int, opcional, default: 0), `size` (int, opcional, default: 20)
* **Respostas:**
    * `200 OK`: Retorna lista de `UserResponse`.
    * `403 Forbidden`: Restrito a Administradores. Retorna `ErrorResponse`.

#### `[POST] /users`
* **Acesso:** Protegido (`Admin` / `Admin-Write`) ou Público (se habilitado autocadastro).
* **Descrição:** Cadastra um novo usuário na plataforma.
* **Corpo da Requisição:** `UserInput` (Obrigatório)
* **Respostas:**
    * `201 Created`: Usuário registrado. Retorna `UserResponse`.
    * `400 Bad Request`: Validação de campos ou duplicidade de e-mail/CPF. Retorna `ErrorResponse`.

#### `[POST] /users/complete-registration`
* **Acesso:** Público / Sessão Pendente.
* **Descrição:** Envia as informações cadastrais obrigatórias faltantes para atualizar o usuário no Keycloak e liberar a autenticação.
* **Corpo da Requisição:** `CompleteRegistrationInput` (Obrigatório)
* **Respostas:**
    * `200 OK`: Cadastro atualizado e ações pendentes removidas. Retorna `CompleteRegistrationResponse`; o cliente deve autenticar novamente para obter `LoginResponse` (nenhuma senha ou credencial é armazenada pelo backend).
    * `400 Bad Request`: Dados ausentes ou inválidos. Retorna `ErrorResponse`.

#### `[PUT] /users/{id}`
* **Acesso:** Protegido (`Admin`, `Admin-Write` OU próprio usuário autenticado com role `User-Write`).
* **Descrição:** Atualização dos dados cadastrais do próprio perfil ou por um Administrador.
* **Parâmetros de Path:** `id` (Long, Obrigatório)
* **Corpo da Requisição:** `UserInput` (Obrigatório)
* **Respostas:**
    * `200 OK`: Cadastro atualizado. Retorna `UserResponse`.
    * `403 Forbidden`: Tentativa de alterar conta de terceiros sem permissão. Retorna `ErrorResponse`.

#### `[DELETE] /users/{id}`
* **Acesso:** Protegido (`Admin` OU `Admin-Write`, `Admin-Delete`).
* **Descrição:** Desativação lógica ou exclusão do registro de usuário.
* **Parâmetros de Path:** `id` (Long, Obrigatório)
* **Respostas:**
    * `204 No Content`: Usuário desativado ou removido com sucesso.
    * `404 Not Found`: Usuário não encontrado. Retorna `ErrorResponse`.

---

### 3.4 Recursos de Avaliações (`/reviews`)

#### `[GET] /reviews`
* **Acesso:** Protegido (`Admin`, `Sub-Admin`, `User` OU `Admin-Read`, `Sub-Admin-Read`, `User-Read`).
* **Descrição:** Recupera avaliações com suporte a filtro opcional por produto (`productId`) ou nota (`rating`).
* **Parâmetros de Query:** `productId` (Long, Opcional), `rating` (Integer, Opcional)
* **Respostas:**
    * `200 OK`: Retorna `Array<Review>`.

#### `[POST] /reviews`
* **Acesso:** Protegido (`User` OU `User-Write`).
* **Descrição:** Cria uma nova avaliação/comentário para um determinado produto.
* **Corpo da Requisição:** `ReviewInput` (Obrigatório)
* **Respostas:**
    * `201 Created`: Avaliação criada. Retorna `Review`.
    * `400 Bad Request`: Falha na validação ou tentativa de duplicar avaliação para o mesmo produto pelo mesmo usuário. Retorna `ErrorResponse`.

#### `[PUT] /reviews/{id}`
* **Acesso:** Protegido (Autor proprietário da avaliação OU perfil `Admin` / `Sub-Admin` para moderação).
* **Descrição:** Atualiza o comentário ou a nota de uma avaliação publicada.
* **Parâmetros de Path:** `id` (Long, Obrigatório)
* **Corpo da Requisição:** `ReviewInput` (Obrigatório)
* **Respostas:**
    * `200 OK`: Avaliação atualizada. Retorna `Review`.
    * `403 Forbidden`: Tentativa de edição sem autoria ou permissão de moderação. Retorna `ErrorResponse`.

#### `[DELETE] /reviews/{id}`
* **Acesso:** Protegido (Autor proprietário OU perfil `Admin` / `Sub-Admin` moderação).
* **Descrição:** Exclusão de avaliação.
* **Parâmetros de Path:** `id` (Long, Obrigatório)
* **Respostas:**
    * `204 No Content`: Avaliação excluída.

---

## 4. Modelos de Dados (Schemas)

### LoginRequest (Object)
* `username` (String, Obrigatório): Nome de usuário / e-mail.
* `password` (String, Obrigatório): Senha.

### LoginResponse (Object)
* `access_token` (String): Token JWT de acesso.
* `expires_in` (Integer): Tempo de expiração em segundos.
* `token_type` (String): `Bearer`.

### PendingRegistrationResponse (Object)
* `status` (String): `"PENDING_REGISTRATION"`.
* `message` (String): Mensagem explicativa.
* `required_actions` (Array<String>): Exemplo: `["UPDATE_PROFILE"]`.
* `missing_fields` (Object): Objeto chave-valor indicando os campos pendentes (ex: `{"firstName": "Obrigatório", "lastName": "Obrigatório"}`).
* `registration_ticket` (String): Ticket opaco emitido pelo backend, de uso único e validade curta; não é token Keycloak.

### CompleteRegistrationInput (Object)
* `username` (String, Obrigatório): Nome de usuário associado ao ticket (não é aceito como identidade isolada).
* `registration_ticket` (String, Obrigatório): Ticket recebido em `PendingRegistrationResponse`; o backend deriva e valida a identidade, expiração e uso único.
* `firstName` (String, Obrigatório): Primeir nome.
* `lastName` (String, Obrigatório): Sobrenome.
* `email` (String, Obrigatório): E-mail do usuário.

### Product (Object)
* `id` (Long): Identificador único do produto.
* `name` (String): Nome do produto.
* `price` (Number, double): Preço.
* `sku` (String): Código SKU.

### ProductInput (Object)
* `name` (String, Obrigatório): Nome do produto.
* `price` (Number, double, Obrigatório): Preço.
* `sku` (String, Obrigatório): Código SKU.

### UserResponse (Object)
* `id` (Long): Identificador interno.
* `keycloak_id` (String): Identificador no Keycloak.
* `username` (String): Nome do usuário.
* `email` (String): E-mail.
* `firstName` (String): Primeir nome.
* `lastName` (String): Sobrenome.
* `active` (Boolean): Status do usuário.

### UserInput (Object)
* `username` (String, Obrigatório)
* `email` (String, Obrigatório)
* `firstName` (String, Obrigatório)
* `lastName` (String, Obrigatório)
* `password` (String, Opcional para atualização)

### Review (Object)
* `id` (Long): ID da avaliação.
* `productId` (Long): ID do produto avaliado.
* `productName` (String): Nome do produto correlacionado.
* `authorUsername` (String): Nome do autor da avaliação.
* `rating` (Integer): Nota de 1 a 5.
* `comment` (String): Comentário detalhado.
* `createdAt` (String, date-time): Timestamp de criação.

### ReviewInput (Object)
* `productId` (Long, Obrigatório): ID do produto.
* `rating` (Integer, Obrigatório, min: 1, max: 5): Nota.
* `comment` (String, Obrigatório): Comentário.

### ErrorResponse (Object)
* `status_code` (Integer): Código HTTP.
* `message` (String): Mensagem amigável de erro.
* `timestamp` (String, date-time): Data e hora da ocorrência.
