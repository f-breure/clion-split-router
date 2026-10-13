package io.github.f_breure.splitrouter;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@State(
        name = "io.github.f_breure.splitrouter.SplitRouterSettings",
        storages = @Storage("SplitRouterSettings.xml")
)
public class SplitRouterSettings implements PersistentStateComponent<SplitRouterSettings> {

    public String headerExtensions = "h, hpp, hxx, hh, inc, inl, tpp, ipp";

    public static SplitRouterSettings getInstance() {
        return ApplicationManager.getApplication().getService(SplitRouterSettings.class);
    }

    @Nullable
    @Override
    public SplitRouterSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull SplitRouterSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public Set<String> getExtensionSet() {
        Set<String> set = new HashSet<>();
        if (headerExtensions != null) {
            for (String ext : headerExtensions.split("[,;\\s]+")) {
                String clean = ext.trim().toLowerCase();
                if (!clean.isEmpty()) {
                    if (clean.startsWith(".")) {
                        clean = clean.substring(1);
                    }
                    set.add(clean);
                }
            }
        }
        return set;
    }
}
