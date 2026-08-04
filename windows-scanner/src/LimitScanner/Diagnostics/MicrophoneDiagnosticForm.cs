namespace LimitScanner.Diagnostics;

public sealed class MicrophoneDiagnosticForm : Form
{
    private readonly MicrophoneInputService microphoneInputService;
    private readonly ComboBox deviceComboBox = new() { DropDownStyle = ComboBoxStyle.DropDownList };
    private readonly Button measureButton = new() { Text = "3초간 측정" };
    private readonly Button normalButton = new()
    {
        Text = "정상",
        Enabled = false,
        AutoSize = true,
        Padding = new Padding(28, 12, 28, 12),
        Font = new Font("Segoe UI", 11F)
    };
    private readonly Button issueButton = new()
    {
        Text = "이상 있음",
        Enabled = false,
        AutoSize = true,
        Padding = new Padding(28, 12, 28, 12),
        Font = new Font("Segoe UI", 11F)
    };
    private readonly Button skipButton = new()
    {
        Text = "건너뛰기",
        AutoSize = true,
        Padding = new Padding(28, 12, 28, 12),
        Font = new Font("Segoe UI", 11F)
    };
    private readonly ProgressBar levelBar = new() { Minimum = 0, Maximum = 100, Width = 520 };
    private readonly Label statusLabel = new() { AutoSize = true };
    private bool measureAttempted;
    private bool measureFailed;
    private bool permissionDenied;
    private MicrophoneMeasurement? lastMeasurement;

    public ModuleResult Result { get; private set; }

    public MicrophoneDiagnosticForm(MicrophoneInputService microphoneInputService)
    {
        this.microphoneInputService = microphoneInputService;
        Result = CreateResult(ModuleUserResults.Skipped);

        Text = "마이크 검사";
        ClientSize = new Size(800, 1020);
        MinimumSize = new Size(800, 1020);
        StartPosition = FormStartPosition.CenterParent;
        Font = new Font("Segoe UI", 10F);
        AutoScaleMode = AutoScaleMode.Dpi;

        var title = new Label
        {
            AutoSize = true,
            Font = new Font(Font.FontFamily, 16F, FontStyle.Bold),
            Text = "마이크 입력 검사"
        };
        var guide = new Label
        {
            AutoSize = true,
            MaximumSize = new Size(540, 0),
            Text = "입력 장치를 선택하고 측정 버튼을 누른 뒤 3초 동안 말하거나 소리를 내 주세요."
        };

        deviceComboBox.Width = 520;
        var devices = microphoneInputService.GetDevices();
        deviceComboBox.Items.AddRange(devices.Cast<object>().ToArray());
        deviceComboBox.SelectedIndex = devices.Count > 0 ? 0 : -1;

        measureButton.AutoSize = true;
        measureButton.Click += MeasureButton_Click;
        normalButton.Click += (_, _) => Finish(ModuleUserResults.Confirmed);
        issueButton.Click += (_, _) => Finish(ModuleUserResults.ReportedIssue);
        skipButton.Click += (_, _) => Finish(ModuleUserResults.Skipped);

        statusLabel.MaximumSize = new Size(540, 0);
        statusLabel.Text = devices.Count > 0
            ? "측정 버튼을 눌러 마이크 입력을 확인하세요."
            : "Windows에서 사용할 수 있는 입력 장치를 찾지 못했습니다.";

        var decisions = new FlowLayoutPanel
        {
            AutoSize = true,
            FlowDirection = FlowDirection.LeftToRight,
            WrapContents = false
        };
        decisions.Controls.AddRange([normalButton, issueButton, skipButton]);

        var stepLabel = DiagnosticUi.CreateStepLabel("5 / 5  마이크");

        var scrollPanel = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.TopDown,
            WrapContents = false,
            AutoScroll = true,
            Padding = new Padding(32, 32, 32, 12)
        };
        scrollPanel.Controls.AddRange([
            stepLabel,
            title,
            guide,
            new Label { AutoSize = true, Text = "입력 장치", Margin = new Padding(3, 18, 3, 4) },
            deviceComboBox,
            measureButton,
            new Label { AutoSize = true, Text = "입력 레벨", Margin = new Padding(3, 18, 3, 4) },
            levelBar,
            statusLabel
        ]);

        var decisionPanel = new Panel
        {
            Dock = DockStyle.Bottom,
            Height = 180,
            Padding = new Padding(32, 8, 32, 40)
        };
        var decisionInner = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.TopDown,
            WrapContents = false
        };
        decisionInner.Controls.AddRange([
            new Label
            {
                AutoSize = true,
                Text = "직접 확인한 결과를 선택해 주세요.",
                Margin = new Padding(0, 0, 0, 10)
            },
            decisions
        ]);
        decisionPanel.Controls.Add(decisionInner);

        Controls.Add(scrollPanel);
        Controls.Add(decisionPanel);
    }

    private async void MeasureButton_Click(object? sender, EventArgs eventArgs)
    {
        if (deviceComboBox.SelectedItem is not AudioInputDevice device)
        {
            statusLabel.Text = "입력 장치를 선택해 주세요.";
            return;
        }

        if (PrivacyConsent.IsMicrophoneAccessDenied())
        {
            measureAttempted = true;
            permissionDenied = true;
            measureFailed = false;
            lastMeasurement = null;
            levelBar.Value = 0;
            statusLabel.Text = "Windows 마이크 권한이 거부되어 있습니다. 설정에서 마이크 접근을 허용한 뒤 다시 시도하세요.";
            normalButton.Enabled = true;
            issueButton.Enabled = true;
            return;
        }

        SetControlsEnabled(false);
        measureAttempted = true;
        permissionDenied = false;
        measureFailed = false;
        statusLabel.Text = "측정하는 중입니다. 말씀하거나 소리를 내 주세요.";
        try
        {
            var measurement = await microphoneInputService.MeasureAsync(device, 3000, CancellationToken.None);
            lastMeasurement = measurement;
            levelBar.Value = Math.Clamp(measurement.PeakLevel, 0, 100);
            statusLabel.Text = measurement.SignalDetected
                ? $"입력 신호를 감지했습니다. (최고 {measurement.PeakLevel}%, 평균 {measurement.AverageLevel}%)"
                : $"측정 시간 동안 뚜렷한 입력 신호를 감지하지 못했습니다. (최고 {measurement.PeakLevel}%)";
        }
        catch (Exception)
        {
            measureFailed = true;
            lastMeasurement = null;
            statusLabel.Text = "마이크 입력을 측정하지 못했습니다.";
        }
        finally
        {
            SetControlsEnabled(true);
            normalButton.Enabled = measureAttempted;
            issueButton.Enabled = measureAttempted;
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
        var device = deviceComboBox.SelectedItem as AudioInputDevice;
        var hasDevice = device is not null;
        var status = !hasDevice
            ? ModuleMeasurementStatuses.NotDetected
            : permissionDenied
                ? ModuleMeasurementStatuses.PermissionDenied
                : lastMeasurement is not null
                    ? ModuleMeasurementStatuses.Detected
                    : ModuleMeasurementStatuses.ExecutionFailed;
        var errorCode = !hasDevice
            ? "MICROPHONE_NOT_FOUND"
            : permissionDenied
                ? "MICROPHONE_PERMISSION_DENIED"
                : lastMeasurement is not null
                    ? null
                    : measureFailed
                        ? "MICROPHONE_MEASUREMENT_FAILED"
                        : "MICROPHONE_NOT_TESTED";

        return ModuleResult.Create(
            ModuleTestTypes.Microphone,
            status,
            userResult,
            new Dictionary<string, object?>
            {
                ["deviceName"] = device?.Name,
                ["deviceNumber"] = device?.DeviceNumber,
                ["measureAttempted"] = measureAttempted,
                ["signalDetected"] = lastMeasurement?.SignalDetected,
                ["peakLevel"] = lastMeasurement?.PeakLevel,
                ["averageLevel"] = lastMeasurement?.AverageLevel
            },
            errorCode);
    }

    private void SetControlsEnabled(bool enabled)
    {
        deviceComboBox.Enabled = enabled;
        measureButton.Enabled = enabled;
        normalButton.Enabled = enabled && measureAttempted;
        issueButton.Enabled = enabled && measureAttempted;
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

    protected override void OnLoad(EventArgs eventArgs)
    {
        base.OnLoad(eventArgs);
        DiagnosticFormSizing.FitToWorkingArea(this);
    }
}
