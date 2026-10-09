# RBAC do realm `poc-keycloak`

`rbac.json` é a declaração versionada, sem usuários, senhas, client secrets ou
IDs gerados pelo Keycloak. `configure-rbac.ps1` aplica essa declaração usando a
Admin REST API. A operação é idempotente: grupos e roles existentes são
reutilizados e os mapeamentos são reaplicados.

Os três grupos são grupos de primeiro nível. Cada grupo recebe as quatro roles
granulares correspondentes; usuários membros herdam essas roles por meio do
mapeamento de roles do grupo. Não há herança entre `Admin`, `Sub-Admin` e
`User`: assim, a separação de privilégios definida na especificação é mantida.

## Aplicar

Com o Docker Compose em execução, a partir da raiz do repositório:

```powershell
& .\Docker\keycloak\configure-rbac.ps1
```

O script solicita credenciais temporárias de uma conta administrativa do realm
`master`. Elas são usadas apenas para obter o token e não são gravadas em
arquivo, variável persistente ou log. Para outro ambiente:

```powershell
& .\Docker\keycloak\configure-rbac.ps1 -KeycloakUrl 'https://keycloak.example' `
  -AdminUser 'admin' -Realm 'poc-keycloak'
```

O usuário administrador precisa poder consultar/criar grupos e roles no realm
de destino. Depois da execução, valide no Admin Console ou com:

```powershell
$token = '<token temporário>'
Invoke-RestMethod -Headers @{Authorization="Bearer $token"} `
  http://localhost:8081/admin/realms/poc-keycloak/groups
Invoke-RestMethod -Headers @{Authorization="Bearer $token"} `
  http://localhost:8081/admin/realms/poc-keycloak/roles
```

O arquivo não substitui a configuração do client `poc-keycloak-api` nem a
atribuição de usuários. Essas operações pertencem à configuração base e às
próximas tarefas; nenhum secret é necessário para versionar o RBAC.
