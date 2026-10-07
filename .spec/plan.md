# Plano de Implementação Técnica (Plan)

Este documento estabelece as etapas sequenciais para o desenvolvimento do projeto, garantindo a divisão modular de arquivos, conformidade arquitetural (MVC/EJB/JPA) e a cobertura de testes exigida.

---

## 1. Fase de Infraestrutura e Configurações Base
O objetivo desta fase é utilizar o ambiente existente e configurar a conectividade entre o WildFly local e os serviços Docker.

*   **Código da Aplicação:** Manter os módulos `Persistence`, `REST` e `Security` dentro da pasta `POC_Keycloak` na raiz do projeto.
*   **PostgreSQL e Keycloak:** Utilizar os serviços definidos em `Docker/docker-compose.yml`. O PostgreSQL e o Keycloak já são executados em containers; não instalar nem provisionar novas instâncias. O script `Docker/init-scripts/init.sql` cria os bancos `minha_app_db` (aplicação) e `keycloak_db` (Keycloak).
*   **WildFly 10.0.0.Final:** Utilizar a distribuição já presente na raiz do projeto (`wildfly-10.0.0.Final`), sem reinstalá-la.
    *   Registrar o driver JDBC e configurar no `standalone.xml` um DataSource JTA para `minha_app_db`, acessando o PostgreSQL pelo host e porta publicados pelo Docker (`localhost:5432`).
    *   Configurar a instância Keycloak 26.8.0 existente em `http://localhost:8081` para a aplicação: criar realm dedicado `poc-keycloak`, client confidencial `poc-keycloak-api` com Direct Access Grants habilitado e Standard Flow desabilitado, e role `admin` atribuída aos usuários autorizados.
    *   Configurar `keycloak.issuer` com `http://localhost:8081/realms/poc-keycloak` e `keycloak.client-id` com `poc-keycloak-api`; fornecer o client secret de forma externa em runtime. Manter o provider JAX-RS de validação JWT dentro do WAR da API.
*   **Estrutura de Persistência:** Criação do arquivo `persistence.xml` definindo o Hibernate como provedor JPA e delegando o gerenciamento de transações ao contêiner (`transaction-type="JTA"`).

---

## 2. Fase do Core Back-End (Camadas JPA e EJB)
Implementação da lógica de negócios e persistência relacional blindada, operando estritamente sob controle transacional automatizado (CMT).

### Camada de Persistência (JPA)
*   Mapeamento das entidades relacionais (ex: classe `Product.java`) utilizando anotações JPA nativas (`@Entity`, `@Table`, `@Id`).
*   Configuração de chaves e relacionamentos de banco de dados.

### Camada de Negócios (EJB 3.x)
*   **Isolamento Transacional:** Criação de Local Session Beans (`@Stateless`) específicos por domínio de negócio.
*   **Operações de Escrita:** Implementação dos métodos de persistência delegando o controle de commits e rollbacks ao contêiner através de **Container-Managed Transactions (CMT)**.
*   **Segurança em Profundidade:** Aplicação da anotação `@RolesAllowed("admin")` nos métodos restritos para garantir dupla validação de acesso na camada servidora.

---

## 3. Fase da Fachada REST (JAX-RS)
Construção da camada de apresentação servidora que funcionará estritamente como fachada (facade), desacoplada das entidades do banco.

*   **Implementação de Endpoints:** Criação de recursos JAX-RS mapeando as rotas públicas (`/auth/login`, `GET /products`) e restritas (`POST /products`).
*   **Injeção de Dependências:** Uso obrigatório de `@EJB` para acoplar os Session Beans aos controladores REST.
*   **Tratamento Global de Erros:** Criação de classes especializadas que implementam `ExceptionMapper<Throwable>`. O interceptor deve capturar exceções de negócio, ocultar StackTraces e serializar a resposta no formato padrão `ErrorResponse`.

---

## 4. Fase de Front-End (AngularJS 1.8.x SPA)
Construção da interface Single Page Application orientada à componentização e segurança.

*   **Modularização do App:** Divisão estrita da estrutura de arquivos de UI. Cada view, controller, service e directive deve residir em seu próprio arquivo isolado e especializado.
*   **Estilização com Bootstrap:** Aplicar Bootstrap 5.3.8 (release estável mais recente confirmada em 06/10/2026) pelo CSS do jsDelivr no `index.html`, fixando versão e hash SRI. Usar classes utilitárias e componentes CSS nas views existentes, sem jQuery ou plugins JavaScript.
*   **Telas responsivas:** Estilizar cabeçalho/navegação, card de login, listagem/tabela e formulário de produtos, botões, alertas e indicadores de carregamento. Preservar bindings AngularJS, validações, labels e comportamento do interceptor.
*   **Rehomologação da UI:** Reabrir TASK-05.04, TASK-05.05 e TASK-07.04 porque a apresentação mudou substancialmente. A lógica previamente concluída permanece implementada; a aprovação visual em desktop/mobile e a homologação do novo WAR em runtime ainda precisam ser refeitas.
*   **Interceptor de Segurança HTTP:**
    *   Criação de um Service Interceptor AngularJS para interceptar requisições de saída e injetar o cabeçalho `Authorization: Bearer <TOKEN>`.
    *   Configuração do tratamento de respostas globais para capturar status `401 Unauthorized` ou `403 Forbidden` e redirecionar imediatamente o usuário para as telas públicas.

---

## 5. Fase de Testes e Validação de Cobertura
Fase final e obrigatória de homologação técnica antes da liberação do código.

*   **Testes Unitários:** Escrita de testes utilizando **JUnit 5** focando no isolamento de regras de negócio dentro dos EJBs, Services e utilitários Java 1.8.
*   **Validação de Métrica:** Execução de ferramentas de análise de cobertura de código (ex: JaCoCo) para garantir conformidade com a meta mínima estipulada de **80% de cobertura**.

---

## 6. Fase de Build Maven e Deploy no WildFly
O build Maven deve transformar os módulos existentes em um WAR implantável e disponibilizá-lo automaticamente no WildFly já presente na raiz do projeto.

*   **Reator Maven:** Criar um POM agregador em `POC_Keycloak` que compile, nessa ordem, `Persistence`, `Security/keycloak-jwt-filter` e `REST`. Fixar propriedades comuns de versão e compilação Java 8; usar dependências Java EE compatíveis com WildFly 10 no escopo `provided`.
*   **WAR da aplicação:** Configurar `REST` como módulo WAR e incluir nele os JARs de `Persistence` e `Security/keycloak-jwt-filter`. Incorporar os arquivos estáticos da SPA (`Frontend/src/main/webapp`) ao WAR para que interface e API sejam servidas pela mesma origem.
*   **Destino de deploy:** No ciclo `package`, copiar `poc-keycloak-api.war` para `C:\Development_Environment\Projects\Keycloak_POC\wildfly-10.0.0.Final\standalone\deployments`. Centralizar o caminho em uma propriedade Maven `wildfly.deployments.dir`, com esse valor como padrão local e possibilidade de override por linha de comando.
*   **Execução e validação:** Executar `mvn clean package` a partir de `POC_Keycloak`; confirmar que o WAR existe em `REST/target` e no diretório de deployments. Iniciar o WildFly existente e confirmar que o scanner implanta o WAR sem erro. Não incluir secrets ou credenciais no artefato.
