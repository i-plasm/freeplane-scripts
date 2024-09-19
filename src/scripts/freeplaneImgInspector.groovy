// @ExecutionModes({ON_SINGLE_NODE="/main_menu/i-plasm/miscUtils"})

package scripts

/*
 * Info & Discussion: https://github.com/freeplane/freeplane/discussions/1948
 *
 * Last Update: 2024-09-18
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
import java.awt.GraphicsConfiguration
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
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.util.function.Predicate
import javax.swing.AbstractAction
import javax.swing.Action
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
  private static final String PLUGIN_VERSION = "v0.8.5"

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

    public ImgAWTEventListener(Class<T> clazz) {
      this.clazz = clazz
    }

    @Override
    public void eventDispatched(AWTEvent e) {
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

      if (popup.hookedComponent != null && popup.hookedComponent != component) {
        for (MouseListener l : popup.hookedComponent.getMouseListeners()) {
          if (l instanceof ImageViewerListener) {
            popup.hookedComponent.removeMouseListener(l)
            break
          }
        }
      } else if (popup.hookedComponent != null && popup.hookedComponent == component) {
        return
      }

      ImageViewerListener<BitmapViewerComponent> bitmapViewerlistener =
          new ImageViewerListener<BitmapViewerComponent>((BitmapViewerComponent) component, popup,
          getImgAvailablePredicate())
      component.addMouseListener(bitmapViewerlistener)
      bitmapViewerlistener.mouseEntered(
          new MouseEvent(component, -1, System.currentTimeMillis(), 0, 0, 0, 0, 0, 0, false, 0))
    }

    private Predicate<BitmapViewerComponent> getImgAvailablePredicate() {
      return {comp -> comp.getOriginalSize() != null}
    }

    private ViewerPopup createViewerPopup() {
      return new ViewerPopup()
    }

    public static boolean isWindows() {
      String os = System.getProperty("os.name").toLowerCase()
      return os.startsWith("windows")
    }
  }

  private static class ViewerPopup extends JPopupMenu {
    public static final String PREVIEW_COMP_NAME = "img_preview_hover"

    HoverButton btnPreview
    Component hookedComponent
    private String previousImage
    private String currentImage
    private String imgBackground

    private ViewerPopup() {
      GridBagConstraints c = new GridBagConstraints()
      this.setLayout(new GridBagLayout())

      // unicode info button
      HoverButton btnInfo = new HoverButton("<html><p><font size=12>&#128712;</font></p></html>")
      btnInfo.setToolTipText("Information & Help")
      // unicode opposition button
      HoverButton btnCopyPath = new HoverButton("<html><p><font size=12>&#9741;</font></p></html>")
      btnCopyPath.setToolTipText("Copy Image URL")

      // unicode Left-Pointing Magnifying Glass button
      btnPreview = new HoverButton("<html><p><font size=12>&#128269;</font></p></html>")

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
              JFrame hoverWindow = createHoverWindow(ViewerPopup.this.getCurrentImage(),
                  ViewerPopup.this.getImgBackground(), ViewerPopup.this.getSuggstedPreviewLocation(),
                  SwingUtilities.getWindowAncestor(ViewerPopup.this).getGraphicsConfiguration())
              hoverWindow.setVisible(true)
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

    static JFrame createHoverWindow(String imgUrl, String bgColor, Point suggestedLocation,
        GraphicsConfiguration config) {
      String html = imageHTMLWithBgColor(imgUrl, bgColor)
      JLabel label = new JLabel(html)
      JFrame frame
      JFrame dummyFrame = new JFrame(config)
      // frame.setName(PREVIEW_COMP_NAME);

      dummyFrame.setLayout(new FlowLayout(FlowLayout.CENTER))
      dummyFrame.add(label)
      dummyFrame.pack()
      Rectangle maxBounds = getMaxWindowBounds(config)
      Rectangle dummyFrameBounds = dummyFrame.getBounds()
      boolean isImgShowingFully = dummyFrame.getBounds().width < maxBounds.width && dummyFrame.getBounds().height < maxBounds.height
      dummyFrame.dispose()

      if (!isImgShowingFully) {
        frame = new JFrame(config)
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
                int width = frame.getWidth()
                int height = frame.getHeight()
                Point location = frame.getLocation()
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
                if (frame.isVisible()) {
                  frame.setVisible(false)
                  frame.dispose()
                }
              }
            })
        frame.addWindowFocusListener(new WindowFocusListener() {
              @Override
              public void windowLostFocus(WindowEvent e) {
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
        frame.setSize((int) maxBounds.width, (int) maxBounds.height)
        // frame.pack();
        // TODO groovy cast to int
        frame.setLocation((int) maxBounds.x, (int) maxBounds.y)
      } else {
        frame = new JFrame(config)
        // frame.setName(PREVIEW_COMP_NAME);
        frame.setUndecorated(true)
        frame.setLayout(new FlowLayout(FlowLayout.CENTER))
        frame.add(label)

        int x = maxBounds.x + maxBounds.width > suggestedLocation.x + dummyFrame.getWidth()
            ? suggestedLocation.x
            : maxBounds.x + maxBounds.width - dummyFrame.getWidth()
        int y = maxBounds.y + maxBounds.height > suggestedLocation.y + dummyFrame.getHeight()
            ? suggestedLocation.y
            : maxBounds.y + maxBounds.height - dummyFrame.getHeight()

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

  static class HoverButton extends JButton {
    public HoverButton(String text) {
      super(text)
      setBorderPainted(false)
      setBackground(UIManager.getColor("control"))
      setBorder(new EmptyBorder(5, 5, 5, 5))
      setFocusable(false)

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
    }
  }

  static class ImageViewerListener<T extends Component> extends MouseAdapter {

    private T component
    private Timer viewerTimer
    private ViewerPopup popup

    public ImageViewerListener(T component, ViewerPopup popup, Predicate<T> isValidImage) {
      this.component = component
      this.popup = popup
      popup.hookToComponent(component)
      this.viewerTimer = new Timer(200, null)

      ActionListener timerAction = new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
              viewerTimer.stop()
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
}
