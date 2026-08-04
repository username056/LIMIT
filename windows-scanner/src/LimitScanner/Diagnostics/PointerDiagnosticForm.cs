namespace LimitScanner.Diagnostics;

public sealed class PointerDiagnosticForm : Form
{
    private readonly PointerTestState state = new();
    private readonly Label statusLabel = new();
    private readonly Panel testArea = new();
    private readonly Button normalButton = new() { Text = "정상", Enabled = false };

    public ModuleResult Result { get; private set; }

    public PointerDiagnosticForm()
    {
        Result = state.CreateResult(ModuleUserResults.Skipped);
        DiagnosticUi.ConfigureForm(this, "포인터 검사");

        testArea.Dock = DockStyle.Fill;
        testArea.BackColor = Color.White;
        testArea.Cursor = Cursors.Cross;
        statusLabel.Dock = DockStyle.Bottom;
        statusLabel.Height = 80;
        statusLabel.Padding = new Padding(12);
        testArea.Controls.Add(statusLabel);
        testArea.MouseMove += (_, _) => { state.RecordMove(); UpdateStatus(); };
        testArea.MouseDown += (_, e) => { state.RecordClick(e.Button); UpdateStatus(); };
        testArea.MouseWheel += (_, _) => { state.RecordScroll(); UpdateStatus(); };
        testArea.MouseEnter += (_, _) => testArea.Focus();

        var issueButton = new Button { Text = "이상 있음" };
        var skipButton = new Button { Text = "건너뛰기" };
        normalButton.Click += (_, _) => Finish(ModuleUserResults.Confirmed);
        issueButton.Click += (_, _) => Finish(ModuleUserResults.ReportedIssue);
        skipButton.Click += (_, _) => Finish(ModuleUserResults.Skipped);

        var layout = new TableLayoutPanel { Dock = DockStyle.Fill, Padding = new Padding(32), RowCount = 5, ColumnCount = 1 };
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 90));
        layout.Controls.Add(DiagnosticUi.CreateStepLabel("포인터"), 0, 0);
        layout.Controls.Add(DiagnosticUi.CreateTitle("이동·좌우 클릭·스크롤 검사"), 0, 1);
        layout.Controls.Add(DiagnosticUi.CreateGuide("아래 영역에서 포인터를 움직이고 왼쪽 클릭, 오른쪽 클릭, 휠 스크롤을 각각 수행하세요."), 0, 2);
        layout.Controls.Add(DiagnosticUi.CreateCard(testArea), 0, 3);
        layout.Controls.Add(DiagnosticUi.CreateDecisionBar(normalButton, issueButton, skipButton), 0, 4);
        Controls.Add(layout);
        UpdateStatus();
    }

    private void UpdateStatus()
    {
        statusLabel.Text = $"이동 {(state.MoveCount > 0 ? "✓" : "○")}   왼쪽 클릭 {(state.LeftClickCount > 0 ? "✓" : "○")}   오른쪽 클릭 {(state.RightClickCount > 0 ? "✓" : "○")}   스크롤 {(state.ScrollEventCount > 0 ? "✓" : "○")}";
        normalButton.Enabled = state.HasRequiredInput;
    }

    private void Finish(string userResult)
    {
        Result = state.CreateResult(userResult);
        DialogResult = DialogResult.OK;
        Close();
    }

    protected override void OnFormClosing(FormClosingEventArgs eventArgs)
    {
        if (DialogResult != DialogResult.OK) Result = state.CreateResult(ModuleUserResults.Skipped);
        base.OnFormClosing(eventArgs);
    }
}
