using Microsoft.Win32;

namespace LimitScanner.Diagnostics;

public sealed class ChargingDiagnosticForm : Form
{
    private readonly Label currentStateLabel = new();
    private readonly Label eventStateLabel = DiagnosticUi.CreateStatusLabel();
    private readonly Button refreshButton = new() { Text = "현재 상태 다시 확인" };
    private readonly Button normalButton = new() { Text = "정상" };
    private readonly Button issueButton = new() { Text = "이상 있음" };
    private readonly Button skipButton = new() { Text = "건너뛰기" };
    private readonly PowerStateTracker powerStateTracker;

    public ModuleResult Result { get; private set; }

    public ChargingDiagnosticForm()
    {
        powerStateTracker = new PowerStateTracker(ReadPowerState());
        Result = CreateResult(ModuleUserResults.Skipped);

        DiagnosticUi.ConfigureForm(this, "충전 연결 검사");

        currentStateLabel.Dock = DockStyle.Fill;
        currentStateLabel.Font = new Font("Segoe UI", 16F, FontStyle.Bold);
        currentStateLabel.Padding = new Padding(18);
        currentStateLabel.TextAlign = ContentAlignment.MiddleLeft;
        currentStateLabel.Text = GetPowerStateText(powerStateTracker.CurrentState);
        UpdatePowerStateAppearance();
        normalButton.Enabled = powerStateTracker.CanConfirm;
        eventStateLabel.Text = "아직 연결·분리 변경을 감지하지 못했습니다.";

        DiagnosticUi.StyleButton(refreshButton, ButtonKind.Secondary, 220);
        refreshButton.Click += (_, _) => RefreshPowerState();
        normalButton.Click += (_, _) => Finish(ModuleUserResults.Confirmed);
        issueButton.Click += (_, _) => Finish(ModuleUserResults.ReportedIssue);
        skipButton.Click += (_, _) => Finish(ModuleUserResults.Skipped);

        var warning = new Label
        {
            Dock = DockStyle.Fill,
            ForeColor = Color.FromArgb(180, 83, 9),
            BackColor = Color.FromArgb(255, 251, 235),
            Padding = new Padding(12),
            Text = "주의: 이 검사는 Windows가 AC 연결 상태 변화를 감지하는지만 확인합니다.\r\n"
                + "충전기 출력, 케이블, 충전 단자 전체의 정상 여부를 보증하지 않습니다."
        };

        var cardContent = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 1,
            RowCount = 5,
            Margin = new Padding(0)
        };
        cardContent.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        cardContent.RowStyles.Add(new RowStyle(SizeType.Absolute, 78F));
        cardContent.RowStyles.Add(new RowStyle(SizeType.Absolute, 58F));
        cardContent.RowStyles.Add(new RowStyle(SizeType.Absolute, 54F));
        cardContent.RowStyles.Add(new RowStyle(SizeType.Percent, 100F));
        cardContent.Controls.Add(new Label
        {
            AutoSize = true,
            ForeColor = DiagnosticUi.TextMain,
            Font = new Font("Segoe UI", 10F, FontStyle.Bold),
            Text = "현재 감지 상태",
            Margin = new Padding(0, 0, 0, 8)
        }, 0, 0);
        cardContent.Controls.Add(currentStateLabel, 0, 1);
        cardContent.Controls.Add(eventStateLabel, 0, 2);
        cardContent.Controls.Add(refreshButton, 0, 3);
        cardContent.Controls.Add(warning, 0, 4);

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
        layout.Controls.Add(DiagnosticUi.CreateStepLabel("3 / 3  충전"), 0, 0);
        layout.Controls.Add(DiagnosticUi.CreateTitle("AC 연결·분리 감지"), 0, 1);
        layout.Controls.Add(DiagnosticUi.CreateGuide(
            "충전기를 한 번 연결하거나 분리해 상태가 바뀌는지 확인해 주세요. "
                + "변경이 감지되면 아래 기록이 자동으로 갱신됩니다."), 0, 2);
        layout.Controls.Add(DiagnosticUi.CreateCard(cardContent), 0, 3);
        layout.Controls.Add(DiagnosticUi.CreateDecisionBar(normalButton, issueButton, skipButton), 0, 4);
        Controls.Add(layout);

        SystemEvents.PowerModeChanged += SystemEvents_PowerModeChanged;
        FormClosed += (_, _) => SystemEvents.PowerModeChanged -= SystemEvents_PowerModeChanged;
    }

    private void SystemEvents_PowerModeChanged(object sender, PowerModeChangedEventArgs eventArgs)
    {
        if (eventArgs.Mode != PowerModes.StatusChange)
        {
            return;
        }

        if (InvokeRequired)
        {
            BeginInvoke((Action)RefreshPowerState);
            return;
        }

        RefreshPowerState();
    }

    private void RefreshPowerState()
    {
        var nextState = ReadPowerState();
        if (powerStateTracker.Record(nextState))
        {
            eventStateLabel.Text = $"연결 상태 변경을 {powerStateTracker.TransitionCount}회 감지했습니다.";
        }

        currentStateLabel.Text = GetPowerStateText(powerStateTracker.CurrentState);
        UpdatePowerStateAppearance();
        normalButton.Enabled = powerStateTracker.CanConfirm;
    }

    private void UpdatePowerStateAppearance()
    {
        (currentStateLabel.BackColor, currentStateLabel.ForeColor) = powerStateTracker.CurrentState switch
        {
            PowerLineStates.Connected => (Color.FromArgb(220, 252, 231), Color.FromArgb(21, 128, 61)),
            PowerLineStates.Disconnected => (Color.FromArgb(254, 243, 199), Color.FromArgb(180, 83, 9)),
            _ => (Color.FromArgb(241, 245, 249), DiagnosticUi.TextSub)
        };
    }

    private void Finish(string userResult)
    {
        RefreshPowerState();
        Result = CreateResult(userResult);
        DialogResult = DialogResult.OK;
        Close();
    }

    private ModuleResult CreateResult(string userResult)
    {
        return powerStateTracker.CreateResult(userResult);
    }

    private static string ReadPowerState() => SystemInformation.PowerStatus.PowerLineStatus switch
    {
        PowerLineStatus.Online => PowerLineStates.Connected,
        PowerLineStatus.Offline => PowerLineStates.Disconnected,
        _ => PowerLineStates.Unknown
    };

    private static string GetPowerStateText(string state) => state switch
    {
        PowerLineStates.Connected => "현재 상태: AC 전원 연결됨",
        PowerLineStates.Disconnected => "현재 상태: AC 전원 분리됨",
        _ => "현재 상태: Windows에서 확인할 수 없음"
    };

    protected override void OnFormClosing(FormClosingEventArgs eventArgs)
    {
        if (DialogResult != DialogResult.OK)
        {
            RefreshPowerState();
            Result = CreateResult(ModuleUserResults.Skipped);
        }

        base.OnFormClosing(eventArgs);
    }
}
