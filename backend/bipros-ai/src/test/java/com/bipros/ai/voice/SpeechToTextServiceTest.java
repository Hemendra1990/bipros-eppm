package com.bipros.ai.voice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpeechToTextServiceTest {

    @Test
    void safariMp4RecordingNamedWebmGetsAnMp4Extension() {
        // The browser always uploads the blob as "voice.webm", but Safari records audio/mp4.
        // Whisper picks the format from the extension, so we must rename it to .mp4 or it 400s.
        assertThat(SpeechToTextService.filenameForMime("voice.webm", "audio/mp4"))
            .isEqualTo("voice.mp4");
    }

    @Test
    void chromeWebmRecordingKeepsWebmEvenWithCodecsSuffix() {
        assertThat(SpeechToTextService.filenameForMime("voice.webm", "audio/webm;codecs=opus"))
            .isEqualTo("voice.webm");
    }

    @Test
    void mapsCommonAudioTypesToWhisperExtensions() {
        assertThat(SpeechToTextService.filenameForMime("voice.webm", "audio/x-m4a")).isEqualTo("voice.m4a");
        assertThat(SpeechToTextService.filenameForMime("voice.webm", "audio/mpeg")).isEqualTo("voice.mp3");
        assertThat(SpeechToTextService.filenameForMime("voice.webm", "audio/wav")).isEqualTo("voice.wav");
        assertThat(SpeechToTextService.filenameForMime("voice.webm", "audio/ogg")).isEqualTo("voice.ogg");
    }

    @Test
    void unknownMimeFallsBackToGivenFilename() {
        assertThat(SpeechToTextService.filenameForMime("voice.webm", "application/octet-stream"))
            .isEqualTo("voice.webm");
        assertThat(SpeechToTextService.filenameForMime(null, null)).isEqualTo("audio.webm");
    }
}
