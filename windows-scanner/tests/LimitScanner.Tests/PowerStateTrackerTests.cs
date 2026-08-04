using LimitScanner.Diagnostics;
using Xunit;

namespace LimitScanner.Tests;

public sealed class PowerStateTrackerTests
{
    [Theory]
    [InlineData(PowerLineStates.Connected, ModuleUserResults.Confirmed, ModuleMeasurementStatuses.Detected, null)]
    [InlineData(PowerLineStates.Disconnected, ModuleUserResults.Confirmed, ModuleMeasurementStatuses.Detected, null)]
    [InlineData(PowerLineStates.Unknown, ModuleUserResults.ReportedIssue, ModuleMeasurementStatuses.NotDetected, "AC_STATUS_UNKNOWN")]
    public void CreatesResultForCurrentPowerState(
        string powerState,
        string userResult,
        string expectedMeasurementStatus,
        string? expectedErrorCode)
    {
        var tracker = new PowerStateTracker(powerState);

        var result = tracker.CreateResult(userResult);

        Assert.Equal(ModuleTestTypes.Charging, result.TestType);
        Assert.Equal(expectedMeasurementStatus, result.MeasurementStatus);
        Assert.Equal(expectedErrorCode, result.ErrorCode);
        Assert.Equal(powerState, result.MeasuredValues["initialAcState"]);
        Assert.Equal(powerState, result.MeasuredValues["finalAcState"]);
        Assert.Equal(0, result.MeasuredValues["transitionCount"]);
    }

    [Fact]
    public void UnknownPowerStateCannotCreateConfirmedResult()
    {
        var tracker = new PowerStateTracker(PowerLineStates.Unknown);

        var result = tracker.CreateResult(ModuleUserResults.Confirmed);

        Assert.False(tracker.CanConfirm);
        Assert.Equal(ModuleUserResults.Skipped, result.UserResult);
        Assert.Equal(ModuleMeasurementStatuses.NotExecuted, result.MeasurementStatus);
        Assert.Equal("AC_STATUS_UNKNOWN", result.ErrorCode);
    }

    [Fact]
    public void InitialStateIsNotCountedAndOnlyActualChangesIncrementCount()
    {
        var tracker = new PowerStateTracker(PowerLineStates.Connected);

        Assert.Equal(0, tracker.TransitionCount);
        Assert.False(tracker.Record(PowerLineStates.Connected));
        Assert.True(tracker.Record(PowerLineStates.Disconnected));
        Assert.False(tracker.Record(PowerLineStates.Disconnected));
        Assert.True(tracker.Record(PowerLineStates.Connected));

        var result = tracker.CreateResult(ModuleUserResults.Confirmed);
        Assert.Equal(2, tracker.TransitionCount);
        Assert.Equal(2, result.MeasuredValues["transitionCount"]);
        Assert.True(Assert.IsType<bool>(result.MeasuredValues["transitionDetected"]));
    }
}
