# Especificação Técnica (Spec) - Versão 0.2.1

## 1. Visão Geral
Esta especificação define o escopo do front-end para a versão 0.2.1, englobando a criação da interface de **Reviews** (avaliações), ajustes visuais no layout da tela de **Login** e o cumprimento rigoroso de controle de acesso visual por papéis (roles) e regras globais do projeto.

---

## 2. Regras e Arquivos Globais
- **Instruções Gerais de Agentes:** Seguir rigorosamente as diretrizes contidas no arquivo `AGENTS.md`.
- **Manutenção de Padrões:** Todo o código desenvolvido deve seguir os padrões arquiteturais, estruturais e visuais (CSS) pré-existentes no projeto.

---

## 3. Módulo: Review

### 3.1 Descrição
Criação da tela de gerenciamento de **Reviews**, permitindo a listagem, cadastro, edição e exclusão de registros.

### 3.2 Integração de APIs (Rotas do Back-end)
As rotas e proteções já se encontram implementadas e protegidas no back-end.

| Método | Rota           | Descrição                   | Permissões de Acesso (Back-end/Front-end) |
| :----- | :------------- | :-------------------------- | :---------------------------------------- |
| `GET`  | `/reviews`     | Listar todas as avaliações  | Todos os usuários logados |
| `POST` | `/reviews`     | Cadastrar nova avaliação    | Todos os usuários logados |
| `PUT`  | `/reviews/{id}`| Editar avaliação existente  | Apenas o próprio autor do registro       |
| `DELETE`| `/reviews/{id}`| Deletar avaliação existente | Apenas o próprio autor do registro       |

### 3.3 Regras de Negócio e Comportamento Visual
1. **Visualização e Cadastro:** Qualquer usuário autenticado/acessando a tela pode visualizar os registros e cadastrar uma nova review.
2. **Propriedade dos Registros:** As ações de **Editar** (`PUT`) e **Deletar** (`DELETE`) devem estar visíveis/disponíveis apenas para o usuário criador do registro correspondente.
3. **Controle de Acesso de Interface:** Elementos e ações que o usuário não possui permissão para acessar com base em suas *roles* ou autoria devem obrigatoriamente permanecer ocultos da árvore DOM (avaliar a utilização de `ng-if`).

---

## 4. Módulo: Login

### 4.1 Descrição
Ajuste de layout na tela de Login para garantir a centralização completa dos elementos de formulário e autenticação.

### 4.2 Requisitos Visuais
- **Centralização:** Mover os elementos da tela de login para o centro exato do viewport (horizontal e verticalmente).
- **Correção:** Ajustar o alinhamento atual (que se encontra deslocado para o canto superior esquerdo).
- **Estilização:** Utilizar e estender o CSS padrão do sistema, sem quebrar a responsividade.