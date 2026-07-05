"use client";

import { useEffect, useRef, useState } from "react";
import { Check, Loader2, Lock, Mic, Sparkles, Square, X } from "lucide-react";
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

const EQ_BARS = [0, 1, 2, 3, 4, 5, 6];

function fmtTime(sec: number): string {
  return `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, "0")}`;
}

/** Voice-reactive-looking equalizer — CSS only, so there's no AudioContext lifecycle to leak. */
function Equalizer() {
  return (
    <div className="flex h-7 items-end justify-center gap-[3px]" aria-hidden="true">
      {EQ_BARS.map((i) => (
        <span
          key={i}
          className="vf-eq-bar w-[3px] rounded-full bg-burgundy"
          style={{ animationDelay: `${(i % 4) * 120}ms`, animationDuration: `${820 + (i % 3) * 160}ms` }}
        />
      ))}
    </div>
  );
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
  const [elapsedSec, setElapsedSec] = useState(0);
  const [procStage, setProcStage] = useState(0);

  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);

  useEffect(() => {
    return () => {
      // Component unmount mid-recording → release the device.
      streamRef.current?.getTracks().forEach((t) => t.stop());
    };
  }, []);

  // Running timer while recording; resets each session.
  useEffect(() => {
    if (!isRecording) return;
    setElapsedSec(0);
    const id = setInterval(() => setElapsedSec((s) => s + 1), 1000);
    return () => clearInterval(id);
  }, [isRecording]);

  // Two honest processing stages: transcription then form-fill (both really happen server-side).
  useEffect(() => {
    if (!isProcessing) {
      setProcStage(0);
      return;
    }
    const id = setTimeout(() => setProcStage(1), 1400);
    return () => clearTimeout(id);
  }, [isProcessing]);

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
   * recovery panel. Clears the previous turn so the recording view starts clean.
   */
  const startRecording = async () => {
    setShowPanel(true);
    setLastTranscript(null);
    setLastReply(null);
    setFollowUp(null);
    setMicError(null);
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

  const hasResult = !!lastTranscript && !isRecording && !isProcessing;
  const showIdle =
    !isRecording && !isProcessing && !hasResult && !micError && permissionState !== "denied";

  return (
    <div className="relative inline-flex items-center gap-2">
      <button
        type="button"
        onClick={isRecording ? stopRecording : startRecording}
        disabled={isProcessing}
        aria-label={isRecording ? "Stop recording" : "Voice fill"}
        className={`inline-flex items-center gap-1.5 rounded-md border px-3 py-1.5 text-sm font-semibold transition focus:outline-none focus-visible:ring-2 focus-visible:ring-gold/50 disabled:opacity-60 ${
          isRecording
            ? "border-burgundy bg-burgundy/10 text-burgundy"
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
          <Square className="h-4 w-4 motion-safe:animate-pulse" />
        ) : isProcessing ? (
          <Loader2 className="h-4 w-4 text-gold motion-safe:animate-spin" />
        ) : (
          <Sparkles className="h-4 w-4 text-gold" />
        )}
        {isRecording ? `Stop · ${fmtTime(elapsedSec)}` : isProcessing ? "Working…" : "Voice fill"}
      </button>

      {showPanel && (
        <div className="absolute right-0 top-full z-30 mt-2 w-80 overflow-hidden rounded-lg border border-hairline bg-paper shadow-xl">
          <style>{`
            @keyframes vf-eq { 0%, 100% { transform: scaleY(0.28); } 50% { transform: scaleY(1); } }
            .vf-eq-bar { height: 100%; transform-origin: bottom; animation-name: vf-eq; animation-timing-function: ease-in-out; animation-iteration-count: infinite; }
            @keyframes vf-slide { 0% { transform: translateX(-120%); } 100% { transform: translateX(320%); } }
            .vf-slide { animation: vf-slide 1.15s ease-in-out infinite; }
            @media (prefers-reduced-motion: reduce) {
              .vf-eq-bar { animation: none; transform: scaleY(0.7); }
              .vf-slide { animation: none; left: 33%; }
            }
          `}</style>

          <div className="flex items-center justify-between gap-2 border-b border-hairline px-3 py-2">
            <div className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-slate">
              <Mic className="h-3.5 w-3.5 text-gold" />
              Voice fill
            </div>
            <div className="flex items-center gap-2.5">
              {history.length > 0 && (
                <button
                  type="button"
                  onClick={resetSession}
                  className="text-[11px] font-semibold text-slate hover:text-charcoal"
                  title="Start a fresh voice session"
                >
                  Reset
                </button>
              )}
              <button
                type="button"
                onClick={dismissPanel}
                className="text-slate hover:text-charcoal"
                aria-label="Close voice fill"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            </div>
          </div>

          <div
            className="max-h-80 overflow-y-auto px-3 py-3 text-xs text-charcoal"
            aria-live="polite"
          >
            {/* 1 — Microphone blocked */}
            {permissionState === "denied" && (
              <div className="space-y-2 rounded-md border border-burgundy/30 bg-burgundy/10 px-3 py-2 text-burgundy">
                <div className="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide">
                  <Lock className="h-3.5 w-3.5" />
                  Microphone blocked
                </div>
                <div className="text-charcoal">Voice fill needs your microphone. To enable it:</div>
                <ol className="list-decimal space-y-1 pl-4 text-charcoal">
                  <li>Click the lock / tune icon in the address bar.</li>
                  <li>
                    Find <span className="font-semibold">Microphone</span> and switch it to{" "}
                    <span className="font-semibold">Allow</span>.
                  </li>
                  <li>Reload the page, or click <span className="font-semibold">Try again</span>.</li>
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

            {/* 2 — Error (non-permission) */}
            {micError && permissionState !== "denied" && (
              <div className="space-y-2 rounded-md border border-burgundy/30 bg-burgundy/10 px-3 py-2">
                <div className="text-burgundy">{micError}</div>
                <button
                  type="button"
                  onClick={startRecording}
                  className="inline-flex items-center gap-1.5 rounded border border-burgundy/40 bg-paper px-2 py-1 text-[11px] font-semibold text-burgundy hover:bg-burgundy/10"
                >
                  <Mic className="h-3.5 w-3.5" /> Try again
                </button>
              </div>
            )}

            {/* 3 — Recording */}
            {isRecording && (
              <div className="flex flex-col items-center gap-3 py-2">
                <div className="relative flex h-14 w-14 items-center justify-center">
                  <span
                    className="absolute inline-flex h-full w-full rounded-full border border-burgundy/40 motion-safe:animate-ping motion-reduce:hidden"
                    aria-hidden="true"
                  />
                  <span className="relative inline-flex h-12 w-12 items-center justify-center rounded-full border border-burgundy/30 bg-burgundy/10">
                    <Mic className="h-5 w-5 text-burgundy" />
                  </span>
                </div>
                <Equalizer />
                <div className="flex items-center gap-2">
                  <span className="text-sm font-semibold text-charcoal">Listening</span>
                  <span className="rounded bg-burgundy/10 px-1.5 py-0.5 text-[11px] font-semibold tabular-nums text-burgundy">
                    {fmtTime(elapsedSec)}
                  </span>
                </div>
                <button
                  type="button"
                  onClick={stopRecording}
                  className="inline-flex w-full items-center justify-center gap-1.5 rounded-md bg-burgundy px-3 py-2 text-sm font-semibold text-white transition hover:brightness-110 focus:outline-none focus-visible:ring-2 focus-visible:ring-burgundy/40"
                >
                  <Square className="h-4 w-4" /> Stop &amp; fill
                </button>
              </div>
            )}

            {/* 4 — Processing */}
            {isProcessing && !isRecording && (
              <div className="flex flex-col items-center gap-3 py-3">
                <span className="inline-flex h-11 w-11 items-center justify-center rounded-full border border-gold/40 bg-gold/10">
                  <Loader2 className="h-5 w-5 text-gold-ink motion-safe:animate-spin" />
                </span>
                <div className="relative h-1.5 w-full overflow-hidden rounded-full bg-ivory">
                  <span className="vf-slide absolute inset-y-0 left-0 w-1/3 rounded-full bg-gold" aria-hidden="true" />
                </div>
                <div className="text-sm font-medium text-charcoal">
                  {procStage === 0 ? "Transcribing your notes…" : "Filling the form…"}
                </div>
                <div className="text-[11px] text-slate">This usually takes a few seconds.</div>
              </div>
            )}

            {/* 5 — Result */}
            {hasResult && (
              <div className="space-y-2.5">
                <div className="flex items-center gap-1.5">
                  <span className="inline-flex h-5 w-5 items-center justify-center rounded-full bg-gold/15">
                    <Check className="h-3.5 w-3.5 text-gold-ink" />
                  </span>
                  <span className="text-xs font-semibold text-charcoal">Form updated</span>
                </div>

                <div className="rounded-md bg-ivory px-2.5 py-2">
                  <div className="mb-0.5 flex items-center gap-1 text-[10px] font-semibold uppercase tracking-wide text-slate">
                    <Mic className="h-3 w-3" /> You said
                  </div>
                  <div className="italic leading-snug text-charcoal">&ldquo;{lastTranscript}&rdquo;</div>
                </div>

                {lastReply && (
                  <div className="px-0.5">
                    <div className="mb-0.5 text-[10px] font-semibold uppercase tracking-wide text-slate">
                      Assistant
                    </div>
                    <div className="leading-snug text-charcoal">{lastReply}</div>
                  </div>
                )}

                {followUp ? (
                  <div className="rounded-md border border-gold/40 bg-gold/10 px-2.5 py-2">
                    <div className="mb-1 text-[10px] font-semibold uppercase tracking-wide text-gold-ink">
                      Needs your input
                    </div>
                    <div className="mb-2 leading-snug text-charcoal">{followUp}</div>
                    <button
                      type="button"
                      onClick={startRecording}
                      className="inline-flex items-center gap-1.5 rounded-md bg-gold px-2.5 py-1.5 text-[11px] font-semibold text-gold-ink transition hover:brightness-95 focus:outline-none focus-visible:ring-2 focus-visible:ring-gold/50"
                    >
                      <Mic className="h-3.5 w-3.5" /> Answer
                    </button>
                  </div>
                ) : (
                  <button
                    type="button"
                    onClick={startRecording}
                    className="inline-flex items-center gap-1.5 text-[11px] font-medium text-slate hover:text-charcoal"
                  >
                    <Mic className="h-3 w-3" /> Tap to add more
                  </button>
                )}
              </div>
            )}

            {/* 6 — Idle / just opened */}
            {showIdle && (
              <div className="flex flex-col items-center gap-2 py-3 text-center">
                <span className="inline-flex h-11 w-11 items-center justify-center rounded-full border border-gold/40 bg-gold/10">
                  <Sparkles className="h-5 w-5 text-gold" />
                </span>
                <div className="text-sm font-semibold text-charcoal">Dictate the day&rsquo;s work</div>
                <div className="text-[11px] leading-snug text-slate">
                  Say the supervisor, activity, quantity, and crew — I&rsquo;ll fill the form.
                </div>
                <button
                  type="button"
                  onClick={startRecording}
                  className="mt-1 inline-flex items-center gap-1.5 rounded-md bg-gold px-3 py-1.5 text-xs font-semibold text-gold-ink transition hover:brightness-95 focus:outline-none focus-visible:ring-2 focus-visible:ring-gold/50"
                >
                  <Mic className="h-4 w-4" /> Start talking
                </button>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
