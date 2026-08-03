using LimitScanner.Services;

namespace LimitScanner;

public sealed class MainForm : Form
{
    private readonly InspectionCoordinator coordinator;
    private readonly TextBox pairingCodeTextBox = new() { MaxLength = 6 };
    private readonly CheckBox consentCheckBox = new()
    {
        AutoSize = true,
        Text = "위 시스템 정보 수집 및 전송에 동의합니다."
    };
    private readonly Button startButton = new() { Text = "검사 시작", Enabled = false };
    private readonly Label statusLabel = new() { AutoSize = true, Text = "웹의 6자리 코드를 입력해 주세요." };
    private readonly ProgressBar progressBar = new() { Style = ProgressBarStyle.Marquee, Visible = false };

    public MainForm(InspectionCoordinator coordinator)
    {
        this.coordinator = coordinator;
        Text = "Limit Windows 자동 검사";
        AutoScaleMode = AutoScaleMode.Dpi;
        Font = new Font("Segoe UI", 10F);
        ClientSize = new Size(760, 600);
        MinimumSize = new Size(700, 560);
        FormBorderStyle = FormBorderStyle.Sizable;
        MaximizeBox = true;
        StartPosition = FormStartPosition.CenterScreen;
        AcceptButton = startButton;

        var title = new Label
        {
            AutoSize = true,
            Font = new Font(Font.FontFamily, 16F, FontStyle.Bold),
            Text = "Limit Windows 자동 검사"
        };
        var description = new Label
        {
            AutoSize = true,
            MaximumSize = new Size(650, 0),
            Font = new Font(Font.FontFamily, 10F),
            Text = "CPU, RAM, GPU, 사운드 장치와 배터리 상태를 수집합니다. "
                + "비밀번호, 개인 파일, 브라우저 기록과 Windows 제품 키는 수집하지 않습니다."
        };

        pairingCodeTextBox.Font = new Font(Font.FontFamily, 20F);
        pairingCodeTextBox.TextAlign = HorizontalAlignment.Center;
        pairingCodeTextBox.Width = 230;
        pairingCodeTextBox.Height = 52;
        startButton.AutoSize = true;
        startButton.Padding = new Padding(18, 8, 18, 8);
        progressBar.Width = 650;
        statusLabel.MaximumSize = new Size(650, 0);
        pairingCodeTextBox.TextChanged += (_, _) => UpdateStartButton();
        consentCheckBox.CheckedChanged += (_, _) => UpdateStartButton();
        startButton.Click += StartButton_Click;

        var layout = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.TopDown,
            Padding = new Padding(36),
            WrapContents = false,
            AutoScroll = true
        };
        layout.Controls.Add(title);
        layout.Controls.Add(description);
        layout.Controls.Add(new Label { AutoSize = true, Text = "연결 코드" });
        layout.Controls.Add(pairingCodeTextBox);
        layout.Controls.Add(consentCheckBox);
        layout.Controls.Add(startButton);
        layout.Controls.Add(progressBar);
        layout.Controls.Add(statusLabel);
        Controls.Add(layout);
    }

    private void UpdateStartButton()
    {
        var code = pairingCodeTextBox.Text.Trim();
        startButton.Enabled = consentCheckBox.Checked
            && code.Length == 6
            && code.All(char.IsDigit)
            && !progressBar.Visible;
    }

    private async void StartButton_Click(object? sender, EventArgs eventArgs)
    {
        SetBusy(true);
        try
        {
            var progress = new Progress<string>(message => statusLabel.Text = message);
            await coordinator.RunAsync(
                pairingCodeTextBox.Text.Trim(),
                progress,
                CancellationToken.None);
            MessageBox.Show(
                this,
                "검사가 완료됐습니다. 웹으로 돌아가 결과를 확인해 주세요.",
                "검사 완료",
                MessageBoxButtons.OK,
                MessageBoxIcon.Information);
        }
        catch (Exception)
        {
            statusLabel.Text = "검사를 완료하지 못했습니다. 코드를 확인하고 다시 시도해 주세요.";
        }
        finally
        {
            SetBusy(false);
        }
    }

    private void SetBusy(bool busy)
    {
        progressBar.Visible = busy;
        pairingCodeTextBox.Enabled = !busy;
        consentCheckBox.Enabled = !busy;
        startButton.Enabled = false;
        if (!busy)
        {
            UpdateStartButton();
        }
    }
}
