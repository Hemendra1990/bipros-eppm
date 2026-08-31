# AI Conversation Smart Titles — Design

**Date:** 2026-05-05
**Module:** `backend/bipros-ai` (chat)
**Status:** Approved for planning

## Problem

The AI chat history sidebar shows every conversation labeled `"Chat"` regardless of content (see `frontend/src/components/ai/AiHistoryView.tsx`, modules `GENERAL`, `DPR`, `RISK` in the screenshot). Users can't distinguish conversations at a glance.

Existing logic in `ConversationService.appendUserMessage` (added in commit `a0c5440`) attempts to derive a title from the first user message, but:

- Conversations created before that commit have a permanent `"Chat"` title.
- Even when the derivation runs, the title is just the truncated first message — not a meaningful summary.

## Goal

Every new conversation gets a meaningful title without user effort:

1. **Instant feedback** — title appears as soon as the user sends their first message.
2. **Smart upgrade** — once the first assistant response arrives, the title is replaced with a concise (3–6 word) LLM-generated summary of the exchange.

## Non-Goals

- **Backfill of pre-existing `"Chat"` titles.** Out of scope. May be added later as a one-shot script.
- **User-facing title editing.** Out of scope. The design leaves room for it (the upgrade guard checks whether the title looks auto-derived) but does not add the UI.
- **Re-titling on conversation drift.** Title is set once after turn 1 and never recomputed.

## Architecture

Two-phase flow, both server-side, no API changes:

### Phase 1 — Instant title (already implemented)

`ConversationService.appendUserMessage` already derives a title from the first user message when `seq == 0` and the current title is null/blank/`"Chat"`. Keep this exactly as-is. It runs in the same transaction that persists the user message, so the sidebar can show a useful title on the very next `listConversations` call.

### Phase 2 — Smart title upgrade (new)

After the first assistant reply is appended, asynchronously generate a smart title and persist it.

Components:

- **`TitleGenerator`** — new class, generates titles via the configured LLM provider.
- **`ConversationService.upgradeTitleAsync(UUID conversationId)`** — new `@Async` method that re-loads the conversation, applies the upgrade-eligibility guard, calls `TitleGenerator`, and persists.
- **`ChatController` hook points** — both the streaming and non-streaming endpoints invoke `upgradeTitleAsync` after the first assistant message is appended.

Spring's `@EnableAsync` is already enabled on `BiprosApplication`, so no new configuration is required.

## Data Flow

```
POST /v1/ai/chat (or /chat/stream)
  └─ ChatController
      ├─ ConversationService.getOrCreate         (title = "Chat")
      ├─ ConversationService.appendUserMessage   (title ← deriveTitle(firstMsg))   ← Phase 1
      ├─ Orchestrator.handle → LLM stream        (assistant reply produced)
      ├─ ConversationService.appendAssistantMessage
      └─ ConversationService.upgradeTitleAsync   (fire-and-forget)                  ← Phase 2
            └─ @Async, separate thread + transaction
                ├─ load conversation + first 2 messages
                ├─ guard: skip unless msgCount == 2 AND title looks auto-derived
                ├─ TitleGenerator.generate(userMsg, assistantMsg, module)
                │     └─ small LLM call, max_tokens=20, temperature=0.2
                ├─ sanitize: strip surrounding quotes, trailing punctuation, cap at 80 chars
                └─ persist title (or skip on null/failure)
```

## Components

### `TitleGenerator`

- **Location:** `backend/bipros-ai/src/main/java/com/bipros/ai/chat/TitleGenerator.java`
- **Type:** `@Component`, depends on `OpenAiCompatibleLlmProvider` and `LlmProviderConfigRepository` (mirrors how `ChatController.resolveConfig` resolves the active provider).
- **API:**
  ```java
  Optional<String> generate(String userMessage, String assistantMessage, String module);
  ```
- **Prompt shape:**
  - System: `"You write concise titles for chat conversations. Output exactly one short title (3-6 words). No quotes, no trailing punctuation. Match the user's language."`
  - User: a compact rendering of the first turn, e.g.
    ```
    Module: <module or "general">
    User: <first user message, truncated to ~500 chars>
    Assistant: <first assistant message, truncated to ~500 chars>
    ```
- **LLM params:** `max_tokens=20`, `temperature=0.2`. Use the same default+active provider config as the main chat endpoint (no per-feature provider override).
- **Sanitization:**
  - `trim()`, collapse whitespace.
  - Strip a single pair of leading/trailing matching quotes (`"..."`, `'...'`, `"..."`, `'...'`).
  - Strip trailing `.`, `!`, `?`, `…`.
  - Cap to 80 characters; if truncated, append `…`.
- **Failure mode:** any exception (LLM error, timeout, empty output after sanitization) → return `Optional.empty()` and log at `WARN`. The instant title remains.

### `ConversationService.upgradeTitleAsync`

- **Signature:** `@Async @Transactional public void upgradeTitleAsync(UUID conversationId)`
- **Logic:**
  1. Load conversation. If missing or soft-deleted, return.
  2. Load messages ordered by `seq`. If `messages.size() != 2`, return. (Guards against retry/concurrent invocation; only fires once at first-turn completion.)
  3. Confirm `messages[0].role == "user"` and `messages[1].role == "assistant"`. Otherwise return.
  4. Compute `expectedAutoTitle = deriveTitle(messages[0].content)`. If `conv.title` differs from both `"Chat"` and `expectedAutoTitle`, the user (or future code) has set a custom title — skip.
  5. Call `TitleGenerator.generate(...)`. On `Optional.empty()`, return.
  6. Set `conv.title = generated`, save.
- **Idempotency:** the `messages.size() == 2` guard and the title-shape guard together make repeated calls safe. No new DB column required.
- **`deriveTitle`:** promote the existing `private static` helper to package-private (or move into a `static` utility) so the upgrade method can compute the expected auto-title without duplication.

### `ChatController` integration

- **Non-streaming `/v1/ai/chat`** — after `appendAssistantMessage(conv.getId(), text.toString())`, add `conversationService.upgradeTitleAsync(conv.getId())`. Fire-and-forget; the `@Async` annotation moves the work off the request thread.
- **Streaming `/v1/ai/chat/stream`** — inside the existing `doOnComplete` lambda (currently `() -> conversationService.appendAssistantMessage(...)`) chain the upgrade call after the assistant message is persisted.

Both paths are protected by the same upgrade-eligibility guard inside `upgradeTitleAsync`, so duplicate or out-of-order invocations are harmless.

## Failure & Edge Cases

| Scenario | Behavior |
|---|---|
| LLM call fails / times out | `TitleGenerator` logs WARN, returns empty. Instant title stays. No user-facing error. |
| User message is empty/image-only | `deriveTitle` returns `"Chat"`. Smart-title upgrade still runs, generates from the assistant reply (and image context if present). |
| Assistant reply errors mid-stream | First-turn never completes → `messages.size() != 2` guard skips upgrade. Instant title stays. |
| Two upgrades fire (e.g., retry) | Guard `messages.size() == 2` + title-shape check makes the second a no-op. |
| User manually edits title (future) | Title-shape check (`title != expectedAutoTitle && title != "Chat"`) skips the upgrade. |
| Conversation soft-deleted before upgrade lands | `upgradeTitleAsync` checks `deletedAt == null` and exits early. |

## Testing

### Unit

- **`TitleGeneratorTest`**
  - Returns sanitized title from a mocked LLM response with surrounding quotes and trailing punctuation.
  - Returns empty when LLM throws.
  - Returns empty when LLM returns blank/whitespace.
  - Caps at 80 characters with ellipsis.
- **`ConversationServiceTitleUpgradeTest`** (or extend existing test class)
  - Skips when message count != 2.
  - Skips when title is user-edited (not `"Chat"` and not the auto-derived form).
  - Persists generated title when guard passes.
  - Persists nothing (no exception, no save) when generator returns empty.

### Integration / manual

- Send first chat → verify instant title in `GET /v1/ai/conversations` response (matches first message truncated).
- Wait ~1–2s → re-fetch → verify smart title (3–6 words, no quotes).
- Send image-only first message → verify smart title still generated from assistant reply.
- Stop the configured LLM provider mid-test → verify request still succeeds, instant title persists, no error to client, WARN log present.

## Implementation Notes

- Keep `TitleGenerator` self-contained in the `chat` package — it's a chat-feature concern, not an orchestrator concern.
- Do not add a new column to `ai_conversations`. The two existing guards (message count + title shape) are sufficient and avoid a schema change.
- Frontend (`AiHistoryView.tsx`) requires no changes; it already reads `c.title` and falls back to `"Chat"`. After the upgrade lands, the next `listConversations` poll picks up the smart title automatically.

## Out of Scope (Possible Follow-ups)

- One-shot backfill script for pre-`a0c5440` conversations stuck at `"Chat"`.
- Manual title editing UI in `AiHistoryView`.
- Periodic title refresh as conversations evolve.
