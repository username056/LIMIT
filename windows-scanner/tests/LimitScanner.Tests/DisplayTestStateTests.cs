using LimitScanner.Diagnostics;
using Xunit;

namespace LimitScanner.Tests;

public sealed class DisplayTestStateTests
{
    [Fact]
    public void RequiredColorsUseFixedInspectionOrder()
    {
        Assert.Equal(
            ["BLACK", "WHITE", "RED", "GREEN", "BLUE"],
            DisplayTestState.RequiredColors);
    }

    [Fact]
    public void InterruptedInspectionCannotCreateConfirmedResult()
    {
        var state = new DisplayTestState();
        state.Reset(hasDisplay: true);
        state.RecordColorShown("BLACK");
        state.RecordColorShown("WHITE");
        state.MarkCompleted();

        var result = state.CreateResult(
            ModuleUserResults.Confirmed,
            "DISPLAY1",
            1920,
            1080);

        Assert.False(state.CompletedAllColors);
        Assert.False(state.CanConfirm);
        Assert.Equal(ModuleUserResults.Skipped, result.UserResult);
        Assert.Equal(ModuleMeasurementStatuses.NotExecuted, result.MeasurementStatus);
        Assert.False(Assert.IsType<bool>(result.MeasuredValues["completedAllColors"]));
    }

    [Fact]
    public void FullColorInspectionAllowsConfirmedResult()
    {
        var state = new DisplayTestState();
        state.Reset(hasDisplay: true);
        foreach (var color in DisplayTestState.RequiredColors)
        {
            state.RecordColorShown(color);
        }
        state.MarkCompleted();

        var result = state.CreateResult(
            ModuleUserResults.Confirmed,
            "DISPLAY1",
            1920,
            1080);

        Assert.True(state.CompletedAllColors);
        Assert.True(state.CanConfirm);
        Assert.Equal(ModuleUserResults.Confirmed, result.UserResult);
        Assert.Equal(ModuleMeasurementStatuses.Detected, result.MeasurementStatus);
        Assert.True(Assert.IsType<bool>(result.MeasuredValues["completedAllColors"]));
        Assert.Equal(
            DisplayTestState.RequiredColors,
            Assert.IsType<string[]>(result.MeasuredValues["testedColors"]));
    }

    [Fact]
    public void RejectsColorRecordedOutOfOrder()
    {
        var state = new DisplayTestState();
        state.Reset(hasDisplay: true);

        Assert.Throws<InvalidOperationException>(() => state.RecordColorShown("RED"));
    }

    [Fact]
    public void ChangingDisplayClearsPreviousColorConfirmation()
    {
        var state = new DisplayTestState();
        state.Reset(hasDisplay: true);
        foreach (var color in DisplayTestState.RequiredColors)
        {
            state.RecordColorShown(color);
        }
        state.MarkCompleted();
        Assert.True(state.CanConfirm);

        state.Reset(hasDisplay: true);

        Assert.False(state.CanConfirm);
        Assert.Empty(state.TestedColors);
    }

    [Fact]
    public void MissingDisplayReturnsNotDetected()
    {
        var state = new DisplayTestState();

        var result = state.CreateResult(ModuleUserResults.ReportedIssue, null, null, null);

        Assert.Equal(ModuleMeasurementStatuses.NotDetected, result.MeasurementStatus);
        Assert.Equal(ModuleUserResults.ReportedIssue, result.UserResult);
        Assert.Equal("DISPLAY_NOT_FOUND", result.ErrorCode);
    }
}
