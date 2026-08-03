using LimitScanner.Services;

namespace LimitScanner.Collectors;

public sealed class DxDiagCollector(CommandRunner commandRunner)
{
    public async Task<string> CollectAsync(
        string workingDirectory,
        CancellationToken cancellationToken)
    {
        var outputPath = Path.Combine(workingDirectory, "DxDiag.txt");
        var dxdiagPath = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.System),
            "dxdiag.exe");

        await commandRunner.RunAsync(
            dxdiagPath,
            $"/t \"{outputPath}\"",
            TimeSpan.FromSeconds(60),
            cancellationToken);

        for (var attempt = 0; attempt < 30; attempt++)
        {
            if (File.Exists(outputPath) && new FileInfo(outputPath).Length > 0)
            {
                return outputPath;
            }

            await Task.Delay(500, cancellationToken);
        }

        throw new InvalidOperationException("DxDiag 결과 파일이 생성되지 않았습니다.");
    }
}
