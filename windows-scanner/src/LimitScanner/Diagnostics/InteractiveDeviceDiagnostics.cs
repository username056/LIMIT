namespace LimitScanner.Diagnostics;

public sealed class InteractiveDeviceDiagnostics(
    AudioOutputService audioOutputService,
    CameraCaptureService cameraCaptureService,
    MicrophoneInputService microphoneInputService)
{
    public IReadOnlyList<ModuleResult> Run(IWin32Window owner)
    {
        var results = new List<ModuleResult>(5);

        MessageBox.Show(
            owner,
            "이제부터 스피커 → 디스플레이 → 충전 → 카메라 → 마이크 순서로 5가지 항목을 직접 확인합니다.\n"
                + "각 항목의 안내를 확인한 뒤 정상·이상 있음·건너뛰기 중 하나를 선택해 주세요.",
            "직접 확인 검사 시작",
            MessageBoxButtons.OK,
            MessageBoxIcon.Information);

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
