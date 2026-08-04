using LimitScanner.Diagnostics;
using Xunit;

namespace LimitScanner.Tests;

public sealed class SpeakerTestStateTests
{
    [Fact]
    public void DeviceMissingCannotCreateConfirmedResult()
    {
        var state = new SpeakerTestState();

        var result = state.CreateResult(ModuleUserResults.Confirmed, null, null);

        Assert.False(state.CanConfirm);
        Assert.Equal(ModuleUserResults.Skipped, result.UserResult);
        Assert.Equal(ModuleMeasurementStatuses.NotExecuted, result.MeasurementStatus);
        Assert.Equal("OUTPUT_DEVICE_NOT_FOUND", result.ErrorCode);
    }

    [Fact]
    public void DeviceMissingReportedIssueReturnsNotDetected()
    {
        var state = new SpeakerTestState();

        var result = state.CreateResult(ModuleUserResults.ReportedIssue, null, null);

        Assert.Equal(ModuleUserResults.ReportedIssue, result.UserResult);
        Assert.Equal(ModuleMeasurementStatuses.NotDetected, result.MeasurementStatus);
        Assert.Equal("OUTPUT_DEVICE_NOT_FOUND", result.ErrorCode);
    }

    [Fact]
    public void PlaybackFailureCannotCreateConfirmedResult()
    {
        var state = new SpeakerTestState();
        state.SetDeviceAvailability(true);
        state.BeginPlayback();
        state.RecordPlaybackFailure();

        var result = state.CreateResult(ModuleUserResults.Confirmed, "스피커", 0);

        Assert.False(state.CanConfirm);
        Assert.Equal(ModuleUserResults.Skipped, result.UserResult);
        Assert.Equal(ModuleMeasurementStatuses.NotExecuted, result.MeasurementStatus);
        Assert.Equal("AUDIO_PLAYBACK_FAILED", result.ErrorCode);
    }

    [Fact]
    public void PlaybackFailureReportedIssueReturnsExecutionFailed()
    {
        var state = new SpeakerTestState();
        state.SetDeviceAvailability(true);
        state.RecordPlaybackFailure();

        var result = state.CreateResult(ModuleUserResults.ReportedIssue, "스피커", 0);

        Assert.Equal(ModuleUserResults.ReportedIssue, result.UserResult);
        Assert.Equal(ModuleMeasurementStatuses.ExecutionFailed, result.MeasurementStatus);
        Assert.Equal("AUDIO_PLAYBACK_FAILED", result.ErrorCode);
    }

    [Theory]
    [InlineData(AudioChannel.Both, "BOTH")]
    [InlineData(AudioChannel.Left, "LEFT")]
    [InlineData(AudioChannel.Right, "RIGHT")]
    public void OneSuccessfulChannelAllowsConfirmedResult(AudioChannel channel, string expectedChannel)
    {
        var state = new SpeakerTestState();
        state.SetDeviceAvailability(true);
        state.RecordPlaybackSuccess(channel);

        var result = state.CreateResult(ModuleUserResults.Confirmed, "스피커", 0);

        Assert.True(state.CanConfirm);
        Assert.Equal(ModuleUserResults.Confirmed, result.UserResult);
        Assert.Equal(ModuleMeasurementStatuses.Detected, result.MeasurementStatus);
        Assert.Null(result.ErrorCode);
        var channels = Assert.IsType<string[]>(result.MeasuredValues["testedChannels"]);
        Assert.Contains(expectedChannel, channels);
    }

    [Fact]
    public void ChangingOutputDeviceClearsPreviousPlaybackConfirmation()
    {
        var state = new SpeakerTestState();
        state.SetDeviceAvailability(true);
        state.RecordPlaybackSuccess(AudioChannel.Both);
        Assert.True(state.CanConfirm);

        state.Reset(hasDevice: true);

        Assert.False(state.CanConfirm);
        Assert.Empty(state.TestedChannels);
        var result = state.CreateResult(ModuleUserResults.Confirmed, "다른 스피커", 1);
        Assert.Equal(ModuleUserResults.Skipped, result.UserResult);
    }
}
