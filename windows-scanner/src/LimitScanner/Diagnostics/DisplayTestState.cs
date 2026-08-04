namespace LimitScanner.Diagnostics;

public sealed class DisplayTestState
{
    public static readonly IReadOnlyList<string> RequiredColors =
        ["BLACK", "WHITE", "RED", "GREEN", "BLUE"];

    private readonly List<string> testedColors = [];

    public bool HasDisplay { get; private set; }
    public IReadOnlyList<string> TestedColors => testedColors;
    public bool CompletedAllColors { get; private set; }
    public bool CanConfirm => HasDisplay && CompletedAllColors;

    public void Reset(bool hasDisplay)
    {
        HasDisplay = hasDisplay;
        testedColors.Clear();
        CompletedAllColors = false;
    }

    public void SetDisplayAvailability(bool hasDisplay)
    {
        HasDisplay = hasDisplay;
    }

    public void RecordColorShown(string color)
    {
        if (testedColors.Count >= RequiredColors.Count)
        {
            return;
        }

        var expectedColor = RequiredColors[testedColors.Count];
        if (!string.Equals(color, expectedColor, StringComparison.Ordinal))
        {
            throw new InvalidOperationException(
                $"디스플레이 검사 색상 순서가 올바르지 않습니다. expected={expectedColor}, actual={color}");
        }

        testedColors.Add(color);
    }

    public void MarkCompleted()
    {
        CompletedAllColors = testedColors.SequenceEqual(RequiredColors);
    }

    public ModuleResult CreateResult(
        string requestedUserResult,
        string? screenDeviceName,
        int? width,
        int? height)
    {
        var userResult = requestedUserResult == ModuleUserResults.Confirmed && !CanConfirm
            ? ModuleUserResults.Skipped
            : requestedUserResult;
        var measurementStatus = userResult == ModuleUserResults.Skipped
            ? ModuleMeasurementStatuses.NotExecuted
            : HasDisplay
                ? ModuleMeasurementStatuses.Detected
                : ModuleMeasurementStatuses.NotDetected;

        return ModuleResult.Create(
            ModuleTestTypes.Display,
            measurementStatus,
            userResult,
            new Dictionary<string, object?>
            {
                ["screenDeviceName"] = screenDeviceName,
                ["width"] = width,
                ["height"] = height,
                ["requiredColors"] = RequiredColors.ToArray(),
                ["testedColors"] = testedColors.ToArray(),
                ["completedAllColors"] = CompletedAllColors
            },
            HasDisplay ? null : "DISPLAY_NOT_FOUND");
    }
}
