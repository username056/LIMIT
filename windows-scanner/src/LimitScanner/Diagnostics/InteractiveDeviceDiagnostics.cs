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

    // 스피커·디스플레이·충전·카메라·마이크는 실동작 자동/반자동 점검 대상에서 빠졌다(카메라·마이크·
    // 스피커는 판매자 확인 스펙으로만 남고, 디스플레이·충전은 영상 증빙으로만 확인한다).
    // ModuleTestTypes와 각 진단 폼은 과거 이력 호환을 위해 남겨 두되, 활성 플로우에는 올리지 않는다.
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
