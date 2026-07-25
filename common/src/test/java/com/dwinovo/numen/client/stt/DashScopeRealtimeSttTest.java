package com.dwinovo.numen.client.stt;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashScopeRealtimeSttTest {

    @Test
    void buildsOfficialRunTaskShape() {
        JsonObject root = JsonParser.parseString(
                DashScopeRealtimeStt.runTaskJson("task-1", "fun-asr-realtime"))
                .getAsJsonObject();
        JsonObject header = root.getAsJsonObject("header");
        JsonObject payload = root.getAsJsonObject("payload");
        JsonObject parameters = payload.getAsJsonObject("parameters");

        assertEquals("run-task", header.get("action").getAsString());
        assertEquals("duplex", header.get("streaming").getAsString());
        assertEquals("audio", payload.get("task_group").getAsString());
        assertEquals("asr", payload.get("task").getAsString());
        assertEquals("recognition", payload.get("function").getAsString());
        assertEquals("fun-asr-realtime", payload.get("model").getAsString());
        assertEquals("pcm", parameters.get("format").getAsString());
        assertEquals(16000, parameters.get("sample_rate").getAsInt());
        assertEquals("zh", parameters.getAsJsonArray("language_hints").get(0).getAsString());
    }

    @Test
    void buildsOfficialFinishTaskShape() {
        JsonObject root = JsonParser.parseString(
                DashScopeRealtimeStt.finishTaskJson("task-1")).getAsJsonObject();
        assertEquals("finish-task",
                root.getAsJsonObject("header").get("action").getAsString());
        assertTrue(root.getAsJsonObject("payload").get("input").isJsonObject());
    }

    @Test
    void parsesPartialAndFinalResults() {
        var partial = DashScopeRealtimeStt.parseEvent("""
                {"header":{"event":"result-generated"},"payload":{"output":{"sentence":{
                  "text":"你好","sentence_end":false,"sentence_id":1
                }}}}
                """);
        assertEquals("result-generated", partial.event());
        assertEquals("你好", partial.text());
        assertFalse(partial.sentenceEnd());
        assertEquals(1, partial.sentenceId());

        var completed = DashScopeRealtimeStt.parseEvent("""
                {"header":{"event":"result-generated"},"payload":{"output":{"sentence":{
                  "text":"你好，Numen。","sentence_end":true,"sentence_id":1
                }}}}
                """);
        assertTrue(completed.sentenceEnd());
        assertEquals("你好，Numen。", completed.text());
    }

    @Test
    void parsesTaskFailure() {
        var event = DashScopeRealtimeStt.parseEvent("""
                {"header":{"event":"task-failed","error_message":"invalid api key"},"payload":{}}
                """);
        assertEquals("task-failed", event.event());
        assertEquals("invalid api key", event.error());
    }

    @Test
    void normalizesEndpoint() {
        assertEquals(DashScopeRealtimeStt.DEFAULT_BASE,
                DashScopeRealtimeStt.composeUrl(""));
        assertEquals("wss://dashscope.aliyuncs.com/api-ws/v1/inference/",
                DashScopeRealtimeStt.composeUrl(
                        "https://dashscope.aliyuncs.com/api-ws/v1/inference"));
    }
}
