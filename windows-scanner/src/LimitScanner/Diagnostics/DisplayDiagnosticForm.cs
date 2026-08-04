namespace LimitScanner.Diagnostics;

public sealed class DisplayDiagnosticForm : Form
{
    private static readonly (string Name, Color Color)[] TestColors =
    [
        ("BLACK", Color.Black),
        ("WHITE", Color.White),
        ("RED", Color.Red),
        ("GREEN", Color.Lime),
        ("BLUE", Color.Blue)
    ];

    private readonly ComboBox screenComboBox = new() { DropDownStyle = ComboBoxStyle.DropDownList };
    private readonly Button startButton = new() { Text = "전체화면 색상 검사 시작" };
    private readonly Button normalButton = new() { Text = "정상", Enabled = false };
    private readonly Button issueButton = new() { Text = "이상 있음" };
    private readonly Button skipButton = new() { Text = "건너뛰기" };
    private readonly Label statusLabel = DiagnosticUi.CreateStatusLabel();
    private readonly DisplayTestState testState = new();

    public ModuleResult Result { get; private set; }

    public DisplayDiagnosticForm()
    {
        Result = CreateResult(ModuleUserResults.Skipped);

        DiagnosticUi.ConfigureForm(this, "디스플레이 검사");

        var screens = Screen.AllScreens
            .Select((screen, index) => new ScreenOption(index, screen))
            .ToArray();
        screenComboBox.Items.AddRange(screens.Cast<object>().ToArray());
        screenComboBox.SelectedIndex = screens.Length > 0 ? 0 : -1;
        testState.SetDisplayAvailability(screenComboBox.SelectedItem is ScreenOption);
        screenComboBox.SelectedIndexChanged += (_, _) =>
        {
            testState.Reset(screenComboBox.SelectedItem is ScreenOption);
            normalButton.Enabled = false;
            statusLabel.Text = "선택한 화면에서 5가지 색상 검사를 시작해 주세요.";
        };
        screenComboBox.Dock = DockStyle.Fill;
        screenComboBox.Font = new Font("Segoe UI", 11F);
        screenComboBox.Margin = new Padding(0, 5, 0, 14);

        DiagnosticUi.StyleButton(startButton, ButtonKind.Primary, 270);
        startButton.Click += (_, _) => RunColorTest();
        normalButton.Click += (_, _) => Finish(ModuleUserResults.Confirmed);
        issueButton.Click += (_, _) => Finish(ModuleUserResults.ReportedIssue);
        skipButton.Click += (_, _) => Finish(ModuleUserResults.Skipped);

        statusLabel.Text = "검정·흰색·빨강·초록·파랑 화면에서 불량 화소, 얼룩, 깜빡임을 확인하세요.";

        var colorLegend = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.LeftToRight,
            WrapContents = false,
            Margin = new Padding(0, 12, 0, 0)
        };
        foreach (var testColor in TestColors)
        {
            var colorName = testColor.Name switch
            {
                "BLACK" => "검정",
                "WHITE" => "흰색",
                "RED" => "빨강",
                "GREEN" => "초록",
                _ => "파랑"
            };
            colorLegend.Controls.Add(new Label
            {
                Width = 100,
                Height = 38,
                BackColor = testColor.Color,
                ForeColor = testColor.Name is "WHITE" or "GREEN" ? Color.Black : Color.White,
                BorderStyle = BorderStyle.FixedSingle,
                Font = new Font("Segoe UI", 9F, FontStyle.Bold),
                Text = colorName,
                TextAlign = ContentAlignment.MiddleCenter,
                Margin = new Padding(0, 0, 8, 0)
            });
        }

        var cardContent = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 1,
            RowCount = 5,
            Margin = new Padding(0)
        };
        cardContent.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        cardContent.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        cardContent.RowStyles.Add(new RowStyle(SizeType.Absolute, 54F));
        cardContent.RowStyles.Add(new RowStyle(SizeType.Absolute, 52F));
        cardContent.RowStyles.Add(new RowStyle(SizeType.Percent, 100F));
        cardContent.Controls.Add(new Label
        {
            AutoSize = true,
            ForeColor = DiagnosticUi.TextMain,
            Font = new Font("Segoe UI", 10F, FontStyle.Bold),
            Text = "1. 검사할 화면 선택"
        }, 0, 0);
        cardContent.Controls.Add(screenComboBox, 0, 1);
        cardContent.Controls.Add(startButton, 0, 2);
        cardContent.Controls.Add(colorLegend, 0, 3);
        cardContent.Controls.Add(statusLabel, 0, 4);

        var layout = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 1,
            RowCount = 5,
            Padding = new Padding(32)
        };
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100F));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 68F));
        layout.Controls.Add(DiagnosticUi.CreateStepLabel("2 / 3  디스플레이"), 0, 0);
        layout.Controls.Add(DiagnosticUi.CreateTitle("전체화면 색상 검사"), 0, 1);
        layout.Controls.Add(DiagnosticUi.CreateGuide(
            "검사를 시작하면 검정 → 흰색 → 빨강 → 초록 → 파랑 화면이 자동으로 전환됩니다. "
                + "직접 넘기려면 클릭하고, 중단하려면 Esc 키를 누르세요."), 0, 2);
        layout.Controls.Add(DiagnosticUi.CreateCard(cardContent), 0, 3);
        layout.Controls.Add(DiagnosticUi.CreateDecisionBar(normalButton, issueButton, skipButton), 0, 4);
        Controls.Add(layout);
    }

    private void RunColorTest()
    {
        if (screenComboBox.SelectedItem is not ScreenOption option)
        {
            statusLabel.Text = "검사할 화면을 선택해 주세요.";
            return;
        }

        testState.Reset(hasDisplay: true);
        using var colorForm = new FullScreenColorForm(option.Screen, TestColors, testState);
        colorForm.ShowDialog(this);
        normalButton.Enabled = testState.CanConfirm;
        issueButton.Enabled = true;
        statusLabel.Text = testState.CompletedAllColors
            ? "5가지 색상 검사가 끝났습니다. 화면 상태를 선택해 주세요."
            : "색상 검사가 중단됐습니다. 정상 선택은 전체 검사를 마친 뒤 활성화됩니다.";
    }

    private void Finish(string userResult)
    {
        Result = CreateResult(userResult);
        DialogResult = DialogResult.OK;
        Close();
    }

    private ModuleResult CreateResult(string userResult)
    {
        var option = screenComboBox.SelectedItem as ScreenOption;
        testState.SetDisplayAvailability(option is not null);
        return testState.CreateResult(
            userResult,
            option?.Screen.DeviceName,
            option?.Screen.Bounds.Width,
            option?.Screen.Bounds.Height);
    }

    private sealed record ScreenOption(int Index, Screen Screen)
    {
        public override string ToString() =>
            $"디스플레이 {Index + 1} — {Screen.Bounds.Width}×{Screen.Bounds.Height}"
            + (Screen.Primary ? " (기본)" : string.Empty);
    }

    protected override void OnFormClosing(FormClosingEventArgs eventArgs)
    {
        if (DialogResult != DialogResult.OK)
        {
            Result = CreateResult(ModuleUserResults.Skipped);
        }

        base.OnFormClosing(eventArgs);
    }

    private sealed class FullScreenColorForm : Form
    {
        private readonly (string Name, Color Color)[] colors;
        private readonly DisplayTestState testState;
        private readonly System.Windows.Forms.Timer colorTimer = new() { Interval = 2500 };
        private int colorIndex;

        public FullScreenColorForm(
            Screen screen,
            (string Name, Color Color)[] colors,
            DisplayTestState testState)
        {
            this.colors = colors;
            this.testState = testState;
            FormBorderStyle = FormBorderStyle.None;
            StartPosition = FormStartPosition.Manual;
            Bounds = screen.Bounds;
            TopMost = true;
            KeyPreview = true;
            BackColor = colors[0].Color;

            Click += (_, _) => NextColor();
            colorTimer.Tick += (_, _) => NextColor();
            KeyDown += (_, eventArgs) =>
            {
                if (eventArgs.KeyCode == Keys.Escape)
                {
                    Close();
                }
                else if (eventArgs.KeyCode is Keys.Space or Keys.Enter or Keys.Right)
                {
                    NextColor();
                }
            };
        }

        protected override void OnShown(EventArgs eventArgs)
        {
            Cursor.Hide();
            testState.RecordColorShown(colors[0].Name);
            colorTimer.Start();
            base.OnShown(eventArgs);
        }

        protected override void OnFormClosed(FormClosedEventArgs eventArgs)
        {
            colorTimer.Stop();
            colorTimer.Dispose();
            Cursor.Show();
            base.OnFormClosed(eventArgs);
        }

        private void NextColor()
        {
            colorIndex++;
            if (colorIndex >= colors.Length)
            {
                testState.MarkCompleted();
                Close();
                return;
            }

            BackColor = colors[colorIndex].Color;
            testState.RecordColorShown(colors[colorIndex].Name);
        }
    }
}
