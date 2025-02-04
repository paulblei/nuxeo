/*
 * (C) Copyright 2011-2018 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Julien Carsique
 *
 */

package org.nuxeo.launcher.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.nuxeo.common.Environment;
import org.nuxeo.log4j.Log4JHelper;

/**
 * Launcher view for graphical user interface
 *
 * @author jcarsique
 * @since 5.4.2
 * @see NuxeoLauncherGUI
 */
public class NuxeoFrame extends JFrame {

    /**
     * @since 5.5
     */
    protected class LogsPanelListener extends ComponentAdapter {
        private String logFile;

        public LogsPanelListener(String logFile) {
            this.logFile = logFile;
        }

        @Override
        public void componentHidden(ComponentEvent e) {
            controller.notifyLogsObserver(logFile, false);
        }

        @Override
        public void componentShown(ComponentEvent e) {
            controller.notifyLogsObserver(logFile, true);
        }

    }

    protected final class ImagePanel extends JPanel {
        private static final long serialVersionUID = 1L;

        private Image backgroundImage;

        public ImagePanel(Icon image, ImageIcon backgroundImage) {
            if (backgroundImage != null) {
                this.backgroundImage = backgroundImage.getImage();
            }
            setOpaque(false);
            add(new JLabel(image));
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(backgroundImage, 0, 0, this);
        }
    }

    /**
     * @since 5.5
     */
    protected Action startAction = new AbstractAction() {
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent e) {
            mainButton.setEnabled(false);
            controller.start();
        }
    };

    protected boolean stopping = false;

    /**
     * @since 5.5
     */
    protected Action stopAction = new AbstractAction() {
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent e) {
            mainButton.setEnabled(false);
            controller.stop();
        }
    };

    protected Action launchBrowserAction = new AbstractAction() {
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent event) {
            try {
                Desktop.getDesktop().browse(java.net.URI.create(controller.getLauncher().getURL()));
            } catch (Exception e) {
                setError("An error occurred while launching browser", e);
            }
        }

    };

    /**
     * Log error and display its message in {@link #errorMessageLabel}
     *
     * @since 5.5
     * @param message Message to log
     * @param e Caught exception
     */
    public void setError(String message, Exception e) {
        log.error(message, e);
        errorMessageLabel.setText(NuxeoLauncherGUI.getMessage("error.occurred") + " <<" + e.getMessage() + ">>.");
    }

    /**
     * Log error and display its message in {@link #errorMessageLabel}
     *
     * @since 5.5
     * @param e Caught exception
     */
    public void setError(Exception e) {
        log.error(e);
        errorMessageLabel.setText(NuxeoLauncherGUI.getMessage("error.occurred") + " <<" + e.getMessage() + ">>.");
    }

    private static final long serialVersionUID = 1L;

    private static final Logger log = LogManager.getLogger(NuxeoFrame.class);

    protected static final int LOG_MAX_SIZE = 200000;

    protected final ImageIcon startIcon = getImageIcon("icons/start.png");

    protected final ImageIcon stopIcon = getImageIcon("icons/stop.png");

    protected final ImageIcon appIcon = getImageIcon("icons/control_panel_icon_32.png");

    protected JButton mainButton = null;

    protected NuxeoLauncherGUI controller;

    protected boolean logsShown = false;

    protected JButton logsButton;

    protected GridBagConstraints constraints;

    protected NuxeoFrame contentPane;

    protected Component filler;

    protected JTabbedPane tabbedPanel;

    protected JLabel summaryStatus;

    protected JLabel summaryURL;

    protected JButton launchBrowserButton;

    protected JLabel errorMessageLabel;

    private JTabbedPane logsTab;

    /**
     * @return JLabel for error display
     * @since 5.5
     */
    public JLabel getErrorMessageLabel() {
        return errorMessageLabel;
    }

    public NuxeoFrame(NuxeoLauncherGUI controller) throws HeadlessException {
        super("NuxeoCtl");
        setController(controller);
        UIManager.getDefaults().put("Button.disabledText", Color.BLACK);

        // Main frame
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setIconImage(appIcon.getImage());
        getContentPane().setBackground(Color.BLACK);
        getContentPane().setLayout(new GridBagLayout());
        constraints = new GridBagConstraints();

        // Header (with main button inside)
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.anchor = GridBagConstraints.PAGE_START;
        JComponent header = buildHeader();
        header.setPreferredSize(new Dimension(480, 170));
        getContentPane().add(header, constraints);

        // Tabs
        constraints.fill = GridBagConstraints.BOTH;
        constraints.ipady = 100;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        getContentPane().add(buildTabbedPanel(), constraints);

        // Footer
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.PAGE_END;
        constraints.ipady = 0;
        constraints.weightx = 0;
        constraints.weighty = 0;
        constraints.insets = new Insets(10, 0, 0, 0);
        getContentPane().add(buildFooter(), constraints);
    }

    protected JComponent buildFooter() {
        String year = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
        JLabel label = new JLabel(NuxeoLauncherGUI.getMessage("footer.label", year));
        label.setForeground(Color.WHITE);
        label.setPreferredSize(new Dimension(470, 16));
        label.setFont(new Font(label.getFont().getName(), label.getFont().getStyle(), 9));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }

    protected JComponent buildHeader() {
        ImagePanel headerLogo = new ImagePanel(getImageIcon("img/nuxeo_control_panel_logo.png"), null);
        headerLogo.setLayout(new GridBagLayout());
        // Main button (start/stop) (added to header)

        GridBagConstraints headerConstraints = new GridBagConstraints();
        headerConstraints.gridx = 0;
        headerLogo.add(buildMainButton(), headerConstraints);
        headerLogo.add(buildLaunchBrowserButton(), headerConstraints);
        return headerLogo;
    }

    protected JComponent buildLaunchBrowserButton() {
        launchBrowserButton = createButton(null);
        launchBrowserButton.setAction(launchBrowserAction);
        launchBrowserButton.setText(NuxeoLauncherGUI.getMessage("browser.button.text"));
        updateLaunchBrowserButton();
        return launchBrowserButton;
    }

    protected JTabbedPane buildLogsTab() {
        JTabbedPane logsTabbedPane = new JTabbedPane(SwingConstants.TOP);
        // Get Launcher log file(s)
        List<String> logFiles = Log4JHelper.getFileAppendersFileNames(
                LoggerContext.getContext(false).getConfiguration());
        // Add nuxeoctl log file
        var configHolder = controller.getConfigurationHolder();
        Path nuxeoctlLog = configHolder.getLogPath().resolve("nuxeoctl.log");
        if (Files.exists(nuxeoctlLog)) {
            logFiles.add(nuxeoctlLog.toString());
        }
        // Get server log file(s)
        logFiles.addAll(Log4JHelper.getFileAppendersFileNames(configHolder.getLogConfigPath().toFile()));
        for (String logFile : logFiles) {
            addFileToLogsTab(logsTabbedPane, logFile);
        }
        return logsTabbedPane;
    }

    protected void addFileToLogsTab(JTabbedPane logsTabbedPane, String logFile) {
        if (!hideLogTab(logFile) && !controller.getLogsMap().containsKey(logFile)) {
            logsTabbedPane.addTab(new File(logFile).getName(), buildLogPanel(logFile));
        }
    }

    /**
     * Called by buildLogsTab to know if a log file should be display. Can be overridden. Return false by default.
     *
     * @return false
     */
    protected boolean hideLogTab(String logFile) {
        return false;
    }

    protected JComponent buildLogPanel(String logFile) {
        ColoredTextPane textArea = new ColoredTextPane();
        textArea.setEditable(false);
        textArea.setAutoscrolls(true);
        textArea.setBackground(Color.BLACK);
        textArea.setMaxSize(LOG_MAX_SIZE);

        JScrollPane logsScroller = new JScrollPane(textArea);
        logsScroller.setVisible(true);
        logsScroller.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        logsScroller.setAutoscrolls(true);
        logsScroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        logsScroller.setWheelScrollingEnabled(true);
        logsScroller.setPreferredSize(new Dimension(450, 160));

        controller.initLogsManagement(logFile, textArea);
        logsScroller.addComponentListener(new LogsPanelListener(logFile));
        return logsScroller;
    }

    protected JComponent buildMainButton() {
        mainButton = createButton(null);
        updateMainButton();
        return mainButton;
    }

    protected Component buildSummaryPanel() {
        JPanel summaryPanel = new JPanel();
        summaryPanel.setLayout(new BoxLayout(summaryPanel, BoxLayout.PAGE_AXIS));
        summaryPanel.setBackground(Color.BLACK);
        summaryPanel.setForeground(Color.WHITE);

        summaryPanel.add(
                new JLabel("<html><font color=#ffffdd>" + NuxeoLauncherGUI.getMessage("summary.status.label")));
        summaryStatus = new JLabel(controller.launcher.status());
        summaryStatus.setForeground(Color.WHITE);
        summaryPanel.add(summaryStatus);

        summaryPanel.add(new JLabel("<html><font color=#ffffdd>" + NuxeoLauncherGUI.getMessage("summary.url.label")));
        summaryURL = new JLabel(controller.launcher.getURL());
        summaryURL.setForeground(Color.WHITE);
        summaryPanel.add(summaryURL);

        errorMessageLabel = new JLabel();
        errorMessageLabel.setForeground(Color.RED);
        summaryPanel.add(errorMessageLabel);

        summaryPanel.add(new JSeparator());
        var configHolder = controller.launcher.getConfigurationGenerator().getConfigurationHolder();
        summaryPanel.add(
                new JLabel("<html><font color=#ffffdd>" + NuxeoLauncherGUI.getMessage("summary.homedir.label")));
        summaryPanel.add(new JLabel("<html><font color=white>" + configHolder.getHomePath()));
        summaryPanel.add(
                new JLabel("<html><font color=#ffffdd>" + NuxeoLauncherGUI.getMessage("summary.nuxeoconf.label")));
        summaryPanel.add(new JLabel("<html><font color=white>" + configHolder.getNuxeoConfPath()));
        summaryPanel.add(
                new JLabel("<html><font color=#ffffdd>" + NuxeoLauncherGUI.getMessage("summary.datadir.label")));
        summaryPanel.add(new JLabel("<html><font color=white>" + configHolder.getDataPath()));
        return summaryPanel;
    }

    protected JComponent buildTabbedPanel() {
        tabbedPanel = new JTabbedPane(SwingConstants.TOP);
        tabbedPanel.addTab(NuxeoLauncherGUI.getMessage("tab.summary.title"), buildSummaryPanel());
        logsTab = buildLogsTab();
        tabbedPanel.addTab(NuxeoLauncherGUI.getMessage("tab.logs.title"), logsTab);
        return tabbedPanel;
    }

    protected JButton createButton(ImageIcon icon) {
        JButton button = new JButton();
        button.setIcon(icon);
        return button;
    }

    public void debug(JComponent parent) {
        for (Component comp : parent.getComponents()) {
            if (comp instanceof JComponent) {
                ((JComponent) comp).setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color.red), ((JComponent) comp).getBorder()));
                log.info("{} size: {}", comp::getClass, comp::getSize);
            }
        }
    }

    protected ImageIcon getImageIcon(String resourcePath) {
        BufferedImage image;
        try {
            ImageIO.setCacheDirectory(Environment.getDefault().getTemp());
            image = ImageIO.read(getClass().getClassLoader().getResource(resourcePath));
        } catch (IOException e) {
            log.error(e);
            throw new RuntimeException(e);
        }
        return new ImageIcon(image);
    }

    protected void updateMainButton() {
        if (controller.launcher.isStarted()) {
            mainButton.setAction(stopAction);
            mainButton.setText(NuxeoLauncherGUI.getMessage("mainbutton.stop.text"));
            mainButton.setToolTipText(NuxeoLauncherGUI.getMessage("mainbutton.stop.tooltip"));
            mainButton.setIcon(stopIcon);
        } else if (controller.launcher.isRunning()) {
            if (stopping) {
                mainButton.setAction(stopAction);
                mainButton.setText(NuxeoLauncherGUI.getMessage("mainbutton.stop.inprogress"));
            } else {
                mainButton.setAction(stopAction);
                mainButton.setText(NuxeoLauncherGUI.getMessage("mainbutton.start.inprogress"));
            }
            mainButton.setToolTipText(NuxeoLauncherGUI.getMessage("mainbutton.stop.tooltip"));
            mainButton.setIcon(stopIcon);
        } else {
            mainButton.setAction(startAction);
            mainButton.setText(NuxeoLauncherGUI.getMessage("mainbutton.start.text"));
            mainButton.setToolTipText(NuxeoLauncherGUI.getMessage("mainbutton.start.tooltip"));
            mainButton.setIcon(startIcon);
        }
        mainButton.setEnabled(true);
        mainButton.validate();
    }

    /**
     * @since 5.5
     */
    protected void updateLaunchBrowserButton() {
        launchBrowserButton.setEnabled(controller.launcher.isStarted());
    }

    /**
     * Update information displayed in summary tab
     */
    public void updateSummary() {
        String errorMessageLabelStr = "";
        Color summaryStatusFgColor = Color.WHITE;
        if (controller.launcher.isStarted()) {
            String startupSummary = controller.launcher.getStartupSummary();
            if (!controller.launcher.wasStartupFine()) {
                String[] lines = startupSummary.split("\n");
                // extract line with summary informations
                for (String line : lines) {
                    if (line.contains("Component Loading Status")) {
                        startupSummary = line;
                        break;
                    }
                }
                errorMessageLabelStr = "An error was detected during startup " + startupSummary + ".";
                summaryStatusFgColor = Color.RED;
            }
        }
        errorMessageLabel.setText(errorMessageLabelStr);
        summaryStatus.setForeground(summaryStatusFgColor);
        summaryStatus.setText(controller.launcher.status());
        summaryURL.setText(controller.launcher.getURL());
    }

    /**
     * Add Windows rotated console log
     *
     * @since 5.6
     */
    public void updateLogsTab(String consoleLogId) {
        if (consoleLogId != null) {
            addFileToLogsTab(logsTab,
                    controller.getConfigurationGenerator()
                              .getConfigurationHolder()
                              .getLogPath()
                              .resolve("console" + consoleLogId + ".log")
                              .toString());
        }
    }

    /**
     * @since 5.5
     * @return GUI controller
     */
    public NuxeoLauncherGUI getController() {
        return controller;
    }

    public void setController(NuxeoLauncherGUI controller) {
        this.controller = controller;
    }

    /**
     * @since 5.6
     */
    public void close() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
    }

}
