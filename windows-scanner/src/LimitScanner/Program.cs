using LimitScanner.Api;
using LimitScanner.Collectors;
using LimitScanner.Diagnostics;
using LimitScanner.Services;
using System.Reflection;

namespace LimitScanner;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        ApplicationConfiguration.Initialize();

        var baseUrl = Environment.GetEnvironmentVariable("LIMIT_API_BASE_URL");
        if (string.IsNullOrWhiteSpace(baseUrl))
        {
            baseUrl = Assembly.GetExecutingAssembly()
                .GetCustomAttributes<AssemblyMetadataAttribute>()
                .First(attribute => attribute.Key == "LimitApiBaseUrl")
                .Value
                ?? throw new InvalidOperationException("Limit API 주소가 설정되지 않았습니다.");
        }

        var commandRunner = new CommandRunner();
        var apiClient = new LimitApiClient(baseUrl);
        var coordinator = new InspectionCoordinator(
            apiClient,
            new DxDiagCollector(commandRunner),
            new BatteryReportCollector(commandRunner),
            new InteractiveDeviceDiagnostics(new AudioOutputService()));

        Application.Run(new MainForm(coordinator));
    }
}
