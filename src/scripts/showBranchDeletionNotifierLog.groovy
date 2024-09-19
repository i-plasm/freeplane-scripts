// @ExecutionModes({ON_SELECTED_NODE="/main_menu/i-plasm/freeplaneNotifier"})
package scripts

import org.freeplane.core.ui.components.UITools
import org.freeplane.features.map.IMapChangeListener
import org.freeplane.features.mode.Controller

def boolean classFound = false
for (IMapChangeListener mapChangeL : Controller.getCurrentModeController().getMapController().getMapChangeListeners()) {
  if (mapChangeL.getClass().getSimpleName().equals("FreeplaneDeletionNotifier")) {
    classFound = true
    return mapChangeL.class.getMethod("displayLogWindow").invoke(mapChangeL)
  }
}

if (!classFound) {
  UITools.informationMessage("It seems the Deletion Notifier has not been initialized yet.")
}
