# Implementation Plan: Server Support & Scripts File I/O (`server_support_file_io`)

## Phase 1: Sandboxed Scripts File I/O (`agent.fs`) [checkpoint: d237727]
- [x] Task: Create Unit Tests for Sandboxed File System (`ScriptFileSystemTest`) [4891a19]
    - [x] Write unit tests verifying path traversal prevention (rejecting `../` escapes outside `scripts/`)
    - [x] Write unit tests verifying UTF-8 read/write, directory creation, exists checks, file listing, and deletion
- [x] Task: Implement `ScriptFileSystem` and GraalJS Binding [1e2623d]
    - [x] Implement `com.graalagent.script.fs.ScriptFileSystem` (<600 lines) with strict canonical path checking
    - [x] Expose `agent.fs` in `AgentAPI.java` with idiomatic JS methods
    - [x] Run automated tests and confirm passing (Green Phase)
- [x] Task: Phase Verification & Checkpoint (Refer to workflow.md) [d237727]

## Phase 2: Dual-Environment Runtime Detection & Thread Dispatcher [checkpoint: dc8bc90]
- [x] Task: Create Tests for Dual-Environment Context & Dispatching [19a1a5a]
    - [x] Write tests verifying client vs dedicated server environment detection
    - [x] Write tests verifying thread dispatch routing
- [x] Task: Refactor and Implement Environment Bridge [dc8bc90]
    - [x] Create `com.graalagent.bridge.EnvironmentContext` supporting Client (`Minecraft`) and Server (`MinecraftServer`)
    - [x] Implement thread-safe execution dispatching via `MinecraftServer.execute(...)` on server instances
    - [x] Update `MinecraftTransformer` to instrument `MinecraftServer` initialization and lifecycle
    - [x] Run automated tests and verify build with `build.ps1`
- [x] Task: Phase Verification & Checkpoint (Refer to workflow.md) [dc8bc90]

## Phase 3: Server Brigadier Command Integration [checkpoint: 3785a87]
- [x] Task: Create Tests for Server Command Dispatcher [996ed07]
    - [x] Write tests verifying server command node registration for `/js` and `/reloadjs`
- [x] Task: Implement Bytecode Transformer for Server Commands [a0899dd]
    - [x] Instrument `net.minecraft.commands.Commands` to register `/js`, `/reloadjs`, and dynamic commands
    - [x] Ensure permission level check (requires op / level 2+ on dedicated servers)
    - [x] Run automated tests and verify command execution
- [x] Task: Phase Verification & Checkpoint (Refer to workflow.md) [3785a87]

## Phase 4: Dynamic Datapack Injection & Hot Reload [checkpoint: a8aafa4]
- [x] Task: Create Tests for Datapack Auto-Discovery [0371be0]
    - [x] Write tests for detecting and indexing `scripts/datapacks/`
- [x] Task: Implement PackRepository Bytecode Hook & Server Resource Reload [a8aafa4]
    - [x] Instrument `PackRepository` to automatically inject scripts datapack sources
    - [x] Wire `/reloadjs` to invoke `MinecraftServer.reloadResources(...)`
    - [x] Provide sample script synthesizing a custom datapack recipe
- [x] Task: Phase Verification & Checkpoint (Refer to workflow.md) [a8aafa4]

## Phase: Review Fixes
- [x] Task: Apply review suggestions [2eddfa2]
