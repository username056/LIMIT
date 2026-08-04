namespace LimitScanner.Diagnostics;

public sealed class KeyboardDiagnosticForm : Form
{
    private readonly KeyboardTestState state = new();
    private readonly Label lastKeyLabel = new();
    private readonly Label countLabel = new();
    private readonly FlowLayoutPanel keyPanel = new();
    private readonly Button normalButton = new() { Text = "정상", Enabled = false };

    public ModuleResult Result { get; private set; }

    public KeyboardDiagnosticForm()
    {
        Result = state.CreateResult(ModuleUserResults.Skipped);
        DiagnosticUi.ConfigureForm(this, "키보드 입력 검사");
        KeyPreview = true;

        lastKeyLabel.Dock = DockStyle.Fill;
        lastKeyLabel.TextAlign = ContentAlignment.MiddleCenter;
        lastKeyLabel.Font = new Font("Segoe UI", 28F, FontStyle.Bold);
        lastKeyLabel.Text = "키를 눌러 주세요";
        countLabel.AutoSize = true;
        countLabel.Text = "감지된 키: 0개";
        keyPanel.Dock = DockStyle.Fill;
        keyPanel.AutoScroll = true;
        keyPanel.WrapContents = true;

        var card = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 3, ColumnCount = 1 };
        card.RowStyles.Add(new RowStyle(SizeType.Absolute, 130));
        card.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        card.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        card.Controls.Add(lastKeyLabel, 0, 0);
        card.Controls.Add(countLabel, 0, 1);
        card.Controls.Add(keyPanel, 0, 2);

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
        layout.Controls.Add(DiagnosticUi.CreateStepLabel("키보드"), 0, 0);
        layout.Controls.Add(DiagnosticUi.CreateTitle("키보드 입력 감지"), 0, 1);
        layout.Controls.Add(DiagnosticUi.CreateGuide("감지 가능한 모든 키를 눌러 확인하세요. Fn 및 Windows가 가로채는 OS 예약 키는 감지를 보장하지 않습니다."), 0, 2);
        layout.Controls.Add(DiagnosticUi.CreateCard(card), 0, 3);
        layout.Controls.Add(DiagnosticUi.CreateDecisionBar(normalButton, issueButton, skipButton), 0, 4);
        Controls.Add(layout);
    }

    protected override bool ProcessCmdKey(ref Message msg, Keys keyData)
    {
        var key = keyData & Keys.KeyCode;
        if (key != Keys.None)
        {
            state.Record(key);
            lastKeyLabel.Text = key.ToString();
            countLabel.Text = $"감지된 키: {state.PressedKeys.Count}개";
            normalButton.Enabled = true;
            if (!keyPanel.Controls.Cast<Control>().Any(control => control.Tag is Keys value && value == key))
            {
                keyPanel.Controls.Add(new Label
                {
                    AutoSize = true,
                    Text = key.ToString(),
                    Tag = key,
                    BackColor = Color.FromArgb(219, 234, 254),
                    ForeColor = Color.FromArgb(30, 64, 175),
                    Padding = new Padding(8),
                    Margin = new Padding(4)
                });
            }
            return true;
        }
        return base.ProcessCmdKey(ref msg, keyData);
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
