using System.ComponentModel;
using System.Runtime.InteropServices;

namespace LimitScanner.Diagnostics;

public sealed record AudioOutputDevice(int DeviceNumber, string Name)
{
    public override string ToString() => Name;
}

public enum AudioChannel
{
    Both,
    Left,
    Right
}

public sealed class AudioOutputService
{
    private const uint WaveMapper = 0xffffffff;
    private const uint WhdrDone = 0x00000001;

    public IReadOnlyList<AudioOutputDevice> GetDevices()
    {
        var count = NativeMethods.waveOutGetNumDevs();
        var devices = new List<AudioOutputDevice>();
        if (count > 0)
        {
            devices.Add(new(-1, "Windows 기본 출력 장치"));
        }

        for (uint index = 0; index < count; index++)
        {
            var result = NativeMethods.waveOutGetDevCaps(
                (UIntPtr)index,
                out var capabilities,
                (uint)Marshal.SizeOf<WaveOutCaps>());
            if (result == 0)
            {
                devices.Add(new(
                    (int)index,
                    string.IsNullOrWhiteSpace(capabilities.ProductName)
                        ? $"출력 장치 {index + 1}"
                        : capabilities.ProductName));
            }
        }

        return devices;
    }

    public async Task PlayTestToneAsync(
        AudioOutputDevice device,
        AudioChannel channel,
        CancellationToken cancellationToken)
    {
        const int sampleRate = 44100;
        const int durationMilliseconds = 900;
        var buffer = PcmTestToneGenerator.CreateStereoTone(
            channel,
            sampleRate,
            durationMilliseconds);
        var format = new WaveFormatEx
        {
            FormatTag = 1,
            Channels = 2,
            SamplesPerSecond = sampleRate,
            BitsPerSample = 16,
            BlockAlign = 4,
            AverageBytesPerSecond = sampleRate * 4,
            Size = 0
        };

        var deviceId = device.DeviceNumber < 0 ? WaveMapper : (uint)device.DeviceNumber;
        ThrowIfError(NativeMethods.waveOutOpen(
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

            ThrowIfError(NativeMethods.waveOutPrepareHeader(
                handle,
                headerPointer,
                (uint)Marshal.SizeOf<WaveHeader>()));
            isPrepared = true;
            ThrowIfError(NativeMethods.waveOutWrite(
                handle,
                headerPointer,
                (uint)Marshal.SizeOf<WaveHeader>()));

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

        }
        finally
        {
            if (isPrepared)
            {
                NativeMethods.waveOutReset(handle);
                NativeMethods.waveOutUnprepareHeader(
                    handle,
                    headerPointer,
                    (uint)Marshal.SizeOf<WaveHeader>());
            }
            if (headerPointer != IntPtr.Zero)
            {
                Marshal.FreeHGlobal(headerPointer);
            }
            dataHandle.Free();
            NativeMethods.waveOutClose(handle);
        }
    }

    private static void ThrowIfError(uint result)
    {
        if (result != 0)
        {
            throw new Win32Exception((int)result, $"오디오 출력에 실패했습니다. waveOut 오류: {result}");
        }
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
    private struct WaveOutCaps
    {
        public ushort ManufacturerId;
        public ushort ProductId;
        public uint DriverVersion;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)]
        public string? ProductName;
        public uint Formats;
        public ushort Channels;
        public ushort Reserved;
        public uint Support;
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
        public static extern uint waveOutGetNumDevs();

        [DllImport("winmm.dll", EntryPoint = "waveOutGetDevCapsW", CharSet = CharSet.Unicode)]
        public static extern uint waveOutGetDevCaps(
            UIntPtr deviceId,
            out WaveOutCaps capabilities,
            uint capabilitiesSize);

        [DllImport("winmm.dll")]
        public static extern uint waveOutOpen(
            out IntPtr handle,
            uint deviceId,
            ref WaveFormatEx format,
            IntPtr callback,
            IntPtr instance,
            uint flags);

        [DllImport("winmm.dll")]
        public static extern uint waveOutPrepareHeader(IntPtr handle, IntPtr header, uint headerSize);

        [DllImport("winmm.dll")]
        public static extern uint waveOutWrite(IntPtr handle, IntPtr header, uint headerSize);

        [DllImport("winmm.dll")]
        public static extern uint waveOutUnprepareHeader(IntPtr handle, IntPtr header, uint headerSize);

        [DllImport("winmm.dll")]
        public static extern uint waveOutReset(IntPtr handle);

        [DllImport("winmm.dll")]
        public static extern uint waveOutClose(IntPtr handle);
    }
}
