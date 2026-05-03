package com.bipros.ai.provider;

import com.bipros.ai.provider.crypto.ApiKeyCipher;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OpenAiCompatibleProvider#parseChatResponse(String)}.
 * Asserts that empty / refusal / length-exhausted responses surface a specific,
 * actionable error rather than the silent empty-content behavior that masked
 * the reasoning-model token-exhaustion incident.
 */
class OpenAiCompatibleProviderParseTest {

    private static OpenAiCompatibleProvider newProvider() {
        // ApiKeyCipher only needed to satisfy the constructor; not exercised here.
        MockEnvironment env = new MockEnvironment();
        ApiKeyCipher cipher = new ApiKeyCipher(env);
        byte[] kek = new byte[32];
        ReflectionTestUtils.setField(cipher, "kekBase64", Base64.getEncoder().encodeToString(kek));
        cipher.init();
        return new OpenAiCompatibleProvider(cipher, new ModelCapabilityRegistry());
    }

    @Test
    void normalResponseParses() {
        String json = """
                {
                  "model": "gpt-4o",
                  "choices": [{
                    "finish_reason": "stop",
                    "message": {"role": "assistant", "content": "{\\"ok\\":true}"}
                  }],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
                }
                """;
        LlmProvider.ChatResponse resp = newProvider().parseChatResponse(json);
        assertThat(resp.content()).isEqualTo("{\"ok\":true}");
        assertThat(resp.model()).isEqualTo("gpt-4o");
        assertThat(resp.usage().totalTokens()).isEqualTo(15);
    }

    @Test
    void emptyContentWithFinishReasonLengthSurfacesActionableMessage() {
        // Reasoning-model token exhaustion: the entire token budget was spent on
        // hidden chain-of-thought, leaving zero for visible output.
        String json = """
                {
                  "model": "gpt-5.5",
                  "choices": [{
                    "finish_reason": "length",
                    "message": {"role": "assistant", "content": ""}
                  }]
                }
                """;
        assertThatThrownBy(() -> newProvider().parseChatResponse(json))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Increase Max Tokens");
    }

    @Test
    void emptyContentWithContentFilterSurfacesSafetyMessage() {
        String json = """
                {
                  "choices": [{
                    "finish_reason": "content_filter",
                    "message": {"role": "assistant", "content": null}
                  }]
                }
                """;
        assertThatThrownBy(() -> newProvider().parseChatResponse(json))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("safety filter");
    }

    @Test
    void refusalIsSurfacedVerbatim() {
        String json = """
                {
                  "choices": [{
                    "finish_reason": "stop",
                    "message": {
                      "role": "assistant",
                      "content": null,
                      "refusal": "I cannot help with that request."
                    }
                  }]
                }
                """;
        assertThatThrownBy(() -> newProvider().parseChatResponse(json))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("I cannot help with that request");
    }

    @Test
    void emptyChoicesArrayThrows() {
        String json = "{\"choices\":[],\"error\":{\"message\":\"bad request\"}}";
        assertThatThrownBy(() -> newProvider().parseChatResponse(json))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("bad request");
    }

    @Test
    void emptyResponseMessageMappingIsExhaustiveForKnownReasons() {
        assertThat(OpenAiCompatibleProvider.emptyResponseMessage("length"))
                .contains("Increase Max Tokens");
        assertThat(OpenAiCompatibleProvider.emptyResponseMessage("max_output_tokens"))
                .contains("Increase Max Tokens");
        assertThat(OpenAiCompatibleProvider.emptyResponseMessage("content_filter"))
                .contains("safety filter");
        assertThat(OpenAiCompatibleProvider.emptyResponseMessage("stop"))
                .contains("response-format mismatch");
        assertThat(OpenAiCompatibleProvider.emptyResponseMessage(""))
                .contains("See backend logs");
        assertThat(OpenAiCompatibleProvider.emptyResponseMessage("weird_new_reason"))
                .contains("weird_new_reason");
    }

    @Test
    void isOpenAiBaseUrlAcceptsCanonicalAndCustomPaths() {
        assertThat(OpenAiCompatibleProvider.isOpenAiBaseUrl("https://api.openai.com/v1")).isTrue();
        assertThat(OpenAiCompatibleProvider.isOpenAiBaseUrl("https://api.openai.com/v1/chat/completions")).isTrue();
        assertThat(OpenAiCompatibleProvider.isOpenAiBaseUrl("https://API.OpenAI.com/v1")).isTrue();
        assertThat(OpenAiCompatibleProvider.isOpenAiBaseUrl("https://api.together.xyz/v1")).isFalse();
        assertThat(OpenAiCompatibleProvider.isOpenAiBaseUrl("https://my-azure.openai.azure.com")).isFalse();
        assertThat(OpenAiCompatibleProvider.isOpenAiBaseUrl(null)).isFalse();
    }

    @Test
    void responseFormatChatShapeIsFlattenedForResponsesApi() throws Exception {
        // Schema as built by WbsAiGenerationService.buildResponseSchema() — Chat Completions wrapping.
        com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode chatShape = m.createObjectNode();
        chatShape.put("type", "json_schema");
        com.fasterxml.jackson.databind.node.ObjectNode js = m.createObjectNode();
        js.put("name", "wbs_generation");
        js.put("strict", true);
        com.fasterxml.jackson.databind.node.ObjectNode schema = m.createObjectNode();
        schema.put("type", "object");
        js.set("schema", schema);
        chatShape.set("json_schema", js);

        com.fasterxml.jackson.databind.JsonNode flat = newProvider().toResponsesFormat(chatShape);

        // Responses API expects { type, name, strict, schema } at the top level — no nested json_schema.
        assertThat(flat.path("type").asText()).isEqualTo("json_schema");
        assertThat(flat.path("name").asText()).isEqualTo("wbs_generation");
        assertThat(flat.path("strict").asBoolean()).isTrue();
        assertThat(flat.path("schema").path("type").asText()).isEqualTo("object");
        assertThat(flat.has("json_schema")).isFalse();
    }

    @Test
    void responsesApiIncompleteWithPartialContentSurfacesTruncationError() throws Exception {
        // Mirrors the real wire-level failure on gpt-5.5: the model started writing
        // a long JSON WBS, hit max_output_tokens budget mid-string, and the Responses
        // API returned status="incomplete" with partial content. Without this branch
        // we'd try to objectMapper.readTree(...) the partial JSON and surface a
        // useless "Unexpected end-of-input" error instead of "Increase Max Tokens".
        String json = """
                {
                  "model": "gpt-5.5",
                  "status": "incomplete",
                  "incomplete_details": {"reason": "max_output_tokens"},
                  "output": [{
                    "type": "message",
                    "content": [{
                      "type": "output_text",
                      "text": "{\\"rationale\\":\\"...\\",\\"nodes\\":[{\\"code\\":\\"PRJ.1\\","
                    }]
                  }]
                }
                """;
        // The provider's parseResponsesResponse is private but reachable via the
        // public entry; we call the private method via reflection to keep the
        // test focused on parsing rather than HTTP wiring.
        java.lang.reflect.Method m = OpenAiCompatibleProvider.class
                .getDeclaredMethod("parseResponsesResponse", String.class);
        m.setAccessible(true);
        OpenAiCompatibleProvider provider = newProvider();
        try {
            m.invoke(provider, json);
            org.junit.jupiter.api.Assertions.fail("Expected truncation to be detected");
        } catch (java.lang.reflect.InvocationTargetException ite) {
            assertThat(ite.getCause()).isInstanceOf(RuntimeException.class);
            assertThat(ite.getCause().getMessage()).contains("Increase Max Tokens");
        }
    }

    @Test
    void responseFormatAlreadyInResponsesShapeIsLeftAlone() {
        com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode flat = m.createObjectNode();
        flat.put("type", "json_schema");
        flat.put("name", "x");
        flat.put("strict", true);
        flat.set("schema", m.createObjectNode());

        com.fasterxml.jackson.databind.JsonNode out = newProvider().toResponsesFormat(flat);
        assertThat(out).isSameAs(flat);
    }
}
