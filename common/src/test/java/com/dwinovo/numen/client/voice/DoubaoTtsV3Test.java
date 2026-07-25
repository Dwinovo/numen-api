package com.dwinovo.numen.client.voice;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DoubaoTtsV3Test {

    @Test
    void composeUrlAcceptsRootOrCompleteEndpoint() {
        assertEquals(DoubaoTtsV3.DEFAULT_BASE, DoubaoTtsV3.composeUrl(""));
        assertEquals(DoubaoTtsV3.DEFAULT_BASE,
                DoubaoTtsV3.composeUrl("https://openspeech.bytedance.com"));
        assertEquals(DoubaoTtsV3.DEFAULT_BASE,
                DoubaoTtsV3.composeUrl(DoubaoTtsV3.DEFAULT_BASE + "/"));
    }

    @Test
    void bodyUsesV3ShapeAndPcm() {
        JsonObject body = DoubaoTtsV3.buildBody(
                "你好，Numen。", "app-123", "zh_female_vv_uranus_bigtts");
        assertEquals("app-123", body.getAsJsonObject("user").get("uid").getAsString());
        JsonObject params = body.getAsJsonObject("req_params");
        assertEquals("你好，Numen。", params.get("text").getAsString());
        assertEquals("zh_female_vv_uranus_bigtts", params.get("speaker").getAsString());
        JsonObject audio = params.getAsJsonObject("audio_params");
        assertEquals("pcm", audio.get("format").getAsString());
        assertEquals(24_000, audio.get("sample_rate").getAsInt());
    }

    @Test
    void chunkedPcmBecomesDecodableWav() throws Exception {
        byte[] first = new byte[]{1, 2};
        byte[] second = new byte[]{3, 4};
        String response = "{\"code\":0,\"data\":\"" + Base64.getEncoder().encodeToString(first) + "\"}\n"
                + "{\"code\":0,\"data\":\"" + Base64.getEncoder().encodeToString(second) + "\"}\n"
                + "{\"code\":20000000,\"message\":\"OK\"}\n";

        byte[] wav = DoubaoTtsV3.parseChunkedResponse(response);
        PcmAudio decoded = WavCodec.decode(wav);
        assertEquals(24_000, decoded.sampleRate());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, decoded.data());
    }

    @Test
    void reportsBusinessErrorsAndInvalidPayloads() {
        var business = assertThrows(IllegalStateException.class,
                () -> DoubaoTtsV3.parseChunkedResponse(
                        "{\"code\":55000000,\"message\":\"resource mismatch\"}\n"));
        assertEquals("豆包 TTS v3 错误 55000000: resource mismatch", business.getMessage());

        assertThrows(IllegalStateException.class,
                () -> DoubaoTtsV3.parseChunkedResponse(""));
        assertThrows(IllegalStateException.class,
                () -> DoubaoTtsV3.parseChunkedResponse(
                        "{\"code\":0,\"data\":\"not-base64!\"}\n"));
    }
}
