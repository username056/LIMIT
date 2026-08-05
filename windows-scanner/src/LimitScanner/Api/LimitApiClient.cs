using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using LimitScanner.Diagnostics;

namespace LimitScanner.Api;

public sealed class LimitApiClient
{
    private readonly HttpClient apiClient;
    private readonly HttpClient uploadClient = new();
    private readonly JsonSerializerOptions jsonOptions = new(JsonSerializerDefaults.Web);
    private string? agentToken;

    public LimitApiClient(string baseUrl)
    {
        apiClient = new HttpClient
        {
            BaseAddress = new Uri(baseUrl, UriKind.Absolute),
            Timeout = TimeSpan.FromSeconds(60)
        };
    }

    public async Task<PairData> PairAsync(
        string pairingCode,
        CancellationToken cancellationToken)
    {
        using var response = await apiClient.PostAsJsonAsync(
            "api/v1/inspection-agent/sessions/pair",
            new { pairingCode, collectorVersion = "0.1.0" },
            cancellationToken);
        await EnsureSuccessAsync(response, cancellationToken);

        var envelope = await response.Content.ReadFromJsonAsync<ApiEnvelope<PairData>>(
            jsonOptions,
            cancellationToken);
        var data = envelope?.Data
            ?? throw new InvalidOperationException("연결 응답이 올바르지 않습니다.");
        agentToken = data.AgentToken;
        return data;
    }

    public async Task UploadDiagnosticAsync(
        string sessionKey,
        string parserType,
        string filePath,
        string contentType,
        CancellationToken cancellationToken)
    {
        var file = new FileInfo(filePath);
        using var createResponse = await PostAgentJsonAsync(
            $"api/v1/inspection-agent/sessions/{sessionKey}/uploads",
            new
            {
                parserType,
                filename = file.Name,
                contentType,
                fileSize = file.Length
            },
            cancellationToken);
        await EnsureSuccessAsync(createResponse, cancellationToken);
        var upload = await createResponse.Content.ReadFromJsonAsync<ApiEnvelope<UploadData>>(
            jsonOptions,
            cancellationToken);
        var uploadData = upload?.Data
            ?? throw new InvalidOperationException("업로드 응답이 올바르지 않습니다.");

        await using var stream = file.OpenRead();
        using var content = new StreamContent(stream);
        foreach (var header in uploadData.RequiredHeaders)
        {
            if (header.Key.Equals("Content-Type", StringComparison.OrdinalIgnoreCase))
            {
                content.Headers.ContentType = MediaTypeHeaderValue.Parse(header.Value);
            }
            else
            {
                content.Headers.TryAddWithoutValidation(header.Key, header.Value);
            }
        }

        using var putResponse = await uploadClient.PutAsync(
            uploadData.PresignedUrl,
            content,
            cancellationToken);
        await EnsureSuccessAsync(putResponse, cancellationToken);

        using var completeResponse = await PostAgentJsonAsync(
            $"api/v1/inspection-agent/sessions/{sessionKey}/uploads/{uploadData.UploadId}/complete",
            new { parserType },
            cancellationToken);
        await EnsureSuccessAsync(completeResponse, cancellationToken);
    }

    public async Task CompleteAsync(
        string sessionKey,
        CancellationToken cancellationToken)
    {
        using var response = await PostAgentJsonAsync(
            $"api/v1/inspection-agent/sessions/{sessionKey}/complete",
            new { },
            cancellationToken);
        await EnsureSuccessAsync(response, cancellationToken);
    }

    public async Task SubmitTestResultAsync(
        string sessionKey,
        ModuleResult result,
        CancellationToken cancellationToken)
    {
        var request = SubmitTestResultRequest.From(result);
        using var response = await PostAgentJsonAsync(
            $"api/v1/inspection-agent/sessions/{sessionKey}/test-results",
            request,
            cancellationToken);
        await EnsureSuccessAsync(response, cancellationToken);
    }

    private async Task<HttpResponseMessage> PostAgentJsonAsync<T>(
        string requestUri,
        T value,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(agentToken))
        {
            throw new InvalidOperationException("먼저 검사 세션을 연결해야 합니다.");
        }

        using var request = new HttpRequestMessage(HttpMethod.Post, requestUri)
        {
            Content = JsonContent.Create(value, options: jsonOptions)
        };
        request.Headers.Authorization =
            new AuthenticationHeaderValue("Bearer", agentToken);
        return await apiClient.SendAsync(request, cancellationToken);
    }

    private static async Task EnsureSuccessAsync(
        HttpResponseMessage response,
        CancellationToken cancellationToken)
    {
        if (response.IsSuccessStatusCode)
        {
            return;
        }

        var body = await response.Content.ReadAsStringAsync(cancellationToken);
        throw new HttpRequestException(
            $"서버 요청이 실패했습니다. HTTP {(int)response.StatusCode}: {body}");
    }
}
