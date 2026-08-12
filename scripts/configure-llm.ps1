[CmdletBinding()]
param(
    [ValidateSet('DeepSeek', 'Qwen', 'Local')]
    [string]$Provider,

    [string]$ApiKey,
    [string]$BaseUrl,
    [string]$Model,

    # Kept for compatibility. Providers are preserved by default now.
    [switch]$KeepOtherProviders,
    [switch]$DisableOtherProviders,
    [switch]$StartBackend
)

$ErrorActionPreference = 'Stop'

function Set-ApplyMateEnvironmentVariable {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Value
    )

    # setx persists the value for the current Windows user. Process makes it
    # available immediately to Maven/Java started from this script.
    & setx.exe $Name $Value | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "setx failed for environment variable: $Name"
    }
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}

function Read-SecretText {
    param([Parameter(Mandatory = $true)][string]$Prompt)

    $secureValue = Read-Host $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

if (-not $Provider) {
    Write-Host 'Select the LLM provider for ApplyMate:' -ForegroundColor Cyan
    Write-Host '  1. DeepSeek (recommended for lower API cost)'
    Write-Host '  2. Qwen (recommended for Chinese content)'
    Write-Host '  3. Local (Ollama / LM Studio)'
    $selection = Read-Host 'Enter 1, 2, or 3'
    $Provider = switch ($selection) {
        '1' { 'DeepSeek' }
        '2' { 'Qwen' }
        '3' { 'Local' }
        default { throw 'Invalid selection. Run the script again and enter 1, 2, or 3.' }
    }
}

$settings = switch ($Provider) {
    'DeepSeek' {
        @{
            Prefix = 'APPLYMATE_DEEPSEEK'
            BaseUrl = 'https://api.deepseek.com'
            Model = 'deepseek-v4-flash'
            RequiresKey = $true
        }
    }
    'Qwen' {
        @{
            Prefix = 'APPLYMATE_QWEN'
            BaseUrl = 'https://dashscope.aliyuncs.com/compatible-mode/v1'
            Model = 'qwen-plus'
            RequiresKey = $true
        }
    }
    'Local' {
        @{
            Prefix = 'APPLYMATE_LOCAL_LLM'
            BaseUrl = 'http://127.0.0.1:11434/v1'
            Model = 'qwen3:8b'
            RequiresKey = $false
        }
    }
}

if ($KeepOtherProviders -and $DisableOtherProviders) {
    throw 'KeepOtherProviders and DisableOtherProviders cannot be used together.'
}

if ($DisableOtherProviders) {
    foreach ($enabledName in @(
        'APPLYMATE_DEEPSEEK_ENABLED',
        'APPLYMATE_QWEN_ENABLED',
        'APPLYMATE_LOCAL_LLM_ENABLED'
    )) {
        Set-ApplyMateEnvironmentVariable -Name $enabledName -Value 'false'
    }
}

$resolvedBaseUrl = if ($BaseUrl) { $BaseUrl.TrimEnd('/') } else { $settings.BaseUrl }
$resolvedModel = if ($Model) { $Model } else { $settings.Model }

if ($settings.RequiresKey -and [string]::IsNullOrWhiteSpace($ApiKey)) {
    $ApiKey = Read-SecretText "Enter the $Provider API key (input is hidden)"
}
if ($settings.RequiresKey -and [string]::IsNullOrWhiteSpace($ApiKey)) {
    throw "$Provider API key cannot be empty."
}
if (-not $settings.RequiresKey -and [string]::IsNullOrWhiteSpace($ApiKey)) {
    $ApiKey = 'local'
}

Set-ApplyMateEnvironmentVariable -Name "$($settings.Prefix)_ENABLED" -Value 'true'
Set-ApplyMateEnvironmentVariable -Name "$($settings.Prefix)_BASE_URL" -Value $resolvedBaseUrl
Set-ApplyMateEnvironmentVariable -Name "$($settings.Prefix)_API_KEY" -Value $ApiKey
Set-ApplyMateEnvironmentVariable -Name "$($settings.Prefix)_MODEL" -Value $resolvedModel

$verification = @{
    "$($settings.Prefix)_ENABLED" = 'true'
    "$($settings.Prefix)_BASE_URL" = $resolvedBaseUrl
    "$($settings.Prefix)_API_KEY" = $ApiKey
    "$($settings.Prefix)_MODEL" = $resolvedModel
}
foreach ($entry in $verification.GetEnumerator()) {
    $savedValue = [Environment]::GetEnvironmentVariable($entry.Key, 'User')
    if ($savedValue -ne $entry.Value) {
        throw "Failed to verify the current-user environment variable: $($entry.Key)"
    }
}

Write-Host ''
Write-Host 'ApplyMate LLM environment variables configured:' -ForegroundColor Green
Write-Host "  Provider : $Provider"
Write-Host "  Base URL : $resolvedBaseUrl"
Write-Host "  Model    : $resolvedModel"
Write-Host "  Others   : $(if ($DisableOtherProviders) { 'disabled' } else { 'preserved' })"
Write-Host '  API Key  : saved to the current-user environment (hidden)'

if ($StartBackend) {
    # A PowerShell window opened before setx does not automatically inherit the
    # newly persisted values. Hydrate every configured provider so one backend
    # process can expose and switch between multiple models immediately.
    $userVariables = [Environment]::GetEnvironmentVariables('User')
    foreach ($name in $userVariables.Keys) {
        if ([string]$name -like 'APPLYMATE_*') {
            [Environment]::SetEnvironmentVariable([string]$name, [string]$userVariables[$name], 'Process')
        }
    }
    $projectRoot = Split-Path -Parent $PSScriptRoot
    $backendDirectory = Join-Path $projectRoot 'backend'
    Write-Host ''
    Write-Host 'Starting the backend with the new configuration...' -ForegroundColor Cyan
    Push-Location $backendDirectory
    try {
        & mvn spring-boot:run
        exit $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
}

Write-Host ''
Write-Host 'The configuration is persistent for programs started in the future.'
Write-Host 'Restart an existing backend, or run this script again with -StartBackend.'
