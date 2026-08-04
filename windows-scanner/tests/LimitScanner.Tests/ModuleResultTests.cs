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
        Assert.NotEqual(Guid.Empty, root.GetProperty("clientResultId").GetGuid());
        Assert.Equal("SPEAKER", root.GetProperty("testType").GetString());
        Assert.Equal("DETECTED", root.GetProperty("measurementStatus").GetString());
        Assert.Equal("USER_CONFIRMED", root.GetProperty("userResult").GetString());
        Assert.Equal("기본 장치", root.GetProperty("measuredValues").GetProperty("deviceName").GetString());
        Assert.Equal(JsonValueKind.Null, root.GetProperty("errorCode").ValueKind);
    }

    [Fact]
    public void SerializesCameraAndMicrophonePermissionDeniedStatus()
    {
        var cameraResult = ModuleResult.Create(
            ModuleTestTypes.Camera,
            ModuleMeasurementStatuses.PermissionDenied,
            ModuleUserResults.ReportedIssue,
            new Dictionary<string, object?> { ["deviceName"] = "내장 카메라" },
            "CAMERA_PERMISSION_DENIED");

        var json = JsonSerializer.Serialize(cameraResult, new JsonSerializerOptions(JsonSerializerDefaults.Web));
        using var document = JsonDocument.Parse(json);
        var root = document.RootElement;

        Assert.Equal("CAMERA", root.GetProperty("testType").GetString());
        Assert.Equal("PERMISSION_DENIED", root.GetProperty("measurementStatus").GetString());
        Assert.Equal("CAMERA_PERMISSION_DENIED", root.GetProperty("errorCode").GetString());
        Assert.Equal("MICROPHONE", ModuleTestTypes.Microphone);
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
