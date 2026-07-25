package com.dwinovo.numen.client.stt;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 阿里云百炼 Fun-ASR 实时语音识别。协议顺序为
 * {@code run-task -> task-started -> PCM binary -> finish-task -> task-finished}。
 *
 * <p>麦克风在 WebSocket 冷启动期间已经开始采集，因此会先缓存 PCM；收到
 * {@code task-started} 后按录音原速排空，避免开头丢字或瞬间灌流导致只返回残片。
 * 服务端的同一句临时结果按 sentence_id 覆盖，向 UI 回传的是当前完整转写。</p>
 */
public final class DashScopeRealtimeStt implements SttBackend {

    public static final String DEFAULT_BASE =
            "wss://dashscope.aliyuncs.com/api-ws/v1/inference/";
    public static final String DEFAULT_MODEL = "fun-asr-realtime";

    private static final int MAX_PENDING_CHUNKS = 120;
    private static final int CHUNK_INTERVAL_MS = 100;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();
    private static final ScheduledExecutorService DRAINER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "numen-stt-dashscope");
                t.setDaemon(true);
                return t;
            });

    private final String url;
    private final String apiKey;
    private final String model;

    public DashScopeRealtimeStt(String baseUrl, String apiKey, String model) {
        this.url = composeUrl(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model.strip();
    }

    @Override
    public SttSession open(SttListener listener) {
        return new Session(listener);
    }

    @Override
    public String describe() {
        return "dashscope-realtime(" + url + ", model=" + model + ")";
    }

    static String composeUrl(String base) {
        String value = base == null ? "" : base.strip();
        if (value.isEmpty()) {
            return DEFAULT_BASE;
        }
        if (value.startsWith("https://")) {
            value = "wss://" + value.substring("https://".length());
        } else if (value.startsWith("http://")) {
            value = "ws://" + value.substring("http://".length());
        } else if (!value.startsWith("wss://") && !value.startsWith("ws://")) {
            value = "wss://" + value;
        }
        if (value.endsWith("/api-ws/v1/inference")) {
            return value + "/";
        }
        return value;
    }

    static String runTaskJson(String taskId, String model) {
        JsonObject header = new JsonObject();
        header.addProperty("action", "run-task");
        header.addProperty("task_id", taskId);
        header.addProperty("streaming", "duplex");

        JsonObject parameters = new JsonObject();
        parameters.addProperty("format", "pcm");
        parameters.addProperty("sample_rate", 16000);
        JsonArray languages = new JsonArray();
        languages.add("zh");
        parameters.add("language_hints", languages);
        parameters.addProperty("semantic_punctuation_enabled", false);
        parameters.addProperty("max_sentence_silence", 500);
        parameters.addProperty("heartbeat", true);

        JsonObject payload = new JsonObject();
        payload.addProperty("task_group", "audio");
        payload.addProperty("task", "asr");
        payload.addProperty("function", "recognition");
        payload.addProperty("model",
                model == null || model.isBlank() ? DEFAULT_MODEL : model.strip());
        payload.add("parameters", parameters);
        payload.add("input", new JsonObject());

        JsonObject root = new JsonObject();
        root.add("header", header);
        root.add("payload", payload);
        return root.toString();
    }

    static String finishTaskJson(String taskId) {
        JsonObject header = new JsonObject();
        header.addProperty("action", "finish-task");
        header.addProperty("task_id", taskId);
        header.addProperty("streaming", "duplex");
        JsonObject input = new JsonObject();
        JsonObject payload = new JsonObject();
        payload.add("input", input);
        JsonObject root = new JsonObject();
        root.add("header", header);
        root.add("payload", payload);
        return root.toString();
    }

    record ServerEvent(String event, String text, boolean sentenceEnd,
                       int sentenceId, String error) {}

    static ServerEvent parseEvent(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject header = root.has("header") && root.get("header").isJsonObject()
                ? root.getAsJsonObject("header") : new JsonObject();
        String event = string(header, "event");
        String error = string(header, "error_message");
        String text = "";
        boolean sentenceEnd = false;
        int sentenceId = 0;
        if ("result-generated".equals(event)
                && root.has("payload") && root.get("payload").isJsonObject()) {
            JsonObject payload = root.getAsJsonObject("payload");
            if (payload.has("output") && payload.get("output").isJsonObject()) {
                JsonObject output = payload.getAsJsonObject("output");
                if (output.has("sentence") && output.get("sentence").isJsonObject()) {
                    JsonObject sentence = output.getAsJsonObject("sentence");
                    text = string(sentence, "text");
                    sentenceEnd = sentence.has("sentence_end")
                            && sentence.get("sentence_end").getAsBoolean();
                    if (sentence.has("sentence_id")) {
                        sentenceId = sentence.get("sentence_id").getAsInt();
                    } else if (sentence.has("begin_time")) {
                        sentenceId = sentence.get("begin_time").getAsInt();
                    }
                }
            }
        }
        return new ServerEvent(event, text, sentenceEnd, sentenceId, error);
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString() : "";
    }

    private final class Session implements SttSession, WebSocket.Listener {

        private final Object lock = new Object();
        private final SttListener listener;
        private final String taskId = UUID.randomUUID().toString();
        private final ArrayDeque<byte[]> pending = new ArrayDeque<>();
        private final Map<Integer, String> sentences = new LinkedHashMap<>();
        private final StringBuilder textFrame = new StringBuilder();

        private WebSocket socket;
        private ScheduledFuture<?> drainJob;
        private boolean taskStarted;
        private boolean finishRequested;
        private boolean finishSent;
        private boolean cancelled;
        private boolean terminal;

        Session(SttListener listener) {
            this.listener = listener;
            if (apiKey.isEmpty()) {
                fail(new IllegalStateException("阿里云百炼 STT 未填写 API Key"));
                return;
            }
            try {
                CLIENT.newWebSocketBuilder()
                        .connectTimeout(Duration.ofSeconds(12))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("User-Agent", "Numen-Minecraft-STT")
                        .buildAsync(URI.create(url), this)
                        .whenComplete((ignored, error) -> {
                            if (error != null) {
                                fail(error);
                            }
                        });
            } catch (RuntimeException error) {
                fail(error);
            }
        }

        @Override
        public void feed(byte[] pcm) {
            if (pcm == null || pcm.length == 0) {
                return;
            }
            synchronized (lock) {
                if (cancelled || terminal || finishRequested) {
                    return;
                }
                if (pending.size() >= MAX_PENDING_CHUNKS) {
                    pending.removeFirst();
                }
                pending.addLast(pcm);
                if (taskStarted) {
                    ensureDrainLocked();
                }
            }
        }

        @Override
        public void finish() {
            synchronized (lock) {
                if (cancelled || terminal || finishRequested) {
                    return;
                }
                finishRequested = true;
                if (taskStarted) {
                    if (pending.isEmpty()) {
                        sendFinishLocked();
                    } else {
                        ensureDrainLocked();
                    }
                }
            }
        }

        @Override
        public void cancel() {
            WebSocket close;
            synchronized (lock) {
                if (cancelled || terminal) {
                    return;
                }
                cancelled = true;
                pending.clear();
                cancelDrainLocked();
                close = socket;
            }
            if (close != null) {
                close.sendClose(WebSocket.NORMAL_CLOSURE, "cancelled");
            }
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            synchronized (lock) {
                if (cancelled || terminal) {
                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "cancelled");
                    return;
                }
                socket = webSocket;
            }
            webSocket.request(1);
            webSocket.sendText(runTaskJson(taskId, model), true)
                    .exceptionally(error -> {
                        fail(error);
                        return null;
                    });
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(
                WebSocket webSocket, CharSequence data, boolean last) {
            String complete = null;
            synchronized (lock) {
                textFrame.append(data);
                if (last) {
                    complete = textFrame.toString();
                    textFrame.setLength(0);
                }
            }
            if (complete != null) {
                try {
                    handle(parseEvent(complete));
                } catch (RuntimeException error) {
                    fail(new IllegalStateException("百炼 STT 返回了无法解析的数据", error));
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onBinary(
                WebSocket webSocket, ByteBuffer data, boolean last) {
            webSocket.request(1);
            return null;
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(
                WebSocket webSocket, int statusCode, String reason) {
            boolean unexpected;
            synchronized (lock) {
                unexpected = !cancelled && !terminal;
            }
            if (unexpected) {
                fail(new IllegalStateException(
                        "百炼 STT 连接提前关闭(" + statusCode + "): " + reason));
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            fail(error);
        }

        private void handle(ServerEvent message) {
            if ("task-started".equals(message.event())) {
                synchronized (lock) {
                    if (cancelled || terminal) {
                        return;
                    }
                    taskStarted = true;
                    if (pending.isEmpty() && finishRequested) {
                        sendFinishLocked();
                    } else {
                        ensureDrainLocked();
                    }
                }
                return;
            }
            if ("result-generated".equals(message.event())) {
                if (message.text().isBlank()) {
                    return;
                }
                String transcript;
                synchronized (lock) {
                    if (cancelled || terminal) {
                        return;
                    }
                    sentences.put(message.sentenceId(), message.text().strip());
                    transcript = transcriptLocked();
                }
                listener.onPartial(transcript);
                return;
            }
            if ("task-failed".equals(message.event())) {
                fail(new IllegalStateException(message.error().isBlank()
                        ? "阿里云百炼 STT 任务失败" : message.error()));
                return;
            }
            if ("task-finished".equals(message.event())) {
                complete();
            }
        }

        private void ensureDrainLocked() {
            if (drainJob != null || !taskStarted || finishSent || terminal || cancelled) {
                return;
            }
            drainJob = DRAINER.scheduleAtFixedRate(
                    this::drainOne, 0, CHUNK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }

        private void drainOne() {
            WebSocket target;
            byte[] chunk;
            synchronized (lock) {
                if (cancelled || terminal || finishSent || !taskStarted) {
                    cancelDrainLocked();
                    return;
                }
                chunk = pending.pollFirst();
                target = socket;
                if (chunk == null) {
                    cancelDrainLocked();
                    if (finishRequested) {
                        sendFinishLocked();
                    }
                    return;
                }
            }
            if (target == null) {
                fail(new IllegalStateException("百炼 STT WebSocket 尚未连接"));
                return;
            }
            target.sendBinary(ByteBuffer.wrap(chunk), true)
                    .exceptionally(error -> {
                        fail(error);
                        return null;
                    });
        }

        private void sendFinishLocked() {
            if (finishSent || socket == null || cancelled || terminal) {
                return;
            }
            finishSent = true;
            cancelDrainLocked();
            socket.sendText(finishTaskJson(taskId), true)
                    .exceptionally(error -> {
                        fail(error);
                        return null;
                    });
        }

        private void complete() {
            WebSocket close;
            String transcript;
            synchronized (lock) {
                if (cancelled || terminal) {
                    return;
                }
                terminal = true;
                cancelDrainLocked();
                transcript = transcriptLocked();
                close = socket;
            }
            if (close != null) {
                close.sendClose(WebSocket.NORMAL_CLOSURE, "done");
            }
            listener.onFinal(transcript);
        }

        private void fail(Throwable error) {
            WebSocket close;
            Throwable cause = unwrap(error);
            synchronized (lock) {
                if (cancelled || terminal) {
                    return;
                }
                terminal = true;
                pending.clear();
                cancelDrainLocked();
                close = socket;
            }
            if (close != null) {
                close.abort();
            }
            listener.onError(cause);
        }

        private String transcriptLocked() {
            StringBuilder out = new StringBuilder();
            for (String sentence : sentences.values()) {
                if (sentence == null || sentence.isBlank()) {
                    continue;
                }
                if (!out.isEmpty() && needsSpace(out, sentence)) {
                    out.append(' ');
                }
                out.append(sentence.strip());
            }
            return out.toString();
        }

        private void cancelDrainLocked() {
            if (drainJob != null) {
                drainJob.cancel(false);
                drainJob = null;
            }
        }
    }

    private static boolean needsSpace(StringBuilder before, String after) {
        char left = before.charAt(before.length() - 1);
        char right = after.charAt(0);
        return left < 128 && right < 128
                && !Character.isWhitespace(left) && !Character.isWhitespace(right);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
