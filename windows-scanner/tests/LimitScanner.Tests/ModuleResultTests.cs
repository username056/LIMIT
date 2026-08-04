using System.Text.Json;
using LimitScanner.Diagnostics;
using Xunit;

namespace LimitScanner.Tests;

public sealed class ModuleResultTests
{
    [Fact]
    public void SerializesWithInspectionApiFieldNamesAndEnumValues()
    {
        var result = ModuleResult.Create(
            ModuleTestTypes.Speaker,
            ModuleMeasurementStatuses.Detected,
            ModuleUserResults.Confirmed,
            new Dictionary<string, object?> { ["deviceName"] = "기본 장치" });

        var json = JsonSerializer.Serialize(result, new JsonSerializerOptions(JsonSerializerDefaults.Web));
        using var document = JsonDocument.Parse(json);
        var root = document.RootElement;

        Assert.NotEqual(Guid.Empty, root.GetProperty("clientResultId").GetGuid());
        Assert.Equal("SPEAKER", root.GetProperty("testType").GetString());
        Assert.Equal("DETECTED", root.GetProperty("measurementStatus").GetString());
        Assert.Equal("USER_CONFIRMED", root.GetProperty("userResult").GetString());
        Assert.Equal("기본 장치", root.GetProperty("measuredValues").GetProperty("deviceName").GetString());
        Assert.Equal(JsonValueKind.Null, root.GetProperty("errorCode").ValueKind);
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
}
