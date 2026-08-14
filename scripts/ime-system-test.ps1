[CmdletBinding()]
param(
    [string]$DllPath,
    [string]$WeaselServerPath,
    [switch]$ValidateOnly,
    [ValidateRange(50, 5000)]
    [int]$PollIntervalMs = 100
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Windows.Forms

if (-not ('AutoSwitchIme.NativeBridge' -as [type])) {
    Add-Type -Language CSharp -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;

namespace AutoSwitchIme
{
    public static class NativeBridge
    {
        [UnmanagedFunctionPointer(CallingConvention.Cdecl)]
        private delegate long GetConversionStatusDelegate();

        [UnmanagedFunctionPointer(CallingConvention.Cdecl)]
        private delegate int ReadIntDelegate();

        [UnmanagedFunctionPointer(CallingConvention.Cdecl)]
        private delegate int SetIntDelegate(int value);

        private static IntPtr library;
        private static GetConversionStatusDelegate getConversionStatus;
        private static SetIntDelegate setAsciiMode;
        private static ReadIntDelegate readCaps;
        private static SetIntDelegate setCaps;
        private static ReadIntDelegate isComposing;

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern IntPtr LoadLibraryW(string path);

        [DllImport("kernel32.dll", CharSet = CharSet.Ansi, SetLastError = true)]
        private static extern IntPtr GetProcAddress(IntPtr module, string name);

        [DllImport("user32.dll")]
        private static extern IntPtr GetForegroundWindow();

        public static void Load(string path)
        {
            if (library != IntPtr.Zero)
            {
                return;
            }

            library = LoadLibraryW(path);
            if (library == IntPtr.Zero)
            {
                throw new Win32Exception(Marshal.GetLastWin32Error(), "Cannot load " + path);
            }

            getConversionStatus = GetDelegate<GetConversionStatusDelegate>("ime_get_conversion_status");
            setAsciiMode = GetDelegate<SetIntDelegate>("ime_set_ascii_mode");
            readCaps = GetDelegate<ReadIntDelegate>("ime_caps_read");
            setCaps = GetDelegate<SetIntDelegate>("ime_caps_set");
            isComposing = GetDelegate<ReadIntDelegate>("ime_is_composing");
        }

        public static long GetConversionStatus() { return getConversionStatus(); }
        public static int SetAsciiMode(int value) { return setAsciiMode(value); }
        public static int ReadCaps() { return readCaps(); }
        public static int SetCaps(int value) { return setCaps(value); }
        public static int IsComposing() { return isComposing(); }
        public static bool IsForeground(IntPtr window) { return GetForegroundWindow() == window; }

        private static T GetDelegate<T>(string name) where T : class
        {
            IntPtr address = GetProcAddress(library, name);
            if (address == IntPtr.Zero)
            {
                throw new MissingMethodException("Missing native export: " + name);
            }

            return (T)(object)Marshal.GetDelegateForFunctionPointer(address, typeof(T));
        }
    }
}
'@
}

function Resolve-ImeSysDll {
    param([string]$ConfiguredPath)

    $candidates = @()
    if ($ConfiguredPath) {
        $candidates += $ConfiguredPath
    }
    $candidates += @(
        (Join-Path $PSScriptRoot '..\ime-sys\target\x86_64-pc-windows-gnu\release\ime_sys.dll'),
        (Join-Path $PSScriptRoot '..\intellij\src\main\resources\native\ime_sys.dll')
    )

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).ProviderPath
        }
    }

    throw 'ime_sys.dll not found. Build the Rust release target first or pass -DllPath.'
}

function Resolve-WeaselServer {
    param([string]$ConfiguredPath)

    if ($ConfiguredPath -and (Test-Path -LiteralPath $ConfiguredPath -PathType Leaf)) {
        return (Resolve-Path -LiteralPath $ConfiguredPath).ProviderPath
    }

    $registryPaths = @(
        'HKLM:\SOFTWARE\Rime\Weasel',
        'HKLM:\SOFTWARE\WOW6432Node\Rime\Weasel'
    )
    foreach ($registryPath in $registryPaths) {
        $root = Get-ItemPropertyValue -LiteralPath $registryPath -Name WeaselRoot -ErrorAction SilentlyContinue
        if (-not $root) {
            continue
        }
        $serverPath = Join-Path $root 'WeaselServer.exe'
        if (Test-Path -LiteralPath $serverPath -PathType Leaf) {
            return (Resolve-Path -LiteralPath $serverPath).ProviderPath
        }
    }

    $installRoots = @(
        'C:\Program Files (x86)\Rime',
        'C:\Program Files\Rime',
        'D:\Program Files\Rime'
    )
    foreach ($installRoot in $installRoots) {
        if (-not (Test-Path -LiteralPath $installRoot -PathType Container)) {
            continue
        }
        $server = Get-ChildItem -LiteralPath $installRoot -Directory -Filter 'weasel-*' |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'WeaselServer.exe' } |
            Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
            Select-Object -First 1
        if ($server) {
            return $server
        }
    }

    return $null
}

function Invoke-WeaselMode {
    param(
        [string]$Argument,
        [System.Windows.Forms.TextBox]$Editor
    )

    if (-not $script:ResolvedWeaselServer) {
        return
    }

    $expectedAsciiMode = $Argument -eq '/ascii'
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        $Editor.Focus()
        [System.Windows.Forms.Application]::DoEvents()
        $selectionStart = $Editor.SelectionStart
        $selectionLength = $Editor.SelectionLength
        [System.Windows.Forms.SendKeys]::SendWait('{LEFT}')
        $Editor.Select($selectionStart, $selectionLength)
        Start-Sleep -Milliseconds 250

        $process = Start-Process -FilePath $script:ResolvedWeaselServer -ArgumentList $Argument -WindowStyle Hidden -PassThru -Wait
        if ($process.ExitCode -ne 0) {
            throw "WeaselServer exited with code $($process.ExitCode)."
        }

        Start-Sleep -Milliseconds 150
        $packed = [AutoSwitchIme.NativeBridge]::GetConversionStatus()
        if ($packed -ge 0) {
            $conversionMode = [uint32]($packed -band [int64]4294967295)
            $isOpen = ($packed -band ([int64]1 -shl 32)) -ne 0
            $isAsciiMode = (-not $isOpen) -or (($conversionMode -band 0x01) -eq 0)
            if ($isAsciiMode -eq $expectedAsciiMode) {
                break
            }
        }
    }
    $Editor.Focus()
}

$resolvedDll = Resolve-ImeSysDll $DllPath
$script:ResolvedWeaselServer = Resolve-WeaselServer $WeaselServerPath
[AutoSwitchIme.NativeBridge]::Load($resolvedDll)

if ($ValidateOnly) {
    [pscustomobject]@{
        DllPath = $resolvedDll
        WeaselServerPath = $script:ResolvedWeaselServer
        ConversionStatus = [AutoSwitchIme.NativeBridge]::GetConversionStatus()
        SystemAsciiSwitch = 'available'
        CapsLock = [AutoSwitchIme.NativeBridge]::ReadCaps() -ne 0
        Composing = [AutoSwitchIme.NativeBridge]::IsComposing()
    }
    return
}

[System.Windows.Forms.Application]::EnableVisualStyles()

$form = [System.Windows.Forms.Form]@{
    Text = 'AutoSwitchIME Test'
    StartPosition = 'CenterScreen'
    ClientSize = [System.Drawing.Size]::new(760, 420)
    MinimumSize = [System.Drawing.Size]::new(560, 320)
}

$layout = [System.Windows.Forms.TableLayoutPanel]@{
    Dock = 'Fill'
    ColumnCount = 1
    RowCount = 3
    Padding = [System.Windows.Forms.Padding]::new(12)
}
$layout.RowStyles.Add([System.Windows.Forms.RowStyle]::new([System.Windows.Forms.SizeType]::Absolute, 46))
$layout.RowStyles.Add([System.Windows.Forms.RowStyle]::new([System.Windows.Forms.SizeType]::Absolute, 48))
$layout.RowStyles.Add([System.Windows.Forms.RowStyle]::new([System.Windows.Forms.SizeType]::Percent, 100))

$status = [System.Windows.Forms.Label]@{
    AutoEllipsis = $true
    Dock = 'Fill'
    Font = [System.Drawing.Font]::new('Segoe UI Semibold', 11)
    TextAlign = 'MiddleLeft'
}

$commands = [System.Windows.Forms.FlowLayoutPanel]@{
    Dock = 'Fill'
    FlowDirection = 'LeftToRight'
    WrapContents = $false
}

$englishButton = [System.Windows.Forms.Button]@{
    Text = 'ENGLISH'
    AutoSize = $true
    Height = 34
}
$chineseButton = [System.Windows.Forms.Button]@{
    Text = 'CHINESE'
    AutoSize = $true
    Height = 34
}
$capsButton = [System.Windows.Forms.Button]@{
    Text = 'CAPS'
    AutoSize = $true
    Height = 34
}
$commands.Controls.AddRange(@($englishButton, $chineseButton, $capsButton))

$editor = [System.Windows.Forms.TextBox]@{
    AcceptsReturn = $true
    AcceptsTab = $true
    BackColor = [System.Drawing.Color]::White
    BorderStyle = 'FixedSingle'
    Dock = 'Fill'
    Font = [System.Drawing.Font]::new('Microsoft YaHei UI', 18)
    Multiline = $true
    ScrollBars = 'Vertical'
}

$layout.Controls.Add($status, 0, 0)
$layout.Controls.Add($commands, 0, 1)
$layout.Controls.Add($editor, 0, 2)
$form.Controls.Add($layout)

$weaselAvailable = $null -ne $script:ResolvedWeaselServer
$englishButton.Enabled = $weaselAvailable
$chineseButton.Enabled = $weaselAvailable
$capsButton.Enabled = $weaselAvailable

$englishButton.Add_Click({
    [AutoSwitchIme.NativeBridge]::SetCaps(0)
    Invoke-WeaselMode '/ascii' $editor
})
$chineseButton.Add_Click({
    [AutoSwitchIme.NativeBridge]::SetCaps(0)
    Invoke-WeaselMode '/nascii' $editor
})
$capsButton.Add_Click({
    Invoke-WeaselMode '/ascii' $editor
    [AutoSwitchIme.NativeBridge]::SetCaps(1)
    $editor.Focus()
})

$timer = [System.Windows.Forms.Timer]@{
    Interval = $PollIntervalMs
}
$timer.Add_Tick({
    try {
        if (-not [AutoSwitchIme.NativeBridge]::IsForeground($form.Handle)) {
            $form.Text = 'AutoSwitchIME Test - INACTIVE'
            $status.Text = 'INACTIVE'
            return
        }

        $packed = [AutoSwitchIme.NativeBridge]::GetConversionStatus()
        $capsOn = [AutoSwitchIme.NativeBridge]::ReadCaps() -ne 0
        $composing = [AutoSwitchIme.NativeBridge]::IsComposing()

        if ($packed -lt 0) {
            $mode = 'UNAVAILABLE'
            $status.Text = "UNAVAILABLE | error=$packed | caps=$capsOn | composing=$composing"
            $editor.BackColor = [System.Drawing.Color]::FromArgb(245, 245, 245)
        } else {
            $conversionMode = [uint32]($packed -band [int64]4294967295)
            $isOpen = ($packed -band ([int64]1 -shl 32)) -ne 0
            $isAsciiMode = (-not $isOpen) -or (($conversionMode -band 0x01) -eq 0)
            $mode = if ($capsOn) { 'CAPS' } elseif ($isAsciiMode) { 'ENGLISH' } else { 'CHINESE' }
            $status.Text = '{0} | open={1} | conversion=0x{2:X8} | caps={3} | composing={4}' -f $mode, $isOpen, $conversionMode, $capsOn, $composing
            $editor.BackColor = switch ($mode) {
                'CAPS' { [System.Drawing.Color]::FromArgb(255, 245, 204) }
                'CHINESE' { [System.Drawing.Color]::FromArgb(225, 248, 234) }
                default { [System.Drawing.Color]::White }
            }
        }

        $form.Text = "AutoSwitchIME Test - $mode"
    } catch {
        $status.Text = "ERROR | $($_.Exception.Message)"
    }
})

$form.Add_Shown({
    $timer.Start()
    $editor.Focus()
})
$form.Add_FormClosed({
    $timer.Stop()
    $timer.Dispose()
})

[void]$form.ShowDialog()
