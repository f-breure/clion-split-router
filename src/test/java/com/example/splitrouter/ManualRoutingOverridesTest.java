package com.example.splitrouter;

import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.xmlb.XmlSerializer;
import org.junit.Test;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class ManualRoutingOverridesTest {
    private static final String FILE = "file:///project/main.cpp";

    @Test
    public void routingEventsDoNotInvalidateFocusButLaterUserSelectionsDo() {
        ManualRoutingOverrides overrides = new ManualRoutingOverrides();
        overrides.recordSelection();
        long initial = overrides.getSelectionVersion();
        overrides.setRouting(true);
        overrides.recordSelection();
        assertEquals(initial, overrides.getSelectionVersion());
        overrides.setRouting(false);
        overrides.recordSelection();
        assertTrue(overrides.getSelectionVersion() > initial);
    }

    @Test
    public void onlyCompletedPaneChangesCreateOverrides() {
        ManualRoutingOverrides overrides = new ManualRoutingOverrides();
        overrides.rememberMove(FILE, false);
        assertFalse(overrides.isExempt(FILE));
        overrides.rememberMove(FILE, true);
        assertTrue(overrides.isExempt(FILE));
        assertFalse(overrides.isExempt("file:///project/other.cpp"));
    }

    @Test
    public void overridesSurviveStateReloadAndStayProjectLocal() {
        ManualRoutingOverrides firstProject = new ManualRoutingOverrides();
        firstProject.rememberMove(FILE, true);
        ManualRoutingOverrides reopened = new ManualRoutingOverrides();
        reopened.loadState(XmlSerializer.deserialize(XmlSerializer.serialize(firstProject.getState()),
                ManualRoutingOverrides.SavedState.class));
        assertTrue(reopened.isExempt(FILE));
        assertFalse(new ManualRoutingOverrides().isExempt(FILE));
    }

    @Test
    public void resetClearsSavedOverrides() {
        ManualRoutingOverrides overrides = new ManualRoutingOverrides();
        overrides.rememberMove(FILE, true);
        overrides.clearOverrides();
        assertFalse(overrides.isExempt(FILE));
        assertTrue(overrides.getState().fileUrls.isEmpty());
    }

    @Test
    public void loadingStateReplacesPreviousOverrides() {
        ManualRoutingOverrides overrides = new ManualRoutingOverrides();
        overrides.rememberMove(FILE, true);
        overrides.loadState(new ManualRoutingOverrides.SavedState());
        assertFalse(overrides.isExempt(FILE));
    }

    @Test
    public void dragTemporarilySuspendsRoutingAndPersistsOnlyOnCompletion() {
        ManualRoutingOverrides overrides = new ManualRoutingOverrides();
        RecordingDrag original = new RecordingDrag();
        TabInfo.DragOutDelegate observer = overrides.observeDrag(original, FILE, () -> true);
        observer.dragOutStarted(null, null);
        assertTrue(overrides.shouldSkip(FILE));
        assertFalse(overrides.isExempt(FILE));
        observer.processDragOut(null, null);
        observer.dragOutFinished(null, null);
        assertTrue(overrides.isExempt(FILE));
        assertEquals(List.of("started", "dragged", "finished"), original.calls);
        overrides.clearOverrides();
        assertFalse(overrides.shouldSkip(FILE));
    }

    @Test
    public void cancellingDragDoesNotCreateOverride() {
        ManualRoutingOverrides overrides = new ManualRoutingOverrides();
        RecordingDrag original = new RecordingDrag();
        TabInfo.DragOutDelegate observer = overrides.observeDrag(original, FILE, () -> true);
        observer.dragOutStarted(null, null);
        observer.dragOutCancelled(null);
        assertFalse(overrides.shouldSkip(FILE));
        assertEquals(List.of("started", "cancelled"), original.calls);
    }

    @Test
    public void droppingIntoSamePaneDoesNotCreateOverride() {
        ManualRoutingOverrides overrides = new ManualRoutingOverrides();
        TabInfo.DragOutDelegate observer = overrides.observeDrag(new RecordingDrag(), FILE, () -> false);
        observer.dragOutStarted(null, null);
        observer.dragOutFinished(null, null);
        assertFalse(overrides.shouldSkip(FILE));
    }

    @Test
    public void failedDropRestoresRoutingAndPropagatesFailure() {
        ManualRoutingOverrides overrides = new ManualRoutingOverrides();
        RecordingDrag original = new RecordingDrag();
        original.failDrop = true;
        TabInfo.DragOutDelegate observer = overrides.observeDrag(original, FILE, () -> true);
        observer.dragOutStarted(null, null);
        assertThrows(IllegalStateException.class, () -> observer.dragOutFinished(null, null));
        assertFalse(overrides.shouldSkip(FILE));
    }

    @Test
    public void asynchronousDropStaysExemptUntilDestinationAppears() {
        ManualRoutingOverrides overrides = new ManualRoutingOverrides();
        AtomicBoolean destinationOpen = new AtomicBoolean();
        TabInfo.DragOutDelegate observer = overrides.observeDrag(
                new RecordingDrag(), FILE, destinationOpen::get, destinationOpen::get);
        observer.dragOutStarted(null, null);
        observer.dragOutFinished(null, null);
        assertTrue(overrides.shouldSkip(FILE));
        assertFalse(overrides.isExempt(FILE));
        destinationOpen.set(true);
        assertTrue(overrides.shouldSkip(FILE));
        assertTrue(overrides.isExempt(FILE));
        ManualRoutingOverrides reopened = new ManualRoutingOverrides();
        reopened.loadState(overrides.getState());
        assertTrue(reopened.isExempt(FILE));
    }

    private static final class RecordingDrag implements TabInfo.DragOutDelegate {
        private final List<String> calls = new ArrayList<>();
        private boolean failDrop;

        @Override
        public void dragOutStarted(MouseEvent event, TabInfo tab) {
            calls.add("started");
        }

        @Override
        public void processDragOut(MouseEvent event, TabInfo tab) {
            calls.add("dragged");
        }

        @Override
        public void dragOutFinished(MouseEvent event, TabInfo tab) {
            calls.add("finished");
            if (failDrop) throw new IllegalStateException("Drop failed");
        }

        @Override
        public void dragOutCancelled(TabInfo tab) {
            calls.add("cancelled");
        }
    }
}
