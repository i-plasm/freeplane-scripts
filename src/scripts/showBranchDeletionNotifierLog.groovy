// @ExecutionModes({ON_SINGLE_NODE="/main_menu/i-plasm/freeplaneNotifier"})
package scripts

/**
 * ShowDeletionNotifierLog: shows the logged notifications of DeletionNotifier for the current session
 *
 * License: GPL-3.0 license (https://github.com/i-plasm/freeplane-scripts/blob/main/LICENSE.txt)
 *
 * Github: https://github.com/i-plasm/freeplane-scripts/blob/main/src/scripts/showDeletionNotifierLog.groovy
 *
 */
import org.freeplane.core.ui.components.UITools
import org.freeplane.features.map.IMapChangeListener
import org.freeplane.features.mode.Controller

def boolean classFound = false
for (IMapChangeListener mapChangeL : Controller.getCurrentModeController().getMapController().getMapChangeListeners()) {
  if (mapChangeL.getClass().getSimpleName().equals("FreeplaneBranchDeletionNotifier")) {
    classFound = true
    return mapChangeL.class.getMethod("displayLogWindow").invoke(mapChangeL)
  }
}

if (!classFound) {
  UITools.informationMessage("It seems BranchDeletionNotifier has not been initialized yet.")
}
