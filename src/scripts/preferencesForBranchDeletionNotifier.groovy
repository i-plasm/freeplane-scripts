// @ExecutionModes({ON_SELECTED_NODE="/main_menu/i-plasm/freeplaneNotifier"})
package scripts

/**
 * ChangeDeletionNotifierThreshold: sets the value of the deletion threshold in DeletionNotifier
 *
 * License: GPL-3.0 license (https://github.com/i-plasm/freeplane-scripts/blob/main/LICENSE.txt)
 *
 * Github: https://github.com/i-plasm/freeplane-scripts/blob/main/src/scripts/changeDeletionThreshold.groovy
 *
 */
import javax.swing.JOptionPane
import org.freeplane.core.ui.components.UITools
import org.freeplane.features.map.IMapChangeListener
import org.freeplane.features.mode.Controller

def boolean classFound = false
for (IMapChangeListener mapChangeL : Controller.getCurrentModeController().getMapController().getMapChangeListeners()) {
  if (mapChangeL.getClass().getSimpleName().equals("FreeplaneBranchDeletionNotifier")) {
    classFound = true
    String response = JOptionPane.showInputDialog(UITools.getCurrentFrame(), "Set threshold value", "Preferences", JOptionPane.INFORMATION_MESSAGE).trim()
    try {
      Integer num = Integer.parseInt(response)
      if (num >= 1) {
        return mapChangeL.getClass().getMethod("setDeletionThreshold", Integer.class).invoke(mapChangeL, num)
      }
      else {
        UITools.informationMessage("Invalid input.")
      }
    }
    catch (NumberFormatException e)  {
      UITools.informationMessage("Invalid input.")
    } catch (Exception e) {
      e.printStackTrace()
      UITools.informationMessage("The setDeletionThreshold method invokation failed.")
    }
  }
}

if (!classFound) {
  UITools.informationMessage("It seems BranchDeletionNotifier has not been initialized yet.")
}
