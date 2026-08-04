namespace LimitScanner.Diagnostics;

public sealed class CameraDiagnosticForm : Form
{
    private readonly CameraCaptureService cameraCaptureService;
    private readonly ComboBox deviceComboBox = new() { DropDownStyle = ComboBoxStyle.DropDownList };
    private readonly Button captureButton = new() { Text = "촬영해서 확인", Enabled = false };
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
    private readonly PictureBox previewBox = new()
    {
        Width = 480,
        Height = 320,
        BorderStyle = BorderStyle.FixedSingle,
        SizeMode = PictureBoxSizeMode.Zoom,
        BackColor = Color.Black
    };
    private readonly Label statusLabel = new() { AutoSize = true };
    private Bitmap? lastFrame;
    private bool captureAttempted;
    private bool frameReceived;
    private bool permissionDenied;
    private bool captureFailed;
    private int capturedWidth;
    private int capturedHeight;

    public ModuleResult Result { get; private set; }

    public CameraDiagnosticForm(CameraCaptureService cameraCaptureService)
    {
        this.cameraCaptureService = cameraCaptureService;
        Result = CreateResult(ModuleUserResults.Skipped);

        Text = "카메라 검사";
        ClientSize = new Size(760, 960);
        MinimumSize = new Size(760, 960);
        StartPosition = FormStartPosition.CenterParent;
        Font = new Font("Segoe UI", 10F);
        AutoScaleMode = AutoScaleMode.Dpi;

        var title = new Label
        {
            AutoSize = true,
            Font = new Font(Font.FontFamily, 16F, FontStyle.Bold),
            Text = "카메라 검사"
        };
        var guide = new Label
        {
            AutoSize = true,
            MaximumSize = new Size(560, 0),
            Text = "카메라를 선택하고 촬영 버튼을 눌러 화면에 영상이 나오는지 확인하세요. "
                + "필요하면 여러 번 다시 촬영할 수 있습니다."
        };

        deviceComboBox.Width = 520;
        captureButton.AutoSize = true;
        captureButton.Click += CaptureButton_Click;
        normalButton.Click += (_, _) => Finish(ModuleUserResults.Confirmed);
        issueButton.Click += (_, _) => Finish(ModuleUserResults.ReportedIssue);

        statusLabel.MaximumSize = new Size(560, 0);
        statusLabel.Text = "카메라 목록을 불러오는 중입니다.";

        var decisions = new FlowLayoutPanel
        {
            AutoSize = true,
            FlowDirection = FlowDirection.LeftToRight,
            WrapContents = false
        };
        decisions.Controls.AddRange([normalButton, issueButton]);

        var scrollPanel = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.TopDown,
            WrapContents = false,
            AutoScroll = true,
            Padding = new Padding(32, 32, 32, 12)
        };
        scrollPanel.Controls.AddRange([
            title,
            guide,
            new Label { AutoSize = true, Text = "카메라 장치", Margin = new Padding(3, 18, 3, 4) },
            deviceComboBox,
            captureButton,
            previewBox,
            statusLabel
        ]);

        var decisionPanel = new Panel
        {
            Dock = DockStyle.Bottom,
            Height = 150,
            Padding = new Padding(32, 8, 32, 20)
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

        _ = LoadDevicesAsync();
    }

    private async Task LoadDevicesAsync()
    {
        try
        {
            var devices = await cameraCaptureService.GetDevicesAsync();
            deviceComboBox.Items.AddRange(devices.Cast<object>().ToArray());
            deviceComboBox.SelectedIndex = devices.Count > 0 ? 0 : -1;
            captureButton.Enabled = devices.Count > 0;
            statusLabel.Text = devices.Count > 0
                ? "촬영 버튼을 눌러 카메라 영상을 확인하세요."
                : "Windows에서 사용할 수 있는 카메라를 찾지 못했습니다.";
        }
        catch (Exception)
        {
            captureFailed = true;
            statusLabel.Text = "카메라 목록을 불러오지 못했습니다.";
        }
    }

    private async void CaptureButton_Click(object? sender, EventArgs eventArgs)
    {
        if (deviceComboBox.SelectedItem is not CameraDevice device)
        {
            statusLabel.Text = "카메라를 선택해 주세요.";
            return;
        }

        SetControlsEnabled(false);
        captureAttempted = true;
        permissionDenied = false;
        captureFailed = false;
        statusLabel.Text = "촬영하는 중입니다.";
        try
        {
            var frame = await cameraCaptureService.CaptureFrameAsync(device.Id);
            var previous = lastFrame;
            lastFrame = frame.Image;
            previewBox.Image = lastFrame;
            previous?.Dispose();
            capturedWidth = frame.Width;
            capturedHeight = frame.Height;
            frameReceived = true;
            statusLabel.Text = $"프레임을 수신했습니다 ({frame.Width}×{frame.Height}). 화면 상태를 확인해 주세요.";
        }
        catch (UnauthorizedAccessException)
        {
            permissionDenied = true;
            statusLabel.Text = "Windows 카메라 권한이 거부되어 있습니다. 설정에서 카메라 접근을 허용한 뒤 다시 시도하세요.";
        }
        catch (Exception)
        {
            captureFailed = true;
            statusLabel.Text = "카메라에서 영상을 가져오지 못했습니다.";
        }
        finally
        {
            SetControlsEnabled(true);
            normalButton.Enabled = captureAttempted;
            issueButton.Enabled = captureAttempted;
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
        var device = deviceComboBox.SelectedItem as CameraDevice;
        var hasDevice = device is not null;
        var status = !hasDevice
            ? ModuleMeasurementStatuses.NotDetected
            : permissionDenied
                ? ModuleMeasurementStatuses.PermissionDenied
                : frameReceived
                    ? ModuleMeasurementStatuses.Detected
                    : ModuleMeasurementStatuses.ExecutionFailed;
        var errorCode = !hasDevice
            ? "CAMERA_NOT_FOUND"
            : permissionDenied
                ? "CAMERA_PERMISSION_DENIED"
                : frameReceived
                    ? null
                    : captureFailed
                        ? "CAMERA_CAPTURE_FAILED"
                        : "CAMERA_NOT_TESTED";

        return ModuleResult.Create(
            ModuleTestTypes.Camera,
            status,
            userResult,
            new Dictionary<string, object?>
            {
                ["deviceId"] = device?.Id,
                ["deviceName"] = device?.Name,
                ["captureAttempted"] = captureAttempted,
                ["frameReceived"] = frameReceived,
                ["width"] = frameReceived ? capturedWidth : null,
                ["height"] = frameReceived ? capturedHeight : null
            },
            errorCode);
    }

    private void SetControlsEnabled(bool enabled)
    {
        deviceComboBox.Enabled = enabled;
        captureButton.Enabled = enabled;
        normalButton.Enabled = enabled && captureAttempted;
        issueButton.Enabled = enabled && captureAttempted;
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

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            previewBox.Image = null;
            lastFrame?.Dispose();
        }

        base.Dispose(disposing);
    }
}
