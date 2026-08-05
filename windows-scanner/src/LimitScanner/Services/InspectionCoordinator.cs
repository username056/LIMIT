using LimitScanner.Api;
using LimitScanner.Collectors;

namespace LimitScanner.Services;

public sealed class InspectionCoordinator(
    LimitApiClient apiClient,
    DxDiagCollector dxDiagCollector,
    BatteryReportCollector batteryReportCollector)
{
    private string? sessionKey;

    public async Task RunAsync(
        string pairingCode,
        IProgress<string> progress,
        CancellationToken cancellationToken)
    {
        progress.Report("웹과 연결하는 중입니다.");
        var session = await apiClient.PairAsync(pairingCode, cancellationToken);
        sessionKey = session.SessionKey;
        using var workspace = new InspectionWorkspace();

        progress.Report("시스템 정보와 배터리 정보를 동시에 수집하고 있습니다.");
        var dxdiagTask = dxDiagCollector.CollectAsync(workspace.DirectoryPath, cancellationToken);
        var batteryReportTask = batteryReportCollector.CollectAsync(workspace.DirectoryPath, cancellationToken);
        await Task.WhenAll(new Task[] { dxdiagTask, batteryReportTask });

        var dxdiagPath = await dxdiagTask;
        var batteryReportPath = await batteryReportTask;

        progress.Report("시스템 정보와 배터리 진단 결과를 동시에 업로드하고 있습니다.");
        var dxdiagUploadTask = apiClient.UploadDiagnosticAsync(
            session.SessionKey,
            "DXDIAG",
            dxdiagPath,
            "text/plain",
            cancellationToken);
        var batteryUploadTask = batteryReportPath is null
            ? Task.CompletedTask
            : apiClient.UploadDiagnosticAsync(
                session.SessionKey,
                "BATTERY_REPORT",
                batteryReportPath,
                "text/html",
                cancellationToken);
        await Task.WhenAll(new Task[] { dxdiagUploadTask, batteryUploadTask });

        progress.Report("시스템 진단을 마쳤습니다. 결과를 확인한 뒤 최종 제출해 주세요.");
    }

    public async Task CompleteInspectionAsync(CancellationToken cancellationToken)
    {
        if (sessionKey is null)
        {
            throw new InvalidOperationException("먼저 시스템 진단을 완료해야 최종 제출할 수 있습니다.");
        }

        await apiClient.CompleteAsync(sessionKey, cancellationToken);
    }
}
