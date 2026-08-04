namespace LimitScanner.Diagnostics;

public sealed class SpeakerDiagnosticForm : Form
{
    private readonly AudioOutputService audioOutputService;
    private readonly ComboBox deviceComboBox = new() { DropDownStyle = ComboBoxStyle.DropDownList };
    private readonly Button runTestButton = new() { Text = "스피커 검사 시작" };
    private readonly Button normalButton = new() { Text = "정상", Enabled = false };
    private readonly Button issueButton = new() { Text = "이상 있음" };
    private readonly Button skipButton = new() { Text = "건너뛰기" };
    private readonly Label statusLabel = DiagnosticUi.CreateStatusLabel();
    private readonly SpeakerTestState testState = new();

    public ModuleResult Result { get; private set; }

    public SpeakerDiagnosticForm(AudioOutputService audioOutputService)
    {
        this.audioOutputService = audioOutputService;
        Result = CreateResult(ModuleUserResults.Skipped);

        DiagnosticUi.ConfigureForm(this, "스피커 검사");

        deviceComboBox.Dock = DockStyle.Fill;
        deviceComboBox.Font = new Font("Segoe UI", 11F);
        deviceComboBox.Margin = new Padding(0, 5, 0, 16);
        var devices = audioOutputService.GetDevices();
        deviceComboBox.Items.AddRange(devices.Cast<object>().ToArray());
        deviceComboBox.SelectedIndex = devices.Count > 0 ? 0 : -1;
        testState.SetDeviceAvailability(deviceComboBox.SelectedItem is AudioOutputDevice);
        deviceComboBox.SelectedIndexChanged += (_, _) =>
        {
            testState.Reset(deviceComboBox.SelectedItem is AudioOutputDevice);
            normalButton.Enabled = false;
            statusLabel.Text = "선택한 출력 장치에서 스피커 검사를 시작해 주세요.";
        };

        DiagnosticUi.StyleButton(runTestButton, ButtonKind.Primary, 220);
        runTestButton.Margin = new Padding(0, 8, 0, 0);
        runTestButton.Click += async (_, _) => await RunSpeakerTestAsync();
        normalButton.Click += (_, _) => Finish(ModuleUserResults.Confirmed);
        issueButton.Click += (_, _) => Finish(ModuleUserResults.ReportedIssue);
        skipButton.Click += (_, _) => Finish(ModuleUserResults.Skipped);

        statusLabel.Text = devices.Count > 0
            ? "검사를 시작하면 전체 → 왼쪽 → 오른쪽 순서로 테스트음이 재생됩니다."
            : "Windows에서 사용할 수 있는 출력 장치를 찾지 못했습니다.";
        runTestButton.Enabled = devices.Count > 0;
        issueButton.Enabled = true;

        var cardContent = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 1,
            RowCount = 5,
            Margin = new Padding(0)
        };
        cardContent.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        cardContent.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        cardContent.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        cardContent.RowStyles.Add(new RowStyle(SizeType.Absolute, 56F));
        cardContent.RowStyles.Add(new RowStyle(SizeType.Percent, 100F));
        cardContent.Controls.Add(new Label
        {
            AutoSize = true,
            ForeColor = DiagnosticUi.TextMain,
            Font = new Font("Segoe UI", 10F, FontStyle.Bold),
            Text = "1. 출력 장치 선택"
        }, 0, 0);
        cardContent.Controls.Add(deviceComboBox, 0, 1);
        cardContent.Controls.Add(new Label
        {
            AutoSize = true,
            ForeColor = DiagnosticUi.TextMain,
            Font = new Font("Segoe UI", 10F, FontStyle.Bold),
            Text = "2. 테스트음 재생"
        }, 0, 2);
        cardContent.Controls.Add(runTestButton, 0, 3);
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
        layout.Controls.Add(DiagnosticUi.CreateStepLabel("1 / 3  스피커"), 0, 0);
        layout.Controls.Add(DiagnosticUi.CreateTitle("스피커·좌우 채널 검사"), 0, 1);
        layout.Controls.Add(DiagnosticUi.CreateGuide(
            "출력 장치를 선택하고 검사 시작을 한 번 누르세요. 전체 → 왼쪽 → 오른쪽 순서로 "
                + "자동 재생되며, 재생이 끝나면 정상 여부만 선택하면 됩니다."), 0, 2);
        layout.Controls.Add(DiagnosticUi.CreateCard(cardContent), 0, 3);
        layout.Controls.Add(DiagnosticUi.CreateDecisionBar(normalButton, issueButton, skipButton), 0, 4);
        Controls.Add(layout);
    }

    private async Task RunSpeakerTestAsync()
    {
        if (deviceComboBox.SelectedItem is not AudioOutputDevice device)
        {
            statusLabel.Text = "출력 장치를 선택해 주세요.";
            return;
        }

        SetPlaybackEnabled(false);
        testState.BeginPlayback();
        var channels = new[]
        {
            (Channel: AudioChannel.Both, Name: "전체"),
            (Channel: AudioChannel.Left, Name: "왼쪽"),
            (Channel: AudioChannel.Right, Name: "오른쪽")
        };

        foreach (var item in channels)
        {
            statusLabel.Text = $"{item.Name} 채널 테스트음을 재생하고 있습니다.";
            statusLabel.Refresh();
            try
            {
                await audioOutputService.PlayTestToneAsync(
                    device,
                    item.Channel,
                    CancellationToken.None);
                testState.RecordPlaybackSuccess(item.Channel);
            }
            catch (Exception)
            {
                testState.RecordPlaybackFailure();
            }

            await Task.Delay(450);
        }

        statusLabel.Text = testState.PlaybackFailed
            ? "일부 테스트음을 재생하지 못했습니다. 들은 결과를 기준으로 선택해 주세요."
            : "전체·왼쪽·오른쪽 테스트가 끝났습니다. 들은 결과를 선택해 주세요.";
        SetPlaybackEnabled(true);
    }

    private void Finish(string userResult)
    {
        Result = CreateResult(userResult);
        DialogResult = DialogResult.OK;
        Close();
    }

    private ModuleResult CreateResult(string userResult)
    {
        var device = deviceComboBox.SelectedItem as AudioOutputDevice;
        testState.SetDeviceAvailability(device is not null);
        return testState.CreateResult(userResult, device?.Name, device?.DeviceNumber);
    }

    private void SetPlaybackEnabled(bool enabled)
    {
        deviceComboBox.Enabled = enabled;
        runTestButton.Enabled = enabled;
        normalButton.Enabled = enabled && testState.CanConfirm;
        issueButton.Enabled = enabled;
        skipButton.Enabled = enabled;
    }

    protected override void OnFormClosing(FormClosingEventArgs eventArgs)
    {
        if (DialogResult != DialogResult.OK)
        {
            Result = CreateResult(ModuleUserResults.Skipped);
        }

        base.OnFormClosing(eventArgs);
    }
}
