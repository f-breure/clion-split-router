package io.github.f_breure.splitrouter;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Point;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileSplitRouterListener implements FileEditorManagerListener {

    private static final Logger LOG = Logger.getInstance(FileSplitRouterListener.class);

    static EditorWindow getTargetWindow(FileEditorManagerEx editorManagerEx, VirtualFile file) {
        EditorsSplitters splitters = editorManagerEx.getSplitters();
        EditorWindow[] windows = splitters.getWindows();
        if (windows == null || windows.length < 2) {
            return null;
        }

        // Sort windows horizontally from left to right based on screen coordinates
        Arrays.sort(windows, Comparator.comparingInt(w -> {
            JComponent comp = w.getTabbedPane().getComponent();
            if (SwingUtilities.getRoot(comp) != null) {
                Point p = new Point(0, 0);
                SwingUtilities.convertPointToScreen(p, comp);
                return p.x;
            }
            return comp.getX();
        }));

        EditorWindow leftWindow = windows[0];
        EditorWindow rightWindow = windows[windows.length - 1];

        String ext = file.getExtension();
        Set<String> headerExtensions = SplitRouterSettings.getInstance().getExtensionSet();
        boolean isHeader = ext != null && headerExtensions.contains(ext.toLowerCase(Locale.ROOT));
        return isHeader ? rightWindow : leftWindow;
    }

    @Override
    public void fileOpened(FileEditorManager source, VirtualFile file) {
        FileEditorManagerEx manager = FileEditorManagerEx.getInstanceEx(source.getProject());
        // The pane holding the new tab may not be the current pane.
        boolean selected = Arrays.stream(manager.getSplitters().getWindows())
                .anyMatch(window -> file.equals(window.getSelectedFile()));
        routeFile(source, file, selected);
    }

    @Override
    public void selectionChanged(FileEditorManagerEvent event) {
        Project project = event.getManager().getProject();
        if (project.isDisposed()) return;
        ManualRoutingOverrides overrides = ManualRoutingOverrides.getInstance(project);
        VirtualFile file = event.getNewFile();
        if (file != null) {
            boolean inSource = overrides.getTransferSources().stream()
                    .anyMatch(window -> !window.isDisposed() && file.equals(window.getSelectedFile()));
            if (overrides.isFocusTransferSelection(file.getUrl(), inSource, System.currentTimeMillis())) {
                LOG.debug("Split router: ignoring selection caused by tab move; file=" + file.getName());
                return;
            }
        }
        overrides.recordSelection();
        if (file != null) {
            routeFile(event.getManager(), file, true);
        }
    }

    private void routeFile(FileEditorManager source, VirtualFile file, boolean selectedOpening) {
        Project project = source.getProject();
        if (project.isDisposed()) return;
        ManualRoutingOverrides overrides = ManualRoutingOverrides.getInstance(project);
        FileEditorManagerEx editorManagerEx = FileEditorManagerEx.getInstanceEx(project);
        overrides.observeTabs(editorManagerEx);
        if (overrides.shouldSkip(file)) return;
        long selectionVersion = overrides.getSelectionVersion();
        logFocus("route queued; selectedOpening=" + selectedOpening, editorManagerEx,
                null, file, overrides, selectionVersion);

        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed() || !file.isValid() || overrides.shouldSkip(file)) {
                LOG.debug("Split router: queued route cancelled (disposed, invalid, or override)");
                return;
            }
            overrides.observeTabs(editorManagerEx);

            EditorWindow targetWindow = getTargetWindow(editorManagerEx, file);
            if (targetWindow == null) {
                LOG.debug("Split router: no split destination");
                return;
            }

            List<EditorWindow> misplacedWindows = Arrays.stream(editorManagerEx.getSplitters().getWindows())
                    .filter(window -> window != targetWindow && window.isFileOpen(file))
                    .toList();
            if (!misplacedWindows.isEmpty()) {
                boolean focusDestination = selectedOpening
                        && selectionVersion == overrides.getSelectionVersion();
                logFocus("moving; sourceCount=" + misplacedWindows.size()
                        + "; focusDestination=" + focusDestination, editorManagerEx,
                        targetWindow, file, overrides, selectionVersion);
                if (focusDestination) {
                    overrides.beginFocusTransfer(file.getUrl(), misplacedWindows,
                            System.currentTimeMillis());
                }
                overrides.setRouting(true);
                try {
                    FileEditorOpenOptions options = new FileEditorOpenOptions()
                            .withSelectAsCurrent(focusDestination)
                            .withRequestFocus(false);
                    editorManagerEx.openFile(file, targetWindow, options);
                    // Do not close the original tab if the destination failed to open.
                    if (targetWindow.isFileOpen(file)) {
                        for (EditorWindow window : misplacedWindows) {
                            window.closeFile(file, false);
                        }
                        if (focusDestination) {
                            editorManagerEx.setCurrentWindow(targetWindow);
                            targetWindow.setSelectedComposite(file, false);
                            focusWhenEditorReady(editorManagerEx, targetWindow, file,
                                    overrides, selectionVersion);
                        }
                    } else {
                        overrides.endFocusTransfer(file.getUrl());
                        LOG.warn("Could not route file to the target editor pane: " + file.getPath());
                    }
                } finally {
                    overrides.setRouting(false);
                    overrides.observeTabs(editorManagerEx);
                }
            } else {
                logFocus("no misplaced tab; no focus scheduled", editorManagerEx,
                        targetWindow, file, overrides, selectionVersion);
            }
        });
    }

    private void focusWhenEditorReady(FileEditorManagerEx manager, EditorWindow window,
                                      VirtualFile file, ManualRoutingOverrides overrides,
                                      long selectionVersion) {
        Project project = manager.getProject();
        var composite = window.getComposite(file);
        if (composite == null) {
            LOG.warn("No destination editor composite for routed file: " + file.getPath());
            overrides.endFocusTransfer(file.getUrl());
            return;
        }
        AtomicBoolean active = new AtomicBoolean(true);
        var listenerLifetime = Disposer.newDisposable("Split router editor readiness");
        Disposer.register(composite, () -> {
            active.set(false);
            Disposer.dispose(listenerLifetime);
        });
        logFocus("waiting for editor", manager, window, file, overrides, selectionVersion);
        Runnable focusSelectedEditor = () -> {
            if (!active.get()) return;
            logFocus("editor readiness callback", manager, window, file, overrides, selectionVersion);
            if (project.isDisposed() || window.isDisposed() || !file.isValid()
                    || selectionVersion != overrides.getSelectionVersion()
                    || overrides.shouldSkip(file)
                    || window.getSelectedComposite() != composite) {
                LOG.debug("Split router: readiness callback cancelled; see preceding state");
                overrides.endFocusTransfer(file.getUrl());
                active.set(false);
                Disposer.dispose(listenerLifetime);
                return;
            }
            var selectedEditor = composite.getSelectedEditor();
            if (selectedEditor == null) {
                LOG.debug("Split router: selected editor not created yet");
                return;
            }
            active.set(false);
            Disposer.dispose(listenerLifetime);
            IdeFocusManager focusManager = IdeFocusManager.getInstance(project);
            Runnable focus = () -> SwingUtilities.invokeLater(() -> {
                logFocus("loaded editor; requesting focus", manager, window, file,
                        overrides, selectionVersion);
                if (project.isDisposed() || window.isDisposed() || !file.isValid()
                        || selectionVersion != overrides.getSelectionVersion()
                        || overrides.shouldSkip(file)
                        || window.getSelectedComposite() != composite
                        || composite.getSelectedEditor() != selectedEditor) {
                    overrides.endFocusTransfer(file.getUrl());
                    LOG.debug("Split router: loaded-editor focus cancelled; see preceding state"
                            + "; selectedEditorChanged="
                            + (composite.getSelectedEditor() != selectedEditor));
                    return;
                }
                JComponent component = selectedEditor instanceof TextEditor textEditor
                        ? textEditor.getEditor().getContentComponent()
                        : selectedEditor.getPreferredFocusedComponent();
                overrides.endFocusTransfer(file.getUrl());
                // Closing the source tab can make the source pane current again.
                manager.setCurrentWindow(window);
                if (component == null) {
                    LOG.warn("No focus component for routed file: " + file.getPath());
                } else {
                    LOG.debug("Split router: requesting focus; component=" + componentId(component)
                            + "; showing=" + component.isShowing()
                            + "; focusable=" + component.isFocusable());
                    var result = focusManager.requestFocus(component, true);
                    if (LOG.isDebugEnabled()) {
                        result.doWhenDone(() -> {
                            LOG.debug("Split router: focus request completed; owner="
                                    + componentId(KeyboardFocusManager.getCurrentKeyboardFocusManager()
                                    .getFocusOwner()));
                            SwingUtilities.invokeLater(() ->
                                    logFocus("after focus request settled", manager, window,
                                            file, overrides, selectionVersion));
                        });
                        result.doWhenRejected(() -> LOG.debug("Split router: focus request rejected"));
                    }
                }
            });
            if (selectedEditor instanceof TextEditor textEditor) {
                LOG.debug("Split router: registering runWhenLoaded");
                manager.runWhenLoaded(textEditor.getEditor(), focus);
            } else {
                focus.run();
            }
        };
        // A new composite can exist before its asynchronous file editors are created.
        project.getMessageBus().connect(listenerLifetime).subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                new FileEditorManagerListener() {
                    @Override
                    public void fileOpened(FileEditorManager source, VirtualFile openedFile) {
                        if (file.equals(openedFile)) {
                            ApplicationManager.getApplication().invokeLater(focusSelectedEditor);
                        }
                    }
                }
        );
        ApplicationManager.getApplication().invokeLater(focusSelectedEditor);
    }

    private void logFocus(String stage, FileEditorManagerEx manager, EditorWindow destination,
                          VirtualFile file, ManualRoutingOverrides overrides, long version) {
        if (!LOG.isDebugEnabled()) return;
        Project project = manager.getProject();
        if (project.isDisposed()) {
            LOG.debug("Split router: " + stage + "; projectDisposed=true");
            return;
        }
        EditorWindow current = manager.getCurrentWindow();
        boolean destinationLive = destination != null && !destination.isDisposed();
        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        LOG.debug("Split router: " + stage + "; file=" + file.getName()
                + "; fileValid=" + file.isValid()
                + "; capturedSelection=" + version
                + "; selectionNow=" + overrides.getSelectionVersion()
                + "; currentPane=" + paneId(current)
                + "; destinationPane=" + paneId(destination)
                + "; destinationLive=" + destinationLive
                + "; destinationSelected=" + (destinationLive
                && file.equals(destination.getSelectedFile()))
                + "; focusOwner=" + componentId(owner)
                + "; ownerInDestination=" + (destinationLive && owner != null
                && SwingUtilities.isDescendingFrom(owner, destination.getTabbedPane().getComponent())));
    }

    private static String paneId(EditorWindow window) {
        return window == null ? "null" : Integer.toHexString(System.identityHashCode(window));
    }

    private static String componentId(Component component) {
        return component == null ? "null" : component.getClass().getName()
                + "@" + Integer.toHexString(System.identityHashCode(component));
    }
}
