namespace LimitScanner.Services;

public sealed class InspectionWorkspace : IDisposable
{
    public InspectionWorkspace()
    {
        DirectoryPath = Path.Combine(
            Path.GetTempPath(),
            "LimitScanner",
            Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(DirectoryPath);
    }

    public string DirectoryPath { get; }

    public void Dispose()
    {
        try
        {
            if (Directory.Exists(DirectoryPath))
            {
                Directory.Delete(DirectoryPath, recursive: true);
            }
        }
        catch (IOException)
        {
            // 잠긴 임시 파일은 Windows가 이후 정리할 수 있도록 남긴다.
        }
        catch (UnauthorizedAccessException)
        {
            // 진단 성공 여부에는 영향을 주지 않는다.
        }
    }
}
