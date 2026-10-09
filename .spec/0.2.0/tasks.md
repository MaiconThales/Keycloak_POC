# 📅 CRONOGRAMA DE TAREFAS (TASKS 0.2.0)

## [TASK-08] Refatoração de Banco de Dados e Modelagem JPA

- [ ] **[TASK-08.01] Liquidation & Migrations:** Criar script SQL para dropar estruturas de tabelas legadas e reconstruir os esquemas com IDs auto-incrementais nativos (`IDENTITY`) para `products` e `reviews`.
- [ ] **[TASK-08.02] Entidade Review:** Mapear a classe `Review.java` contendo a chave estrangeira `@ManyToOne` para `Product` e a coluna `user_keycloak_id` (String) para guardar o identificador do Keycloak.
- [ ] **[TASK-08.03] Cobertura Unitária da Persistência:** Escrever testes em JUnit 5 mockando o `EntityManager` para validar a persistência em cascata e relacionamentos de produtos e revisões, mantendo os 80% de cobertura mínima.

## [TASK-09] Atualização dos Filtros de Segurança e Camada EJB

- [ ] **[TASK-09.01] Mapeamento Multiclair das Roles:** Atualizar o `ContainerRequestFilter` (filtro JAX-RS de segurança) para ler o array de grupos e sub-roles específicas contidas no payload do JWT em tempo de execução.
- [ ] **[TASK-09.02] Implementação e Proteção dos EJBs:** Implementar os Session Beans das novas funcionalidades aplicando `@RolesAllowed` com o array completo de strings finas (ex: `@RolesAllowed({"Admin-Write", "Sub-Admin-Write"})`).
- [ ] **[TASK-09.03] Tratamento Global de Exceções de Acesso:** Garantir que o `ExceptionMapper` capture falhas de acesso negado geradas pelas novas roles e devolva respostas formatadas como `ErrorResponse` com status 403.

## [TASK-10] Implementação das Novas Fachadas REST (JAX-RS)

- [ ] **[TASK-10.01] Endpoints de Review:** Desenvolver o recurso `ReviewResource.java` com métodos estruturados para postagem e visualização de comentários. O ID do usuário comentador deve ser extraído de forma segura do token JWT do contexto da requisição.
- [ ] **[TASK-10.02] Endpoints de Usuários:** Criar o recurso administrativo `UserResource.java` protegido unicamente para a role `Admin-Read` e `Admin-Write`.
- [ ] **[TASK-10.03] Proteção da Rota de Listagem:** Remover a brecha pública do endpoint `GET /products`, injetando a obrigatoriedade de validação de token Bearer para as roles de leitura.

## [TASK-11] Evolução da Interface SPA (AngularJS & Bootstrap)

- [ ] **[TASK-11.01] Reestruturação de Rotas e Telas Públicas:** Configurar a árvore de estados da aplicação para que somente a tela de login seja acessada sem credenciais.
- [ ] **[TASK-11.02] Desenvolvimento do Menu Lateral (Sidebar):** Criar uma diretiva ou componente de layout lateral estilizado com Bootstrap 5.3.8 contendo links de navegação condicionados ao perfil do usuário conectado através de validação de permissões por expressões lógicas do AngularJS.
- [ ] **[TASK-11.03] Topbar e Sistema de Logout:** Implementar uma barra superior fixa contendo o botão de encerramento de sessão alinhado à esquerda. O clique deve acionar o descarte do token e o redirecionamento forçado para a view de login.
- [ ] **[TASK-11.04] Homologação e Verificação de Layout:** Validar o comportamento responsivo do menu lateral em telas menores (Mobile/Tablet), garantindo que ele colapse ou se ajuste sem quebrar a navegação da aplicação.