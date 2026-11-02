# Specification: Multiplayer Command Passthrough, Dynamic Suggestion Sync & Security Sandboxing (`multiplayer_command_passthrough`)

## 1. Overview
This track addresses command routing, Brigadier suggestion tree consistency, caller-aware security sandboxing, and `/reload` hierarchy across dual-environment setups (Client and Dedicated/Integrated Server). It eliminates client-side command swallowing in multiplayer games, establishes the server as the single source of truth for command registration and suggestion trees, fixes backend console execution for zero-argument custom commands, and prevents Polyglot context invalidation across reloads.

## 2. Functional Requirements

### 2.1 Multiplayer Command Passthrough
- **Multiplayer Passthrough:** When the client detects it is connected to a multiplayer server (`!Minecraft.getInstance().isLocalServer()` or `Minecraft.getInstance().getCurrentServer() != null`), `ClientPacketListener.sendCommand` must NOT swallow `/js`, `/reloadjs`, or any custom script commands. These commands must pass through cleanly as vanilla `ServerboundChatCommandPacket` to the server.
- **Client Local Debug Command:** Retain `/cjs <code...>` and `/clientjs <code...>` as client-only commands, evaluating locally in the client JavaScript runtime even when connected to a remote server.
- **Singleplayer Consistency:** In Singleplayer mode, commands route through the IntegratedServer via the standard vanilla command pipeline, keeping environment semantics and outputs completely identical to multiplayer servers.

### 2.2 Server-Driven Brigadier Suggestions & Real-Time Sync
- **No Client Suggestion Pollution:** In multiplayer mode, the client's `ClientPacketListener.handleCommands` hook must not inject client-local `customCommands` into the client's command dispatcher. Autocompletion and syntax highlighting must be completely driven by the server's `ClientboundCommandsPacket`.
- **Dynamic Suggestions Push on Reload:** When `/reloadjs` or dynamic command registration occurs on the server, the server must update its `CommandDispatcher` and broadcast updated command trees to all connected clients via `server.getPlayerList().getPlayers().forEach(p -> server.getCommands().sendCommands(p))`, refreshing client autocomplete in real time without reconnecting.

### 2.3 Server Command Registration & Console Fixes
- **Zero-Argument Command Execution:** In `ServerCommandDispatcher`, bind the command execution proxy to both `customLiteral` (for zero-argument invocations like `serverinfo`) and `customArgNode` (for argument invocations like `serverinfo args`).
- **Dynamic Polyglot Context Resolution:** Decouple command execution from closed contexts by dynamically resolving command callbacks via `ScriptManager.getInstance().getCustomCommands().get(cmdName)` at execution time, eliminating `PolyglotException: Context execution was cancelled` after reloads.
- **Console Feedback & ClassLoader Resolution:** Fix ClassLoader resolution in `EnvironmentContext.broadcastMessage` to load `net.minecraft.network.chat.Component` using the server instance's ClassLoader.

### 2.4 Multiplayer Security & Sandboxing
- **Role-Based Permissions (RBAC):**
  - `/reloadjs` is strictly restricted to server console or OP Level 2 (Commands.LEVEL_ADMINS).
  - Custom commands registered via `agent.registerCommand` execute pre-defined server administrator scripts and can be executed by players based on permission settings.
- **Client-Originated `/js` Sandboxing:**
  - When `/js` is evaluated from an in-game player source:
    - Block access to `Java` and `Java.type` globals to prevent Java reflection or classloader escape.
    - Block access to `agent.fs` file I/O operations (reading/writing server files is forbidden for remote clients).
    - Limit evaluation to pure JavaScript computations and safe read-only queries.
  - When `/js` is executed by the server console:
    - Full administrator privileges remain available (access to `Java.type`, `server`, `agent.fs`).

### 2.5 Reload Hierarchy
- **`/reloadjs`:** Lightly reloads only JavaScript scripts and custom commands without triggering vanilla datapack/recipe/advancement reloads, followed by syncing updated command trees to online players.
- **Vanilla `/reload`:** By hooking `MinecraftServer.reloadResources`, triggering a vanilla `/reload` command automatically reloads all datapacks AND simultaneously executes `reloadjs`, ensuring full inclusion.

## 3. Non-Functional Requirements & Architecture Standards
- **File Length Limit:** Strictly maintain <600 lines per Java source file.
- **Thread Safety:** Ensure server commands execute safely on the server main thread.
- **Encoding:** Strict UTF-8 across command handling, feedback, and logs.
- **Zero Third-Party Dependencies:** Utilize JDK 25 standard APIs and GraalJS dependencies in `lib/`.

## 4. Acceptance Criteria
1. Client connected to Dedicated Server sends `/serverinfo`, which executes on the server and displays Dedicated Server runtime information.
2. In server console, executing `serverinfo` or `fstest` with zero arguments succeeds without `Unknown or incomplete command` errors.
3. In server console, `js 1+1` evaluates and outputs `=> 2`.
4. In multiplayer, in-game player execution of `/js` cannot access `Java.type` or `agent.fs` to modify or inspect server files.
5. In server console, administrator `/js` has full host access and file system capabilities.
6. Server `/reloadjs` refreshes suggestions for all connected clients and does not invalidate command callbacks.
7. Vanilla `/reload` reloads both datapacks and JavaScript scripts; `/reloadjs` reloads only JavaScript scripts.
8. Client can execute `/cjs <expression>` to evaluate locally on client while connected to a server.

## 5. Out of Scope
- Custom binary packet networking channels.
- Client-side custom rendering pipelines in multiplayer.
