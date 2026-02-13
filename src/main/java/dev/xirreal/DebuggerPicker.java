package dev.xirreal;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.util.concurrent.*;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.border.*;

public class DebuggerPicker extends JFrame {

   private final CountDownLatch latch = new CountDownLatch(1);

   public static enum DebuggerSelection {
      GPU_TRACE,
      FRAME_DEBUGGER,
      RENDERDOC,
      NONE,
   }

   private DebuggerSelection selectedDebugger = DebuggerSelection.NONE;

   private static final Color BG_PRIMARY = new Color(17, 17, 21);
   private static final Color BG_SURFACE = new Color(28, 28, 35);
   private static final Color BG_HOVER = new Color(40, 40, 50);
   private static final Color BORDER_COLOR = new Color(55, 55, 70);
   private static final Color TEXT_PRIMARY = new Color(237, 237, 242);
   private static final Color TEXT_SECONDARY = new Color(145, 145, 165);
   private static final Color ACCENT_BLUE = new Color(96, 165, 250);
   private static final Color ACCENT_GREEN = new Color(74, 222, 128);
   private static final Color ACCENT_ORANGE = new Color(251, 146, 60);

   private static final Font FONT_BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
   private static final Font FONT_BUTTON = new Font(Font.SANS_SERIF, Font.BOLD, 13);

   public DebuggerPicker() {
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

      root.add(header, BorderLayout.NORTH);

      JPanel cards = new JPanel();
      cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
      cards.setOpaque(false);

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
      cards.add(Box.createVerticalStrut(8));
      cards.add(
         createDebuggerCard(
            "RenderDoc",
            "Open-source and cross-vendor graphics debugging tool",
            ACCENT_GREEN,
            DebuggerSelection.RENDERDOC,
            createImageIcon("/assets/gfx-debuggers/renderdoc.png")
         )
      );

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

      JPanel skipBtn = createSkipButton();
      cards.add(skipBtn);

      root.add(cards, BorderLayout.CENTER);
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
      setLocationRelativeTo(null);
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
                     selectedDebugger = value;
                     dispose();
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

   private JPanel createSkipButton() {
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
                     selectedDebugger = DebuggerSelection.NONE;
                     dispose();
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

      JLabel label = new JLabel("Skip injection");
      label.setFont(FONT_BODY);
      label.setForeground(TEXT_SECONDARY);
      label.setHorizontalAlignment(SwingConstants.CENTER);
      btn.add(label, BorderLayout.CENTER);
      btn.setAlignmentX(Component.LEFT_ALIGNMENT);
      return btn;
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

   public DebuggerSelection getSelection() {
      setVisible(true);
      try {
         latch.await();
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
      return selectedDebugger;
   }
}
