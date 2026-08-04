namespace LimitScanner.Diagnostics;

public sealed class PointerTestState
{
    public int MoveCount { get; private set; }
    public int LeftClickCount { get; private set; }
    public int RightClickCount { get; private set; }
    public int ScrollEventCount { get; private set; }

    public bool HasRequiredInput => MoveCount > 0 && LeftClickCount > 0 && RightClickCount > 0 && ScrollEventCount > 0;

    public void RecordMove() => MoveCount++;

    public void RecordClick(MouseButtons button)
    {
        if (button == MouseButtons.Left) LeftClickCount++;
        if (button == MouseButtons.Right) RightClickCount++;
    }

    public void RecordScroll() => ScrollEventCount++;

    public ModuleResult CreateResult(string userResult) => ModuleResult.Create(
        ModuleTestTypes.Pointer,
        HasRequiredInput ? ModuleMeasurementStatuses.Detected : ModuleMeasurementStatuses.NotDetected,
        userResult,
        new Dictionary<string, object?>
        {
            ["moveCount"] = MoveCount,
            ["leftClickCount"] = LeftClickCount,
            ["rightClickCount"] = RightClickCount,
            ["scrollEventCount"] = ScrollEventCount
        },
        HasRequiredInput ? null : "POINTER_INPUT_INCOMPLETE");
}
