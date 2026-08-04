namespace LimitScanner.Diagnostics;

public static class DiagnosticFormSizing
{
    public static void FitToWorkingArea(Form form)
    {
        var workingArea = Screen.FromControl(form).WorkingArea;
        var maxHeight = workingArea.Height - 40;
        var maxWidth = workingArea.Width - 40;

        // MinimumSize silently clamps Height/Width assignments below it, so it must
        // be relaxed first or the shrink below has no effect on a tall/wide form.
        form.MinimumSize = new Size(
            Math.Min(form.MinimumSize.Width, maxWidth),
            Math.Min(form.MinimumSize.Height, maxHeight));

        if (form.Height > maxHeight)
        {
            form.Height = maxHeight;
        }

        if (form.Width > maxWidth)
        {
            form.Width = maxWidth;
        }

        var owner = form.Owner;
        var centerArea = owner is not null ? owner.Bounds : workingArea;
        var left = centerArea.Left + (centerArea.Width - form.Width) / 2;
        var top = centerArea.Top + (centerArea.Height - form.Height) / 2;
        form.Location = new Point(
            Math.Max(workingArea.Left, Math.Min(left, workingArea.Right - form.Width)),
            Math.Max(workingArea.Top, Math.Min(top, workingArea.Bottom - form.Height)));
    }
}
