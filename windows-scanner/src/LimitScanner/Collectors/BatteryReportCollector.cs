using LimitScanner.Services;

namespace LimitScanner.Collectors;

public sealed class BatteryReportCollector(CommandRunner commandRunner)
{
    public async Task<string?> CollectAsync(
        string workingDirectory,
        CancellationToken cancellationToken)
    {
        var outputPath = Path.Combine(workingDirectory, "battery-report.html");

        try
        {
            await commandRunner.RunAsync(
                "powercfg.exe",
                $"/batteryreport /output \"{outputPath}\"",
                TimeSpan.FromSeconds(30),
                cancellationToken);
        }
        catch (InvalidOperationException)
        {
            return null;
        }

        return File.Exists(outputPath) && new FileInfo(outputPath).Length > 0
            ? outputPath
            : null;
    }
}
