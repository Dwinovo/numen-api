package com.dwinovo.numen.agent.provider;

import com.dwinovo.numen.agent.llm.VisualObservation;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAIProviderVisionTest {

    @Test
    void buildsOpenAiMultimodalContentArrayWithDataUrlAndDetail() {
        VisualObservation frame = new VisualObservation("image/jpeg", "YWJj", 1280, 720, "high");
        JsonObject message = new OpenAIProvider().buildVisionUserMessage("look", frame);

        assertEquals("user", message.get("role").getAsString());
        JsonArray content = message.getAsJsonArray("content");
        assertEquals("text", content.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("look", content.get(0).getAsJsonObject().get("text").getAsString());
        JsonObject image = content.get(1).getAsJsonObject();
        assertEquals("image_url", image.get("type").getAsString());
        assertEquals("data:image/jpeg;base64,YWJj",
                image.getAsJsonObject("image_url").get("url").getAsString());
        assertEquals("high", image.getAsJsonObject("image_url").get("detail").getAsString());
        assertTrue(frame.dataUrl().startsWith("data:image/jpeg;base64,"));
    }
}
