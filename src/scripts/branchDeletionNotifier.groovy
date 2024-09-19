// @ExecutionModes({ON_SINGLE_NODE="/main_menu/i-plasm/freeplaneNotifier"})
package scripts

/*
 * Info & Discussion:
 *
 * Last Update:
 *
 * ---------
 *
 * 
 * DeletionNotifier: Freeplane floating notifications for alerting/informing on node deletion
 * events.
 *
 * Copyright (C) 2024 bbarbosa
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

/*
 * Some of the classes used have been derived from classes from the "jHelperUtils"
 * library
 *
 * Github: https://github.com/i-plasm/jhelperutils/
 * 
 * License: https://github.com/i-plasm/jhelperutils/blob/main/LICENSE
 *
 */

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.util.stream.Collectors
import java.util.stream.Stream
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSeparator
import javax.swing.JTextArea
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.UIManager
import org.freeplane.core.extension.IExtension
import org.freeplane.core.ui.components.UITools
import org.freeplane.core.util.HtmlUtils
import org.freeplane.core.util.TextUtils
import org.freeplane.features.map.IMapChangeListener
import org.freeplane.features.map.NodeDeletionEvent
import org.freeplane.features.map.NodeModel
import org.freeplane.features.mode.Controller
import groovy.transform.Field

@Field static boolean hasBeenStarted = false

if (hasBeenStarted) {
  FreeplaneBranchDeletionNotifier.SwingNotifications.getInstance().showLatestNotification()
  return
}

try {
  new FreeplaneBranchDeletionNotifier(UITools.isLightLookAndFeelInstalled())
}
catch (Exception e) {
  boolean useLightLaf = UIManager.getLookAndFeel() != null && UIManager.getLookAndFeel().getName().toLowerCase().contains("dar") ? false: true
  new FreeplaneBranchDeletionNotifier(useLightLaf)
}

hasBeenStarted = true

public class FreeplaneBranchDeletionNotifier implements IExtension, IMapChangeListener {

  private Integer deletionThreshold = 2 // min number of deleted branch node count
  private boolean shouldBeep = true
  private static int allDeletedNodesCounter = 0
  private Map<String, String> parentsOfDeleted = new HashMap<>()

  public FreeplaneBranchDeletionNotifier(boolean isLightLookAndFeel) {
    if (isLightLookAndFeel) {
      SwingNotifications.getInstance().setGradientColors(new Color(73, 135, 200), new Color(255, 255, 255),
          SwingNotifications.MessageType.INFO)
    } else {
      SwingNotifications.getInstance().setGradientColors(new Color(73, 135, 200).darker().darker(),
          UIManager.getColor("Panel.background").darker(), SwingNotifications.MessageType.INFO)
      SwingNotifications.getInstance().useDarkModeFont(true)
    }

    SwingNotifications.getInstance().setAutoClose(false)
    Controller.getCurrentController().getModeController().getMapController()
        .addUIMapChangeListener(this)
  }


  @Override
  public void onPreNodeDelete(NodeDeletionEvent nodeDeletionEvent) {
    // createID only creates ID if it does not exist
    parentsOfDeleted.put(nodeDeletionEvent.node.createID(),
        nodeDeletionEvent.node.getParentNode().createID())
  }

  @Override
  public void onNodeDeleted(NodeDeletionEvent nodeDeletionEvent) {
    NodeModel deletedNode = nodeDeletionEvent.node
    int deletedCount = countBranchNodes(deletedNode)
    allDeletedNodesCounter += deletedCount
    if (deletedCount >= deletionThreshold) {
      if (shouldBeep) {
        Toolkit.getDefaultToolkit().beep()
      }
      String mapPathMsg = deletedNode.getMap().getFile() == null ? ""
          : "\n" + "FilePath: " + deletedNode.getMap().getFile().toString()
      String parentID = "\n" + "Parent id: " + parentsOfDeleted.get(nodeDeletionEvent.node.getID())
      String fullNodeText =
          "\n" + "Full Node Text: " + HtmlUtils.htmlToPlain(deletedNode.getText(), true, true)
      SwingNotifications.getInstance().showNotification(
          "Branch root: " +
          TextUtils.getShortText(HtmlUtils.htmlToPlain(deletedNode.getText(), true, true), 10,
          "...") +
          mapPathMsg + "\n" + "MindMap: " + deletedNode.getMap().getTitle() + "\n" +
          "Node id: " + deletedNode.getID() + parentID + fullNodeText,
          deletedCount + " nodes were deleted", "Freeplane Branch Deletion Notifier",
          SwingNotifications.MessageType.INFO)
    }
  }

  public void displayLogWindow() {
    SwingNotifications.getInstance().displayLog("", "DELETION THRESHOLD: " + deletionThreshold)
    JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "\n" +
        "TOTAL # OF NODES DELETED IN THIS SESSION(*): " + allDeletedNodesCounter + "\n" +
        "(*) Includes deletion of single nodes and deletions below the threshold. Deletions detected provided the deletion event has been fired during the time this script has been active.")
  }

  public int countBranchNodes(NodeModel node, int counter) {
    int childCount = node.getChildCount()
    counter += childCount
    for (int i = 0; i < childCount; i++) {
      NodeModel child = node.getChildAt(i)
      counter += countBranchNodes(child, 0)
    }
    return counter
  }

  public int countBranchNodes(NodeModel node) {
    return countBranchNodes(node, 1)
  }

  public void setDeletionThreshold(Integer deletionThreshold) {
    this.deletionThreshold = deletionThreshold
  }

  static class GradientJPanel extends JPanel {

    private Color firstColor = Color.MAGENTA
    private Color secondColor = Color.BLUE
    private int borderCurveRadius = 20
    private int secondPointX = 400
    private int shadowThickness = 0
    private int shadowOffset = 0

    public Color getFirstColor() {
      return firstColor
    }

    public void setUniColor(Color color) {
      this.firstColor = color
      this.secondColor = color
    }

    public void setFirstColor(Color firstColor) {
      this.firstColor = firstColor
    }

    public Color getLastColor() {
      return secondColor
    }


    public void setSecondColor(Color secondColor) {
      this.secondColor = secondColor
    }

    public void setSecondPointX(int secondPointX) {
      this.secondPointX = secondPointX
    }

    public void setBorderCurveRadius(int borderCurveRadius) {
      this.borderCurveRadius = borderCurveRadius
    }

    public void setShadowThickness(int shadowThickness) {
      this.shadowThickness = shadowThickness
    }

    public GradientJPanel() {}

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g)
      Graphics2D g2d = (Graphics2D) g
      applyQualityProperties(g2d)

      int totalWidth = getWidth()
      int totalHeight = getHeight()

      // Drawing shadow
      g2d.setColor(new Color(0, 0, 0, 0.05f)) //
      g2d.fillRoundRect(0 + shadowOffset + shadowThickness, 0 + shadowOffset + shadowThickness,
          totalWidth - 1 - shadowOffset - shadowThickness,
          totalHeight - 1 - shadowOffset - shadowThickness, borderCurveRadius, borderCurveRadius)
      g2d.drawRoundRect(0 + shadowOffset + shadowThickness, 0 + shadowOffset + shadowThickness,
          totalWidth - 1 - shadowOffset - shadowThickness,
          totalHeight - 1 - shadowOffset - shadowThickness, borderCurveRadius, borderCurveRadius)

      // Drawing panel gradient
      GradientPaint gp = new GradientPaint(0, 0, firstColor, secondPointX, totalHeight, secondColor)
      g2d.setPaint(gp)
      g2d.fillRoundRect(0, 0, totalWidth - 1 - shadowThickness, totalHeight - 1 - shadowThickness,
          borderCurveRadius, borderCurveRadius)
      g2d.drawRoundRect(0, 0, totalWidth - 1 - shadowThickness, totalHeight - 1 - shadowThickness,
          borderCurveRadius, borderCurveRadius)
    }

    private static void applyQualityProperties(Graphics2D g2) {
      g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

      // Secondary properties
      g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
          RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
      g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,
          RenderingHints.VALUE_COLOR_RENDER_QUALITY)
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
          RenderingHints.VALUE_INTERPOLATION_BICUBIC)
      g2.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE)
      g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
          RenderingHints.VALUE_FRACTIONALMETRICS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }
  }


  static class SwingNotifications {


    enum Location {
      TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
    }

    public enum MessageType {
      INFO, ERROR
    }

    private boolean useDarkModeFont = false
    private boolean autoClose = false

    private final JFrame auxFrame
    private final JTextArea messagePane
    private final JTextArea callerAppPane
    private final JTextArea subjectPane
    private final Timer timer
    private final GradientJPanel panel
    private final Location location = Location.TOP_RIGHT
    private final JLabel expandLabel
    private final JPanel glass

    private int calculatedMinHeight
    private boolean isMaximizedState

    private static final int SECONDS = 5
    private static final int WIDTH = 300
    private static final int MIN_HEIGHT = 60
    private static final int MAX_HEIGHT = 160
    private static final Color DEFAULT_FIRST_COLOR = Color.decode("#9adbfe")
    private static final Color DEFAULT_SECOND_COLOR = Color.WHITE
    private static final Color LIGHT_FONT_COLOR = Color.WHITE.darker()
    private static final Color DARK_FONT_COLOR = new Color(75, 75, 75)

    private static SwingNotifications instance
    private static final List<NotificationData> NOTIFICATIONS_LOG = new ArrayList<>()

    public static SwingNotifications getInstance() {
      if (instance == null) {
        instance = new SwingNotifications()
      }
      return instance
    }

    private SwingNotifications() {

      Font liberationFont = null

      List<String> liberationFontList = Stream
          .of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
          .filter{it -> it.toLowerCase().contains("liberation sans")}.collect(Collectors.toList())
      if (!liberationFontList.isEmpty()) {
        liberationFont = new Font(liberationFontList.get(0), Font.PLAIN, 10)
      }

      auxFrame = new JFrame()
      auxFrame.setType(Window.Type.POPUP)
      auxFrame.setUndecorated(true)
      auxFrame.setAlwaysOnTop(true)
      auxFrame.setAutoRequestFocus(true)
      auxFrame.setLocationRelativeTo(null)
      auxFrame.setFocusableWindowState(false)

      messagePane = new JTextArea()
      subjectPane = new JTextArea()
      callerAppPane = new JTextArea()

      JSeparator separator = new javax.swing.JSeparator(SwingConstants.HORIZONTAL)
      separator.setPreferredSize(new Dimension(0, 2))
      separator.setOpaque(true)

      panel = new GradientJPanel()
      panel.setFirstColor(DEFAULT_FIRST_COLOR)
      panel.setSecondColor(DEFAULT_SECOND_COLOR)
      panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15))
      panel.setLayout(new BorderLayout(5, 5))

      JPanel backgroundPanel = new JPanel()
      backgroundPanel.setLayout(new BoxLayout(backgroundPanel, BoxLayout.PAGE_AXIS))
      panel.setShadowThickness(5)
      panel.setSecondPointX(200)
      panel.add(backgroundPanel, BorderLayout.CENTER)

      backgroundPanel.add(callerAppPane)
      backgroundPanel.add(Box.createRigidArea(new Dimension(0, 5)))
      backgroundPanel.add(separator)
      backgroundPanel.add(Box.createRigidArea(new Dimension(0, 5)))

      messagePane.setMargin(new Insets(0, 0, 0, 0))
      subjectPane.setMargin(new Insets(0, 0, 0, 0))
      callerAppPane.setMargin(new Insets(0, 0, 0, 0))
      messagePane.setLineWrap(true)
      messagePane.setWrapStyleWord(true)
      messagePane.setAlignmentX(Component.LEFT_ALIGNMENT)
      messagePane.setOpaque(false)
      callerAppPane.setLineWrap(true)
      callerAppPane.setWrapStyleWord(true)
      callerAppPane.setAlignmentX(Component.LEFT_ALIGNMENT)
      callerAppPane.setOpaque(false)
      subjectPane.setLineWrap(true)
      subjectPane.setWrapStyleWord(true)
      subjectPane.setAlignmentX(Component.LEFT_ALIGNMENT)
      subjectPane.setOpaque(false)

      separator.setBackground(Color.LIGHT_GRAY)
      separator.setForeground(Color.LIGHT_GRAY)

      if (liberationFont != null) {
        callerAppPane.setFont(liberationFont.deriveFont(Font.BOLD, 12))
        messagePane.setFont(liberationFont.deriveFont(Font.PLAIN, 15))
        subjectPane.setFont(liberationFont.deriveFont(Font.BOLD, 13))
      } else {
        callerAppPane.setFont(callerAppPane.getFont().deriveFont(Font.BOLD, 12))
        messagePane.setFont(messagePane.getFont().deriveFont(Font.PLAIN, 15))
        subjectPane.setFont(subjectPane.getFont().deriveFont(Font.BOLD, 13))
      }

      setForegroundColors()
      subjectPane.setAlignmentX(Component.LEFT_ALIGNMENT)

      JPanel bottomPanel = new JPanel(new BorderLayout())
      JPanel contentsPanel = new JPanel(new BorderLayout(5, 5))
      JPanel buttonsPanel = new JPanel()

      contentsPanel.add(subjectPane, BorderLayout.NORTH)
      contentsPanel.add(messagePane, BorderLayout.CENTER)

      bottomPanel.add(contentsPanel, BorderLayout.CENTER)
      bottomPanel.add(buttonsPanel, BorderLayout.EAST)
      bottomPanel.setAlignmentX(Component.LEFT_ALIGNMENT)

      backgroundPanel.add(bottomPanel)
      bottomPanel.setOpaque(false)
      buttonsPanel.setOpaque(false)
      contentsPanel.setOpaque(false)
      backgroundPanel.setOpaque(false)

      panel.setOpaque(false)
      auxFrame.getContentPane().add(panel)
      auxFrame.setBackground(new Color(0, 0, 0, 0))
      auxFrame.getRootPane().setBackground(new Color(0, 0, 0, 0))
      auxFrame.getRootPane().setOpaque(false)

      glass = (JPanel) auxFrame.getGlassPane()
      glass.setVisible(true)

      expandLabel = new JLabel("\u25BC")
      expandLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 22))
      expandLabel.setBackground(new Color(0, 0, 0, 0))
      expandLabel.setOpaque(true)
      // expandLabel.setBorder(new LineBorder(Color.LIGHT_GRAY, 1, false));
      buttonsPanel.add(expandLabel)

      MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
              boolean isInsideExpandButton = isPointInsideComponent(e, expandLabel)
              boolean isInsideAppTitle = isPointInsideComponent(e, callerAppPane)
              boolean isInsideCaption = isPointInsideComponent(e, subjectPane)
              if (isInsideExpandButton && expandLabel.getBackground().getAlpha() == 0) {
                Color firstColor = panel.getFirstColor()
                expandLabel.setBackground(
                    new Color(firstColor.getRed(), firstColor.getGreen(), firstColor.getBlue(), 100))
              } else if (!isInsideExpandButton && expandLabel.getBackground().getAlpha() != 0) {
                expandLabel.setBackground(new Color(0, 0, 0, 0))
              }

              if (isInsideExpandButton || isInsideCaption) {
                glass.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
                if (isInsideCaption) {
                  glass.setToolTipText("Open notifications log")
                }
              } else {
                glass.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))
                glass.setToolTipText("")
              }
            }
          }

      glass.addMouseMotionListener(mouseAdapter)

      ActionListener listener = new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
              auxFrame.setVisible(false)
            }
          }

      timer = new Timer(SECONDS * 1000, listener)
      timer.setRepeats(false)
    }

    public void showNotification(String text, String caption, String callerApp, MessageType type) {
      launchNotification(text, caption, callerApp, type, 80, false)
    }

    private void setForegroundColors() {
      subjectPane.setForeground(useDarkModeFont ? Color.WHITE : Color.BLACK)
      callerAppPane.setForeground(useDarkModeFont ? Color.WHITE : Color.BLACK)
      messagePane.setForeground(useDarkModeFont ? LIGHT_FONT_COLOR : DARK_FONT_COLOR)
    }

    private void expandNotification(String text, String caption, String callerApp, MessageType type) {
      launchNotification(text, caption, callerApp, type, 0, true)
    }

    private void contractNotification(String text, String caption, String callerApp,
        MessageType type) {
      launchNotification(text, caption, callerApp, type, 80, true)
    }

    private void launchNotification(String text, String caption, String callerApp, MessageType type,
        int truncateSize, boolean isReshowOfNotification) {
      timer.stop()
      if (auxFrame.isVisible() && (truncateSize > 0 || !isReshowOfNotification)) {
        auxFrame.setVisible(false)
        auxFrame.dispose()
      }
      if (!isReshowOfNotification) {
        isMaximizedState = false
        NotificationData.add(text, caption, callerApp, new Date(), type)
      }

      String displayedText = text
      if (truncateSize > 0) {
        if (text.length() > truncateSize) {
          displayedText = text.substring(0, 80) + "..."
        }
      }
      messagePane.setText(displayedText)
      callerAppPane.setText(callerApp)

      subjectPane.setText(caption)
      if (caption.equals("")) {
        subjectPane.setVisible(false)
      } else {
        subjectPane.setVisible(true)
      }

      if (calculatedMinHeight == 0) {
        auxFrame.pack()
        calculatedMinHeight = (int) panel.getMinimumSize().getHeight()
      }
      if (truncateSize == 0) {
        auxFrame.pack()
        auxFrame.setPreferredSize(new Dimension(WIDTH, (int) panel.getMinimumSize().getHeight()))
        auxFrame.setSize(new Dimension(WIDTH, (int) panel.getMinimumSize().getHeight()))
      } else {
        auxFrame.setMinimumSize(new Dimension(WIDTH, calculatedMinHeight))
        auxFrame.setPreferredSize(new Dimension(WIDTH, calculatedMinHeight))
        auxFrame.setSize(new Dimension(WIDTH, calculatedMinHeight))
      }

      NotificationPosition pos = new NotificationPosition(location,
          new Dimension(WIDTH, auxFrame.getHeight()), auxFrame.getGraphicsConfiguration())
      // TODO groovy cast to int
      auxFrame.setLocation((int) pos.effectiveCoordinates.x, (int) pos.effectiveCoordinates.y)
      auxFrame.setVisible(true)

      MyMouseAdapter mouseAdapter = new MyMouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
              if (autoClose) {
                timer.stop()
              }

              boolean isInsideExpandButton = isPointInsideComponent(e, expandLabel)
              boolean isInsideAppTitle = isPointInsideComponent(e, callerAppPane)
              boolean isInsideCaption = isPointInsideComponent(e, subjectPane)
              if (isInsideExpandButton) {
                if (!isMaximizedState) {
                  isMaximizedState = true
                  expandNotification(text, caption, callerApp, type)
                } else {
                  isMaximizedState = false
                  contractNotification(text, caption, callerApp, type)
                }
              } else if (isInsideCaption) {
                displayLog()
              } else {
                auxFrame.setVisible(false)
              }
            }
          }

      for (MouseListener l : glass.getMouseListeners()) {
        if (l instanceof MyMouseAdapter) {
          glass.removeMouseListener(l)
        }
      }
      glass.addMouseListener(mouseAdapter)

      if (autoClose) {
        timer.start()
      }
    }

    public static boolean isPointInsideComponent(MouseEvent e, Component component) {
      boolean isPointInsideExpandButton = component.bounds()
          .contains(SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), component))
      return isPointInsideExpandButton
    }

    static abstract class MyMouseAdapter extends MouseAdapter {
    }

    public void displayLog() {
      displayLog("", "")
    }

    public void displayLog(String header, String title) {
      String prefix = header == null || header.equals("") ? ""
          : header + "\n\n" + "------------------------------"
      String log = prefix + NotificationData.getSessionLog().stream().map{it -> it.toString()}
      .collect(Collectors.joining("\n\n" + "------------------------------"))
      JTextArea textArea = new JTextArea(log)
      textArea.setLineWrap(true)
      textArea.setWrapStyleWord(true)
      JScrollPane scrollPane = new JScrollPane() {
            @Override
            public Dimension getPreferredSize() {
              return new Dimension(300, 500)
            }
          }
      scrollPane.getViewport().add(textArea)
      JOptionPane.showMessageDialog(auxFrame, scrollPane, "Notifications Log - " + title,
          JOptionPane.INFORMATION_MESSAGE)
    }


    static class NotificationData {
      private String text
      private String caption
      private String callerApp
      private Date date
      private MessageType type

      private NotificationData(String text, String caption, String callerApp, Date date,
      MessageType type) {
        super()
        this.text = text
        this.caption = caption
        this.callerApp = callerApp
        this.date = date
        this.type = type
      }

      public static void add(String text, String caption, String callerApp, Date date,
          MessageType type) {
        NOTIFICATIONS_LOG.add(0, new NotificationData(text, caption, callerApp, date, type))
      }

      @Override
      public String toString() {
        return "\n" + "DATE: " + date.toString() + "\n" + "CALLER: " + callerApp + "\n" +
            "CAPTION: " + caption + "\n" + "TEXT: " + "\n" + text
      }

      public static List<NotificationData> getSessionLog() {
        return new ArrayList<>(NOTIFICATIONS_LOG)
      }

      public String getText() {
        return text
      }

      public String getCaption() {
        return caption
      }

      public String getCallerApp() {
        return callerApp
      }


      public static Optional<NotificationData> getLatestNotification() {
        return NOTIFICATIONS_LOG.size() > 0 ? Optional.of(NOTIFICATIONS_LOG.get(0))
            : Optional.empty()
      }

      public MessageType getType() {
        return type
      }
    }

    static class NotificationPosition {
      Point effectiveCoordinates

      NotificationPosition(Location location, Dimension preferredComponentSize,
      GraphicsConfiguration gc) {

        Rectangle bounds = gc.getBounds()
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(gc)

        Rectangle effectiveScreenArea = new Rectangle()

        effectiveScreenArea.x = bounds.x + screenInsets.left
        effectiveScreenArea.y = bounds.y + screenInsets.top
        effectiveScreenArea.height = bounds.height - screenInsets.top - screenInsets.bottom
        effectiveScreenArea.width = bounds.width - screenInsets.left - screenInsets.right

        int x = 0
        int y = 0
        int offset = 5

        double prefWidth = preferredComponentSize.getWidth()
        double prefHeight = preferredComponentSize.getHeight()

        if (location == Location.TOP_LEFT) {
          x = effectiveScreenArea.x + offset
          y = effectiveScreenArea.y + offset
        } else if (location == Location.TOP_RIGHT) {
          x = (int) (effectiveScreenArea.x + effectiveScreenArea.width - prefWidth - offset)
          y = effectiveScreenArea.y + offset
        } else if (location == Location.TOP_CENTER) {
        } else if (location == Location.BOTTOM_LEFT) {
          x = effectiveScreenArea.x + offset
          y = (int) (effectiveScreenArea.y + effectiveScreenArea.height - prefHeight - offset)
        } else if (location == Location.BOTTOM_RIGHT) {
          x = (int) (effectiveScreenArea.x + effectiveScreenArea.width - prefWidth - offset)
          y = (int) (effectiveScreenArea.y + effectiveScreenArea.height - prefHeight - offset)
        } else if (location == Location.BOTTOM_CENTER) {
        }
        int effectiveX = x
        int effectiveY = y
        effectiveCoordinates = new Point(effectiveX, effectiveY)
      }
    }

    public boolean isAutoClose() {
      return autoClose
    }

    public void setAutoClose(boolean shouldAutoClose) {
      autoClose = shouldAutoClose
    }


    public void showLatestNotification() {
      Optional<NotificationData> latest = NotificationData.getLatestNotification()
      if (latest.isPresent()) {
        NotificationData data = latest.get()
        launchNotification(data.getText(), data.getCaption(), data.getCallerApp(), data.getType(), 80,
            true)
      } else {
        JOptionPane.showMessageDialog(null,
            "No notifications have been sent in this session so far.")
      }
    }

    public void setGradientColors(Color firstColor, Color secondColor, MessageType infoMessageType) {
      panel.setFirstColor(firstColor)
      panel.setSecondColor(secondColor)
    }

    public void useDarkModeFont(boolean useDark) {
      useDarkModeFont = useDark
      setForegroundColors()
    }
  }
}

