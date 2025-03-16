// @ExecutionModes({ON_SINGLE_NODE="/main_menu/i-plasm/miscUtils"})

/*
 * Info & Discussion: https://github.com/freeplane/freeplane/issues/116#issuecomment-2498343102
 *
 * Last Update: 2025-01-09
 *
 * ---------
 *
 * OverwriteMonitor: Script for detecting and warning when in-line node core editor gets activated
 * and possibly overwritten
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

package scripts

import java.awt.Color
import java.awt.Component
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener
import java.awt.event.FocusListener
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.Timer
import org.freeplane.api.Node
import org.freeplane.core.resources.ResourceController
import org.freeplane.core.ui.components.UITools
import org.freeplane.core.util.MenuUtils
import org.freeplane.features.map.NodeModel
import org.freeplane.features.mode.Controller
import org.freeplane.features.ui.IMapViewChangeListener
import org.freeplane.view.swing.map.MapView
import groovy.transform.Field

@Field static boolean hasBeenLaunched = false

if (hasBeenLaunched) {
  return
}
if (ResourceController.getResourceController().getBooleanProperty("layout_map_on_text_change")) {
  JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "In order to use this script you need to deactivate this preference: 'Preferences->Behaviour->In-line node editor->Layout map during editing'",
      NodeOverwriteWarn.PLUGIN_NAME + " - Action required", JOptionPane.WARNING_MESSAGE)
  return
}
hasBeenLaunched = true
new NodeOverwriteWarn()

public class NodeOverwriteWarn {

  private static final String PLUGIN_NAME ="OverwriteMonitor"
  private static final String FIVE_MINS = "5 mins"
  private static final String TEN_MINS = "10 mins"
  private static final String KEEP_WARNING ="Keep warning"

  private static final String[] OPTIONS = [
    KEEP_WARNING,
    FIVE_MINS,
    TEN_MINS
  ]
  private static final String GO_TO_DISCUSSION ="Go to github discussion"
  private static final String REACTIVATE_WARNING ="Reactivate warnings"

  private static final String[] HELP_OPTIONS = [
    REACTIVATE_WARNING,
    GO_TO_DISCUSSION
  ]

  private static final Color WARNING_ACTIVE_COLOR = new Color(204, 51, 153)
  private static final Color WARNING_INACTIVE_COLOR = new Color(51, 204, 51)
  private static final String FREEPLANE_ON_KEY_TYPE_PROPERTY = "key_type_action"
  private static final String FREEPLANE_ON_KEY_TYPE_DO_NOTHING = "IGNORE"
  private static final String FREEPLANE_ON_KEY_TYPE_OVERWRITE_CONTENT = "EDIT_CURRENT"

  private final JButton indicatorButton
  private final Timer timer

  private List<MapView> processedMapViews = new ArrayList()
  private IMapViewChangeListener mapViewChangeListener = createMapViewChangeListener()
  private ContainerListener containerListener = createContainerListener()

  public NodeOverwriteWarn() {
    setFreeplaneProperty(FREEPLANE_ON_KEY_TYPE_PROPERTY, FREEPLANE_ON_KEY_TYPE_DO_NOTHING)

    Controller.currentController.mapViewManager.addMapViewChangeListener(mapViewChangeListener)
    //changeFreeplaneEditMode(true)

    // processing first map view
    MapView mapView = getMapView()
    if (mapView != null) {
      processedMapViews.add(mapView)
      mapView.addContainerListener(containerListener)
    }

    timer = new Timer(0, {
      setFreeplaneProperty(FREEPLANE_ON_KEY_TYPE_PROPERTY, FREEPLANE_ON_KEY_TYPE_DO_NOTHING)
      indicatorButton.setBackground(WARNING_ACTIVE_COLOR)
    })
    timer.setRepeats(false)

    indicatorButton = new JButton(" ")
    indicatorButton.setToolTipText(PLUGIN_NAME)
    indicatorButton.setBackground(WARNING_ACTIVE_COLOR)
    getStatusBar().add(indicatorButton,0)
    getStatusBar().revalidate()
    getStatusBar().repaint()

    indicatorButton.addActionListener{
      int choice =  displayHelpAndOptionsPopup()
      if (choice < 0) {
        return
      }
      if (REACTIVATE_WARNING.equals(HELP_OPTIONS[choice])) {
        if (!isWarningActive()) {
          setFreeplaneProperty(FREEPLANE_ON_KEY_TYPE_PROPERTY, FREEPLANE_ON_KEY_TYPE_DO_NOTHING)
          //changeFreeplaneEditMode(true)
          timer.setInitialDelay(0)
          timer.restart()
          JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "The warning system has been reactivated.")
        } else {
          JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "The warning system is already active.")
        }
      } else if (GO_TO_DISCUSSION.equals(HELP_OPTIONS[choice])) {
        browseToDiscussion()
      }
    }
  }

  private boolean isWarningActive() {
    return !timer.isRunning()
  }

  private void browseToDiscussion() {
    String uri = "https://github.com/freeplane/freeplane/issues/116#issuecomment-2498343102"
    try {
      browseViaDesktop(new URI(uri))
    } catch (Exception e) {
      Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard()
      if (clipboard == null) {
        return
      }
      StringSelection sel = new StringSelection(uri)
      try {
        clipboard.setContents(sel, null)
        JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "Your configuration currently does not allow browsing." +
            " The webpage URL has been copied to clipboard:\n" + uri, "",
            JOptionPane.INFORMATION_MESSAGE)
      } catch (Exception e2) {
        e.printStackTrace()
      }
    }
  }

  private void pruneMapViews() {
    List<MapView> allCurrentViews = getAllMapViews()
    Iterator iter= processedMapViews.iterator()

    while (iter.hasNext()){
      MapView view=iter.next()
      if (!allCurrentViews.contains(view)) {
        iter.remove()
      }
    }
  }

  public static NodeModel getNodeModel(Node node) {
    return org.freeplane.features.mode.Controller.getCurrentController().getMap().getNodeForID(node.getId())
  }

  private IMapViewChangeListener createMapViewChangeListener() {
    return new IMapViewChangeListener() {
          public void beforeViewChange(final Component oldView, final Component newView) {}

          public void afterViewChange(final Component oldView, final Component newView) {
            // Avoiding memory leaks
            pruneMapViews()

            if (newView == null || processedMapViews.contains(newView)) {
              return
            }

            MapView mapView = (MapView) newView
            processedMapViews.add(mapView)
            mapView.addContainerListener(containerListener)
          }
        }
  }

  private ContainerListener createContainerListener() {
    return new ContainerListener() {
          @Override
          public void componentAdded(ContainerEvent e) {
            if (!isWarningActive()) {
              return
            }

            // Based on the logic from Freeplane's 'EditNodeTextField.show' method
            Component componentZero = ((MapView)e.getSource()).getComponent(0)
            //String topCompClassName = componentZero.getClass().getName()
            if (componentZero instanceof JEditorPane) {
              //topCompClassName.contains("org.freeplane.features.text.mindmapmode.MTextController")
              FocusListener editorsFocusListener

              for (FocusListener l : componentZero.getFocusListeners()) {
                if (l.getClass().getName().contains("TextFieldListener")) {
                  editorsFocusListener = l
                  break
                }
              }
              componentZero.removeFocusListener(editorsFocusListener)
              SwingUtilities.invokeLater{

                int result = displayWarningPopup()

                if (result >= 0) {
                  if (!OPTIONS[result].equals(KEEP_WARNING)) {
                    setFreeplaneProperty(FREEPLANE_ON_KEY_TYPE_PROPERTY, FREEPLANE_ON_KEY_TYPE_OVERWRITE_CONTENT)

                    indicatorButton.setBackground(WARNING_INACTIVE_COLOR)
                    //changeFreeplaneEditMode(false)

                    // TODO needs to be adapted in case more timing options are added
                    timer.setInitialDelay(OPTIONS[result].equals(FIVE_MINS) ? 5 * 60 * 1000 : 10 * 60 * 1000)
                    timer.restart()
                  }
                }
                if (!Arrays.asList(componentZero.getFocusListeners()).contains(editorsFocusListener)) {
                  componentZero.addFocusListener(editorsFocusListener)
                }
                SwingUtilities.invokeLater{
                  componentZero.requestFocusInWindow()
                }
              }
            }
          }

          @Override
          public void componentRemoved(ContainerEvent e) {}
        }
  }

  private int displayWarningPopup() {
    String msg = "The selected node may have been overwritten via in-line node core edition. Do you wish to deactivate this warning for a period of time?" +
        "\n\nNote: if you modified the text unintendedly, you need to undo changes manually."
    return  JOptionPane.showOptionDialog(UITools.getCurrentFrame(), msg, PLUGIN_NAME +  " - Warning!",
        JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, OPTIONS, OPTIONS[0])
  }

  private int displayHelpAndOptionsPopup() {
    return  JOptionPane.showOptionDialog(UITools.getCurrentFrame(), PLUGIN_NAME + " options", PLUGIN_NAME,
        JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, HELP_OPTIONS, HELP_OPTIONS[1])
  }

  public static MapView getMapView() {
    return (MapView) org.freeplane.features.mode.Controller.getCurrentController()
        .getMapViewManager().getMapViewComponent()
  }

  public static List<MapView> getAllMapViews() {
    return (List<MapView>) org.freeplane.features.mode.Controller.getCurrentController()
        .getMapViewManager().getMapViewVector()
  }

  private static JComponent getStatusBar() {
    final JComponent toolBar = Controller.getCurrentModeController().getUserInputListenerFactory().getToolBar("/status")
    return toolBar
  }

  private static void changeFreeplaneEditMode(boolean isViewMode) {
    if (isViewMode) {
      MenuUtils.executeMenuItems([
        'SetStringPropertyAction.view_mode.true'
      ])
    } else {
      MenuUtils.executeMenuItems([
        'SetStringPropertyAction.view_mode.false'
      ])
    }
  }

  public static void browseViaDesktop(URI uri) throws IOException {
    Desktop desktop = Desktop.getDesktop()
    if (Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.BROWSE)) {
      desktop.browse(uri)
    }
  }

  public static void setFreeplaneProperty(String property, String value) {
    ResourceController.getResourceController().setProperty(property, value)
  }
}
