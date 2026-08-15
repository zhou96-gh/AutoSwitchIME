[CmdletBinding()]
param(
    [string]$Schema,
    [string]$RimeDir = (Join-Path $env:APPDATA 'Rime'),
    [switch]$Uninstall,
    [switch]$SkipDeploy
)

$ErrorActionPreference = 'Stop'
$ProcessorLine = '  "engine/processors/@before 0": lua_processor@*autoswitchime_bridge'
$LuaDirectory = Join-Path $RimeDir 'lua'
$LuaTarget = Join-Path $LuaDirectory 'autoswitchime_bridge.lua'
$DllTarget = Join-Path $LuaDirectory 'autoswitchime_ipc.dll'
$SourceLua = Join-Path $PSScriptRoot 'autoswitchime_bridge.lua'
$SourceDll = Join-Path $PSScriptRoot 'autoswitchime_ipc.dll'

function Get-SchemaCandidates {
    $names = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::OrdinalIgnoreCase
    )
    Get-ChildItem -LiteralPath $RimeDir -Filter '*.schema.yaml' -File -ErrorAction SilentlyContinue |
        ForEach-Object { [void]$names.Add($_.Name -replace '\.schema\.yaml$', '') }
    Get-ChildItem -LiteralPath $RimeDir -Filter '*.custom.yaml' -File -ErrorAction SilentlyContinue |
        ForEach-Object {
            $name = $_.Name -replace '\.custom\.yaml$', ''
            if ($name -notin @('default', 'weasel', 'installation')) {
                [void]$names.Add($name)
            }
        }
    return @($names | Sort-Object)
}

function Select-Schema {
    if ($Schema) {
        return $Schema -replace '\.custom\.yaml$', ''
    }

    $candidates = @(Get-SchemaCandidates)
    if ($candidates.Count -eq 0) {
        return Read-Host 'Enter the schema name (for example: rime_ice)'
    }
    if ($candidates.Count -eq 1) {
        return $candidates[0]
    }

    Write-Host 'Select the Rime schema that should load AutoSwitchIME:'
    for ($index = 0; $index -lt $candidates.Count; $index++) {
        Write-Host "  [$($index + 1)] $($candidates[$index])"
    }
    $selection = Read-Host "Enter a number [1-$($candidates.Count)]"
    $selectedIndex = 0
    if (-not [int]::TryParse($selection, [ref]$selectedIndex) -or
        $selectedIndex -lt 1 -or $selectedIndex -gt $candidates.Count) {
        throw "Invalid schema selection: $selection"
    }
    return $candidates[$selectedIndex - 1]
}

function Backup-File([string]$Path) {
    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmssfff'
    $backupPath = "$Path.autoswitchime.bak-$timestamp"
    Copy-Item -LiteralPath $Path -Destination $backupPath
    Write-Host "Backup created: $backupPath"
}

function Set-Processor([string]$Path, [bool]$Enabled) {
    $newLine = if ([IO.File]::Exists($Path) -and
        [IO.File]::ReadAllText($Path).Contains("`r`n")) { "`r`n" } else { "`n" }
    $content = if ([IO.File]::Exists($Path)) { [IO.File]::ReadAllText($Path) } else { '' }
    $updated = [regex]::Replace(
        $content,
        '(?m)^.*lua_processor@\*(?:autoswitchime_bridge|rimevim_bridge).*(?:\r?\n|$)',
        ''
    )

    if ($Enabled) {
        if ($updated -match '(?m)^patch:\s*(?:\r?\n|$)') {
            $patchPattern = [regex]::new('(?m)^patch:\s*(?:\r?\n|$)')
            $updated = $patchPattern.Replace(
                $updated,
                "patch:$newLine$ProcessorLine$newLine",
                1
            )
        } else {
            $separator = if ($updated.Length -eq 0 -or $updated.EndsWith($newLine)) { '' } else { $newLine }
            $updated = "$updated$separator${newLine}patch:$newLine$ProcessorLine$newLine"
        }
    }

    if ($updated -eq $content) {
        return $false
    }
    if ([IO.File]::Exists($Path)) {
        Backup-File $Path
    }
    [IO.File]::WriteAllText($Path, $updated, [Text.UTF8Encoding]::new($false))
    return $true
}

function Find-WeaselExecutable([string]$ExecutableName) {
    foreach ($registryPath in @(
        'HKLM:\SOFTWARE\Rime\Weasel',
        'HKLM:\SOFTWARE\WOW6432Node\Rime\Weasel'
    )) {
        try {
            $root = (Get-ItemProperty -LiteralPath $registryPath -Name WeaselRoot).WeaselRoot
            $candidate = Join-Path $root $ExecutableName
            if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                return $candidate
            }
            if (Test-Path -LiteralPath $root -PathType Container) {
                $candidate = Get-ChildItem -LiteralPath $root -Filter $ExecutableName -File -Recurse |
                    Sort-Object FullName -Descending |
                    Select-Object -First 1
                if ($candidate) {
                    return $candidate.FullName
                }
            }
        } catch {
        }
    }

    foreach ($baseDirectory in @(
        (Join-Path $env:ProgramFiles 'Rime'),
        (Join-Path ${env:ProgramFiles(x86)} 'Rime')
    )) {
        if (-not $baseDirectory -or -not (Test-Path -LiteralPath $baseDirectory)) {
            continue
        }
        $candidate = Get-ChildItem -LiteralPath $baseDirectory -Filter $ExecutableName -File -Recurse |
            Sort-Object FullName -Descending |
            Select-Object -First 1
        if ($candidate) {
            return $candidate.FullName
        }
    }
    return $null
}

function Invoke-RimeDeploy {
    if ($SkipDeploy) {
        Write-Host 'Rime deployment skipped. Redeploy from the Weasel tray menu.'
        return
    }
    $weaselDeployer = Find-WeaselExecutable 'WeaselDeployer.exe'
    if (-not $weaselDeployer) {
        Write-Warning 'WeaselDeployer.exe was not found. Redeploy from the Weasel tray menu.'
        return
    }
    $process = Start-Process -FilePath $weaselDeployer -ArgumentList '/deploy' -WindowStyle Hidden -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        throw "Weasel redeployment failed with exit code $($process.ExitCode)."
    }
    Write-Host 'Weasel redeployment completed.'
}

function Stop-WeaselServer {
    $weaselServer = Find-WeaselExecutable 'WeaselServer.exe'
    if (-not $weaselServer) {
        return
    }

    $process = Start-Process -FilePath $weaselServer -ArgumentList '/quit' -WindowStyle Hidden -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        throw "Weasel shutdown failed with exit code $($process.ExitCode)."
    }

    $deadline = [DateTime]::UtcNow.AddSeconds(5)
    while (Get-Process -Name 'WeaselServer' -ErrorAction SilentlyContinue) {
        if ([DateTime]::UtcNow -ge $deadline) {
            throw 'WeaselServer.exe did not exit before the deployment timeout.'
        }
        Start-Sleep -Milliseconds 100
    }
    Write-Host 'Weasel service stopped for bridge update.'
}

if (-not (Test-Path -LiteralPath $RimeDir -PathType Container)) {
    throw "Rime user directory does not exist: $RimeDir"
}

if ($Uninstall) {
    $schemaFiles = if ($Schema) {
        @(Join-Path $RimeDir "$(Select-Schema).custom.yaml")
    } else {
        @(Get-ChildItem -LiteralPath $RimeDir -Filter '*.custom.yaml' -File |
            Select-Object -ExpandProperty FullName)
    }
    foreach ($schemaFile in $schemaFiles) {
        if (Test-Path -LiteralPath $schemaFile) {
            [void](Set-Processor $schemaFile $false)
        }
    }
    Remove-Item -LiteralPath $LuaTarget -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $DllTarget -Force -ErrorAction SilentlyContinue
    Invoke-RimeDeploy
    Write-Host 'AutoSwitchIME Rime bridge uninstalled.'
    exit 0
}

foreach ($source in @($SourceLua, $SourceDll)) {
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Package file is missing: $source"
    }
}

$selectedSchema = Select-Schema
if (-not $selectedSchema) {
    throw 'Schema name cannot be empty.'
}
$schemaPath = Join-Path $RimeDir "$selectedSchema.custom.yaml"

New-Item -ItemType Directory -Path $LuaDirectory -Force | Out-Null
Stop-WeaselServer
Copy-Item -LiteralPath $SourceLua -Destination $LuaTarget -Force
Copy-Item -LiteralPath $SourceDll -Destination $DllTarget -Force

$legacyLua = Join-Path $LuaDirectory 'rimevim_bridge.lua'
if (Test-Path -LiteralPath $legacyLua -PathType Leaf) {
    $legacyBackup = "$legacyLua.autoswitchime-legacy.bak-$(Get-Date -Format 'yyyyMMdd-HHmmssfff')"
    Move-Item -LiteralPath $legacyLua -Destination $legacyBackup
    Write-Host "Legacy script backed up: $legacyBackup"
}

[void](Set-Processor $schemaPath $true)
Invoke-RimeDeploy
Write-Host "Installation completed for schema: $selectedSchema"
Write-Host 'Type in the focused editor, then run diagnose.cmd to inspect rime_state.'
