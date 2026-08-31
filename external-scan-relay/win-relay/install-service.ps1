# Run in an elevated PowerShell from the published application directory.
$ErrorActionPreference = "Stop"
$ServiceName = "AliyunApiRelay"
$InstallDir = Join-Path $env:ProgramFiles $ServiceName
$SourceDir = Split-Path -Parent $MyInvocation.MyCommand.Path

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
Copy-Item (Join-Path $SourceDir "AliyunApiRelay.exe") $InstallDir -Force
Copy-Item (Join-Path $SourceDir "relay-config.json") $InstallDir -Force

& netsh.exe http delete urlacl url=http://+:80/aliyun-relay/ 2>$null
& netsh.exe http add urlacl url=http://+:80/aliyun-relay/ "sddl=D:(A;;GX;;;SY)"

if (Get-Service $ServiceName -ErrorAction SilentlyContinue) {
    Stop-Service $ServiceName -ErrorAction SilentlyContinue
    & sc.exe delete $ServiceName | Out-Null
    Start-Sleep -Seconds 2
}

& sc.exe create $ServiceName binPath= "`"$InstallDir\AliyunApiRelay.exe`"" start= auto obj= LocalSystem DisplayName= "阿里云 API 中转服务"
& sc.exe failure $ServiceName reset= 86400 actions= "restart/5000/restart/10000/restart/30000"
& sc.exe description $ServiceName "公司 Linux 到阿里云标准 HTTPS API 的专用中转服务"
Start-Service $ServiceName
Get-Service $ServiceName
