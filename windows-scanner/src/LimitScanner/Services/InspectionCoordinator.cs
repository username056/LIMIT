using LimitScanner.Api;
using LimitScanner.Collectors;
using LimitScanner.Diagnostics;

namespace LimitScanner.Services;

public sealed class InspectionCoordinator(
    LimitApiClient apiClient,
    DxDiagCollector dxDiagCollector,
    BatteryReportCollector batteryReportCollector,
    InteractiveDeviceDiagnostics deviceDiagnostics)
{
    private string? sessionKey;

    public async Task RunAsync(
        string pairingCode,
        IWin32Window owner,
        IProgress<string> progress,
        CancellationToken cancellationToken)
    {
        progress.Report("웹과 연결하는 중입니다.");
        var session = await apiClient.PairAsync(pairingCode, cancellationToken);
        sessionKey = session.SessionKey;
        using var workspace = new InspectionWorkspace();

        progress.Report("시스템 정보와 배터리 정보를 동시에 수집하고 있습니다.");
        var dxdiagTask = dxDiagCollector.CollectAsync(
            workspace.DirectoryPath,
            cancellationToken);
        var batteryReportTask = batteryReportCollector.CollectAsync(
            workspace.DirectoryPath,
            cancellationToken);
        await Task.WhenAll(new Task[] { dxdiagTask, batteryReportTask });

        var dxdiagPath = await dxdiagTask;
        var batteryReportPath = await batteryReportTask;

        progress.Report("진단 결과를 업로드하고 있습니다.");
        await apiClient.UploadDiagnosticAsync(
            session.SessionKey,
            "DXDIAG",
            dxdiagPath,
            "text/plain",
            cancellationToken);

        if (batteryReportPath is not null)
        {
            await apiClient.UploadDiagnosticAsync(
                session.SessionKey,
                "BATTERY_REPORT",
                batteryReportPath,
                "text/html",
                cancellationToken);
        }

        progress.Report("스피커·디스플레이·충전·카메라·마이크·키보드·포인터 상태를 직접 확인해 주세요.");
        var moduleResults = deviceDiagnostics.Run(owner);
        foreach (var moduleResult in moduleResults)
        {
            progress.Report($"{moduleResult.TestType} 검사 결과를 전송하고 있습니다.");
            await apiClient.SubmitTestResultAsync(
                session.SessionKey,
                moduleResult,
                cancellationToken);
        }

        progress.Report("모든 항목 검사를 마쳤습니다. 필요하면 항목을 선택해 재검사한 뒤 최종 제출해 주세요.");
    }

    public async Task RerunCameraAsync(IWin32Window owner, CancellationToken cancellationToken)
    {
        var result = deviceDiagnostics.RunCamera(owner);
        await SubmitSingleModuleAsync(result, cancellationToken);
    }

    public async Task RerunMicrophoneAsync(IWin32Window owner, CancellationToken cancellationToken)
    {
        var result = deviceDiagnostics.RunMicrophone(owner);
        await SubmitSingleModuleAsync(result, cancellationToken);
    }

    public async Task RerunModuleAsync(
        string testType,
        IWin32Window owner,
        CancellationToken cancellationToken)
    {
        var result = deviceDiagnostics.RunModule(testType, owner);
        await SubmitSingleModuleAsync(result, cancellationToken);
    }

    public async Task CompleteInspectionAsync(CancellationToken cancellationToken)
    {
        if (sessionKey is null)
        {
            throw new InvalidOperationException("먼저 전체 검사를 완료해야 최종 제출을 할 수 있습니다.");
        }

        await apiClient.CompleteAsync(sessionKey, cancellationToken);
    }

    private async Task SubmitSingleModuleAsync(ModuleResult result, CancellationToken cancellationToken)
    {
        if (sessionKey is null)
        {
            throw new InvalidOperationException("먼저 전체 검사를 완료해야 개별 재검사를 할 수 있습니다.");
        }

        await apiClient.SubmitTestResultAsync(sessionKey, result, cancellationToken);
    }
}
