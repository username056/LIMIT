using System.Text.Json.Serialization;

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
