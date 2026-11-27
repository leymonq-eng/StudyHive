package com.mycompany.studyhive;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

/**
 * Main Java Swing window for StudyHive.
 * Provides the Home hub (Join/Create + Study Profile with animal avatars and '+' custom image picker)
 * and the Session workspace (Left search/filter/board, Center live stream with centered notices and
 * status colors, Right connection status with session/joined timers and activity log).
 */
public class StudyHiveUI extends JFrame implements StudyClient.Listener {

    // [MODIFIABLE UI COLOR CONSTANTS] Adjust colors to refine the vintage coffee-cream look.
    private static final Color BG_CREAM_BROWN = new Color(0xEC, 0xE0, 0xD1);
    private static final Color CARD_LATTE = new Color(0xF5, 0xEE, 0xE4);
    private static final Color PANEL_MOCHA = new Color(0xE3, 0xD5, 0xC3);
    private static final Color BTN_BROWN = new Color(0x8C, 0x62, 0x39);
    private static final Color BTN_BROWN_HOVER = new Color(0x73, 0x4E, 0x2B);
    private static final Color OUTLINE_DARK = new Color(0x3B, 0x23, 0x14);
    private static final Color TEXT_ESPRESSO = new Color(0x2B, 0x1A, 0x10);
    private static final Color TEXT_MUTED = new Color(0x68, 0x4F, 0x3E);

    // [MODIFIABLE ENTRY HIGHLIGHT COLORS]
    // NOTE: Subtle warm honey tint (not overly bright/glaring)
    // QUESTION (open): Solid red
    // QUESTION (responded & marked right/resolved): Solid green
    private static final Color NOTE_GLOW_BG = new Color(0xF6, 0xE2, 0xB3);
    private static final Color QUESTION_RED_BG = new Color(0xD9, 0x43, 0x36);
    private static final Color QUESTION_GREEN_BG = new Color(0x3E, 0x8E, 0x55);

    private static final String CARD_HOME = "HOME";
    private static final String CARD_SESSION = "SESSION";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel rootCards = new JPanel(cardLayout);

    // Home screen components
    private JLabel homeLogoBadge;
    private JLabel bigAvatarBadge;
    private final List<JToggleButton> avatarButtons = new ArrayList<>();
    private JTextField nicknameField;
    private JTextField deviceIpField;
    private JTextField defaultPortField;
    private JTextField joinIpField;
    private JTextField joinPortField;
    private JLabel homeFooterStatus;

    // Session header components
    private JLabel sessionBannerLabel;
    private JLabel sessionLogoBadge;
    private JLabel topRightAvatarBadge;

    // Left column components (Search, Filter, Board List, Actions)
    private JTextField keywordSearchField;
    private JComboBox<String> categoryFilterCombo;
    private final DefaultListModel<StudyModels.BoardEntry> boardListModel = new DefaultListModel<>();
    private JList<StudyModels.BoardEntry> boardJList;
    private JButton respondQuestionBtn;
    private JButton markResolvedBtn;
    private JButton saveMediaFileBtn;

    // Center column components (Live Study Stream + Composer)
    private JTextPane chatStreamPane;
    private HTMLEditorKit htmlKit;
    private HTMLDocument htmlDoc;
    private JCheckBox tagNoteCheck;
    private JCheckBox tagQuestionCheck;
    private JTextField messageInputField;
    private JButton attachDocBtn;
    private JButton attachImageBtn;
    private JButton sendBtn;

    // Right column components (Connection Status + Timers, Online Peers, Activity Log)
    private JTextArea connectionStatusArea;
    private final DefaultListModel<String> onlinePeersModel = new DefaultListModel<>();
    private JList<String> onlinePeersList;
    private JTextArea activityLogArea;

    // Session & timer state
    private StudyServer embeddedServer;
    private StudyClient client;
    private String currentNick = "Jade";
    private String currentAvatar = "CAT";
    private boolean isHosting = false;
    private String activeHostIp = "127.0.0.1";
    private int activePort = StudyModels.DEFAULT_PORT;

    private long sessionElapsedSeconds = 0L;
    private long joinedElapsedSeconds = 0L;
    private final Timer sessionTimer;

    private final List<StudyModels.BoardEntry> allEntries = new ArrayList<>();
    private final Map<String, StudyModels.BoardEntry> entriesById = new HashMap<>();

    /**
     * Builds the StudyHive frame, initializes timers and views, and binds window close cleanup.
     */
    public StudyHiveUI() {
        setTitle("StudyHive");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1140, 720));
        setPreferredSize(new Dimension(1280, 790));
        getContentPane().setBackground(BG_CREAM_BROWN);

        sessionTimer = new Timer(1000, e -> {
                sessionElapsedSeconds++;
                if (!isHosting) {
                    joinedElapsedSeconds++;
                }
                updateConnectionStatusBox();
        });

        rootCards.setBackground(BG_CREAM_BROWN);
        rootCards.add(buildHomeView(), CARD_HOME);
        rootCards.add(buildSessionView(), CARD_SESSION);

        setContentPane(rootCards);
        pack();
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    cleanupSession();
                    dispose();
                    System.exit(0);
                }
        });
    }

    /**
     * Resolves a calligraphy/display font with safe fallbacks across operating systems.
     */
    private Font getApricotOrCalligraphyFont(float size) {
        // [MODIFIABLE DISPLAY FONT] Add or reorder preferred title font names here.
        String[] fontCandidates = {
            "Apricot", "Lucida Calligraphy", "Segoe Script", "Brush Script MT", "Georgia"
        };
        for (String name : fontCandidates) {
            Font f = new Font(name, name.equals("Georgia") ? (Font.BOLD | Font.ITALIC) : Font.BOLD, Math.round(size));
            if (f.getFamily().equalsIgnoreCase(name)) {
                return f.deriveFont(size);
            }
        }
        return new Font("Serif", Font.BOLD | Font.ITALIC, Math.round(size));
    }

    /**
     * Creates a dark-outlined card border with inner padding.
     */
    private Border createOutlinedCardBorder(int top, int left, int bottom, int right) {
        return new CompoundBorder(
                    new LineBorder(OUTLINE_DARK, 1, false),
            new EmptyBorder(top, left, bottom, right)
        );
    }

    /**
     * Creates a styled text field matching the vintage cream-brown and dark-outline theme.
     */
    private JTextField createVintageTextField(String text, int cols) {
        JTextField tf = new JTextField(text, cols);
        tf.setBackground(CARD_LATTE);
        tf.setForeground(TEXT_ESPRESSO);
        tf.setCaretColor(OUTLINE_DARK);
        tf.setFont(new Font("SansSerif", Font.PLAIN, 13));
        tf.setBorder(new CompoundBorder(
                        new LineBorder(OUTLINE_DARK, 1, false),
                new EmptyBorder(6, 9, 6, 9)
        ));
        return tf;
    }

    /**
     * Creates a rounded vintage brown button with hover/press states and dark outline.
     */
    private JButton createVintageButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (!isEnabled()) {
                    g2.setColor(new Color(0xB5, 0x9C, 0x84));
                } else if (getModel().isPressed()) {
                    g2.setColor(BTN_BROWN_HOVER.darker());
                } else if (getModel().isRollover()) {
                    g2.setColor(BTN_BROWN_HOVER);
                } else {
                    g2.setColor(BTN_BROWN);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(OUTLINE_DARK);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setForeground(BG_CREAM_BROWN);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(8, 14, 8, 14));
        return btn;
    }

    // =========================================================================
    // HOME VIEW
    // =========================================================================

    /**
     * Builds the Home view with the Join/Create session hub on the left and Study Profile on the right.
     */
    private JPanel buildHomeView() {
        JPanel container = new JPanel(new GridBagLayout());
        container.setBackground(BG_CREAM_BROWN);
        container.setBorder(new EmptyBorder(18, 18, 18, 18));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;

        JPanel leftHubPanel = buildHomeSessionHubPanel();
        gbc.gridx = 0;
        gbc.weightx = 0.72;
        gbc.insets = new Insets(0, 0, 0, 12);
        container.add(leftHubPanel, gbc);

        JPanel rightProfilePanel = buildHomeProfileSidebar();
        gbc.gridx = 1;
        gbc.weightx = 0.28;
        gbc.insets = new Insets(0, 12, 0, 0);
        container.add(rightProfilePanel, gbc);

        return container;
    }

    /**
     * Builds the central Join Session / Create a Session hub with StudyHive logo and title.
     */
    private JPanel buildHomeSessionHubPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(CARD_LATTE);
        panel.setBorder(createOutlinedCardBorder(24, 28, 24, 28));

        JPanel centerStack = new JPanel();
        centerStack.setLayout(new BoxLayout(centerStack, BoxLayout.Y_AXIS));
        centerStack.setBackground(CARD_LATTE);

        // [MODIFIABLE HOME LOGO SIZE] Adjust (64, 64) if you want a larger or smaller logo badge.
        homeLogoBadge = new JLabel(StudyModels.createStudyHiveLogoIcon(64, 64), SwingConstants.CENTER);
        homeLogoBadge.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel titleLabel = new JLabel("StudyHive", SwingConstants.CENTER);
        titleLabel.setFont(getApricotOrCalligraphyFont(54f));
        titleLabel.setForeground(OUTLINE_DARK);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel taglineLabel = new JLabel("A cozy place for studyholics", SwingConstants.CENTER);
        taglineLabel.setFont(new Font("Georgia", Font.ITALIC, 16));
        taglineLabel.setForeground(TEXT_MUTED);
        taglineLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel joinCard = new JPanel();
        joinCard.setLayout(new BoxLayout(joinCard, BoxLayout.Y_AXIS));
        joinCard.setBackground(PANEL_MOCHA);
        joinCard.setBorder(createOutlinedCardBorder(18, 22, 18, 22));
        joinCard.setMaximumSize(new Dimension(440, 220));
        joinCard.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel joinForm = new JPanel(new GridBagLayout());
        joinForm.setBackground(PANEL_MOCHA);
        GridBagConstraints fg = new GridBagConstraints();
        fg.insets = new Insets(5, 6, 5, 6);
        fg.fill = GridBagConstraints.HORIZONTAL;

        JLabel ipLbl = new JLabel("IP Address:");
        ipLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        ipLbl.setForeground(TEXT_ESPRESSO);
        fg.gridx = 0; fg.gridy = 0; fg.weightx = 0.32;
        joinForm.add(ipLbl, fg);

        joinIpField = createVintageTextField(StudyModels.detectLocalIp(), 16);
        fg.gridx = 1; fg.gridy = 0; fg.weightx = 0.68;
        joinForm.add(joinIpField, fg);

        JLabel portLbl = new JLabel("Server Port:");
        portLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        portLbl.setForeground(TEXT_ESPRESSO);
        fg.gridx = 0; fg.gridy = 1; fg.weightx = 0.32;
        joinForm.add(portLbl, fg);

        joinPortField = createVintageTextField(String.valueOf(StudyModels.DEFAULT_PORT), 16);
        fg.gridx = 1; fg.gridy = 1; fg.weightx = 0.68;
        joinForm.add(joinPortField, fg);

        JButton joinSessionBtn = createVintageButton("Join Session");
        joinSessionBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        joinSessionBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        joinSessionBtn.addActionListener(e -> handleJoinSession());

        joinCard.add(joinForm);
        joinCard.add(Box.createVerticalStrut(12));
        joinCard.add(joinSessionBtn);

        JPanel orRow = new JPanel(new GridBagLayout());
        orRow.setBackground(CARD_LATTE);
        orRow.setMaximumSize(new Dimension(440, 30));
        orRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        GridBagConstraints og = new GridBagConstraints();
        og.fill = GridBagConstraints.HORIZONTAL;
        og.weightx = 1.0;
        JSeparator sep1 = new JSeparator();
        sep1.setForeground(OUTLINE_DARK);
        orRow.add(sep1, og);

        og.weightx = 0;
        og.insets = new Insets(0, 12, 0, 12);
        JLabel orLabel = new JLabel("OR");
        orLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        orLabel.setForeground(TEXT_MUTED);
        orRow.add(orLabel, og);

        og.weightx = 1.0;
        og.insets = new Insets(0, 0, 0, 0);
        JSeparator sep2 = new JSeparator();
        sep2.setForeground(OUTLINE_DARK);
        orRow.add(sep2, og);
        JButton createSessionBtn = createVintageButton("Create a Session");
        createSessionBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        createSessionBtn.setMaximumSize(new Dimension(440, 44));
        createSessionBtn.setPreferredSize(new Dimension(440, 44));
        createSessionBtn.addActionListener(e -> handleCreateSession());

        centerStack.add(homeLogoBadge);
        centerStack.add(Box.createVerticalStrut(8));
        centerStack.add(titleLabel);
        centerStack.add(Box.createVerticalStrut(4));
        centerStack.add(taglineLabel);
        centerStack.add(Box.createVerticalStrut(24));
        centerStack.add(joinCard);
        centerStack.add(Box.createVerticalStrut(16));
        centerStack.add(orRow);
        centerStack.add(Box.createVerticalStrut(16));
        centerStack.add(createSessionBtn);

        panel.add(centerStack);
        return panel;
    }

    /**
     * Builds the right-side Study Profile panel with animal avatars, '+' custom image button, and IP/port fields.
     */
    private JPanel buildHomeProfileSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout(10, 10));
        sidebar.setBackground(PANEL_MOCHA);
        sidebar.setBorder(createOutlinedCardBorder(18, 18, 14, 18));

        JPanel topSection = new JPanel();
        topSection.setLayout(new BoxLayout(topSection, BoxLayout.Y_AXIS));
        topSection.setBackground(PANEL_MOCHA);

        JLabel heading = new JLabel("Your Study Profile", SwingConstants.CENTER);
        heading.setFont(new Font("Georgia", Font.BOLD, 18));
        heading.setForeground(TEXT_ESPRESSO);
        heading.setAlignmentX(Component.CENTER_ALIGNMENT);

        bigAvatarBadge = new JLabel(StudyModels.createAvatarIcon(currentAvatar, 84), SwingConstants.CENTER);
        bigAvatarBadge.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Animal avatar row + '+' button to pick a custom image
        JPanel avatarRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        avatarRow.setBackground(PANEL_MOCHA);
        avatarRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        ButtonGroup group = new ButtonGroup();
        avatarButtons.clear();
        for (String animal : StudyModels.ANIMAL_AVATARS) {
            JToggleButton tb = new JToggleButton(StudyModels.createAvatarIcon(animal, 30));
            tb.setToolTipText(animal);
            tb.setPreferredSize(new Dimension(38, 38));
            tb.setBackground(CARD_LATTE);
            tb.setFocusPainted(false);
            tb.setBorder(new LineBorder(OUTLINE_DARK, animal.equals(currentAvatar) ? 2 : 1, false));
            tb.setSelected(animal.equals(currentAvatar));
            tb.addActionListener(e -> {
                    currentAvatar = animal;
                    updateAvatarDisplays();
                    for (JToggleButton b : avatarButtons) {
                        b.setBorder(new LineBorder(OUTLINE_DARK, b.isSelected() ? 2 : 1, false));
                    }
            });
            group.add(tb);
            avatarButtons.add(tb);
            avatarRow.add(tb);
        }

        // '+' button to choose a custom image file for the user's avatar
        JButton plusImageBtn = createVintageButton("+");
        plusImageBtn.setToolTipText("Choose a custom picture for your avatar");
        plusImageBtn.setPreferredSize(new Dimension(38, 38));
        plusImageBtn.setBorder(new EmptyBorder(4, 8, 4, 8));
        plusImageBtn.addActionListener(e -> handleUploadCustomAvatar());
        avatarRow.add(plusImageBtn);

        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBackground(PANEL_MOCHA);
        formPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel nickLbl = new JLabel("Username / Nickname:");
        nickLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        nickLbl.setForeground(TEXT_ESPRESSO);
        nickLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        nicknameField = createVintageTextField(currentNick, 16);
        nicknameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        nicknameField.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton saveProfileBtn = createVintageButton("Save");
        saveProfileBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        saveProfileBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        saveProfileBtn.addActionListener(e -> saveHomeProfile());

        JLabel devIpLbl = new JLabel("Device IP Address:");
        devIpLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        devIpLbl.setForeground(TEXT_ESPRESSO);
        devIpLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        String detectedIp = StudyModels.detectLocalIp();
        deviceIpField = createVintageTextField(detectedIp, 16);
        deviceIpField.setEditable(false);
        deviceIpField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        deviceIpField.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel defPortLbl = new JLabel("Default Port:");
        defPortLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        defPortLbl.setForeground(TEXT_ESPRESSO);
        defPortLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        defaultPortField = createVintageTextField(String.valueOf(StudyModels.DEFAULT_PORT), 16);
        defaultPortField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        defaultPortField.setAlignmentX(Component.LEFT_ALIGNMENT);

        formPanel.add(nickLbl);
        formPanel.add(Box.createVerticalStrut(4));
        formPanel.add(nicknameField);
        formPanel.add(Box.createVerticalStrut(8));
        formPanel.add(saveProfileBtn);
        formPanel.add(Box.createVerticalStrut(12));
        formPanel.add(devIpLbl);
        formPanel.add(Box.createVerticalStrut(4));
        formPanel.add(deviceIpField);
        formPanel.add(Box.createVerticalStrut(8));
        formPanel.add(defPortLbl);
        formPanel.add(Box.createVerticalStrut(4));
        formPanel.add(defaultPortField);

        topSection.add(heading);
        topSection.add(Box.createVerticalStrut(10));
        topSection.add(bigAvatarBadge);
        topSection.add(Box.createVerticalStrut(10));
        topSection.add(avatarRow);
        topSection.add(Box.createVerticalStrut(12));
        topSection.add(formPanel);

        homeFooterStatus = new JLabel("Pick an animal or click '+' to select your own picture.");
        homeFooterStatus.setFont(new Font("SansSerif", Font.ITALIC, 11));
        homeFooterStatus.setForeground(TEXT_MUTED);

        sidebar.add(topSection, BorderLayout.NORTH);
        sidebar.add(homeFooterStatus, BorderLayout.SOUTH);
        return sidebar;
    }

    /**
     * Opens a file chooser for custom avatar images, center-crops to a 128x128 square,
     * and stores it as a Base64 custom avatar.
     */
    private void handleUploadCustomAvatar() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Image Files (*.png, *.jpg, *.jpeg)", "png", "jpg", "jpeg"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                BufferedImage original = ImageIO.read(file);
                if (original == null) {
                    JOptionPane.showMessageDialog(this, "Selected file is not a supported image.",
                        "Avatar Error", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                int crop = Math.min(original.getWidth(), original.getHeight());
                BufferedImage square = original.getSubimage(
                            (original.getWidth() - crop) / 2,
                    (original.getHeight() - crop) / 2,
                    crop, crop
                );
                BufferedImage resized = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = resized.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(square, 0, 0, 128, 128, null);
                g2.dispose();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(resized, "png", baos);
                currentAvatar = "CUSTOM:" + Base64.getEncoder().encodeToString(baos.toByteArray());

                for (JToggleButton b : avatarButtons) {
                    b.setSelected(false);
                    b.setBorder(new LineBorder(OUTLINE_DARK, 1, false));
                }
                updateAvatarDisplays();
                homeFooterStatus.setText("Selected custom image: " + file.getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Could not load image: " + ex.getMessage(),
                    "Avatar Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Refreshes circular avatar icons across the Home profile and Session header.
     */
    private void updateAvatarDisplays() {
        bigAvatarBadge.setIcon(StudyModels.createAvatarIcon(currentAvatar, 84));
        if (topRightAvatarBadge != null) {
            topRightAvatarBadge.setIcon(StudyModels.createAvatarIcon(currentAvatar, 32));
        }
    }

    // =========================================================================
    // SESSION WORKSPACE VIEW
    // =========================================================================

    /**
     * Builds the Session workspace with header bar (logo + title + avatar) and 3 main columns.
     */
    private JPanel buildSessionView() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(BG_CREAM_BROWN);
        root.setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel topBar = new JPanel(new BorderLayout(10, 0));
        topBar.setBackground(PANEL_MOCHA);
        topBar.setBorder(createOutlinedCardBorder(8, 12, 8, 12));

        JButton backBtn = createVintageButton("< Leave");
        backBtn.addActionListener(e -> leaveSession());

        JPanel titleCluster = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        titleCluster.setBackground(PANEL_MOCHA);

        // [MODIFIABLE SESSION LOGO SIZE] Adjust (34, 34) for header logo badge sizing.
        sessionLogoBadge = new JLabel(StudyModels.createStudyHiveLogoIcon(34, 34));
        sessionBannerLabel = new JLabel("StudyHive — Session");
        sessionBannerLabel.setFont(getApricotOrCalligraphyFont(26f));
        sessionBannerLabel.setForeground(OUTLINE_DARK);

        titleCluster.add(sessionLogoBadge);
        titleCluster.add(sessionBannerLabel);

        topRightAvatarBadge = new JLabel(StudyModels.createAvatarIcon(currentAvatar, 32));
        topRightAvatarBadge.setToolTipText("Current Profile");
        topBar.add(backBtn, BorderLayout.WEST);
        topBar.add(titleCluster, BorderLayout.CENTER);
        topBar.add(topRightAvatarBadge, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        JPanel columnsPanel = new JPanel(new GridBagLayout());
        columnsPanel.setBackground(BG_CREAM_BROWN);
        GridBagConstraints cg = new GridBagConstraints();
        cg.gridy = 0;
        cg.fill = GridBagConstraints.BOTH;
        cg.weighty = 1.0;

        JPanel leftCol = buildLeftBoardColumn();
        cg.gridx = 0;
        cg.weightx = 0.25;
        cg.insets = new Insets(0, 0, 0, 8);
        columnsPanel.add(leftCol, cg);

        JPanel centerCol = buildCenterStreamColumn();
        cg.gridx = 1;
        cg.weightx = 0.49;
        cg.insets = new Insets(0, 4, 0, 4);
        columnsPanel.add(centerCol, cg);

        JPanel rightCol = buildRightStatusColumn();
        cg.gridx = 2;
        cg.weightx = 0.26;
        cg.insets = new Insets(0, 8, 0, 0);
        columnsPanel.add(rightCol, cg);

        root.add(columnsPanel, BorderLayout.CENTER);
        return root;
    }

    /**
     * Builds the left column containing keyword search, category filter, board entries list, and action buttons.
     */
    private JPanel buildLeftBoardColumn() {
        JPanel col = new JPanel(new BorderLayout(8, 8));
        col.setBackground(PANEL_MOCHA);
        col.setBorder(createOutlinedCardBorder(12, 12, 12, 12));

        JPanel topFilter = new JPanel();
        topFilter.setLayout(new BoxLayout(topFilter, BoxLayout.Y_AXIS));
        topFilter.setBackground(PANEL_MOCHA);

        JLabel searchHeading = new JLabel("Search Chat Keywords", SwingConstants.CENTER);
        searchHeading.setFont(new Font("SansSerif", Font.BOLD, 13));
        searchHeading.setForeground(TEXT_ESPRESSO);
        searchHeading.setAlignmentX(Component.CENTER_ALIGNMENT);

        keywordSearchField = createVintageTextField("", 16);
        keywordSearchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        keywordSearchField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { refreshFilteredBoard(); }
                @Override public void removeUpdate(DocumentEvent e) { refreshFilteredBoard(); }
                @Override public void changedUpdate(DocumentEvent e) { refreshFilteredBoard(); }
        });

        JPanel filterRow = new JPanel(new BorderLayout(6, 0));
        filterRow.setBackground(PANEL_MOCHA);

        categoryFilterCombo = new JComboBox<>(new String[]{
                "All Entries", "Notes Only", "Questions (All)", "Open Questions", "Resolved Questions", "Media & Files"
        });
        categoryFilterCombo.setBackground(CARD_LATTE);
        categoryFilterCombo.setForeground(TEXT_ESPRESSO);
        categoryFilterCombo.setBorder(new LineBorder(OUTLINE_DARK, 1, false));
        categoryFilterCombo.addActionListener(e -> refreshFilteredBoard());

        JButton clearFilterBtn = createVintageButton("Clear");
        clearFilterBtn.addActionListener(e -> {
                keywordSearchField.setText("");
                categoryFilterCombo.setSelectedIndex(0);
                refreshFilteredBoard();
        });

        filterRow.add(categoryFilterCombo, BorderLayout.CENTER);
        filterRow.add(clearFilterBtn, BorderLayout.EAST);
        topFilter.add(searchHeading);
        topFilter.add(Box.createVerticalStrut(6));
        topFilter.add(keywordSearchField);
        topFilter.add(Box.createVerticalStrut(6));
        topFilter.add(filterRow);

        boardJList = new JList<>(boardListModel);
        boardJList.setBackground(CARD_LATTE);
        boardJList.setForeground(TEXT_ESPRESSO);
        boardJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        boardJList.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                    JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof StudyModels.BoardEntry entry) {
                        String tagText = "[" + entry.type.name() + "]";
                        Color rowBg = index % 2 == 0 ? CARD_LATTE : BG_CREAM_BROWN;
                        Color rowFg = TEXT_ESPRESSO;
                        String labelColorHex = "#3B2314";

                        if (entry.type == StudyModels.EntryType.NOTE) {
                            rowBg = NOTE_GLOW_BG;
                            labelColorHex = "#5C3D12";
                        } else if (entry.type == StudyModels.EntryType.QUESTION) {
                            if (entry.resolved) {
                                rowBg = QUESTION_GREEN_BG;
                                rowFg = Color.WHITE;
                                labelColorHex = "#FFFFFF";
                                tagText = "[QUESTION • RIGHT / RESOLVED]";
                            } else {
                                rowBg = QUESTION_RED_BG;
                                rowFg = Color.WHITE;
                                labelColorHex = "#FFFFFF";
                                tagText = "[QUESTION • OPEN]";
                            }
                        }

                        String summary = (entry.fileName != null && !entry.fileName.isEmpty())
                                ? entry.fileName : entry.content;
                        lbl.setText("<html><b><font color='" + labelColorHex + "'>" + tagText + "</font></b> "
                                    + escapeHtml(entry.author) + ": " + escapeHtml(summary)
                                    + (entry.responses.isEmpty() ? "" : " <i>(" + entry.responses.size() + " replies)</i>")
                                    + "</html>");
                        lbl.setBorder(new EmptyBorder(6, 8, 6, 8));

                        if (!isSelected) {
                            lbl.setBackground(rowBg);
                            lbl.setForeground(rowFg);
                        } else {
                            lbl.setBackground(BTN_BROWN);
                            lbl.setForeground(BG_CREAM_BROWN);
                        }
                    }
                    return lbl;
                }
        });

        boardJList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        StudyModels.BoardEntry sel = boardJList.getSelectedValue();
                        if (sel != null && sel.type == StudyModels.EntryType.IMAGE) {
                            openDimmedImageLightbox(sel);
                        }
                    }
                }
        });

        JScrollPane boardScroll = new JScrollPane(boardJList);
        boardScroll.setBorder(new LineBorder(OUTLINE_DARK, 1, false));

        JPanel actionStack = new JPanel(new GridLayout(3, 1, 0, 6));
        actionStack.setBackground(PANEL_MOCHA);

        respondQuestionBtn = createVintageButton("Respond to Question");
        respondQuestionBtn.addActionListener(e -> handleRespondToQuestion());

        markResolvedBtn = createVintageButton("Mark Question Resolved");
        markResolvedBtn.addActionListener(e -> handleMarkQuestionResolved());

        saveMediaFileBtn = createVintageButton("Save Selected Media / File");
        saveMediaFileBtn.addActionListener(e -> handleSaveSelectedMediaOrFile());

        actionStack.add(respondQuestionBtn);
        actionStack.add(markResolvedBtn);
        actionStack.add(saveMediaFileBtn);

        col.add(topFilter, BorderLayout.NORTH);
        col.add(boardScroll, BorderLayout.CENTER);
        col.add(actionStack, BorderLayout.SOUTH);
        return col;
    }

    /**
     * Builds the center column with the Live Study Stream, Notes/Question tags, Doc/Img buttons, and input box.
     */
    private JPanel buildCenterStreamColumn() {
        JPanel col = new JPanel(new BorderLayout(8, 8));
        col.setBackground(CARD_LATTE);
        col.setBorder(createOutlinedCardBorder(12, 12, 12, 12));

        JPanel streamHeader = new JPanel(new BorderLayout());
        streamHeader.setBackground(CARD_LATTE);

        JLabel title = new JLabel("Live Study Stream");
        title.setFont(new Font("SansSerif", Font.BOLD, 14));
        title.setForeground(TEXT_ESPRESSO);

        JButton helpBtn = createVintageButton("/help");
        helpBtn.setBorder(new EmptyBorder(4, 10, 4, 10));
        helpBtn.addActionListener(e -> appendCenteredNotice("Commands: /name <newname>, /help, /exit"));

        streamHeader.add(title, BorderLayout.WEST);
        streamHeader.add(helpBtn, BorderLayout.EAST);

        htmlKit = new HTMLEditorKit();
        htmlDoc = new HTMLDocument();
        chatStreamPane = new JTextPane();
        chatStreamPane.setContentType("text/html");
        chatStreamPane.setEditorKit(htmlKit);
        chatStreamPane.setDocument(htmlDoc);
        chatStreamPane.setEditable(false);
        chatStreamPane.setBackground(BG_CREAM_BROWN);
        chatStreamPane.setForeground(TEXT_ESPRESSO);

        chatStreamPane.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int pos = chatStreamPane.viewToModel2D(e.getPoint());
                    if (pos >= 0) {
                        Element elem = htmlDoc.getCharacterElement(pos);
                        Object entryIdAttr = elem.getAttributes().getAttribute("entry-id");
                        if (entryIdAttr != null) {
                            StudyModels.BoardEntry entry = entriesById.get(entryIdAttr.toString());
                            if (entry != null && entry.type == StudyModels.EntryType.IMAGE) {
                                openDimmedImageLightbox(entry);
                            }
                        }
                    }
                }
        });

        try {
            htmlKit.insertHTML(htmlDoc, htmlDoc.getLength(),
                "<div style='font-family:SansSerif;color:#684F3E;text-align:center;padding:6px;'>"
                        + "<i>Tip: Type /help for commands (/name &lt;newname&gt;, /exit, /help). "
                        + "Click any shared image to preview.</i></div>",
                0, 0, null);
        } catch (Exception ignored) {
        }

        JScrollPane chatScroll = new JScrollPane(chatStreamPane);
        chatScroll.setBorder(new LineBorder(OUTLINE_DARK, 1, false));
        JPanel composerBox = new JPanel(new BorderLayout(6, 6));
        composerBox.setBackground(PANEL_MOCHA);
        composerBox.setBorder(createOutlinedCardBorder(8, 10, 8, 10));

        JPanel tagRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        tagRow.setBackground(PANEL_MOCHA);

        JLabel tagLbl = new JLabel("Tag:");
        tagLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        tagLbl.setForeground(TEXT_ESPRESSO);

        tagNoteCheck = new JCheckBox("Notes");
        tagNoteCheck.setBackground(PANEL_MOCHA);
        tagNoteCheck.setForeground(TEXT_ESPRESSO);

        tagQuestionCheck = new JCheckBox("Question");
        tagQuestionCheck.setBackground(PANEL_MOCHA);
        tagQuestionCheck.setForeground(TEXT_ESPRESSO);

        tagNoteCheck.addActionListener(e -> {
                if (tagNoteCheck.isSelected()) tagQuestionCheck.setSelected(false);
        });
        tagQuestionCheck.addActionListener(e -> {
                if (tagQuestionCheck.isSelected()) tagNoteCheck.setSelected(false);
        });

        tagRow.add(tagLbl);
        tagRow.add(tagNoteCheck);
        tagRow.add(tagQuestionCheck);

        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.setBackground(PANEL_MOCHA);

        JPanel leftAttachPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftAttachPanel.setBackground(PANEL_MOCHA);

        attachDocBtn = createVintageButton("Doc");
        attachDocBtn.setToolTipText("Attach Document / File");
        attachDocBtn.addActionListener(e -> handleAttachDocument());

        attachImageBtn = createVintageButton("Img");
        attachImageBtn.setToolTipText("Attach Image");
        attachImageBtn.addActionListener(e -> handleAttachImage());

        leftAttachPanel.add(attachDocBtn);
        leftAttachPanel.add(attachImageBtn);

        messageInputField = createVintageTextField("", 24);
        messageInputField.addActionListener(e -> handleSendMessage());

        sendBtn = createVintageButton("Send");
        sendBtn.addActionListener(e -> handleSendMessage());

        inputRow.add(leftAttachPanel, BorderLayout.WEST);
        inputRow.add(messageInputField, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);

        composerBox.add(tagRow, BorderLayout.NORTH);
        composerBox.add(inputRow, BorderLayout.SOUTH);

        col.add(streamHeader, BorderLayout.NORTH);
        col.add(chatScroll, BorderLayout.CENTER);
        col.add(composerBox, BorderLayout.SOUTH);
        return col;
    }

    /**
     * Builds the right column showing Connection Status (including session/joined timers), Online Peers, and Activity Log.
     */
    private JPanel buildRightStatusColumn() {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBackground(PANEL_MOCHA);
        col.setBorder(createOutlinedCardBorder(12, 12, 12, 12));

        JLabel statusHdr = new JLabel("Connection Status");
        statusHdr.setFont(new Font("SansSerif", Font.BOLD, 13));
        statusHdr.setForeground(TEXT_ESPRESSO);
        statusHdr.setAlignmentX(Component.LEFT_ALIGNMENT);

        connectionStatusArea = new JTextArea(6, 18);
        connectionStatusArea.setEditable(false);
        connectionStatusArea.setBackground(CARD_LATTE);
        connectionStatusArea.setForeground(TEXT_ESPRESSO);
        connectionStatusArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        connectionStatusArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane statusScroll = new JScrollPane(connectionStatusArea);
        statusScroll.setBorder(new LineBorder(OUTLINE_DARK, 1, false));
        statusScroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel peersHdr = new JLabel("Online Study Peers");
        peersHdr.setFont(new Font("SansSerif", Font.BOLD, 13));
        peersHdr.setForeground(TEXT_ESPRESSO);
        peersHdr.setAlignmentX(Component.LEFT_ALIGNMENT);

        onlinePeersList = new JList<>(onlinePeersModel);
        onlinePeersList.setBackground(CARD_LATTE);
        onlinePeersList.setForeground(TEXT_ESPRESSO);
        JScrollPane peersScroll = new JScrollPane(onlinePeersList);
        peersScroll.setBorder(new LineBorder(OUTLINE_DARK, 1, false));
        peersScroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel logHdr = new JLabel("Activity Log");
        logHdr.setFont(new Font("SansSerif", Font.BOLD, 13));
        logHdr.setForeground(TEXT_ESPRESSO);
        logHdr.setAlignmentX(Component.LEFT_ALIGNMENT);

        activityLogArea = new JTextArea(8, 18);
        activityLogArea.setEditable(false);
        activityLogArea.setLineWrap(true);
        activityLogArea.setWrapStyleWord(true);
        activityLogArea.setBackground(CARD_LATTE);
        activityLogArea.setForeground(TEXT_ESPRESSO);
        activityLogArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        activityLogArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane logScroll = new JScrollPane(activityLogArea);
        logScroll.setBorder(new LineBorder(OUTLINE_DARK, 1, false));
        logScroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        col.add(statusHdr);
        col.add(Box.createVerticalStrut(4));
        col.add(statusScroll);
        col.add(Box.createVerticalStrut(10));
        col.add(peersHdr);
        col.add(Box.createVerticalStrut(4));
        col.add(peersScroll);
        col.add(Box.createVerticalStrut(10));
        col.add(logHdr);
        col.add(Box.createVerticalStrut(4));
        col.add(logScroll);
        return col;
    }

    /**
     * Displays a dimmed overlay lightbox modal for inspecting and saving shared images.
     */
    private void openDimmedImageLightbox(StudyModels.BoardEntry entry) {
        if (entry == null || entry.base64Data == null || entry.base64Data.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Image data is unavailable.", "Image Preview", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        byte[] imageBytes;
        try {
            imageBytes = Base64.getDecoder().decode(entry.base64Data);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "Could not decode image.", "Image Preview", JOptionPane.ERROR_MESSAGE);
            return;
        }

        ImageIcon rawIcon = new ImageIcon(imageBytes);
        int maxW = Math.max(360, getWidth() - 160);
        int maxH = Math.max(260, getHeight() - 160);
        Image displayImg = rawIcon.getImage();
        if (rawIcon.getIconWidth() > maxW || rawIcon.getIconHeight() > maxH) {
            double scale = Math.min((double) maxW / rawIcon.getIconWidth(), (double) maxH / rawIcon.getIconHeight());
            int scaledW = Math.max(1, (int) (rawIcon.getIconWidth() * scale));
            int scaledH = Math.max(1, (int) (rawIcon.getIconHeight() * scale));
            displayImg = rawIcon.getImage().getScaledInstance(scaledW, scaledH, Image.SCALE_SMOOTH);
        }

        JDialog lightbox = new JDialog(this, "Image Preview", true);
        lightbox.setUndecorated(true);
        lightbox.setBackground(new Color(0, 0, 0, 0));
        lightbox.setSize(getSize());
        lightbox.setLocationRelativeTo(this);

        JPanel dimmedOverlay = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.74f));
                g2.setColor(new Color(0x16, 0x0E, 0x09));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        dimmedOverlay.setOpaque(false);

        JPanel previewCard = new JPanel(new BorderLayout(8, 8));
        previewCard.setBackground(CARD_LATTE);
        previewCard.setBorder(createOutlinedCardBorder(12, 12, 12, 12));

        JLabel captionLbl = new JLabel(
                    (entry.fileName != null ? entry.fileName : "Shared Image") + " — shared by " + entry.author,
            SwingConstants.CENTER
        );
        captionLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        captionLbl.setForeground(TEXT_ESPRESSO);

        JLabel imgView = new JLabel(new ImageIcon(displayImg));
        imgView.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnBar.setBackground(CARD_LATTE);

        JButton downloadImgBtn = createVintageButton("Save Image");
        downloadImgBtn.addActionListener(e -> saveEntryToDisk(entry, lightbox));

        JButton closeBtn = createVintageButton("Close");
        closeBtn.addActionListener(e -> lightbox.dispose());

        btnBar.add(downloadImgBtn);
        btnBar.add(closeBtn);

        previewCard.add(captionLbl, BorderLayout.NORTH);
        previewCard.add(imgView, BorderLayout.CENTER);
        previewCard.add(btnBar, BorderLayout.SOUTH);

        dimmedOverlay.add(previewCard);
        dimmedOverlay.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    lightbox.dispose();
                }
        });

        lightbox.setContentPane(dimmedOverlay);
        lightbox.setVisible(true);
    }

    /**
     * Validates and saves the user's nickname from the profile sidebar.
     */
    private void saveHomeProfile() {
        String typed = nicknameField.getText().trim();
        if (typed.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a non-blank nickname.", "Profile", JOptionPane.WARNING_MESSAGE);
            return;
        }
        currentNick = typed;
        homeFooterStatus.setText("Saved profile: " + currentNick + " (" +
                    StudyModels.avatarDisplayLabel(currentAvatar) + ")");
    }
    /**
     * Starts an embedded StudyServer on the configured port and connects the host client to it.
     */
    private void handleCreateSession() {
        saveHomeProfile();
        int port;
        try {
            port = Integer.parseInt(defaultPortField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter a valid port number.", "Invalid Port", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            cleanupSession();
            embeddedServer = new StudyServer(port);
            embeddedServer.start();
            isHosting = true;
            activeHostIp = StudyModels.detectLocalIp();
            activePort = port;
            sessionElapsedSeconds = 0L;
            joinedElapsedSeconds = 0L;
            connectToServer("127.0.0.1", port);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not start host server: " + ex.getMessage(),
                "Host Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Validates IP and port inputs and connects as a peer to an existing StudyHive session.
     */
    private void handleJoinSession() {
        saveHomeProfile();
        String ip = joinIpField.getText().trim();
        if (ip.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter the host's IP address.", "Invalid IP", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int port;
        try {
            port = Integer.parseInt(joinPortField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter a valid port number.", "Invalid Port", JOptionPane.ERROR_MESSAGE);
            return;
        }
        isHosting = false;
        activeHostIp = ip;
        activePort = port;
        sessionElapsedSeconds = 0L;
        joinedElapsedSeconds = 0L;
        connectToServer(ip, port);
    }

    /**
     * Connects StudyClient, starts the session timer, updates status info, and switches to Session view.
     */
    private void connectToServer(String connectIp, int port) {
        try {
            client = new StudyClient(connectIp, port, currentNick, currentAvatar, this);
            client.connect();
            sessionBannerLabel.setText("StudyHive — Session (" + activeHostIp + ":" + port + ")");
            updateConnectionStatusBox();
            sessionTimer.start();
            cardLayout.show(rootCards, CARD_SESSION);
            appendActivityLog(currentNick + (isHosting ? " (Host) created the session." : " joined the session."));
        } catch (Exception ex) {
            sessionTimer.stop();
            JOptionPane.showMessageDialog(this, "Connection failed: " + ex.getMessage(),
                "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Sends /exit if connected, stops timers/sockets, resets session state, and returns to Home view.
     */
    private void leaveSession() {
        sessionTimer.stop();
        if (client != null) {
            client.sendCommand("/exit");
        }
        cleanupSession();
        allEntries.clear();
        entriesById.clear();
        boardListModel.clear();
        onlinePeersModel.clear();
        activityLogArea.setText("");
        sessionElapsedSeconds = 0L;
        joinedElapsedSeconds = 0L;
        cardLayout.show(rootCards, CARD_HOME);
    }

    /**
     * Closes active client and embedded host server sockets safely.
     */
    private void cleanupSession() {
        sessionTimer.stop();
        if (client != null) {
            client.closeQuietly();
            client = null;
        }
        if (embeddedServer != null) {
            embeddedServer.stopServer();
            embeddedServer = null;
        }
    }

    /**
     * Sends a slash command, tagged note, tagged question, or regular chat message from the input box.
     */
    private void handleSendMessage() {
        String raw = messageInputField.getText().trim();
        if (raw.isEmpty() || client == null) {
            return;
        }
        messageInputField.setText("");

        if (raw.startsWith("/")) {
            if (raw.equalsIgnoreCase("/help")) {
                appendCenteredNotice("Available commands: /name <newname>, /exit, /help");
                return;
            }
            client.sendCommand(raw);
            if (raw.toLowerCase().startsWith("/exit")) {
                leaveSession();
            }
            return;
        }

        if (tagNoteCheck.isSelected()) {
            client.sendNote(raw);
            tagNoteCheck.setSelected(false);
        } else if (tagQuestionCheck.isSelected()) {
            client.sendQuestion(raw);
            tagQuestionCheck.setSelected(false);
        } else {
            client.sendChat(raw);
        }
    }

    /**
     * Prompts the user to pick a document/file and broadcasts it as Base64.
     */
    private void handleAttachDocument() {
        if (client == null) return;
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                String b64 = Base64.getEncoder().encodeToString(bytes);
                client.sendFile(file.getName(), StudyModels.formatFileSize(bytes.length), b64);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Could not read file: " + ex.getMessage(),
                    "File Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Prompts the user to pick an image file and broadcasts it as Base64.
     */
    private void handleAttachImage() {
        if (client == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Images (*.png, *.jpg, *.jpeg, *.gif)", "png", "jpg", "jpeg", "gif"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                String b64 = Base64.getEncoder().encodeToString(bytes);
                client.sendImage(file.getName(), StudyModels.formatFileSize(bytes.length), b64);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Could not read image: " + ex.getMessage(),
                    "Image Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Prompts for a reply to the selected question and broadcasts it to the session.
     */
    private void handleRespondToQuestion() {
        StudyModels.BoardEntry selected = boardJList.getSelectedValue();
        if (selected == null || selected.type != StudyModels.EntryType.QUESTION || client == null) {
            JOptionPane.showMessageDialog(this, "Select a QUESTION item from the left list first.",
                "Respond to Question", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String reply = JOptionPane.showInputDialog(this,
            "Reply to " + selected.author + "'s question:\n\"" + selected.content + "\"",
            "Respond to Question", JOptionPane.PLAIN_MESSAGE);
        if (reply != null && !reply.trim().isEmpty()) {
            client.sendResponse(selected.id, reply.trim());
        }
    }

    /**
     * Toggles whether the selected question is marked right/resolved across the session.
     */
    private void handleMarkQuestionResolved() {
        StudyModels.BoardEntry selected = boardJList.getSelectedValue();
        if (selected == null || selected.type != StudyModels.EntryType.QUESTION || client == null) {
            JOptionPane.showMessageDialog(this, "Select a QUESTION item from the left list first.",
                "Mark Question Resolved", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        client.sendResolve(selected.id, !selected.resolved);
    }

    /**
     * Validates selection and saves the chosen IMAGE or FILE attachment to disk.
     */
    private void handleSaveSelectedMediaOrFile() {
        StudyModels.BoardEntry selected = boardJList.getSelectedValue();
        if (selected == null || (selected.type != StudyModels.EntryType.IMAGE && selected.type != StudyModels.EntryType.FILE)) {
            JOptionPane.showMessageDialog(this, "Select an IMAGE or FILE entry in the left list to download.",
                "Save Media / File", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        saveEntryToDisk(selected, this);
    }

    /**
     * Writes decoded Base64 entry payload to a user-chosen file path.
     */
    private void saveEntryToDisk(StudyModels.BoardEntry entry, Component parent) {
        if (entry.base64Data == null || entry.base64Data.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "No file payload available for this entry.",
                "Download Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser saver = new JFileChooser();
        saver.setSelectedFile(new File(entry.fileName != null ? entry.fileName : "studyhive_attachment"));
        if (saver.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
            try {
                byte[] decoded = Base64.getDecoder().decode(entry.base64Data);
                Files.write(saver.getSelectedFile().toPath(), decoded);
                appendActivityLog("Saved attachment: " + saver.getSelectedFile().getName());
                JOptionPane.showMessageDialog(parent, "Saved to " + saver.getSelectedFile().getAbsolutePath(),
                    "Download Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(parent, "Failed to save file: " + ex.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Rebuilds the left board list based on the current keyword query and category filter.
     */
    private void refreshFilteredBoard() {
        String kw = keywordSearchField.getText().trim().toLowerCase();
        String filter = (String) categoryFilterCombo.getSelectedItem();

        boardListModel.clear();
        for (StudyModels.BoardEntry e : allEntries) {
            if ("Notes Only".equals(filter) && e.type != StudyModels.EntryType.NOTE) continue;
            if ("Questions (All)".equals(filter) && e.type != StudyModels.EntryType.QUESTION) continue;
            if ("Open Questions".equals(filter) && (e.type != StudyModels.EntryType.QUESTION || e.resolved)) continue;
            if ("Resolved Questions".equals(filter) && (e.type != StudyModels.EntryType.QUESTION || !e.resolved)) continue;
            if ("Media & Files".equals(filter)
                        && e.type != StudyModels.EntryType.IMAGE && e.type != StudyModels.EntryType.FILE) continue;

            if (!kw.isEmpty()) {
                boolean matchText = (e.content != null && e.content.toLowerCase().contains(kw))
                        || (e.fileName != null && e.fileName.toLowerCase().contains(kw))
                        || (e.author != null && e.author.toLowerCase().contains(kw));
                if (!matchText) continue;
            }
            boardListModel.addElement(e);
        }
    }

    /**
     * Updates the right-side connection status text, showing Session Time for everyone
     * and Joined Time additionally for joining peers.
     */
    private void updateConnectionStatusBox() {
        StringBuilder sb = new StringBuilder();
        sb.append("Role: ").append(isHosting ? "Host" : "Peer").append("\n");
        sb.append("Server: ").append(activeHostIp).append("\n");
        sb.append("Port: ").append(activePort).append("\n");
        sb.append("User: ").append(currentNick).append("\n");
        sb.append("Peers Connected: ").append(Math.max(1, onlinePeersModel.size())).append(" ");
        sb.append("Session Time: ").append(StudyModels.formatDuration(sessionElapsedSeconds));
        if (!isHosting) {
            sb.append("\nJoined Time: ").append(StudyModels.formatDuration(joinedElapsedSeconds));
        }
        connectionStatusArea.setText(sb.toString());
    }

    /**
     * Inserts HTML markup at the end of the live stream and auto-scrolls to the bottom.
     */
    private void appendStreamHtml(String html) {
        try {
            htmlKit.insertHTML(htmlDoc, htmlDoc.getLength(), html, 0, 0, null);
            chatStreamPane.setCaretPosition(htmlDoc.getLength());
        } catch (BadLocationException | IOException ignored) {
        }
    }
    /**
     * Appends a center-aligned notice (such as join/leave or note/question posted) to the stream.
     */
    private void appendCenteredNotice(String text) {
        appendStreamHtml("<div style='font-family:SansSerif;color:#684F3E;font-size:12px;"
                    + "text-align:center;margin:6px 0;'><i>[" + StudyModels.nowTime() + "] "
                    + escapeHtml(text) + "</i></div>");
    }

    /**
     * Appends a timestamped event line to the right-side activity log.
     */
    private void appendActivityLog(String text) {
        activityLogArea.append("[" + StudyModels.nowTime() + "] " + text + "\n");
        activityLogArea.setCaretPosition(activityLogArea.getDocument().getLength());
    }

    /**
     * Escapes HTML special characters before inserting dynamic text into the stream.
     */
    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // =========================================================================
    // StudyClient.Listener Callbacks
    // =========================================================================

    @Override
    public void onSessionSynced(long initialSessionElapsedSeconds) {
        SwingUtilities.invokeLater(() -> {
                this.sessionElapsedSeconds = Math.max(0L, initialSessionElapsedSeconds);
                this.joinedElapsedSeconds = 0L;
                updateConnectionStatusBox();
        });
    }

    @Override
    public void onChatMessage(String sender, String avatar, String message, String timestamp) {
        SwingUtilities.invokeLater(() -> {
                appendStreamHtml("<div style='font-family:SansSerif;color:#2B1A10;margin:5px 0;'>"
                            + "<b>[" + escapeHtml(StudyModels.avatarDisplayLabel(avatar)) + "] " + escapeHtml(sender) + "</b> "
                            + "<span style='color:#684F3E;font-size:11px;'>" + escapeHtml(timestamp) + "</span><br/>"
                            + escapeHtml(message) + "</div>");
                appendActivityLog("Message from " + sender);
        });
    }

    @Override
    public void onBoardEntryAdded(StudyModels.BoardEntry entry) {
        SwingUtilities.invokeLater(() -> {
                allEntries.add(entry);
                entriesById.put(entry.id, entry);
                refreshFilteredBoard();

                if (entry.type == StudyModels.EntryType.NOTE) {
                    // Subtle warm glow highlight for notes (not overly bright)
                    appendStreamHtml("<div style='font-family:SansSerif;background-color:#F6E2B3;"
                                + "border:1px solid #8C6239;color:#2B1A10;padding:6px 10px;margin:6px 0;'>"
                                + "<b>[NOTE] " + escapeHtml(entry.author) + ":</b> "
                                + escapeHtml(entry.content) + "</div>");
                } else if (entry.type == StudyModels.EntryType.QUESTION) {
                    // Solid red for open questions; turns green once responded and marked right
                    String bgHex = entry.resolved ? "#3E8E55" : "#D94336";
                    String statusTag = entry.resolved ? "QUESTION • RIGHT / RESOLVED" : "QUESTION • OPEN";
                    appendStreamHtml("<div style='font-family:SansSerif;background-color:" + bgHex + ";"
                                + "border:1px solid #3B2314;color:#FFFFFF;padding:6px 10px;margin:6px 0;'>"
                                + "<b>[" + statusTag + "] " + escapeHtml(entry.author) + ":</b> "
                                + escapeHtml(entry.content) + "</div>");
                } else if (entry.type == StudyModels.EntryType.IMAGE) {
                    appendStreamHtml("<div style='font-family:SansSerif;color:#2B1A10;margin:6px 0;'>"
                                + "<b>" + escapeHtml(entry.author) + "</b> shared an image: "
                                + "<span entry-id='" + escapeHtml(entry.id) + "' style='color:#8C6239;text-decoration:underline;cursor:pointer;'>"
                                + "[Click to Preview: " + escapeHtml(entry.fileName) + "]</span></div>");
                } else if (entry.type == StudyModels.EntryType.FILE) {
                    appendStreamHtml("<div style='font-family:SansSerif;color:#2B1A10;margin:6px 0;'>"
                                + "<b>" + escapeHtml(entry.author) + "</b> shared file: <u>"
                                + escapeHtml(entry.fileName) + "</u> (" + escapeHtml(entry.content) + ") "
                                + "<i>— select in left panel to save</i></div>");
                }
                appendActivityLog("Added " + entry.type + " by " + entry.author);
        });
    }

    @Override
    public void onBoardResponseAdded(String entryId, StudyModels.ResponseItem response) {
        SwingUtilities.invokeLater(() -> {
                StudyModels.BoardEntry entry = entriesById.get(entryId);
                if (entry != null) {
                    entry.responses.add(response);
                    refreshFilteredBoard();
                    appendStreamHtml("<div style='font-family:SansSerif;color:#2B1A10;margin:4px 0 4px 16px;'>"
                                + "↳ <b>" + escapeHtml(response.author) + "</b> replied: "
                                + escapeHtml(response.text) + "</div>");
                    appendActivityLog(response.author + " replied to a question");
                }
        });
    }

    @Override
    public void onBoardEntryResolved(String entryId, boolean resolved) {
        SwingUtilities.invokeLater(() -> {
                StudyModels.BoardEntry entry = entriesById.get(entryId);
                if (entry != null) {
                    entry.resolved = resolved;
                    refreshFilteredBoard();
                    appendCenteredNotice("Question marked " + (resolved ? "RIGHT / RESOLVED (Green)" : "OPEN (Red)"));
                    appendActivityLog("Question status changed to " + (resolved ? "RESOLVED" : "OPEN"));
                }
        });
    }

    @Override
    public void onOnlinePeersUpdated(List<String> peers) {
        SwingUtilities.invokeLater(() -> {
                onlinePeersModel.clear();
                for (String p : peers) {
                    onlinePeersModel.addElement("• " + p);
                }
                updateConnectionStatusBox();
        });
    }

    @Override
    public void onSystemNotice(String category, String notice) {
        SwingUtilities.invokeLater(() -> {
                appendCenteredNotice(notice);
                appendActivityLog(notice);
        });
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
                sessionTimer.stop();
                appendCenteredNotice("Disconnected: " + reason);
                appendActivityLog("Disconnected: " + reason);
        });
    }
}
