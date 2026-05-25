# Re-run the Khasab import chain against the running stack.
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) '..')
& .\deploy.ps1 -SkipBuild -Force
