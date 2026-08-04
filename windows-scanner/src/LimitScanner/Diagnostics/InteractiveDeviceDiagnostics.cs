namespace LimitScanner.Diagnostics;

public sealed class InteractiveDeviceDiagnostics(AudioOutputService audioOutputService)
{
    public IReadOnlyList<ModuleResult> Run(IWin32Window owner)
    {
        var results = new List<ModuleResult>(3);

        using (var speakerForm = new SpeakerDiagnosticForm(audioOutputService))
        {
            speakerForm.ShowDialog(owner);
            results.Add(speakerForm.Result);
        }

        using (var displayForm = new DisplayDiagnosticForm())
        {
            displayForm.ShowDialog(owner);
            results.Add(displayForm.Result);
        }

        using (var chargingForm = new ChargingDiagnosticForm())
        {
            chargingForm.ShowDialog(owner);
            results.Add(chargingForm.Result);
        }

        return results;
    }
}
