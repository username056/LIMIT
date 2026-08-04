using System.ComponentModel;
using System.Runtime.InteropServices;

namespace LimitScanner.Diagnostics;

public sealed record AudioInputDevice(int DeviceNumber, string Name)
{
    public override string ToString() => Name;
}

public sealed record MicrophoneMeasurement(int PeakLevel, int AverageLevel, bool SignalDetected);

public sealed class MicrophoneInputService
{
    private const uint WaveMapper = 0xffffffff;
    private const uint WhdrDone = 0x00000001;
    private const int SignalThreshold = 5;

    public IReadOnlyList<AudioInputDevice> GetDevices()
    {
        var count = NativeMethods.waveInGetNumDevs();
        var devices = new List<AudioInputDevice>();
        if (count > 0)
        {
            devices.Add(new(-1, "Windows 기본 입력 장치"));
        }

        for (uint index = 0; index < count; index++)
        {
            var result = NativeMethods.waveInGetDevCaps(
                (UIntPtr)index,
                out var capabilities,
                (uint)Marshal.SizeOf<WaveInCaps>());
            if (result == 0)
            {
                devices.Add(new(
                    (int)index,
                    string.IsNullOrWhiteSpace(capabilities.ProductName)
                        ? $"입력 장치 {index + 1}"
                        : capabilities.ProductName));
            }
        }

        return devices;
    }

    public async Task<MicrophoneMeasurement> MeasureAsync(
        AudioInputDevice device,
        int durationMilliseconds,
        CancellationToken cancellationToken)
    {
        const int sampleRate = 44100;
        var sampleCount = sampleRate * durationMilliseconds / 1000;
        var buffer = new byte[sampleCount * 2];
        var format = new WaveFormatEx
        {
            FormatTag = 1,
            Channels = 1,
            SamplesPerSecond = sampleRate,
            BitsPerSample = 16,
            BlockAlign = 2,
            AverageBytesPerSecond = sampleRate * 2,
            Size = 0
        };

        var deviceId = device.DeviceNumber < 0 ? WaveMapper : (uint)device.DeviceNumber;
        ThrowIfError(NativeMethods.waveInOpen(
            out var handle,
            deviceId,
            ref format,
            IntPtr.Zero,
            IntPtr.Zero,
            0));

        var dataHandle = GCHandle.Alloc(buffer, GCHandleType.Pinned);
        var headerPointer = IntPtr.Zero;
        var isPrepared = false;
        try
        {
            var header = new WaveHeader
            {
                Data = dataHandle.AddrOfPinnedObject(),
                BufferLength = (uint)buffer.Length
            };
            headerPointer = Marshal.AllocHGlobal(Marshal.SizeOf<WaveHeader>());
            Marshal.StructureToPtr(header, headerPointer, false);

            ThrowIfError(NativeMethods.waveInPrepareHeader(
                handle,
                headerPointer,
                (uint)Marshal.SizeOf<WaveHeader>()));
            isPrepared = true;
            ThrowIfError(NativeMethods.waveInAddBuffer(
                handle,
                headerPointer,
                (uint)Marshal.SizeOf<WaveHeader>()));
            ThrowIfError(NativeMethods.waveInStart(handle));

            while (true)
            {
                cancellationToken.ThrowIfCancellationRequested();
                header = Marshal.PtrToStructure<WaveHeader>(headerPointer);
                if ((header.Flags & WhdrDone) != 0)
                {
                    break;
                }

                await Task.Delay(25, cancellationToken);
            }

            NativeMethods.waveInStop(handle);
        }
        finally
        {
            if (isPrepared)
            {
                NativeMethods.waveInUnprepareHeader(
                    handle,
                    headerPointer,
                    (uint)Marshal.SizeOf<WaveHeader>());
            }
            if (headerPointer != IntPtr.Zero)
            {
                Marshal.FreeHGlobal(headerPointer);
            }
            dataHandle.Free();
            NativeMethods.waveInClose(handle);
        }

        return AnalyzeLevels(buffer);
    }

    private static MicrophoneMeasurement AnalyzeLevels(byte[] buffer)
    {
        var sampleCount = buffer.Length / 2;
        if (sampleCount == 0)
        {
            return new MicrophoneMeasurement(0, 0, false);
        }

        long squareSum = 0;
        var peak = 0;
        for (var index = 0; index < sampleCount; index++)
        {
            var sample = BitConverter.ToInt16(buffer, index * 2);
            var magnitude = Math.Abs((int)sample);
            peak = Math.Max(peak, magnitude);
            squareSum += (long)sample * sample;
        }

        var rms = Math.Sqrt(squareSum / (double)sampleCount);
        var peakLevel = (int)Math.Round(peak / 32768d * 100);
        var averageLevel = (int)Math.Round(rms / 32768d * 100);
        return new MicrophoneMeasurement(peakLevel, averageLevel, peakLevel >= SignalThreshold);
    }

    private static void ThrowIfError(uint result)
    {
        if (result != 0)
        {
            throw new Win32Exception((int)result, $"마이크 입력에 실패했습니다. waveIn 오류: {result}");
        }
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
    private struct WaveInCaps
    {
        public ushort ManufacturerId;
        public ushort ProductId;
        public uint DriverVersion;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)]
        public string? ProductName;
        public uint Formats;
        public ushort Channels;
        public ushort Reserved;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct WaveFormatEx
    {
        public ushort FormatTag;
        public ushort Channels;
        public uint SamplesPerSecond;
        public uint AverageBytesPerSecond;
        public ushort BlockAlign;
        public ushort BitsPerSample;
        public ushort Size;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct WaveHeader
    {
        public IntPtr Data;
        public uint BufferLength;
        public uint BytesRecorded;
        public UIntPtr User;
        public uint Flags;
        public uint Loops;
        public IntPtr Next;
        public UIntPtr Reserved;
    }

    private static class NativeMethods
    {
        [DllImport("winmm.dll")]
        public static extern uint waveInGetNumDevs();

        [DllImport("winmm.dll", EntryPoint = "waveInGetDevCapsW", CharSet = CharSet.Unicode)]
        public static extern uint waveInGetDevCaps(
            UIntPtr deviceId,
            out WaveInCaps capabilities,
            uint capabilitiesSize);

        [DllImport("winmm.dll")]
        public static extern uint waveInOpen(
            out IntPtr handle,
            uint deviceId,
            ref WaveFormatEx format,
            IntPtr callback,
            IntPtr instance,
            uint flags);

        [DllImport("winmm.dll")]
        public static extern uint waveInPrepareHeader(IntPtr handle, IntPtr header, uint headerSize);

        [DllImport("winmm.dll")]
        public static extern uint waveInAddBuffer(IntPtr handle, IntPtr header, uint headerSize);

        [DllImport("winmm.dll")]
        public static extern uint waveInStart(IntPtr handle);

        [DllImport("winmm.dll")]
        public static extern uint waveInStop(IntPtr handle);

        [DllImport("winmm.dll")]
        public static extern uint waveInUnprepareHeader(IntPtr handle, IntPtr header, uint headerSize);

        [DllImport("winmm.dll")]
        public static extern uint waveInClose(IntPtr handle);
    }
}
