namespace LimitScanner.Diagnostics;

public sealed class KeyboardTestState
{
    public static readonly IReadOnlyList<Keys> RequiredKeys =
    [
        Keys.Oemtilde,
        Keys.D1, Keys.D2, Keys.D3, Keys.D4, Keys.D5, Keys.D6, Keys.D7, Keys.D8, Keys.D9, Keys.D0,
        Keys.OemMinus, Keys.Oemplus, Keys.Back,
        Keys.Tab,
        Keys.Q, Keys.W, Keys.E, Keys.R, Keys.T, Keys.Y, Keys.U, Keys.I, Keys.O, Keys.P,
        Keys.OemOpenBrackets, Keys.OemCloseBrackets, Keys.Oem5,
        Keys.CapsLock,
        Keys.A, Keys.S, Keys.D, Keys.F, Keys.G, Keys.H, Keys.J, Keys.K, Keys.L,
        Keys.Oem1, Keys.Oem7, Keys.Enter,
        Keys.LShiftKey,
        Keys.Z, Keys.X, Keys.C, Keys.V, Keys.B, Keys.N, Keys.M,
        Keys.Oemcomma, Keys.OemPeriod, Keys.OemQuestion,
        Keys.LControlKey, Keys.HanjaMode, Keys.Space, Keys.HangulMode,
        Keys.Left, Keys.Up, Keys.Down, Keys.Right
    ];

    private static readonly HashSet<Keys> RequiredKeySet = [.. RequiredKeys];
    private readonly HashSet<Keys> pressedKeys = [];

    public IReadOnlyCollection<Keys> PressedKeys => pressedKeys;
    public IReadOnlyList<Keys> MissingKeys => RequiredKeys.Where(key => !pressedKeys.Contains(key)).ToArray();
    public bool IsComplete => MissingKeys.Count == 0;
    public string? LastKey { get; private set; }

    public bool Record(Keys key)
    {
        var normalized = Normalize(key);
        if (!RequiredKeySet.Contains(normalized)) return false;
        LastKey = normalized.ToString();
        return pressedKeys.Add(normalized);
    }

    public ModuleResult CreateResult(string requestedUserResult)
    {
        if (requestedUserResult == ModuleUserResults.Skipped)
        {
            return ModuleResult.Create(
                ModuleTestTypes.Keyboard,
                ModuleMeasurementStatuses.NotExecuted,
                ModuleUserResults.Skipped,
                MeasuredValues(),
                "KEYBOARD_TEST_SKIPPED");
        }

        var isComplete = IsComplete;
        return ModuleResult.Create(
            ModuleTestTypes.Keyboard,
            isComplete ? ModuleMeasurementStatuses.Detected : ModuleMeasurementStatuses.NotDetected,
            isComplete ? ModuleUserResults.Confirmed : ModuleUserResults.ReportedIssue,
            MeasuredValues(),
            isComplete ? null : "KEY_INPUT_INCOMPLETE");
    }

    private Dictionary<string, object?> MeasuredValues() => new()
    {
        ["detectedKeyCount"] = pressedKeys.Count,
        ["targetKeyCount"] = RequiredKeys.Count,
        ["detectedKeys"] = pressedKeys.OrderBy(value => (int)value).Select(value => value.ToString()).ToArray(),
        ["missingKeys"] = MissingKeys.Select(value => value.ToString()).ToArray(),
        ["lastKey"] = LastKey,
        ["limitations"] = "Fn and OS-reserved keys are excluded from automatic judgment"
    };

    private static Keys Normalize(Keys key) => key switch
    {
        Keys.ShiftKey => Keys.LShiftKey,
        Keys.ControlKey => Keys.LControlKey,
        _ => key
    };
}
