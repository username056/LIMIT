using LimitScanner.Api;
using LimitScanner.Collectors;
using System.Diagnostics;

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
        var totalTimer = Stopwatch.StartNew();
        progress.Report("웹과 연결하는 중입니다.");
        var (session, pairingElapsed) = await MeasureAsync(
            () => apiClient.PairAsync(pairingCode, cancellationToken));
        sessionKey = session.SessionKey;
        using var workspace = new InspectionWorkspace();

        progress.Report("시스템 정보와 배터리 정보를 동시에 수집하고 있습니다.");
        var dxdiagTask = MeasureAsync(
            () => dxDiagCollector.CollectAsync(workspace.DirectoryPath, cancellationToken));
        var batteryReportTask = MeasureAsync(
            () => batteryReportCollector.CollectAsync(workspace.DirectoryPath, cancellationToken));
        await Task.WhenAll(new Task[] { dxdiagTask, batteryReportTask });

        var (dxdiagPath, dxdiagCollectionElapsed) = await dxdiagTask;
        var (batteryReportPath, batteryCollectionElapsed) = await batteryReportTask;
        progress.Report(
            $"정보 수집 완료 · 시스템 {InspectionTimingFormatter.Format(dxdiagCollectionElapsed)} · 배터리 {InspectionTimingFormatter.Format(batteryCollectionElapsed)}");

        progress.Report("시스템 정보와 배터리 진단 결과를 동시에 업로드하고 있습니다.");
        var dxdiagUploadTask = MeasureAsync(async () =>
        {
            await apiClient.UploadDiagnosticAsync(
                session.SessionKey,
                "DXDIAG",
                dxdiagPath,
                "text/plain",
                cancellationToken);
            return true;
        });
        var batteryUploadTask = batteryReportPath is null
            ? Task.FromResult<(bool Result, TimeSpan Elapsed)>((false, TimeSpan.Zero))
            : MeasureAsync(async () =>
            {
                await apiClient.UploadDiagnosticAsync(
                    session.SessionKey,
                    "BATTERY_REPORT",
                    batteryReportPath,
                    "text/html",
                    cancellationToken);
                return true;
            });
        await Task.WhenAll(new Task[] { dxdiagUploadTask, batteryUploadTask });

        var (_, dxdiagUploadElapsed) = await dxdiagUploadTask;
        var (hasBatteryUpload, batteryUploadElapsed) = await batteryUploadTask;
        var batteryUploadText = hasBatteryUpload
            ? InspectionTimingFormatter.Format(batteryUploadElapsed)
            : "대상 없음";
        progress.Report(
            $"업로드 완료 · 시스템 {InspectionTimingFormatter.Format(dxdiagUploadElapsed)} · 배터리 {batteryUploadText}");

        totalTimer.Stop();
        progress.Report(
            $"시스템 진단 완료 · 연결 {InspectionTimingFormatter.Format(pairingElapsed)}"
            + $" · 시스템 수집 {InspectionTimingFormatter.Format(dxdiagCollectionElapsed)}"
            + $" · 배터리 수집 {InspectionTimingFormatter.Format(batteryCollectionElapsed)}"
            + $" · 시스템 업로드 {InspectionTimingFormatter.Format(dxdiagUploadElapsed)}"
            + $" · 배터리 업로드 {batteryUploadText}"
            + $" · 전체 {InspectionTimingFormatter.Format(totalTimer.Elapsed)}. 최종 제출해 주세요.");
    }

    public async Task CompleteInspectionAsync(CancellationToken cancellationToken)
    {
        if (sessionKey is null)
        {
            throw new InvalidOperationException("먼저 시스템 진단을 완료해야 최종 제출할 수 있습니다.");
        }

        await apiClient.CompleteAsync(sessionKey, cancellationToken);
    }

    private static async Task<(T Result, TimeSpan Elapsed)> MeasureAsync<T>(Func<Task<T>> action)
    {
        var timer = Stopwatch.StartNew();
        var result = await action();
        timer.Stop();
        return (result, timer.Elapsed);
    }
}
