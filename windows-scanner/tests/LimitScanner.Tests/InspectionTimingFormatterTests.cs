using LimitScanner.Services;
using Xunit;

namespace LimitScanner.Tests;

public sealed class InspectionTimingFormatterTests
{
    [Fact]
    public void FormatsSubSecondDurationsAsMilliseconds()
    {
        Assert.Equal("425ms", InspectionTimingFormatter.Format(TimeSpan.FromMilliseconds(425)));
    }

    [Fact]
    public void FormatsLongerDurationsAsSeconds()
    {
        Assert.Equal("1.3초", InspectionTimingFormatter.Format(TimeSpan.FromMilliseconds(1250)));
    }
}
