using LimitScanner.Diagnostics;
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
    private readonly List<Button> rerunButtons = [];
    private readonly Label rerunStatusLabel = new() { AutoSize = true, MaximumSize = new Size(750, 0) };
    private readonly Button finalSubmitButton = new() { Text = "최종 제출", Enabled = false, AutoSize = true };
    private bool hasCompletedFullRun;
    private bool hasFinalized;

    public MainForm(InspectionCoordinator coordinator)
    {
        this.coordinator = coordinator;
        Text = "Limit Windows 자동 진단";
        AutoScaleMode = AutoScaleMode.Dpi;
        Font = new Font("Segoe UI", 10F);
        ClientSize = new Size(860, 960);
        MinimumSize = new Size(800, 900);
        FormBorderStyle = FormBorderStyle.Sizable;
        MaximizeBox = true;
        StartPosition = FormStartPosition.CenterScreen;
        AcceptButton = startButton;

        var title = new Label
        {
            AutoSize = true,
            Font = new Font(Font.FontFamily, 16F, FontStyle.Bold),
            Text = "Limit Windows 자동 진단",
            Margin = new Padding(3, 3, 3, 14)
        };
        var description = new Label
        {
            AutoSize = true,
            MaximumSize = new Size(750, 0),
            Font = new Font(Font.FontFamily, 10F),
            Text = "CPU, RAM, GPU, 저장 장치, 배터리 정보와 키보드·포인터 점검 결과를 수집합니다. "
                + "비밀번호, 개인 파일, 브라우저 기록, Windows 제품 키, 촬영된 영상·음성 원본은 수집하지 않습니다.",
            Margin = new Padding(3, 0, 3, 18)
        };
        var pairingCodeLabel = new Label
        {
            AutoSize = true,
            Text = "연결 코드",
            Margin = new Padding(3, 0, 3, 8)
        };

        pairingCodeTextBox.Font = new Font(Font.FontFamily, 20F);
        pairingCodeTextBox.TextAlign = HorizontalAlignment.Center;
        pairingCodeTextBox.Width = 230;
        pairingCodeTextBox.Height = 52;
        pairingCodeTextBox.Margin = new Padding(3, 0, 3, 16);
        consentCheckBox.Margin = new Padding(3, 0, 3, 16);
        startButton.AutoSize = true;
        startButton.Padding = new Padding(18, 8, 18, 8);
        startButton.Margin = new Padding(3, 0, 3, 18);
        progressBar.Width = 750;
        progressBar.Margin = new Padding(3, 0, 3, 14);
        statusLabel.MaximumSize = new Size(750, 0);
        statusLabel.Margin = new Padding(3, 0, 3, 3);
        pairingCodeTextBox.TextChanged += (_, _) => UpdateStartButton();
        consentCheckBox.CheckedChanged += (_, _) => UpdateStartButton();
        startButton.Click += StartButton_Click;
        finalSubmitButton.Click += FinalSubmitButton_Click;
        rerunStatusLabel.Margin = new Padding(3, 4, 3, 12);
        finalSubmitButton.Padding = new Padding(18, 8, 18, 8);
        finalSubmitButton.Margin = new Padding(3, 4, 3, 4);

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
        layout.Controls.Add(pairingCodeLabel);
        layout.Controls.Add(pairingCodeTextBox);
        layout.Controls.Add(consentCheckBox);
        layout.Controls.Add(startButton);
        layout.Controls.Add(progressBar);
        layout.Controls.Add(statusLabel);
        layout.Controls.Add(CreateRerunPanel());
        layout.Controls.Add(rerunStatusLabel);
        layout.Controls.Add(finalSubmitButton);
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
                this,
                progress,
                CancellationToken.None);
            hasCompletedFullRun = true;
        }
        catch (HttpRequestException)
        {
            statusLabel.Text = "서버에 연결하지 못했습니다. 백엔드 실행 상태와 연결 코드를 확인해 주세요.";
        }
        catch (TaskCanceledException)
        {
            statusLabel.Text = "검사 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.";
        }
        catch (TimeoutException)
        {
            statusLabel.Text = "Windows 시스템 정보 수집 시간이 초과되었습니다. 다시 시도해 주세요.";
        }
        catch (InvalidOperationException)
        {
            statusLabel.Text = "Windows 시스템 정보를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
        }
        catch (IOException)
        {
            statusLabel.Text = "검사 결과 파일을 처리하지 못했습니다. 저장 공간을 확인해 주세요.";
        }
        catch (Exception)
        {
            statusLabel.Text = "검사를 완료하지 못했습니다. 연결 코드를 확인하고 다시 시도해 주세요.";
        }
        finally
        {
            SetBusy(false);
        }
    }

    private Control CreateRerunPanel()
    {
        var modules = new[]
        {
            ("키보드", ModuleTestTypes.Keyboard),
            ("포인터", ModuleTestTypes.Pointer)
        };
        var panel = new FlowLayoutPanel
        {
            AutoSize = true,
            FlowDirection = FlowDirection.LeftToRight,
            WrapContents = true,
            MaximumSize = new Size(750, 0),
            Margin = new Padding(3, 22, 3, 4)
        };

        foreach (var (name, testType) in modules)
        {
            var button = new Button
            {
                Text = $"{name} 재검사",
                Enabled = false,
                AutoSize = true,
                Padding = new Padding(12, 6, 12, 6),
                Margin = new Padding(3, 3, 6, 3)
            };
            button.Click += async (_, _) => await RerunSingleModuleAsync(testType, name);
            rerunButtons.Add(button);
            panel.Controls.Add(button);
        }

        return panel;
    }

    private async Task RerunSingleModuleAsync(
        string testType,
        string moduleName)
    {
        SetRerunControlsEnabled(false);
        rerunStatusLabel.Text = $"{moduleName} 재검사를 진행하고 있습니다.";
        try
        {
            await coordinator.RerunModuleAsync(testType, this, CancellationToken.None);
            rerunStatusLabel.Text = $"{moduleName} 재검사 결과를 전송했습니다.";
        }
        catch (Exception)
        {
            rerunStatusLabel.Text = $"{moduleName} 재검사 결과를 전송하지 못했습니다. 다시 시도해 주세요.";
        }
        finally
        {
            SetRerunControlsEnabled(true);
        }
    }

    private async void FinalSubmitButton_Click(object? sender, EventArgs eventArgs)
    {
        SetRerunControlsEnabled(false);
        rerunStatusLabel.Text = "최종 제출을 진행하고 있습니다.";
        try
        {
            await coordinator.CompleteInspectionAsync(CancellationToken.None);
            hasFinalized = true;
            MessageBox.Show(
                this,
                "검사가 완료됐습니다. 웹으로 돌아가 결과를 확인해 주세요.",
                "검사 완료",
                MessageBoxButtons.OK,
                MessageBoxIcon.Information);
            rerunStatusLabel.Text = "최종 제출을 완료했습니다.";
        }
        catch (Exception)
        {
            rerunStatusLabel.Text = "최종 제출에 실패했습니다. 다시 시도해 주세요.";
        }
        finally
        {
            SetRerunControlsEnabled(true);
        }
    }

    private void SetRerunControlsEnabled(bool enabled)
    {
        var available = enabled && hasCompletedFullRun && !hasFinalized;
        foreach (var button in rerunButtons)
        {
            button.Enabled = available;
        }
        finalSubmitButton.Enabled = available;
    }

    private void SetBusy(bool busy)
    {
        progressBar.Visible = busy;
        pairingCodeTextBox.Enabled = !busy;
        consentCheckBox.Enabled = !busy;
        startButton.Enabled = false;
        SetRerunControlsEnabled(!busy);
        if (!busy)
        {
            UpdateStartButton();
        }
    }

    protected override void OnLoad(EventArgs eventArgs)
    {
        base.OnLoad(eventArgs);
        DiagnosticFormSizing.FitToWorkingArea(this);
    }
}
