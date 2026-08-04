namespace LimitScanner.Diagnostics;

public sealed class SpeakerDiagnosticForm : Form
{
    private readonly AudioOutputService audioOutputService;
    private readonly ComboBox deviceComboBox = new() { DropDownStyle = ComboBoxStyle.DropDownList };
    private readonly Button bothButton = new() { Text = "전체 테스트음" };
    private readonly Button leftButton = new() { Text = "왼쪽 채널" };
    private readonly Button rightButton = new() { Text = "오른쪽 채널" };
    private readonly Button normalButton = new() { Text = "정상" };
    private readonly Button issueButton = new() { Text = "이상 있음" };
    private readonly Label statusLabel = new() { AutoSize = true };
    private readonly HashSet<string> testedChannels = [];
    private bool playbackAttempted;
    private bool playbackFailed;

    public ModuleResult Result { get; private set; }

    public SpeakerDiagnosticForm(AudioOutputService audioOutputService)
    {
        this.audioOutputService = audioOutputService;
        Result = CreateResult(ModuleUserResults.Skipped);

        Text = "스피커 검사";
        ClientSize = new Size(620, 390);
        MinimumSize = new Size(620, 390);
        StartPosition = FormStartPosition.CenterParent;
        Font = new Font("Segoe UI", 10F);
        AutoScaleMode = AutoScaleMode.Dpi;

        var title = new Label
        {
            AutoSize = true,
            Font = new Font(Font.FontFamily, 16F, FontStyle.Bold),
            Text = "스피커·좌우 채널 검사"
        };
        var guide = new Label
        {
            AutoSize = true,
            MaximumSize = new Size(540, 0),
            Text = "출력 장치를 선택하고 테스트음을 재생하세요. 전체음을 먼저 확인한 뒤 "
                + "가능하면 왼쪽과 오른쪽 채널도 각각 확인해 주세요."
        };

        deviceComboBox.Width = 520;
        var devices = audioOutputService.GetDevices();
        deviceComboBox.Items.AddRange(devices.Cast<object>().ToArray());
        deviceComboBox.SelectedIndex = devices.Count > 0 ? 0 : -1;

        var soundButtons = new FlowLayoutPanel
        {
            AutoSize = true,
            FlowDirection = FlowDirection.LeftToRight,
            WrapContents = false
        };
        soundButtons.Controls.AddRange([bothButton, leftButton, rightButton]);

        var decisionButtons = new FlowLayoutPanel
        {
            AutoSize = true,
            FlowDirection = FlowDirection.LeftToRight,
            WrapContents = false
        };
        decisionButtons.Controls.AddRange([normalButton, issueButton]);

        bothButton.Click += async (_, _) => await PlayAsync(AudioChannel.Both, "전체");
        leftButton.Click += async (_, _) => await PlayAsync(AudioChannel.Left, "왼쪽");
        rightButton.Click += async (_, _) => await PlayAsync(AudioChannel.Right, "오른쪽");
        normalButton.Click += (_, _) => Finish(ModuleUserResults.Confirmed);
        issueButton.Click += (_, _) => Finish(ModuleUserResults.ReportedIssue);

        statusLabel.MaximumSize = new Size(540, 0);
        statusLabel.Text = devices.Count > 0
            ? "테스트음을 재생해 주세요. 음량이 너무 크지 않은지 먼저 확인하세요."
            : "Windows에서 사용할 수 있는 출력 장치를 찾지 못했습니다.";

        var layout = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.TopDown,
            WrapContents = false,
            AutoScroll = true,
            Padding = new Padding(32)
        };
        layout.Controls.AddRange([
            title,
            guide,
            new Label { AutoSize = true, Text = "출력 장치", Margin = new Padding(3, 18, 3, 4) },
            deviceComboBox,
            soundButtons,
            statusLabel,
            new Label
            {
                AutoSize = true,
                Text = "직접 들은 결과를 선택해 주세요.",
                Margin = new Padding(3, 22, 3, 4)
            },
            decisionButtons
        ]);
        Controls.Add(layout);
    }

    private async Task PlayAsync(AudioChannel channel, string channelName)
    {
        if (deviceComboBox.SelectedItem is not AudioOutputDevice device)
        {
            statusLabel.Text = "출력 장치를 선택해 주세요.";
            return;
        }

        SetPlaybackEnabled(false);
        playbackAttempted = true;
        statusLabel.Text = $"{channelName} 테스트음을 재생하고 있습니다.";
        try
        {
            await audioOutputService.PlayTestToneAsync(device, channel, CancellationToken.None);
            testedChannels.Add(channel.ToString().ToUpperInvariant());
            statusLabel.Text = $"{channelName} 테스트음 재생이 끝났습니다.";
        }
        catch (Exception)
        {
            playbackFailed = true;
            statusLabel.Text = "선택한 장치에서 테스트음을 재생하지 못했습니다.";
        }
        finally
        {
            SetPlaybackEnabled(true);
        }
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
        var hasDevice = device is not null;
        var playbackOnlyFailed = playbackAttempted && testedChannels.Count == 0 && playbackFailed;
        return ModuleResult.Create(
            ModuleTestTypes.Speaker,
            !hasDevice
                ? ModuleMeasurementStatuses.NotDetected
                : playbackOnlyFailed
                    ? ModuleMeasurementStatuses.ExecutionFailed
                    : ModuleMeasurementStatuses.Detected,
            userResult,
            new Dictionary<string, object?>
            {
                ["deviceName"] = device?.Name,
                ["deviceNumber"] = device?.DeviceNumber,
                ["testedChannels"] = testedChannels.OrderBy(value => value).ToArray(),
                ["playbackAttempted"] = playbackAttempted,
                ["playbackFailureDetected"] = playbackFailed
            },
            !hasDevice
                ? "OUTPUT_DEVICE_NOT_FOUND"
                : playbackOnlyFailed
                    ? "AUDIO_PLAYBACK_FAILED"
                    : null);
    }

    private void SetPlaybackEnabled(bool enabled)
    {
        deviceComboBox.Enabled = enabled;
        bothButton.Enabled = enabled;
        leftButton.Enabled = enabled;
        rightButton.Enabled = enabled;
        normalButton.Enabled = enabled;
        issueButton.Enabled = enabled;
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
