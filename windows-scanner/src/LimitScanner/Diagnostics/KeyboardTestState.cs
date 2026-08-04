namespace LimitScanner.Diagnostics;

public sealed class KeyboardTestState
{
    private readonly HashSet<int> pressedKeys = [];

    public IReadOnlyCollection<int> PressedKeys => pressedKeys;

    public string? LastKey { get; private set; }

    public bool Record(Keys key)
    {
        LastKey = key.ToString();
        return pressedKeys.Add((int)key);
    }

    public ModuleResult CreateResult(string userResult) => ModuleResult.Create(
        ModuleTestTypes.Keyboard,
        pressedKeys.Count > 0 ? ModuleMeasurementStatuses.Detected : ModuleMeasurementStatuses.NotDetected,
        userResult,
        new Dictionary<string, object?>
        {
            ["detectedKeyCount"] = pressedKeys.Count,
            ["detectedKeys"] = pressedKeys.Order().Select(value => ((Keys)value).ToString()).ToArray(),
            ["lastKey"] = LastKey,
            ["limitations"] = "Fn and OS-reserved keys are not guaranteed"
        },
        pressedKeys.Count > 0 ? null : "KEY_INPUT_NOT_DETECTED");
}
