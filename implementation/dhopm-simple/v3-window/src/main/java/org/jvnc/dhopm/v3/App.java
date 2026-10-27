package org.jvnc.dhopm.v3;

import javax.swing.SwingUtilities;

public final class App {
    private App() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new EngineWindow().setVisible(true));
    }
}
