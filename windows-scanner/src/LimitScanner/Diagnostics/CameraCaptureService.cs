using System.Drawing.Imaging;
using System.Runtime.InteropServices;
using System.Runtime.InteropServices.WindowsRuntime;
using Windows.Devices.Enumeration;
using Windows.Graphics.Imaging;
using Windows.Media.Capture;
using Windows.Media.MediaProperties;

namespace LimitScanner.Diagnostics;

public sealed record CameraDevice(string Id, string Name)
{
    public override string ToString() => Name;
}

public sealed record CameraFrame(Bitmap Image, int Width, int Height);

public sealed class CameraCaptureService
{
    public async Task<IReadOnlyList<CameraDevice>> GetDevicesAsync()
    {
        var devices = await DeviceInformation.FindAllAsync(DeviceClass.VideoCapture);
        return devices
            .Select(device => new CameraDevice(
                device.Id,
                string.IsNullOrWhiteSpace(device.Name) ? "카메라 장치" : device.Name))
            .ToArray();
    }

    public async Task<CameraFrame> CaptureFrameAsync(string deviceId)
    {
        using var mediaCapture = new MediaCapture();
        await mediaCapture.InitializeAsync(new MediaCaptureInitializationSettings
        {
            VideoDeviceId = deviceId,
            StreamingCaptureMode = StreamingCaptureMode.Video,
            PhotoCaptureSource = PhotoCaptureSource.VideoPreview
        });

        var lowLagCapture = await mediaCapture.PrepareLowLagPhotoCaptureAsync(
            ImageEncodingProperties.CreateUncompressed(MediaPixelFormat.Bgra8));
        try
        {
            var capturedPhoto = await lowLagCapture.CaptureAsync();
            using var softwareBitmap = SoftwareBitmap.Convert(
                capturedPhoto.Frame.SoftwareBitmap,
                BitmapPixelFormat.Bgra8,
                BitmapAlphaMode.Ignore);
            return new CameraFrame(ToBitmap(softwareBitmap), softwareBitmap.PixelWidth, softwareBitmap.PixelHeight);
        }
        finally
        {
            await lowLagCapture.FinishAsync();
        }
    }

    private static Bitmap ToBitmap(SoftwareBitmap softwareBitmap)
    {
        var width = softwareBitmap.PixelWidth;
        var height = softwareBitmap.PixelHeight;
        var buffer = new byte[4 * width * height];
        softwareBitmap.CopyToBuffer(buffer.AsBuffer());

        var bitmap = new Bitmap(width, height, PixelFormat.Format32bppRgb);
        var bitmapData = bitmap.LockBits(
            new Rectangle(0, 0, width, height),
            ImageLockMode.WriteOnly,
            PixelFormat.Format32bppRgb);
        try
        {
            Marshal.Copy(buffer, 0, bitmapData.Scan0, buffer.Length);
        }
        finally
        {
            bitmap.UnlockBits(bitmapData);
        }

        return bitmap;
    }
}
