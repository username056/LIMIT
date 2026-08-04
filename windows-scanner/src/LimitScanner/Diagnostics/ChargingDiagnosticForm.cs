using Microsoft.Win32;

namespace LimitScanner.Diagnostics;

public sealed class ChargingDiagnosticForm : Form
{
    private readonly Label currentStateLabel = new() { AutoSize = true };
    private readonly Label eventStateLabel = new() { AutoSize = true };
    private readonly Button normalButton = new() { Text = "정상" };
    private readonly Button issueButton = new() { Text = "이상 있음" };
    private readonly PowerStateTracker powerStateTracker;

    public ModuleResult Result { get; private set; }

    public ChargingDiagnosticForm()
    {
        powerStateTracker = new PowerStateTracker(ReadPowerState());
        Result = CreateResult(ModuleUserResults.Skipped);

        Text = "충전 연결 검사";
        ClientSize = new Size(650, 440);
        MinimumSize = new Size(650, 440);
        StartPosition = FormStartPosition.CenterParent;
        Font = new Font("Segoe UI", 10F);
        AutoScaleMode = AutoScaleMode.Dpi;

        currentStateLabel.Font = new Font(Font.FontFamily, 15F, FontStyle.Bold);
        currentStateLabel.Text = GetPowerStateText(powerStateTracker.CurrentState);
        eventStateLabel.Text = "아직 연결·분리 변경을 감지하지 못했습니다.";

        normalButton.Click += (_, _) => Finish(ModuleUserResults.Confirmed);
        issueButton.Click += (_, _) => Finish(ModuleUserResults.ReportedIssue);

        var decisions = new FlowLayoutPanel
        {
            AutoSize = true,
            FlowDirection = FlowDirection.LeftToRight,
            WrapContents = false
        };
        decisions.Controls.AddRange([normalButton, issueButton]);

        var warning = new Label
        {
            AutoSize = true,
            MaximumSize = new Size(565, 0),
            ForeColor = Color.FromArgb(180, 83, 9),
            BackColor = Color.FromArgb(255, 251, 235),
            Padding = new Padding(12),
            Text = "주의: 이 검사는 Windows가 AC 연결 상태 변화를 감지하는지만 확인합니다. "
                + "충전기 출력, 케이블, 충전 단자 전체의 정상 여부를 보증하지 않습니다."
        };

        var layout = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.TopDown,
            WrapContents = false,
            AutoScroll = true,
            Padding = new Padding(32)
        };
        layout.Controls.AddRange([
            new Label
            {
                AutoSize = true,
                Font = new Font(Font.FontFamily, 16F, FontStyle.Bold),
                Text = "AC 연결·분리 감지"
            },
            new Label
            {
                AutoSize = true,
                MaximumSize = new Size(565, 0),
                Text = "충전기를 한 번 연결하거나 분리해 상태와 이벤트가 바뀌는지 확인해 주세요.",
                Margin = new Padding(3, 8, 3, 18)
            },
            currentStateLabel,
            eventStateLabel,
            warning,
            new Label
            {
                AutoSize = true,
                Text = "직접 확인한 결과를 선택해 주세요.",
                Margin = new Padding(3, 22, 3, 4)
            },
            decisions
        ]);
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
        var isKnown = powerStateTracker.CurrentState != "UNKNOWN";
        return ModuleResult.Create(
            ModuleTestTypes.Charging,
            isKnown ? ModuleMeasurementStatuses.Detected : ModuleMeasurementStatuses.NotDetected,
            userResult,
            new Dictionary<string, object?>
            {
                ["initialAcState"] = powerStateTracker.InitialState,
                ["finalAcState"] = powerStateTracker.CurrentState,
                ["transitionDetected"] = powerStateTracker.TransitionCount > 0,
                ["transitionCount"] = powerStateTracker.TransitionCount,
                ["scope"] = "WINDOWS_AC_LINE_STATUS_ONLY"
            },
            isKnown ? null : "AC_STATUS_UNKNOWN");
    }

    private static string ReadPowerState() => SystemInformation.PowerStatus.PowerLineStatus switch
    {
        PowerLineStatus.Online => "CONNECTED",
        PowerLineStatus.Offline => "DISCONNECTED",
        _ => "UNKNOWN"
    };

    private static string GetPowerStateText(string state) => state switch
    {
        "CONNECTED" => "현재 상태: AC 전원 연결됨",
        "DISCONNECTED" => "현재 상태: AC 전원 분리됨",
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

public sealed class PowerStateTracker(string initialState)
{
    public string InitialState { get; } = initialState;
    public string CurrentState { get; private set; } = initialState;
    public int TransitionCount { get; private set; }

    public bool Record(string nextState)
    {
        if (string.Equals(nextState, CurrentState, StringComparison.Ordinal))
        {
            return false;
        }

        CurrentState = nextState;
        TransitionCount++;
        return true;
    }
}
