# Java Style Guide & Engineering Standards (Java 24+ / Java 25)

This document outlines key rules, best practices, and project constraints based on the Google Java Style Guide and modern Java 24+ conventions.

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

## 4. Modern Java 24+ Practices
- **Data Carriers:** Use `record` for immutable DTOs, value objects, and event payloads instead of boilerplate classes with manual getters/setters.
- **Pattern Matching:** Prefer pattern matching for `switch` and `instanceof`.
- **Bytecode Instrumentation:** Use the standard `java.lang.classfile` API (Class-File API); do not introduce third-party bytecode libraries (ASM, ByteBuddy).
- **Resource Management:** Always use `try-with-resources` for auto-closeable streams, files, and channels.

## 5. Modern Class-File API Standards (JEP 484 / Java 24+)
All bytecode transformations, micro-plugins, and SMV components must adhere to the immutable pipeline and nominal typing paradigm:

### 5.1 Nominal Typing First (标称类型优先)
- **Zero Raw Descriptors:** Strictly prohibit raw JVM descriptor strings (e.g., `([Ljava/lang/String;)V`, `()V`, `()Ljava/lang/ClassLoader;`) in business logic.
- **Type Descriptors:** Always use the `java.lang.constant` system:
  - Standard types: Use `ConstantDescs` constants (`CD_String`, `CD_int`, `CD_void`, `CD_long`, `CD_boolean`, etc.).
  - Custom/Game classes: Use dot-separated names via `ClassDesc.of("net.minecraft.client.Minecraft")`. Never use slashes (`net/minecraft/...`) or `L...;` wrapper syntax.
- **Method Signatures:** Construct via `MethodTypeDesc.of(returnDesc, paramDescs...)`. Never invoke `MethodTypeDesc.ofDescriptor("...")` with handwritten raw descriptor strings.
- **Symbolic Matching:**
  - Match method types via `mm.methodTypeSymbol().equals(MTD_CONSTANT)`.
  - Match invocation instructions via `inv.owner().asSymbol().equals(CD_TARGET)` and `inv.typeSymbol().equals(MTD_TARGET)`.
  - Use `mm.methodName().equalsString("name")` / `inv.name().equalsString("name")` instead of `.stringValue().equals(...)`.

### 5.2 Immutable Pipeline & Passthrough (不可变流水线与默认透传)
- **Immutable Input, Builder Output:** Treat `ClassModel`, `MethodModel`, and `CodeModel` as read-only immutable inputs; emit modified or new elements to the corresponding `*Builder`.
- **Explicit Default Passthrough:** Any unintercepted element MUST be forwarded explicitly via `builder.with(element)` or `cb.with(ce)`. Never silently drop unmatched elements.
- **Declarative Pipeline Composition:** Prefer high-level transform factories such as `ClassTransform.transformingMethodBodies(...)` and `ClassTransform.transformingMethods(...)` over repetitive manual `for (ClassElement ce : classModel)` loops.
- **Fast-Fail Filtering:** Filter by method name and descriptor at the highest level (`ClassTransform` or `MethodTransform`) rather than checking names instruction-by-instruction inside `CodeTransform`.

### 5.3 Structured Control Flow & Frame Safety (控制流与帧安全)
- **Strict Label Lifecycle:** Create labels strictly via `cb.newLabel()`, and bind them exactly once via `cb.labelBinding(label)`. Never compute manual byte offsets.
- **Managed Stack & Frames:** Let `ClassFile` automatic frame generation calculate `max_stack`, `max_locals`, and `StackMapTable`. Do not manually calculate stack depth or slot indices.
- **Structured Exceptions:** Prefer structured blocks (e.g. `cb.trying(...)`) over manual exception table range label management.

### 5.4 Context Lifecycle
- **Context Reuse:** `ClassFile` instances are immutable and thread-safe. Reuse them via `private static final ClassFile CF = ClassFile.of();` where applicable.

### 5.5 Anti-Patterns Quick Reference
| Prohibited (Anti-Pattern) | Required (Modern Pattern) |
| :--- | :--- |
| `"java/lang/String"` or `"Ljava/lang/String;"` | `ConstantDescs.CD_String` |
| `"net/minecraft/bundler/Main"` | `ClassDesc.of("net.minecraft.bundler.Main")` |
| `"([Ljava/lang/String;)V"` | `MethodTypeDesc.of(CD_void, CD_String.arrayType())` |
| `MethodTypeDesc.ofDescriptor("()V")` | `MethodTypeDesc.of(CD_void)` |
| `mm.methodType().stringValue().equals(...)` | `mm.methodTypeSymbol().equals(...)` |
| `inv.owner().asInternalName().equals(...)` | `inv.owner().asSymbol().equals(...)` |
| `inv.type().stringValue().equals(...)` | `inv.typeSymbol().equals(...)` |

## 6. Concurrency & Minecraft Thread Safety
- **Thread Dispatching:** Never perform state mutations (world, entity, chat, GUI) from asynchronous threads or GraalJS background callbacks. Dispatch via `mc.execute(...)` (client) or `server.execute(...)` (server).
- **Graceful Error Handling:** Trap script execution exceptions in the Agent boundary; prevent script failures from terminating server ticks or the client game loop.

## 7. Naming Conventions
- **Classes, Records, Interfaces, Enums:** `UpperCamelCase`.
- **Methods & Variables:** `lowerCamelCase`.
- **Constants:** `CONSTANT_CASE` (static final fields).
