package com.dwinovo.numen.client.voice;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Volcengine Doubao Seed TTS 2.0 through the v3 HTTP Chunked endpoint.
 *
 * <p>The service streams newline-delimited JSON objects whose {@code data}
 * fields contain base64 PCM chunks. Numen's voice pipeline consumes complete
 * PCM WAV files, so this backend joins the chunks and adds a standard
 * 24 kHz/16-bit/mono WAV header before returning.</p>
 *
 * <p>Authentication supports both Volcengine console generations:</p>
 * <ul>
 *   <li>new console: {@code X-Api-Key}, leave App ID blank;</li>
 *   <li>legacy console: {@code X-Api-App-Key} plus
 *       {@code X-Api-Access-Key}.</li>
 * </ul>
 *
 * <p>VoiceLibrary field mapping: {@code apiKey}=API/Access Key,
 * {@code groupId}=legacy App ID, {@code model}=resource ID,
 * {@code voice}=speaker ID.</p>
 */
public final class DoubaoTtsV3 implements TtsBackend {

    public static final String DEFAULT_BASE =
            "https://openspeech.bytedance.com/api/v3/tts/unidirectional";
    public static final String DEFAULT_RESOURCE_ID = "seed-tts-2.0";
    static final int SAMPLE_RATE = 24_000;

    private final String url;
    private final String apiKey;
    private final String appId;
    private final String resourceId;
    private final String speaker;

    public DoubaoTtsV3(String baseUrl, String apiKey, String appId,
                       String resourceId, String speaker) {
        this.url = composeUrl(baseUrl);
        this.apiKey = clean(apiKey);
        this.appId = clean(appId);
        String resource = clean(resourceId);
        this.resourceId = resource.isEmpty() ? DEFAULT_RESOURCE_ID : resource;
        this.speaker = clean(speaker);
    }

    static String composeUrl(String base) {
        String b = VoiceHttp.ensureScheme(base);
        if (b.isEmpty()) return DEFAULT_BASE;
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (b.endsWith("/api/v3/tts/unidirectional")) return b;
        return b + "/api/v3/tts/unidirectional";
    }

    static JsonObject buildBody(String text, String uid, String speaker) {
        JsonObject audio = new JsonObject();
        audio.addProperty("format", "pcm");
        audio.addProperty("sample_rate", SAMPLE_RATE);

        JsonObject params = new JsonObject();
        params.addProperty("text", text);
        params.addProperty("speaker", speaker);
        params.add("audio_params", audio);

        JsonObject user = new JsonObject();
        user.addProperty("uid", uid == null || uid.isBlank()
                ? "numen-" + UUID.randomUUID() : uid.strip());

        JsonObject body = new JsonObject();
        body.add("user", user);
        body.add("req_params", params);
        return body;
    }

    @Override
    public CompletableFuture<byte[]> synthesize(String text) {
        if (apiKey.isEmpty()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("豆包 TTS 未填写 API Key / Access Key"));
        }
        if (speaker.isEmpty()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("豆包 TTS 未填写音色 Speaker ID"));
        }

        HttpRequest.Builder builder;
        try {
            builder = HttpRequest.newBuilder()
                    .uri(VoiceHttp.uriOf(url))
                    .timeout(VoiceHttp.REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("X-Api-Resource-Id", resourceId)
                    .header("X-Api-Request-Id", UUID.randomUUID().toString());
            if (appId.isEmpty()) {
                builder.header("X-Api-Key", apiKey);
            } else {
                builder.header("X-Api-App-Key", appId)
                        .header("X-Api-Access-Key", apiKey);
            }
            builder.POST(HttpRequest.BodyPublishers.ofString(
                    buildBody(text, appId, speaker).toString(), StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }

        return VoiceHttp.CLIENT.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        throw new IllegalStateException(VoiceHttp.humanHttpError(
                                "豆包 TTS v3", resp.statusCode(), resp.body()));
                    }
                    return parseChunkedResponse(resp.body());
                });
    }

    static byte[] parseChunkedResponse(String response) {
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        String completionMessage = "";
        int objects = 0;

        for (String line : response.split("\\R")) {
            String json = line.strip();
            if (json.isEmpty()) continue;
            objects++;
            JsonObject object;
            try {
                object = JsonParser.parseString(json).getAsJsonObject();
            } catch (RuntimeException ex) {
                throw new IllegalStateException("豆包 TTS v3 返回了无法解析的数据", ex);
            }

            long code = object.has("code") ? object.get("code").getAsLong() : 0L;
            if (object.has("message") && object.get("message").isJsonPrimitive()) {
                completionMessage = object.get("message").getAsString();
            }
            if (object.has("data") && object.get("data").isJsonPrimitive()) {
                String encoded = object.get("data").getAsString();
                if (!encoded.isBlank()) {
                    try {
                        pcm.writeBytes(Base64.getDecoder().decode(encoded));
                    } catch (IllegalArgumentException ex) {
                        throw new IllegalStateException("豆包 TTS v3 返回了无效的 base64 音频", ex);
                    }
                }
            }

            // Audio chunks use code=0; 20000000 is the normal terminal packet.
            if (code != 0L && code != 20_000_000L) {
                throw new IllegalStateException("豆包 TTS v3 错误 " + code
                        + (completionMessage.isBlank() ? "" : ": " + completionMessage));
            }
        }

        if (objects == 0) {
            throw new IllegalStateException("豆包 TTS v3 返回为空");
        }
        if (pcm.size() == 0) {
            throw new IllegalStateException("豆包 TTS v3 没有返回音频"
                    + (completionMessage.isBlank() ? "" : ": " + completionMessage));
        }
        return pcmToWav(pcm.toByteArray(), SAMPLE_RATE);
    }

    static byte[] pcmToWav(byte[] pcm, int sampleRate) {
        int dataSize = pcm.length - (pcm.length & 1);
        ByteBuffer wav = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        wav.put(new byte[]{'R', 'I', 'F', 'F'});
        wav.putInt(36 + dataSize);
        wav.put(new byte[]{'W', 'A', 'V', 'E'});
        wav.put(new byte[]{'f', 'm', 't', ' '});
        wav.putInt(16);
        wav.putShort((short) 1);
        wav.putShort((short) 1);
        wav.putInt(sampleRate);
        wav.putInt(sampleRate * 2);
        wav.putShort((short) 2);
        wav.putShort((short) 16);
        wav.put(new byte[]{'d', 'a', 't', 'a'});
        wav.putInt(dataSize);
        wav.put(pcm, 0, dataSize);
        return wav.array();
    }

    @Override
    public String describe() {
        return "doubao-v3(" + url + ", resource=" + resourceId
                + ", speaker=" + speaker + ")";
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
