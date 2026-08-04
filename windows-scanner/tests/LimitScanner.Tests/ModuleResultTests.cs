using System.Text.Json;
using LimitScanner.Api;
using LimitScanner.Diagnostics;
using Xunit;

namespace LimitScanner.Tests;

public sealed class ModuleResultTests
{
    [Fact]
    public void MapsAndSerializesTestResultRequestWithApiFieldNamesAndEnumValues()
    {
        var results = new[]
        {
            ModuleResult.Create(
                ModuleTestTypes.Speaker,
                ModuleMeasurementStatuses.Detected,
                ModuleUserResults.Confirmed,
                new Dictionary<string, object?> { ["deviceName"] = "기본 장치" }),
            ModuleResult.Create(
                ModuleTestTypes.Display,
                ModuleMeasurementStatuses.Detected,
                ModuleUserResults.ReportedIssue,
                new Dictionary<string, object?> { ["completedAllColors"] = true }),
            ModuleResult.Create(
                ModuleTestTypes.Charging,
                ModuleMeasurementStatuses.NotDetected,
                ModuleUserResults.ReportedIssue,
                new Dictionary<string, object?> { ["finalAcState"] = PowerLineStates.Unknown },
                "AC_STATUS_UNKNOWN")
        };

        foreach (var result in results)
        {
            var request = SubmitTestResultRequest.From(result);
            var json = JsonSerializer.Serialize(
                request,
                new JsonSerializerOptions(JsonSerializerDefaults.Web));
            using var document = JsonDocument.Parse(json);
            var root = document.RootElement;

            Assert.Equal(result.ClientResultId, request.ClientResultId);
            Assert.Equal(result.TestType, request.TestType);
            Assert.Equal(result.MeasurementStatus, request.MeasurementStatus);
            Assert.Equal(result.UserResult, request.UserResult);
            Assert.Equal(result.TestedAt, request.TestedAt);
            Assert.Equal(result.ErrorCode, request.ErrorCode);
            Assert.Equal(result.ClientResultId, root.GetProperty("clientResultId").GetGuid());
            Assert.Equal(result.TestType, root.GetProperty("testType").GetString());
            Assert.Equal(result.MeasurementStatus, root.GetProperty("measurementStatus").GetString());
            Assert.Equal(result.UserResult, root.GetProperty("userResult").GetString());
            Assert.Equal(JsonValueKind.Object, root.GetProperty("measuredValues").ValueKind);
            Assert.True(root.TryGetProperty("testedAt", out _));
            Assert.True(root.TryGetProperty("errorCode", out _));
        }
    }
}
