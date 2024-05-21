// @ExecutionModes({ON_SINGLE_NODE="/main_menu/i-plasm/miscUtils"})
package scripts

/*
 * Info & Discussion: https://github.com/freeplane/freeplane/discussions/1534
 *
 * Last Update: 2024-05-20
 *
 * ---------
 *
 * IntelliFlow: Intelligent ('context-aware') menu acting like a services/command provider for
 * Freeplane.
 *
 * Copyright (C) 2023 bbarbosa
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

import java.awt.Color
import java.awt.Component
import java.awt.Desktop
import java.awt.EventQueue
import java.awt.Graphics2D
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.FocusListener
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.image.BufferedImage
import java.lang.reflect.Method
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import java.util.regex.Pattern
import java.util.stream.Stream
import javax.imageio.ImageIO
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JToolTip
import javax.swing.JViewport
import javax.swing.MenuElement
import javax.swing.MenuSelectionManager
import javax.swing.SwingUtilities
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.text.BadLocationException
import javax.swing.text.Element
import javax.swing.text.JTextComponent
import javax.swing.text.MutableAttributeSet
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.HTMLEditorKit
import org.freeplane.api.FreeplaneVersion
import org.freeplane.api.Node
import org.freeplane.core.resources.ResourceController
import org.freeplane.core.ui.components.UITools
import org.freeplane.core.util.Compat
import org.freeplane.core.util.HtmlUtils
import org.freeplane.core.util.Hyperlink
import org.freeplane.core.util.MenuUtils
import org.freeplane.features.filter.FilterController
import org.freeplane.features.link.LinkController
import org.freeplane.features.map.NodeModel
import org.freeplane.features.mode.Controller
import org.freeplane.features.mode.ModeController
import org.freeplane.features.url.FreeplaneUriConverter
import org.freeplane.features.url.NodeAndMapReference
import org.freeplane.features.url.UrlManager
import org.freeplane.main.application.Browser
import org.freeplane.n3.nanoxml.XMLException
import org.freeplane.n3.nanoxml.XMLParseException
import org.freeplane.plugin.script.proxy.ScriptUtils
import org.freeplane.view.swing.features.filepreview.ExternalResource
import org.freeplane.view.swing.features.filepreview.ViewerController
import org.freeplane.view.swing.map.MapView

new IntelliFlow().run()

public class IntelliFlow {

  private static boolean isFloatingMsg = false
  private static boolean isFloatingDialog = false

  private static FocusListener editorsFocusListener
  private static JTextComponent editor
  private static boolean isNodeCoreInlineEditor = false

  private static LaunchMode launchMode
  private static boolean userNodeToolTipsPref
  private static String userSelectionPref

  private static final String LOGGING_STRING = "INTELLIFLOW_: "
  private static final String THREAD_PREFIX = "INTELLIFLOW_"
  private static final String MENU_TEXT = "IntelliFlow"
  private static final String PLUGIN_NAME = "IntelliFlow"
  private static final String PLUGIN_VERSION = "v0.7.0"

  enum LaunchMode {
    ON_NODE, ON_TEXT_COMPONENT
  }

  private static final String NODE_MODE_DISPLAYSTRING = "Node"
  private static final String TEXT_MODE_DISPLAYSTRING = "Text"

  private static final String FREEPLANE_NODE_TOOLTIPS_PROPERTY = "show_node_tooltips"
  private static final String FREEPLANE_FOLD_ON_CLICK_INSIDE_PROPERTY = "fold_on_click_inside"
  private static final String FREEPLANE_SELECTION_METHOD_BY_CLICK = "selection_method_by_click"
  private static final String FREEPLANE_SELECTION_METHOD_PROPERTY = "selection_method"

  public static void run() {
    List<Object> launchEnvironment = determineLaunchModeAndComponent()
    launchMode = (LaunchMode) launchEnvironment.get(0)
    editor = (JTextComponent) launchEnvironment.get(1)
    launch(launchMode, editor)
  }

  public static void runDev() {
    if (!FreeplaneTools.getBooleanProperty(FREEPLANE_NODE_TOOLTIPS_PROPERTY))
      JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "Tooltips found to be disabled")
    run()
  }

  private static void launchInvokingLater(LaunchMode launchMode, JTextComponent editor) {
    SwingUtilities.invokeLater({
      launch(launchMode, editor)
    })
  }

  // @CompileStatic
  private static void launch(LaunchMode launchMode, JTextComponent editor) {

    ScriptUtils.c().setStatusInfo("Launching " + PLUGIN_NAME + ". Mode: " + launchMode.toString())
    JPopupMenu popup = new JPopupMenu()

    userNodeToolTipsPref = FreeplaneTools.getBooleanProperty(FREEPLANE_NODE_TOOLTIPS_PROPERTY)
    userSelectionPref= FreeplaneTools.getProperty(FREEPLANE_SELECTION_METHOD_PROPERTY)
    // Handling node core editor UI interactions
    if (launchMode == LaunchMode.ON_TEXT_COMPONENT) {
      isNodeCoreInlineEditor = editor.getClass().getName().contains("MTextController")

      // Dealing with node core focus listener when editing the node core
      if (isNodeCoreInlineEditor) {
        // popup.setFocusable(false)
        for (FocusListener l : editor.getFocusListeners()) {
          if (l.getClass().getName().contains("TextFieldListener")) {
            editorsFocusListener = l
            break
          }
        }

        uiChangeWhenOnTextComponentMode()
      }
    }
    // Handling Mode 'ON_NODE' UI interactions
    else {
      uiChangeWhenOnNodeMode()
    }

    // Handling UI interactions affecting all special modes
    if (isSpecialUiMode()) {
      uiChangeAllSpecialModes()
      popup.addPopupMenuListener(getMainPopupMenuListener())
      avoidLosingFocusAndAttemptActionByEnter(popup)
    }

    addMenuItems(popup)
    showPopupMenu(popup, launchMode)
    requestFocusIfNeeded(popup)
  }


  private static List<Object> determineLaunchModeAndComponent() {
    LaunchMode launchMode
    JTextComponent editor = null

    Component componentToEvaluate = SwingAwtTools.getFocusedComponent()
    Component keyboardFocusOwner =
        KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()

    componentToEvaluate = keyboardFocusOwner

    // --------- Determining the LaunchMode and the editor
    if (!(componentToEvaluate instanceof JTextComponent)) {
      launchMode = LaunchMode.ON_NODE

      Component focusedComponent =
          KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()
      String focusedComponentClass =
          focusedComponent == null ? "null" : focusedComponent.getClass().getName()

      if (focusedComponentClass.toLowerCase().contains("jrootpane")) {
        JOptionPane.showMessageDialog(null,
            "<html><body width='600px'; style=\"font-size: 13px\">" + "It seems you did not call " +
            PLUGIN_NAME + " from a shortcut." +
            "<br> 'TEXT' mode is not available in this case (unless you are on MacOS)." +
            "<br>It is strongly recommended that you call " + PLUGIN_NAME +
            " from a shortcut so that all modes are available, and for easier interaction." +
            "<br>To assign shortcuts in Freeplane go to ' <b>Tools -> Assign hot key</b> '" +
            "</body></html>")
      }
    } else if (SwingUtilities.getAncestorOfClass(JToolTip.class,
        componentToEvaluate) == null) {
      editor = (JTextComponent) componentToEvaluate
      launchMode = LaunchMode.ON_TEXT_COMPONENT
    } else {
      launchMode = LaunchMode.ON_NODE
    }

    List<Object> returnList = new ArrayList<>()
    returnList.add(launchMode)
    returnList.add(editor)

    return returnList
  }

  private static String getModeStringForDisplay() {
    if (launchMode == LaunchMode.ON_NODE)
      return NODE_MODE_DISPLAYSTRING
    else
      return TEXT_MODE_DISPLAYSTRING
  }

  private static void showPopupMenu(JPopupMenu popup, LaunchMode launchMode) {
    Rectangle location = null
    int x = 0
    int y = 0
    Component componentForEmbedding

    // Case Mode: ON_TEXT_COMPONENT
    if (launchMode == LaunchMode.ON_TEXT_COMPONENT) {
      componentForEmbedding = editor
      // Subcase: there exists a text selection
      if (editor.getSelectedText() != null) {
        int selectionEnd = editor.getSelectionEnd()
        try {
          location = editor.modelToView(selectionEnd)
          x = location.x
          y = location.y + location.height
        } catch (BadLocationException e) {
          // TODO Auto-generated catch block
          e.printStackTrace()
        }
        // Subcase: no text selection
      } else {
        location = editor.getBounds()
        int caretPos = editor.getCaretPosition()
        x = location.x
        y = location.y

        Rectangle boundsCaret = null
        try {
          boundsCaret = editor.modelToView(editor.getCaretPosition())
          x = boundsCaret.x
          y = boundsCaret.y + boundsCaret.height
        } catch (BadLocationException e1) {
          e1.printStackTrace()
        }
        // FIX: Subcase: caret is visible
        if (editor.isEditable() & location.contains(boundsCaret)) {
          location = boundsCaret
          x = location.x
          y = location.y + location.height
        }
      }
      // Small offset. Useful for displaying in relation to text selection or caret position
      if (x - 10 >= 0)
        x = x - 10
      if (y - 10 >= 0)
        y = y - 10
      // Case Mode: ON_NODE
    } else {
      JViewport mapViewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class,
          FreeplaneTools.getMapView())
      location = mapViewport.getBounds()
      componentForEmbedding = mapViewport
      x = (int) (location.width / 2 - popup.getPreferredSize().getWidth() / 2)
      y = (int) (location.height / 2 - popup.getPreferredSize().getHeight() / 2)
    }
    // ScriptUtils.c().setStatusInfo(componentForEmbedding.getClass().getName());
    popup.show(componentForEmbedding, x, y)
  }

  private static void addMenuItems(JPopupMenu popup) {

    JMenuItem menuItemSmartOpen =
        makeMenuItemWithPrevalidation("SmartOpen " + getModeStringForDisplay().toLowerCase(),
        "smartopen", {
          smartOpen(editor)
        }, {
          prevalidateSmartOpen(editor)
        })
    if (menuItemSmartOpen != null) {
      popup.add(menuItemSmartOpen)
    }

    // -------- Web Search Menu ---------

    JMenu websearchMenu = new JMenu("Web Search")
    popup.add(websearchMenu)

    JMenuItem menuItemSearchInGoogle =
        makeStandardMenuItem("Search " + getModeStringForDisplay().toLowerCase() + " in Google",
        "google", {
          new SearchEngineQuery("https://www.google.com/search?q=", launchMode)
              .performSearch()
        })// searchInBing(editor)
    websearchMenu.add(menuItemSearchInGoogle)

    JMenuItem menuItemSearchInBing = makeStandardMenuItem(
        "Search " + getModeStringForDisplay().toLowerCase() + " in Bing", "bing", {
          new SearchEngineQuery("https://www.bing.com/search?q=", launchMode).performSearch()
        })
    websearchMenu.add(menuItemSearchInBing)

    popup.addSeparator()

    // --------- End Web Search Menu -------

    // --------- Sketcher Menu --------

    MenuHelper.DynamicMenu sketcherMenu =
        new MenuHelper.DynamicMenu("Sketcher", {
          SketcherUtility.getAssociatedFilesMenu()
        }, true)
    popup.add(sketcherMenu)

    JMenuItem menuItemSketcherCommand =
        makeMenuItemWithPrevalidation("Sketch drawing on node " ,
        "sketcher", {
          SketcherUtility.run()
        }, {
          SketcherUtility.prevalidateSketcher()
        })
    if (menuItemSketcherCommand != null) {
      sketcherMenu.add(menuItemSketcherCommand)
    }

    JMenuItem mainOrFirstTemplateMenu = SketcherUtility.getMainOrFirstTemplate()
    if (mainOrFirstTemplateMenu != null) {
      sketcherMenu.add(mainOrFirstTemplateMenu)
    }

    MenuHelper.DynamicMenu allTemplatesMenu = new MenuHelper.DynamicMenu("All templates", {
      SketcherUtility.getUserTemplatesMenu()
    }, false)
    allTemplatesMenu.addDynamicItems()
    sketcherMenu.add(allTemplatesMenu)

    sketcherMenu.addSeparator()

    JMenuItem menuItemSketcherRefreshImage = makeStandardMenuItem(
        "Refresh " + NODE_MODE_DISPLAYSTRING.toLowerCase() + " image", "sketcher_refresh_image", {
          SketcherUtility.refreshImageScheduled(ScriptUtils.c().getSelected())
        })
    sketcherMenu.add(menuItemSketcherRefreshImage)

    JMenuItem menuItemSketcherOpenContainingFolder = makeStandardMenuItem("Open image folder",
        "sketcher_openfolder", {
          SketcherUtility.sketcherOpenFolder()
        })
    sketcherMenu.add(menuItemSketcherOpenContainingFolder)

    JMenuItem menuItemSketcherConfigure = makeStandardMenuItem("Configuration",
        "sketcher_configure", {
          SketcherUtility.sketcherConfigure()
        })
    sketcherMenu.add(menuItemSketcherConfigure)

    JMenuItem menuItemSketcherHelp =
        makeStandardMenuItem("Help", "sketcherhelp", {
          SketcherUtility.displaySketcherHelp()
        })
    sketcherMenu.add(menuItemSketcherHelp)

    sketcherMenu.addDynamicItems()

    // --------- end Sketcher Menu ---------

    JMenuItem menuItemBreadCrumb =
        makeStandardMenuItem("Breadcrumb " + NODE_MODE_DISPLAYSTRING.toLowerCase(), "breadcrumb", {
          viewSelectedNodesAsBreadcrumb(true)
        })
    popup.add(menuItemBreadCrumb)

    JMenuItem menuItemFind = makeMenuItemThatExecutesFreeplaneCommand("Find in Map", "find",
        "NodeListAction", "Find in map", SwingAwtTools.getTrimmedSelectedText(editor))
    popup.add(menuItemFind)

    // -------- Help & Config ---------

    popup.addSeparator()

    JMenu helpMenu = new JMenu("Help & Config")
    popup.add(helpMenu)

    JMenuItem menuItemHelp = makeStandardMenuItem(MENU_TEXT + " help",
        MENU_TEXT.toLowerCase() + "help", {
          displayHelp()
        })
    helpMenu.add(menuItemHelp)

    helpMenu.addSeparator()

    JMenuItem menuItemOpenResourceHelp =
        makeStandardMenuItem("SmartOpen help", "smartopenhelp", {
          displaySmartOpenHelp()
        })
    helpMenu.add(menuItemOpenResourceHelp)

    // --------- end Help ---------

    popup.addSeparator()

    if (FreeplaneTools.getBooleanProperty(FREEPLANE_FOLD_ON_CLICK_INSIDE_PROPERTY)) {
      String message = "<html><body style=\"font-size: 12px\">" +
          "&#x2713;&nbsp;Pending Tasks&nbsp;(1)&nbsp;" + "</body></html>"
      JMenuItem menuItemPendingTasks =
          MenuHelper.makeMenuItem(message, "pendingtasks", {
            displayPendingTasks()
          })
      popup.add(menuItemPendingTasks)
    }

    String modeDisplay = launchMode == LaunchMode.ON_NODE ? "NODE" : "TEXT"
    String footer = "<b><i>" + MENU_TEXT + "</i>" + "" + "  ::" + modeDisplay + "::</b>"
    String message = "<html><body style=\"font-size: 12px\">" + footer + "</body></html>"
    JMenuItem menuItemReadThis = MenuHelper.makeMenuItem(message, "readthis", {
      displayAbout()
    })
    popup.add(menuItemReadThis)
  }

  static class SearchEngineQuery {
    String simpleQuery
    String websiteSearchPrefix
    LaunchMode launchMode

    SearchEngineQuery(String websiteSearchPrefix, LaunchMode launchMode) {
      this.websiteSearchPrefix = websiteSearchPrefix
      this.launchMode = launchMode
      if (launchMode == LaunchMode.ON_NODE) {
        this.simpleQuery =
            TextUtils.removeLineBreaks(ScriptUtils.c().getSelected().getPlainText().trim())
      } else {
        this.simpleQuery = SwingAwtTools.getTrimmedSelectedText(editor)
      }

      simpleQuery = simpleQuery.replace('#', ' ').replace(" -", " ")
    }

    void performSearch() {
      String diagnosis = ""

      if (simpleQuery.equals("")) {
        diagnosis = "There is no text to search or you haven't made any text selection."
      }

      if (launchMode == LaunchMode.ON_NODE && ScriptUtils.c().getSelecteds().size() > 1) {
        diagnosis = "There is no text to search because there is more than one node selected."
      }

      if (!diagnosis.equals("")) {
        int n = JOptionPane.showConfirmDialog(UITools.getCurrentFrame(),
            diagnosis + " Do you want to launch the search website anyway?", "Web search",
            JOptionPane.YES_NO_OPTION)
        if (n != JOptionPane.YES_OPTION) {
          return
        } else {
          simpleQuery = ""
        }
      }

      genericWebSearch(simpleQuery, websiteSearchPrefix)
    }

    public static void genericWebSearch(String query, String websiteSearchPrefix) {
      String feedbackMessage = "The requested query (" + query + ") couldn't be processed."
      FreeplaneTools.visitWebsite(websiteSearchPrefix + query, feedbackMessage)
    }
  }

  private static void displayHelp() {
    displayAbout()
  }

  private static void displayPendingTasks() {
    MenuHelper.FloatingMsgPopup floatingPopup = new MenuHelper.FloatingMsgPopup()

    JMenuItem contentsMenuItem = MenuHelper.createHeaderNoticeMenuItem("<b>" + PLUGIN_NAME +
        " detected that the '<i>Fold on click inside</i>' Freeplane preference is activated. It is strongly suggested that you " +
        "disable that property. You can disable it by clicking on the option below ('Disable Fold on click inside'). Alternatively, you can do it" +
        " manually at <i>' Preferences -> Behaviour -> Fold on click inside '</i>.</b>",
        "Pending Tasks - " + PLUGIN_NAME)
    floatingPopup.add(contentsMenuItem)

    // String disableFoldOnClick =
    // floatingMenuItemUnderlinedActionHTML("Disable 'Fold on click inside'",
    // "Click here to disable Freeplane's property 'Fold on click inside'");
    JPanel disableFoldOnClickItem = MenuHelper.createFloatingActionPanel(
        "Click to disable the preference:", "", "Disable 'Fold on click inside'", {
          FreeplaneTools.setProperty(FREEPLANE_FOLD_ON_CLICK_INSIDE_PROPERTY, false)
        })
    floatingPopup.add(disableFoldOnClickItem)

    contentsMenuItem.addActionListener{ l ->
      launchInvokingLater(launchMode, editor)
    }
    // disableFoldOnClickItem.addActionListener(l -> launchInvokingLater(launchMode, editor));

    configureFloatingPopup(floatingPopup, launchMode, editor)
    showPopupMenu(floatingPopup, launchMode)
    requestFocusIfNeeded(floatingPopup)
  }

  private static void displayAbout() {
    MenuHelper.FloatingMsgPopup floatingPopup = new MenuHelper.FloatingMsgPopup()

    JMenuItem contentsMenuItem = MenuHelper.createHeaderNoticeMenuItem("<b>" + PLUGIN_NAME +
        "</b>" + "<br><i>version " + PLUGIN_VERSION + "</i><br><br>" +
        "Intelligent ('context-aware') menu acting like a services/command provider that aims to make Freeplane workflows faster and more flexible." +
        "<br><br>" + "Except on MacOS, " + PLUGIN_NAME +
        " must be called using a shortcut in order to have all modes available." +
        " Set it on Freeplane's menu ' Tools -> Assign hot key.' " +
        "<br>Your feedback is welcome! Check the linked references for more information on usage and discussion.",
        "About " + PLUGIN_NAME)
    floatingPopup.add(contentsMenuItem)

    String visitWebsite = MenuHelper.floatingMenuItemUnderlinedActionHTML("Discusion & Info:",
        "https://github.com/freeplane/freeplane/discussions/1534")
    JMenuItem visitWebsiteItem =
        createLinkMenuItem("https://github.com/freeplane/freeplane/discussions/1534", visitWebsite)
    floatingPopup.add(visitWebsiteItem)

    String updatesWebsite = MenuHelper.floatingMenuItemUnderlinedActionHTML("Updates:",
        "https://github.com/i-plasm/freeplane-scripts/wiki/IntelliFlow-Updates")
    JMenuItem updatesItem =
        createLinkMenuItem("https://github.com/i-plasm/freeplane-scripts/wiki/IntelliFlow-Updates", updatesWebsite)
    floatingPopup.add(updatesItem)

    JMenuItem licenseMenuItem = MenuHelper.createContentNoticeMenuItem(PLUGIN_NAME + " " +
        PLUGIN_VERSION +
        " - Intelligent ('context-aware') menu acting like a services/command provider for Freeplane." +
        "<br><br>Copyright (C) 2023 bbarbosa" +
        "<br>This program is free software: you can redistribute it and/or modify it under the terms of the" +
        " GNU General Public License as published by the Free Software Foundation, either version 3 of the" +
        " License, or (at your option) any later version." +
        "<br>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without" +
        " even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU" +
        " General Public License for more details." +
        "<br>You should have received a copy of the GNU General Public License along with this program. If" +
        " not, see &lt;https://www.gnu.org/licenses/&gt;.", "License")
    floatingPopup.add(licenseMenuItem)

    contentsMenuItem.addActionListener{ l ->
      launch(launchMode, editor)
    }
    visitWebsiteItem.addActionListener{ l ->
      launch(launchMode, editor)
    }
    updatesItem.addActionListener{ l ->
      launch(launchMode, editor)
    }
    licenseMenuItem.addActionListener{ l ->
      launch(launchMode, editor)
    }

    configureFloatingPopup(floatingPopup, launchMode, editor)
    showPopupMenu(floatingPopup, launchMode)
    requestFocusIfNeeded(floatingPopup)
  }

  private static void viewSelectedNodesAsBreadcrumb(boolean createNewMapView) {
    if (ScriptUtils.c().getSelecteds().size() > 1) {
      JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
          "You have selected multiple nodes. Currently, it is only possible to breadcrumb a single node.")
      return
    }

    if (createNewMapView)
      FreeplaneTools.executeMenuItem("NewMapViewAction")

    EventQueue.invokeLater(new Runnable() {
          @Override
          public void run() {
            if (!FilterController.getCurrentFilterController().getShowAncestors().isSelected()) {
              FreeplaneTools.executeMenuItem("ShowAncestorsAction")
            }
          }
        })

    EventQueue.invokeLater(new Runnable() {

          @Override
          public void run() {
            if (!FilterController.getCurrentFilterController().getShowDescendants().isSelected()) {
              FreeplaneTools.executeMenuItem("ShowDescendantsAction")
            }
          }
        })

    FreeplaneTools.executeMenuItemLater("ApplySelectedViewConditionAction")
    FreeplaneTools.executeMenuItemLater("MoveSelectedNodeAction.CENTER")
  }

  private static void displaySmartOpenHelp() {
    MenuHelper.FloatingMsgPopup floatingPopup = new MenuHelper.FloatingMsgPopup()

    JMenuItem contentsMenuItem = MenuHelper.createHeaderNoticeMenuItem("<b>SmartOpen</b>" +
        "<br><i>IntelliFlow version</i><br><br>" +
        "<p>Smart and easy detection and browsing of resources: file paths, app/web URIs, and Freeplane nodes.</p>" +
        "<ul> <li> 'NODE' mode: if the node has a link property, it will browse/open it. </li> " +
        "<li> 'TEXT' mode: the clicked or selected text will be opened (if it exists). It can be anything (except executables) - a website, a URI, a file, a node ID. " +
        "<ul> <li> Ease of use. In general, you don't need to select your target. Just click anywhere on it, and it will be detected. Exception: if your target is plain text containing whitespaces, you need to select it. </li> " +
        "<li> Files. It can process both relative and absolute paths. </li> " +
        "<li> URIs. It can process URIs, both relative and absolute. </li> <li> Jump to nodes. It understands node " +
        "IDs and &quot;jumps&quot; to the node. </li> " +
        "<li> Intelligent detection. It can usually detect path/uri/nodeID even if it is not an HTML hyperlink, but just plain text. </li> </ul> </li> </ul>",
        "SmartOpen Help")
    floatingPopup.add(contentsMenuItem)

    String visitWebsite = MenuHelper.floatingMenuItemUnderlinedActionHTML("Discusion & Updates:",
        "https://github.com/freeplane/freeplane/discussions/1534")
    JMenuItem visitWebsiteItem =
        createLinkMenuItem("https://github.com/freeplane/freeplane/discussions/1534", visitWebsite)
    floatingPopup.add(visitWebsiteItem)

    contentsMenuItem.addActionListener{ l ->
      launch(launchMode, editor)
    }
    visitWebsiteItem.addActionListener{ l ->
      launch(launchMode, editor)
    }

    configureFloatingPopup(floatingPopup, launchMode, editor)
    showPopupMenu(floatingPopup, launchMode)
    requestFocusIfNeeded(floatingPopup)
  }

  private static String prevalidateSmartOpen(JTextComponent editor) {
    final String prefix = "Go to: "

    if (launchMode == LaunchMode.ON_NODE) {
      List<? extends Node> selecteds = ScriptUtils.c().getSelecteds()
      if (selecteds.size() == 1 && selecteds.get(0).getLink().getText() != null) {
        return prefix + selecteds.get(0).getLink().getText().trim()
      } else {
        return null
      }
    }

    String htmlURL = editor.getDocument() instanceof HTMLDocument
        ? SwingAwtTools.detectURLOnHTMLDocument((JEditorPane) editor)
        : ""
    // Case: html reference is defined in HTMLDocument
    if (!htmlURL.equals("")) {
      return prefix + htmlURL
    }

    String docText = ""
    try {
      docText = editor.getDocument().getText(0, editor.getDocument().getLength())
    } catch (BadLocationException e) {
      // TODO Auto-generated catch block
      e.printStackTrace()
    }

    String suggestedResource = editor.getSelectedText() == null
        ? SwingAwtTools.autodetectLinkFromNeighbor(editor.getCaretPosition(), docText)
        : editor.getSelectedText().trim()
    if (suggestedResource != null && !TextUtils.containsLineBreaks(suggestedResource)) {
      // Case: Freeplane node ID
      if (FreeplaneTools.isNodeID(suggestedResource)) {
        Node node = FreeplaneTools.getNodeById(suggestedResource)
        String nodeText = ""
        if (node != null) {
          nodeText = node.getPlainText().replaceAll("\\v+|\\s+", " ")
        } else {
        }
        return "To node: " + nodeText
      }

      // Case : rough attempt to detect a whitespace-free file name or url has been selected
      if (!TextUtils.containsWhiteSpaces(suggestedResource) && suggestedResource.contains(".")
          && suggestedResource.indexOf(".") != suggestedResource.length() - 1) {
        return prefix + suggestedResource
      }

      // Case: selected text contains any special character defined in the set, which may point to
      // a path/url
      Set<String> specialPathOrURICharacters = new HashSet<>(Arrays.asList("\\", "/", ":"))
      Set<String> selectedCharsAsSet = new HashSet<>()
      for (char i : suggestedResource.toCharArray()) {
        selectedCharsAsSet.add(Character.toString(i))
      }
      if (!Collections.disjoint(specialPathOrURICharacters, selectedCharsAsSet)) {
        if (!suggestedResource.endsWith(":")) {
          return prefix + suggestedResource
        }
      }
    }

    return ""
  }

  private static void smartOpen(JTextComponent editor) {
    String urlOrPath = ""

    // Case mode: 'ON_NODE'
    if (launchMode == LaunchMode.ON_NODE) {
      List<? extends Node> selecteds = ScriptUtils.c().getSelecteds()
      if (selecteds.size() > 1) {
        JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
            "There is more than one node selected. Please select only one node and try again.")
        return
      } else {
        urlOrPath = selecteds.get(0).getLink().getText()
        if (urlOrPath.trim().equals("")) {
          JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
              "There is more than one node selected. Please select only one node and try again.")
          return
        }
      }
      // Case mode: 'ON_TEXT_COMPONENT'
    } else {
      try {
        urlOrPath = SwingAwtTools.detectPossibleURLorPathOnEditor((JEditorPane) editor)
      } catch (IllegalArgumentException e) {
        displayFloatingInformationMessage(
            "No suggestion for a browsable resource (path/uri) could be found. If you have made a" +
            " text selection instead of a click, please make sure it does not contain line-breaks." +
            " (" + e.getMessage() + " )")
        return
      }
      // Logic to be applied when there is no explicit text selection. Show confirmation message
      // when there is no underlying html link and when the urlOrPath value is not a local node ID.
      // Display the suggested auto-detected target/urlOrPath to the user.
      if (!FreeplaneTools.isNodeID(urlOrPath) && editor.getSelectedText() == null
          && (!(editor.getDocument() instanceof HTMLDocument)
          || (editor.getDocument() instanceof HTMLDocument
          && SwingAwtTools.detectURLOnHTMLDocument((JEditorPane) editor).equals("")))) {

        String message = "<html><body width='600px'; style=\"font-size: 12px\">" +
            "You did not make a explicit selection and the detected position does not define a html hyperlink or a local node ID. Would you like to try to open this potential resource? " +
            "<br><br>" + "Open:<i> " + HtmlUtils.toXMLEscapedText(urlOrPath) + "</i>" +
            "</body></html>"
        int n = JOptionPane.showConfirmDialog(UITools.getCurrentFrame(), message,
            "Confirmation SmartOpen Resource", JOptionPane.YES_NO_OPTION)
        if (n != JOptionPane.YES_OPTION || urlOrPath.equals("")) {
          return
        }
      }
    }

    String feedbackMessage = "It was not possible to locate or resolve the resource."
    if (launchMode != LaunchMode.ON_NODE) {
      feedbackMessage = feedbackMessage +
          " If your resource path/uri contains whitespaces, make sure you first select the whole path/uri."
    }
    FreeplaneTools.openResource(urlOrPath, feedbackMessage)
  }

  /**
   * Adding a listener to the JPopupMenu that consumes all keys except the ones to operate on the
   * popup menu itself: VK_UP, VK_DOWN, VK_ENTER, VK_ESCAPE, etc. This avoids the user switching
   * (via the keyboard) the focus to a component other than the the popup menu and any derived
   * component. This is especially important for use on in-line node core editor or 'ON_NODE' mode,
   * where focus actions are important for correct interaction of the popup and the Freeplane UI.
   * (For instance, with incorrect focus handling, the user might become confused as to whether
   * Freeplane vs. popup menu and its components are focused and might type commands to the wrong
   * target
   */
  private static void avoidLosingFocusAndAttemptActionByEnter(JPopupMenu popup) {
    popup.addKeyListener(new KeyListener() {

          @Override
          public void keyTyped(KeyEvent e) {}

          @Override
          public void keyReleased(KeyEvent e) {}

          @Override
          public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() != KeyEvent.VK_UP && e.getKeyCode() != KeyEvent.VK_DOWN
                && !e.getKeyText(e.getKeyCode()).toLowerCase().equals("enter")
                && e.getKeyCode() != KeyEvent.VK_ESCAPE && e.getKeyCode() != KeyEvent.VK_RIGHT
                && e.getKeyCode() != KeyEvent.VK_LEFT) {
              e.consume()
            }

            if (e.getKeyText(e.getKeyCode()).toLowerCase().equals("enter")) {
              MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath()
              if (!(path[path.length - 1] instanceof JMenuItem)) {
                return
              }
              JMenuItem item = (JMenuItem) path[path.length - 1]
              MenuSelectionManager.defaultManager().clearSelectedPath()
              item.doClick()
            }
          }
        })
  }

  private static PopupMenuListener getMainPopupMenuListener() {
    return new PopupMenuListener() {

          // Restoring Freeplane UI interaction when the main popup is exited
          @Override
          public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            SwingUtilities.invokeLater(new Runnable() {

                  @Override
                  public void run() {
                    if (isFloatingMsg || isFloatingDialog) {
                      return
                    }
                    restoreUiChanges()
                  }
                })
          }

          // Restoring Freeplane UI interaction when the main popup is canceled
          @Override
          public void popupMenuCanceled(PopupMenuEvent e) {
            restoreUiChanges()
          }

          @Override
          public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}
        }
  }

  private static PopupMenuListener getFloatingPopupMenuListener(LaunchMode launchMode,
      JTextComponent editor) {
    return new PopupMenuListener() {
          // Restoring Freeplane UI interaction when the main popup is exited
          @Override
          public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            isFloatingMsg = false
            restoreUiChanges()
          }

          // Restoring Freeplane UI interaction when the main popup is canceled
          @Override
          public void popupMenuCanceled(PopupMenuEvent e) {
            isFloatingMsg = false
            restoreUiChanges()
          }

          @Override
          public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}
        }
  }

  private static void uiChangeWhenOnTextComponentMode() {
    if (!isSpecialUiMode())
      throw new AssertionError()
    // Temporarily removing the editor focusListener of type 'TextFieldListener' so that the
    // plugin popup won't trigger the stopping of in-line editing mode.
    editor.removeFocusListener(editorsFocusListener)
  }

  private static void uiChangeAllSpecialModes() {
    if (!isSpecialUiMode())
      throw new AssertionError()
    FreeplaneTools.setProperty(FREEPLANE_NODE_TOOLTIPS_PROPERTY, false)
  }

  private static void uiChangeWhenOnNodeMode() {
    if (!isSpecialUiMode())
      throw new AssertionError()
    FreeplaneTools.setProperty(FREEPLANE_SELECTION_METHOD_PROPERTY, FREEPLANE_SELECTION_METHOD_BY_CLICK)
  }

  private static void restoreUiChanges() {
    if (!isSpecialUiMode())
      throw new AssertionError()

    uiRestorationAllSpecialModes()

    if (launchMode == LaunchMode.ON_NODE) {
      uiRestorationWhenOnNodeMode()
      return
    }

    if (launchMode == LaunchMode.ON_TEXT_COMPONENT) {
      uiRestorationWhenOnTextComponentMode()
      return
    }
  }

  private static void uiRestorationWhenOnTextComponentMode() {
    if (!isSpecialUiMode())
      throw new AssertionError()
    if (!Arrays.asList(editor.getFocusListeners()).contains(editorsFocusListener)) {
      editor.addFocusListener(editorsFocusListener)
    }
  }

  private static void uiRestorationWhenOnNodeMode() {
    if (!isSpecialUiMode())
      throw new AssertionError()
    if (!userSelectionPref.equals(FREEPLANE_SELECTION_METHOD_BY_CLICK)) {
      FreeplaneTools.setProperty(FREEPLANE_SELECTION_METHOD_PROPERTY, userSelectionPref)
    }
  }

  private static void uiRestorationAllSpecialModes() {
    if (!isSpecialUiMode())
      throw new AssertionError()
    if (userNodeToolTipsPref
        && !FreeplaneTools.getBooleanProperty(FREEPLANE_NODE_TOOLTIPS_PROPERTY)) {
      FreeplaneTools.setProperty(FREEPLANE_NODE_TOOLTIPS_PROPERTY, true)
    }
  }

  private static boolean isSpecialUiMode() {
    if (isNodeCoreInlineEditor || launchMode == LaunchMode.ON_NODE) {
      return true
    }
    return false
  }

  private static void requestFocusIfNeeded(JPopupMenu popup) {
    // Request focus needed so that the avoidLosingFocusAndAttemptActionByEnter(JPopupMenu popup)
    // method can work when on Node core in-line editor or 'ON_NODE' mode
    if (isSpecialUiMode()) {
      popup.requestFocusInWindow()
    }
  }

  private static void configureFloatingPopup(JPopupMenu floatingPopup, LaunchMode launchMode,
      JTextComponent editor) {
    if (isSpecialUiMode()) {
      floatingPopup.addPopupMenuListener(getFloatingPopupMenuListener(launchMode, editor))
      isFloatingMsg = true
      avoidLosingFocusAndAttemptActionByEnter(floatingPopup)
    }
  }

  private static JMenuItem makeStandardMenuItem(String text, String name, Runnable doThis) {
    return MenuHelper.makeMenuItem(text, name, doThis, 40)
  }

  private static JMenuItem makeMenuItemThatExecutesFreeplaneCommand(String text, String name,
      String menuCommand, String description, String inputTextToInsertInJComponent) {

    Runnable runnable = new Runnable() {
          @Override
          public void run() {
            FreeplaneTools.executeMenuItemLater(menuCommand)
          }
        }

    return makeStandardMenuItem(text, name, runnable)
  }

  private static JMenuItem createLinkMenuItem(String url, String menuText) {
    String feedbackMessage = "It was not possible to locate or resolve the linked resource. " +
        System.lineSeparator() + url
    return createHTMLMenuItem(menuText, "", {
      FreeplaneTools.visitWebsite(url, feedbackMessage)
    })
  }

  private static JMenuItem createHTMLMenuItem(String menuText, String name, Runnable runnable) {
    return MenuHelper.makeMenuItem(menuText, "", runnable)
  }

  private static JMenuItem makeMenuItemWithPrevalidation(String defaultMenuItemText, String name,
      Runnable doThis, Callable<String> prevalidator) {
    String validation =
        MenuHelper.getMenuItemValidation(defaultMenuItemText, name, doThis, prevalidator)
    return validation == null ? null : IntelliFlow.makeStandardMenuItem(validation, name, doThis)
  }

  private static void displayFloatingInformationMessage(String htmlMessage) {
    MenuHelper.FloatingMsgPopup floatingPopup = new MenuHelper.FloatingMsgPopup()

    JMenuItem contentsMenuItem =
        MenuHelper.createHeaderNoticeMenuItem(htmlMessage, "Information Message - " + PLUGIN_NAME)
    floatingPopup.add(contentsMenuItem)

    contentsMenuItem.addActionListener{ l ->
      launch(launchMode, editor)
    }

    configureFloatingPopup(floatingPopup, launchMode, editor)
    showPopupMenu(floatingPopup, launchMode)
    requestFocusIfNeeded(floatingPopup)
  }

  /**
   * Sketcher service bundle/add-on class
   */
  static class SketcherUtility {
    private static String editorBinary = ""
    private static final String DEFAULT_VIEWER_ASSOCIATED_KEY = "default"
    private static final String SKETCHER_CONFIG_FILE = "sketcher-service-config.txt"
    private static final String SKETCHER_LOGGING_STRING = "SKETCHER: "
    private static final String INITIAL_SUGGESTED_VIEWER_WINDOWS = "mspaint"
    private static final String INITIAL_SUGGESTED_VIEWER_LINUX = "drawing"

    private static final String MAIN_TEMPLATE = "main_template"
    private static final String TEMPLATE = "template"
    // public static boolean shouldMonitorExtProcess = false;

    private static String getUserDefinedEditorBinary() throws IOException {
      String userDir = FreeplaneTools.getFreeplaneUserdir()
      String editorBinary = ""
      List<String> lines =
          Files.readAllLines(Paths.get(userDir, "scripts", SKETCHER_CONFIG_FILE))
      editorBinary =
          lines.stream().filter{ it ->
            !it.trim().equals("") && !it.trim().startsWith("#") && !it.contains(TEMPLATE + "=")
          }.findFirst().orElse("").trim()
      return editorBinary.equalsIgnoreCase(DEFAULT_VIEWER_ASSOCIATED_KEY) ? "" : editorBinary
    }

    public static void run() {
      run("")
    }

    public static void run(String templatePath) {
      try {
        editorBinary = getUserDefinedEditorBinary()
      } catch (IOException e1) {
        if (Compat.isWindowsOS()) {
          editorBinary = INITIAL_SUGGESTED_VIEWER_WINDOWS
        } else if (!Compat.isMacOsX()) {
          editorBinary = INITIAL_SUGGESTED_VIEWER_LINUX
        }
        // TODO refactor path
        Path path = Paths.get(FreeplaneTools.getFreeplaneUserdir(), "scripts", SKETCHER_CONFIG_FILE)
        if (Files.exists(path)) {
          String errMsg =
              "ERROR: The Sketcher configuration file could not be read. Defaulting to a suggested viewer. The reason could be related to script permissions. " +"\n" +
              "Please check your Tools -> Preferences -> Plug-ins section, and make sure scripts have sufficient permissions. " +"\n" +
              "Another reason may be the folder where the image is to be created doesn't have read permissions - in that case you'd have to modify folder permissions, " +"\n" +
              "or move your mindmap to a folder that does have enough permissions." +
              "\n" + "MESSAGE: " + e1.getMessage()
          JOptionPane.showMessageDialog(UITools.getCurrentFrame(), errMsg,
              "Configuration file error", JOptionPane.ERROR_MESSAGE)
          System.err.println(SKETCHER_LOGGING_STRING + errMsg)
        }
      }
      File imageFile = null

      File mapFile = Controller.getCurrentController().getMap().getFile()
      if (mapFile == null) {
        JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
            "It seems the currently focused map has never been saved. Please save it in order to use Sketcher.")
        return
      }

      if (ScriptUtils.c().getSelecteds().size() > 1) {
        JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
            "It seems you have more than one node selected. Please select only one in order to use Sketcher.")
        return
      }


      File mapDir = mapFile.getParentFile()
      String mapName = Controller.getCurrentController().getMap().getFile().getName().trim()
      String suffix = mapName.substring(mapName.length() - ".mm".length(), mapName.length())
      if (suffix.equals(".mm")) {
        mapName = mapName.substring(0, mapName.length() - ".mm".length())
      }

      Path imageDir = null
      try {
        imageDir =
            Files.createDirectories(Paths.get(mapDir.toPath().toString(), mapName + "_files"))
      } catch (IOException e) {
        e.printStackTrace()
      }

      Node node = ScriptUtils.c().getSelected()
      NodeModel selectedNodeModel =
          Controller.getCurrentModeController().getMapController().getSelectedNode()
      try {

        if (getExternalResource(selectedNodeModel) == null) {
          if (!templatePath.isBlank()) {
            imageFile = createCopyFromTemplate(templatePath, imageDir.toFile()).toFile()
          } else {
            imageFile = new File(imageDir.toFile(),
                "sketch_" + System.currentTimeMillis() + "_" + mapName + ".png")
            createNewPNG(imageFile)
          }
          final URI uriRelativeOrAbsoluteAccordingToMapPrefs =
              LinkController.toLinkTypeDependantURI(mapFile, imageFile)
          try {
            addImageToNode(uriRelativeOrAbsoluteAccordingToMapPrefs, selectedNodeModel)
            setImageZoom(node, 0.5f)
          } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
                "It was not possible to sketch on this node. If this node already has an image, it may be that the path to the image is broken or doesn't exist. Please remove the image, if necessary reassign the correct path, and try again.")
            launch(launchMode, editor)
            return
          }
        } else {
          if (!templatePath.isBlank()) {
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
                "This node already has an image. To create an image from scratch, please use a node that does not have an image.")
            launch(launchMode, editor)
            return
          }

          URI uri = getExternalResource(selectedNodeModel).getUri()
          uri = uri.isAbsolute() ? uri
              : mapDir.toURI().resolve(getExternalResource(selectedNodeModel).getUri())
          imageFile = new File(uri)
          refreshImageInMap(node)
        }

        Process process = null
        try {
          process = openImageInEditor(imageFile, editorBinary)
          showRefreshPrompt(node, editorBinary)
        } catch (IOException e) {
          // shouldMonitorExtProcess = false;
          System.out.println(SKETCHER_LOGGING_STRING +
              " WARNING: The Image could not be open in the indicated editor '" + editorBinary +
              "'" + ". MESSAGE: " + e.getMessage())
          UrlManager urlManager =
              Controller.getCurrentModeController().getExtension(UrlManager.class)
          urlManager.loadHyperlink(new Hyperlink(imageFile.toURI()))
          showRefreshPrompt(node, "system default")
        }
      } catch (IOException e) {
        e.printStackTrace()
        JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
            "It was not possible to create a new sketch image. The reason could be related to script permissions. " +
            "Please check your Tools -> Preferences -> Plug-ins section, and make sure scripts have sufficient permissions. " +
            "Another reason may be the folder where the image is to be created doesn't have write permissions - in that case you'd have to modify folder permissions, " +
            "or move your mindmap to a folder that does have enough permissions. " +
            System.lineSeparator() +
            "If you tried to create a new image using a template, please make sure that the template at the configured path exists. " +
            templatePath.trim())
      }

      // System.out.println(file.toString());
    }

    private static void addImageToNode(URI uri, NodeModel selectedNode) {
      ViewerController viewer = Controller.getCurrentController().getModeController()
          .getExtension(ViewerController.class)
      if (selectedNode == null) {
        throw new IllegalArgumentException()
      }
      ExternalResource extRes = new ExternalResource(uri)
      viewer.paste(uri, selectedNode)
    }

    private static ExternalResource getExternalResource(NodeModel nodeModel) {
      return nodeModel.getExtension(ExternalResource.class)
    }

    private static class RefreshRunnable implements Runnable {
      Node node

      RefreshRunnable(Node node) {
        this.node = node
      }

      @Override
      public void run() {
        EventQueue.invokeLater({
          refreshImageInMap(node)
        })
      }
    }

    private static void createNewPNG(File file) throws IOException {
      BufferedImage img = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB)
      Graphics2D g2d = img.createGraphics()
      g2d.setColor(Color.WHITE)
      g2d.fillRect(0, 0, 400, 400)

      // file.getParentFile().mkdirs();
      ImageIO.write(img, "PNG", file)
    }

    private static void showRefreshPrompt(Node node, String viewer) {
      JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
          "<html><body width='400px'; style=\"font-size: 13px\">" + "Launching Image Viewer: " +
          viewer + "<br><br>" +
          "CLICK 'OK' once you finish editing your sketch. Please save any image changes beforehand." +
          "<br><br>" + "Please check your taskbar if you can't see the application." +
          "<br><br>" +
          "(Note: you may configure your viewer in Sketcher -> Configuration )" +
          "</body></html>")
      refreshImageScheduled(node)
    }

    private static void refreshImageScheduled(Node node) {
      NodeModel nodeModel =
          Controller.getCurrentModeController().getMapController().getSelectedNode()
      if (getExternalResource(nodeModel) != null) {

        Executors.newScheduledThreadPool(1).schedule(new RefreshRunnable(node), 0,
            TimeUnit.SECONDS)
      }
    }

    private static Process openImageInEditor(File file, String imageEditor) throws IOException {
      Process process = null
      Runtime runTime = Runtime.getRuntime()
      String[] cmd = [
        imageEditor,
        file.toString()
      ]
      process = runTime.exec(cmd)
      return process
    }

    private static void refreshImageInMap(Node node) {
      float zoom = node.getExternalObject().getZoom()
      // TODO: (Groovy version) requires a cast of zoom - ... to float
      setImageZoom(node, (float) (zoom - 0.05f))
      EventQueue.invokeLater({
        setImageZoom(node, zoom)
      })
    }

    private static void setImageZoom(Node node, float zoom) {
      node.getExternalObject().setZoom(zoom)
    }

    private static void sketcherCreateConfigIfNotExists() {
      Path path = Paths.get(FreeplaneTools.getFreeplaneUserdir(), "scripts", SKETCHER_CONFIG_FILE)
      try {
        Files.createFile(path)
        File file = new File(
            FreeplaneTools.getFreeplaneUserdir() + System.getProperty("file.separator") + "scripts",
            SketcherUtility.SKETCHER_CONFIG_FILE)

        String initialSketcherConfigFile = ""
        if (Compat.isWindowsOS()) {
          initialSketcherConfigFile = INITIAL_SUGGESTED_VIEWER_WINDOWS + System.lineSeparator() +
              "#" + "C:\\Program Files\\GIMP 2\\bin\\gimp-2.10.exe" + System.lineSeparator + "#" +
              "C:\\Program Files\\draw.io\\draw.io.exe" + System.lineSeparator() + "#" +
              INITIAL_SUGGESTED_VIEWER_LINUX + System.lineSeparator() + "#" +
              DEFAULT_VIEWER_ASSOCIATED_KEY
        } else if (!Compat.isMacOsX()) {
          initialSketcherConfigFile = INITIAL_SUGGESTED_VIEWER_LINUX + System.lineSeparator() +
              "#" + INITIAL_SUGGESTED_VIEWER_WINDOWS + System.lineSeparator() + "#" +
              "C:\\Program Files\\GIMP 2\\bin\\gimp-2.10.exe" + System.lineSeparator() + "#" +
              "C:\\Program Files\\draw.io\\draw.io.exe" + System.lineSeparator() + "#" +
              DEFAULT_VIEWER_ASSOCIATED_KEY
        } else {
          initialSketcherConfigFile = DEFAULT_VIEWER_ASSOCIATED_KEY + System.lineSeparator() + "#" +
              INITIAL_SUGGESTED_VIEWER_WINDOWS + System.lineSeparator() + "#" +
              "C:\\Program Files\\GIMP 2\\bin\\gimp-2.10.exe" + System.lineSeparator() + "#" +
              "C:\\Program Files\\draw.io\\draw.io.exe" + System.lineSeparator() + "#" +
              INITIAL_SUGGESTED_VIEWER_LINUX
        }

        initialSketcherConfigFile = initialSketcherConfigFile + System.lineSeparator() +
            System.lineSeparator() +
            "######################## SKETCHER ########################" +
            System.lineSeparator() +
            "# Info and discussion: https://github.com/freeplane/freeplane/discussions/1496" +
            System.lineSeparator() +
            System.lineSeparator() +
            "####################### VIEWER #######################" +
            System.lineSeparator() +
            "# The first uncommented line (i.e line not prefixed by the symbol '#') will be taken as the image editor command" +
            System.lineSeparator() +
            "# 'default' indicates the use of the default Image viewer in your computer environment" +
            System.lineSeparator() +
            "# Please check instructions for setting up a custom editor for opening images at: " +
            System.lineSeparator() +
            "# https://github.com/i-plasm/freeplane-scripts/wiki/Configuring-Sketcher:-The-external-viewer" +
            System.lineSeparator() +
            "# NOTE 1: (Windows) The path for the Gimp 2.10 binary is a typical path for a Windows installation." +
            System.lineSeparator() +
            "# Please change it if you want to use Gimp and it doesn't match yours." +
            System.lineSeparator() +
            "# NOTE 2: (MacOS) If you want to use Gimp on MacOS, please check the wiki referenced in this file for an important caveat." +
            System.lineSeparator() +
            "# NOTE 3: (Linux) 'drawing' refers to the popular Linux application Drawing." +
            System.lineSeparator() +
            "# NOTE 4: (Linux) If your Freeplane installation is a FlatPak, unfortunately you can not configure a custom viewer." +
            System.lineSeparator() +
            "# In contrast, the 'bin/portable' Freeplane version is able to use the viewer binaries of your choice." +
            System.lineSeparator() +
            "# NOTE 5: (App: draw.io) For creating draw.io-editable images from scratch with Sketcher, you can configure a template" +
            System.lineSeparator() +
            "# pointing to a draw.io-editable PNG. To open a .PNG in draw.io using Sketcher, it must be draw.io-editable." +
            System.lineSeparator() +
            "# More info on Sketcher and draw.io: https://github.com/freeplane/freeplane/discussions/1496#discussioncomment-9479024 "+
            System.lineSeparator() +
            System.lineSeparator() +
            "####################### TEMPLATES #######################" +
            System.lineSeparator() +
            "# Please check instructions for configuring templates at: " +
            System.lineSeparator() +
            "# https://github.com/i-plasm/freeplane-scripts/wiki/Configuring-Sketcher:-Templates" +
            System.lineSeparator() +
            "# Examples (you can only configure one 'main_template', and you can configure any number of 'template'" +
            System.lineSeparator() +
            "# main_template=<full_path_to_image_file>" +
            System.lineSeparator() +
            "# template=<full_path_to_image_file>" +
            System.lineSeparator() +
            "# IMPORTANT!: Template size must be AT LEAST 50x50 pixels, so that there are no troubles refreshing in Freeplane."

        Files.write(file.toPath(), initialSketcherConfigFile.getBytes())
      } catch (IOException e) {
        if (!(e instanceof FileAlreadyExistsException)) {
          JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
              "It was not possible to create a configuration file." + System.lineSeparator() +
              "Message: " + e.getMessage())
          e.printStackTrace()
        }
      }
    }

    private static void sketcherConfigure() {
      sketcherCreateConfigIfNotExists()
      try {
        LinkController.getController()
            .loadHyperlink(LinkController.createHyperlink(FreeplaneTools.getFreeplaneUserdir() +
            System.getProperty("file.separator") + "scripts" +
            System.getProperty("file.separator") + SketcherUtility.SKETCHER_CONFIG_FILE))
      } catch (URISyntaxException e) {
        // TODO Auto-generated catch block
        e.printStackTrace()
      }
    }

    private static void displaySketcherHelp() {
      MenuHelper.FloatingMsgPopup floatingPopup = new MenuHelper.FloatingMsgPopup()

      JMenuItem contentsMenuItem = MenuHelper.createHeaderNoticeMenuItem(
          "<b>Sketcher: Quickly Sketch Drawings on a Node</b>" + "<br><i>IntelliFlow version</i>",
          "Sketcher Help")
      floatingPopup.add(contentsMenuItem)

      String visitWebsite = MenuHelper.floatingMenuItemUnderlinedActionHTML("Discusion & Updates:",
          "https://github.com/freeplane/freeplane/discussions/1496")
      JMenuItem visitWebsiteItem = createLinkMenuItem(
          "https://github.com/freeplane/freeplane/discussions/1496", visitWebsite)
      floatingPopup.add(visitWebsiteItem)

      String customEditorInstructionsWebsite = MenuHelper.floatingMenuItemUnderlinedActionHTML(
          "Instructions for setting up a custom image editor:" +
          "<br><i>(Instructions also in the Sketcher 'Configuration' option)</i>",
          "https://github.com/i-plasm/freeplane-scripts/wiki/Configuring-Sketcher:-The-external-viewer")
      JMenuItem customEditorInstructionsItem = createLinkMenuItem(
          "https://github.com/i-plasm/freeplane-scripts/wiki/Configuring-Sketcher:-The-external-viewer",
          customEditorInstructionsWebsite)
      floatingPopup.add(customEditorInstructionsItem)

      String templatesInstructionsWebsite = MenuHelper.floatingMenuItemUnderlinedActionHTML(
          "Instructions for configuring templates:" +
          "<br><i>(Instructions also in the Sketcher 'Configuration' option)</i>",
          "https://github.com/i-plasm/freeplane-scripts/wiki/Configuring-Sketcher:-Templates")
      JMenuItem templatesInstructionsItem = createLinkMenuItem(
          "https://github.com/i-plasm/freeplane-scripts/wiki/Configuring-Sketcher:-Templates",
          templatesInstructionsWebsite)
      floatingPopup.add(templatesInstructionsItem)

      contentsMenuItem.addActionListener{ l ->
        launch(launchMode, editor)
      }
      visitWebsiteItem.addActionListener{ l ->
        launch(launchMode, editor)
      }
      customEditorInstructionsItem.addActionListener{ l ->
        launch(launchMode, editor)
      }

      configureFloatingPopup(floatingPopup, launchMode, editor)
      showPopupMenu(floatingPopup, launchMode)
      requestFocusIfNeeded(floatingPopup)
    }

    public static void sketcherOpenFolder() {
      File imageFile = null
      File mapFile = Controller.getCurrentController().getMap().getFile()
      List<? extends Node> selecteds = ScriptUtils.c().getSelecteds()

      if (selecteds.size() > 1 || mapFile == null) {
        return
      }

      NodeModel selectedNodeModel =
          Controller.getCurrentModeController().getMapController().getSelectedNode()

      if (getExternalResource(selectedNodeModel) == null) {
        return
      }

      File mapDir = mapFile.getParentFile()

      URI uri = getExternalResource(selectedNodeModel).getUri()
      uri = uri.isAbsolute() ? uri
          : mapDir.toURI().resolve(getExternalResource(selectedNodeModel).getUri())
      imageFile = new File(uri)
      File imageDir = imageFile.getParentFile()

      String feedbackMessage =
          "It was not possible to locate or resolve the resource: " + imageDir.toString()
      FreeplaneTools.openResource(imageDir.toString(), feedbackMessage)
    }

    /**
     * Scans the directory where the image is located and filters all "associated" files, i.e having
     * the same name (without extension) as the Image file, in order to offer appropriate actions.
     *
     * @return an array of menu items pointing to "associated" files able to execute appropriate
     *         actions
     */
    static JMenuItem[] getAssociatedFilesMenu() {
      File imageFile = null
      File mapFile = Controller.getCurrentController().getMap().getFile()
      List<? extends Node> selecteds = ScriptUtils.c().getSelecteds()

      if (selecteds.size() > 1 || mapFile == null) {
        return new JMenuItem[0]
      }

      NodeModel selectedNodeModel =
          Controller.getCurrentModeController().getMapController().getSelectedNode()

      if (getExternalResource(selectedNodeModel) == null) {
        return new JMenuItem[0]
      }

      File mapDir = mapFile.getParentFile()

      URI uri = getExternalResource(selectedNodeModel).getUri()
      uri = uri.isAbsolute() ? uri
          : mapDir.toURI().resolve(getExternalResource(selectedNodeModel).getUri())
      imageFile = new File(uri)
      File imageDir = imageFile.getParentFile()

      String fileName = imageFile.getName()
      int extensionLength = TextUtils.extractExtension(fileName) != ""
          ? TextUtils.extractExtension(fileName).length() + 1
          : 0
      String fileNameWithoutExt = fileName.substring(0, fileName.length() - extensionLength)

      File[] listOfFiles = imageDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
              String ext = TextUtils.extractExtension(name)
              return !name.equals(fileName) && name.startsWith(fileNameWithoutExt) && (name.endsWith(fileNameWithoutExt + "." + ext)
                  || (name.endsWith(fileName + "." + ext)
                  || name.toLowerCase().endsWith(fileNameWithoutExt.toLowerCase() + "." + ext))
                  || name.toLowerCase().endsWith(fileName.toLowerCase() + "." + ext))
            }
          })

      List<JMenuItem> menuItems = new ArrayList<>()
      for (File companionFile : listOfFiles) {
        String feedbackMessage =
            "It was not possible to locate or resolve the resource: " + companionFile.toString()

        JMenuItem menuItem = makeStandardMenuItem(
            "Open associated ." + TextUtils.extractExtension(companionFile.getName()).toUpperCase() +
            " file: " + companionFile.getName(),
            "", {
              FreeplaneTools.openResource(companionFile.toString(), feedbackMessage)
            })
        menuItem.setToolTipText(companionFile.toString())
        menuItems.add(menuItem)
      }
      return menuItems.toArray(new JMenuItem[0])
    }

    private static Path createCopyFromTemplate(String templatePath, File targetFolder)
    throws IOException {
      File templateFile = new File(templatePath)
      Path copied = Paths.get(targetFolder.getPath(),
          System.currentTimeMillis() + "_" + templateFile.getName())
      Path originalPath = templateFile.toPath()
      return Files.copy(originalPath, copied)
    }

    static JMenuItem[] getUserTemplatesMenu() {
      return getUserDefinedTemplates().map{ line ->
        return getMenuForTemplate(line)
      }.toArray(JMenuItem.class)
    }

    private static JMenuItem getMenuForTemplate(String configFileline) {
      int index = configFileline.indexOf(MAIN_TEMPLATE) >= 0
          ? configFileline.indexOf(MAIN_TEMPLATE) + MAIN_TEMPLATE.length()
          : configFileline.indexOf(TEMPLATE) + TEMPLATE.length()
      index += 1 // to account for "="
      String path = configFileline.substring(index).trim()
      File file = new File(path)
      JMenuItem menuItem =
          makeStandardMenuItem("Create from Template: " + file.getName(), "", {
            run(path)
          })
      menuItem.setToolTipText(path)
      return menuItem
    }

    private static JMenuItem getMainOrFirstTemplate() {
      Optional<JMenuItem> userMainTemplateMenu = getUserMainTemplateMenu()
      return userMainTemplateMenu.orElse(
          getUserDefinedTemplates().findFirst().map{ it ->
            getMenuForTemplate(it)
          }.orElse(null)
          )
    }

    private static Optional<JMenuItem> getUserMainTemplateMenu() {
      String userDir = FreeplaneTools.getFreeplaneUserdir()
      JMenuItem menu = null
      try {
        List<String> lines =
            Files.readAllLines(Paths.get(userDir, "scripts", SKETCHER_CONFIG_FILE))

        String userMainTempl = lines.stream()
            .filter{it -> !it.trim().equals("") && it.trim().startsWith(MAIN_TEMPLATE + "=")}
            .findFirst().orElse(null)
        if (userMainTempl != null) {
          menu = getMenuForTemplate(userMainTempl)
        }
      } catch (IOException e1) {
      }
      return Optional.ofNullable(menu)
    }

    private static Stream<String> getUserDefinedTemplates() {
      String userDir = FreeplaneTools.getFreeplaneUserdir()
      try {
        List<String> lines =
            Files.readAllLines(Paths.get(userDir, "scripts", SKETCHER_CONFIG_FILE))
        return lines.stream().filter{ it ->
          !it.trim().equals("") && (it.trim().startsWith(TEMPLATE + "=") || it.trim().startsWith(MAIN_TEMPLATE + "="))
        }
      } catch (IOException e1) {
      }
      return Stream.empty()
    }

    private static String prevalidateSketcher() {
      String bin = ""
      Optional<String> uri = Optional.ofNullable(ScriptUtils.c().getSelected().getExternalObject().getUri())
      String prefix = uri.isEmpty() || uri.get().isBlank() ? "New Sketch" : "Sketch drawing"
      try {
        bin = getUserDefinedEditorBinary()
      } catch (IOException e) {
        if (Compat.isWindowsOS()) {
          bin = INITIAL_SUGGESTED_VIEWER_WINDOWS
        } else if (!Compat.isMacOsX()) {
          bin = INITIAL_SUGGESTED_VIEWER_LINUX
        }
      }
      if (bin.equals("")) {
        return prefix + " on node with: default viewer"
      }
      return prefix + " on node with: " +
          (!bin.contains(".") ? bin : new File(bin).getName())
    }
  }

  // ---------------------------------------------------------------
  // EXTERNAL DEPENDENCIES
  // ---------------------------------------------------------------

  /**
   * The methods in this class are derived/copied from the library "Freeplane Helper Library":
   *
   * Github: https://github.com/i-plasm/freeplane-helper-library
   *
   */
  public static class FreeplaneTools {

    public static MapView getMapView() {
      return (MapView) org.freeplane.features.mode.Controller.getCurrentController()
          .getMapViewManager().getMapViewComponent()
    }

    public static void executeMenuItem(String menuComand) {
      MenuUtils.executeMenuItems(Arrays.asList(menuComand))
    }

    public static void executeMenuItemLater(String menuComand) {
      EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {
              executeMenuItem(menuComand)
            }
          })
    }

    public static Node getNodeById(String id) {
      if (id.startsWith("#")) {
        id = id.substring(1)
      }
      return ScriptUtils.node().getMap().node(id)
    }

    public static void goToNodeById(String id) {
      ScriptUtils.c().select(getNodeById(id))
    }

    public static String getSelectedNodeText() {
      return ScriptUtils.c().getSelected().getPlainText()
    }

    public static String getFreeplaneUserdir() {
      return ResourceController.getResourceController().getFreeplaneUserDirectory()
    }

    public static String getProperty(String string) {
      return ResourceController.getResourceController().getProperty(string)
    }

    public static boolean getBooleanProperty(String string) {
      return ResourceController.getResourceController().getBooleanProperty(string)
    }

    public static void setProperty(String property, boolean value) {
      ResourceController.getResourceController().setProperty(property, value)
    }

    public static void setProperty(String property, String value) {
      ResourceController.getResourceController().setProperty(property, value)
    }

    public static FreeplaneVersion getFreeplaneVersion(String version) {
      return org.freeplane.core.util.FreeplaneVersion.getVersion(version)
    }

    public static FreeplaneVersion getCurrentFreeplaneVersion() {
      return org.freeplane.core.util.FreeplaneVersion.getVersion()
    }

    public static void loadMindMap(URI uri) throws FileNotFoundException, XMLParseException,
    IOException, URISyntaxException, XMLException {
      FreeplaneUriConverter freeplaneUriConverter = new FreeplaneUriConverter()
      final URL url = freeplaneUriConverter.freeplaneUrl(uri)
      final ModeController modeController = Controller.getCurrentModeController()
      modeController.getMapController().openMap(url)
    }

    public static boolean isFreeplaneAltBrowserMethodAvailable() {
      Class<?> browserClazz = null
      try {
        browserClazz = Class.forName("org.freeplane.main.application.Browser")
      } catch (ClassNotFoundException e) {
        // TODO Auto-generated catch block
        e.printStackTrace()
      }
      Method method = null
      try {
        method =
            browserClazz.getDeclaredMethod("openDocumentNotSupportedByDesktop", Hyperlink.class)
      } catch (NoSuchMethodException | SecurityException e) {
        // TODO Auto-generated catch block
        return false
      }
      return true
      // method.setAccessible(true);
    }

    public static File getMapFile() {
      return Controller.getCurrentController().getMap().getFile()
    }

    public static boolean isNodeID(String strToEvaluate) {
      return Pattern.compile("(?i)#?ID_[0-9]+").matcher(strToEvaluate).matches()
    }

    public static void openResourceUsingFreeplaneBroswer(URI uri) throws IOException {
      if (uri != null && uri.getScheme() != null
          && uri.getScheme().equalsIgnoreCase("file")
          && !new File(uri).exists()) {
        throw new IOException("The file resource does not seem to exist.")
      }
      if (uri != null) {
        new Browser().openDocument(new Hyperlink(uri))
      } else {
        throw new IllegalArgumentException()
      }
    }

    public static void visitWebsite(String urlStr, String feedbackMessage) {
      URL url = null
      try {
        url = new URL(urlStr)
      } catch (MalformedURLException e2) {
        // TODO Auto-generated catch block
        e2.printStackTrace()
      }
      URI uri = null
      try {
        uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(),
            url.getPath(), url.getQuery(), url.getRef())
        uri = new URI(uri.toASCIIString())
      } catch (URISyntaxException e2) {
        // TODO Auto-generated catch block
        e2.printStackTrace()
      }

      openResource(uri.toString(), feedbackMessage)
    }

    public static void openResource(String urlOrPath, String feedbackMessage) {
      // Case: resource has script extension (.sh, .bat, , etc). These are not supported.
      final Set<String> execExtensions = new HashSet<String>(
          Arrays.asList([
            "exe",
            "bat",
            "cmd",
            "sh",
            "command",
            "app"
          ]))
      String extension = TextUtils.extractExtension(urlOrPath)
      if (execExtensions.contains(extension)) {
        JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
            "It seems you are attempting to launch an executable or script file with extension '." +
            extension + "'" + " This service is not adequate for that purpose.")
        return
      }

      // Case: map local link to node
      if (isNodeID(urlOrPath)) {
        if (!urlOrPath.startsWith("#")) {
          urlOrPath = "#" + urlOrPath
        }
        goToNodeById(urlOrPath.substring(1))
        return
      }

      // Case: especial uri: link to menu item
      if (urlOrPath.startsWith("menuitem:_")) {
        executeMenuItem(
            urlOrPath.substring(urlOrPath.indexOf("menuitem:_") + "menuitem:_".length()))
        return
      }

      // Validating map has been saved
      File mapFile = getMapFile()
      if (mapFile == null) {
        JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
            "It seems the currently focused map has never been saved. Please save it in order to use this service.")
        return
      }
      File mapDir = mapFile.getParentFile()

      // Ajustment for windows
      if (Compat.isWindowsOS() && urlOrPath.toLowerCase().startsWith("c:/")) {
        urlOrPath = "file:///" + urlOrPath
      }

      // When resource url begins with 'file:' scheme. Adjusting slashes for compatibility with most
      // OS
      if (urlOrPath.indexOf("file:/") == 0) {
        int numOfSlashes = 1
        if (urlOrPath.startsWith("file:////")) {
          numOfSlashes = 4
        } else if (urlOrPath.startsWith("file:///")) {
          numOfSlashes = 3
        } else if (urlOrPath.startsWith("file://")) {
          numOfSlashes = 2
        }

        urlOrPath =
            "file:///" + urlOrPath.substring("file:".length() + numOfSlashes, urlOrPath.length())
      }

      // Constructing the options for uri
      URI uri = null
      URI uriForPossibleRelativePath =
          new Hyperlink(tryToResolveAndNormalizeRelativePath(urlOrPath, mapDir)).getUri()
      try {
        uri = atttemptToGetValidURI(urlOrPath, mapDir, uri)
      } catch (URISyntaxException e2) {
        JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
            feedbackMessage + System.lineSeparator() + System.lineSeparator() + urlOrPath
            + System.lineSeparator() + System.lineSeparator() + e2.getMessage())
        return
      }

      // Case: mindmap, without node reference
      if (extension.equalsIgnoreCase("mm")) {
        try {
          loadMindMap(uri)
        } catch (IOException | URISyntaxException | XMLException | RuntimeException e) {
          try {
            loadMindMap(uriForPossibleRelativePath)
          } catch (IOException | URISyntaxException | XMLException | RuntimeException e1) {
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
                "External MindMap could not be loaded. It either does not exist or could not be resolved." +
                System.lineSeparator() + System.lineSeparator() + urlOrPath +
                System.lineSeparator() + "Message: " + e1.getMessage())
          }
        }
        return
      }

      // Case: mindmap, with node reference
      final NodeAndMapReference nodeAndMapReference = new NodeAndMapReference(urlOrPath)
      if (nodeAndMapReference.hasNodeReference()) {
        if (uriForPossibleRelativePath != null) {
          urlManagerLoadHyperlink(uriForPossibleRelativePath)
        } else {
          urlManagerLoadHyperlink(uri)
        }
        return
      }

      // General case (i.e, not a node or a Freeplane mindmap)
      URI defaultURI = uri
      String userInput = urlOrPath
      // Thread for opening the resource
      Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {

              if (uri == null && uriForPossibleRelativePath == null) {
                showResourceCouldNotBeOpenPrompt(null)
              }

              // Case: when desktop is supported and the OS is either Windows or Mac
              if (Desktop.isDesktopSupported()
                  && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
                  && (Compat.isMacOsX() || Compat.isWindowsOS())) {
                try {
                  IOUtils.browseURLOrPathViaDesktop(uri)
                } catch (IOException e) {

                  if (uriForPossibleRelativePath != null) {

                    try {
                      IOUtils.browseURLOrPathViaDesktop(uriForPossibleRelativePath)
                    } catch (IOException e1) {
                      // uriForPossibleRelativePath: Now attempting with alternative methods for browsing
                      // resources
                      if (Compat.isWindowsOS()) {
                        try {
                          openResourceUsingFreeplaneBroswer(uriForPossibleRelativePath)
                        } catch (IOException e2) {
                          showResourceCouldNotBeOpenPrompt(e2)
                        }
                      } else {
                        alternativeOpen(uriForPossibleRelativePath)
                      }
                    }
                  } else {
                    // uri: Now attempting with alternative methods for browsing resources
                    if (Compat.isWindowsOS()) {
                      try {
                        openResourceUsingFreeplaneBroswer(uri)
                      } catch (IOException e1) {
                        showResourceCouldNotBeOpenPrompt(e1)
                      }
                    } else {
                      alternativeOpen(uri)
                    }
                  }
                }
              }
              // Case: when desktop is not supported and/or the OS is neither Windows nor Mac
              else {
                // Pre-check in case the resource is a file that doesn't exist, since the
                // command below may not throw an exception that we could use to inform the user
                if (uriForPossibleRelativePath != null
                    && uriForPossibleRelativePath.getScheme().equalsIgnoreCase("file")
                    && !new File(uriForPossibleRelativePath).exists()) {
                  showResourceCouldNotBeOpenPrompt(null)
                  // Intentionally not returning here, to try a very last time in case the method
                  // 'File...exists()' did not give an accurate result
                }

                URI uriToTry = uriForPossibleRelativePath == null ? uri : uriForPossibleRelativePath
                alternativeOpen(uriToTry)
              }
            }

            void showResourceCouldNotBeOpenPrompt(Exception e) {
              String exceptionMsg = e == null ? "" : e.getMessage()
              JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
                  feedbackMessage + System.lineSeparator() + System.lineSeparator() + urlOrPath
                  + System.lineSeparator() + System.lineSeparator() + exceptionMsg)
            }

            void alternativeOpen(URI uriToTry) {
              try {
                if (!Compat.isMacOsX() && !Compat.isWindowsOS()) {
                  altOpenOtherOS(uriToTry, false)
                } else if (Compat.isMacOsX()) {
                  altOpenMacOS(uriToTry, false)
                }
              } catch (IOException e) {
                showResourceCouldNotBeOpenPrompt(e)
              }
            }
          })
      thread.setName("FreeplaneUtils: " + "OPEN_RESOURCE")
      thread.start()
    }

    public static URI atttemptToGetValidURI(String urlOrPath, File mapDir, URI uri)
    throws URISyntaxException {
      // First uri attempt
      try {
        uri = LinkController.createHyperlink(urlOrPath).getUri()
      } catch (URISyntaxException e) {
        // Second uri attempt
        URL url
        try {
          url = new URL(urlOrPath)
          try {
            uri = new URI(url.getProtocol(), url.getHost(), url.getPath(), url.getQuery(),
                url.getRef())
          } catch (URISyntaxException e1) {
            // Third uri attempt
            url = new Hyperlink(tryToResolveAndNormalizeRelativePath(urlOrPath, mapDir)).toUrl()
            uri = new URI(url.getProtocol(), url.getHost(), url.getPath(), url.getQuery(),
                url.getRef())
            // e1.printStackTrace();
          }
        } catch (MalformedURLException e2) {
          // e2.printStackTrace();
        }
        // uri = new Hyperlink(new File(urlOrPath).toURI().normalize()).getUri();
        // e.printStackTrace();
      }
      return uri
    }

    public static URI tryToResolveAndNormalizeRelativePath(String url, File baseDir) {
      URI uri = null
      try {
        uri = new URL(baseDir.toURL(), url).toURI().normalize()
      } catch (Exception e) {
        try {
          uri = new URL(baseDir.toURL(), LinkController.createHyperlink(url).getUri().toString())
              .toURI().normalize()
        } catch (Exception e1) {
        }
      }
      return uri
    }

    /**
     * @throws RuntimeException (propagated from Freeplanes attempt to load the hyperlink)
     */
    public static void urlManagerLoadHyperlink(URI uri) {
      UrlManager urlManager = Controller.getCurrentModeController().getExtension(UrlManager.class)
      try {
        urlManager.loadHyperlink(new Hyperlink(uri))
      } catch (RuntimeException e) {
        throw new RuntimeException(e.getMessage())
      }
    }

    public static void altOpenOtherOS(URI uriToTry, boolean shouldWaitFor) throws IOException {
      try {
        IOUtils.openViaOSCommand(uriToTry.toString(),
            getProperty("default_browser_command_other_os"), shouldWaitFor)
      } catch (IOException e) {
        System.err.println("Caught: " + e)
        throw new IOException(e)
      }
    }

    public static void altOpenMacOS(URI uriToTry, boolean shouldWaitFor) throws IOException {
      String uriString = uriToTry.toString()
      if (uriToTry.getScheme().equals("file")) {
        uriString = uriToTry.getPath().toString()
      }

      try {
        IOUtils.openViaOSCommand(uriString, getProperty("default_browser_command_mac"),
            shouldWaitFor)
      } catch (IOException e) {
        System.err.println("Caught: " + e)
        throw new IOException(e)
      }
    }
  }

  // ---------------------------------------------------------------
  // end FreeplaneTools
  // ---------------------------------------------------------------

  /**
   * The methods in this class are derived/copied from the external "jhelperutils" library:
   *
   * Github: https://github.com/i-plasm/jhelperutils/
   */
  public static class SwingAwtTools {

    public static String detectURLOnHTMLDocument(JEditorPane componentToEvaluate) {
      JEditorPane htmlEditor = componentToEvaluate
      HTMLDocument doc = (HTMLDocument) htmlEditor.getDocument()
      int textStart = htmlEditor.getSelectionStart()
      int textEnd = htmlEditor.getSelectionEnd()

      if (htmlEditor.getSelectedText() == null) {
        // In case there's no selection, we detect
        // consider
        // the character at the caret position
        textEnd = componentToEvaluate.getCaretPosition() + 1
        textStart = componentToEvaluate.getCaretPosition()
      }

      for (int i = textStart; i < textEnd; i++) {
        Element characterElement = doc.getCharacterElement(i)
        SimpleAttributeSet charElemSimpleAttributeSet =
            new SimpleAttributeSet(characterElement.getAttributes().copyAttributes())

        MutableAttributeSet aTagAttributes =
            (MutableAttributeSet) charElemSimpleAttributeSet.getAttribute(HTML.Tag.A)

        if (aTagAttributes != null) {
          ArrayList<?> aTagAttributeKeys = Collections.list(aTagAttributes.getAttributeNames())
          for (Object aTagAttrKey : aTagAttributeKeys) {
            if (aTagAttrKey.toString().equalsIgnoreCase("href")) {
              // System.out.println(aTagAttributes.getAttribute(aTagAttrKey));
              return aTagAttributes.getAttribute(aTagAttrKey).toString()
            }
          }
        }
      }
      return ""
    }

    public static String autodetectLinkFromNeighbor(int caretPosition, String docText) {
      int surroundingNeighborhoodLeftIndex = caretPosition
      int surroundingNeighborhoodRightIndex = caretPosition
      if (caretPosition != 0) {
        while (surroundingNeighborhoodLeftIndex > 0) {
          String previousChar = docText.substring(surroundingNeighborhoodLeftIndex - 1,
              surroundingNeighborhoodLeftIndex)
          if (previousChar.trim().equals("")) {
            break
          }
          surroundingNeighborhoodLeftIndex--
        }
      }

      if (caretPosition != docText.length() - 1) {
        while (surroundingNeighborhoodRightIndex < docText.length()) {
          String nextChar = docText.substring(surroundingNeighborhoodRightIndex,
              surroundingNeighborhoodRightIndex + 1)
          if (nextChar.trim().equals("")) {
            break
          }
          surroundingNeighborhoodRightIndex++
        }
      }
      return docText.substring(surroundingNeighborhoodLeftIndex, surroundingNeighborhoodRightIndex)
    }

    /**
     * Attempts to detect a string that could serve as url or path on JEditorPane. It does not
     * matter if the user has made a text selection or not. The type of editor kit is irrelevant.
     *
     * Detections are first considered on selected text basis, and if no selection exist, then caret
     * position is used as basis to inquire on its neighboring characters.
     *
     * If the editor kit is of HTMLEditorKit type, its underlying html will be examined for the 'a'
     * tag and the 'href' attribute. If none is detected, then the detection will proceed as if it
     * was plain text.
     *
     * @throws IllegalArgumentException if no valid detection whatsoever could be made
     */
    public static String detectPossibleURLorPathOnEditor(JEditorPane editorPane) {
      boolean isTextualLink = false

      String urlOrPath = ""
      if (editorPane.getEditorKit() instanceof HTMLEditorKit) {
        urlOrPath = detectURLOnHTMLDocument(editorPane)
      }

      if (urlOrPath == "") {
        isTextualLink = true
        if (editorPane.getSelectedText() != null) {
          urlOrPath = editorPane.getSelectedText().trim()
        }
        // Case: no selection, editable text component
        else {
          // if (editorPane.isEditable()) {
          int caretPosition = editorPane.getCaretPosition()
          String docText = ""
          try {
            docText = editorPane.getDocument().getText(0, editorPane.getDocument().getLength())
          } catch (BadLocationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace()
          }

          urlOrPath = autodetectLinkFromNeighbor(caretPosition, docText)
        }
        if (TextUtils.containsLineBreaks(urlOrPath)) {
          throw new IllegalArgumentException(
          "Text selections not containing any HTML hyperlink inside can not contain line breaks.")
        }
      }
      return urlOrPath
    }

    public static Component getFocusedComponent() {

      Window fosusedWindow = null
      for (Window w : Window.getWindows()) {
        if (w.isFocused()) {
          fosusedWindow = w
        }
      }
      return fosusedWindow == null ? null : fosusedWindow.getFocusOwner()
    }

    public static void browseURLOrPathViaDesktop(URI uri) throws IOException {
      Desktop desktop = Desktop.getDesktop()
      if (Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.BROWSE)) {
        desktop.browse(uri)
      }
    }

    public static String getTrimmedSelectedText(JTextComponent editor) {
      String selectedTxt = editor == null ? "" : editor.getSelectedText()
      return selectedTxt == null ? "" : selectedTxt.trim()
    }
  }

  /**
   * The methods in this class are derived/copied from the external "jhelperutils" library:
   *
   * Github: https://github.com/i-plasm/jhelperutils/
   */
  public static class TextUtils {
    public static String extractExtension(String urlOrPath) {
      String extension = ""
      int dotIndex = urlOrPath.lastIndexOf(".")
      if (dotIndex != -1) {
        extension = urlOrPath.substring(dotIndex + 1, urlOrPath.length())
      }
      return extension
    }

    public static boolean containsLineBreaks(String string) {
      return Pattern.compile("\\v+").matcher(string).find()
    }

    public static boolean containsWhiteSpaces(String string) {
      return Pattern.compile("\\s+").matcher(string).find()
    }

    public static String removeLineBreaks(String string) {
      return string.replaceAll("\\v+", " ")
    }
  }

  /**
   * The methods in this class are derived/copied from the external "jhelperutils" library:
   *
   * Github: https://github.com/i-plasm/jhelperutils/
   */
  static class SysUtils {
    public static boolean isNeitherWindowsNorMac() {
      String os = System.getProperty("os.name").toLowerCase()
      return !os.startsWith("windows") && !os.startsWith("mac")
    }

    public static boolean isMacOs() {
      String os = System.getProperty("os.name").toLowerCase()
      return os.startsWith("mac")
    }

    public static boolean isWindows() {
      String os = System.getProperty("os.name").toLowerCase()
      return os.startsWith("windows")
    }
  }

  /**
   * The methods in this class are derived/copied from the external "jhelperutils" library:
   *
   * Github: https://github.com/i-plasm/jhelperutils/
   */
  public static class MenuHelper {

    public static class FloatingMsgPopup extends JPopupMenu {}

    public static JMenuItem makeMenuItem(String text, String name, Runnable doThis) {
      JMenuItem menuItem = new JMenuItem(text)
      menuItem.setName(name)
      menuItem.addActionListener{ l ->
        doThis.run()
      }
      return menuItem
    }

    public static String getMenuItemValidation(String defaultMenuItemText, String name,
        Runnable doThis, Callable<String> prevalidator) {
      String validation = defaultMenuItemText
      try {
        String prevalidation = prevalidator.call()
        if (prevalidation == null || !prevalidation.equals(""))
          validation = prevalidation
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace()
      }
      return validation
    }

    public static JMenuItem makeMenuItem(String text, String name, Runnable doThis,
        int maxTextLength) {
      if (text.length() > maxTextLength)
        text = text.substring(0, maxTextLength) + "..."
      JMenuItem menuItem = new JMenuItem(text)
      menuItem.setName(name)
      menuItem.addActionListener{ l ->
        doThis.run()
      }
      return menuItem
    }

    public static JMenuItem createHeaderNoticeMenuItem(String message, String title) {
      message = "<html><body width='600px'; style=\"font-size: 12px\">" +
          "[x CLOSE]&nbsp;&nbsp;&nbsp;&nbsp;" + title + "<br><br>" + message + "<br><br>" +
          "</body></html>"
      JMenuItem menuItem = new JMenuItem(message)
      return menuItem
    }

    public static JMenuItem createContentNoticeMenuItem(String message, String title) {
      message = "<html><body width='600px'; style=\"font-size: 12px\">" + "<br>" + title +
          "<br><br>" + message + "<br><br>" + "</body></html>"
      JMenuItem menuItem = new JMenuItem(message)
      return menuItem
    }

    public static String floatingMenuItemUnderlinedActionHTML(String legend,
        String actionDisplayTitle) {
      return String.format("%s%s%s", "<html><body width='600px'; style=\"font-size: 12px\">",
          legend + "<br> <a href=\"" + actionDisplayTitle + "\">" + actionDisplayTitle + "</a>",
          "</body></html>")
    }

    public static JPanel createFloatingActionPanel(String menuText, String name, String buttonText,
        Runnable runnable) {
      Box b1 = Box.createHorizontalBox()
      JPanel panel = new JPanel()
      JLabel label = new JLabel(menuText)
      JButton actionButton = new JButton(buttonText)
      panel.add(label)
      panel.add(actionButton)
      b1.add(panel)
      b1.add(Box.createHorizontalGlue())
      actionButton.addActionListener{ l ->
        actionButton.setEnabled(false)
        runnable.run()
      }
      actionButton.setFocusable(false)
      return panel
    }

    public static class DynamicMenu extends JMenu {
      private Supplier<JMenuItem[]> menuItemGenerator
      private boolean addSeparators

      public DynamicMenu(String text, Supplier<JMenuItem[]> menuItemGenerator,
      boolean addSeparators) {
        super(text)
        this.menuItemGenerator = menuItemGenerator
        this.addSeparators = addSeparators
      }

      public void addDynamicItems() {
        if (menuItemGenerator.get().length > 0) {
          if (addSeparators) {
            this.addSeparator()
          }
        }

        for (JMenuItem item : menuItemGenerator.get()) {
          this.add(item)
        }

        if (menuItemGenerator.get().length > 0) {
          if (addSeparators) {
            this.addSeparator()
          }
        }
      }
    }
  }

  /**
   * The methods in this class are derived/copied from the external "jhelperutils" library:
   *
   * Github: https://github.com/i-plasm/jhelperutils/
   */
  static class IOUtils {

    public static void openViaOSCommand(String uriString, String command, boolean shouldWaitFor)
    throws IOException {
      String[] cmd = [command, uriString]
      Process process = Runtime.getRuntime().exec(cmd)
      if (shouldWaitFor) {
        try {
          process.waitFor()
        } catch (InterruptedException e) {
          throw new IOException(e)
        }
      }
    }

    public static void browseURLOrPathViaDesktop(URI uri) throws IOException {
      Desktop desktop = Desktop.getDesktop()
      if (Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.BROWSE)) {
        desktop.browse(uri)
      }
    }
  }

  // ---------------------------------------------------------------
  // END OF EXTERNAL DEPENDENCIES
  // ---------------------------------------------------------------
}
