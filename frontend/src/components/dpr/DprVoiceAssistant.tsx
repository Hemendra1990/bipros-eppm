"use client";

import { useEffect, useRef, useState } from "react";
import { Lock, Mic, Square, Sparkles, X } from "lucide-react";
import { dprApi, type DprVoicePatch, type DprVoiceTurn } from "@/lib/api/dprApi";

interface Props {
  projectId: string;
  /** When the DPR has been saved, the assistant can also caption already-uploaded photos. */
  dprId: string | null;
  /** Snapshot of the form's current state. Lifted via a getter so we always send fresh state. */
  getState: () => unknown;
  /** Apply a patch to the form. The form's deep-merge logic is the source of truth here. */
  applyPatch: (patch: DprVoicePatch) => void;
}

interface SessionTurn {
  role: "user" | "assistant";
  content: string;
}

/**
 * Voice form-fill assistant. Click the mic to start recording; click again to stop. The audio
 * is shipped to {@code POST /v1/projects/{projectId}/dpr/voice-fill} together with the form's
 * current state and the running session history. The backend transcribes via Whisper, calls an
 * LLM with a structured-output schema, and returns either a patch to merge or a follow-up
 * question (or both). The assistant keeps the question visible and re-arms the mic so the
 * supervisor can answer hands-free.
 *
 * <p>Mic permission, MediaRecorder lifecycle, and stream cleanup follow the same pattern as
 * {@code AiChatPanel.tsx} so behavior is consistent across the app.
 */
export function DprVoiceAssistant({ projectId, dprId, getState, applyPatch }: Props) {
  const [isRecording, setIsRecording] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [history, setHistory] = useState<SessionTurn[]>([]);
  const [followUp, setFollowUp] = useState<string | null>(null);
  const [lastTranscript, setLastTranscript] = useState<string | null>(null);
  const [lastReply, setLastReply] = useState<string | null>(null);
  const [micError, setMicError] = useState<string | null>(null);
  const [permissionState, setPermissionState] = useState<"unknown" | "granted" | "prompt" | "denied">("unknown");
  const [showPanel, setShowPanel] = useState(false);

  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);

  useEffect(() => {
    return () => {
      // Component unmount mid-recording → release the device.
      streamRef.current?.getTracks().forEach((t) => t.stop());
    };
  }, []);

  const beginCapture = async () => {
    setMicError(null);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      setPermissionState("granted");
      streamRef.current = stream;
      const mimeType = MediaRecorder.isTypeSupported("audio/webm")
        ? "audio/webm"
        : MediaRecorder.isTypeSupported("audio/mp4")
          ? "audio/mp4"
          : "";
      const recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);
      mediaRecorderRef.current = recorder;

      const chunks: BlobPart[] = [];
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunks.push(e.data);
      };
      recorder.onstop = async () => {
        const blob = new Blob(chunks, { type: recorder.mimeType || "audio/webm" });
        stream.getTracks().forEach((t) => t.stop());
        streamRef.current = null;
        await sendRecording(blob);
      };
      recorder.start();
      setIsRecording(true);
    } catch (err: unknown) {
      const name = err instanceof Error ? err.name : "";
      if (name === "NotAllowedError" || name === "SecurityError") {
        setPermissionState("denied");
        setMicError(null); // The "denied" panel speaks for itself; no separate error string.
      } else if (name === "NotFoundError" || name === "OverconstrainedError") {
        setMicError("No microphone found on this device.");
      } else if (name === "NotReadableError") {
        setMicError("Microphone is in use by another application.");
      } else {
        setMicError("Could not start the microphone.");
      }
      setShowPanel(true);
    }
  };

  /**
   * Entry point — always calls {@code getUserMedia} and lets it be the source of truth. Chrome's
   * Permissions API can lag the actual state (notably right after the user toggles the setting via
   * the address bar), so we don't consult it at all here. If {@code getUserMedia} succeeds we set
   * permissionState=granted; if it fails with NotAllowedError we set it to denied and show the
   * recovery panel.
   */
  const startRecording = async () => {
    setShowPanel(true);
    await beginCapture();
  };

  const recheckPermission = async () => {
    // Drop the cached "denied" so the Try again click goes through beginCapture and trusts
    // getUserMedia's verdict (which is the canonical state, even when permissions API is stale).
    setPermissionState("unknown");
    await beginCapture();
  };

  const reloadPage = () => {
    if (typeof window !== "undefined") window.location.reload();
  };

  const stopRecording = () => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== "inactive") {
      mediaRecorderRef.current.stop();
    }
    setIsRecording(false);
  };

  const sendRecording = async (blob: Blob) => {
    setIsProcessing(true);
    try {
      const state = getState();
      const wireHistory: DprVoiceTurn[] = history;
      const result = await dprApi.voiceFill(projectId, blob, state, wireHistory, dprId);

      setLastTranscript(result.transcript);
      setLastReply(result.assistantTurn.content);
      setFollowUp(result.followUpQuestion);

      // Merge patch into the form state via the parent callback.
      if (result.patch) applyPatch(result.patch);

      // Append both turns so the next call has the full context.
      setHistory((prev) => [
        ...prev,
        { role: "user", content: result.transcript },
        { role: result.assistantTurn.role, content: result.assistantTurn.content },
      ]);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "voice fill failed";
      setMicError(msg);
    } finally {
      setIsProcessing(false);
    }
  };

  const dismissPanel = () => {
    setShowPanel(false);
    setFollowUp(null);
    setLastTranscript(null);
    setLastReply(null);
  };

  const resetSession = () => {
    setHistory([]);
    setFollowUp(null);
    setLastTranscript(null);
    setLastReply(null);
    setMicError(null);
  };

  return (
    <div className="relative inline-flex items-center gap-2">
      <button
        type="button"
        onClick={isRecording ? stopRecording : startRecording}
        disabled={isProcessing}
        className={`inline-flex items-center gap-1.5 rounded-md border px-3 py-1.5 text-sm font-semibold transition disabled:opacity-60 ${
          isRecording
            ? "border-burgundy bg-burgundy/10 text-burgundy animate-pulse"
            : "border-hairline bg-paper text-charcoal hover:bg-ivory"
        }`}
        title={
          isRecording
            ? "Stop recording"
            : isProcessing
              ? "Processing…"
              : "Voice fill — dictate the day's work"
        }
      >
        {isRecording ? (
          <Square className="h-4 w-4" />
        ) : (
          <Sparkles className="h-4 w-4 text-gold" />
        )}
        {isRecording ? "Stop" : isProcessing ? "Working…" : "Voice fill"}
      </button>

      {showPanel && (
        <div className="absolute right-0 top-full z-30 mt-2 w-80 rounded-md border border-hairline bg-paper shadow-lg">
          <div className="flex items-start justify-between gap-2 border-b border-hairline px-3 py-2">
            <div className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-slate">
              <Mic className="h-3.5 w-3.5 text-gold" />
              Voice fill
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={resetSession}
                className="text-[11px] font-semibold text-slate hover:text-charcoal"
                title="Start a fresh voice session"
              >
                Reset
              </button>
              <button
                type="button"
                onClick={dismissPanel}
                className="text-slate hover:text-charcoal"
                aria-label="Dismiss"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            </div>
          </div>
          <div className="max-h-72 space-y-2 overflow-y-auto px-3 py-2 text-xs text-charcoal">
            {permissionState === "denied" && (
              <div className="space-y-2 rounded border border-burgundy/30 bg-burgundy/10 px-3 py-2 text-burgundy">
                <div className="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide">
                  <Lock className="h-3.5 w-3.5" />
                  Microphone blocked
                </div>
                <div className="text-charcoal">
                  Voice fill needs your microphone. To enable it:
                </div>
                <ol className="list-decimal space-y-1 pl-4 text-charcoal">
                  <li>Click the lock / tune icon in the address bar.</li>
                  <li>Find <span className="font-semibold">Microphone</span> and switch it to <span className="font-semibold">Allow</span>.</li>
                  <li>Reload the page, or click <span className="font-semibold">Try again</span> below.</li>
                </ol>
                <div className="mt-1 flex flex-wrap gap-1.5">
                  <button
                    type="button"
                    onClick={recheckPermission}
                    className="inline-flex items-center gap-1 rounded border border-burgundy/40 bg-paper px-2 py-1 text-[11px] font-semibold text-burgundy hover:bg-burgundy/10"
                  >
                    Try again
                  </button>
                  <button
                    type="button"
                    onClick={reloadPage}
                    className="inline-flex items-center gap-1 rounded border border-burgundy/40 bg-paper px-2 py-1 text-[11px] font-semibold text-burgundy hover:bg-burgundy/10"
                    title="If you just changed the site permission, Chrome usually needs a reload to apply it."
                  >
                    Reload page
                  </button>
                </div>
              </div>
            )}
            {micError && permissionState !== "denied" && (
              <div className="rounded border border-burgundy/30 bg-burgundy/10 px-2 py-1.5 text-burgundy">
                {micError}
              </div>
            )}
            {isRecording && (
              <div className="rounded border border-burgundy/30 bg-burgundy/10 px-2 py-1.5 text-burgundy">
                Listening… click <span className="font-semibold">Stop</span> when done.
              </div>
            )}
            {isProcessing && !isRecording && (
              <div className="text-slate">Transcribing and filling the form…</div>
            )}
            {lastTranscript && (
              <div>
                <div className="text-[10px] font-semibold uppercase tracking-wide text-slate">You said</div>
                <div className="italic text-charcoal">&ldquo;{lastTranscript}&rdquo;</div>
              </div>
            )}
            {lastReply && (
              <div>
                <div className="text-[10px] font-semibold uppercase tracking-wide text-slate">Assistant</div>
                <div>{lastReply}</div>
              </div>
            )}
            {followUp && (
              <div className="rounded border border-gold/40 bg-gold/10 px-2 py-1.5">
                <div className="text-[10px] font-semibold uppercase tracking-wide text-gold-ink">Follow-up</div>
                <div>{followUp}</div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
