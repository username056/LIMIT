namespace LimitScanner.Diagnostics;

public sealed class KeyboardDiagnosticForm : Form
{
    private readonly KeyboardTestState state = new();
    private readonly Label lastKeyLabel = new();
    private readonly Label countLabel = new();
    private readonly TableLayoutPanel keyPanel = new();
    private readonly Dictionary<Keys, Label> keyLabels = [];
    private readonly Button completeButton = new() { Text = "완료" };

    private static readonly IReadOnlyList<IReadOnlyList<Keys>> VisualKeyRows =
    [
        [Keys.Oemtilde, Keys.D1, Keys.D2, Keys.D3, Keys.D4, Keys.D5, Keys.D6, Keys.D7, Keys.D8, Keys.D9, Keys.D0, Keys.OemMinus, Keys.Oemplus, Keys.Back],
        [Keys.Tab, Keys.Q, Keys.W, Keys.E, Keys.R, Keys.T, Keys.Y, Keys.U, Keys.I, Keys.O, Keys.P, Keys.OemOpenBrackets, Keys.OemCloseBrackets, Keys.Oem5],
        [Keys.CapsLock, Keys.A, Keys.S, Keys.D, Keys.F, Keys.G, Keys.H, Keys.J, Keys.K, Keys.L, Keys.Oem1, Keys.Oem7, Keys.Enter],
        [Keys.LShiftKey, Keys.Z, Keys.X, Keys.C, Keys.V, Keys.B, Keys.N, Keys.M, Keys.Oemcomma, Keys.OemPeriod, Keys.OemQuestion, Keys.RShiftKey],
        [Keys.LControlKey, Keys.LWin, Keys.LMenu, Keys.HanjaMode, Keys.Space, Keys.HangulMode, Keys.RMenu, Keys.Left, Keys.Up, Keys.Down, Keys.Right]
    ];

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
        countLabel.Text = $"감지된 키: 0 / {KeyboardTestState.RequiredKeys.Count}";
        keyPanel.Dock = DockStyle.Fill;
        keyPanel.AutoScroll = true;
        keyPanel.ColumnCount = 1;
        keyPanel.RowCount = VisualKeyRows.Count;
        keyPanel.Padding = new Padding(8);
        foreach (var rowKeys in VisualKeyRows)
        {
            keyPanel.RowStyles.Add(new RowStyle(SizeType.Absolute, 58));
            var row = new FlowLayoutPanel
            {
                Dock = DockStyle.Fill,
                WrapContents = false,
                FlowDirection = FlowDirection.LeftToRight,
                Margin = new Padding(0)
            };
            foreach (var key in rowKeys)
            {
                var isRequired = KeyboardTestState.RequiredKeys.Contains(key);
                var label = new Label
                {
                    AutoSize = false,
                    Size = new Size(KeyWidth(key), 50),
                    Text = KeyLabel(key),
                    Tag = key,
                    TextAlign = ContentAlignment.MiddleCenter,
                    Font = new Font("Segoe UI", 11F, FontStyle.Regular),
                    BackColor = isRequired ? Color.White : Color.FromArgb(241, 245, 249),
                    ForeColor = isRequired ? Color.FromArgb(71, 85, 105) : Color.FromArgb(148, 163, 184),
                    BorderStyle = BorderStyle.FixedSingle,
                    Margin = new Padding(3)
                };
                if (isRequired) keyLabels[key] = label;
                row.Controls.Add(label);
            }
            keyPanel.Controls.Add(row);
        }

        var card = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 3, ColumnCount = 1 };
        card.RowStyles.Add(new RowStyle(SizeType.Absolute, 130));
        card.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        card.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        card.Controls.Add(lastKeyLabel, 0, 0);
        card.Controls.Add(countLabel, 0, 1);
        card.Controls.Add(keyPanel, 0, 2);

        var resultGuideButton = new Button { Text = "누락 키는 부분 실패", Enabled = false };
        var skipButton = new Button { Text = "건너뛰기" };
        completeButton.Click += (_, _) => Finish(ModuleUserResults.Confirmed);
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
        layout.Controls.Add(DiagnosticUi.CreateDecisionBar(completeButton, resultGuideButton, skipButton), 0, 4);
        Controls.Add(layout);
    }

    protected override bool ProcessCmdKey(ref Message msg, Keys keyData)
    {
        var key = keyData & Keys.KeyCode;
        if (key != Keys.None)
        {
            if (state.Record(key))
            {
                lastKeyLabel.Text = state.LastKey;
                countLabel.Text = $"감지된 키: {state.PressedKeys.Count} / {KeyboardTestState.RequiredKeys.Count}";
                var normalizedKey = key switch
                {
                    Keys.ShiftKey => Keys.LShiftKey,
                    Keys.ControlKey => Keys.LControlKey,
                    _ => key
                };
                keyLabels[normalizedKey].BackColor = Color.FromArgb(99, 102, 241);
                keyLabels[normalizedKey].ForeColor = Color.White;
                if (state.IsComplete)
                {
                    BeginInvoke(() => Finish(ModuleUserResults.Confirmed));
                }
                return true;
            }
        }
        return base.ProcessCmdKey(ref msg, keyData);
    }

    private void Finish(string userResult)
    {
        Result = state.CreateResult(userResult);
        DialogResult = DialogResult.OK;
        Close();
    }

    private static int KeyWidth(Keys key) => key switch
    {
        Keys.Back => 78,
        Keys.Tab => 70,
        Keys.CapsLock => 88,
        Keys.Enter => 82,
        Keys.LShiftKey or Keys.RShiftKey => 104,
        Keys.LControlKey => 68,
        Keys.Space => 220,
        _ => 42
    };

    private static string KeyLabel(Keys key) => key switch
    {
        Keys.Oemtilde => "`",
        >= Keys.D0 and <= Keys.D9 => ((int)key - (int)Keys.D0).ToString(),
        Keys.OemMinus => "-",
        Keys.Oemplus => "=",
        Keys.Back => "⌫",
        Keys.OemOpenBrackets => "[",
        Keys.OemCloseBrackets => "]",
        Keys.Oem5 => "\\",
        Keys.CapsLock => "Caps Lock",
        Keys.Oem1 => ";",
        Keys.Oem7 => "'",
        Keys.LShiftKey or Keys.RShiftKey => "Shift",
        Keys.Oemcomma => ",",
        Keys.OemPeriod => ".",
        Keys.OemQuestion => "/",
        Keys.LControlKey => "Ctrl",
        Keys.LWin => "Win",
        Keys.LMenu or Keys.RMenu => "Alt",
        Keys.HanjaMode => "한자",
        Keys.HangulMode => "한/영",
        Keys.Space => "Space",
        Keys.Left => "←",
        Keys.Up => "↑",
        Keys.Down => "↓",
        Keys.Right => "→",
        _ => key.ToString()
    };

    protected override void OnFormClosing(FormClosingEventArgs eventArgs)
    {
        if (DialogResult != DialogResult.OK) Result = state.CreateResult(ModuleUserResults.Skipped);
        base.OnFormClosing(eventArgs);
    }
}
