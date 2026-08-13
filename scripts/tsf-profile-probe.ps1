<#
.SYNOPSIS
Enumerates Windows TSF keyboard profiles and probes process-scoped activation.

.DESCRIPTION
The default mode is read-only and lists enabled keyboard profiles. Activation uses
TF_IPPMF_FORPROCESS and verifies the result with GetActiveProfile. It never uses
TF_IPPMF_FORSESSION or changes the enabled-profile registry configuration.

The TestRime mode observes only the TSF input context owned by its WinForms UI
thread. Its compartment values cannot be used to infer another process or editor
thread's Rime ascii_mode.

.EXAMPLE
pwsh -File .\scripts\tsf-profile-probe.ps1

.EXAMPLE
pwsh -File .\scripts\tsf-profile-probe.ps1 -All -Json

.EXAMPLE
pwsh -File .\scripts\tsf-profile-probe.ps1 -Watch -WatchSeconds 10

.EXAMPLE
pwsh -Sta -File .\scripts\tsf-profile-probe.ps1 -TestRime

.EXAMPLE
pwsh -File .\scripts\tsf-profile-probe.ps1 -ActivateClsid a3f4cded-b1e9-41ee-9ca6-7b4d0de6cb0a -ActivateProfile 3d02cab6-2b8e-4781-ba20-1c9267529467 -ActivateLanguageId 2052 -Interactive
#>
[CmdletBinding(DefaultParameterSetName = 'List')]
param(
    [Parameter(Mandatory, ParameterSetName = 'Activate')]
    [Guid]$ActivateClsid,

    [Parameter(Mandatory, ParameterSetName = 'Activate')]
    [Guid]$ActivateProfile,

    [Parameter(Mandatory, ParameterSetName = 'Activate')]
    [UInt16]$ActivateLanguageId,

    [Parameter(ParameterSetName = 'Activate')]
    [switch]$Interactive,

    [Parameter(Mandatory, ParameterSetName = 'TestRime')]
    [switch]$TestRime,

    [Parameter(ParameterSetName = 'List')]
    [switch]$Watch,

    [ValidateRange(50, 5000)]
    [int]$PollIntervalMs = 200,

    [Parameter(ParameterSetName = 'List')]
    [ValidateRange(0, 86400)]
    [int]$WatchSeconds = 0,

    [switch]$All,

    [switch]$Json
)

$ErrorActionPreference = 'Stop'

if (-not ('AutoSwitchIme.TsfProbe' -as [type])) {
    Add-Type -Language CSharp -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Globalization;
using System.Runtime.InteropServices;
using Microsoft.Win32;

namespace AutoSwitchIme
{
    internal static class NativeMethods
    {
        [DllImport("user32.dll")]
        internal static extern short GetKeyState(int virtualKey);
    }

    [StructLayout(LayoutKind.Sequential)]
    internal struct TfInputProcessorProfile
    {
        internal uint ProfileType;
        internal ushort LanguageId;
        internal Guid Clsid;
        internal Guid ProfileGuid;
        internal Guid CategoryGuid;
        internal IntPtr SubstituteKeyboardLayout;
        internal uint Capabilities;
        internal IntPtr KeyboardLayout;
        internal uint Flags;
    }

    [ComImport]
    [Guid("71C6E74C-0F28-11D8-A82A-00065B84435C")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface ITfInputProcessorProfileMgr
    {
        [PreserveSig]
        int ActivateProfile(
            uint profileType,
            ushort languageId,
            ref Guid clsid,
            ref Guid profileGuid,
            IntPtr keyboardLayout,
            uint flags);

        [PreserveSig]
        int DeactivateProfile(
            uint profileType,
            ushort languageId,
            ref Guid clsid,
            ref Guid profileGuid,
            IntPtr keyboardLayout,
            uint flags);

        [PreserveSig]
        int GetProfile(
            uint profileType,
            ushort languageId,
            ref Guid clsid,
            ref Guid profileGuid,
            IntPtr keyboardLayout,
            out TfInputProcessorProfile profile);

        [PreserveSig]
        int EnumProfiles(ushort languageId, out IEnumTfInputProcessorProfiles profiles);

        [PreserveSig]
        int ReleaseInputProcessor(ref Guid clsid, uint flags);

        [PreserveSig]
        int RegisterProfile(
            ref Guid clsid,
            ushort languageId,
            ref Guid profileGuid,
            IntPtr description,
            uint descriptionLength,
            IntPtr iconFile,
            uint iconFileLength,
            uint iconIndex,
            IntPtr substituteKeyboardLayout,
            uint preferredLayout,
            [MarshalAs(UnmanagedType.Bool)] bool enabledByDefault,
            uint flags);

        [PreserveSig]
        int UnregisterProfile(ref Guid clsid, ushort languageId, ref Guid profileGuid, uint flags);

        [PreserveSig]
        int GetActiveProfile(ref Guid categoryGuid, out TfInputProcessorProfile profile);
    }

    [ComImport]
    [Guid("71C6E74D-0F28-11D8-A82A-00065B84435C")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IEnumTfInputProcessorProfiles
    {
        [PreserveSig]
        int Clone(out IEnumTfInputProcessorProfiles profiles);

        [PreserveSig]
        int Next(uint count, out TfInputProcessorProfile profile, out uint fetched);

        [PreserveSig]
        int Reset();

        [PreserveSig]
        int Skip(uint count);
    }

    [ComImport]
    [Guid("AA80E801-2021-11D2-93E0-0060B067B86E")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface ITfThreadMgr
    {
        [PreserveSig]
        int Activate(out uint clientId);

        [PreserveSig]
        int Deactivate();

        [PreserveSig]
        int CreateDocumentMgr(out IntPtr documentManager);

        [PreserveSig]
        int EnumDocumentMgrs(out IntPtr documentManagers);

        [PreserveSig]
        int GetFocus(out IntPtr documentManager);

        [PreserveSig]
        int SetFocus(IntPtr documentManager);

        [PreserveSig]
        int AssociateFocus(IntPtr window, IntPtr newDocumentManager, out IntPtr previousDocumentManager);

        [PreserveSig]
        int IsThreadFocus([MarshalAs(UnmanagedType.Bool)] out bool threadHasFocus);

        [PreserveSig]
        int GetFunctionProvider(ref Guid clsid, out IntPtr functionProvider);

        [PreserveSig]
        int EnumFunctionProviders(out IntPtr functionProviders);

        [PreserveSig]
        int GetGlobalCompartment(out ITfCompartmentMgr compartmentManager);
    }

    [ComImport]
    [Guid("7DCF57AC-18AD-438B-824D-979BFFB74B7C")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface ITfCompartmentMgr
    {
        [PreserveSig]
        int GetCompartment(ref Guid compartmentGuid, out ITfCompartment compartment);

        [PreserveSig]
        int ClearCompartment(uint clientId, ref Guid compartmentGuid);

        [PreserveSig]
        int EnumCompartments(out IntPtr compartments);
    }

    [ComImport]
    [Guid("BB08F7A9-607A-4384-8623-056892B64371")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface ITfCompartment
    {
        [PreserveSig]
        int SetValue(uint clientId, [MarshalAs(UnmanagedType.Struct)] ref object value);

        [PreserveSig]
        int GetValue([MarshalAs(UnmanagedType.Struct)] out object value);
    }

    public sealed class TsfProfile
    {
        internal TsfProfile(TfInputProcessorProfile profile)
        {
            ProfileType = profile.ProfileType;
            LanguageId = profile.LanguageId;
            Language = GetLanguageName(profile.LanguageId);
            Clsid = profile.Clsid;
            ProfileGuid = profile.ProfileGuid;
            CategoryGuid = profile.CategoryGuid;
            KeyboardLayout = FormatHandle(profile.KeyboardLayout);
            Capabilities = profile.Capabilities;
            Flags = profile.Flags;
            Active = (profile.Flags & 0x00000001) != 0;
            Enabled = (profile.Flags & 0x00000002) != 0;
            Name = GetProfileName(profile.Clsid, profile.ProfileType, profile.KeyboardLayout);
        }

        public uint ProfileType { get; private set; }
        public ushort LanguageId { get; private set; }
        public string Language { get; private set; }
        public string Name { get; private set; }
        public Guid Clsid { get; private set; }
        public Guid ProfileGuid { get; private set; }
        public Guid CategoryGuid { get; private set; }
        public string KeyboardLayout { get; private set; }
        public uint Capabilities { get; private set; }
        public uint Flags { get; private set; }
        public bool Active { get; private set; }
        public bool Enabled { get; private set; }

        internal bool HasSameIdentity(TsfProfile other)
        {
            return ProfileType == other.ProfileType
                && LanguageId == other.LanguageId
                && Clsid == other.Clsid
                && ProfileGuid == other.ProfileGuid
                && KeyboardLayout == other.KeyboardLayout;
        }

        private static string GetLanguageName(ushort languageId)
        {
            if (languageId == 0)
            {
                return "Language neutral";
            }

            try
            {
                return CultureInfo.GetCultureInfo(languageId).DisplayName;
            }
            catch (CultureNotFoundException)
            {
                return "Unknown";
            }
        }

        private static string GetProfileName(Guid clsid, uint profileType, IntPtr keyboardLayout)
        {
            if (profileType == 0x0002)
            {
                return "Keyboard layout " + FormatHandle(keyboardLayout);
            }

            using (RegistryKey key = Registry.ClassesRoot.OpenSubKey("CLSID\\" + clsid.ToString("B")))
            {
                object value = key == null ? null : key.GetValue(null);
                return value == null ? "Unregistered TSF profile" : value.ToString();
            }
        }

        private static string FormatHandle(IntPtr value)
        {
            return "0x" + value.ToInt64().ToString("X16");
        }
    }

    public sealed class ActivationResult
    {
        public int HResult { get; internal set; }
        public bool ApiSucceeded { get; internal set; }
        public bool Verified { get; internal set; }
        public TsfProfile Requested { get; internal set; }
        public TsfProfile Active { get; internal set; }
    }

    public sealed class CompartmentSnapshot
    {
        public object ThreadOpenClose { get; internal set; }
        public object ThreadConversion { get; internal set; }
        public object GlobalOpenClose { get; internal set; }
        public object GlobalConversion { get; internal set; }
        public bool CapsLock { get; internal set; }

        public string Identity
        {
            get
            {
                return Format(ThreadOpenClose) + "|"
                    + Format(ThreadConversion) + "|"
                    + Format(GlobalOpenClose) + "|"
                    + Format(GlobalConversion) + "|"
                    + CapsLock;
            }
        }

        private static string Format(object value)
        {
            return value == null ? "empty" : Convert.ToString(value, CultureInfo.InvariantCulture);
        }
    }

    public sealed class CompartmentProbe : IDisposable
    {
        private static readonly Guid OpenClose =
            new Guid("58273AAD-01BB-4164-95C6-755BA0B5162D");

        private static readonly Guid Conversion =
            new Guid("CCF05DD8-4A87-11D7-A6E2-00065B84435C");

        private ITfThreadMgr threadManager;
        private ITfCompartmentMgr threadCompartments;
        private ITfCompartmentMgr globalCompartments;
        private bool activated;

        internal CompartmentProbe(object instance)
        {
            threadManager = (ITfThreadMgr)instance;

            uint clientId;
            int hr = threadManager.Activate(out clientId);
            if (hr < 0)
            {
                Marshal.ThrowExceptionForHR(hr, new IntPtr(-1));
            }

            activated = true;
            threadCompartments = (ITfCompartmentMgr)instance;

            hr = threadManager.GetGlobalCompartment(out globalCompartments);
            if (hr < 0)
            {
                Marshal.ThrowExceptionForHR(hr, new IntPtr(-1));
            }
        }

        public CompartmentSnapshot Read()
        {
            return new CompartmentSnapshot
            {
                ThreadOpenClose = ReadValue(threadCompartments, OpenClose),
                ThreadConversion = ReadValue(threadCompartments, Conversion),
                GlobalOpenClose = ReadValue(globalCompartments, OpenClose),
                GlobalConversion = ReadValue(globalCompartments, Conversion),
                CapsLock = (NativeMethods.GetKeyState(0x14) & 0x0001) != 0
            };
        }

        public void Dispose()
        {
            if (globalCompartments != null)
            {
                Marshal.FinalReleaseComObject(globalCompartments);
                globalCompartments = null;
            }

            if (threadManager != null)
            {
                if (activated)
                {
                    threadManager.Deactivate();
                    activated = false;
                }

                Marshal.FinalReleaseComObject(threadManager);
                threadManager = null;
                threadCompartments = null;
            }
        }

        private static object ReadValue(ITfCompartmentMgr manager, Guid compartmentGuid)
        {
            ITfCompartment compartment;
            int hr = manager.GetCompartment(ref compartmentGuid, out compartment);
            if (hr < 0 || compartment == null)
            {
                return null;
            }

            try
            {
                object value;
                hr = compartment.GetValue(out value);
                return hr == 0 ? value : null;
            }
            finally
            {
                Marshal.FinalReleaseComObject(compartment);
            }
        }
    }

    public static class TsfProbe
    {
        private const uint InputProcessorProfile = 0x0001;
        private const uint ForProcess = 0x10000000;
        private const uint DontCareCurrentInputLanguage = 0x00000004;

        private static readonly Guid InputProcessorProfilesClsid =
            new Guid("33C53A50-F456-4884-B049-85FD643ECFED");

        private static readonly Guid KeyboardCategory =
            new Guid("34745C63-B2F0-4784-8B67-5E12C8701A31");

        private static readonly Guid ThreadManagerClsid =
            new Guid("529A9E6B-6587-4F23-AB9E-9C7D683E3C50");

        public static CompartmentProbe CreateCompartmentProbe()
        {
            object instance = Activator.CreateInstance(Type.GetTypeFromCLSID(ThreadManagerClsid, true));
            return new CompartmentProbe(instance);
        }

        public static TsfProfile[] Enumerate()
        {
            object instance = Activator.CreateInstance(Type.GetTypeFromCLSID(InputProcessorProfilesClsid, true));
            ITfInputProcessorProfileMgr manager = (ITfInputProcessorProfileMgr)instance;

            try
            {
                IEnumTfInputProcessorProfiles enumerator;
                ThrowIfFailed(manager.EnumProfiles(0, out enumerator), "EnumProfiles");

                try
                {
                    List<TsfProfile> result = new List<TsfProfile>();
                    while (true)
                    {
                        TfInputProcessorProfile profile;
                        uint fetched;
                        int hr = enumerator.Next(1, out profile, out fetched);
                        if (hr != 0 || fetched == 0)
                        {
                            break;
                        }

                        result.Add(new TsfProfile(profile));
                    }

                    return result.ToArray();
                }
                finally
                {
                    Marshal.FinalReleaseComObject(enumerator);
                }
            }
            finally
            {
                Marshal.FinalReleaseComObject(manager);
            }
        }

        public static TsfProfile GetActive()
        {
            object instance = Activator.CreateInstance(Type.GetTypeFromCLSID(InputProcessorProfilesClsid, true));
            ITfInputProcessorProfileMgr manager = (ITfInputProcessorProfileMgr)instance;

            try
            {
                TfInputProcessorProfile profile;
                Guid keyboardCategory = KeyboardCategory;
                int hr = manager.GetActiveProfile(ref keyboardCategory, out profile);
                return hr == 0 ? new TsfProfile(profile) : null;
            }
            finally
            {
                Marshal.FinalReleaseComObject(manager);
            }
        }

        public static ActivationResult Activate(Guid clsid, Guid profileGuid, ushort languageId)
        {
            TsfProfile requested = null;
            foreach (TsfProfile profile in Enumerate())
            {
                if (profile.ProfileType == InputProcessorProfile
                    && profile.Clsid == clsid
                    && profile.ProfileGuid == profileGuid
                    && profile.LanguageId == languageId
                    && profile.Enabled)
                {
                    requested = profile;
                    break;
                }
            }

            if (requested == null)
            {
                throw new InvalidOperationException("The requested enabled TSF input processor profile was not found.");
            }

            object instance = Activator.CreateInstance(Type.GetTypeFromCLSID(InputProcessorProfilesClsid, true));
            ITfInputProcessorProfileMgr manager = (ITfInputProcessorProfileMgr)instance;

            try
            {
                Guid requestedClsid = requested.Clsid;
                Guid requestedProfile = requested.ProfileGuid;
                int hr = manager.ActivateProfile(
                    requested.ProfileType,
                    requested.LanguageId,
                    ref requestedClsid,
                    ref requestedProfile,
                    IntPtr.Zero,
                    ForProcess | DontCareCurrentInputLanguage);

                TfInputProcessorProfile activeProfile;
                Guid keyboardCategory = KeyboardCategory;
                int activeHr = manager.GetActiveProfile(ref keyboardCategory, out activeProfile);
                TsfProfile active = activeHr == 0 ? new TsfProfile(activeProfile) : null;

                return new ActivationResult
                {
                    HResult = hr,
                    ApiSucceeded = hr >= 0,
                    Requested = requested,
                    Active = active,
                    Verified = active != null && requested.HasSameIdentity(active)
                };
            }
            finally
            {
                Marshal.FinalReleaseComObject(manager);
            }
        }

        private static void ThrowIfFailed(int hr, string operation)
        {
            if (hr < 0)
            {
                Marshal.ThrowExceptionForHR(hr, new IntPtr(-1));
            }

            if (hr != 0)
            {
                throw new InvalidOperationException(operation + " returned HRESULT 0x" + hr.ToString("X8"));
            }
        }
    }
}
'@
}

function Get-ProfileIdentity {
    param([AutoSwitchIme.TsfProfile]$Profile)

    if ($null -eq $Profile) {
        return 'none'
    }

    return '{0}|{1}|{2}|{3}|{4}' -f $Profile.ProfileType,
        $Profile.LanguageId,
        $Profile.Clsid,
        $Profile.ProfileGuid,
        $Profile.KeyboardLayout
}

function New-ProfileEvent {
    param(
        [AutoSwitchIme.TsfProfile]$Profile,
        [string]$Event
    )

    [pscustomobject]@{
        Timestamp = [DateTimeOffset]::Now.ToString('yyyy-MM-dd HH:mm:ss.fff zzz')
        Event = $Event
        Name = if ($null -eq $Profile) { $null } else { $Profile.Name }
        LanguageId = if ($null -eq $Profile) { $null } else { $Profile.LanguageId }
        Language = if ($null -eq $Profile) { $null } else { $Profile.Language }
        Clsid = if ($null -eq $Profile) { $null } else { $Profile.Clsid }
        ProfileGuid = if ($null -eq $Profile) { $null } else { $Profile.ProfileGuid }
        KeyboardLayout = if ($null -eq $Profile) { $null } else { $Profile.KeyboardLayout }
    }
}

function Format-ProfileEvent {
    param([psobject]$ProfileEvent)

    if ($null -eq $ProfileEvent.Name) {
        return '[{0}] {1}: no active keyboard profile' -f $ProfileEvent.Timestamp, $ProfileEvent.Event
    }

    return ('[{0}] {1}: {2} | LANGID={3} | CLSID={4} | Profile={5}' -f
        $ProfileEvent.Timestamp,
        $ProfileEvent.Event,
        $ProfileEvent.Name,
        $ProfileEvent.LanguageId,
        $ProfileEvent.Clsid,
        $ProfileEvent.ProfileGuid)
}

function Format-CompartmentSnapshot {
    param([AutoSwitchIme.CompartmentSnapshot]$Snapshot)

    return 'TSF mode: thread(open={0}, conversion={1}) | global(open={2}, conversion={3}) | caps={4}' -f
        (Format-CompartmentValue $Snapshot.ThreadOpenClose),
        (Format-CompartmentValue $Snapshot.ThreadConversion),
        (Format-CompartmentValue $Snapshot.GlobalOpenClose),
        (Format-CompartmentValue $Snapshot.GlobalConversion),
        $(if ($Snapshot.CapsLock) { 'on' } else { 'off' })
}

function Format-CompartmentValue {
    param([object]$Value)

    if ($null -eq $Value) {
        return 'empty'
    }

    if ($Value -is [int]) {
        $unsignedValue = [BitConverter]::ToUInt32([BitConverter]::GetBytes($Value), 0)
        return '{0} (0x{1:X8})' -f $Value, $unsignedValue
    }

    return $Value.ToString()
}

if ($TestRime) {
    $activeProfile = [AutoSwitchIme.TsfProbe]::GetActive()
    $weaselProfiles = @([AutoSwitchIme.TsfProbe]::Enumerate() | Where-Object {
        $_.ProfileType -eq 1 -and $_.Enabled -and $_.Name -eq 'Weasel'
    })
    $targetProfile = $weaselProfiles | Where-Object {
        $null -ne $activeProfile -and
        $_.LanguageId -eq $activeProfile.LanguageId -and
        $_.Clsid -eq $activeProfile.Clsid -and
        $_.ProfileGuid -eq $activeProfile.ProfileGuid
    } | Select-Object -First 1

    if ($null -eq $targetProfile) {
        $targetProfile = $weaselProfiles | Sort-Object {
            if ($_.LanguageId -eq 2052) { 0 } else { 1 }
        } | Select-Object -First 1
    }

    if ($null -eq $targetProfile) {
        throw 'No enabled Weasel TSF profile was found.'
    }

    $ActivateClsid = $targetProfile.Clsid
    $ActivateProfile = $targetProfile.ProfileGuid
    $ActivateLanguageId = $targetProfile.LanguageId
    $Interactive = $true
}

if ($PSCmdlet.ParameterSetName -in @('Activate', 'TestRime')) {
    $result = [AutoSwitchIme.TsfProbe]::Activate($ActivateClsid, $ActivateProfile, $ActivateLanguageId)

    if ($Json) {
        $result | ConvertTo-Json -Depth 4
    } else {
        $result
    }

    if (-not $result.Verified) {
        Write-Error ('ActivateProfile returned 0x{0:X8}, but GetActiveProfile did not verify the requested profile.' -f $result.HResult)
    }

    if ($Interactive) {
        Add-Type -AssemblyName System.Drawing
        Add-Type -AssemblyName System.Windows.Forms

        [System.Windows.Forms.Application]::EnableVisualStyles()

        $form = [System.Windows.Forms.Form]@{
            Text = 'AutoSwitchIME TSF probe'
            StartPosition = 'CenterScreen'
            ClientSize = [System.Drawing.Size]::new(640, 360)
            TopMost = $true
        }
        $status = [System.Windows.Forms.Label]@{
            AutoSize = $false
            Dock = 'Top'
            Height = 82
            Padding = [System.Windows.Forms.Padding]::new(12)
            Text = ('Active: {0} | LANGID: {1} | Verified: {2}' -f $result.Active.Name, $result.Active.LanguageId, $result.Verified)
        }
        $log = [System.Windows.Forms.TextBox]@{
            BackColor = [System.Drawing.Color]::FromArgb(245, 245, 245)
            Dock = 'Bottom'
            Font = [System.Drawing.Font]::new('Consolas', 9)
            Height = 120
            Multiline = $true
            ReadOnly = $true
            ScrollBars = 'Vertical'
        }
        $editor = [System.Windows.Forms.TextBox]@{
            AcceptsReturn = $true
            AcceptsTab = $true
            Dock = 'Fill'
            Font = [System.Drawing.Font]::new('Microsoft YaHei UI', 16)
            Multiline = $true
            ScrollBars = 'Vertical'
        }

        $form.Controls.Add($editor)
        $form.Controls.Add($log)
        $form.Controls.Add($status)

        $compartmentProbe = [AutoSwitchIme.TsfProbe]::CreateCompartmentProbe()
        $initialSnapshot = $compartmentProbe.Read()
        $monitorState = @{
            Identity = Get-ProfileIdentity $result.Active
            CompartmentIdentity = $initialSnapshot.Identity
        }
        $initialEvent = New-ProfileEvent -Profile $result.Active -Event 'initial'
        $log.AppendText((Format-ProfileEvent $initialEvent) + [Environment]::NewLine)
        $log.AppendText((Format-CompartmentSnapshot $initialSnapshot) + [Environment]::NewLine)
        $status.Text += [Environment]::NewLine + (Format-CompartmentSnapshot $initialSnapshot)
        $status.Text += [Environment]::NewLine + ('Same-thread probe only; polling every {0} ms.' -f $PollIntervalMs)

        $timer = [System.Windows.Forms.Timer]@{
            Interval = $PollIntervalMs
        }
        $timer.Add_Tick({
            try {
                $currentProfile = [AutoSwitchIme.TsfProbe]::GetActive()
                $currentIdentity = Get-ProfileIdentity $currentProfile
                if ($currentIdentity -eq $monitorState.Identity) {
                    $profileChanged = $false
                } else {
                    $profileChanged = $true
                    $monitorState.Identity = $currentIdentity
                    $profileEvent = New-ProfileEvent -Profile $currentProfile -Event 'profile changed'
                    $log.AppendText((Format-ProfileEvent $profileEvent) + [Environment]::NewLine)
                }

                $snapshot = $compartmentProbe.Read()
                $modeChanged = $snapshot.Identity -ne $monitorState.CompartmentIdentity
                if ($modeChanged) {
                    $monitorState.CompartmentIdentity = $snapshot.Identity
                    $log.AppendText(('[{0}] mode changed: {1}' -f [DateTimeOffset]::Now.ToString('yyyy-MM-dd HH:mm:ss.fff zzz'), (Format-CompartmentSnapshot $snapshot)) + [Environment]::NewLine)
                }

                $profileName = if ($null -eq $currentProfile) { 'none' } else { $currentProfile.Name }
                $languageId = if ($null -eq $currentProfile) { '-' } else { $currentProfile.LanguageId }
                $status.Text = 'Active: {0} | LANGID: {1} | Initial activation verified: {2}' -f $profileName, $languageId, $result.Verified
                $status.Text += [Environment]::NewLine + (Format-CompartmentSnapshot $snapshot)
                $status.Text += [Environment]::NewLine + ('Same-thread probe only; polling every {0} ms.' -f $PollIntervalMs)

                if ($profileChanged -or $modeChanged) {
                    $log.SelectionStart = $log.TextLength
                    $log.ScrollToCaret()
                }
            } catch {
                $log.AppendText(('[{0}] error: {1}' -f [DateTimeOffset]::Now.ToString('HH:mm:ss.fff'), $_.Exception.Message) + [Environment]::NewLine)
            }
        })
        $form.Add_Shown({
            $form.TopMost = $false
            $timer.Start()
            $editor.Focus()
        })
        $form.Add_FormClosed({
            $timer.Stop()
            $timer.Dispose()
            $compartmentProbe.Dispose()
        })
        [void]$form.ShowDialog()
    }

    return
}

$profiles = [AutoSwitchIme.TsfProbe]::Enumerate()
$active = [AutoSwitchIme.TsfProbe]::GetActive()

if ($Watch) {
    if (-not $Json) {
        Write-Host ('Watching this process TSF keyboard profile every {0} ms. Press Ctrl+C to stop.' -f $PollIntervalMs)
        Write-Host 'This does not observe keystrokes or Rime ascii_mode.'
    }

    $lastIdentity = $null
    $watchDeadline = if ($WatchSeconds -eq 0) { $null } else { [DateTimeOffset]::Now.AddSeconds($WatchSeconds) }
    while ($null -eq $watchDeadline -or [DateTimeOffset]::Now -lt $watchDeadline) {
        $currentProfile = [AutoSwitchIme.TsfProbe]::GetActive()
        $currentIdentity = Get-ProfileIdentity $currentProfile
        if ($currentIdentity -ne $lastIdentity) {
            $eventName = if ($null -eq $lastIdentity) { 'initial' } else { 'changed' }
            $profileEvent = New-ProfileEvent -Profile $currentProfile -Event $eventName

            if ($Json) {
                $profileEvent | ConvertTo-Json -Compress
            } else {
                Write-Host (Format-ProfileEvent $profileEvent)
            }

            $lastIdentity = $currentIdentity
        }

        Start-Sleep -Milliseconds $PollIntervalMs
    }

    return
}

if (-not $All) {
    $keyboardCategory = [Guid]'34745c63-b2f0-4784-8b67-5e12c8701a31'
    $profiles = @($profiles | Where-Object { $_.Enabled -and $_.CategoryGuid -eq $keyboardCategory })
}

if ($Json) {
    [pscustomobject]@{
        Active = $active
        Profiles = $profiles
    } | ConvertTo-Json -Depth 4
    return
}

Write-Host ('TSF profiles: {0}; active profile: {1}' -f $profiles.Count, $(if ($null -eq $active) { 'none' } else { $active.Name }))
$profiles |
    Sort-Object LanguageId, Name, ProfileGuid |
    Format-Table Active, Enabled, Language, Name, Clsid, ProfileGuid -AutoSize
