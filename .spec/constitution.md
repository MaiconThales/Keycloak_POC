# Constituição do Projeto (Constitution & Diretrizes)

## 1. Ambiente de Execução
*   **Código da Aplicação:** Manter todo o código-fonte e recursos codificados dentro de `POC_Keycloak` (submódulos `Persistence`, `REST` e `Security`).
*   **Servidor de Aplicação:** Utilizar o WildFly 10.0.0.Final já disponível na raiz do projeto (`wildfly-10.0.0.Final`); não instalar uma segunda distribuição.
*   **Serviços de Infraestrutura:** PostgreSQL 16 e Keycloak 26.8.0 são executados em Docker conforme `Docker/docker-compose.yml`. O PostgreSQL publica a porta `5432` e o Keycloak publica a porta `8081` no host.
*   **Bancos de Dados:** Usar `minha_app_db` para a aplicação e `keycloak_db` para o Keycloak, conforme provisionamento em `Docker/init-scripts/init.sql`. Com o WildFly executado no host, conectar ao PostgreSQL em `localhost:5432`; não usar o hostname Docker `postgres` nessa conexão.
*   **Configuração do Keycloak:** Configurar a instância que já está em execução no Docker para a aplicação: realm `poc-keycloak`, client confidencial `poc-keycloak-api` com Direct Access Grants habilitado e Standard Flow desabilitado, além da role de realm `admin` atribuída somente aos usuários autorizados (atualmente `system-admin`). Usar issuer `http://localhost:8081/realms/poc-keycloak`; configurar client ID e fornecer o secret externamente em runtime, sem armazená-lo no repositório.
*   **Build e deploy:** Usar Maven com um POM agregador em `POC_Keycloak` e módulos para `Persistence`, `Security/keycloak-jwt-filter` e `REST`. O build deve produzir um WAR executável pelo WildFly 10 e copiá-lo para `C:\Development_Environment\Projects\Keycloak_POC\wildfly-10.0.0.Final\standalone\deployments`, onde o deployment scanner fará o deploy. O caminho deve aceitar sobrescrita por propriedade Maven para outros ambientes.
*   **Empacotamento:** O WAR da API deve incluir os artefatos de persistência e segurança, além dos arquivos da SPA em `Frontend/src/main/webapp`. APIs Java EE fornecidas pelo WildFly devem ter escopo `provided`, para não empacotar implementações duplicadas no WAR. O build deve usar Java 8 e preservar as APIs `javax.*` compatíveis com WildFly 10.

## 2. Princípios de Arquitetura e Divisão de Arquivos
*   **Isolamento de Responsabilidades:** A camada REST deve atuar apenas como fachada (facade). Toda lógica de negócios e controle transacional deve residir estritamente nos **EJBs**.
*   **Segurança como Cidadã de Primeira Classe:** Nenhum endpoint de negócio deve ficar exposto sem validação explícita de Role, exceto os caminhos explicitamente definidos como públicos na especificação (`/products` público e `/auth/login`).
*   **Padrão Arquitetural:** Utilize o padrão MVC (Model-View-Controller) para separar claramente a camada de apresentação (REST), a camada de negócios (EJBs) e a camada de persistência (JPA).
*   **Modularidade e Divisão de Arquivos:** **Cada arquivo de código, configuração ou interface deve ser obrigatoriamente dividido em partes menores e especializadas**, garantindo alta coesão, baixo acoplamento e fácil manutenção. Componentes grandes ou monolíticos na camada REST, nos EJBs ou nos scripts AngularJS não serão aceitos.

## 3. Camada de Persistência e Transações
*   **Banco de Dados:** Utilização obrigatória do **PostgreSQL** via camada de persistência gerenciada pelo **JPA (Java Persistence API)** com Hibernate (provedor nativo do WildFly 10).
*   **Persistência Relacional:** Toda interação com o banco de dados deve ser feita por meio do `EntityManager`. É proibido o uso de queries JDBC puras ou gerenciamento manual de conexões.
*   **Gerenciamento Transacional (CMT):** O controle transacional deve ser delegado ao contêiner (**Container-Managed Transactions**). Métodos de escrita (salvar, atualizar, deletar) devem garantir atomicidade automática a partir dos EJBs. A camada REST jamais deve interagir diretamente com o `EntityManager`.

## 4. Convenções de Código e Nomenclatura (Java 1.8 & EJB)
*   **Injeção:** Utilizar `@Inject` para CDI sempre que aplicável e `@EJB` estritamente para a injeção dos Session Beans na camada REST.
*   **Tratamento de Erros:** Não retornar StackTraces nas respostas HTTP. Utilizar `ExceptionMapper` do JAX-RS para interceptar exceções de negócio e transformá-las em status HTTP adequados (ex: 400 Bad Request, 401 Unauthorized, 403 Forbidden).
*   **Gerenciamento de Recursos:** Fechar conexões ou Streams explicitamente utilizando o bloco try-with-resources do Java 8.
*   **Build reproduzível:** A partir de `POC_Keycloak`, `mvn clean package` deve compilar todos os módulos na ordem correta, gerar `poc-keycloak-api.war` e disponibilizar o mesmo artefato no diretório `standalone/deployments` configurado. Falhas de compilação ou empacotamento devem fazer o build falhar, sem deixar um WAR antigo representar uma execução bem-sucedida.

## 5. Camada de Apresentação (AngularJS 1.8.x)
*   **Estilo da UI:** Utilizar Bootstrap **5.3.8**, versão estável mais recente confirmada em 06/10/2026, com versão fixa no CDN oficial indicado pela documentação (jsDelivr), `integrity` e `crossorigin="anonymous"`. Carregar apenas CSS enquanto nenhum componente exigir JavaScript; não adicionar jQuery nem substituir o AngularJS.
*   **Layout e acessibilidade:** Aplicar layout responsivo, navegação com indicação da página atual, cards, formulários, botões, tabela responsiva e alertas nas telas de login e produtos. Preservar labels, estados de carregamento, mensagens acessíveis e regras de autenticação/validação existentes.
*   **Arquitetura Single Page Application (SPA):** Toda a interface deve ser construída utilizando AngularJS 1.8.x, totalmente isolada da camada de negócios servidora.
*   **Componentização da UI:** Seguindo o princípio de manutenibilidade, as views, controllers, services e directives do AngularJS devem ser estritamente separados em arquivos pequenos e dedicados por funcionalidade (evitar arquivos únicos de rotas ou módulos massivos).
*   **Interceptors HTTP (Segurança):** Criar um Interceptor no AngularJS para capturar todas as requisições de saída e injetar automaticamente o cabeçalho `Authorization: Bearer <TOKEN>` obtido após a autenticação.
*   **Tratamento de Sessão Expirada:** O interceptor deve escutar respostas `401 Unauthorized` ou `403 Forbidden` e redirecionar o usuário imediatamente para a tela pública correspondente.

## 6. Princípios de Segurança e Validação (WildFly 10 & Keycloak 26.8.0)
*   **Autenticação:** Baseada estritamente em tokens **JWT Bearer** emitidos pelo realm `poc-keycloak` da instância Keycloak em Docker. O endpoint de login usa o client `poc-keycloak-api` configurado para Direct Access Grants.
*   **Integração de Segurança:** A validação do JWT gerado pelo Keycloak 26.8.0 dentro do ambiente WildFly 10.0.0.Final deve ser realizada por filtro JAX-RS dedicado (`ContainerRequestFilter`) consultando o JWKS do issuer configurado. Validar assinatura RS256, emissor, expiração e role `admin`; não confiar somente na presença de um token.
*   **Autorização em Profundidade:** Validar as Roles mapeadas no token tanto na camada REST quanto nos EJBs (usando `@RolesAllowed` para garantir segurança em múltiplas camadas).

## 7. Padrão de Testes Obrigatório
*   **Cobertura de Código:** Cobertura mínima de **80%** para todos os módulos do projeto.
*   **Testes Unitários:** Utilizar **JUnit 5** para testes unitários de todos os EJBs, Services e classes utilitárias no ambiente Java 1.8.

## 8. Padrões de Acessibilidade e Consistência
*   **Consistência de Resposta:** Utilize um padrão único de Response para retornar os dados da API de forma consistente, incluindo mensagens de erro padronizadas e códigos HTTP apropriados em conformidade com as diretrizes do JAX-RS.
