namespace LimitScanner.Diagnostics;

internal static class DiagnosticUi
{
    public static readonly Color Primary = Color.FromArgb(37, 99, 235);
    public static readonly Color Success = Color.FromArgb(22, 163, 74);
    public static readonly Color Danger = Color.FromArgb(220, 38, 38);
    public static readonly Color TextMain = Color.FromArgb(31, 41, 55);
    public static readonly Color TextSub = Color.FromArgb(75, 85, 99);
    public static readonly Color Surface = Color.FromArgb(248, 250, 252);
    public static readonly Color Border = Color.FromArgb(203, 213, 225);

    public static void ConfigureForm(Form form, string title)
    {
        form.Text = title;
        form.ClientSize = new Size(760, 650);
        form.MinimumSize = new Size(720, 620);
        form.StartPosition = FormStartPosition.CenterParent;
        form.Font = new Font("Segoe UI", 10F);
        form.AutoScaleMode = AutoScaleMode.Dpi;
        form.BackColor = Color.White;
    }

    public static Label CreateStepLabel(string text) => new()
    {
        AutoSize = true,
        BackColor = Color.FromArgb(219, 234, 254),
        ForeColor = Color.FromArgb(30, 64, 175),
        Font = new Font("Segoe UI", 9F, FontStyle.Bold),
        Padding = new Padding(10, 5, 10, 5),
        Text = text,
        Margin = new Padding(0, 0, 0, 10)
    };

    public static Label CreateTitle(string text) => new()
    {
        AutoSize = true,
        ForeColor = TextMain,
        Font = new Font("Segoe UI", 18F, FontStyle.Bold),
        Text = text,
        Margin = new Padding(0, 0, 0, 8)
    };

    public static Label CreateGuide(string text) => new()
    {
        AutoSize = true,
        Dock = DockStyle.Fill,
        ForeColor = TextSub,
        MaximumSize = new Size(680, 0),
        Text = text,
        Margin = new Padding(0, 0, 0, 18)
    };

    public static Panel CreateCard(Control content)
    {
        var card = new Panel
        {
            Dock = DockStyle.Fill,
            BackColor = Surface,
            Padding = new Padding(20),
            Margin = new Padding(0)
        };
        card.Paint += (_, eventArgs) =>
        {
            using var pen = new Pen(Border);
            eventArgs.Graphics.DrawRectangle(
                pen,
                0,
                0,
                Math.Max(0, card.ClientSize.Width - 1),
                Math.Max(0, card.ClientSize.Height - 1));
        };
        card.Controls.Add(content);
        return card;
    }

    public static Label CreateStatusLabel() => new()
    {
        Dock = DockStyle.Fill,
        AutoEllipsis = true,
        BackColor = Color.White,
        ForeColor = TextMain,
        Padding = new Padding(12),
        TextAlign = ContentAlignment.MiddleLeft,
        Margin = new Padding(0, 12, 0, 0)
    };

    public static void StyleButton(Button button, ButtonKind kind, int width = 150)
    {
        button.AutoSize = false;
        button.Width = width;
        button.Height = 44;
        button.FlatStyle = FlatStyle.Flat;
        button.FlatAppearance.BorderSize = kind == ButtonKind.Secondary ? 1 : 0;
        button.FlatAppearance.BorderColor = Border;
        button.UseVisualStyleBackColor = false;
        button.Cursor = Cursors.Hand;
        button.Font = new Font("Segoe UI", 10F, FontStyle.Bold);
        button.Margin = new Padding(0, 0, 10, 0);

        var activeColors = kind switch
        {
            ButtonKind.Primary => (Primary, Color.White),
            ButtonKind.Success => (Success, Color.White),
            ButtonKind.Danger => (Danger, Color.White),
            _ => (Color.White, TextMain)
        };

        void ApplyEnabledAppearance()
        {
            (button.BackColor, button.ForeColor) = button.Enabled
                ? activeColors
                : (Color.FromArgb(226, 232, 240), Color.FromArgb(148, 163, 184));
        }

        ApplyEnabledAppearance();
        button.EnabledChanged += (_, _) => ApplyEnabledAppearance();
    }

    public static Control CreateDecisionBar(
        Button normalButton,
        Button issueButton,
        Button skipButton)
    {
        StyleButton(normalButton, ButtonKind.Success, 120);
        StyleButton(issueButton, ButtonKind.Danger, 120);
        StyleButton(skipButton, ButtonKind.Secondary, 120);

        var buttons = new FlowLayoutPanel
        {
            AutoSize = true,
            FlowDirection = FlowDirection.LeftToRight,
            WrapContents = false,
            Anchor = AnchorStyles.Right
        };
        buttons.Controls.AddRange([normalButton, issueButton, skipButton]);

        var bar = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 2,
            RowCount = 1,
            Margin = new Padding(0, 20, 0, 0)
        };
        bar.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100F));
        bar.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));
        bar.Controls.Add(new Label
        {
            AutoSize = true,
            Anchor = AnchorStyles.Left,
            ForeColor = TextMain,
            Font = new Font("Segoe UI", 10F, FontStyle.Bold),
            Text = "직접 확인한 결과"
        }, 0, 0);
        bar.Controls.Add(buttons, 1, 0);
        return bar;
    }
}

internal enum ButtonKind
{
    Primary,
    Success,
    Danger,
    Secondary
}
