using Microsoft.Win32;

namespace LimitScanner.Diagnostics;

public static class PrivacyConsent
{
    public static bool IsMicrophoneAccessDenied()
    {
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(
                @"SOFTWARE\Microsoft\Windows\CurrentVersion\CapabilityAccessManager\ConsentStore\microphone");
            var value = key?.GetValue("Value") as string;
            return string.Equals(value, "Deny", StringComparison.OrdinalIgnoreCase);
        }
        catch (Exception)
        {
            return false;
        }
    }
}
