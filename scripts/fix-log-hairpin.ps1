# Requires Administrator. Run ONLY on the PC that is currently hosting the bot.
# Makes http://YOUR_PUBLIC_IP:PORT work ON THIS PC (NAT hairpin workaround).
# If you move the bot to the mini PC / laptop, run undo-log-hairpin.ps1 on this
# PC first — otherwise :8890/:9090 links from here hit this machine and refuse.

$ErrorActionPreference = "Stop"
$publicIp = "68.39.20.91"
$loopback = "Loopback Pseudo-Interface 1"
$marker = "endzone-modmail-hairpin"
$hostsPath = "$env:SystemRoot\System32\drivers\etc\hosts"

$envFile = Join-Path (Split-Path $PSScriptRoot -Parent) ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*MODMAIL_LOGS_BASE_URL\s*=\s*https?://([^/:]+)') {
            $publicIp = $Matches[1].Trim()
        }
    }
}

$identity = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
if (-not $identity.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Error "Run this as Administrator (right-click PowerShell → Run as administrator)."
}

if (Test-Path $hostsPath) {
    $lines = @(Get-Content $hostsPath) | Where-Object {
        $_ -notmatch [regex]::Escape($marker) -and
        $_ -notmatch "^\s*\d[\d.]*\s+$([regex]::Escape($publicIp))(\s|$)"
    }
    Set-Content -Path $hostsPath -Value $lines -Encoding ascii
    ipconfig /flushdns | Out-Null
}

$existing = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -eq $publicIp }
if (-not $existing) {
    New-NetIPAddress -InterfaceAlias $loopback -IPAddress $publicIp -PrefixLength 32 -SkipAsSource $true | Out-Null
}

Write-Host "Assigned $publicIp/32 to $loopback (SkipAsSource)."
Write-Host "Discord can keep http://${publicIp}:<port>/logs/... on THIS PC only."
Write-Host "If you move the bot to another machine, run scripts\undo-log-hairpin.ps1 here first."
