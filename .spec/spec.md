# Especificação Técnica da API (OpenAPI 3.0.0)

## 1. Informações Gerais e Metadados
*   **Título da API:** API do Projeto EJB (WildFly 10 & Keycloak)
*   **Descrição:** Contrato técnico da camada REST que atua como fachada (facade). Toda a lógica de negócios e o controle transacional (CMT) são delegados aos EJBs. O tratamento de erros é centralizado via `ExceptionMapper`, omitindo StackTraces.
*   **Versão do Contrato:** 1.0.0
*   **Servidor de Desenvolvimento:** `http://localhost:8080/api/v1`

## 2. Ambiente de Execução
*   **Código da Aplicação:** Fontes e recursos ficam em `POC_Keycloak`, organizados nos submódulos `Persistence`, `REST` e `Security`.
*   **WildFly:** Distribuição 10.0.0.Final já disponível na raiz do projeto (`wildfly-10.0.0.Final`).
*   **PostgreSQL:** Executado em Docker pelo serviço `postgres` de `Docker/docker-compose.yml`; acessível pelo WildFly no host em `localhost:5432`. O banco da aplicação é `minha_app_db`.
*   **Keycloak:** Executado em Docker pelo serviço `keycloak` do mesmo Compose; acessível pelo host em `http://localhost:8081`. O banco dedicado do Keycloak é `keycloak_db`. Configurar essa instância existente para o projeto, sem instalar outro Keycloak: criar o realm dedicado `poc-keycloak`, o client confidencial `poc-keycloak-api` e a role de realm `admin`.
*   **Configuração do realm/client:** No client `poc-keycloak-api`, habilitar Direct Access Grants e desabilitar Standard Flow. A role de realm `admin` está atribuída ao usuário `system-admin`, que precisa definir sua senha antes de autenticar. Configurar o WildFly com `keycloak.issuer=http://localhost:8081/realms/poc-keycloak` e `keycloak.client-id=poc-keycloak-api`; fornecer `keycloak.client-secret` somente em runtime e nunca versioná-lo. O endpoint JWKS é `<keycloak.issuer>/protocol/openid-connect/certs`.

## 2.1 Build Maven e Implantação
*   **Agregador:** O projeto Maven deve residir em `POC_Keycloak/pom.xml`, agregando os módulos `Persistence`, `Security/keycloak-jwt-filter` e `REST`, nessa ordem de dependência.
*   **Artefato:** `REST` deve gerar o WAR `poc-keycloak-api.war`, compatível com Java 8 e WildFly 10. O pacote deve conter as classes e recursos de `Persistence`, o provider `KeycloakJwtFilter` e a SPA existente em `Frontend/src/main/webapp`. As bibliotecas Java EE/JAX-RS do servidor não devem ser empacotadas como dependências privadas.
*   **Comando:** Executar `mvn clean package` a partir de `POC_Keycloak`. O build deve copiar o WAR concluído para `C:\Development_Environment\Projects\Keycloak_POC\wildfly-10.0.0.Final\standalone\deployments`, mantendo também a saída normal do módulo em `REST/target`.
*   **Configuração do destino:** Usar a propriedade Maven `wildfly.deployments.dir`, com o diretório acima como padrão e permitindo override (por exemplo, `mvn clean package -Dwildfly.deployments.dir="C:\caminho\alternativo\deployments"`). O diretório configurado precisa existir ou ser criado pelo build; erro de cópia deve falhar a execução.
*   **Deploy:** O WildFly 10 existente deve detectar o WAR pelo deployment scanner de `standalone/deployments`. Não instalar outro servidor nem exigir deploy manual pela console.

---

## 2.2 Interface e Estilo
*   **Framework visual:** Bootstrap **5.3.8**, versão estável mais recente confirmada em 06/10/2026 na release oficial `twbs/bootstrap`. CSS carregado uma única vez em `Frontend/src/main/webapp/index.html` via jsDelivr, com versão fixa, SRI e CORS anônimo. O carregamento do estilo requer acesso à internet.
*   **Navegação:** Cabeçalho responsivo com links para produtos/login e indicação visual e acessível da página atual.
*   **Login:** Card centralizado, campos e botão estilizados, indicador de carregamento e alerta de erro; preservar validação e autenticação existentes.
*   **Produtos:** Listagem em card com tabela responsiva, botão de atualização, estados vazio/carregando/erro, formulário responsivo de cadastro e alertas de sucesso/erro. Manter a listagem pública e a autorização administrativa no backend.
*   **Compatibilidade:** AngularJS 1.8.3 continua responsável pela navegação, estado e eventos. Não usar plugins Bootstrap que manipulem o DOM nem adicionar jQuery; os componentes atuais precisam somente do CSS.

---

## 3. Esquemas de Segurança (Security Schemes)

### KeycloakBearerAuth
*   **Tipo:** HTTP Bearer
*   **Formato do Token:** JWT (JSON Web Token)
*   **Descrição:** Token gerado pelo Keycloak 26.8.0. O Interceptor do AngularJS 1.8.x deve injetar automaticamente este token no cabeçalho `Authorization: Bearer <TOKEN>` para rotas protegidas.

---

## 4. Endpoints e Rotas (Paths)

### [POST] /auth/login
*   **Acesso:** Público (Sem autenticação)
*   **Descrição:** Endpoint para autenticação de usuários. Retorna o token de acesso.
*   **Corpo da Requisição (Request Body):** `LoginRequest` (Obrigatório)
*   **Respostas (Responses):**
    *   **200 OK:** Autenticação bem-sucedida. Retorna `LoginResponse`.
    *   **400 Bad Request:** Parâmetros obrigatórios ausentes. Retorna `ErrorResponse`.
    *   **401 Unauthorized:** Credenciais inválidas. Retorna `ErrorResponse`.

### [GET] /products
*   **Acesso:** Público (Sem autenticação)
*   **Descrição:** Listagem pública de produtos.
*   **Respostas (Responses):**
    *   **200 OK:** Retorna uma lista (Array) contendo objetos do tipo `Product`.
    *   **500 Internal Server Error:** Erro inesperado capturado pelo `ExceptionMapper`. Retorna `ErrorResponse`.

### [POST] /products
*   **Acesso:** Protegido (Exige escopo/role `admin`)
*   **Descrição:** Criação de um novo produto. Garante segurança em profundidade (JAX-RS + `@RolesAllowed` no EJB).
*   **Corpo da Requisição (Request Body):** `ProductInput` (Obrigatório)
*   **Respostas (Responses):**
    *   **201 Created:** Produto criado. Transação gerenciada via CMT. Retorna `Product`.
    *   **400 Bad Request:** Dados de entrada inválidos. Retorna `ErrorResponse`.
    *   **401 Unauthorized:** Token ausente ou expirado. O AngularJS deve redirecionar para a tela de login. Retorna `ErrorResponse`.
    *   **403 Forbidden:** Usuário autenticado, mas sem privilégios de `admin`. Retorna `ErrorResponse`.

---

## 5. Modelos de Dados (Schemas)

### LoginRequest (Object)
*   `username` (String, Obrigatório): Nome de usuário. Exemplo: `usuario_sistema`
*   `password` (String, formato password, Obrigatório): Senha de acesso. Exemplo: `P@ssword123`

### LoginResponse (Object)
*   `access_token` (String): Token JWT gerado pelo Keycloak. Exemplo: `eyJhbGciOiJSUzI1...`
*   `expires_in` (Integer): Tempo de expiração em segundos. Exemplo: `3600`
*   `token_type` (String): Tipo do token gerado. Exemplo: `Bearer`

### Product (Object)
*   `id` (Integer, format int64): Identificador único gerado pelo JPA/PostgreSQL. Exemplo: `101`
*   `name` (String): Nome do produto. Exemplo: `Notebook Corporativo`
*   `price` (Number, format double): Preço de venda. Exemplo: `4500.00`
*   `sku` (String): Código identificador de estoque. Exemplo: `NTB-4500-X`

### ProductInput (Object)
*   `name` (String, Obrigatório): Nome do produto. Exemplo: `Notebook Corporativo`
*   `price` (Number, format double, Obrigatório): Preço de venda. Exemplo: `4500.00`
*   `sku` (String, Obrigatório): Código identificador de estoque. Exemplo: `NTB-4500-X`

### ErrorResponse (Object)
*   `status_code` (Integer, Obrigatório): Código de status HTTP gerado pela exceção. Exemplo: `400`
*   `message` (String, Obrigatório): Mensagem amigável tratada pelo `ExceptionMapper`. Exemplo: `"O campo 'sku' é obrigatório."`
*   `timestamp` (String, format date-time, Obrigatório): Momento exato em que o erro ocorreu. Exemplo: `"2026-10-06T15:30:00Z"`
