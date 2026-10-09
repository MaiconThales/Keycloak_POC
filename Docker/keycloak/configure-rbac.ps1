[CmdletBinding()]
param(
    [string]$KeycloakUrl = 'http://localhost:8081',
    [string]$Realm = 'poc-keycloak',
    [string]$AdminRealm = 'master',
    [string]$ConfigPath = "$PSScriptRoot\rbac.json",
    [string]$AdminUser,
    [SecureString]$AdminPassword
)

$ErrorActionPreference = 'Stop'

function Get-AdminToken {
    param([string]$BaseUrl, [string]$LoginRealm, [string]$Username, [SecureString]$Password)
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Password))
    try {
        $body = @{ client_id = 'admin-cli'; username = $Username; password = $plainPassword; grant_type = 'password' }
        (Invoke-RestMethod -Method Post -Uri "$BaseUrl/realms/$LoginRealm/protocol/openid-connect/token" `
            -ContentType 'application/x-www-form-urlencoded' -Body $body).access_token
    } finally {
        $plainPassword = $null
    }
}

function Invoke-Keycloak {
    param([string]$Method, [string]$Uri, [string]$Token, [object]$Body)
    $headers = @{ Authorization = "Bearer $Token" }
    $parameters = @{ Method = $Method; Uri = $Uri; Headers = $headers }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = ($Body | ConvertTo-Json -Depth 10 -Compress)
    }
    Invoke-RestMethod @parameters
}

function Find-OrCreateRole {
    param([string]$BaseUrl, [string]$Token, [string]$TargetRealm, [string]$RoleName)
    $roles = Invoke-Keycloak -Method Get -Uri "$BaseUrl/admin/realms/$TargetRealm/roles" -Token $Token
    $role = @($roles | Where-Object { $_.name -eq $RoleName }) | Select-Object -First 1
    if ($null -eq $role) {
        Invoke-Keycloak -Method Post -Uri "$BaseUrl/admin/realms/$TargetRealm/roles" -Token $Token `
            -Body @{ name = $RoleName; description = "RBAC role $RoleName" } | Out-Null
        $role = Invoke-Keycloak -Method Get -Uri "$BaseUrl/admin/realms/$TargetRealm/roles/$RoleName" -Token $Token
    }
    $role
}

function Find-OrCreateGroup {
    param([string]$BaseUrl, [string]$Token, [string]$TargetRealm, [string]$GroupName)
    $groups = Invoke-Keycloak -Method Get -Uri "$BaseUrl/admin/realms/$TargetRealm/groups?briefRepresentation=true" -Token $Token
    $group = @($groups | Where-Object { $_.name -eq $GroupName }) | Select-Object -First 1
    if ($null -eq $group) {
        Invoke-Keycloak -Method Post -Uri "$BaseUrl/admin/realms/$TargetRealm/groups" -Token $Token `
            -Body @{ name = $GroupName } | Out-Null
        $groups = Invoke-Keycloak -Method Get -Uri "$BaseUrl/admin/realms/$TargetRealm/groups?briefRepresentation=true" -Token $Token
        $group = @($groups | Where-Object { $_.name -eq $GroupName }) | Select-Object -First 1
    }
    $group
}

$config = Get-Content -Raw -LiteralPath $ConfigPath | ConvertFrom-Json
if ($config.realm -ne $Realm) { throw "Configuração destinada ao realm '$($config.realm)', não '$Realm'." }
if (-not $AdminUser) { $AdminUser = Read-Host 'Usuário administrador do Keycloak (master)' }
if (-not $AdminPassword) { $AdminPassword = Read-Host 'Senha do administrador do Keycloak' -AsSecureString }
$token = Get-AdminToken -BaseUrl $KeycloakUrl.TrimEnd('/') -LoginRealm $AdminRealm -Username $AdminUser -Password $AdminPassword

foreach ($groupConfig in @($config.groups)) {
    $group = Find-OrCreateGroup -BaseUrl $KeycloakUrl.TrimEnd('/') -Token $token -TargetRealm $Realm -GroupName $groupConfig.name
    $roleRepresentations = @($groupConfig.roles | ForEach-Object {
        Find-OrCreateRole -BaseUrl $KeycloakUrl.TrimEnd('/') -Token $token -TargetRealm $Realm -RoleName $_
    })
    Invoke-Keycloak -Method Post -Uri "$($KeycloakUrl.TrimEnd('/'))/admin/realms/$Realm/groups/$($group.id)/role-mappings/realm" `
        -Token $token -Body $roleRepresentations | Out-Null
    Write-Host "Grupo '$($groupConfig.name)' configurado com $($roleRepresentations.Count) roles."
}

Write-Host "RBAC aplicado ao realm '$Realm'. Nenhum usuário ou segredo foi criado ou armazenado."
