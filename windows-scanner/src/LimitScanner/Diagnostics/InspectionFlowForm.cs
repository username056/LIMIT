namespace LimitScanner.Diagnostics;

public sealed class InspectionFlowForm : Form
{
    private readonly IReadOnlyList<InspectionModule> modules;
    private readonly Dictionary<string, ModuleResult> results = [];
    private readonly Label progressLabel = new();
    private readonly Label moduleTitle = new();
    private readonly Label resultLabel = new();
    private readonly ProgressBar progressBar = new();
    private readonly Button previousButton = new() { Text = "이전" };
    private readonly Button runButton = new() { Text = "검사 실행" };
    private readonly Button skipButton = new() { Text = "건너뛰기" };
    private readonly Button nextButton = new() { Text = "다음" };
    private readonly Button finishButton = new() { Text = "결과 전송 단계로" };
    private int index;
    private bool allowClose;

    public IReadOnlyList<ModuleResult> Results => modules.Select(module =>
        results.TryGetValue(module.TestType, out var result) ? result : CreateSkipped(module.TestType)).ToArray();

    public InspectionFlowForm(IReadOnlyList<InspectionModule> modules)
    {
        this.modules = modules;
        DiagnosticUi.ConfigureForm(this, "장치 검사");
        ClientSize = new Size(1040, 680);
        MinimumSize = new Size(920, 620);

        progressLabel.AutoSize = true;
        progressBar.Dock = DockStyle.Top;
        progressBar.Maximum = modules.Count;
        moduleTitle.AutoSize = true;
        moduleTitle.Font = new Font("Segoe UI", 24F, FontStyle.Bold);
        resultLabel.Dock = DockStyle.Fill;
        resultLabel.TextAlign = ContentAlignment.MiddleCenter;
        resultLabel.Font = new Font("Segoe UI", 13F);

        DiagnosticUi.StyleButton(previousButton, ButtonKind.Secondary, 100);
        DiagnosticUi.StyleButton(runButton, ButtonKind.Primary, 140);
        DiagnosticUi.StyleButton(skipButton, ButtonKind.Secondary, 110);
        DiagnosticUi.StyleButton(nextButton, ButtonKind.Secondary, 100);
        DiagnosticUi.StyleButton(finishButton, ButtonKind.Success, 180);
        previousButton.Click += (_, _) => MoveSelection(-1);
        nextButton.Click += (_, _) => MoveSelection(1);
        runButton.Click += (_, _) => RunCurrent();
        skipButton.Click += (_, _) => SkipCurrent();
        finishButton.Click += (_, _) => Finish();

        var navigation = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            AutoSize = true,
            AutoScroll = true,
            WrapContents = true,
            FlowDirection = FlowDirection.LeftToRight,
            Padding = new Padding(0, 4, 0, 4)
        };
        navigation.Controls.AddRange([previousButton, runButton, skipButton, nextButton, finishButton]);
        var layout = new TableLayoutPanel { Dock = DockStyle.Fill, Padding = new Padding(32), RowCount = 5, ColumnCount = 1 };
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 32));
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        layout.Controls.Add(progressLabel, 0, 0);
        layout.Controls.Add(progressBar, 0, 1);
        layout.Controls.Add(moduleTitle, 0, 2);
        layout.Controls.Add(resultLabel, 0, 3);
        layout.Controls.Add(navigation, 0, 4);
        Controls.Add(layout);
        UpdateView();
    }

    private void RunCurrent()
    {
        var module = modules[index];
        results[module.TestType] = module.Run(this);
        ScheduleViewUpdate();
    }

    private void SkipCurrent()
    {
        results[modules[index].TestType] = CreateSkipped(modules[index].TestType);
        if (index < modules.Count - 1) index++;
        UpdateView();
    }

    private void MoveSelection(int offset)
    {
        index = Math.Clamp(index + offset, 0, modules.Count - 1);
        UpdateView();
    }

    private void UpdateView()
    {
        var module = modules[index];
        var completed = results.Count;
        progressLabel.Text = $"진행률 {completed} / {modules.Count}   ·   현재 {index + 1} / {modules.Count}";
        progressBar.Value = completed;
        moduleTitle.Text = module.Name;
        if (results.TryGetValue(module.TestType, out var result))
        {
            resultLabel.Text = result.UserResult == ModuleUserResults.Skipped
                ? "건너뛴 항목입니다. 필요하면 다시 검사할 수 있습니다."
                : $"검사 완료 · {result.UserResult}\r\n‘다시 검사’를 누르면 새 시도 UUID로 결과가 교체됩니다.";
            runButton.Text = "다시 검사";
        }
        else
        {
            resultLabel.Text = "아직 실행하지 않은 검사입니다.";
            runButton.Text = "검사 실행";
        }
        previousButton.Enabled = index > 0;
        nextButton.Enabled = index < modules.Count - 1;
        finishButton.Enabled = index == modules.Count - 1;
    }

    private void ScheduleViewUpdate()
    {
        // ShowDialog returns while the click that closed the child dialog is still
        // being dispatched. Updating on the next message-loop turn ensures the
        // owner has been re-enabled before progress and button state are painted.
        BeginInvoke(() =>
        {
            var keepPreviousProgress = results.Count == modules.Count;
            var progressText = progressLabel.Text;
            var progressValue = progressBar.Value;
            UpdateView();
            if (keepPreviousProgress)
            {
                progressLabel.Text = progressText;
                progressBar.Value = progressValue;
            }
            PerformLayout();
            Refresh();
        });
    }

    private void Finish()
    {
        allowClose = true;
        DialogResult = DialogResult.OK;
        Close();
    }

    protected override void OnFormClosing(FormClosingEventArgs eventArgs)
    {
        if (!allowClose && results.Count < modules.Count)
        {
            var answer = MessageBox.Show(this,
                $"검사하지 않은 항목이 {modules.Count - results.Count}개 있습니다. 중간 종료하면 해당 항목은 건너뛰기로 처리됩니다. 종료할까요?",
                "검사 중간 종료", MessageBoxButtons.YesNo, MessageBoxIcon.Warning);
            if (answer != DialogResult.Yes)
            {
                eventArgs.Cancel = true;
                return;
            }
        }
        base.OnFormClosing(eventArgs);
    }

    private static ModuleResult CreateSkipped(string testType) => ModuleResult.Create(
        testType,
        ModuleMeasurementStatuses.NotExecuted,
        ModuleUserResults.Skipped,
        new Dictionary<string, object?>(),
        "USER_SKIPPED");
}
