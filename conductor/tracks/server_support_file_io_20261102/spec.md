# Specification: Server Support & Scripts File I/O (`server_support_file_io`)

## 1. Overview
This track transforms `mc-agent` from a client-centric agent into a robust, dual-environment (Client & Dedicated Server) lightweight Mod API. It introduces automatic server environment detection, safe main-thread execution dispatching on both client and server, a sandboxed JavaScript File I/O API (`agent.fs`) scoped to `scripts/`, and dynamic `PackRepository` injection enabling scripts to synthesize and mount custom datapacks for unmodified vanilla clients.

## 2. Functional Requirements

### 2.1 Dual-Environment Detection & Execution Engine
- **Runtime Detection:** Dynamically identify whether the agent is running in a Dedicated Server (`net.minecraft.server.MinecraftServer`) or Client (`net.minecraft.client.Minecraft`) environment.
- **Thread-Safe Dispatching:**
  - Client: dispatch UI, client chat, and player operations via `Minecraft.getInstance().execute(...)`.
  - Server: dispatch world interactions, broadcast messages, and game rules via `MinecraftServer.execute(...)`.
- **Brigadier Command Registration:**
  - Support server-side command trees (`/js`, `/reloadjs`, and `agent.registerCommand`) accessible to server console and connected operators without requiring client-side mods.

### 2.2 Sandboxed Scripts File I/O API (`agent.fs`)
- Expose an ergonomic, native JavaScript object `agent.fs` with methods:
  - `agent.fs.readFile(relativePath, encoding = 'utf-8')`: string
  - `agent.fs.readBytes(relativePath)`: byte array
  - `agent.fs.writeFile(relativePath, content, encoding = 'utf-8')`: void
  - `agent.fs.writeBytes(relativePath, bytes)`: void
  - `agent.fs.exists(relativePath)`: boolean
  - `agent.fs.isDirectory(relativePath)`: boolean
  - `agent.fs.mkdir(relativePath)`: boolean
  - `agent.fs.list(relativePath)`: string[]
  - `agent.fs.delete(relativePath)`: boolean
- **Security Sandbox:** Strictly restrict path resolution to the canonical `scripts/` directory tree. Reject path traversal attempts (e.g. `../../windows/system32`) with a descriptive `SecurityException`.

### 2.3 Dynamic Datapack & Asset Repository Injection
- Transform Minecraft's `PackRepository` / resource provider lifecycle via Java 25 `java.lang.classfile` bytecode injection.
- Dynamically mount `scripts/datapacks/` or synthetic in-memory/file packs into the server's resource pack collection.
- Deliver custom datapack features (custom recipes, function tags, loot tables, advancements) directly to vanilla clients over standard vanilla networking.

### 2.4 Hot-Reloading & Resource Sync
- When `/reloadjs` is invoked, trigger a hot reload of JavaScript logic and synchronize server datapacks via `server.reloadResources(...)`.

## 3. Non-Functional Requirements & Architecture Standards
- **File Length Limit:** Strictly adhere to the <600 lines threshold per Java source file, decoupling bridge, fs, and server modules into dedicated classes.
- **Encoding:** Strict UTF-8 across all file read/write operations and console/chat logging.
- **Zero Third-Party Dependencies:** Utilize only JDK 25 standard APIs and GraalJS dependencies already present in `lib/`.

## 4. Acceptance Criteria
1. Dedicated Server boots with `-javaagent:mc-agent.jar` cleanly without ClassNotFound errors or thread assertion failures.
2. Server console and in-game operators can execute `/js <code...>` and `/reloadjs`.
3. JS scripts can read, write, and create directories within `scripts/`, but are prohibited from escaping outside `scripts/`.
4. Datapacks generated or placed in `scripts/datapacks/` are automatically mounted by the server, allowing unmodified vanilla clients to experience custom recipes/tags.

## 5. Out of Scope
- Client-side custom rendering pipelines requiring client mod installation.
- External database/network sockets (deferred to future tracks).
