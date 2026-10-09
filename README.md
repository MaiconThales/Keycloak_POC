# POC: Migração do Keycloak 1.9.x para 26.8

Esta prova de conceito avalia a integração do Keycloak 26.8.0 com uma aplicação Java EE legada executada em Java 8 e WildFly 10.0.0.Final.
O objetivo é mapear mudanças de integração e validar autenticação, autorização e persistência sem atualizar a plataforma legada durante o experimento.

## 🎯 Motivação e Objetivos

O sistema de origem utiliza uma camada de identidade antiga, baseada no Keycloak 1.9.x. A atualização para a linha 26.8 busca recuperar recursos e suporte de segurança atuais, mas representa uma mudança ampla de arquitetura e APIs. Esta POC isola o impacto sobre o ecossistema legado antes de planejar uma migração do sistema principal.

Os objetivos são:

- Validar o login via OpenID Connect e a validação de access tokens emitidos pelo Keycloak 26.8.0.
- Verificar autorização por role e acesso protegido a uma API REST, mantendo Java 8, APIs `javax.*` e WildFly 10.
- Identificar o trabalho de infraestrutura necessário, incluindo datasource, driver JDBC e configuração do servidor.
- Registrar os limites da compatibilidade: o Keycloak roda separadamente em Docker; não se tenta executar o servidor Keycloak 26.8 dentro do WildFly legado.

O aplicativo não usa o adaptador Java antigo do Keycloak. O login troca credenciais pelo endpoint OIDC do Keycloak; um filtro JAX-RS próprio valida o JWT recebido, incluindo assinatura, emissor, expiração e role. Isso permite testar a integração preservando o runtime legado, sem implicar que o Keycloak 26.8 ou suas bibliotecas de servidor sejam compatíveis com Java 8/WildFly 10.

## 🛠️ Requisitos e Ferramentas Necessárias

| Ferramenta | Versão / requisito | Uso |
|---|---|---|
| JDK | Java 8 (1.8), obrigatório para executar o WildFly 10 incluído | Compilar e executar a aplicação |
| Maven | 3.6.3 ou superior recomendado; comando `mvn` disponível no `PATH` | Build, testes e empacotamento |
| Docker Engine e Docker Compose | Versões compatíveis com Compose v2 | Executar PostgreSQL e Keycloak |
| PostgreSQL | 16 (imagem definida no Compose) | Banco do Keycloak e banco da aplicação |
| Keycloak | 26.8.0 (imagem definida no Compose) | Emissor OIDC e provedor de identidade |
| WildFly | 10.0.0.Final, distribuição versionada no repositório | Hospedar o WAR; não instalar outra versão |
| PowerShell | Windows PowerShell 5.1 ou PowerShell 7+ | Script de build/deploy `deploy.init` |

O Maven usa compilação `release 8`, Java EE 7 com escopo `provided`, JUnit Jupiter 5.10.3, Surefire 3.2.5 e JaCoCo 0.8.12. É necessária conexão à internet no primeiro build para baixar dependências. O frontend também carrega AngularJS e Bootstrap de CDNs.

## 🚀 Como Executar o Projeto Localmente

Os comandos abaixo são para PowerShell, executados na raiz do repositório.

1. Inicie o PostgreSQL e o Keycloak:

   ```powershell
    docker compose --env-file .\Docker\.env -f .\Docker\docker-compose.yml up -d
    docker compose --env-file .\Docker\.env -f .\Docker\docker-compose.yml ps
   ```

   As portas publicadas no host são `5432` (PostgreSQL) e `8081` (Keycloak). Antes do primeiro `up`, copie `Docker\.env.example` para `Docker\.env` e substitua os valores. O Compose exige essas variáveis e o arquivo local é ignorado pelo Git; nunca reutilize essas credenciais fora do ambiente local. Para o datasource no WildFly, exporte os mesmos `POSTGRES_USER`/`POSTGRES_PASSWORD` como `APP_DB_USER`/`APP_DB_PASSWORD` no processo que inicia o servidor.

2. Configure o realm no console do Keycloak em `http://localhost:8081`:

   - Crie o realm `poc-keycloak`.
   - Crie o client `poc-keycloak-api` como confidencial (Client authentication habilitado) e habilite **Direct access grants**. O script de deploy também exige que o client esteja habilitado.
   - Crie/atribua a role `admin` aos usuários que poderão criar produtos.
   - Crie um usuário de teste e defina sua senha.
   - Para o script automatizado, use uma conta administrativa do realm `master` que possa consultar o client e ler seu secret. Contas que exigem MFA interativo não atendem ao fluxo de senha usado pelo script.

3. Prepare o schema da aplicação. O diretório PostgreSQL é persistido em `data\postgres`; os scripts de inicialização do container são executados apenas quando o diretório de dados é inicializado pela primeira vez. Como `create_tables.sql` pode ser executado antes de `init.sql` criar `minha_app_db`, aplique o schema explicitamente no banco correto:

   ```powershell
   docker exec -i postgres_db psql -U admin -d minha_app_db -f /docker-entrypoint-initdb.d/create_tables.sql
   ```

   O script cria as tabelas `products` e `users` de forma idempotente. Se o banco ainda não existir, verifique a inicialização do PostgreSQL antes de prosseguir.

4. Compile, execute os testes, empacote e implante no WildFly. O script valida o JDK 8, constrói o WAR, solicita credenciais administrativas do Keycloak para obter o secret do client e inicia o WildFly em primeiro plano:

   ```powershell
   & ([scriptblock]::Create((Get-Content -Raw .\deploy.init))) `
     -JavaHome 'C:\Program Files\Java\jdk1.8.0_202'
   ```

   Ajuste o caminho do JDK para a instalação local. Se `JAVA_HOME` já apontar para JDK 8, `-JavaHome` pode ser omitido. O processo usa por padrão Keycloak em `http://localhost:8081`, aplicação em `http://localhost:8080` e management em `9990`. Use `-HttpPort` ou `WILDFLY_HTTP_PORT` para outro valor. Forneça `KEYCLOAK_CLIENT_SECRET` no ambiente protegido; sem ele o script solicita uma conta administrativa apenas para obtê-lo em runtime. Para parar o servidor, encerre o processo em primeiro plano com `Ctrl+C`.

5. Acesse a SPA em `http://localhost:8080/`. A API fica em `http://localhost:8080/api/v1`; `GET /products` é público e `POST /products` exige bearer token válido com role `admin`.

Para executar somente build e testes, sem iniciar o servidor:

```powershell
Set-Location .\POC_Keycloak
mvn clean verify
```

O `verify` aplica o limite mínimo de 80% de cobertura de linhas em cada módulo. `mvn clean package` gera `REST\target\poc-keycloak-api.war` e, por padrão, também o copia para `wildfly-10.0.0.Final\standalone\deployments`. O diretório pode ser substituído por `-Dwildfly.deployments.dir=C:\caminho\deployments`.

## 🖥️ Guia de Replicação do Servidor (WildFly 10.0.0.Final)

Use a distribuição versionada em `wildfly-10.0.0.Final`. O perfil padrão `standalone.xml` é o usado pelo script de deploy. As demais configurações (`standalone-full.xml`, `standalone-ha.xml` etc.) não são necessárias para esta POC.

### Módulos e drivers adicionados

O driver PostgreSQL foi instalado como módulo JBoss Modules:

```text
wildfly-10.0.0.Final/
└── modules/system/layers/base/org/postgresql/main/
    ├── module.xml
    └── postgresql-42.7.5.jar
```

O `module.xml` define o módulo `org.postgresql`, aponta para o JAR 42.7.5 e declara dependências em `javax.api` e `javax.transaction.api`. Para replicar em outra distribuição WildFly 10.0.0.Final, copie a pasta `org\postgresql\main` mantendo esses nomes e confira o caminho e a versão do JAR.

Não foram adicionados módulos do adaptador Keycloak ao WildFly. O filtro JWT e a persistência são dependências Java empacotadas no WAR. `org.picketbox` é uma dependência já fornecida pelo WildFly e é referenciada em `REST\src\main\webapp\WEB-INF\jboss-deployment-structure.xml`.

### Configurações do WildFly

Em `standalone\configuration\standalone.xml`:

- `system-properties` define `keycloak.issuer` como `http://localhost:8081/realms/poc-keycloak` e `keycloak.client-id` como `poc-keycloak-api`. O script de deploy passa esses mesmos valores como argumentos de inicialização, permitindo substituir URL, realm e client.
- O datasource JTA `MinhaAppDS`, com JNDI `java:jboss/datasources/MinhaAppDS`, conecta a `jdbc:postgresql://localhost:5432/minha_app_db` usando o driver `postgresql`.
- A seção `drivers` registra o driver PostgreSQL no módulo `org.postgresql`, classe `org.postgresql.Driver`.
- Os security domains presentes (`other`, `jboss-web-policy`, `jboss-ejb-policy`, `jaspitest`) são os domínios padrão do perfil; esta POC não adiciona um security domain Keycloak. A autorização de tokens da API é feita no filtro da aplicação.

 O datasource recebe `APP_DB_USER` e `APP_DB_PASSWORD` somente do ambiente do processo WildFly; valores vazios fazem a conexão falhar, em vez de habilitar uma credencial embutida. O secret do client Keycloak não deve ser escrito no XML, no código, no WAR ou em propriedades de linha de comando. O script `deploy.init` aceita `KEYCLOAK_CLIENT_SECRET` já fornecido pelo runtime (ou o obtém interativamente do Keycloak) e só o expõe ao processo WildFly.

O persistence unit `MinhaAppPU`, em `Persistence\src\main\resources\META-INF\persistence.xml`, usa JTA, Hibernate fornecido pelo servidor e o JNDI `java:jboss/datasources/MinhaAppDS`. A aplicação depende de `minha_app_db` e da tabela `products`.

### Deployments

O artefato da aplicação é:

```text
POC_Keycloak\REST\target\poc-keycloak-api.war
```

O Maven o copia para:

```text
wildfly-10.0.0.Final\standalone\deployments\poc-keycloak-api.war
```

O WAR inclui classes REST, os JARs dos módulos `Persistence` e `keycloak-jwt-filter` e os arquivos estáticos da SPA em `Frontend\src\main\webapp`. As APIs Java EE são `provided` e não são empacotadas. `jboss-web.xml` define o context root `/`, servindo SPA e API na mesma origem; o endpoint REST usa `/api/v1`. O deployment scanner do WildFly 10 monitora a pasta (intervalo de 5 segundos na configuração incluída). Para deploy manual, copie o WAR e solicite o deploy com o marcador:

```powershell
Copy-Item .\POC_Keycloak\REST\target\poc-keycloak-api.war `
  .\wildfly-10.0.0.Final\standalone\deployments\
New-Item -ItemType File `
  .\wildfly-10.0.0.Final\standalone\deployments\poc-keycloak-api.war.dodeploy
```

Confirme a criação de `poc-keycloak-api.war.deployed` e a ausência de `poc-keycloak-api.war.failed`; em caso de falha, consulte `wildfly-10.0.0.Final\standalone\log\server.log`. Não é necessário implantar EAR nem o servidor Keycloak no WildFly.

## 📝 Desafios Encontrados e Aprendizados

- Keycloak 26.8.0 é um serviço externo, executado com Java moderno na imagem Docker; WildFly 10 continua preso ao runtime Java 8. Separar os processos evita tentar executar componentes incompatíveis no mesmo servidor.
- O WildFly 10 fornece Java EE 7 (`javax.*`), enquanto a integração é implementada via endpoints OIDC/JWKS e validação JWT própria, não via adaptador antigo acoplado ao servidor.
- O datasource e o driver JDBC precisam ser configurados no WildFly separadamente da conexão PostgreSQL usada pelo Keycloak.
- A obtenção do token de login requer que o client confidencial tenha Direct access grants habilitado. A API não persiste nem registra a senha do usuário; para ambientes não locais, use HTTPS.
- Credenciais não são versionadas: Docker exige `Docker/.env` local e WildFly exige `APP_DB_USER`/`APP_DB_PASSWORD` no ambiente. O secret Keycloak é tratado em runtime e deve continuar fora do repositório e do artefato. Credenciais usadas em commits antigos devem ser auditadas e rotacionadas; esta correção não reescreve o histórico.
