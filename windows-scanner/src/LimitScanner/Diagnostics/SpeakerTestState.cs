namespace LimitScanner.Diagnostics;

public sealed class SpeakerTestState
{
    private readonly HashSet<string> testedChannels = [];

    public bool HasDevice { get; private set; }
    public bool PlaybackAttempted { get; private set; }
    public bool PlaybackFailed { get; private set; }
    public IReadOnlyCollection<string> TestedChannels => testedChannels;
    public bool CanConfirm => HasDevice && testedChannels.Count > 0;

    public void SetDeviceAvailability(bool hasDevice)
    {
        HasDevice = hasDevice;
    }

    public void Reset(bool hasDevice)
    {
        HasDevice = hasDevice;
        PlaybackAttempted = false;
        PlaybackFailed = false;
        testedChannels.Clear();
    }

    public void BeginPlayback()
    {
        PlaybackAttempted = true;
    }

    public void RecordPlaybackSuccess(AudioChannel channel)
    {
        PlaybackAttempted = true;
        testedChannels.Add(channel.ToString().ToUpperInvariant());
    }

    public void RecordPlaybackFailure()
    {
        PlaybackAttempted = true;
        PlaybackFailed = true;
    }

    public ModuleResult CreateResult(
        string requestedUserResult,
        string? deviceName,
        int? deviceNumber)
    {
        var userResult = requestedUserResult == ModuleUserResults.Confirmed && !CanConfirm
            ? ModuleUserResults.Skipped
            : requestedUserResult;
        var playbackOnlyFailed = PlaybackAttempted
            && testedChannels.Count == 0
            && PlaybackFailed;
        var measurementStatus = userResult == ModuleUserResults.Skipped
            ? ModuleMeasurementStatuses.NotExecuted
            : !HasDevice
                ? ModuleMeasurementStatuses.NotDetected
                : playbackOnlyFailed
                    ? ModuleMeasurementStatuses.ExecutionFailed
                    : ModuleMeasurementStatuses.Detected;

        return ModuleResult.Create(
            ModuleTestTypes.Speaker,
            measurementStatus,
            userResult,
            new Dictionary<string, object?>
            {
                ["deviceName"] = deviceName,
                ["deviceNumber"] = deviceNumber,
                ["testedChannels"] = testedChannels.OrderBy(value => value).ToArray(),
                ["playbackAttempted"] = PlaybackAttempted,
                ["playbackFailureDetected"] = PlaybackFailed
            },
            !HasDevice
                ? "OUTPUT_DEVICE_NOT_FOUND"
                : playbackOnlyFailed
                    ? "AUDIO_PLAYBACK_FAILED"
                    : null);
    }
}
