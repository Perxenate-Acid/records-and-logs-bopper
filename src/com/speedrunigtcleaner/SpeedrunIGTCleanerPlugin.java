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
    private static final String HOTKEY_ACTION = "Clean Records";

    private static final String CONFIG_FILE_NAME = "records-and-logs-bopper.properties";
    private static final String PROP_AUTO_CLEAN = "autoCleanOnStartup";
    private static final String PROP_THRESHOLD_MB = "thresholdMB";
    private static final String PROP_KEEP_RECENT_MANUAL = "keepRecentManual";
    private static final String PROP_KEEP_RECENT_AUTO = "keepRecentAuto";
    private static final String PROP_LOGS_AUTO_CLEAN = "logsAutoCleanOnStartup";
    private static final String PROP_LOGS_THRESHOLD_MB = "logsThresholdMB";
    private static final String PROP_LOGS_KEEP_RECENT = "logsKeepRecent";
    private static final String PROP_MULTIMC_PATH = "multimcPath";

    private static final boolean DEFAULT_AUTO_CLEAN = true;
    private static final double DEFAULT_THRESHOLD_MB = 50.0;
    private static final double MIN_THRESHOLD_MB = 1.0;
    private static final int KEEP_RECENT_COUNT = 10;

    private static final boolean DEFAULT_LOGS_AUTO_CLEAN = true;
    private static final double DEFAULT_LOGS_THRESHOLD_MB = 50.0;
    private static final int DEFAULT_LOGS_KEEP_RECENT = 5;

    private static final DecimalFormat MB_FORMAT = new DecimalFormat("0.0");

    private static Path configPath;
    private static JPanel mainPanel;
    private static JLabel statusLabel;
    private static JLabel logsStatusLabel;
    private static JTextField multimcPathField;

    private static boolean autoCleanEnabled = DEFAULT_AUTO_CLEAN;
    private static double thresholdMB = DEFAULT_THRESHOLD_MB;
    private static boolean keepRecentManual = false;
    private static boolean keepRecentAuto = false;

    private static boolean logsAutoCleanEnabled = DEFAULT_LOGS_AUTO_CLEAN;
    private static double logsThresholdMB = DEFAULT_LOGS_THRESHOLD_MB;
    private static int logsKeepRecent = DEFAULT_LOGS_KEEP_RECENT;
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
        PluginHotkeys.addHotkeyAction(HOTKEY_ACTION, SpeedrunIGTCleanerPlugin::cleanAndReport);

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

        JLabel subtitle = new JLabel("\u6e05\u7406 SpeedrunIGT \u901f\u901a\u8bb0\u5f55\u548c MultiMC/Prism \u5b9e\u4f8b\u65e5\u5fd7\u3002");
        subtitle.setFont(subtitle.getFont().deriveFont(11f));
        panel.add(subtitle, gbc);

        // ==================== Section 1: Records Cleaner ====================
        JLabel sec1 = new JLabel("\u2014\u2014 \u901f\u901a\u8bb0\u5f55\u6e05\u7406 \u2014\u2014");
        sec1.setFont(sec1.getFont().deriveFont(Font.BOLD, 13f));
        panel.add(sec1, gbc);

        JLabel recordsDesc = new JLabel("\u53ea\u6e05\u7406 " + getRecordsDir() + " \u5185\u7531 SpeedrunIGT \u6a21\u7ec4\u751f\u6210\u7684\u901f\u901a\u8bb0\u5f55\u3002");
        recordsDesc.setFont(recordsDesc.getFont().deriveFont(11f));
        panel.add(recordsDesc, gbc);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(12f));
        panel.add(statusLabel, gbc);

        JCheckBox keepRecentManualCheckbox = new JCheckBox(
                "\u6e05\u7406\u65f6\u4fdd\u7559\u6700\u8fd1 " + KEEP_RECENT_COUNT + " \u4e2a\u8bb0\u5f55", keepRecentManual);
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
                "\u81ea\u52a8\u6e05\u7406\u65f6\u4fdd\u7559\u6700\u8fd1 " + KEEP_RECENT_COUNT + " \u4e2a\u8bb0\u5f55", keepRecentAuto);
        keepRecentAutoCheckbox.addActionListener(e -> {
            keepRecentAuto = keepRecentAutoCheckbox.isSelected();
            saveConfig();
        });
        panel.add(keepRecentAutoCheckbox, gbc);

        JPanel thresholdRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        thresholdRow.add(new JLabel("\u8bb0\u5f55\u81ea\u52a8\u6e05\u7406\u9608\u503c (MB):"));
        JTextField thresholdField = new JTextField(String.valueOf((long) thresholdMB), 8);
        thresholdRow.add(thresholdField);
        panel.add(thresholdRow, gbc);

        Runnable applyThreshold = () -> {
            try {
                double v = Double.parseDouble(thresholdField.getText().trim());
                if (v < MIN_THRESHOLD_MB) {
                    v = MIN_THRESHOLD_MB;
                }
                thresholdMB = v;
                thresholdField.setText(String.valueOf((long) v));
                saveConfig();
                updateStatusLabel();
            } catch (NumberFormatException ex) {
                thresholdField.setText(String.valueOf((long) thresholdMB));
            }
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

        JLabel logsDesc = new JLabel("\u6e05\u7406 MultiMC / Prism Launcher \u5b9e\u4f8b .minecraft/logs \u4e2d\u5806\u79ef\u7684\u65e5\u5fd7\u3002latest.log \u4e0d\u4f1a\u88ab\u5220\u9664\u3002");
        logsDesc.setFont(logsDesc.getFont().deriveFont(11f));
        panel.add(logsDesc, gbc);

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
        panel.add(logsThresholdRow, gbc);

        JPanel keepRecentRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        keepRecentRow.add(new JLabel("\u4fdd\u7559\u6700\u8fd1\u65e5\u5fd7\u6570\u91cf:"));
        JTextField keepRecentField = new JTextField(String.valueOf(logsKeepRecent), 8);
        keepRecentRow.add(keepRecentField);
        panel.add(keepRecentRow, gbc);

        JLabel logsHint = new JLabel("\u81ea\u52a8\u6e05\u7406\u65f6\u4fdd\u7559\u6700\u8fd1 N \u4e2a\u65e5\u5fd7\uff0c\u5220\u9664\u5176\u4f59\u65e7\u65e5\u5fd7\u3002latest.log \u59cb\u7ec8\u4fdd\u7559\u3002");
        logsHint.setFont(logsHint.getFont().deriveFont(11f));
        panel.add(logsHint, gbc);

        Runnable applyLogsThreshold = () -> {
            try {
                double v = Double.parseDouble(logsThresholdField.getText().trim());
                if (v < MIN_THRESHOLD_MB) {
                    v = MIN_THRESHOLD_MB;
                }
                logsThresholdMB = v;
                logsThresholdField.setText(String.valueOf((long) v));
                saveConfig();
            } catch (NumberFormatException ex) {
                logsThresholdField.setText(String.valueOf((long) logsThresholdMB));
            }
        };
        logsThresholdField.addActionListener(e -> applyLogsThreshold.run());
        logsThresholdField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                applyLogsThreshold.run();
            }
        });

        Runnable applyKeepRecent = () -> {
            try {
                int v = Integer.parseInt(keepRecentField.getText().trim());
                if (v < 0) {
                    v = 0;
                }
                logsKeepRecent = v;
                keepRecentField.setText(String.valueOf(v));
                saveConfig();
            } catch (NumberFormatException ex) {
                keepRecentField.setText(String.valueOf(logsKeepRecent));
            }
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
        JButton cleanLogsButton = new JButton("\u7acb\u5373\u6e05\u7406\u6240\u6709\u65e5\u5fd7");
        cleanLogsButton.addActionListener(e -> cleanAllLogsAndReport());
        panel.add(cleanLogsButton, gbc);

        JLabel manualHint = new JLabel("\u624b\u52a8\u6e05\u7406\u4f1a\u5220\u9664\u6240\u6709\u65e7\u65e5\u5fd7\uff08\u4ec5\u4fdd\u7559 latest.log\uff09\uff0c\u4e0d\u7559\u526f\u672c\u3002");
        manualHint.setFont(manualHint.getFont().deriveFont(11f));
        panel.add(manualHint, gbc);

        JLabel footnote = new JLabel("\u63d0\u793a\uff1a\u53ef\u5728 \"Hotkeys\" \u6807\u7b7e\u9875\u628a \"" + HOTKEY_ACTION + "\" \u7ed1\u5b9a\u5230\u5feb\u6377\u952e\u3002");
        footnote.setFont(footnote.getFont().deriveFont(11f));
        panel.add(footnote, gbc);

        // Extra bottom padding
        gbc.weighty = 1.0;
        panel.add(new JLabel(" "), gbc);

        updateStatusLabel();
        refreshLogsStatus();
        return panel;
    }

    private static JButton makeQuickActionButton() {
        return JingleGUI.makeButton(
                QUICK_ACTION_TEXT,
                SpeedrunIGTCleanerPlugin::cleanAndReport,
                () -> JingleGUI.get().openTab(mainPanel),
                "Deletes SpeedrunIGT record files in ~/speedrunigt/records (keeps the folder and non-record files). Right-click for settings.",
                true);
    }

    /* ------------------------------------------------------------------ */
    /* Records cleaner actions                                             */
    /* ------------------------------------------------------------------ */

    private static void cleanAndReport() {
        new Thread(() -> {
            try {
                int keep = keepRecentManual ? KEEP_RECENT_COUNT : 0;
                RecordsCleaner.CleanResult result = RecordsCleaner.clean(getRecordsDir(), keep);
                SwingUtilities.invokeLater(() -> {
                    updateStatusLabel();
                    String message = "\u5df2\u5220\u9664 " + result.filesDeleted + " \u4e2a\u8bb0\u5f55\u6587\u4ef6\uff0c\u91ca\u653e "
                            + MB_FORMAT.format(result.bytesFreed / 1024.0 / 1024.0) + " MB\u3002\nrecords \u6587\u4ef6\u5939\u5df2\u4fdd\u7559\u3002";
                    if (keep > 0) {
                        message += "\n\u5df2\u4fdd\u7559\u6700\u8fd1 " + KEEP_RECENT_COUNT + " \u4e2a\u8bb0\u5f55\u6587\u4ef6\u3002";
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
                int keep = keepRecentAuto ? KEEP_RECENT_COUNT : 0;
                RecordsCleaner.CleanResult result = RecordsCleaner.clean(dir, keep);
                String logMsg = TAB_NAME + ": Auto-cleaned records (" + result.filesDeleted
                        + " files, " + MB_FORMAT.format(result.bytesFreed / 1024.0 / 1024.0) + " MB freed)";
                if (keep > 0) {
                    logMsg += ", kept " + KEEP_RECENT_COUNT + " most recent records";
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
                int totalDeleted = 0;
                long totalFreed = 0;
                int totalFailures = 0;
                int totalSkipped = 0;
                for (MultiMCDetector.InstanceInfo inst : instances) {
                    LogsCleaner.CleanResult r = LogsCleaner.cleanAllLogs(inst.logsDir);
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
                            + "latest.log \u7b49\u5f53\u524d\u65e5\u5fd7\u5df2\u4fdd\u7559\u3002";
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
        logsAutoCleanEnabled = DEFAULT_LOGS_AUTO_CLEAN;
        logsThresholdMB = DEFAULT_LOGS_THRESHOLD_MB;
        logsKeepRecent = DEFAULT_LOGS_KEEP_RECENT;
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
            logsAutoCleanEnabled = Boolean.parseBoolean(props.getProperty(PROP_LOGS_AUTO_CLEAN, String.valueOf(DEFAULT_LOGS_AUTO_CLEAN)));
            logsThresholdMB = Double.parseDouble(props.getProperty(PROP_LOGS_THRESHOLD_MB, String.valueOf(DEFAULT_LOGS_THRESHOLD_MB)));
            if (logsThresholdMB < MIN_THRESHOLD_MB) {
                logsThresholdMB = DEFAULT_LOGS_THRESHOLD_MB;
            }
            logsKeepRecent = Integer.parseInt(props.getProperty(PROP_LOGS_KEEP_RECENT, String.valueOf(DEFAULT_LOGS_KEEP_RECENT)));
            if (logsKeepRecent < 0) {
                logsKeepRecent = DEFAULT_LOGS_KEEP_RECENT;
            }
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
            props.setProperty(PROP_LOGS_AUTO_CLEAN, String.valueOf(logsAutoCleanEnabled));
            props.setProperty(PROP_LOGS_THRESHOLD_MB, String.valueOf(logsThresholdMB));
            props.setProperty(PROP_LOGS_KEEP_RECENT, String.valueOf(logsKeepRecent));
            props.setProperty(PROP_MULTIMC_PATH, multimcPath);
            try (OutputStream out = Files.newOutputStream(configPath)) {
                props.store(out, "Records & Logs Bopper config");
            }
        } catch (IOException e) {
            Jingle.logError(TAB_NAME + ": Failed to save config!", e);
        }
    }
}
