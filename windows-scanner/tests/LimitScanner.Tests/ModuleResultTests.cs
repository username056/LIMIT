using System.Text.Json;
using LimitScanner.Api;
using LimitScanner.Diagnostics;
using Xunit;
using System.Windows.Forms;

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

    [Fact]
    public void EachModuleAttemptGetsANewClientResultId()
    {
        var first = ModuleResult.Create(
            ModuleTestTypes.Keyboard,
            ModuleMeasurementStatuses.Detected,
            ModuleUserResults.Confirmed,
            new Dictionary<string, object?>());
        var rerun = ModuleResult.Create(
            ModuleTestTypes.Keyboard,
            ModuleMeasurementStatuses.Detected,
            ModuleUserResults.Confirmed,
            new Dictionary<string, object?>());

        Assert.NotEqual(Guid.Empty, first.ClientResultId);
        Assert.NotEqual(Guid.Empty, rerun.ClientResultId);
        Assert.NotEqual(first.ClientResultId, rerun.ClientResultId);
    }

    [Fact]
    public void PointerRequiresMoveBothClicksAndScroll()
    {
        var state = new PointerTestState();
        state.RecordMove();
        state.RecordClick(MouseButtons.Left);
        state.RecordClick(MouseButtons.Right);
        Assert.False(state.HasRequiredInput);

        state.RecordScroll();

        Assert.True(state.HasRequiredInput);
        Assert.Equal(ModuleMeasurementStatuses.Detected,
            state.CreateResult(ModuleUserResults.Confirmed).MeasurementStatus);
        Assert.Equal("TOUCHPAD",
            state.CreateResult(ModuleUserResults.Confirmed).TestType);
    }

    [Fact]
    public void KeyboardTracksDistinctDetectableKeys()
    {
        var state = new KeyboardTestState();
        state.Record(Keys.A);
        state.Record(Keys.A);
        state.Record(Keys.Enter);

        Assert.Equal(2, state.PressedKeys.Count);
        Assert.Equal("Enter", state.LastKey);
        Assert.Equal(ModuleMeasurementStatuses.Detected,
            state.CreateResult(ModuleUserResults.Confirmed).MeasurementStatus);
    }
}
