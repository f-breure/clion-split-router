# JetBrains Marketplace Plugin Verification Report & API Analysis

**Plugin:** Split Window File Router
**Submitted version:** 1.1.1
**Current local version:** 1.1.3
**Verified against:** IntelliJ IDEA 2026.3 EAP (`263.6800.24`)
**Date:** 2026-10-13
**Marketplace result for 1.1.1:** **Compatible** in Plugin Verifier, but Marketplace requested resolution of internal API usages before approval.

---

## 1. Summary of Verification Findings

The 1.1.1 plugin passed binary compatibility verification, but Marketplace requested that internal API findings be resolved or justified. The report listed 3 deprecated usages, 3 experimental usages, and 8 internal API usages.

| Category | Count | Risk Level | Details |
|---|:---:|:---:|---|
| **Internal API** | 8 | Medium | APIs under `com.intellij.openapi.fileEditor.impl.*` or marked `@ApiStatus.Internal`. Can change between platform major releases without deprecation cycles. |
| **Deprecated API** | 3 in 1.1.1; 0 in 1.1.2 source | Low | The deprecated calls reported for 1.1.1 have been replaced. |
| **Experimental API** | 3 in 1.1.1; 0 in 1.1.2 source | Low/Medium | The experimental calls reported for 1.1.1 have been replaced. |

---

## 2. Detailed API Breakdown

### 2.1 Internal API Usages (8)

1. **`FileEditorManagerEx.openFile(VirtualFile, EditorWindow, FileEditorOpenOptions)`** (1 usage)
   - *Location:* `FileSplitRouterListener.java` (invoking `openFile` targeting a specific split `EditorWindow`).
   - *Reason:* IntelliJ's public `FileEditorManager` does not provide an API to open a file into a specific editor window/split pane.
2. **`FileEditorOpenOptions`** (Class usage, constructor `<init>()`, and methods) (3 usages)
   - `FileEditorOpenOptions.withRequestFocus(boolean)`
   - `FileEditorOpenOptions.withSelectAsCurrent(boolean)`
   - *Reason:* Configures tab opening flags without stealing focus or forcing immediate activation prior to readiness checks.
3. **`EditorWindow` / split management** (additional usages)
   - *Reason:* Horizontally inspecting splits, sorting left vs. right panes, checking which files are open in which split window, and closing tabs across panes.

*Assessment:* The plugin's split-routing behavior depends on editor-window APIs. Marketplace requested that these usages be resolved or justified; no approval or exception has been granted yet.

---

### 2.2 Deprecated API Usages (0 remaining)

The 3 deprecated usages reported for 1.1.1 have been refactored in the 1.1.2 source:
- `Disposer.isDisposed(Disposable)`: Replaced with an explicit `AtomicBoolean` lifecycle flag registered with `Disposer.register(composite, ...)`.
- `IdeFocusManager.doWhenFocusSettlesDown(Runnable)`: Replaced with `SwingUtilities.invokeLater(...)` for EDT scheduling, matching the IntelliJ Platform SDK recommended migration path.

Verification with `-Xlint:deprecation` now reports **0 deprecation warnings**.

---

### 2.3 Experimental API Usages (0 remaining)

The 3 experimental usages reported for 1.1.1 have been refactored in the 1.1.2 source:
- `EditorCompositeListener` and `editorAdded(...)`: Replaced by subscribing to standard OpenAPI `FileEditorManagerListener.FILE_EDITOR_MANAGER` events on the project message bus.
- `TextEditor.isEditorLoaded()`: Removed from diagnostic logging in favor of standard `FileEditorManagerEx.runWhenLoaded(...)`.

All experimental API usages are now **0**.

---

## 3. Recommended Strategy

- **Marketplace follow-up:** The Marketplace rejection email specifically cites internal API findings. The author can ask JetBrains whether a public alternative exists or explain why split-pane routing requires these APIs.
- **Future maintenance:** Re-run Plugin Verifier against the 1.1.3 archive to confirm its findings. The 1.1.1 report itself remains historical and still records its original deprecated and experimental usages.
