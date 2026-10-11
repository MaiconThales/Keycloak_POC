# Plano de Implementação (Plan) - Versão 0.2.1

## 1. Contexto e Objetivos
O objetivo deste plano é estruturar a implementação do front-end para a release 0.2.1, focando na adição do módulo de **Reviews**, centralização visual da tela de **Login** e aplicação das diretrizes de controle de acesso e padrões de CSS.

---

## 2. Análise Técnica e Dependências

### 2.1 Análise de Diretrizes Globais
- Consultar e revisar o arquivo `AGENTS.md` antes do início do desenvolvimento.
- Garantir reutilização de classes CSS e componentes globais.

### 2.2 Análise do Controle de Exibição (`ng-if`)
- Avaliar e garantir o uso de diretivas condicionais estruturais (como `ng-if`) em vez de ocultamento apenas via CSS (`display: none` / `ng-show`), para assegurar que elementos sem permissão não sejam renderizados no DOM do navegador por razões de segurança e clean code.

---

## 3. Fases de Execução

### Fase 1: Preparação e Alinhamento
- Leitura do `AGENTS.md`.
- Mapeamento das regras de autenticação e identificação do usuário logado na sessão para comparar com os registros da API `/reviews`.

### Fase 2: Ajuste na Tela de Login
- Modificação das regras CSS/Flexbox/Grid da página de login.
- Centralização vertical e horizontal dos elementos de formulário.
- Testes em diferentes resoluções.

### Fase 3: Desenvolvimento do Módulo de Review
- Criação dos serviços de consumo de API (`GET`, `POST`, `PUT`, `DELETE` para `/reviews`).
- Criação da view/tela de Reviews respeitando o CSS padrão.
- Aplicação das condições de visibilidade para os botões de Edição e Exclusão (exibidos apenas se o usuário for dono do registro).

### Fase 4: Validação e Testes
- Validação de visualização de reviews por diferentes usuários.
- Validação do fluxo de cadastro.
- Validação do impedimento visual de editar/deletar registros de terceiros.
- Validação da centralização do login em telas mobile e desktop.