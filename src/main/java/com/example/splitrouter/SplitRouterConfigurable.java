package com.example.splitrouter;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class SplitRouterConfigurable implements Configurable {

    private JTextField extensionsField;

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
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Right window extensions (comma-separated):"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        extensionsField = new JTextField();
        panel.add(extensionsField, gbc);

        // Filler to keep controls pinned to the top
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JPanel(), gbc);

        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        SplitRouterSettings settings = SplitRouterSettings.getInstance();
        return extensionsField != null && !extensionsField.getText().trim().equals(settings.headerExtensions.trim());
    }

    @Override
    public void apply() throws ConfigurationException {
        if (extensionsField != null) {
            SplitRouterSettings.getInstance().headerExtensions = extensionsField.getText().trim();
        }
    }

    @Override
    public void reset() {
        if (extensionsField != null) {
            extensionsField.setText(SplitRouterSettings.getInstance().headerExtensions);
        }
    }
}
