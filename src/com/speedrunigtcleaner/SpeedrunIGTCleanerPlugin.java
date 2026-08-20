package com.speedrunigtcleaner;

import org.apache.logging.log4j.Level;
import xyz.duncanruns.jingle.Jingle;
import xyz.duncanruns.jingle.gui.JingleGUI;
import xyz.duncanruns.jingle.plugin.PluginHotkeys;

import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.Properties;

/**
 * Jingle plugin that cleans up the SpeedrunIGT "records" cache folder
 * (located at %USERPROFILE%\speedrunigt\records).
 *
 * Features:
 *  - A "Clean Records" quick-action button on Jingle's main tab (one click).
 *  - A dedicated plugin tab with a clean button, folder opener and settings.
 *  - Optional auto-clean when Jingle starts, if the folder exceeds a size threshold.
 *  - A hotkey action that can be bound in Jingle's Hotkeys tab.
 */
public final class SpeedrunIGTCleanerPlugin {

    private static final String TAB_NAME = "Records Cleaner";
    private static final String QUICK_ACTION_TEXT = "Clean Records";
    private static final String HOTKEY_ACTION = "Clean SpeedrunIGT Records";

    private static final String CONFIG_FILE_NAME = "speedrunigt-records-cleaner.properties";
    private static final String PROP_AUTO_CLEAN = "autoCleanOnStartup";
    private static final String PROP_THRESHOLD_MB = "thresholdMB";

    private static final boolean DEFAULT_AUTO_CLEAN = true;
    private static final double DEFAULT_THRESHOLD_MB = 500.0;
    private static final double MIN_THRESHOLD_MB = 1.0;

    private static final DecimalFormat MB_FORMAT = new DecimalFormat("0.0");

    private static Path configPath;
    private static JPanel mainPanel;
    private static JLabel statusLabel;

    private static boolean autoCleanEnabled = DEFAULT_AUTO_CLEAN;
    private static double thresholdMB = DEFAULT_THRESHOLD_MB;

    private SpeedrunIGTCleanerPlugin() {
    }

    /* ------------------------------------------------------------------ */
    /* Jingle plugin entry point                                           */
    /* ------------------------------------------------------------------ */

    /** Called by Jingle when the plugin is loaded (runs when Jingle starts). */
    public static void initialize() {
        configPath = Jingle.FOLDER.resolve(CONFIG_FILE_NAME);
        loadConfig();

        mainPanel = buildPanel();
        JingleGUI.addPluginTab(TAB_NAME, mainPanel, SpeedrunIGTCleanerPlugin::updateStatusLabel);
        JingleGUI.get().registerQuickActionButton(2000, SpeedrunIGTCleanerPlugin::makeQuickActionButton);
        PluginHotkeys.addHotkeyAction(HOTKEY_ACTION, SpeedrunIGTCleanerPlugin::cleanAndReport);

        // Optional auto-clean shortly after startup (does not block Jingle).
        if (autoCleanEnabled) {
            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
                autoCleanIfOverThreshold(true);
            }, "speedrunigt-cleaner-autoclean");
            t.setDaemon(true);
            t.start();
        }
    }

    /* ------------------------------------------------------------------ */
    /* GUI building                                                        */
    /* ------------------------------------------------------------------ */

    private static JPanel buildPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 10, 5, 10);

        JLabel title = new JLabel("SpeedrunIGT 记录缓存清理");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(title, gbc);

        JLabel subtitle = new JLabel("只会删除 " + getRecordsDir() + " 内的文件，文件夹本身会保留。");
        subtitle.setFont(subtitle.getFont().deriveFont(11f));
        panel.add(subtitle, gbc);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(12f));
        panel.add(statusLabel, gbc);

        JButton cleanButton = new JButton("立即清理所有记录文件");
        cleanButton.addActionListener(e -> cleanAndReport());
        panel.add(cleanButton, gbc);

        JButton openFolderButton = new JButton("打开 records 文件夹");
        openFolderButton.addActionListener(e -> openRecordsFolder());
        panel.add(openFolderButton, gbc);

        panel.add(new JSeparator(), gbc);

        JCheckBox autoCleanCheckbox = new JCheckBox("启动 Jingle 时自动清理（超出阈值则清理）", autoCleanEnabled);
        autoCleanCheckbox.addActionListener(e -> {
            autoCleanEnabled = autoCleanCheckbox.isSelected();
            saveConfig();
        });
        panel.add(autoCleanCheckbox, gbc);

        JPanel thresholdRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        thresholdRow.add(new JLabel("自动清理阈值 (MB):"));
        JTextField thresholdField = new JTextField(String.valueOf((long) thresholdMB), 8);
        thresholdRow.add(thresholdField);
        panel.add(thresholdRow, gbc);

        JLabel hint = new JLabel("缓存大小超过该阈值时，Jingle 启动后会自动删除全部记录文件（保留文件夹）。");
        hint.setFont(hint.getFont().deriveFont(11f));
        panel.add(hint, gbc);

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

        JLabel footnote = new JLabel("提示：也可在“Hotkeys”标签页把“" + HOTKEY_ACTION + "”绑定到快捷键。");
        footnote.setFont(footnote.getFont().deriveFont(11f));
        panel.add(footnote, gbc);

        // Extra bottom padding
        gbc.weighty = 1.0;
        panel.add(new JLabel(" "), gbc);

        updateStatusLabel();
        return panel;
    }

    private static JButton makeQuickActionButton() {
        return JingleGUI.makeButton(
                QUICK_ACTION_TEXT,
                SpeedrunIGTCleanerPlugin::cleanAndReport,
                () -> JingleGUI.get().openTab(mainPanel),
                "Deletes all files in ~/speedrunigt/records (keeps the folder). Right-click for settings.",
                true);
    }

    /* ------------------------------------------------------------------ */
    /* Actions                                                             */
    /* ------------------------------------------------------------------ */

    /** Manual clean, triggered from the button / quick action / hotkey. */
    private static void cleanAndReport() {
        new Thread(() -> {
            try {
                RecordsCleaner.CleanResult result = RecordsCleaner.clean(getRecordsDir());
                SwingUtilities.invokeLater(() -> {
                    updateStatusLabel();
                    String message = "已删除 " + result.filesDeleted + " 个文件，释放 "
                            + MB_FORMAT.format(result.bytesFreed / 1024.0 / 1024.0) + " MB。\nrecords 文件夹已保留。";
                    if (result.failures > 0) {
                        message += "\n\n注意：有 " + result.failures + " 个文件正被占用，未能删除（重启后重试即可）。";
                        JOptionPane.showMessageDialog(JingleGUI.get(), message, "SpeedrunIGT Cleaner", JOptionPane.WARNING_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(JingleGUI.get(), message, "SpeedrunIGT Cleaner", JOptionPane.INFORMATION_MESSAGE);
                    }
                });
            } catch (Exception e) {
                Jingle.logError("SpeedrunIGT Cleaner: Failed to clean records!", e);
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(JingleGUI.get(),
                        "清理失败：" + e.getMessage(), "SpeedrunIGT Cleaner", JOptionPane.ERROR_MESSAGE));
            }
        }, "speedrunigt-cleaner").start();
    }

    /** Auto-clean check run when Jingle starts. */
    private static void autoCleanIfOverThreshold(boolean logWhenSkipped) {
        try {
            Path dir = getRecordsDir();
            long size = RecordsCleaner.getFolderSize(dir);
            long thresholdBytes = (long) (thresholdMB * 1024 * 1024);
            if (size > thresholdBytes) {
                RecordsCleaner.CleanResult result = RecordsCleaner.clean(dir);
                Jingle.log(Level.INFO, "SpeedrunIGT Cleaner: Auto-cleaned records (" + result.filesDeleted
                        + " files, " + MB_FORMAT.format(result.bytesFreed / 1024.0 / 1024.0) + " MB freed).");
                SwingUtilities.invokeLater(SpeedrunIGTCleanerPlugin::updateStatusLabel);
            } else if (logWhenSkipped) {
                Jingle.log(Level.INFO, "SpeedrunIGT Cleaner: Auto-clean skipped, records size ("
                        + MB_FORMAT.format(size / 1024.0 / 1024.0) + " MB) is within threshold ("
                        + (long) thresholdMB + " MB).");
            }
        } catch (Exception e) {
            Jingle.logError("SpeedrunIGT Cleaner: Auto-clean failed!", e);
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
            Jingle.logError("SpeedrunIGT Cleaner: Failed to open records folder!", e);
        }
    }

    private static void updateStatusLabel() {
        try {
            Path dir = getRecordsDir();
            long size = RecordsCleaner.getFolderSize(dir);
            int count = RecordsCleaner.countFiles(dir);
            String text = "<html>当前缓存大小：<b>" + MB_FORMAT.format(size / 1024.0 / 1024.0) + " MB</b>（"
                    + count + " 个文件）&nbsp;&nbsp;|&nbsp;&nbsp;自动清理阈值：" + (long) thresholdMB + " MB"
                    + (autoCleanEnabled ? "" : "（自动清理已关闭）") + "</html>";
            if (statusLabel != null) {
                statusLabel.setText(text);
            }
        } catch (Exception e) {
            Jingle.logError("SpeedrunIGT Cleaner: Failed to read folder status!", e);
        }
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
        } catch (IOException | NumberFormatException e) {
            Jingle.log(Level.WARN, "SpeedrunIGT Cleaner: Failed to load config, using defaults.");
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
            try (OutputStream out = Files.newOutputStream(configPath)) {
                props.store(out, "SpeedrunIGT Records Cleaner config");
            }
        } catch (IOException e) {
            Jingle.logError("SpeedrunIGT Cleaner: Failed to save config!", e);
        }
    }
}
