package com.bipros.ai.voice;

import com.bipros.ai.provider.LlmProviderConfig;
import com.bipros.ai.provider.LlmProviderConfigRepository;
import com.bipros.ai.provider.crypto.ApiKeyCipher;
import com.bipros.common.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Locale;
import java.util.Map;

/**
 * Speech-to-text using OpenAI-compatible Whisper API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeechToTextService {

    /**
     * Force Whisper's transcription language to English. With no {@code language} hint, Whisper
     * auto-detects, and on Indian-accented English it frequently mis-detects Hindi and returns
     * Devanagari gibberish (e.g. "प्लीज़ प्रवाज़ार का हमेंद्रव" for "please, supervisor is Hemendra"),
     * which the downstream form-fill LLM then can't map to any field. Every DPR reference list
     * (activities, supervisors, BOQ) is English, so English transcription is a hard requirement.
     */
    private static final String TRANSCRIBE_LANGUAGE = "en";

    private final LlmProviderConfigRepository configRepository;
    private final ApiKeyCipher apiKeyCipher;
    private final RestTemplate restTemplate = new RestTemplate();

    public String transcribe(byte[] audioBytes, String filename, String mimeType) {
        return transcribe(audioBytes, filename, mimeType, null);
    }

    /**
     * @param promptHint optional domain vocabulary (trades, equipment, supervisor / activity names)
     *   that biases Whisper's decoding. Without it, accented dictation is frequently mis-heard —
     *   e.g. "10 masons" → "10 machines" (which then lands in Equipment, not Manpower), or
     *   "chainage" → "Chennai". Whisper uses only the last ~224 tokens of the prompt.
     */
    public String transcribe(byte[] audioBytes, String filename, String mimeType, String promptHint) {
        LlmProviderConfig cfg = configRepository.findByIsDefaultTrueAndIsActiveTrue()
                .orElseThrow(() -> new BusinessRuleException("AI_NO_PROVIDER", "No active LLM provider configured"));

        String baseUrl = cfg.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String url = baseUrl + "/audio/transcriptions";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKeyCipher.decrypt(cfg.getApiKeyIv(), cfg.getApiKeyCiphertext(), cfg.getApiKeyVersion()));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // Whisper picks the audio format from the filename extension, not the bytes. The browser
        // always uploads the blob as "voice.webm", but Safari's MediaRecorder actually produces
        // audio/mp4 — so a mp4 payload named .webm is rejected with "Invalid file format". Derive
        // the extension from the real content type so every recorder (Chrome=webm, Safari=mp4) works.
        String uploadName = filenameForMime(filename, mimeType);
        ByteArrayResource audioResource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return uploadName;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", audioResource);
        body.add("model", "whisper-1");
        body.add("language", TRANSCRIBE_LANGUAGE);
        if (promptHint != null && !promptHint.isBlank()) {
            body.add("prompt", promptHint);
        }
        body.add("response_format", "json");

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map result = response.getBody();
            if (result != null && result.get("text") != null) {
                return result.get("text").toString();
            }
            throw new BusinessRuleException("STT_EMPTY", "Transcription returned empty text");
        } catch (Exception e) {
            log.error("STT transcription failed", e);
            throw new BusinessRuleException("STT_FAILED", "Speech-to-text failed: " + e.getMessage());
        }
    }

    /**
     * Give the upload a filename whose extension matches its actual content type, so Whisper (which
     * sniffs format from the extension) accepts it regardless of what the browser named the blob.
     * Falls back to the given filename when the content type is unknown.
     */
    static String filenameForMime(String filename, String mimeType) {
        String ext = extensionForMime(mimeType);
        if (ext == null) {
            return (filename == null || filename.isBlank()) ? "audio.webm" : filename;
        }
        String base = (filename == null || filename.isBlank()) ? "audio" : filename;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return base + "." + ext;
    }

    /** Map a browser MediaRecorder content type to a Whisper-accepted file extension. */
    private static String extensionForMime(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return null;
        }
        String m = mimeType.toLowerCase(Locale.ROOT);
        int semi = m.indexOf(';'); // strip "; codecs=opus"
        if (semi >= 0) {
            m = m.substring(0, semi).trim();
        }
        return switch (m) {
            case "audio/webm", "video/webm" -> "webm";
            case "audio/mp4", "video/mp4" -> "mp4";
            case "audio/x-m4a", "audio/m4a", "audio/aac" -> "m4a";
            case "audio/mpeg", "audio/mp3" -> "mp3";
            case "audio/wav", "audio/x-wav", "audio/wave" -> "wav";
            case "audio/ogg", "application/ogg", "audio/oga" -> "ogg";
            case "audio/flac", "audio/x-flac" -> "flac";
            default -> null;
        };
    }
}
