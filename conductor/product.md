# Minecraft 26.3 JavaScript Agent & Lightweight Mod API (`graal-agent`)

## Vision & Purpose
`graal-agent` is a high-performance, developer-friendly Java Agent and lightweight Mod API for Minecraft 26.3 (Mojang mappings). Powered by Oracle GraalVM JDK 25 and GraalJS (Polyglot Truffle), it provides a minimal, high-efficiency API and underlying bytecode bridge that transforms JavaScript scripts into mod-grade extensions without the weight or lock-in of heavy traditional modding loaders.

## Core Architectural Pillars
1. **Dual-Environment Architecture (Client & Dedicated Server):**
   - Seamlessly operates in both client environments and dedicated server environments.
   - Automatically adapts context (e.g. `mc.execute(...)` on client render threads vs `server.execute(...)` on server main ticks).

2. **Server-Driven & Vanilla Client Compatibility (Bukkit/Server-Mod Paradigm):**
   - Enables pure server-side modding where unmodified vanilla clients can connect and experience custom gameplay mechanics, dynamic commands, virtual inventories, and server-side logic without needing client mods.
   - When running on a client, it functions as an in-game script browser and development workbench.

3. **Minimalist, High-Performance Mod API:**
   - Adheres to a "least-is-more" philosophy: exposes a compact, zero-overhead core API while granting full Java interoperability (`Java.type`) and raw event hooks.
   - Relies on Java 25 standard `java.lang.classfile` for clean, zero-dependency bytecode instrumentation.

4. **Script-Driven Asset & Data Pack Synthesis (Mod-Grade Capability):**
   - Grants JS scripts managed read/write access to the `scripts/` directory and custom virtual resource packs / data packs.
   - Scripts can dynamically generate, bundle, and serve textures, custom models, JSON definitions, recipes, advancements, and data packs directly to the game or connecting vanilla clients.

5. **Hot-Reloading & Dynamic Developer Experience:**
   - Real-time `WatchService` file monitoring and `/reloadjs` command.
   - Hot-reload game logic, listeners, commands, and generated data without restarting the server or client.

6. **Full-Stack Brigadier, Diagnostics & UTF-8 Integration:**
   - Standard `/` command syntax registration via `BrigadierAdapter` (`/js`, `/reloadjs`, and dynamic custom commands) with instant player sync.
   - Built-in structured telemetry (`DiagnosticsHub`) and automated crash snapshots (`CrashDumpRecorder`).
   - Standardized UTF-8 encoding across Windows console, log files (`agent.log`), and game text components.

## Boundaries & Non-Goals
- Avoids becoming a monolithic, heavy framework: keeps the Java core ultra-lightweight, pushing flexibility to JavaScript.
- Prioritizes vanilla compatibility over client-side intrusive rendering hacks.
