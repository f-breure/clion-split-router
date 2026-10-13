package com.example.splitrouter;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Point;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

public class FileSplitRouterListener implements FileEditorManagerListener, FileEditorManagerListener.Before {

    private volatile boolean isRouting = false;

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
        boolean isHeader = ext != null && headerExtensions.contains(ext.toLowerCase());
        return isHeader ? rightWindow : leftWindow;
    }

    @Override
    public void beforeFileOpened(FileEditorManager source, VirtualFile file) {
        if (isRouting) return;

        Project project = source.getProject();
        if (project.isDisposed()) return;

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
        Project project = source.getProject();
        FileEditorManagerEx editorManagerEx = FileEditorManagerEx.getInstanceEx(project);

        ApplicationManager.getApplication().invokeLater(() -> {
            if (isRouting || project.isDisposed()) {
                return;
            }

            EditorWindow targetWindow = getTargetWindow(editorManagerEx, file);
            if (targetWindow == null) {
                return;
            }

            EditorWindow currentWindow = editorManagerEx.getCurrentWindow();
            if (currentWindow != null && currentWindow != targetWindow) {
                isRouting = true;
                try {
                    FileEditorOpenOptions options = new FileEditorOpenOptions()
                            .withSelectAsCurrent(true)
                            .withRequestFocus(true);
                    editorManagerEx.openFile(file, targetWindow, options);
                    currentWindow.closeFile(file);
                } finally {
                    isRouting = false;
                }
            }
        });
    }
}
