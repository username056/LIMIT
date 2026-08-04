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
    private readonly Button issueButton = new() { Text = "이상 있음", Enabled = false };
    private readonly Label statusLabel = new() { AutoSize = true };
    private bool completedAllColors;

    public ModuleResult Result { get; private set; }

    public DisplayDiagnosticForm()
    {
        Result = CreateResult(ModuleUserResults.Skipped);

        Text = "디스플레이 검사";
        ClientSize = new Size(620, 420);
        MinimumSize = new Size(620, 420);
        StartPosition = FormStartPosition.CenterParent;
        Font = new Font("Segoe UI", 10F);
        AutoScaleMode = AutoScaleMode.Dpi;

        var screens = Screen.AllScreens
            .Select((screen, index) => new ScreenOption(index, screen))
            .ToArray();
        screenComboBox.Items.AddRange(screens.Cast<object>().ToArray());
        screenComboBox.SelectedIndex = screens.Length > 0 ? 0 : -1;
        screenComboBox.Width = 520;

        startButton.AutoSize = true;
        startButton.Click += (_, _) => RunColorTest();
        normalButton.Click += (_, _) => Finish(ModuleUserResults.Confirmed);
        issueButton.Click += (_, _) => Finish(ModuleUserResults.ReportedIssue);

        statusLabel.MaximumSize = new Size(540, 0);
        statusLabel.Text = "검정·흰색·빨강·초록·파랑 화면에서 불량 화소, 얼룩, 깜빡임을 확인하세요.";

        var decisions = new FlowLayoutPanel
        {
            AutoSize = true,
            FlowDirection = FlowDirection.LeftToRight,
            WrapContents = false
        };
        decisions.Controls.AddRange([normalButton, issueButton]);

        var layout = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.TopDown,
            WrapContents = false,
            Padding = new Padding(32)
        };
        layout.Controls.AddRange([
            new Label
            {
                AutoSize = true,
                Font = new Font(Font.FontFamily, 16F, FontStyle.Bold),
                Text = "전체화면 색상 검사"
            },
            new Label
            {
                AutoSize = true,
                MaximumSize = new Size(540, 0),
                Text = "검사할 화면을 선택한 뒤 시작하세요. 전체화면에서 클릭하거나 Space/Enter 키를 누르면 "
                    + "다음 색으로 이동하고, Esc 키를 누르면 중단합니다.",
                Margin = new Padding(3, 8, 3, 16)
            },
            new Label { AutoSize = true, Text = "검사 화면" },
            screenComboBox,
            startButton,
            statusLabel,
            new Label
            {
                AutoSize = true,
                Text = "직접 확인한 결과를 선택해 주세요.",
                Margin = new Padding(3, 22, 3, 4)
            },
            decisions
        ]);
        Controls.Add(layout);
    }

    private void RunColorTest()
    {
        if (screenComboBox.SelectedItem is not ScreenOption option)
        {
            statusLabel.Text = "검사할 화면을 선택해 주세요.";
            return;
        }

        using var colorForm = new FullScreenColorForm(option.Screen, TestColors);
        colorForm.ShowDialog(this);
        completedAllColors = colorForm.CompletedAllColors;
        normalButton.Enabled = true;
        issueButton.Enabled = true;
        statusLabel.Text = completedAllColors
            ? "5가지 색상 검사가 끝났습니다. 화면 상태를 선택해 주세요."
            : "색상 검사가 중단됐습니다. 다시 시작하거나 확인한 범위에서 결과를 선택해 주세요.";
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
        return ModuleResult.Create(
            ModuleTestTypes.Display,
            option is null ? ModuleMeasurementStatuses.NotDetected : ModuleMeasurementStatuses.Detected,
            userResult,
            new Dictionary<string, object?>
            {
                ["screenDeviceName"] = option?.Screen.DeviceName,
                ["width"] = option?.Screen.Bounds.Width,
                ["height"] = option?.Screen.Bounds.Height,
                ["colors"] = TestColors.Select(item => item.Name).ToArray(),
                ["completedAllColors"] = completedAllColors
            },
            option is null ? "DISPLAY_NOT_FOUND" : null);
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
        private int colorIndex;

        public bool CompletedAllColors { get; private set; }

        public FullScreenColorForm(Screen screen, (string Name, Color Color)[] colors)
        {
            this.colors = colors;
            FormBorderStyle = FormBorderStyle.None;
            StartPosition = FormStartPosition.Manual;
            Bounds = screen.Bounds;
            TopMost = true;
            KeyPreview = true;
            BackColor = colors[0].Color;

            Click += (_, _) => NextColor();
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
            base.OnShown(eventArgs);
        }

        protected override void OnFormClosed(FormClosedEventArgs eventArgs)
        {
            Cursor.Show();
            base.OnFormClosed(eventArgs);
        }

        private void NextColor()
        {
            colorIndex++;
            if (colorIndex >= colors.Length)
            {
                CompletedAllColors = true;
                Close();
                return;
            }

            BackColor = colors[colorIndex].Color;
        }
    }
}
