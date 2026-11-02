# Technology Stack: Minecraft 26.3 JavaScript Agent (`mc-agent`)

## 1. Core Runtime & Language
- **Language / Version:** Java 25
- **JDK Distribution:** Oracle GraalVM JDK 25.0.4+7.1 (Windows x64)
- **Class Format:** Class Version 69 (Java 25)

## 2. Scripting & Polyglot Engine
- **Engine:** Oracle GraalJS 25.0.4
- **Language Specification:** Modern JavaScript (ES2024+)
- **Runtime Libraries:**
  - `polyglot-25.0.4.jar`
  - `truffle-api-25.0.4.jar`
  - `truffle-runtime-25.0.4.jar`
  - `js-language-25.0.4.jar`
  - `icu4j-25.0.4.jar`
  - `regex-25.0.4.jar`
- **Dynamic Loading:** Injected dynamically into system classloader at runtime via `Instrumentation.appendToSystemClassLoaderSearch`.

## 3. Bytecode Manipulation & Game Integration
- **Instrumentation Mechanism:** Java Instrumentation API (`premain` / `agentmain`).
- **Bytecode Framework:** Native `java.lang.classfile` (Class-File API introduced in modern Java, zero third-party dependencies).
- **Target Platform:** Minecraft 26.3 (Mojang mappings, Client & Dedicated Server).
- **Command Framework:** Minecraft native Brigadier command dispatcher.

## 4. Concurrency & File System
- **Thread Safety:** Dispatching to client render thread (`mc.execute(...)`) and server tick thread (`server.execute(...)`).
- **File System Monitoring:** Java NIO `WatchService` monitoring `scripts/` directory for live hot reloading.
- **Data & Asset Management:** Java NIO Path / UTF-8 File APIs enabling scripts to generate datapacks, virtual models, and configs.

## 5. Build & Development Tools
- **Build Script:** PowerShell 7 (`build.ps1`).
- **Compilation Toolchain:** `javac` (UTF-8 encoded) + `jar` manifest packaging.
- **Output Artifact:** `mc-agent.jar` (single agent JAR containing manifest and core classes).
