// @ExecutionModes({ON_SINGLE_NODE="/main_menu/i-plasm/miscUtils"})

package scripts

/*
 * Info & Discussion: https://github.com/freeplane/freeplane/discussions/1948
 *
 * Last Update: 2024-09-28
 *
 * ---------
 *
 * ImgInspector: Freeplane auto-launching popup tool that provides convenient image previewing
 * functionalities.
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

import java.awt.AWTEvent
import java.awt.Color
import java.awt.Component
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Frame
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import java.awt.event.AWTEventListener
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.util.function.Predicate
import java.util.stream.Stream
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JRootPane
import javax.swing.JScrollPane
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.UIManager
import javax.swing.border.EmptyBorder
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener
import org.freeplane.api.Node
import org.freeplane.features.mode.Controller
import org.freeplane.plugin.script.proxy.ScriptUtils
import org.freeplane.view.swing.features.filepreview.BitmapViewerComponent
import org.freeplane.view.swing.map.NodeView

new ImgInspector().init()

/**
 * Most classes used have been derived from classes from the "jHelperUtils"
 * library
 *
 * Github: https://github.com/i-plasm/jhelperutils/
 *
 */
public class ImgInspector {

  private static final String EXTENSION_NAME = "ImgInspector"
  private static final String PLUGIN_VERSION = "v0.8.6"

  private ImgAWTEventListener<BitmapViewerComponent> listener

  public void stop() {
    Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
    //ZoomablePreview.EXECUTOR.shutdown()
  }

  public void init() {
    listener = new ImgAWTEventListener<BitmapViewerComponent>(BitmapViewerComponent.class)
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_MOTION_EVENT_MASK)
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK)
  }

  static class ImgAWTEventListener<T> implements AWTEventListener {
    private ViewerPopup popup = createViewerPopup()
    private Class<T> clazz
    private JPopupMenu externalPopupShown = null

    public ImgAWTEventListener(Class<T> clazz) {
      this.clazz = clazz
    }

    @Override
    public void eventDispatched(AWTEvent e) {
      // Validating if external popup is showing
      if (e.getSource() instanceof JPopupMenu && !(e.getSource() instanceof ViewerPopup)) {
        externalPopupShown = (JPopupMenu) e.getSource()
      } else if (externalPopupShown != null && !externalPopupShown.isShowing()) {
        externalPopupShown = null
      }

      if (!(e.getSource() instanceof Component)) {
        return
      }

      Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()
      boolean isAnotherWindowOnTop = focusOwner != null &&
          SwingUtilities.getWindowAncestor((Component) e.getSource()) != SwingUtilities
          .getWindowAncestor(focusOwner)
      if (!(e.getSource().getClass().isAssignableFrom(clazz)) || isAnotherWindowOnTop) {
        return
      }

      Component component = (Component) e.getSource()

      Optional<MouseListener> viewerListener =
          popup.hookedComponent != null
          ? Stream.of(popup.hookedComponent.getMouseListeners())
          .filter{it -> it instanceof ImageViewerListener}.findFirst()
          : Optional.empty()

      if (popup.hookedComponent != null && popup.hookedComponent != component) {
        viewerListener.ifPresent{it -> popup.hookedComponent.removeMouseListener(it)}
      } else if (popup.hookedComponent != null && popup.hookedComponent == component) {
        if (externalPopupShown != null) {
          viewerListener.ifPresent{it -> ((ImageViewerListener) it).deactivatePopup()}
        } else {
          viewerListener.ifPresent{it -> ((ImageViewerListener) it).activatePopup()}
        }
        return
      }

      ImageViewerListener<BitmapViewerComponent> bitmapViewerlistener =
          new ImageViewerListener<BitmapViewerComponent>((BitmapViewerComponent) component, popup,
          getImgAvailablePredicate())
      component.addMouseListener(bitmapViewerlistener)
      if (externalPopupShown != null) {
        bitmapViewerlistener.deactivatePopup()
      } else {
        bitmapViewerlistener.activatePopup()
      }
      bitmapViewerlistener.mouseEntered(
          new MouseEvent(component, -1, System.currentTimeMillis(), 0, 0, 0, 0, 0, 0, false, 0))
    }

    private Predicate<BitmapViewerComponent> getImgAvailablePredicate() {
      return {comp -> comp.getOriginalSize() != null}
    }

    private ViewerPopup createViewerPopup() {
      return new ViewerPopup()
    }
  }

  private static class ViewerPopup extends JPopupMenu {
    public static final String PREVIEW_COMP_NAME = "img_preview_hover"

    private HoverButton btnPreview
    protected Component hookedComponent
    private String previousImage
    private String currentImage
    private String imgBackground
    private static boolean isPreviewBeingLoaded
    private static final Insets CANONICAL_FRAME_INSETS = getCanonicalFrameInsets()

    private ViewerPopup() {
      GridBagConstraints c = new GridBagConstraints()
      this.setLayout(new GridBagLayout())

      HoverButton btnInfo = null
      HoverButton btnCopyPath

      // unicode opposition button
      btnCopyPath = new HoverButton("<html><p><font size=12>&#9741;</font></p></html>")

      if (isWindowsOS()) {
        // unicode info button
        btnInfo = new HoverButton("<html><p><font size=12>&#128712;</font></p></html>")

        // unicode Left-Pointing Magnifying Glass button
        btnPreview = new HoverButton("<html><p><font size=12>&#128269;</font></p></html>")
      } else {
        btnInfo = new HoverButton("<html><p><font size=12>&nbsp;?&nbsp;</font></p></html>")

        // unicode Greek Letter Qoppa
        //btnPreview = new HoverButton("\u03D8")
        //btnPreview.setFont(btnPreview.getFont().deriveFont(Font.BOLD | Font.ITALIC))
        btnPreview = new HoverButton("<html><p><font size=12><i>&nbsp;&#984;&nbsp;</i></font></p></html>")
      }

      btnInfo.setToolTipText("Information & Help")
      btnCopyPath.setToolTipText("Copy Image URL")

      btnCopyPath.addActionListener{ l ->
        copyURL()
      }
      btnInfo.addActionListener{ l ->
        displayHelp()
      }

      MouseAdapter exitAdapter = new MouseAdapter() {

            @Override
            public void mouseExited(MouseEvent e) {

              JPopupMenu popup = ViewerPopup.this

              Point p = java.awt.MouseInfo.getPointerInfo().getLocation()
              boolean isPointContainedInHookedComp =
                  p.x >= getHookedComponent().getLocationOnScreen().x &&
                  p.x <= (getHookedComponent().getWidth() +
                  getHookedComponent().getLocationOnScreen().x) &&
                  p.y >= getHookedComponent().getLocationOnScreen().y &&
                  p.y <= (getHookedComponent().getHeight() +
                  getHookedComponent().getLocationOnScreen().y)
              SwingUtilities.convertPointFromScreen(p, popup)

              if (popup != null && !isPointContainedInHookedComp &&
                  !popup.getVisibleRect().contains(p)) {
                popup.setVisible(false)
              }
            }
          }

      btnPreview.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
              // Workaround for focus loss while loading in Linux
              if (isPreviewBeingLoaded) {
                return
              }
              isPreviewBeingLoaded = true
              UndecoratedPreviewFrame hoverWindow = createHoverWindow(ViewerPopup.this.getCurrentImage(),
                  ViewerPopup.this.getImgBackground(), ViewerPopup.this.getSuggstedPreviewLocation(),
                  SwingUtilities.getWindowAncestor(ViewerPopup.this.getHookedComponent()).getGraphicsConfiguration())

              // The small size is temporary. We maximize only after it is visible to avoid glitches on certain environments.
              if (!isWindowsOS() && hoverWindow.isMaximized()) {
                hoverWindow.setSize(2,2)
              }

              if (!hoverWindow.isMaximized()) {
                hoverWindow.getRootPane().setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, new Color(0, 0, 255, 150)))
              }
              hoverWindow.setVisible(true)

              SwingUtilities.invokeLater{
                // Windows excluded due to bug: https://bugs.java.com/bugdatabase/view_bug?bug_id=4737788
                // Necessary for at least some Linux OS, where true screen insets can not be obtained under multi-monitor set up
                if (!isWindowsOS() && hoverWindow.isMaximized()) {
                  hoverWindow.setExtendedState(Frame.MAXIMIZED_BOTH)
                }

                SwingUtilities.invokeLater{
                  ViewerPopup.this.setVisible(false)

                  SwingUtilities.invokeLater{
                    isPreviewBeingLoaded = false
                  }
                }
              }
            }
          })

      btnInfo.addMouseListener(exitAdapter)
      btnCopyPath.addMouseListener(exitAdapter)

      this.add(btnPreview)
      this.add(btnCopyPath)
      this.add(btnInfo)

      c.weightx = 1d
      c.weighty = 0d
      c.fill = GridBagConstraints.HORIZONTAL
      c.anchor = GridBagConstraints.CENTER
      c.gridy = 0
      c.insets = new Insets(2, 0, 2, 0)
      c.gridwidth = 4
      c.gridx = 0
      c.gridy = GridBagConstraints.RELATIVE
      c.insets = new Insets(0, 0, 0, 0)
    }

    private void copyURL() {

      Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard()
      if (clipboard == null) {
        JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(ViewerPopup.this), "Failed to copy to clipboard", "",
            JOptionPane.ERROR_MESSAGE)
        return
      }
      StringSelection sel = new StringSelection(getCurrentImage())
      try {
        clipboard.setContents(sel, null)
        JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(ViewerPopup.this), "Copied to clipboard:\n" + getCurrentImage(), "",
            JOptionPane.INFORMATION_MESSAGE)
      } catch (Exception e) {
        JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(ViewerPopup.this), "Failed to copy to clipboard", "",
            JOptionPane.ERROR_MESSAGE)
        e.printStackTrace()
      }
    }

    private void displayHelp() {
      int result = JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(ViewerPopup.this),
          "Click 'OK' to learn more about " + EXTENSION_NAME + ", and get updates on Github." +
          "\n\n" + "Version: " + PLUGIN_VERSION,
          EXTENSION_NAME + " - Information & Help", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE)

      if (result == JOptionPane.OK_OPTION) {
        Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null
        if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
          try {
            desktop.browse(new URI("https://github.com/freeplane/freeplane/discussions/1948"))
          } catch (Exception e) {
            e.printStackTrace()
          }
        }
      }
    }

    public void hookToComponent(Component component) {
      this.hookedComponent = component
      String compImg = getImageURI().toString()
      this.previousImage = previousImage == null ? compImg : currentImage
      this.currentImage = compImg
      this.imgBackground = getBackgroundColor()
      setVisible(false)
    }

    public Component getHookedComponent() {
      return hookedComponent
    }

    public String getCurrentImage() {
      return currentImage
    }

    public String getImgBackground() {
      return imgBackground
    }

    private String getBackgroundColor() {
      String bg = ScriptUtils.node().getMap().getBackgroundColorCode()
      return bg == null ? "#ffffff" : bg
    }

    private URI getImageURI() {
      NodeView nodeView =
          (NodeView) SwingUtilities.getAncestorOfClass(NodeView.class, hookedComponent)
      Node node
      //TODO diff freeplane versions : nodeView.getModel() vs. nodeView.getNode()
      try {
        node = ScriptUtils.node().getMap().node(nodeView.getModel().createID())
      } catch (Exception e1) {
        node = ScriptUtils.node().getMap().node(nodeView.getNode().createID())
      }
      URI uri = null

      try {
        uri = new URI(node.getExternalObject().getUri())
        File mapFile = Controller.getCurrentController().getMap().getFile()
        if (mapFile == null) {

          return null
        }
        File mapDir = mapFile.getParentFile()
        uri = uri.isAbsolute() ? uri : mapDir.toURI().resolve(uri)
      } catch (URISyntaxException e) {
        // TODO Auto-generated catch block
        e.printStackTrace()
      }

      return uri
    }

    private static String imageHTMLWithBgColor(String uri, String bgColor) {
      String str = "<img src = \"" + uri + "\">"
      return "<html><body style=\"background-color:" + bgColor + ";\">" + str + "</body></html>"
    }

    public static Rectangle getMaxWindowBounds(GraphicsConfiguration config) {
      Rectangle bounds = null
      bounds = config.getBounds()
      Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
      bounds.x += insets.left
      bounds.y += insets.top
      bounds.width -= insets.left + insets.right
      bounds.height -= insets.top + insets.bottom


      return bounds
    }

    private static Insets getCanonicalFrameInsets() {
      JFrame frame = new JFrame()
      frame.pack()
      return frame.getInsets()
    }

    static JFrame createHoverWindow(String imgUrl, String bgColor, Point suggestedLocation,
        GraphicsConfiguration config) {
      String html = imageHTMLWithBgColor(imgUrl, bgColor)
      JLabel label = new JLabel(html)
      UndecoratedPreviewFrame frame
      JFrame dummyFrame = new JFrame(config)
      // frame.setName(PREVIEW_COMP_NAME);

      dummyFrame.setLayout(new FlowLayout(FlowLayout.CENTER))
      dummyFrame.add(label)
      dummyFrame.pack()
      Rectangle maxBounds = getMaxWindowBounds(config)
      Rectangle dummyFrameBounds = dummyFrame.getBounds()
      Rectangle fullScreenBounds = config.getBounds()

      boolean isImgShowingFully = dummyFrameBounds.width < maxBounds.width &&
          (dummyFrameBounds.height - CANONICAL_FRAME_INSETS.top) < maxBounds.height
      // Defensive workaround for possible issues when calculating screen insets in some environments
      boolean shouldApplyOversizePrevention = dummyFrameBounds.width > fullScreenBounds.width*0.9 ||
          (dummyFrameBounds.height - CANONICAL_FRAME_INSETS.top) > fullScreenBounds.height*0.9
      dummyFrame.dispose()

      if (!isImgShowingFully || shouldApplyOversizePrevention) {
        frame = new UndecoratedPreviewFrame(config)//new JFrame(config)
        frame.setMaximized(true)
        // frame.setName(PREVIEW_COMP_NAME);
        final JPanel panel = new JPanel() {
              @Override
              public Dimension getPreferredSize() {
                //TODO groovy cast to int
                return new Dimension((int) label.getBounds().width, (int) label.getBounds().height)
              }
            }

        panel.add(label)
        JScrollPane scrollPane = new JScrollPane(panel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED)

        panel.addMouseListener(new MouseAdapter() {
              @Override
              public void mouseExited(MouseEvent e) {
                if (!panel.isShowing()) {
                  return
                }
                if ((e.getLocationOnScreen().y < panel.getLocationOnScreen().y) ||
                    (e.getLocationOnScreen().y < scrollPane.getViewportBorderBounds().y)) {
                  frame.setVisible(false)
                  frame.dispose()
                }
              }
            })

        label.addMouseListener(new MouseAdapter() {
              @Override
              public void mouseClicked(MouseEvent e) {
                GraphicsConfiguration defaultConfig = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration()
                int width = frame.getWidth()
                int height = isWindowsOS()? frame.getHeight() : frame.getHeight() - CANONICAL_FRAME_INSETS.top
                Point location = isWindowsOS()? frame.getLocation() : new Point((int) frame.getLocation().x,
                    (int) frame.getLocation().y + (int) CANONICAL_FRAME_INSETS.top*2 - Toolkit.getDefaultToolkit().getScreenInsets(defaultConfig).top)
                frame.setVisible(false)
                frame.dispose()
                previewFullSize(imgUrl, bgColor, width, height, location)
              }

              @Override
              public void mouseExited(MouseEvent e) {
                if (!label.isShowing()) {
                  return
                }
                if ((e.getLocationOnScreen().y < label.getLocationOnScreen().y) ||
                    (e.getLocationOnScreen().y < scrollPane.getViewportBorderBounds().y)) {
                  frame.setVisible(false)
                  frame.dispose()
                }
              }
            })

        frame.addWindowListener(new WindowAdapter() {

              @Override
              public void windowDeactivated(WindowEvent e) {
                // Workaround to sometimes being autoclosed before finishing loading + maximization
                if (isPreviewBeingLoaded) {
                  return
                }
                if (frame.isVisible()) {
                  frame.setVisible(false)
                  frame.dispose()
                }
              }
            })
        frame.addWindowFocusListener(new WindowFocusListener() {
              @Override
              public void windowLostFocus(WindowEvent e) {
                // Workaround to sometimes being autoclosed before finishing loading + maximization
                if (isPreviewBeingLoaded) {
                  return
                }
                if (frame.isVisible()) {
                  frame.setVisible(false)
                  frame.dispose()
                }
              }

              @Override
              public void windowGainedFocus(WindowEvent e) {}
            })

        frame.add(scrollPane)
        frame.setUndecorated(true)
        // TODO groovy cast to int
        frame.setSize((int) maxBounds.width, isWindowsOS()? (int) maxBounds.height :
            (int) maxBounds.height - (int) CANONICAL_FRAME_INSETS.top)
        // frame.pack();
        // TODO groovy cast to int
        frame.setLocation((int) maxBounds.x, (int) maxBounds.y)
      } else {
        frame = new UndecoratedPreviewFrame(config)//new JFrame(config)
        frame.setMaximized(false)
        // frame.setName(PREVIEW_COMP_NAME);
        frame.setUndecorated(true)
        frame.setLayout(new FlowLayout(FlowLayout.CENTER))
        frame.add(label)

        // Correction due to dummy frame title bar
        int yCorrection = CANONICAL_FRAME_INSETS.top == 0 ? 25 : CANONICAL_FRAME_INSETS.top
        if (!isMacOS()) {
          yCorrection += 10
        }
        int x = maxBounds.x + maxBounds.width > suggestedLocation.x + dummyFrame.getWidth()
            ? suggestedLocation.x
            : maxBounds.x + maxBounds.width - dummyFrame.getWidth()
        int y = maxBounds.y + maxBounds.height > suggestedLocation.y + dummyFrame.getHeight()
            ? suggestedLocation.y
            : maxBounds.y + maxBounds.height - dummyFrame.getHeight() + yCorrection

        frame.setLocation(x, y)
        frame.pack()

        label.addMouseListener(new MouseAdapter() {
              @Override
              public void mouseExited(MouseEvent e) {
                frame.setVisible(false)
                frame.dispose()
              }

              @Override
              public void mouseClicked(MouseEvent e) {
                Point location = frame.getLocation()
                frame.setVisible(false)
                frame.dispose()
                previewFullSize(imgUrl, bgColor, maxBounds, location, config)
              }
            })
      }

      addEscapeListener(frame)
      return frame
    }

    public Point getSuggstedPreviewLocation() {
      return btnPreview.getLocationOnScreen()
    }

    public static void addEscapeListener(JFrame frame) {
      Action dispatchClosing = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
              frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING))
            }
          }

      KeyStroke escape = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)
      JRootPane rootPane = frame.getRootPane()
      rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escape, "closeWindow")
      rootPane.getActionMap().put("closeWindow", dispatchClosing)
    }

    static void previewFullSize(String imgUrl, String bgColor, Rectangle maxBounds,
        Point suggestedLocation,  GraphicsConfiguration config) {
      String html = imageHTMLWithBgColor(imgUrl, bgColor)
      JLabel label = new JLabel(html)
      JFrame frame
      frame = new JFrame(config)
      frame.setName(PREVIEW_COMP_NAME)
      final JPanel panel = new JPanel()
      panel.add(label)
      JScrollPane scrollPane = new JScrollPane(panel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
          JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED)
      frame.add(scrollPane)
      frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE)
      addEscapeListener(frame)
      frame.pack()
      frame.setLocation(suggestedLocation)
      frame.setVisible(true)
    }

    static void previewFullSize(String imgUrl, String bgColor, int width, int height,
        Point location) {
      String html = imageHTMLWithBgColor(imgUrl, bgColor)
      JLabel label = new JLabel(html)
      JFrame frame
      frame = new JFrame()
      frame.setName(PREVIEW_COMP_NAME)
      final JPanel panel = new JPanel()
      panel.add(label)
      JScrollPane scrollPane = new JScrollPane(panel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
          JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED)
      frame.add(scrollPane)
      frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE)
      addEscapeListener(frame)
      frame.pack()
      frame.setSize(width, height)
      frame.setLocation(location)
      frame.setVisible(true)
    }

    private static String imageHTML(String uri) {
      String str = "<img src = \"" + uri + "\">"
      return "<html><body>" + str + "</body></html>"
    }
  }

  static class UndecoratedPreviewFrame extends JFrame {
    private boolean maximized

    public UndecoratedPreviewFrame(GraphicsConfiguration config)  {
      super(config)
      this.maximized = maximized
    }

    public boolean isMaximized() {
      return maximized
    }

    public void setMaximized(boolean maximized) {
      this.maximized = maximized
    }
  }

  static class HoverButton extends JButton {
    private static final Font MODEL_FONT = new JButton().getFont()

    public HoverButton(String text) {
      super(text)
      setBorderPainted(false)
      setBackground(UIManager.getColor("control"))
      setBorder(new EmptyBorder(5, 5, 5, 5))
      setFocusable(false)
      setFont(MODEL_FONT.deriveFont(40f).deriveFont(Font.PLAIN))

      Color selColor = UIManager.getColor("MenuItem.selectionBackground")

      addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
              setBackground(selColor.brighter())
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
              setBackground(UIManager.getColor("control"))
            }
          })

      addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorRemoved(AncestorEvent event) {
              setBackground(UIManager.getColor("control"))
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {}

            @Override
            public void ancestorAdded(AncestorEvent event) {}
          })

      addHierarchyListener(new HierarchyListener() {
            @Override
            public void hierarchyChanged(HierarchyEvent e) {
              if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0 ||
                  (e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (!e.getComponent().isDisplayable()) {
                  setBackground(UIManager.getColor("control"))
                }
              }
            }
          })
    }
  }

  static class ImageViewerListener<T extends Component> extends MouseAdapter {

    private T component
    private Timer viewerTimer
    private ViewerPopup popup
    private boolean deactivatePopup = false

    public ImageViewerListener(T component, ViewerPopup popup, Predicate<T> isValidImage) {
      this.component = component
      this.popup = popup
      popup.hookToComponent(component)
      this.viewerTimer = new Timer(200, null)

      ActionListener timerAction = new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
              viewerTimer.stop()

              if (deactivatePopup) {
                return
              }
              Point pos = component.getMousePosition()
              if (pos == null) {
                return
              }

              boolean isValidImagePresent = isValidImage.test(component)

              if (!isValidImagePresent) {
                if (popup != null) {
                  popup.setVisible(false)
                }
                return
              }
              if (popup != null && !popup.isShowing()) {
                int x = pos.x - popup.getWidth() / 2
                int y = pos.y

                popup.show(component, x, y)
              }
            }
          }

      this.viewerTimer.addActionListener(timerAction)

      for (MouseListener l : popup.getMouseListeners()) {
        if (PopupAdapter.class.isInstance(l)) {
          popup.removeMouseListener(l)
          break
        }
      }
      popup.addMouseListener(new PopupAdapter())
    }

    public void deactivatePopup() {
      deactivatePopup = true
    }

    public void activatePopup() {
      deactivatePopup = false
    }

    @Override
    public void mouseExited(MouseEvent e) {
      Point p = java.awt.MouseInfo.getPointerInfo().getLocation()
      // SwingUtilities.convertPointFromScreen(p, popup);

      if (popup.isShowing()) {
        boolean isPointContainedInPopup = p.x >= popup.getLocationOnScreen().x &&
            p.x <= (popup.getWidth() + popup.getLocationOnScreen().x) &&
            p.y >= popup.getLocationOnScreen().y &&
            p.y <= (popup.getHeight() + popup.getLocationOnScreen().y)

        if (!isPointContainedInPopup) {
          popup.setVisible(false)
        }
        return
      }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()
      boolean isAnotherWindowOnTop = focusOwner != null &&
          SwingUtilities.getWindowAncestor((Component) e.getSource()) != SwingUtilities
          .getWindowAncestor(focusOwner)
      if (popup.isShowing() || isAnotherWindowOnTop  || focusOwner == null) {
        return
      }
      viewerTimer.start()
      viewerTimer.setRepeats(false)
    }

    class PopupAdapter extends MouseAdapter {
      @Override
      public void mouseExited(MouseEvent e) {
        if (!component.isShowing()) {
          return
        }
        Point p = java.awt.MouseInfo.getPointerInfo().getLocation()
        boolean isPointContainedInBitmapViewer = p.x >= component.getLocationOnScreen().x &&
            p.x <= (component.getWidth() + component.getLocationOnScreen().x) &&
            p.y >= component.getLocationOnScreen().y &&
            p.y <= (component.getHeight() + component.getLocationOnScreen().y)

        if (!isPointContainedInBitmapViewer) {
          popup.setVisible(false)
        }
      }
    }
  }

  // ----UTIL METHODS

  public static boolean isWindowsOS() {
    String os = System.getProperty("os.name").toLowerCase()
    return os.startsWith("windows")
  }

  public static boolean isMacOS() {
    String os = System.getProperty("os.name").toLowerCase()
    return os.startsWith("mac os")
  }
}
