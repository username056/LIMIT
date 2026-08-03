using LimitScanner.Api;
using LimitScanner.Collectors;
using LimitScanner.Services;

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
            baseUrl = "http://localhost:8080/";
        }

        var commandRunner = new CommandRunner();
        var apiClient = new LimitApiClient(baseUrl);
        var coordinator = new InspectionCoordinator(
            apiClient,
            new DxDiagCollector(commandRunner),
            new BatteryReportCollector(commandRunner));

        Application.Run(new MainForm(coordinator));
    }
}
