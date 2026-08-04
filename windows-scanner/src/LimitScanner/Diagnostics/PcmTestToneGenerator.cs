namespace LimitScanner.Diagnostics;

public static class PcmTestToneGenerator
{
    public static byte[] CreateStereoTone(
        AudioChannel channel,
        int sampleRate = 44100,
        int durationMilliseconds = 900,
        double frequency = 523.25,
        short amplitude = 9000)
    {
        var sampleCount = sampleRate * durationMilliseconds / 1000;
        var data = new byte[sampleCount * 4];

        for (var sampleIndex = 0; sampleIndex < sampleCount; sampleIndex++)
        {
            var edgeSamples = Math.Min(sampleIndex, sampleCount - sampleIndex - 1);
            var fade = Math.Min(1d, edgeSamples / (sampleRate * 0.02));
            var sample = (short)(amplitude
                * fade
                * Math.Sin(2 * Math.PI * frequency * sampleIndex / sampleRate));
            var left = channel == AudioChannel.Right ? (short)0 : sample;
            var right = channel == AudioChannel.Left ? (short)0 : sample;
            var offset = sampleIndex * 4;
            BitConverter.TryWriteBytes(data.AsSpan(offset, 2), left);
            BitConverter.TryWriteBytes(data.AsSpan(offset + 2, 2), right);
        }

        return data;
    }
}
