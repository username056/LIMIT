namespace LimitScanner.Diagnostics;

public sealed class InteractiveDeviceDiagnostics
{
    public IReadOnlyList<ModuleResult> Run(IWin32Window owner)
    {
        using var form = new InspectionFlowForm(CreateModules());
        form.ShowDialog(owner);
        return form.Results;
    }

    public ModuleResult RunModule(string testType, IWin32Window owner)
    {
        var module = CreateModules().SingleOrDefault(candidate => candidate.TestType == testType)
            ?? throw new ArgumentOutOfRangeException(nameof(testType), testType, "지원하지 않는 검사 유형입니다.");
        return module.Run(owner);
    }

    private IReadOnlyList<InspectionModule> CreateModules() =>
    [
        new("키보드", ModuleTestTypes.Keyboard, owner => RunDialog(owner, new KeyboardDiagnosticForm())),
        new("포인터", ModuleTestTypes.Pointer, owner => RunDialog(owner, new PointerDiagnosticForm()))
    ];

    private static ModuleResult RunDialog(IWin32Window owner, Form form)
    {
        using (form)
        {
            form.ShowDialog(owner);
            return (ModuleResult)form.GetType().GetProperty("Result")!.GetValue(form)!;
        }
    }
}

public sealed record InspectionModule(string Name, string TestType, Func<IWin32Window, ModuleResult> Run);
