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
        var result = ModuleResult.Create(
            ModuleTestTypes.Speaker,
            ModuleMeasurementStatuses.Detected,
            ModuleUserResults.Confirmed,
            new Dictionary<string, object?> { ["deviceName"] = "기본 장치" });

        var request = SubmitTestResultRequest.From(result);
        var json = JsonSerializer.Serialize(request, new JsonSerializerOptions(JsonSerializerDefaults.Web));
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
        Assert.Equal(JsonValueKind.Object, root.GetProperty("measuredValues").ValueKind);
        Assert.True(root.TryGetProperty("testedAt", out _));
        Assert.True(root.TryGetProperty("errorCode", out _));
    }

    [Fact]
    public void PowerStateTrackerCountsOnlyActualStateChanges()
    {
        var tracker = new PowerStateTracker("CONNECTED");

        Assert.False(tracker.Record("CONNECTED"));
        Assert.True(tracker.Record("DISCONNECTED"));
        Assert.False(tracker.Record("DISCONNECTED"));
        Assert.True(tracker.Record("CONNECTED"));

        Assert.Equal("CONNECTED", tracker.InitialState);
        Assert.Equal("CONNECTED", tracker.CurrentState);
        Assert.Equal(2, tracker.TransitionCount);
    }

    [Fact]
    public void EachModuleAttemptGetsANewClientResultId()
    {
        var first = ModuleResult.Create(
            ModuleTestTypes.Speaker,
            ModuleMeasurementStatuses.Detected,
            ModuleUserResults.Confirmed,
            new Dictionary<string, object?>());
        var rerun = ModuleResult.Create(
            ModuleTestTypes.Speaker,
            ModuleMeasurementStatuses.Detected,
            ModuleUserResults.Confirmed,
            new Dictionary<string, object?>());

        Assert.NotEqual(Guid.Empty, first.ClientResultId);
        Assert.NotEqual(Guid.Empty, rerun.ClientResultId);
        Assert.NotEqual(first.ClientResultId, rerun.ClientResultId);
    }

}
