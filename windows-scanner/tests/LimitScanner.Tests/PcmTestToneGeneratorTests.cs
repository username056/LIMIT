using LimitScanner.Diagnostics;
using Xunit;

namespace LimitScanner.Tests;

public sealed class PcmTestToneGeneratorTests
{
    [Fact]
    public void BothChannelWritesSameSamplesToLeftAndRight()
    {
        var frames = ReadFrames(PcmTestToneGenerator.CreateStereoTone(
            AudioChannel.Both,
            sampleRate: 1000,
            durationMilliseconds: 100,
            frequency: 50));

        Assert.Contains(frames, frame => frame.Left != 0);
        Assert.All(frames, frame => Assert.Equal(frame.Left, frame.Right));
    }

    [Fact]
    public void LeftChannelMutesRightSamples()
    {
        var frames = ReadFrames(PcmTestToneGenerator.CreateStereoTone(
            AudioChannel.Left,
            sampleRate: 1000,
            durationMilliseconds: 100,
            frequency: 50));

        Assert.Contains(frames, frame => frame.Left != 0);
        Assert.All(frames, frame => Assert.Equal(0, frame.Right));
    }

    [Fact]
    public void RightChannelMutesLeftSamples()
    {
        var frames = ReadFrames(PcmTestToneGenerator.CreateStereoTone(
            AudioChannel.Right,
            sampleRate: 1000,
            durationMilliseconds: 100,
            frequency: 50));

        Assert.Contains(frames, frame => frame.Right != 0);
        Assert.All(frames, frame => Assert.Equal(0, frame.Left));
    }

    private static IReadOnlyList<(short Left, short Right)> ReadFrames(byte[] pcm)
    {
        Assert.Equal(0, pcm.Length % 4);
        return Enumerable.Range(0, pcm.Length / 4)
            .Select(index =>
            {
                var offset = index * 4;
                return (
                    BitConverter.ToInt16(pcm, offset),
                    BitConverter.ToInt16(pcm, offset + 2));
            })
            .ToArray();
    }
}
