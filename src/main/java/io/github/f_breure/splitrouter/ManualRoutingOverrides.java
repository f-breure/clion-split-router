package io.github.f_breure.splitrouter;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.tabs.TabInfo;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

@State(name = "SplitRouterManualOverrides", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class ManualRoutingOverrides implements PersistentStateComponent<ManualRoutingOverrides.SavedState>, Disposable {
    public static final class SavedState {
        public List<String> fileUrls = new ArrayList<>();
    }

    private final Set<String> fileUrls = new HashSet<>();
    private final Set<String> dragging = new HashSet<>();
    private final Map<String, ObservedDrag> pendingDrops = new java.util.HashMap<>();
    private final Map<TabInfo, ObservedDrag> observedTabs = new IdentityHashMap<>();
    static final long FOCUS_TRANSFER_TIMEOUT_MS = 3000;

    private boolean routing;
    private long selectionVersion;
    private String transferUrl;
    private long transferDeadline;
    private List<EditorWindow> transferSources = List.of();

    public void recordSelection() {
        if (!routing) selectionVersion++;
    }

    public void beginFocusTransfer(String url, List<EditorWindow> sources, long nowMillis) {
        transferUrl = url;
        transferSources = List.copyOf(sources);
        transferDeadline = nowMillis + FOCUS_TRANSFER_TIMEOUT_MS;
    }

    public List<EditorWindow> getTransferSources() {
        return transferUrl == null ? List.of() : transferSources;
    }

    public void endFocusTransfer(String url) {
        if (url.equals(transferUrl)) {
            transferUrl = null;
            transferSources = List.of();
        }
    }

    /**
     * Selection events caused by moving a tab: selecting the moved file in its
     * destination, or the replacement tab CLion selects in the source pane.
     */
    public boolean isFocusTransferSelection(String newUrl, boolean selectedInSourcePane, long nowMillis) {
        if (transferUrl == null) return false;
        if (nowMillis > transferDeadline) {
            transferUrl = null;
            transferSources = List.of();
            return false;
        }
        return transferUrl.equals(newUrl) || selectedInSourcePane;
    }

    public long getSelectionVersion() {
        return selectionVersion;
    }

    public static ManualRoutingOverrides getInstance(Project project) {
        return project.getService(ManualRoutingOverrides.class);
    }

    @Override
    public synchronized SavedState getState() {
        SavedState state = new SavedState();
        state.fileUrls = fileUrls.stream().sorted().toList();
        return state;
    }

    @Override
    public synchronized void loadState(SavedState state) {
        fileUrls.clear();
        fileUrls.addAll(state.fileUrls);
    }

    public synchronized boolean isExempt(String url) {
        return fileUrls.contains(url);
    }

    public synchronized void rememberMove(String url, boolean changedPane) {
        rememberMove(url, changedPane, false);
    }

    public synchronized void rememberMove(String url, boolean changedPane, boolean movedToDefault) {
        if (changedPane) {
            if (movedToDefault) {
                fileUrls.remove(url);
            } else {
                fileUrls.add(url);
            }
        }
    }

    public synchronized void clearOverrides() {
        fileUrls.clear();
        pendingDrops.clear();
    }

    public boolean shouldSkip(VirtualFile file) {
        return shouldSkip(file.getUrl());
    }

    boolean shouldSkip(String url) {
        ObservedDrag pending = pendingDrops.get(url);
        if (pending != null) {
            if (pending.changedPane.getAsBoolean()) {
                rememberMove(url, true, pending.movedToDefault.getAsBoolean());
                pendingDrops.remove(url);
            } else if (pending.filePresent.getAsBoolean()) {
                pendingDrops.remove(url);
            } else {
                return true;
            }
        }
        return routing || dragging.contains(url) || isExempt(url);
    }

    public void setRouting(boolean routing) {
        this.routing = routing;
    }

    public void observeTabs(FileEditorManagerEx manager) {
        Set<TabInfo> liveTabs = new HashSet<>();
        for (EditorWindow window : manager.getSplitters().getWindows()) {
            for (TabInfo tab : window.getTabbedPane().getTabs().getTabs()) {
                liveTabs.add(tab);
                if (tab.getObject() instanceof VirtualFile file) {
                    TabInfo.DragOutDelegate original = tab.getDragOutDelegate();
                    if (original != null && !(original instanceof ObservedDrag)) {
                        ObservedDrag observer = new ObservedDrag(original, file.getUrl(),
                                () -> findDestinationWindow(manager, window, file) != null,
                                () -> {
                                    EditorWindow dest = findDestinationWindow(manager, window, file);
                                    if (dest == null) return false;
                                    EditorWindow target = FileSplitRouterListener.getTargetWindow(manager, file);
                                    return target != null && dest == target;
                                },
                                () -> java.util.Arrays.stream(manager.getWindows())
                                        .anyMatch(candidate -> candidate.isFileOpen(file)));
                        observedTabs.put(tab, observer);
                        tab.setDragOutDelegate(observer);
                    }
                }
            }
        }
        observedTabs.entrySet().removeIf(entry -> {
            if (liveTabs.contains(entry.getKey()) || dragging.contains(entry.getValue().url)) return false;
            restoreDelegate(entry.getKey(), entry.getValue());
            return true;
        });
    }

    private static EditorWindow findDestinationWindow(FileEditorManagerEx manager, EditorWindow sourceWindow, VirtualFile file) {
        return java.util.Arrays.stream(manager.getWindows())
                .filter(candidate -> candidate != sourceWindow && candidate.isFileOpen(file))
                .findFirst()
                .orElse(null);
    }

    private void restoreDelegate(TabInfo tab, ObservedDrag observer) {
        if (tab.getDragOutDelegate() == observer) {
            tab.setDragOutDelegate(observer.original);
        }
    }

    ObservedDrag observeDrag(TabInfo.DragOutDelegate original, String url, BooleanSupplier changedPane) {
        return new ObservedDrag(original, url, changedPane, () -> false, () -> true);
    }

    ObservedDrag observeDrag(TabInfo.DragOutDelegate original, String url,
                             BooleanSupplier changedPane, BooleanSupplier filePresent) {
        return new ObservedDrag(original, url, changedPane, () -> false, filePresent);
    }

    ObservedDrag observeDrag(TabInfo.DragOutDelegate original, String url,
                             BooleanSupplier changedPane, BooleanSupplier movedToDefault,
                             BooleanSupplier filePresent) {
        return new ObservedDrag(original, url, changedPane, movedToDefault, filePresent);
    }

    @Override
    public void dispose() {
        observedTabs.forEach(this::restoreDelegate);
        observedTabs.clear();
        dragging.clear();
        pendingDrops.clear();
    }

    // Preserve CLion's own drag implementation; only observe its start/end.
    final class ObservedDrag implements TabInfo.DragOutDelegate {
        private final TabInfo.DragOutDelegate original;
        private final String url;
        private final BooleanSupplier changedPane;
        private final BooleanSupplier movedToDefault;
        private final BooleanSupplier filePresent;

        private ObservedDrag(TabInfo.DragOutDelegate original, String url,
                             BooleanSupplier changedPane, BooleanSupplier movedToDefault,
                             BooleanSupplier filePresent) {
            this.original = original;
            this.url = url;
            this.changedPane = changedPane;
            this.movedToDefault = movedToDefault;
            this.filePresent = filePresent;
        }

        @Override
        public void dragOutStarted(MouseEvent event, TabInfo tab) {
            dragging.add(url);
            try {
                original.dragOutStarted(event, tab);
            } catch (RuntimeException | Error failure) {
                dragging.remove(url);
                throw failure;
            }
        }

        @Override
        public void processDragOut(MouseEvent event, TabInfo tab) {
            try {
                original.processDragOut(event, tab);
            } catch (RuntimeException | Error failure) {
                dragging.remove(url);
                throw failure;
            }
        }

        @Override
        public void dragOutFinished(MouseEvent event, TabInfo tab) {
            try {
                original.dragOutFinished(event, tab);
                if (changedPane.getAsBoolean()) {
                    rememberMove(url, true, movedToDefault.getAsBoolean());
                } else if (!filePresent.getAsBoolean()) {
                    // CLion may create the destination asynchronously after the drag ends.
                    pendingDrops.put(url, this);
                }
            } finally {
                dragging.remove(url);
            }
        }

        @Override
        public void dragOutCancelled(TabInfo tab) {
            try {
                original.dragOutCancelled(tab);
            } finally {
                dragging.remove(url);
            }
        }
    }
}
