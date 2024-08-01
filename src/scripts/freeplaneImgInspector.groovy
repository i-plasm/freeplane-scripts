// @ExecutionModes({ON_SINGLE_NODE="/main_menu/i-plasm/miscUtils"})

package scripts

/*
 * Info & Discussion:
 *
 * Last Update: 2024-07-31
 *
 * ---------
 *
 * ImgInspector: Freeplane auto-launching popup tool that provides convenient image previewing
 * functionalities.
 *
 * Copyright (C) 2023-2024 bbarbosa
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
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import java.awt.event.AWTEventListener
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.util.function.Predicate
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.ToolTipManager
import javax.swing.UIManager
import javax.swing.border.EmptyBorder
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import org.freeplane.api.Node
import org.freeplane.features.mode.Controller
import org.freeplane.plugin.script.proxy.ScriptUtils
import org.freeplane.view.swing.features.filepreview.BitmapViewerComponent
import org.freeplane.view.swing.map.NodeView

new ImgInspector().init()

/**
 * Most classes used have been duplicated or derived from classes from the "jHelperUtils"
 * library
 *
 * Github: https://github.com/i-plasm/jhelperutils/
 *
 */
public class ImgInspector {

  private ImgAWTEventListener<BitmapViewerComponent> listener

  public void stop() {
    Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
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
      if (e.getID() == MouseEvent.MOUSE_PRESSED && isWindows() &&
          e.getSource().getClass().getName()
          .contains("javax.swing.PopupFactory\$MediumWeightPopup\$MediumWeightComponent") &&
          popup.isCurrentlyDisplayingPreviewTip()) {
        ViewerPopup.previewFullSize(popup.getCurrentImage(), popup.getImgBackground())
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
        if (popup.hookedComponent.getMouseListeners() != null) {
          for (MouseListener l : popup.hookedComponent.getMouseListeners()) {
            if (l instanceof ImageViewerListener) {
              popup.hookedComponent.removeMouseListener(l)
              break
            }
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
    private boolean isCurrentlyDisplayingPreviewTip = false
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
      btnPreview = new HoverButton("<html><p><font size=12>&#128269;</font></p></html>") {

            @Override
            public Point getToolTipLocation(MouseEvent event) {
              Point p = btnPreview.getLocationOnScreen()
              java.awt.MouseInfo.getPointerInfo().getLocation()

              SwingUtilities.convertPointFromScreen(p, btnPreview)

              JFrame dummyHiddenFrame = getDummyFrame()

              p = new Point((int) (p.x + btnPreview.getBounds().width - btnPreview.getBounds().width / 4), (int) (p.y +
                  btnPreview.getBounds().height / 2 - dummyHiddenFrame.getPreferredSize().height / 2))
              dummyHiddenFrame.dispose()
              return p
            }

            private JFrame getDummyFrame() {
              String previousImage = ViewerPopup.this.previousImage
              String currentImage = ViewerPopup.this.currentImage
              // if (dummyHiddenFrame != null && previousImage != null &&
              // currentImage.equals(previousImage)) {
              // return dummyHiddenFrame;
              // }
              ViewerPopup.this.previousImage = currentImage
              JLabel label = new JLabel(imageHTML(currentImage))
              JFrame dummyHiddenFrame = new JFrame()
              dummyHiddenFrame.add(label)
              dummyHiddenFrame.pack()
              return dummyHiddenFrame
            }
          }

      btnPreview.addActionListener{ l ->
        previewFullSize(getCurrentImage(), ViewerPopup.this.getImgBackground())
      }
      btnCopyPath.addActionListener{ l ->
        copyURL()
      }
      btnInfo.addActionListener{ l ->
        displayHelp()
      }
      final int defaultInitialDelay = ToolTipManager.sharedInstance().getInitialDelay()

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
                  !popup.getVisibleRect().contains(p) && !isCurrentlyDisplayingPreviewTip) {
                popup.setVisible(false)
              }
            }
          }

      btnPreview.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
              ToolTipManager.sharedInstance().setInitialDelay(0)
              btnPreview.setToolTipText(imageHTMLWithBgColor(ViewerPopup.this.getCurrentImage(),
                  ViewerPopup.this.getImgBackground()))
              isCurrentlyDisplayingPreviewTip = true
            }

            @Override
            public void mouseExited(MouseEvent e) {
              Point p = java.awt.MouseInfo.getPointerInfo().getLocation()
              SwingUtilities.convertPointFromScreen(p, btnPreview)
              if (btnPreview.getVisibleRect().contains(p)) {
                return
              }
              hidePreviewToolTip(defaultInitialDelay)
            }
          })

      btnInfo.addMouseListener(exitAdapter)
      btnCopyPath.addMouseListener(exitAdapter)

      addPopupMenuListener(new PopupMenuListener() {

            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
              if (isCurrentlyDisplayingPreviewTip) {
                hidePreviewToolTip(defaultInitialDelay)
              }
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
              hidePreviewToolTip(defaultInitialDelay)
            }
          })

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
        JOptionPane.showMessageDialog(null, "Failed to copy to clipboard", "",
            JOptionPane.ERROR_MESSAGE)
        return
      }
      StringSelection sel = new StringSelection(getCurrentImage())
      try {
        clipboard.setContents(sel, null)
        JOptionPane.showMessageDialog(null, "Copied to clipboard:\n" + getCurrentImage(), "",
            JOptionPane.INFORMATION_MESSAGE)
      } catch (Exception e) {
        JOptionPane.showMessageDialog(null, "Failed to copy to clipboard", "",
            JOptionPane.ERROR_MESSAGE)
        e.printStackTrace()
      }
    }

    private void displayHelp() {
      int result = JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(ViewerPopup.this),
          "Click 'OK' to learn more about this extension, and get updates on Github.",
          "Information & Help", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE)

      if (result == JOptionPane.OK_OPTION) {
      }
    }

    private void hidePreviewToolTip(final int defaultDismissTimeout) {
      btnPreview.setToolTipText("")
      ToolTipManager.sharedInstance().mouseMoved(
          new MouseEvent(btnPreview, -1, System.currentTimeMillis(), 0, 0, 0, 0, 0, 0, false, 0))
      ToolTipManager.sharedInstance().setInitialDelay(defaultDismissTimeout)
      isCurrentlyDisplayingPreviewTip = false
    }

    public void hookToComponent(Component component) {
      this.hookedComponent = component
      String compImg = getImageURI().toString()
      this.previousImage = previousImage == null ? compImg : currentImage
      this.currentImage = compImg
      this.imgBackground = getBackgroundColor()
      setVisible(false)
    }

    public boolean isCurrentlyDisplayingPreviewTip() {
      return isCurrentlyDisplayingPreviewTip
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
      Node node = ScriptUtils.node().getMap().node(nodeView.getModel().createID())
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

    static void previewFullSize(String imgUrl, String bgColor) {
      String html = imageHTMLWithBgColor(imgUrl, bgColor)
      JLabel label = new JLabel(html)
      JFrame frame
      frame = new JFrame()
      frame.setName(PREVIEW_COMP_NAME)
      frame.setLayout(new FlowLayout(FlowLayout.CENTER))
      frame.add(label)
      frame.pack()
      frame.setLocationRelativeTo(null)
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
    }
  }

  static class ImageViewerListener<T extends Component> extends MouseAdapter {

    T component
    Timer viewerTimer

    ActionListener timerAction
    private ViewerPopup popup

    public ImageViewerListener(T component, ViewerPopup popup, Predicate<T> isValidImage) {
      this.component = component
      this.popup = popup
      popup.hookToComponent(component)

      ActionListener timerAction = new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
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

      this.viewerTimer = new Timer(500, timerAction)

      for (MouseListener l : popup.getMouseListeners()) {

        if (PopupAdapter.class.isInstance(l)) {
          // l instanceof PopupAdapter
          popup.removeMouseListener(l)
          break
        }
      }
      popup.addMouseListener(new PopupAdapter())
    }

    @Override
    public void mouseExited(MouseEvent e) {
      if (viewerTimer != null && viewerTimer.isRunning()) {
        viewerTimer.stop()
      }

      Point p = java.awt.MouseInfo.getPointerInfo().getLocation()
      // SwingUtilities.convertPointFromScreen(p, popup);

      if (popup.isShowing()) {
        boolean isPointContainedInPopup = p.x >= popup.getLocationOnScreen().x &&
            p.x <= (popup.getWidth() + popup.getLocationOnScreen().x) &&
            p.y >= popup.getLocationOnScreen().y &&
            p.y <= (popup.getHeight() + popup.getLocationOnScreen().y)

        if (!isPointContainedInPopup && !popup.isCurrentlyDisplayingPreviewTip()) {
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
      if (popup.isShowing() || isAnotherWindowOnTop) {
        return
      }
      viewerTimer.start()
      viewerTimer.setRepeats(false)
    }

    class PopupAdapter extends MouseAdapter {
      @Override
      public void mouseExited(MouseEvent e) {
        Point p = java.awt.MouseInfo.getPointerInfo().getLocation()
        boolean isPointContainedInBitmapViewer = p.x >= component.getLocationOnScreen().x &&
            p.x <= (component.getWidth() + component.getLocationOnScreen().x) &&
            p.y >= component.getLocationOnScreen().y &&
            p.y <= (component.getHeight() + component.getLocationOnScreen().y)

        if (!isPointContainedInBitmapViewer && !popup.isCurrentlyDisplayingPreviewTip()) {
          popup.setVisible(false)
        }
      }
    }
  }
}
