package dev.xirreal;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.*;
import net.fabricmc.loader.api.FabricLoader;

public class DebuggerPicker extends JFrame {

   private final CountDownLatch latch = new CountDownLatch(1);

   public static enum DebuggerSelection {
      GPU_TRACE,
      FRAME_DEBUGGER,
      RENDERDOC,
      NONE,
   }

   private DebuggerLaunchRequest request = new DebuggerLaunchRequest(DebuggerSelection.NONE);

   private static final Color BG_PRIMARY = new Color(17, 17, 21);
   private static final Color BG_SURFACE = new Color(28, 28, 35);
   private static final Color BG_HOVER = new Color(40, 40, 50);
   private static final Color BG_INPUT = new Color(22, 22, 28);
   private static final Color BORDER_COLOR = new Color(55, 55, 70);
   private static final Color TEXT_PRIMARY = new Color(237, 237, 242);
   private static final Color TEXT_SECONDARY = new Color(145, 145, 165);
   private static final Color ACCENT_BLUE = new Color(96, 165, 250);
   private static final Color ACCENT_GREEN = new Color(74, 222, 128);
   private static final Color ACCENT_ORANGE = new Color(251, 146, 60);

   private static final Font FONT_BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
   private static final Font FONT_SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
   private static final Font FONT_BUTTON = new Font(Font.SANS_SERIF, Font.BOLD, 13);

   private final boolean renderdocAvailable;
   private final boolean ngfxAvailable;
   private final NgfxHelpInfo ngfxHelp;
   private final Properties savedConfig;

   private CardLayout cardLayout;
   private JPanel cardPanel;
   private Dimension selectionSize;

   private static Path getConfigPath() {
      return FabricLoader.getInstance().getConfigDir().resolve("gfx-debuggers.properties");
   }

   static Properties loadConfig() {
      Properties props = new Properties();
      Path path = getConfigPath();
      if (Files.exists(path)) {
         try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
         } catch (IOException ignored) {}
      }
      return props;
   }

   private static void saveConfig(Properties props) {
      try (OutputStream out = Files.newOutputStream(getConfigPath())) {
         props.store(out, "gfx-debuggers settings");
      } catch (IOException ignored) {}
   }

   public DebuggerPicker(boolean renderdocAvailable, boolean ngfxAvailable, NgfxHelpInfo ngfxHelp) {
      this.renderdocAvailable = renderdocAvailable;
      this.ngfxAvailable = ngfxAvailable;
      this.ngfxHelp = ngfxHelp;
      this.savedConfig = loadConfig();

      UIManager.put("ToolTip.background", BG_SURFACE);
      UIManager.put("ToolTip.foreground", TEXT_PRIMARY);
      UIManager.put("ToolTip.font", FONT_SMALL);
      UIManager.put(
         "ToolTip.border",
         BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1), BorderFactory.createEmptyBorder(4, 8, 4, 8))
      );

      setTitle("Graphics Debugger Selector");
      setDefaultCloseOperation(DISPOSE_ON_CLOSE);
      setResizable(false);
      try {
         BufferedImage rawIcon = ImageIO.read(DebuggerPicker.class.getResourceAsStream("/assets/gfx-debuggers/icon.png"));
         int size = rawIcon.getWidth();
         BufferedImage roundedIcon = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
         Graphics2D ig = roundedIcon.createGraphics();
         ig.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         ig.setColor(Color.WHITE);
         ig.fill(new RoundRectangle2D.Float(0, 0, size, size, size * 0.25f, size * 0.25f));
         ig.setComposite(AlphaComposite.SrcIn);
         ig.drawImage(rawIcon, 0, 0, null);
         ig.dispose();
         setIconImage(roundedIcon);
      } catch (Exception ignored) {}
      addWindowListener(
         new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
               latch.countDown();
            }
         }
      );

      JPanel root = new JPanel(new BorderLayout()) {
         @Override
         protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BG_PRIMARY);
            g2.fill(new Rectangle2D.Float(0, 0, getWidth(), getHeight()));
            g2.dispose();
         }
      };
      root.setOpaque(false);
      root.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));

      cardLayout = new CardLayout();
      cardPanel = new JPanel(cardLayout);
      cardPanel.setOpaque(false);

      cardPanel.add(buildSelectionPanel(), "selection");
      cardPanel.add(buildGpuTraceOptionsPanel(), "gpu-trace-options");

      root.add(cardPanel, BorderLayout.CENTER);
      setContentPane(root);

      final Point[] dragOffset = { null };
      root.addMouseListener(
         new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
               dragOffset[0] = e.getPoint();
            }
         }
      );
      root.addMouseMotionListener(
         new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
               if (dragOffset[0] != null) {
                  Point loc = getLocation();
                  setLocation(loc.x + e.getX() - dragOffset[0].x, loc.y + e.getY() - dragOffset[0].y);
               }
            }
         }
      );

      pack();
      selectionSize = getSize();
      setLocationRelativeTo(null);
   }

   private JPanel buildSelectionPanel() {
      JPanel panel = new JPanel(new BorderLayout());
      panel.setOpaque(false);

      JPanel header = new JPanel();
      header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
      header.setOpaque(false);
      header.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));

      JLabel subtitle = new JLabel("Select a debugger to attach to this session");
      subtitle.setFont(FONT_BODY);
      subtitle.setForeground(TEXT_SECONDARY);
      JPanel subtitleCenter = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
      subtitleCenter.setOpaque(false);
      subtitleCenter.add(subtitle);
      header.add(subtitleCenter);

      panel.add(header, BorderLayout.NORTH);

      JPanel cards = new JPanel();
      cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
      cards.setOpaque(false);

      boolean hasCards = false;

      if (ngfxAvailable) {
         cards.add(
            createDebuggerCard(
               "NSight GPU Trace Profiler",
               "Record and analyze GPU frame timings and performance metrics",
               ACCENT_ORANGE,
               DebuggerSelection.GPU_TRACE,
               createImageIcon("/assets/gfx-debuggers/gpu-trace.png")
            )
         );
         cards.add(Box.createVerticalStrut(8));
         cards.add(
            createDebuggerCard(
               "NSight Frame Debugger",
               "Capture and inspect individual rendered frames",
               ACCENT_BLUE,
               DebuggerSelection.FRAME_DEBUGGER,
               createImageIcon("/assets/gfx-debuggers/frame-debugger.png")
            )
         );
         hasCards = true;
      }

      if (renderdocAvailable) {
         if (hasCards) {
            cards.add(Box.createVerticalStrut(8));
         }
         cards.add(
            createDebuggerCard(
               "RenderDoc",
               "Open-source and cross-vendor graphics debugging tool",
               ACCENT_GREEN,
               DebuggerSelection.RENDERDOC,
               createImageIcon("/assets/gfx-debuggers/renderdoc.png")
            )
         );
         hasCards = true;
      }

      cards.add(Box.createVerticalStrut(16));

      JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL) {
         @Override
         protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(BORDER_COLOR);
            g2.fillRect(0, getHeight() / 2, getWidth(), 1);
            g2.dispose();
         }
      };
      sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
      sep.setPreferredSize(new Dimension(0, 1));
      sep.setAlignmentX(Component.LEFT_ALIGNMENT);
      cards.add(sep);
      cards.add(Box.createVerticalStrut(16));

      JPanel skipBtn = createActionButton("Skip injection", TEXT_SECONDARY, () -> {
         request = new DebuggerLaunchRequest(DebuggerSelection.NONE);
         dispose();
      });
      cards.add(skipBtn);

      panel.add(cards, BorderLayout.CENTER);
      return panel;
   }

   private JPanel buildGpuTraceOptionsPanel() {
      JPanel panel = new JPanel(new BorderLayout());
      panel.setOpaque(false);

      JPanel header = new JPanel();
      header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
      header.setOpaque(false);
      header.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));

      JLabel title = new JLabel("GPU Trace Profiler Options");
      title.setFont(FONT_BUTTON);
      title.setForeground(TEXT_PRIMARY);
      JPanel titleCenter = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
      titleCenter.setOpaque(false);
      titleCenter.add(title);
      header.add(titleCenter);

      panel.add(header, BorderLayout.NORTH);

      JPanel optionsContainer = new JPanel();
      optionsContainer.setLayout(new BoxLayout(optionsContainer, BoxLayout.Y_AXIS));
      optionsContainer.setOpaque(false);

      List<String> platforms = ngfxHelp != null ? ngfxHelp.platforms : List.of();
      List<NgfxOption> options = ngfxHelp != null ? ngfxHelp.gpuTraceOptions : List.of();

      JComboBox<String> platformCombo = null;
      // if (platforms.size() > 1) {
      JPanel platformRow = new JPanel(new BorderLayout(8, 0));
      platformRow.setOpaque(false);
      platformRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
      platformRow.setAlignmentX(Component.LEFT_ALIGNMENT);

      JLabel platformLabel = new JLabel("Platform");
      platformLabel.setFont(FONT_BODY);
      platformLabel.setForeground(TEXT_PRIMARY);
      platformLabel.setPreferredSize(new Dimension(140, 28));
      platformRow.add(platformLabel, BorderLayout.WEST);

      platformCombo = createStyledComboBox(platforms.toArray(new String[0]));
      String savedPlatform = savedConfig.getProperty("platform");
      if (savedPlatform != null) {
         platformCombo.setSelectedItem(savedPlatform);
      }
      platformRow.add(platformCombo, BorderLayout.CENTER);

      optionsContainer.add(platformRow);
      optionsContainer.add(Box.createVerticalStrut(8));
      // }

      Map<NgfxOption, JCheckBox> checkBoxes = new LinkedHashMap<>();
      Map<NgfxOption, JTextField> textFields = new LinkedHashMap<>();
      Map<NgfxOption, JComboBox<String>> comboBoxes = new LinkedHashMap<>();

      for (NgfxOption opt : options) {
         if (opt.deprecated) {
            continue;
         }

         if (!opt.takesValue) {
            JCheckBox cb = createStyledCheckBox(formatLabel(opt.flag));
            cb.setToolTipText(opt.description);
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            String saved = savedConfig.getProperty("opt." + opt.flag);
            if (saved != null) {
               cb.setSelected("true".equals(saved));
            } else if (opt.flag.equals("--start-after-hotkey")) {
               cb.setSelected(true);
            }
            checkBoxes.put(opt, cb);
            optionsContainer.add(cb);
            optionsContainer.add(Box.createVerticalStrut(6));
         } else if (opt.booleanArg) {
            JCheckBox cb = createStyledCheckBox(formatLabel(opt.flag));
            cb.setToolTipText(opt.description);
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            String saved = savedConfig.getProperty("opt." + opt.flag);
            if (saved != null) {
               cb.setSelected("true".equals(saved));
            } else {
               cb.setSelected("1".equals(opt.defaultValue));
            }
            checkBoxes.put(opt, cb);
            optionsContainer.add(cb);
            optionsContainer.add(Box.createVerticalStrut(6));
         } else if (!opt.choices.isEmpty()) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel label = new JLabel(formatLabel(opt.flag));
            label.setFont(FONT_BODY);
            label.setForeground(TEXT_PRIMARY);
            label.setPreferredSize(new Dimension(140, 28));
            label.setToolTipText(opt.description);
            row.add(label, BorderLayout.WEST);

            JComboBox<String> combo = createStyledComboBox(opt.choices.toArray(new String[0]));
            combo.setToolTipText(opt.description);
            String savedCombo = savedConfig.getProperty("opt." + opt.flag);
            if (savedCombo != null) {
               combo.setSelectedItem(savedCombo);
            } else if (opt.defaultValue != null) {
               combo.setSelectedItem(opt.defaultValue);
            }
            comboBoxes.put(opt, combo);
            row.add(combo, BorderLayout.CENTER);

            optionsContainer.add(row);
            optionsContainer.add(Box.createVerticalStrut(6));
         } else {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel label = new JLabel(formatLabel(opt.flag));
            label.setFont(FONT_BODY);
            label.setForeground(TEXT_PRIMARY);
            label.setPreferredSize(new Dimension(140, 28));
            label.setToolTipText(opt.description);
            row.add(label, BorderLayout.WEST);

            String savedText = savedConfig.getProperty("opt." + opt.flag);
            String initial;
            if (savedText != null) {
               initial = savedText;
            } else {
               initial = opt.defaultValue != null ? opt.defaultValue : "";
               if (opt.flag.equals("--limit-to-frames") && initial.isEmpty()) {
                  initial = "5";
               }
            }
            JTextField field = createStyledTextField(initial);
            field.setToolTipText(opt.description);
            textFields.put(opt, field);
            row.add(field, BorderLayout.CENTER);

            optionsContainer.add(row);
            optionsContainer.add(Box.createVerticalStrut(6));
         }
      }

      JScrollPane scrollPane = new JScrollPane(optionsContainer);
      scrollPane.setOpaque(false);
      scrollPane.getViewport().setOpaque(false);
      scrollPane.setBorder(BorderFactory.createEmptyBorder());
      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane.setPreferredSize(new Dimension(400, 240));

      scrollPane.setViewportBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));

      JScrollBar vsb = scrollPane.getVerticalScrollBar();
      vsb.setUnitIncrement(16);
      vsb.setOpaque(false);
      vsb.setPreferredSize(new Dimension(8, 0));
      vsb.setUI(
         new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
               trackColor = BG_PRIMARY;
               thumbColor = BORDER_COLOR;
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
               return createZeroButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
               return createZeroButton();
            }

            private JButton createZeroButton() {
               JButton btn = new JButton();
               btn.setPreferredSize(new Dimension(0, 0));
               btn.setMaximumSize(new Dimension(0, 0));
               btn.setMinimumSize(new Dimension(0, 0));
               return btn;
            }

            @Override
            protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
               if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) return;
               Graphics2D g2 = (Graphics2D) g.create();
               g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
               g2.setColor(BORDER_COLOR);
               g2.fill(new RoundRectangle2D.Float(thumbBounds.x + 1, thumbBounds.y, thumbBounds.width - 2, thumbBounds.height, 6, 6));
               g2.dispose();
            }

            @Override
            protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {}
         }
      );

      panel.add(scrollPane, BorderLayout.CENTER);

      JPanel buttons = new JPanel();
      buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
      buttons.setOpaque(false);
      buttons.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));

      JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL) {
         @Override
         protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(BORDER_COLOR);
            g2.fillRect(0, getHeight() / 2, getWidth(), 1);
            g2.dispose();
         }
      };
      sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
      sep.setPreferredSize(new Dimension(0, 1));
      sep.setAlignmentX(Component.LEFT_ALIGNMENT);
      buttons.add(sep);
      buttons.add(Box.createVerticalStrut(12));

      final JComboBox<String> finalPlatformCombo = platformCombo;

      JPanel launchBtn = createActionButton("Launch GPU Trace Profiler", ACCENT_ORANGE, () -> {
         List<String> extraArgs = new ArrayList<>();
         for (var entry : checkBoxes.entrySet()) {
             NgfxOption opt = entry.getKey();
             boolean selected = entry.getValue().isSelected();
             if (opt.booleanArg && selected) {
                extraArgs.add(opt.flag);
                extraArgs.add("1");
             } else if (!opt.booleanArg && selected) {
                extraArgs.add(opt.flag);
             }
          }
         for (var entry : comboBoxes.entrySet()) {
            String val = (String) entry.getValue().getSelectedItem();
            if (val != null && !val.isEmpty()) {
               extraArgs.add(entry.getKey().flag);
               extraArgs.add(val);
            }
         }
         for (var entry : textFields.entrySet()) {
            String val = entry.getValue().getText().strip();
            if (!val.isEmpty()) {
               extraArgs.add(entry.getKey().flag);
               extraArgs.add(val);
            }
         }

         String platform = null;
         if (finalPlatformCombo != null) {
            platform = (String) finalPlatformCombo.getSelectedItem();
         } else if (platforms.size() == 1) {
            platform = platforms.get(0);
         }

         Properties config = new Properties();
         config.setProperty("debugger", "GPU_TRACE");
         if (platform != null) {
            config.setProperty("platform", platform);
         }
         for (var entry : checkBoxes.entrySet()) {
            config.setProperty("opt." + entry.getKey().flag, String.valueOf(entry.getValue().isSelected()));
         }
         for (var entry : comboBoxes.entrySet()) {
            String val = (String) entry.getValue().getSelectedItem();
            if (val != null) {
               config.setProperty("opt." + entry.getKey().flag, val);
            }
         }
         for (var entry : textFields.entrySet()) {
            config.setProperty("opt." + entry.getKey().flag, entry.getValue().getText().strip());
         }
         saveConfig(config);

         request = new DebuggerLaunchRequest(DebuggerSelection.GPU_TRACE, platform, extraArgs);
         dispose();
      });
      buttons.add(launchBtn);
      buttons.add(Box.createVerticalStrut(8));

      JPanel backBtn = createActionButton("Back", TEXT_SECONDARY, () -> {
         cardLayout.show(cardPanel, "selection");
         setResizable(false);
         setSize(selectionSize);
      });
      buttons.add(backBtn);

      panel.add(buttons, BorderLayout.SOUTH);
      return panel;
   }

   private void selectDebugger(DebuggerSelection selection) {
      if (selection == DebuggerSelection.GPU_TRACE && ngfxHelp != null && (!ngfxHelp.gpuTraceOptions.isEmpty() || ngfxHelp.platforms.size() > 1)) {
         cardLayout.show(cardPanel, "gpu-trace-options");
         setResizable(true);
         pack();
         setSize(Math.max(getWidth(), 500), Math.max(getHeight(), 450));
      } else {
         String platform = null;
         if (ngfxHelp != null && ngfxHelp.platforms.size() == 1) {
            platform = ngfxHelp.platforms.get(0);
         }
         Properties config = new Properties();
         config.setProperty("debugger", selection.name());
         saveConfig(config);
         request = new DebuggerLaunchRequest(selection, platform, List.of());
         dispose();
      }
   }

   private JPanel createDebuggerCard(String name, String description, Color accent, DebuggerSelection value, Icon icon) {
      JPanel card = new JPanel(new BorderLayout(12, 0)) {
         private boolean hovered = false;

         {
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));

            addMouseListener(
               new MouseAdapter() {
                  @Override
                  public void mouseEntered(MouseEvent e) {
                     hovered = true;
                     repaint();
                  }

                  @Override
                  public void mouseExited(MouseEvent e) {
                     hovered = false;
                     repaint();
                  }

                  @Override
                  public void mouseClicked(MouseEvent e) {
                     selectDebugger(value);
                  }
               }
            );
         }

         @Override
         protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color bg = hovered ? BG_HOVER : BG_SURFACE;
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
            Color border = hovered ? accent.darker() : BORDER_COLOR;
            g2.setColor(border);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 12, 12));
            g2.dispose();
         }
      };

      JLabel iconLabel = new JLabel(icon);
      iconLabel.setVerticalAlignment(SwingConstants.CENTER);
      card.add(iconLabel, BorderLayout.WEST);

      JPanel textPanel = new JPanel();
      textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
      textPanel.setOpaque(false);

      JLabel nameLabel = new JLabel(name);
      nameLabel.setFont(FONT_BUTTON);
      nameLabel.setForeground(TEXT_PRIMARY);
      nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
      textPanel.add(nameLabel);
      textPanel.add(Box.createVerticalStrut(2));

      JLabel descLabel = new JLabel(description);
      descLabel.setFont(FONT_BODY);
      descLabel.setForeground(TEXT_SECONDARY);
      descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
      textPanel.add(descLabel);

      card.add(textPanel, BorderLayout.CENTER);

      JPanel arrow = new JPanel() {
         @Override
         protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(TEXT_SECONDARY);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int midY = getHeight() / 2;
            int midX = getWidth() / 2;
            g2.drawLine(midX - 2, midY - 5, midX + 1, midY);
            g2.drawLine(midX + 1, midY, midX - 2, midY + 5);
            g2.dispose();
         }
      };
      arrow.setOpaque(false);
      arrow.setPreferredSize(new Dimension(16, 16));
      JPanel arrowWrapper = new JPanel(new BorderLayout());
      arrowWrapper.setOpaque(false);
      arrowWrapper.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
      arrowWrapper.add(arrow, BorderLayout.CENTER);
      card.add(arrowWrapper, BorderLayout.EAST);

      card.setAlignmentX(Component.LEFT_ALIGNMENT);
      return card;
   }

   private JPanel createActionButton(String text, Color textColor, Runnable action) {
      JPanel btn = new JPanel(new BorderLayout()) {
         private boolean hovered = false;

         {
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

            addMouseListener(
               new MouseAdapter() {
                  @Override
                  public void mouseEntered(MouseEvent e) {
                     hovered = true;
                     repaint();
                  }

                  @Override
                  public void mouseExited(MouseEvent e) {
                     hovered = false;
                     repaint();
                  }

                  @Override
                  public void mouseClicked(MouseEvent e) {
                     action.run();
                  }
               }
            );
         }

         @Override
         protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color bg = hovered ? BG_HOVER : BG_SURFACE;
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
            g2.dispose();
         }
      };

      JLabel label = new JLabel(text);
      label.setFont(FONT_BODY);
      label.setForeground(textColor);
      label.setHorizontalAlignment(SwingConstants.CENTER);
      btn.add(label, BorderLayout.CENTER);
      btn.setAlignmentX(Component.LEFT_ALIGNMENT);
      return btn;
   }

   private JTextField createStyledTextField(String initialValue) {
      JTextField field = new JTextField(initialValue) {
         @Override
         protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
            g2.dispose();
            super.paintComponent(g);
         }

         @Override
         protected void paintBorder(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BORDER_COLOR);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 8, 8));
            g2.dispose();
         }
      };
      field.setOpaque(false);
      field.setBackground(BG_INPUT);
      field.setForeground(TEXT_PRIMARY);
      field.setCaretColor(TEXT_PRIMARY);
      field.setFont(FONT_BODY);
      field.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
      field.setPreferredSize(new Dimension(200, 28));
      return field;
   }

   private JCheckBox createStyledCheckBox(String text) {
      JCheckBox cb = new JCheckBox(text);
      cb.setOpaque(false);
      cb.setForeground(TEXT_PRIMARY);
      cb.setFont(FONT_BODY);
      cb.setFocusPainted(false);
      cb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      Icon icon = new Icon() {
         @Override
         public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BG_INPUT);
            g2.fillRoundRect(x, y, getIconWidth(), getIconHeight(), 4, 4);
            g2.setColor(BORDER_COLOR);
            g2.drawRoundRect(x, y, getIconWidth() - 1, getIconHeight() - 1, 4, 4);
            if (((JCheckBox) c).isSelected()) {
               g2.setColor(ACCENT_BLUE);
               g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
               int px = x + 3;
               int py = y + 7;
               g2.drawLine(px, py, px + 3, py + 3);
               g2.drawLine(px + 3, py + 3, px + 9, py - 3);
            }
            g2.dispose();
         }
         @Override public int getIconWidth() { return 16; }
         @Override public int getIconHeight() { return 16; }
      };
      cb.setIcon(icon);
      cb.setSelectedIcon(icon);
      return cb;
   }

   private JComboBox<String> createStyledComboBox(String[] items) {
      JComboBox<String> combo = new JComboBox<>(items);
      combo.setBackground(BG_INPUT);
      combo.setForeground(TEXT_PRIMARY);
      combo.setFont(FONT_BODY);
      combo.setBorder(new AbstractBorder() {
         @Override
         public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(c.hasFocus() ? ACCENT_BLUE : BORDER_COLOR);
            g2.drawRoundRect(x, y, width - 1, height - 1, 8, 8);
            g2.dispose();
         }

         @Override
         public Insets getBorderInsets(Component c) {
            return new Insets(4, 8, 4, 8);
         }
      });
      combo.setFocusable(true);
      combo.setUI(
         new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
               JButton btn = new JButton("▾");
               btn.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 6));
               btn.setBackground(BG_INPUT);
               btn.setForeground(TEXT_SECONDARY);
               btn.setFont(FONT_SMALL);
               btn.setFocusPainted(false);
               btn.setContentAreaFilled(false);
               return btn;
            }

            @Override
            public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
               g.setColor(BG_INPUT);
               g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }

            @Override
            public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
               // Always render as non-focused to prevent the default focus highlight
               super.paintCurrentValue(g, bounds, false);
            }

            @Override
            protected void installListeners() {
               super.installListeners();
               comboBox.addFocusListener(new FocusAdapter() {
                  @Override
                  public void focusGained(FocusEvent e) { comboBox.repaint(); }
                  @Override
                  public void focusLost(FocusEvent e) { comboBox.repaint(); }
               });
            }
         }
      );
      combo.setRenderer(
         new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
               Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
               if (isSelected) {
                  c.setBackground(BG_HOVER);
                  c.setForeground(TEXT_PRIMARY);
               } else {
                  c.setBackground(BG_INPUT);
                  c.setForeground(TEXT_PRIMARY);
               }
               ((JLabel) c).setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
               return c;
            }
         }
      );
      Object popupComp = combo.getUI().getAccessibleChild(combo, 0);
      if (popupComp instanceof JPopupMenu popup) {
         popup.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
      }
      combo.setPreferredSize(new Dimension(200, 28));
      return combo;
   }

   private static String formatLabel(String flag) {
      String name = flag.startsWith("--") ? flag.substring(2) : flag;
      String[] parts = name.split("-");
      StringBuilder sb = new StringBuilder();
      for (String part : parts) {
         if (sb.length() > 0) {
            sb.append(' ');
         }
         if (!part.isEmpty()) {
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1));
         }
      }
      return sb.toString();
   }

   private static Icon createImageIcon(String resourcePath) {
      return new Icon() {
         private BufferedImage masked;

         {
            try {
               BufferedImage raw = ImageIO.read(DebuggerPicker.class.getResourceAsStream(resourcePath));
               masked = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
               Graphics2D mg = masked.createGraphics();
               mg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
               mg.setColor(Color.WHITE);
               mg.fill(new RoundRectangle2D.Float(0, 0, 32, 32, 8, 8));
               mg.setComposite(AlphaComposite.SrcIn);
               mg.drawImage(raw, 0, 0, null);
               mg.dispose();
            } catch (Exception e) {
               masked = null;
            }
         }

         @Override
         public void paintIcon(Component c, Graphics g, int x, int y) {
            if (masked != null) {
               g.drawImage(masked, x, y, null);
            }
         }

         @Override
         public int getIconWidth() {
            return 32;
         }

         @Override
         public int getIconHeight() {
            return 32;
         }
      };
   }

   public DebuggerLaunchRequest getRequest() {
      setVisible(true);
      try {
         latch.await();
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
      return request;
   }

   public DebuggerSelection getSelection() {
      return getRequest().selection;
   }
}
