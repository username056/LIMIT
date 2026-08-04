namespace LimitScanner.Diagnostics;

public static class PowerLineStates
{
    public const string Connected = "CONNECTED";
    public const string Disconnected = "DISCONNECTED";
    public const string Unknown = "UNKNOWN";
}

public sealed class PowerStateTracker(string initialState)
{
    public string InitialState { get; } = initialState;
    public string CurrentState { get; private set; } = initialState;
    public int TransitionCount { get; private set; }
    public bool CanConfirm => CurrentState != PowerLineStates.Unknown;

    public bool Record(string nextState)
    {
        if (string.Equals(nextState, CurrentState, StringComparison.Ordinal))
        {
            return false;
        }

        CurrentState = nextState;
        TransitionCount++;
        return true;
    }

    public ModuleResult CreateResult(string userResult)
    {
        if (userResult == ModuleUserResults.Confirmed && !CanConfirm)
        {
            userResult = ModuleUserResults.Skipped;
        }

        var isKnown = CurrentState != PowerLineStates.Unknown;
        var measurementStatus = userResult == ModuleUserResults.Skipped
            ? ModuleMeasurementStatuses.NotExecuted
            : isKnown
                ? ModuleMeasurementStatuses.Detected
                : ModuleMeasurementStatuses.NotDetected;
        return ModuleResult.Create(
            ModuleTestTypes.Charging,
            measurementStatus,
            userResult,
            new Dictionary<string, object?>
            {
                ["initialAcState"] = InitialState,
                ["finalAcState"] = CurrentState,
                ["transitionDetected"] = TransitionCount > 0,
                ["transitionCount"] = TransitionCount,
                ["scope"] = "WINDOWS_AC_LINE_STATUS_ONLY"
            },
            isKnown ? null : "AC_STATUS_UNKNOWN");
    }
}
