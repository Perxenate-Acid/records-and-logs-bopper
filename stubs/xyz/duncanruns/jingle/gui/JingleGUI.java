package xyz.duncanruns.jingle.gui;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Container;
import java.util.function.Supplier;

/**
 * Compile-time stub of JingleGUI (matches the real signatures used by this plugin).
 * NOT included in the final plugin jar - Jingle provides the real classes at runtime.
 */
public class JingleGUI extends JFrame {

    public static synchronized JingleGUI get() {
        return null;
    }

    public static void addPluginTab(String name, JPanel panel) {
    }

    public static void addPluginTab(String name, JPanel panel, Runnable onSwitchTo) {
    }

    public static JButton makeButton(String text, Runnable onClick, Runnable onRightClick, String toolTipText, Boolean enabled) {
        return new JButton();
    }

    public void registerQuickActionButton(int priority, Supplier<JButton> buttonSupplier) {
    }

    public void openTab(Container tab) {
    }
}
