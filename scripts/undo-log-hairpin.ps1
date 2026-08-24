# Run in Administrator PowerShell.
# Removes 68.39.20.91 from THIS PC's loopback so :9090/:8890 links
# go to the mini PC / laptop instead of dying with connection refused.

$ErrorActionPreference = "Stop"
$publicIp = "68.39.20.91"
$loopback = "Loopback Pseudo-Interface 1"

$identity = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
if (-not $identity.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Error "Run this as Administrator (right-click PowerShell -> Run as administrator)."
}

$existing = @(Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -eq $publicIp })

if ($existing.Count -gt 0) {
    netsh interface ipv4 delete address $loopback $publicIp | Out-Null
    Write-Host "Removed $publicIp from this PC."
    Write-Host "Mini PC :9090 and laptop :8890 links can leave this machine again."
} else {
    Write-Host "$publicIp is not assigned on this PC. Nothing to remove."
}

Write-Host "Forward WAN TCP 9090 to the mini PC LAN IP."
Write-Host "Allow port 9090 in Windows Firewall on the mini PC."
