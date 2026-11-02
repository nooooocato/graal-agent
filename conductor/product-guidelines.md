# Product Guidelines: Minecraft 26.3 JavaScript Agent (`graal-agent`)

## 1. Tone & Voice
- **Developer-Centric & Precise:** Technical, unambiguous, and developer-first documentation and diagnostics.
- **Structured Logging:** Standardized log prefixes `[GRAAL-AGENT] [INFO/WARN/ERROR]` with explicit context (source script, class, or event type).
- **In-Game UX:** Clean, color-coded Minecraft chat feedback (e.g., `§a` green for success, `§c` red for errors, `§e` yellow for warnings, `§7` gray for secondary info). Never clutter the chat stream unless requested by user commands.

## 2. API Design Principles
- **Minimalist & Ergonomic:** Keep the core Java bridge as compact as possible. Provide intuitive, idiomatic modern JavaScript (ES2024+) bindings rather than verbose Java-like wrappers.
- **Fail-Safe Isolation:** User script exceptions in event handlers or command executions must never crash the server tick thread or client render loop. Catch errors gracefully and provide script line numbers and stack traces.
- **Implicit Thread Safety:** All mutations impacting world, entity, or rendering state must be automatically dispatched to the appropriate main thread (`mc.execute(...)` or `server.execute(...)`).
- **Vanilla-First Compatibility:** Server-side features must prioritize native vanilla protocol compatibility (virtual inventories, Brigadier trees, custom data packs) so unmodified vanilla clients can join seamlessly.

## 3. Script & Data Pack Extensibility
- **Asset & Data Sandboxing:** Script read/write operations must operate relative to the `scripts/` root to maintain structured asset and datapack generation.
- **Hot-Reload Idempotency:** Registering events and commands during hot reloads (`/reloadjs`) must cleanly deregister previous listeners to prevent memory leaks and duplicate execution.

## 4. Code & Artifact Consistency
- **Encoding:** Strict UTF-8 across all file reads, log outputs, and network strings.
- **Build & Distribution:** Single-command packaging via `build.ps1`, zero-dependency runtime JAR, clean separation of `lib/` and user scripts.
