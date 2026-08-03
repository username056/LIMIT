using LimitScanner.Api;
using LimitScanner.Collectors;

namespace LimitScanner.Services;

public sealed class InspectionCoordinator(
    LimitApiClient apiClient,
    DxDiagCollector dxDiagCollector,
    BatteryReportCollector batteryReportCollector)
{
    public async Task RunAsync(
        string pairingCode,
        IProgress<string> progress,
        CancellationToken cancellationToken)
    {
        progress.Report("웹과 연결하는 중입니다.");
        var session = await apiClient.PairAsync(pairingCode, cancellationToken);
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

        await apiClient.CompleteAsync(session.SessionKey, cancellationToken);
        progress.Report("검사가 완료됐습니다. 웹으로 돌아가 결과를 확인해 주세요.");
    }
}
