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
        using var workspace = new InspectionWorkspace();
        using var pipelineCancellation =
            CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        var pipelineToken = pipelineCancellation.Token;

        progress.Report("웹 연결과 시스템 정보 수집을 동시에 시작합니다.");
        var pairingTask = apiClient.PairAsync(pairingCode, pipelineToken);
        var dxdiagTask = dxDiagCollector.CollectAsync(workspace.DirectoryPath, pipelineToken);
        var batteryReportTask =
            batteryReportCollector.CollectAsync(workspace.DirectoryPath, pipelineToken);

        try
        {
            var session = await pairingTask;
            sessionKey = session.SessionKey;

            progress.Report("수집이 끝난 진단 결과부터 업로드하고 있습니다.");
            var dxdiagUploadTask = UploadDxdiagWhenReadyAsync(
                session.SessionKey,
                dxdiagTask,
                pipelineToken);
            var batteryUploadTask = UploadBatteryReportWhenReadyAsync(
                session.SessionKey,
                batteryReportTask,
                pipelineToken);
            await Task.WhenAll(dxdiagUploadTask, batteryUploadTask);
        }
        catch
        {
            await pipelineCancellation.CancelAsync();
            await ObservePipelineTasksAsync(pairingTask, dxdiagTask, batteryReportTask);
            throw;
        }

        progress.Report("시스템 진단을 마쳤습니다. 결과를 확인한 뒤 최종 제출해 주세요.");
    }

    private async Task UploadDxdiagWhenReadyAsync(
        string currentSessionKey,
        Task<string> dxdiagTask,
        CancellationToken cancellationToken)
    {
        var dxdiagPath = await dxdiagTask;
        await apiClient.UploadDiagnosticAsync(
            currentSessionKey,
            "DXDIAG",
            dxdiagPath,
            "text/plain",
            cancellationToken);
    }

    private async Task UploadBatteryReportWhenReadyAsync(
        string currentSessionKey,
        Task<string?> batteryReportTask,
        CancellationToken cancellationToken)
    {
        var batteryReportPath = await batteryReportTask;
        if (batteryReportPath is null)
        {
            return;
        }

        await apiClient.UploadDiagnosticAsync(
            currentSessionKey,
            "BATTERY_REPORT",
            batteryReportPath,
            "text/html",
            cancellationToken);
    }

    private static async Task ObservePipelineTasksAsync(params Task[] tasks)
    {
        try
        {
            await Task.WhenAll(tasks);
        }
        catch
        {
            // 원래 예외를 유지하면서 백그라운드 Task 예외만 소비합니다.
        }
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
