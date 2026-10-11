# 🗺️ PLANO DE IMPLEMENTAÇÃO TÉCNICA (PLAN 0.2.0)

## Fase 1: Ajustes de Infraestrutura e Database Migration
1.  **Refatoração do Banco:** Criar scripts SQL de migração para dropar as tabelas antigas.
2.  **Modelagem JPA:** Desenvolver a nova entidade `Review.java` e ajustar `Product.java` para refletir o relacionamento unidirecional.
3.  **Ajuste do Keycloak Context:** Atualizar as configurações de segurança do backend para ler a árvore de claims do token JWT, extraindo as novas permissões de granularidade de escrita, leitura, deleção e atualização (`*-Read`, `*-Write`, etc.).

## Fase 2: Camada de Negócios e Fachada REST
1.  **Segurança EJB:** Alterar as anotações `@RolesAllowed` nos métodos dos Session Beans existentes e novos (`ReviewServiceBean` e `UserServiceBean`) para validar as novas strings de roles finas.
2.  **Fachada REST:** Implementar os controladores JAX-RS para os caminhos de `/users` e `/reviews`. Remover a brecha pública de listagem de produtos, adicionando a validação do filtro de segurança para todas as rotas de negócio.

## Fase 3: Front-End (AngularJS Single Page Application)
1.  **Bloqueio de Rotas Públicas:** Atualizar as rotas do AngularJS para que apenas o estado `/login` seja exposto publicamente. Se um usuário não autenticado tentar forçar qualquer outra URL, ele é redirecionado de volta imediatamente.
2.  **Menu Lateral Dinâmico (Sidebar):** Implementar um componente de layout contendo a navegação do sistema em um menu lateral. As opções de menu devem usar a diretiva `ng-if` alimentada pelas roles decodificadas do token:
    *   Menu "Usuários" visível apenas para quem possui o grupo `Admin`.
    *   Menu "Produtos" visível para `Admin`, `Sub-Admin` e `User`.
3.  **Área Superior (Navbar):** Fixar um cabeçalho superior que contenha o botão de **Logout** posicionado no canto superior esquerdo. Ao clicar, o `authService.js` deve limpar o token da memória, invalidar o estado e redirecionar para a tela de login.