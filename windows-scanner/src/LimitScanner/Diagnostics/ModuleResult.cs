namespace LimitScanner.Diagnostics;

public static class ModuleTestTypes
{
    public const string Speaker = "SPEAKER";
    public const string Display = "DISPLAY";
    public const string Charging = "CHARGING";
    public const string Camera = "CAMERA";
    public const string Microphone = "MICROPHONE";
}

public static class ModuleMeasurementStatuses
{
    public const string Detected = "DETECTED";
    public const string NotDetected = "NOT_DETECTED";
    public const string ExecutionFailed = "EXECUTION_FAILED";
    public const string PermissionDenied = "PERMISSION_DENIED";
}

public static class ModuleUserResults
{
    public const string Confirmed = "USER_CONFIRMED";
    public const string ReportedIssue = "USER_REPORTED_ISSUE";
    public const string Skipped = "SKIPPED";
}

public sealed record ModuleResult(
    Guid ClientResultId,
    string TestType,
    string MeasurementStatus,
    string? UserResult,
    IReadOnlyDictionary<string, object?> MeasuredValues,
    DateTimeOffset TestedAt,
    string? ErrorCode)
{
    public static ModuleResult Create(
        string testType,
        string measurementStatus,
        string? userResult,
        IReadOnlyDictionary<string, object?> measuredValues,
        string? errorCode = null) =>
        new(
            Guid.NewGuid(),
            testType,
            measurementStatus,
            userResult,
            measuredValues,
            DateTimeOffset.Now,
            errorCode);
}
