namespace LimitScanner.Services;

public static class InspectionTimingFormatter
{
    public static string Format(TimeSpan elapsed) =>
        elapsed.TotalSeconds < 1
            ? $"{elapsed.TotalMilliseconds:0}ms"
            : $"{elapsed.TotalSeconds:0.0}초";
}
