package io.github.f_breure.splitrouter;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class SplitRouterConfigurable implements Configurable {

    private JTextField extensionsField;
    private final Project project;
    private boolean clearOverrides;

    public SplitRouterConfigurable(Project project) {
        this.project = project;
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Split Window File Router";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Right window extensions (comma-separated):"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        extensionsField = new JTextField();
        panel.add(extensionsField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JButton clearButton = new JButton("Reset manual routing overrides for this project");
        clearButton.addActionListener(event -> clearOverrides = true);
        panel.add(clearButton, gbc);

        gbc.gridy = 2;
        panel.add(new JLabel("Dragged tabs stay exempt across restarts. Apply to reset overrides."), gbc);

        gbc.gridy = 3;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JPanel(), gbc);

        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        SplitRouterSettings settings = SplitRouterSettings.getInstance();
        return clearOverrides || (extensionsField != null
                && !extensionsField.getText().trim().equals(settings.headerExtensions.trim()));
    }

    @Override
    public void apply() {
        if (extensionsField != null) {
            SplitRouterSettings.getInstance().headerExtensions = extensionsField.getText().trim();
        }
        if (clearOverrides) {
            ManualRoutingOverrides.getInstance(project).clearOverrides();
            clearOverrides = false;
        }
    }

    @Override
    public void reset() {
        clearOverrides = false;
        if (extensionsField != null) {
            extensionsField.setText(SplitRouterSettings.getInstance().headerExtensions);
        }
    }

    @Override
    public void disposeUIResources() {
        extensionsField = null;
        clearOverrides = false;
    }
}
