package com.speedrunigtcleaner;

import org.apache.logging.log4j.Level;
import xyz.duncanruns.jingle.Jingle;
import xyz.duncanruns.jingle.gui.JingleGUI;
import xyz.duncanruns.jingle.plugin.PluginHotkeys;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Properties;

/**
 * Jingle plugin ("Records & Logs Bopper") that cleans up:
 *   1. SpeedrunIGT "records" folder (%USERPROFILE%\speedrunigt\records)
 *      where the mod keeps speedrun records that pile up over time.
 *   2. Minecraft log files in MultiMC / Prism Launcher instances
 *      (.minecraft/logs), preserving latest.log and keeping recent logs.
 */
public final class SpeedrunIGTCleanerPlugin {

    private static final String TAB_NAME = "Records & Logs Bopper";
    private static final String QUICK_ACTION_TEXT = "Clean Records";
    private static final String QUICK_ACTION_LOGS_TEXT = "Clean Logs";
    private static final String HOTKEY_ACTION = "Clean Records";
    private static final String HOTKEY_LOGS_ACTION = "Clean Logs";

    private static final String CONFIG_FILE_NAME = "records-and-logs-bopper.properties";
    private static final String PROP_AUTO_CLEAN = "autoCleanOnStartup";
    private static final String PROP_THRESHOLD_MB = "thresholdMB";
    private static final String PROP_KEEP_RECENT_MANUAL = "keepRecentManual";
    private static final String PROP_KEEP_RECENT_AUTO = "keepRecentAuto";
    private static final String PROP_RECORDS_KEEP_RECENT = "recordsKeepRecent";
    private static final String PROP_LOGS_AUTO_CLEAN = "logsAutoCleanOnStartup";
    private static final String PROP_LOGS_THRESHOLD_MB = "logsThresholdMB";
    private static final String PROP_LOGS_KEEP_RECENT = "logsKeepRecent";
    private static final String PROP_LOGS_KEEP_RECENT_MANUAL = "logsKeepRecentManual";
    private static final String PROP_MULTIMC_PATH = "multimcPath";

    private static final boolean DEFAULT_AUTO_CLEAN = false;
    private static final double DEFAULT_THRESHOLD_MB = 50.0;
    private static final double MIN_THRESHOLD_MB = 0.0;
    private static final int DEFAULT_RECORDS_KEEP_RECENT = 10;

    private static final boolean DEFAULT_LOGS_AUTO_CLEAN = false;
    private static final double DEFAULT_LOGS_THRESHOLD_MB = 50.0;
    private static final int DEFAULT_LOGS_KEEP_RECENT = 5;
    private static final boolean DEFAULT_LOGS_KEEP_RECENT_MANUAL = true;

    private static final DecimalFormat MB_FORMAT = new DecimalFormat("0.0");
    private static final Color ERROR_COLOR = new Color(0xC62828);

    private static Path configPath;
    private static JPanel mainPanel;
    private static JLabel statusLabel;
    private static JLabel logsStatusLabel;
    private static JTextField multimcPathField;
    private static JCheckBox logsKeepRecentManualCheckbox;

    private static boolean autoCleanEnabled = DEFAULT_AUTO_CLEAN;
    private static double thresholdMB = DEFAULT_THRESHOLD_MB;
    private static boolean keepRecentManual = false;
    private static boolean keepRecentAuto = false;
    private static int recordsKeepRecent = DEFAULT_RECORDS_KEEP_RECENT;

    private static boolean logsAutoCleanEnabled = DEFAULT_LOGS_AUTO_CLEAN;
    private static double logsThresholdMB = DEFAULT_LOGS_THRESHOLD_MB;
    private static int logsKeepRecent = DEFAULT_LOGS_KEEP_RECENT;
    private static boolean logsKeepRecentManual = DEFAULT_LOGS_KEEP_RECENT_MANUAL;
    private static String multimcPath = "";

    private SpeedrunIGTCleanerPlugin() {
    }

    /* ------------------------------------------------------------------ */
    /* Jingle plugin entry point                                           */
    /* ------------------------------------------------------------------ */

    public static void initialize() {
        configPath = Jingle.FOLDER.resolve(CONFIG_FILE_NAME);
        loadConfig();

        mainPanel = buildPanel();
        JingleGUI.addPluginTab(TAB_NAME, mainPanel, SpeedrunIGTCleanerPlugin::refreshAll);

        JingleGUI.get().registerQuickActionButton(2000, SpeedrunIGTCleanerPlugin::makeQuickActionButton);
        JingleGUI.get().registerQuickActionButton(2001, SpeedrunIGTCleanerPlugin::makeQuickActionLogsButton);
        PluginHotkeys.addHotkeyAction(HOTKEY_ACTION, SpeedrunIGTCleanerPlugin::cleanAndReport);
        PluginHotkeys.addHotkeyAction(HOTKEY_LOGS_ACTION, SpeedrunIGTCleanerPlugin::cleanAllLogsAndReport);

        if (autoCleanEnabled) {
            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
                autoCleanIfOverThreshold(true);
            }, "records-logs-bopper-autoclean");
            t.setDaemon(true);
            t.start();
        }

        if (logsAutoCleanEnabled) {
            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {
                }
                autoCleanLogs();
            }, "mc-logs-cleaner-autoclean");
            t.setDaemon(true);
            t.start();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Unified GUI (single tab)                                            */
    /* ------------------------------------------------------------------ */

    private static JPanel buildPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 10, 5, 10);

        // --- Title ---
        JLabel title = new JLabel("Records & Logs Bopper");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(title, gbc);

        // ==================== Section 1: Records Cleaner ====================
        JLabel sec1 = new JLabel("\u2014\u2014 \u901f\u901a\u8bb0\u5f55\u6e05\u7406 \u2014\u2014");
        sec1.setFont(sec1.getFont().deriveFont(Font.BOLD, 13f));
        panel.add(sec1, gbc);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(12f));
        panel.add(statusLabel, gbc);

        JCheckBox keepRecentManualCheckbox = new JCheckBox(
                "\u6e05\u7406\u65f6\u4fdd\u7559\u6700\u8fd1 " + recordsKeepRecent + " \u4e2a\u8bb0\u5f55", keepRecentManual);
        keepRecentManualCheckbox.addActionListener(e -> {
            keepRecentManual = keepRecentManualCheckbox.isSelected();
            saveConfig();
        });
        panel.add(keepRecentManualCheckbox, gbc);

        JPanel recordsBtnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton cleanButton = new JButton("\u7acb\u5373\u6e05\u7406\u8bb0\u5f55");
        cleanButton.addActionListener(e -> cleanAndReport());
        recordsBtnRow.add(cleanButton);
        JButton openFolderButton = new JButton("\u6253\u5f00 records \u6587\u4ef6\u5939");
        openFolderButton.addActionListener(e -> openRecordsFolder());
        recordsBtnRow.add(openFolderButton);
        panel.add(recordsBtnRow, gbc);

        JCheckBox autoCleanCheckbox = new JCheckBox("\u542f\u52a8 Jingle \u65f6\u81ea\u52a8\u6e05\u7406\u8bb0\u5f55\uff08\u8d85\u51fa\u9608\u503c\u5219\u6e05\u7406\uff09", autoCleanEnabled);
        autoCleanCheckbox.addActionListener(e -> {
            autoCleanEnabled = autoCleanCheckbox.isSelected();
            saveConfig();
        });
        panel.add(autoCleanCheckbox, gbc);

        JCheckBox keepRecentAutoCheckbox = new JCheckBox(
                "\u81ea\u52a8\u6e05\u7406\u65f6\u4fdd\u7559\u6700\u8fd1 " + recordsKeepRecent + " \u4e2a\u8bb0\u5f55", keepRecentAuto);
        keepRecentAutoCheckbox.addActionListener(e -> {
            keepRecentAuto = keepRecentAutoCheckbox.isSelected();
            saveConfig();
        });
        panel.add(keepRecentAutoCheckbox, gbc);

        JPanel recordsKeepRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        recordsKeepRow.add(new JLabel("\u4fdd\u7559\u8bb0\u5f55\u6570\u91cf:"));
        JTextField recordsKeepField = new JTextField(String.valueOf(recordsKeepRecent), 8);
        recordsKeepRow.add(recordsKeepField);
        JLabel recordsKeepErr = makeErrorLabel();
        recordsKeepRow.add(recordsKeepErr);
        panel.add(recordsKeepRow, gbc);

        addLiveTextSync(recordsKeepField, () -> {
            String err = validateCount(recordsKeepField.getText());
            if (err == null) {
                int v = Integer.parseInt(recordsKeepField.getText().trim());
                keepRecentManualCheckbox.setText(
                        "\u6e05\u7406\u65f6\u4fdd\u7559\u6700\u8fd1 " + v + " \u4e2a\u8bb0\u5f55");
                keepRecentAutoCheckbox.setText(
                        "\u81ea\u52a8\u6e05\u7406\u65f6\u4fdd\u7559\u6700\u8fd1 " + v + " \u4e2a\u8bb0\u5f55");
                setError(recordsKeepErr, null);
            } else {
                setError(recordsKeepErr, err);
            }
        });

        Runnable applyRecordsKeep = () -> {
            String err = validateCount(recordsKeepField.getText());
            if (err != null) {
                showFieldError(recordsKeepField, recordsKeepErr, err, String.valueOf(recordsKeepRecent));
                return;
            }
            int v = Integer.parseInt(recordsKeepField.getText().trim());
            recordsKeepRecent = v;
            recordsKeepField.setText(String.valueOf(v));
            keepRecentManualCheckbox.setText(
                    "\u6e05\u7406\u65f6\u4fdd\u7559\u6700\u8fd1 " + recordsKeepRecent + " \u4e2a\u8bb0\u5f55");
            keepRecentAutoCheckbox.setText(
                    "\u81ea\u52a8\u6e05\u7406\u65f6\u4fdd\u7559\u6700\u8fd1 " + recordsKeepRecent + " \u4e2a\u8bb0\u5f55");
            setError(recordsKeepErr, null);
            saveConfig();
        };
        recordsKeepField.addActionListener(e -> applyRecordsKeep.run());
        recordsKeepField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                applyRecordsKeep.run();
            }
        });

        JPanel thresholdRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        thresholdRow.add(new JLabel("\u8bb0\u5f55\u81ea\u52a8\u6e05\u7406\u9608\u503c (MB):"));
        JTextField thresholdField = new JTextField(String.valueOf((long) thresholdMB), 8);
        thresholdRow.add(thresholdField);
        JLabel thresholdErr = makeErrorLabel();
        thresholdRow.add(thresholdErr);
        panel.add(thresholdRow, gbc);

        addLiveTextSync(thresholdField, () -> {
            String err = validateThreshold(thresholdField.getText());
            setError(thresholdErr, err);
        });

        Runnable applyThreshold = () -> {
            String err = validateThreshold(thresholdField.getText());
            if (err != null) {
                showFieldError(thresholdField, thresholdErr, err, formatMb(thresholdMB));
                return;
            }
            double v = Double.parseDouble(thresholdField.getText().trim());
            thresholdMB = v;
            thresholdField.setText(formatMb(v));
            setError(thresholdErr, null);
            saveConfig();
            updateStatusLabel();
        };
        thresholdField.addActionListener(e -> applyThreshold.run());
        thresholdField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                applyThreshold.run();
            }
        });

        // --- Separator ---
        panel.add(new JSeparator(), gbc);

        // ==================== Section 2: MC Logs Cleaner ====================
        JLabel sec2 = new JLabel("\u2014\u2014 MC \u65e5\u5fd7\u6e05\u7406 \u2014\u2014");
        sec2.setFont(sec2.getFont().deriveFont(Font.BOLD, 13f));
        panel.add(sec2, gbc);

        // MultiMC path row
        JPanel pathRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        pathRow.add(new JLabel("MultiMC \u8def\u5f84:"));
        multimcPathField = new JTextField(multimcPath, 30);
        pathRow.add(multimcPathField);
        JButton browseButton = new JButton("Browse");
        browseButton.addActionListener(e -> browseMultiMC());
        pathRow.add(browseButton);
        panel.add(pathRow, gbc);

        // Auto-detect + refresh buttons
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton autoDetectButton = new JButton("Auto Detect");
        autoDetectButton.addActionListener(e -> {
            Path detected = MultiMCDetector.autoDetect();
            if (detected != null) {
                multimcPath = detected.toString();
                multimcPathField.setText(multimcPath);
                saveConfig();
                refreshLogsStatus();
                JOptionPane.showMessageDialog(JingleGUI.get(),
                        "\u5df2\u68c0\u6d4b\u5230\uff1a" + multimcPath, TAB_NAME, JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(JingleGUI.get(),
                        "\u672a\u627e\u5230 MultiMC / Prism Launcher\uff0c\u8bf7\u624b\u52a8\u9009\u62e9\u8def\u5f84\u3002",
                        TAB_NAME, JOptionPane.INFORMATION_MESSAGE);
            }
        });
        buttonRow.add(autoDetectButton);
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshLogsStatus());
        buttonRow.add(refreshButton);
        panel.add(buttonRow, gbc);

        multimcPathField.addActionListener(e -> {
            multimcPath = multimcPathField.getText().trim();
            saveConfig();
            refreshLogsStatus();
        });
        multimcPathField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                String newText = multimcPathField.getText().trim();
                if (!newText.equals(multimcPath)) {
                    multimcPath = newText;
                    saveConfig();
                    refreshLogsStatus();
                }
            }
        });

        // Logs status label
        logsStatusLabel = new JLabel(" ");
        logsStatusLabel.setFont(logsStatusLabel.getFont().deriveFont(12f));
        panel.add(logsStatusLabel, gbc);

        panel.add(new JSeparator(), gbc);

        // --- Auto-clean logs section ---
        JCheckBox logsAutoCleanCheckbox = new JCheckBox("\u542f\u52a8 Jingle \u65f6\u81ea\u52a8\u6e05\u7406\u65e5\u5fd7\uff08\u8d85\u51fa\u9608\u503c\u5219\u6e05\u7406\uff09", logsAutoCleanEnabled);
        logsAutoCleanCheckbox.addActionListener(e -> {
            logsAutoCleanEnabled = logsAutoCleanCheckbox.isSelected();
            saveConfig();
        });
        panel.add(logsAutoCleanCheckbox, gbc);

        JPanel logsThresholdRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        logsThresholdRow.add(new JLabel("\u65e5\u5fd7\u81ea\u52a8\u6e05\u7406\u9608\u503c (MB):"));
        JTextField logsThresholdField = new JTextField(String.valueOf((long) logsThresholdMB), 8);
        logsThresholdRow.add(logsThresholdField);
        JLabel logsThresholdErr = makeErrorLabel();
        logsThresholdRow.add(logsThresholdErr);
        panel.add(logsThresholdRow, gbc);

        addLiveTextSync(logsThresholdField, () -> {
            String err = validateThreshold(logsThresholdField.getText());
            setError(logsThresholdErr, err);
        });

        JPanel keepRecentRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        keepRecentRow.add(new JLabel("\u4fdd\u7559\u6700\u8fd1\u65e5\u5fd7\u6570\u91cf:"));
        JTextField keepRecentField = new JTextField(String.valueOf(logsKeepRecent), 8);
        keepRecentRow.add(keepRecentField);
        JLabel logsKeepErr = makeErrorLabel();
        keepRecentRow.add(logsKeepErr);
        panel.add(keepRecentRow, gbc);

        addLiveTextSync(keepRecentField, () -> {
            String err = validateCount(keepRecentField.getText());
            if (err == null) {
                int v = Integer.parseInt(keepRecentField.getText().trim());
                if (logsKeepRecentManualCheckbox != null) {
                    logsKeepRecentManualCheckbox.setText(
                            "\u624b\u52a8\u6e05\u7406\u65f6\u4fdd\u7559\u6700\u8fd1 " + v + " \u4e2a\u65e5\u5fd7");
                }
                setError(logsKeepErr, null);
            } else {
                setError(logsKeepErr, err);
            }
        });

        Runnable applyLogsThreshold = () -> {
            String err = validateThreshold(logsThresholdField.getText());
            if (err != null) {
                showFieldError(logsThresholdField, logsThresholdErr, err, formatMb(logsThresholdMB));
                return;
            }
            double v = Double.parseDouble(logsThresholdField.getText().trim());
            logsThresholdMB = v;
            logsThresholdField.setText(formatMb(v));
            setError(logsThresholdErr, null);
            saveConfig();
        };
        logsThresholdField.addActionListener(e -> applyLogsThreshold.run());
        logsThresholdField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                applyLogsThreshold.run();
            }
        });

        Runnable applyKeepRecent = () -> {
            String err = validateCount(keepRecentField.getText());
            if (err != null) {
                showFieldError(keepRecentField, logsKeepErr, err, String.valueOf(logsKeepRecent));
                return;
            }
            int v = Integer.parseInt(keepRecentField.getText().trim());
            logsKeepRecent = v;
            keepRecentField.setText(String.valueOf(v));
            if (logsKeepRecentManualCheckbox != null) {
                logsKeepRecentManualCheckbox.setText(
                        "\u624b\u52a8\u6e05\u7406\u65f6\u4fdd\u7559\u6700\u8fd1 " + logsKeepRecent + " \u4e2a\u65e5\u5fd7");
            }
            setError(logsKeepErr, null);
            saveConfig();
        };
        keepRecentField.addActionListener(e -> applyKeepRecent.run());
        keepRecentField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                applyKeepRecent.run();
            }
        });

        panel.add(new JSeparator(), gbc);

        // --- Manual clean logs ---
        logsKeepRecentManualCheckbox = new JCheckBox(
                "\u624b\u52a8\u6e05\u7406\u65f6\u4fdd\u7559\u6700\u8fd1 " + logsKeepRecent + " \u4e2a\u65e5\u5fd7", logsKeepRecentManual);
        logsKeepRecentManualCheckbox.addActionListener(e -> {
            logsKeepRecentManual = logsKeepRecentManualCheckbox.isSelected();
            saveConfig();
        });
        panel.add(logsKeepRecentManualCheckbox, gbc);

        JPanel logsBtnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton cleanLogsButton = new JButton("\u7acb\u5373\u6e05\u7406\u65e7\u65e5\u5fd7");
        cleanLogsButton.addActionListener(e -> cleanAllLogsAndReport());
        logsBtnRow.add(cleanLogsButton);
        panel.add(logsBtnRow, gbc);

        // Extra bottom padding
        gbc.weighty = 1.0;
        panel.add(new JLabel(" "), gbc);

        updateStatusLabel();
        refreshLogsStatus();
        return panel;
    }

    /** Re-runs the given sync action whenever the text field's content changes (live typing). */
    private static void addLiveTextSync(JTextField field, Runnable onTextChanged) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onTextChanged.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onTextChanged.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onTextChanged.run();
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /* Input validation & inline error display                            */
    /* ------------------------------------------------------------------ */

    /**
     * Validates a "keep count" text field. Only non-negative integers (0 or positive
     * whole numbers) are accepted. Negative numbers, decimals, letters and any
     * expression-like input are rejected. Returns an error description, or null when
     * the input is valid.
     */
    private static String validateCount(String text) {
        String t = text.trim();
        if (t.isEmpty()) {
            return "\u4e0d\u80fd\u4e3a\u7a7a\uff0c\u8bf7\u8f93\u5165\u975e\u8d1f\u6574\u6570\uff080 \u6216\u6b63\u6574\u6570\uff09";
        }
        if (!t.matches("\\d+")) {
            if (t.startsWith("-")) {
                return "\u4e0d\u80fd\u4e3a\u8d1f\u6570\uff0c\u4ec5\u652f\u6301 0 \u6216\u6b63\u6574\u6570";
            }
            return "\u4ec5\u652f\u6301\u975e\u8d1f\u6574\u6570\uff080/1/2...\uff09\uff0c\u4e0d\u652f\u6301\u5c0f\u6570\u3001\u5b57\u6bcd\u6216\u8868\u8fbe\u5f0f";
        }
        try {
            Integer.parseInt(t);
            return null;
        } catch (NumberFormatException ex) {
            return "\u6570\u5b57\u592a\u5927\uff0c\u6700\u5927\u4ec5\u652f\u6301 " + Integer.MAX_VALUE;
        }
    }

    /**
     * Validates an "MB threshold" text field. Only non-negative decimal numbers
     * (e.g. 0, 50, 0.5) are accepted. Negative numbers, letters and expressions are
     * rejected. Values that would overflow double/long range are also rejected.
     * Returns an error description, or null when the input is valid.
     */
    private static String validateThreshold(String text) {
        String t = text.trim();
        if (t.isEmpty()) {
            return "\u4e0d\u80fd\u4e3a\u7a7a\uff0c\u8bf7\u8f93\u5165\u975e\u8d1f\u6570\u5b57\uff08\u5982 0\u300150\u30010.5\uff09";
        }
        if (!t.matches("\\d+(\\.\\d+)?")) {
            if (t.startsWith("-")) {
                return "\u4e0d\u80fd\u4e3a\u8d1f\u6570\uff0c\u9608\u503c\u5fc5\u987b\u5927\u4e8e\u7b49\u4e8e 0 MB";
            }
            return "\u4ec5\u652f\u6301\u975e\u8d1f\u6570\u5b57\uff08\u5982 0\u300150\u30010.5\uff09\uff0c\u4e0d\u652f\u6301\u5b57\u6bcd\u6216\u8868\u8fbe\u5f0f";
        }
        try {
            double v = Double.parseDouble(t);
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                return "\u6570\u5b57\u592a\u5927\uff0c\u8d85\u51fa\u53ef\u5904\u7406\u8303\u56f4";
            }
            // Ensure (long)(v * 1024 * 1024) used by the byte comparison does not overflow.
            if (v > Long.MAX_VALUE / (1024.0 * 1024.0)) {
                return "\u6570\u5b57\u592a\u5927\uff0c\u8d85\u51fa\u53ef\u5904\u7406\u8303\u56f4";
            }
            return null;
        } catch (NumberFormatException ex) {
            return "\u6570\u5b57\u683c\u5f0f\u4e0d\u5408\u6cd5";
        }
    }

    /** Creates a red inline error label, initially blank (hidden). */
    private static JLabel makeErrorLabel() {
        JLabel label = new JLabel(" ");
        label.setForeground(ERROR_COLOR);
        return label;
    }

    /** Shows (non-null message) or clears (null message) the inline error label. */
    private static void setError(JLabel label, String message) {
        if (label == null) {
            return;
        }
        if (message == null) {
            label.setText(" ");
            label.setToolTipText(null);
        } else {
            label.setText(message);
            label.setToolTipText(message);
        }
    }

    /**
     * Reverts the field to its last valid value, clears the inline error, shows an
     * error dialog describing the problem and returns focus to the field. Never
     * throws — used to report invalid input without crashing the plugin.
     */
    private static void showFieldError(JTextField field, JLabel errLabel, String message, String revertText) {
        field.setText(revertText);
        setError(errLabel, null);
        JOptionPane.showMessageDialog(JingleGUI.get(), message, TAB_NAME, JOptionPane.ERROR_MESSAGE);
        field.requestFocusInWindow();
    }

    /** Formats an MB value for display: whole numbers without decimals, otherwise as-is. */
    private static String formatMb(double v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static JButton makeQuickActionButton() {
        return JingleGUI.makeButton(
                QUICK_ACTION_TEXT,
                SpeedrunIGTCleanerPlugin::cleanAndReport,
                () -> JingleGUI.get().openTab(mainPanel),
                "Deletes SpeedrunIGT record files in ~/speedrunigt/records (keeps the folder and non-record files). Right-click for settings.",
                true);
    }

    private static JButton makeQuickActionLogsButton() {
        return JingleGUI.makeButton(
                QUICK_ACTION_LOGS_TEXT,
                SpeedrunIGTCleanerPlugin::cleanAllLogsAndReport,
                () -> JingleGUI.get().openTab(mainPanel),
                "Deletes old MC log files in MultiMC/Prism instances, keeping the most recent logs. Right-click for settings.",
                true);
    }

    /* ------------------------------------------------------------------ */
    /* Records cleaner actions                                             */
    /* ------------------------------------------------------------------ */

    private static void cleanAndReport() {
        int confirm = JOptionPane.showConfirmDialog(JingleGUI.get(),
                "\u5373\u5c06\u5220\u9664 SpeedrunIGT \u901f\u901a\u8bb0\u5f55\u6587\u4ef6\u3002\n\u786e\u5b9a\u8981\u7ee7\u7eed\u5417\uff1f",
                TAB_NAME, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        new Thread(() -> {
            try {
                int keep = keepRecentManual ? recordsKeepRecent : 0;
                RecordsCleaner.CleanResult result = RecordsCleaner.clean(getRecordsDir(), keep);
                SwingUtilities.invokeLater(() -> {
                    updateStatusLabel();
                    String message = "\u5df2\u5220\u9664 " + result.filesDeleted + " \u4e2a\u8bb0\u5f55\u6587\u4ef6\uff0c\u91ca\u653e "
                            + MB_FORMAT.format(result.bytesFreed / 1024.0 / 1024.0) + " MB\u3002\nrecords \u6587\u4ef6\u5939\u5df2\u4fdd\u7559\u3002";
                    if (keep > 0) {
                        message += "\n\u5df2\u4fdd\u7559\u6700\u8fd1 " + recordsKeepRecent + " \u4e2a\u8bb0\u5f55\u6587\u4ef6\u3002";
                    }
                    if (result.filesSkipped > 0) {
                        message += "\n\u5df2\u8df3\u8fc7 " + result.filesSkipped + " \u4e2a\u975e\u6a21\u7ec4\u6587\u4ef6\uff08\u672a\u5220\u9664\uff09\u3002";
                    }
                    if (result.failures > 0) {
                        message += "\n\n\u6ce8\u610f\uff1a\u6709 " + result.failures + " \u4e2a\u6587\u4ef6\u6b63\u88ab\u5360\u7528\uff0c\u672a\u80fd\u5220\u9664\uff08\u91cd\u542f\u540e\u91cd\u8bd5\u5373\u53ef\uff09\u3002";
                        JOptionPane.showMessageDialog(JingleGUI.get(), message, TAB_NAME, JOptionPane.WARNING_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(JingleGUI.get(), message, TAB_NAME, JOptionPane.INFORMATION_MESSAGE);
                    }
                });
            } catch (Exception e) {
                Jingle.logError(TAB_NAME + ": Failed to clean records!", e);
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(JingleGUI.get(),
                        "\u6e05\u7406\u5931\u8d25\uff1a" + e.getMessage(), TAB_NAME, JOptionPane.ERROR_MESSAGE));
            }
        }, "records-cleaner").start();
    }

    private static void autoCleanIfOverThreshold(boolean logWhenSkipped) {
        try {
            Path dir = getRecordsDir();
            long size = RecordsCleaner.getFolderSize(dir);
            long thresholdBytes = (long) (thresholdMB * 1024 * 1024);
            if (size > thresholdBytes) {
                int keep = keepRecentAuto ? recordsKeepRecent : 0;
                RecordsCleaner.CleanResult result = RecordsCleaner.clean(dir, keep);
                String logMsg = TAB_NAME + ": Auto-cleaned records (" + result.filesDeleted
                        + " files, " + MB_FORMAT.format(result.bytesFreed / 1024.0 / 1024.0) + " MB freed)";
                if (keep > 0) {
                    logMsg += ", kept " + recordsKeepRecent + " most recent records";
                }
                if (result.filesSkipped > 0) {
                    logMsg += ", skipped " + result.filesSkipped + " non-record files";
                }
                Jingle.log(Level.INFO, logMsg + ".");
                SwingUtilities.invokeLater(SpeedrunIGTCleanerPlugin::updateStatusLabel);
            } else if (logWhenSkipped) {
                Jingle.log(Level.INFO, TAB_NAME + ": Auto-clean skipped, records size ("
                        + MB_FORMAT.format(size / 1024.0 / 1024.0) + " MB) is within threshold ("
                        + (long) thresholdMB + " MB).");
            }
        } catch (Exception e) {
            Jingle.logError(TAB_NAME + ": Auto-clean failed!", e);
        }
    }

    private static void openRecordsFolder() {
        try {
            Path dir = getRecordsDir();
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Desktop.getDesktop().open(dir.toFile());
        } catch (Exception e) {
            Jingle.logError(TAB_NAME + ": Failed to open records folder!", e);
        }
    }

    private static void updateStatusLabel() {
        try {
            Path dir = getRecordsDir();
            long size = RecordsCleaner.getFolderSize(dir);
            int count = RecordsCleaner.countFiles(dir);
            String text = "<html>\u5f53\u524d\u8bb0\u5f55\u5927\u5c0f\uff1a<b>" + MB_FORMAT.format(size / 1024.0 / 1024.0) + " MB</b>\uff08"
                    + count + " \u4e2a\u6587\u4ef6\uff09&nbsp;&nbsp;|&nbsp;&nbsp;\u81ea\u52a8\u6e05\u7406\u9608\u503c\uff1a" + (long) thresholdMB + " MB"
                    + (autoCleanEnabled ? "" : "\uff08\u81ea\u52a8\u6e05\u7406\u5df2\u5173\u95ed\uff09") + "</html>";
            if (statusLabel != null) {
                statusLabel.setText(text);
            }
        } catch (Exception e) {
            Jingle.logError(TAB_NAME + ": Failed to read folder status!", e);
        }
    }

    /* ------------------------------------------------------------------ */
    /* MC Logs cleaner actions                                             */
    /* ------------------------------------------------------------------ */

    private static void browseMultiMC() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setDialogTitle("\u9009\u62e9 MultiMC / Prism Launcher \u5b89\u88c5\u76ee\u5f55\u6216 exe");
        if (multimcPath != null && !multimcPath.isEmpty()) {
            File currentDir = new File(multimcPath);
            if (currentDir.isDirectory()) {
                chooser.setCurrentDirectory(currentDir);
            } else if (currentDir.getParentFile() != null) {
                chooser.setCurrentDirectory(currentDir.getParentFile());
            }
        }
        int result = chooser.showOpenDialog(JingleGUI.get());
        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            Path path = selected.toPath();
            if (Files.isRegularFile(path)) {
                path = path.getParent();
            }
            multimcPath = path.toString();
            if (multimcPathField != null) {
                multimcPathField.setText(multimcPath);
            }
            saveConfig();
            refreshLogsStatus();
        }
    }

    private static void refreshLogsStatus() {
        if (logsStatusLabel == null) {
            return;
        }
        try {
            if (multimcPath == null || multimcPath.isEmpty()) {
                logsStatusLabel.setText("<html>MultiMC \u8def\u5f84\u672a\u8bbe\u7f6e\u3002\u8bf7\u70b9\u51fb \"Auto Detect\" \u81ea\u52a8\u8bc6\u522b\uff0c\u6216\u70b9\u51fb \"Browse\" \u624b\u52a8\u9009\u62e9\u3002</html>");
                return;
            }
            Path multimcDir = Paths.get(multimcPath);
            if (!MultiMCDetector.isValidMultiMCDir(multimcDir)) {
                logsStatusLabel.setText("<html>\u8def\u5f84\u65e0\u6548\uff08\u672a\u627e\u5230 instances \u6587\u4ef6\u5939\uff09\uff1a" + multimcPath + "</html>");
                return;
            }
            List<MultiMCDetector.InstanceInfo> instances = MultiMCDetector.getInstances(multimcDir);
            if (instances.isEmpty()) {
                logsStatusLabel.setText("<html>MultiMC: " + multimcPath + "<br>\u672a\u627e\u5230\u542b logs \u6587\u4ef6\u5939\u7684\u5b9e\u4f8b\u3002</html>");
                return;
            }
            long totalSize = 0;
            int totalCount = 0;
            for (MultiMCDetector.InstanceInfo inst : instances) {
                totalSize += inst.logSize;
                totalCount += inst.logCount;
            }
            StringBuilder sb = new StringBuilder("<html>MultiMC: " + multimcPath + "<br>");
            sb.append("\u68c0\u6d4b\u5230 <b>").append(instances.size()).append("</b> \u4e2a\u5b9e\u4f8b | \u603b\u65e5\u5fd7: <b>")
              .append(MB_FORMAT.format(totalSize / 1024.0 / 1024.0)).append(" MB</b> (").append(totalCount).append(" \u4e2a\u6587\u4ef6)")
              .append("&nbsp;&nbsp;|&nbsp;&nbsp;\u81ea\u52a8\u6e05\u7406\u9608\u503c: ").append((long) logsThresholdMB).append(" MB")
              .append(logsAutoCleanEnabled ? "" : "\uff08\u81ea\u52a8\u6e05\u7406\u5df2\u5173\u95ed\uff09");
            for (MultiMCDetector.InstanceInfo inst : instances) {
                sb.append("<br>&nbsp;&nbsp;- ").append(inst.name).append(": ")
                  .append(MB_FORMAT.format(inst.logSize / 1024.0 / 1024.0)).append(" MB (")
                  .append(inst.logCount).append(" \u4e2a\u6587\u4ef6)");
            }
            sb.append("</html>");
            logsStatusLabel.setText(sb.toString());
        } catch (Exception e) {
            logsStatusLabel.setText("<html>\u8bfb\u53d6\u72b6\u6001\u5931\u8d25: " + e.getMessage() + "</html>");
        }
    }

    private static void cleanAllLogsAndReport() {
        String warnMsg = logsKeepRecentManual
                ? "\u5373\u5c06\u5220\u9664 MC \u65e7\u65e5\u5fd7\uff08\u4fdd\u7559\u6700\u8fd1 " + logsKeepRecent + " \u4e2a\u65e5\u5fd7\uff09\u3002\n\u786e\u5b9a\u8981\u7ee7\u7eed\u5417\uff1f"
                : "\u5373\u5c06\u5220\u9664\u6240\u6709 MC \u65e7\u65e5\u5fd7\uff08\u4ec5\u4fdd\u7559 latest.log\uff09\u3002\n\u786e\u5b9a\u8981\u7ee7\u7eed\u5417\uff1f";
        int confirm = JOptionPane.showConfirmDialog(JingleGUI.get(),
                warnMsg, TAB_NAME, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        new Thread(() -> {
            try {
                if (multimcPath == null || multimcPath.isEmpty()) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(JingleGUI.get(),
                            "\u8bf7\u5148\u8bbe\u7f6e MultiMC \u8def\u5f84\u3002", TAB_NAME, JOptionPane.WARNING_MESSAGE));
                    return;
                }
                Path multimcDir = Paths.get(multimcPath);
                List<MultiMCDetector.InstanceInfo> instances = MultiMCDetector.getInstances(multimcDir);
                if (instances.isEmpty()) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(JingleGUI.get(),
                            "\u672a\u627e\u5230\u542b logs \u6587\u4ef6\u5939\u7684\u5b9e\u4f8b\u3002", TAB_NAME, JOptionPane.INFORMATION_MESSAGE));
                    return;
                }
                final int keep = logsKeepRecentManual ? logsKeepRecent : 0;
                int totalDeleted = 0;
                long totalFreed = 0;
                int totalFailures = 0;
                int totalSkipped = 0;
                for (MultiMCDetector.InstanceInfo inst : instances) {
                    LogsCleaner.CleanResult r = LogsCleaner.cleanLogs(inst.logsDir, keep);
                    totalDeleted += r.filesDeleted;
                    totalFreed += r.bytesFreed;
                    totalFailures += r.failures;
                    totalSkipped += r.filesSkipped;
                }
                final int deleted = totalDeleted;
                final long freed = totalFreed;
                final int failures = totalFailures;
                final int skipped = totalSkipped;
                final int instanceCount = instances.size();
                SwingUtilities.invokeLater(() -> {
                    refreshLogsStatus();
                    String message = "\u5df2\u6e05\u7406 " + instanceCount + " \u4e2a\u5b9e\u4f8b\uff1a\n"
                            + "\u5220\u9664 " + deleted + " \u4e2a\u65e5\u5fd7\u6587\u4ef6\uff0c\u91ca\u653e "
                            + MB_FORMAT.format(freed / 1024.0 / 1024.0) + " MB\u3002\n"
                            + (keep > 0
                                    ? "\u5df2\u4fdd\u7559\u6bcf\u4e2a\u5b9e\u4f8b\u6700\u8fd1 " + keep + " \u4e2a\u65e5\u5fd7\u3002"
                                    : "latest.log \u7b49\u5f53\u524d\u65e5\u5fd7\u5df2\u4fdd\u7559\u3002");
                    if (skipped > 0) {
                        message += "\n\u5df2\u8df3\u8fc7 " + skipped + " \u4e2a\u975e\u65e5\u5fd7\u6587\u4ef6\u3002";
                    }
                    if (failures > 0) {
                        message += "\n\n\u6ce8\u610f\uff1a\u6709 " + failures + " \u4e2a\u6587\u4ef6\u6b63\u88ab\u5360\u7528\uff0c\u672a\u80fd\u5220\u9664\uff08\u5173\u95ed\u6e38\u620f\u540e\u91cd\u8bd5\u5373\u53ef\uff09\u3002";
                        JOptionPane.showMessageDialog(JingleGUI.get(), message, TAB_NAME, JOptionPane.WARNING_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(JingleGUI.get(), message, TAB_NAME, JOptionPane.INFORMATION_MESSAGE);
                    }
                });
            } catch (Exception e) {
                Jingle.logError(TAB_NAME + ": Failed to clean logs!", e);
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(JingleGUI.get(),
                        "\u6e05\u7406\u5931\u8d25\uff1a" + e.getMessage(), TAB_NAME, JOptionPane.ERROR_MESSAGE));
            }
        }, "mc-logs-cleaner").start();
    }

    private static void autoCleanLogs() {
        try {
            if (!logsAutoCleanEnabled) {
                return;
            }
            if (multimcPath == null || multimcPath.isEmpty()) {
                Jingle.log(Level.INFO, TAB_NAME + ": Auto-clean skipped, MultiMC path not set.");
                return;
            }
            Path multimcDir = Paths.get(multimcPath);
            if (!MultiMCDetector.isValidMultiMCDir(multimcDir)) {
                Jingle.log(Level.WARN, TAB_NAME + ": Auto-clean skipped, invalid MultiMC path: " + multimcPath);
                return;
            }
            List<MultiMCDetector.InstanceInfo> instances = MultiMCDetector.getInstances(multimcDir);
            if (instances.isEmpty()) {
                Jingle.log(Level.INFO, TAB_NAME + ": Auto-clean skipped, no instances with logs found.");
                return;
            }
            long totalSize = 0;
            for (MultiMCDetector.InstanceInfo inst : instances) {
                totalSize += inst.logSize;
            }
            long thresholdBytes = (long) (logsThresholdMB * 1024 * 1024);
            if (totalSize <= thresholdBytes) {
                Jingle.log(Level.INFO, TAB_NAME + ": Auto-clean skipped, total log size ("
                        + MB_FORMAT.format(totalSize / 1024.0 / 1024.0) + " MB) is within threshold ("
                        + (long) logsThresholdMB + " MB).");
                return;
            }
            int totalDeleted = 0;
            long totalFreed = 0;
            int totalFailures = 0;
            for (MultiMCDetector.InstanceInfo inst : instances) {
                LogsCleaner.CleanResult r = LogsCleaner.cleanLogs(inst.logsDir, logsKeepRecent);
                totalDeleted += r.filesDeleted;
                totalFreed += r.bytesFreed;
                totalFailures += r.failures;
            }
            String logMsg = TAB_NAME + ": Auto-cleaned logs across " + instances.size() + " instances ("
                    + totalDeleted + " files, " + MB_FORMAT.format(totalFreed / 1024.0 / 1024.0) + " MB freed"
                    + ", kept " + logsKeepRecent + " most recent per instance";
            if (totalFailures > 0) {
                logMsg += ", " + totalFailures + " failures";
            }
            logMsg += ").";
            Jingle.log(Level.INFO, logMsg);
            SwingUtilities.invokeLater(SpeedrunIGTCleanerPlugin::refreshLogsStatus);
        } catch (Exception e) {
            Jingle.logError(TAB_NAME + ": Auto-clean failed!", e);
        }
    }

    private static void refreshAll() {
        updateStatusLabel();
        refreshLogsStatus();
    }

    /* ------------------------------------------------------------------ */
    /* Paths & config                                                      */
    /* ------------------------------------------------------------------ */

    private static Path getRecordsDir() {
        return Paths.get(System.getProperty("user.home"), "speedrunigt", "records");
    }

    private static void loadConfig() {
        autoCleanEnabled = DEFAULT_AUTO_CLEAN;
        thresholdMB = DEFAULT_THRESHOLD_MB;
        keepRecentManual = false;
        keepRecentAuto = false;
        recordsKeepRecent = DEFAULT_RECORDS_KEEP_RECENT;
        logsAutoCleanEnabled = DEFAULT_LOGS_AUTO_CLEAN;
        logsThresholdMB = DEFAULT_LOGS_THRESHOLD_MB;
        logsKeepRecent = DEFAULT_LOGS_KEEP_RECENT;
        logsKeepRecentManual = DEFAULT_LOGS_KEEP_RECENT_MANUAL;
        multimcPath = "";
        if (configPath == null || !Files.exists(configPath)) {
            return;
        }
        try (InputStream in = Files.newInputStream(configPath)) {
            Properties props = new Properties();
            props.load(in);
            autoCleanEnabled = Boolean.parseBoolean(props.getProperty(PROP_AUTO_CLEAN, String.valueOf(DEFAULT_AUTO_CLEAN)));
            thresholdMB = Double.parseDouble(props.getProperty(PROP_THRESHOLD_MB, String.valueOf(DEFAULT_THRESHOLD_MB)));
            if (thresholdMB < MIN_THRESHOLD_MB) {
                thresholdMB = DEFAULT_THRESHOLD_MB;
            }
            keepRecentManual = Boolean.parseBoolean(props.getProperty(PROP_KEEP_RECENT_MANUAL, "false"));
            keepRecentAuto = Boolean.parseBoolean(props.getProperty(PROP_KEEP_RECENT_AUTO, "false"));
            recordsKeepRecent = Integer.parseInt(props.getProperty(
                    PROP_RECORDS_KEEP_RECENT, String.valueOf(DEFAULT_RECORDS_KEEP_RECENT)));
            if (recordsKeepRecent < 0) {
                recordsKeepRecent = DEFAULT_RECORDS_KEEP_RECENT;
            }
            logsAutoCleanEnabled = Boolean.parseBoolean(props.getProperty(PROP_LOGS_AUTO_CLEAN, String.valueOf(DEFAULT_LOGS_AUTO_CLEAN)));
            logsThresholdMB = Double.parseDouble(props.getProperty(PROP_LOGS_THRESHOLD_MB, String.valueOf(DEFAULT_LOGS_THRESHOLD_MB)));
            if (logsThresholdMB < MIN_THRESHOLD_MB) {
                logsThresholdMB = DEFAULT_LOGS_THRESHOLD_MB;
            }
            logsKeepRecent = Integer.parseInt(props.getProperty(PROP_LOGS_KEEP_RECENT, String.valueOf(DEFAULT_LOGS_KEEP_RECENT)));
            if (logsKeepRecent < 0) {
                logsKeepRecent = DEFAULT_LOGS_KEEP_RECENT;
            }
            logsKeepRecentManual = Boolean.parseBoolean(props.getProperty(
                    PROP_LOGS_KEEP_RECENT_MANUAL, String.valueOf(DEFAULT_LOGS_KEEP_RECENT_MANUAL)));
            multimcPath = props.getProperty(PROP_MULTIMC_PATH, "");
        } catch (IOException | NumberFormatException e) {
            Jingle.log(Level.WARN, TAB_NAME + ": Failed to load config, using defaults.");
        }
    }

    private static void saveConfig() {
        if (configPath == null) {
            return;
        }
        try {
            Files.createDirectories(configPath.getParent());
            Properties props = new Properties();
            props.setProperty(PROP_AUTO_CLEAN, String.valueOf(autoCleanEnabled));
            props.setProperty(PROP_THRESHOLD_MB, String.valueOf(thresholdMB));
            props.setProperty(PROP_KEEP_RECENT_MANUAL, String.valueOf(keepRecentManual));
            props.setProperty(PROP_KEEP_RECENT_AUTO, String.valueOf(keepRecentAuto));
            props.setProperty(PROP_RECORDS_KEEP_RECENT, String.valueOf(recordsKeepRecent));
            props.setProperty(PROP_LOGS_AUTO_CLEAN, String.valueOf(logsAutoCleanEnabled));
            props.setProperty(PROP_LOGS_THRESHOLD_MB, String.valueOf(logsThresholdMB));
            props.setProperty(PROP_LOGS_KEEP_RECENT, String.valueOf(logsKeepRecent));
            props.setProperty(PROP_LOGS_KEEP_RECENT_MANUAL, String.valueOf(logsKeepRecentManual));
            props.setProperty(PROP_MULTIMC_PATH, multimcPath);
            try (OutputStream out = Files.newOutputStream(configPath)) {
                props.store(out, "Records & Logs Bopper config");
            }
        } catch (IOException e) {
            Jingle.logError(TAB_NAME + ": Failed to save config!", e);
        }
    }
}
