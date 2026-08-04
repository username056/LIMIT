namespace LimitScanner.Diagnostics;

public sealed class InteractiveDeviceDiagnostics(
    AudioOutputService audioOutputService,
    CameraCaptureService cameraCaptureService,
    MicrophoneInputService microphoneInputService)
{
    public IReadOnlyList<ModuleResult> Run(IWin32Window owner)
    {
        var results = new List<ModuleResult>(5);

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

        results.Add(RunCamera(owner));
        results.Add(RunMicrophone(owner));

        return results;
    }

    public ModuleResult RunCamera(IWin32Window owner)
    {
        using var cameraForm = new CameraDiagnosticForm(cameraCaptureService);
        cameraForm.ShowDialog(owner);
        return cameraForm.Result;
    }

    public ModuleResult RunMicrophone(IWin32Window owner)
    {
        using var microphoneForm = new MicrophoneDiagnosticForm(microphoneInputService);
        microphoneForm.ShowDialog(owner);
        return microphoneForm.Result;
    }
}
