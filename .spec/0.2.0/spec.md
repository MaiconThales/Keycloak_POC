# 📑 ESPECIFICAÇÃO TÉCNICA DA API (SPEC 0.2.0)

## 1. Controle de Acesso e Matriz de Autorização (RBAC)
A autenticação permanece baseada em tokens **JWT Bearer** emitidos pelo Keycloak 26.8.0 sob o realm `poc-keycloak`. No entanto, o sistema passa a adotar um controle de acesso granular baseado nas ações de CRUD mapeadas no token (tanto nas chaves de `roles` quanto de `groups`).

A validação de segurança deve ser aplicada em profundidade: interceptores no AngularJS tratam a visibilidade de menus, filtros JAX-RS barram rotas na fachada REST, e anotações `@RolesAllowed` protegem a execução dos EJBs.

| Recurso / Rota REST | Operação HTTP | Roles Keycloak Permitidas | Grupo Equivalente | Contexto de Negócio |
| :--- | :--- | :--- | :--- | :--- |
| `/auth/login` | `POST` | *Público* | Qualquer um | Autenticação inicial e emissão do JWT. |
| `/products` | `GET` | `Admin-Read`, `Sud-Admin-Read`, `User-Read` | Admin, Sub-Admin, User | Listagem geral de produtos (agora protegida). |
| `/products` | `POST` | `Admin-Write`, `Sud-Admin-Write` | Admin, Sub-Admin | Criação de novos produtos no catálogo. |
| `/products/{id}` | `PUT` | `Admin-Update`, `Sud-Admin-Update` | Admin, Sub-Admin | Alteração de dados de um produto existente. |
| `/products/{id}` | `DELETE` | `Admin-Delete`, `Sud-Admin-Delete` | Admin, Sub-Admin | Exclusão física. |
| `/users` | `GET` | `Admin-Read` | Admin | Listagem de usuários integrados. |
| `/users` | `POST` | `Admin-Write` | Admin | Provisionamento/registro de novos usuários. |
| `/users/{id}` | `PUT` | `Admin-Update` | Admin | Atualização de metadados de usuários. |
| `/users/{id}` | `DELETE` | `Admin-Delete` | Admin | Remoção de usuários. |
| `/reviews` | `GET` | `Admin-Read`, `Sud-Admin-Read`, `User-Read` | Admin, Sub-Admin, User | Visualização de comentários de produtos. |
| `/reviews` | `POST` | `Admin-Write`, `Sud-Admin-Write`, `User-Write` | Admin, Sub-Admin, User | Inserção de um comentário sobre um produto. |
| `/reviews/{id}` | `PUT` | `Admin-Update`, `Sud-Admin-Update`, `User-Update` | Admin, Sub-Admin, User | Edição de um comentário próprio. |
| `/reviews/{id}` | `DELETE` | `Admin-Delete`, `Sud-Admin-Delete`, `User-Delete` | Admin, Sub-Admin, User | Remoção de um comentário próprio. |

---

## 2. Modelagem de Dados Relacional (JPA)
A arquitetura de banco de dados foi simplificada para eliminar redundâncias com o provedor de identidade. **Não deve existir tabela de usuários local**.

```
  +-----------------------+              +-----------------------+
  |        PRODUCTS       |              |        REVIEWS        |
  +-----------------------+              +-----------------------+
  | PK | id (BIGINT - AUTO)              | PK | id (BIGINT - AUTO)
  |    | name (VARCHAR)   |<------------ | FK | product_id(BIGINT)
  |    | price (NUMERIC)  |              |    | user_keycloak_id(UUID)
  |    | sku (VARCHAR)    |              |    | comment (TEXT)   |
  +-----------------------+              +-----------------------+
```

### Regras de Persistência:
*   **Auto-incremento:** Todas as tabelas ativas utilizam chaves primárias numéricas geradas por estratégias nativas do PostgreSQL (`GenerationType.IDENTITY`).
*   **Vínculo com Keycloak:** A tabela `REVIEWS` armazena uma coluna indexada do tipo String/UUID contendo o identificador do usuário (`sub` claim do JWT) correspondente ao registro do Keycloak, sem integridade referencial física por `FOREIGN KEY` no banco de dados local.

---

## 3. Endpoints e Contratos REST Expandidos

### 3.1 Submódulo: `/products`
*   `GET /api/v1/products` → Retorna array de `ProductResponse`. Exige Role de leitura.
*   `POST /api/v1/products` → Envia `ProductInput`. Retorna status `201 Created`.
*   `PUT /api/v1/products/{id}` → Envia `ProductInput`. Atualiza o registro. Retorna status `200 OK`.
*   `DELETE /api/v1/products/{id}` → Remove o produto. Retorna status `204 No Content`.

### 3.2 Submódulo: `/users` (Exclusivo Admin)
*   `GET /api/v1/users` → Lista metadados básicos integrados. Retorna status `200 OK`.
*   `POST /api/v1/users` → Envia dados cadastrais de controle corporativo. Retorna status `201 Created`.

### 3.3 Submódulo: `/reviews` (Qualquer Usuário Autenticado)
*   `GET /api/v1/reviews?productId={id}` → Filtra os comentários vinculados a um produto específico.
*   `POST /api/v1/reviews` → Envia o payload abaixo:
    ```json
    {
      "product_id": 101,
      "comment": "Excelente custo benefício, o notebook superou as expectativas."
    }
    ```
    *O backend captura o ID do Keycloak implicitamente a partir do contexto de segurança do Token JWT validado.*