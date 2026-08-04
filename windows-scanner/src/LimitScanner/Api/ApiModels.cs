using System.Text.Json.Serialization;
using LimitScanner.Diagnostics;

namespace LimitScanner.Api;

public sealed record ApiEnvelope<T>([property: JsonPropertyName("data")] T Data);

public sealed record PairData(
    string SessionKey,
    string AgentToken,
    DateTimeOffset ExpiresAt);

public sealed record UploadData(
    string UploadId,
    string PresignedUrl,
    IReadOnlyDictionary<string, string> RequiredHeaders);

public sealed record SubmitTestResultRequest(
    Guid ClientResultId,
    string TestType,
    string MeasurementStatus,
    string? UserResult,
    IReadOnlyDictionary<string, object?> MeasuredValues,
    DateTimeOffset TestedAt,
    string? ErrorCode)
{
    public static SubmitTestResultRequest From(ModuleResult result) => new(
        result.ClientResultId,
        result.TestType,
        result.MeasurementStatus,
        result.UserResult,
        result.MeasuredValues,
        result.TestedAt,
        result.ErrorCode);
}
