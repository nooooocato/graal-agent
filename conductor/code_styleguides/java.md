# Java Style Guide & Engineering Standards (Java 25)

This document outlines key rules, best practices, and project constraints based on the Google Java Style Guide and modern Java 25 conventions.

## 1. Source File Basics
- **File Encoding:** Strict UTF-8.
- **Whitespace:** Use only ASCII horizontal spaces (0x20). Never use tabs for indentation.
- **Indentation:** +4 spaces per block level.
- **Single Top-Level Class:** Exactly one top-level class per source file matching the filename (`ClassName.java`).

## 2. Architectural Boundaries & File Size Limit
- **600-Line Refactoring Threshold:** When a source file exceeds 600 lines or contains multiple disparate classes/responsibilities, it MUST be refactored and decomposed into separate files, subpackages, and classes using standard software engineering design patterns (e.g., Bridge, Strategy, Factory, EventBus).

## 3. Formatting & Structure
- **Braces:** K&R style ("Egyptian brackets") for classes, methods, and all control structures (`if`, `for`, `while`), even single-line blocks.
- **Imports:** Wildcard imports (`import foo.*`) are strictly prohibited. Group imports logically: standard library, third-party, project imports.
- **Method Spacing:** Separate method declarations with a single blank line.

## 4. Modern Java 25 Practices
- **Data Carriers:** Use `record` for immutable DTOs, value objects, and event payloads instead of boilerplate classes with manual getters/setters.
- **Pattern Matching:** Prefer pattern matching for `switch` and `instanceof`.
- **Bytecode Instrumentation:** Use the standard `java.lang.classfile` API (Class-File API); do not introduce third-party bytecode libraries (ASM, ByteBuddy).
- **Resource Management:** Always use `try-with-resources` for auto-closeable streams, files, and channels.

## 5. Concurrency & Minecraft Thread Safety
- **Thread Dispatching:** Never perform state mutations (world, entity, chat, GUI) from asynchronous threads or GraalJS background callbacks. Dispatch via `mc.execute(...)` (client) or `server.execute(...)` (server).
- **Graceful Error Handling:** Trap script execution exceptions in the Agent boundary; prevent script failures from terminating server ticks or the client game loop.

## 6. Naming Conventions
- **Classes, Records, Interfaces, Enums:** `UpperCamelCase`.
- **Methods & Variables:** `lowerCamelCase`.
- **Constants:** `CONSTANT_CASE` (static final fields).
