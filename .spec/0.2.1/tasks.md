# Lista de Tarefas (Tasks) - Versão 0.2.1

## Configuração e Alinhamento
- [x] **TASK-01:** Ler e seguir as diretrizes contidas no arquivo `AGENTS.md`.
- [x] **TASK-02:** Revisar o CSS padrão do projeto para reaproveitamento nos novos componentes.

---

## Módulo: Login
- [x] **TASK-03:** Localizar o componente e folha de estilo da tela de Login.
- [x] **TASK-04:** Ajustar a estilização para centralizar os elementos do formulário de Login horizontal e verticalmente na tela.
- [x] **TASK-05:** Validar a responsividade da tela de Login centralizada em diferentes tamanhos de viewport.

---

## Módulo: Reviews (Front-end)
- [x] **TASK-06:** Criar a estrutura base da tela/rota `/reviews`.
- [x] **TASK-07:** Criar a camada de serviço/integração com a API de Reviews:
    - [x] Implementar `GET /reviews` (Listagem).
    - [x] Implementar `POST /reviews` (Cadastro).
    - [x] Implementar `PUT /reviews/{id}` (Edição).
    - [x] Implementar `DELETE /reviews/{id}` (Exclusão).
- [x] **TASK-08:** Desenvolver a interface de listagem e formulário de cadastro de Reviews mantendo o CSS padrão do sistema.
- [x] **TASK-09:** Implementar o controle de exibição nos botões/ações de Edição e Deletar:
    - [x] Analisar o uso de `ng-if` para omitir completamente do DOM os elementos que o usuário não tem permissão por conta de *roles* ou autoria do registro.
    - [x] Garantir que o usuário só veja os botões de editar e deletar nos seus próprios registros.

---

## Validação e Finalização
- [x] **TASK-10:** Testar o fluxo completo de CRUD de Reviews com múltiplos perfis de usuário.
- [x] **TASK-11:** Confirmar se nenhum elemento oculto por *roles* está presente no DOM.
- [x] **TASK-12:** Revisão final do layout e padronização visual.
