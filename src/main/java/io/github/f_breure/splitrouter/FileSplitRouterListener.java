package io.github.f_breure.splitrouter;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Point;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.List;
import java.util.Locale;

public class FileSplitRouterListener implements FileEditorManagerListener, FileEditorManagerListener.Before {

    private static final Logger LOG = Logger.getInstance(FileSplitRouterListener.class);

    private EditorWindow getTargetWindow(FileEditorManagerEx editorManagerEx, VirtualFile file) {
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
    public void beforeFileOpened(FileEditorManager source, VirtualFile file) {
        Project project = source.getProject();
        if (project.isDisposed()) return;
        if (ManualRoutingOverrides.getInstance(project).shouldSkip(file)) return;

        FileEditorManagerEx editorManagerEx = FileEditorManagerEx.getInstanceEx(project);
        EditorWindow targetWindow = getTargetWindow(editorManagerEx, file);
        if (targetWindow == null) return;

        EditorWindow currentWindow = editorManagerEx.getCurrentWindow();
        if (currentWindow != null && currentWindow != targetWindow) {
            editorManagerEx.setCurrentWindow(targetWindow);
        }
    }

    @Override
    public void fileOpened(FileEditorManager source, VirtualFile file) {
        FileEditorManagerEx manager = FileEditorManagerEx.getInstanceEx(source.getProject());
        EditorWindow current = manager.getCurrentWindow();
        routeFile(source, file, current != null && file.equals(current.getSelectedFile()));
    }

    @Override
    public void selectionChanged(FileEditorManagerEvent event) {
        if (event.getManager().getProject().isDisposed()) return;
        ManualRoutingOverrides.getInstance(event.getManager().getProject()).recordSelection();
        VirtualFile file = event.getNewFile();
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

        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed() || !file.isValid() || overrides.shouldSkip(file)) {
                return;
            }
            overrides.observeTabs(editorManagerEx);

            EditorWindow targetWindow = getTargetWindow(editorManagerEx, file);
            if (targetWindow == null) {
                return;
            }

            List<EditorWindow> misplacedWindows = Arrays.stream(editorManagerEx.getSplitters().getWindows())
                    .filter(window -> window != targetWindow && window.isFileOpen(file))
                    .toList();
            if (!misplacedWindows.isEmpty()) {
                boolean focusDestination = selectedOpening
                        && selectionVersion == overrides.getSelectionVersion();
                overrides.setRouting(true);
                try {
                    FileEditorOpenOptions options = new FileEditorOpenOptions()
                            .withSelectAsCurrent(focusDestination)
                            .withRequestFocus(focusDestination);
                    editorManagerEx.openFile(file, targetWindow, options);
                    // Do not close the original tab if the destination failed to open.
                    if (targetWindow.isFileOpen(file)) {
                        for (EditorWindow window : misplacedWindows) {
                            window.closeFile(file, false);
                        }
                        if (focusDestination) {
                            editorManagerEx.setCurrentWindow(targetWindow);
                            targetWindow.setSelectedComposite(file, true);
                            targetWindow.requestFocus(true);
                            IdeFocusManager focusManager = IdeFocusManager.getInstance(project);
                            // Closing the old tab can enqueue its own focus request; re-assert focus on destination
                            focusManager.doWhenFocusSettlesDown(() -> {
                                if (project.isDisposed() || !file.isValid()
                                        || editorManagerEx.getCurrentWindow() != targetWindow
                                        || !file.equals(targetWindow.getSelectedFile())
                                        || selectionVersion != overrides.getSelectionVersion()
                                        || overrides.shouldSkip(file)) {
                                    return;
                                }
                                var composite = targetWindow.getSelectedComposite();
                                if (composite != null) {
                                    JComponent component = composite.getPreferredFocusedComponent();
                                    if (component != null) {
                                        focusManager.requestFocus(component, true);
                                    } else {
                                        targetWindow.requestFocus(true);
                                    }
                                } else {
                                    targetWindow.requestFocus(true);
                                }
                            });
                        }
                    } else {
                        LOG.warn("Could not route file to the target editor pane: " + file.getPath());
                    }
                } finally {
                    overrides.setRouting(false);
                    overrides.observeTabs(editorManagerEx);
                }
            }
        });
    }
}
