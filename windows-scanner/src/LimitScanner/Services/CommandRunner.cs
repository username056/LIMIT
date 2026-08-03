using System.Diagnostics;

namespace LimitScanner.Services;

public sealed class CommandRunner
{
    public async Task RunAsync(
        string fileName,
        string arguments,
        TimeSpan timeout,
        CancellationToken cancellationToken)
    {
        using var process = new Process
        {
            StartInfo = new ProcessStartInfo
            {
                FileName = fileName,
                Arguments = arguments,
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardError = true
            }
        };

        if (!process.Start())
        {
            throw new InvalidOperationException($"{fileName}을(를) 시작하지 못했습니다.");
        }

        using var timeoutSource =
            CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeoutSource.CancelAfter(timeout);

        try
        {
            await process.WaitForExitAsync(timeoutSource.Token);
        }
        catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested)
        {
            try
            {
                process.Kill(entireProcessTree: true);
            }
            catch (InvalidOperationException)
            {
                // 프로세스가 이미 종료됐다.
            }

            throw new TimeoutException($"{fileName} 실행 시간이 초과됐습니다.");
        }

        if (process.ExitCode != 0)
        {
            throw new InvalidOperationException($"{fileName} 실행에 실패했습니다.");
        }
    }
}
