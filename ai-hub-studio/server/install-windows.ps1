# Execute por duplo clique em Instalar-e-Iniciar.cmd. Não expõe portas à Internet.
param([switch]$InstallOllama,[switch]$InstallComfyUI,[string]$Model='', [string]$BindAddress='')
$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if ($PSBoundParameters.Count -eq 0) {
    Write-Host 'AI HUB STUDIO: 1 = instalar Ollama + modelo de texto pequeno; 2 = servidor para ferramentas existentes; 3 = usar apenas APIs no Android (sem PC)'
    $modeChoice=Read-Host 'Escolha 1, 2 ou 3'
    if ($modeChoice -eq '3') {Write-Host 'Na app abra Configuração e escolha Pollinations sem chave ou uma chave gratuita própria. Não é necessário instalar o servidor.';exit 0}
    if ($modeChoice -eq '1') {
        $InstallOllama=$true
        Write-Host 'O modelo implica download e espaço em disco. Confirme a licença no catálogo https://ollama.com/library/qwen3'
        $Model=Read-Host 'Nome do modelo (Enter = qwen3:1.7b, adequado para começar com 16 GB)'
        if (!$Model) {$Model='qwen3:1.7b'}
    } elseif ($modeChoice -ne '2') {throw 'Escolha inválida. Não foi instalado nenhum componente.'}
}
function Install-Package([string]$Id) {
    if (!(Get-Command winget -ErrorAction SilentlyContinue)) {throw 'Instale o App Installer (winget) da Microsoft Store e repita.'}
    & winget install --id $Id --exact --source winget --accept-source-agreements --accept-package-agreements
    if ($LASTEXITCODE -notin @(0,-1978335189)) {throw "Não foi possível instalar $Id. Consulte a mensagem do winget."}
}
if (!(Get-Command py -ErrorAction SilentlyContinue)) {
    Install-Package 'Python.Python.3.12'
    $env:Path=[Environment]::GetEnvironmentVariable('Path','Machine')+';'+[Environment]::GetEnvironmentVariable('Path','User')
    if (!(Get-Command py -ErrorAction SilentlyContinue)) { Write-Host 'Python instalado. Reabra Instalar-e-Iniciar.cmd para atualizar o PATH.';exit 0 }
}
if (!(Test-Path .venv\Scripts\python.exe)) { & py -3 -m venv .venv; if($LASTEXITCODE){throw 'Falhou a criação do ambiente Python'} }
$Python=(Resolve-Path .venv\Scripts\python.exe).Path
& $Python -m pip install -r server\requirements.txt
if($LASTEXITCODE){throw 'Falhou a instalação de dependências; confirme Internet/proxy e volte a executar.'}
if (!(Get-Command ffmpeg -ErrorAction SilentlyContinue)) {
    Install-Package 'Gyan.FFmpeg'
    $env:Path=[Environment]::GetEnvironmentVariable('Path','Machine')+';'+[Environment]::GetEnvironmentVariable('Path','User')
}
if ($InstallOllama) {
    if (!(Get-Command ollama -ErrorAction SilentlyContinue)) {Install-Package 'Ollama.Ollama'; $env:Path+=';'+[Environment]::GetEnvironmentVariable('Path','User')}
    if (!(Get-Command ollama -ErrorAction SilentlyContinue)) {throw 'Ollama instalado; feche e volte a executar para atualizar o PATH.'}
    try {$null=Invoke-RestMethod 'http://127.0.0.1:11434/api/tags' -TimeoutSec 3} catch {Start-Process ollama -ArgumentList 'serve';Start-Sleep -Seconds 4}
    if($Model){ & ollama pull $Model; if($LASTEXITCODE){throw 'Falhou o download do modelo. Confirme nome, licença e espaço livre.'} }
}
if ($InstallComfyUI) { Start-Process 'https://www.comfy.org/download'; Write-Host 'Instale ComfyUI Desktop pelo instalador oficial; pesos e nós dependem do hardware. Não são simulados nem instalados sem escolha.' }
if (!$BindAddress) {
    $addresses=@(Get-NetIPConfiguration | Where-Object {$_.NetAdapter.Status -eq 'Up' -and $_.IPv4DefaultGateway} | ForEach-Object {$_.IPv4Address.IPAddress})
    if(!$addresses.Count){throw 'Sem endereço IPv4 ativo com gateway. Ligue o PC à rede privada ou use -BindAddress IP.'}
    $BindAddress=$addresses[0]
}
$profile=Get-NetConnectionProfile | Where-Object {$_.IPv4Connectivity -ne 'Disconnected'}
if (!($profile | Where-Object {$_.NetworkCategory -eq 'Private'})) {throw 'Defina esta rede doméstica como Privada no Windows. Não será aberta uma firewall numa rede pública.'}
$admin=([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if ($admin) {
    if(!(Get-NetFirewallRule -DisplayName 'AI Hub Studio TLS LAN' -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -DisplayName 'AI Hub Studio TLS LAN' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8787 -LocalAddress $BindAddress -RemoteAddress LocalSubnet -Profile Private | Out-Null
    } else { Get-NetFirewallRule -DisplayName 'AI Hub Studio TLS LAN' | Get-NetFirewallAddressFilter | Set-NetFirewallAddressFilter -LocalAddress $BindAddress -RemoteAddress LocalSubnet }
    if(!(Get-NetFirewallRule -DisplayName 'AI Hub Studio mDNS LAN' -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -DisplayName 'AI Hub Studio mDNS LAN' -Direction Inbound -Action Allow -Protocol UDP -LocalPort 5353 -RemoteAddress LocalSubnet -Profile Private -Program $Python | Out-Null
    }
} else { Write-Host 'Para criar as regras de firewall privadas, execute o CMD como administrador uma vez. Se já existem, pode continuar sem privilégios.' }
$data=Join-Path $env:LOCALAPPDATA 'AIHubStudio'
New-Item -ItemType Directory -Force $data | Out-Null
& icacls $data /inheritance:r /grant:r "${env:USERNAME}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null
# Preparação sem arrancar para permitir abrir o ficheiro de emparelhamento.
& $Python -c 'from server.network import prepare; import sys; prepare(sys.argv[1],sys.argv[2])' $data $BindAddress
if($LASTEXITCODE){throw 'Não foi possível preparar o certificado de rede.'}
Start-Process notepad (Join-Path $data 'emparelhamento.json')
Write-Host 'No Android: Configuração inicial > Servidor PC > Procurar na rede. Compare a impressão digital e introduza o token do ficheiro aberto.'
Write-Host 'Ollama e ComfyUI podem ficar em 127.0.0.1 NO PC; o AI Hub é a ponte TLS para o Android.'
& $Python -m server.launch --lan --host $BindAddress --data $data
