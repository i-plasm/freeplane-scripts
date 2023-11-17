// @ExecutionModes({ON_SINGLE_NODE="/main_menu/i-plasm/filtering"})

/* 
 * News 2023-11-16:
 * This standalone script is now superseded by the 'Breadcrumb node' service within IntelliFlow - an intelligent service-provider 
 * menu for Freeplane -. Any updates and maintainance will go toward the 'Breadcrumb node' IntelliFlow service: 
 * https://github.com/freeplane/freeplane/discussions/1534
 * 
 * Update 23-11-16. Improved performance for larger maps.
 * 
 * ---------
 * 
 * WEBSITE: https://github.com/i-plasm/freeplane-scripts
 *  
 * The script essentially filters the selected node, making sure the ancestors and descendants are
 * shown. This makes it possible to get a focused view on one node and its branch,
 * and to be able to toggle to 'normal' view in a fast way (via just one shortcut). 
 * 
 * Note: this doesn't imply the descendant branch will be fully unfolded. It will be shown
 * exactly as displayed prior to the filtering. Unfolding is a costly operation in general, so I
 * left that out of the script. You can still unfold whenever you need via Freeplane's "Unfold all"
 * action. That said, a script variation can be done to include unfolding the whole branch
 * automatically. 
 * 
 * Script location: 'i-plasm -> Filtering -> Filter Node Ancestors Descendants'
 * 
 * You'd need to assign a shortcut to that script, and also to the 'Filter -> No Filtering' option.
 * That way, via your keyboard you'd do the filtering/unfiltering. Note that in order to unfilter
 * you do not need to position yourself on any particular node, so the mechanism is fast.
 */
package scripts

import java.awt.EventQueue
import javax.swing.JOptionPane
import org.freeplane.core.ui.components.UITools
import org.freeplane.core.util.MenuUtils
import org.freeplane.features.filter.FilterController
import org.freeplane.plugin.script.proxy.ScriptUtils

filter()

def filter() {
  if (ScriptUtils.c().getSelecteds().size() > 1) {
    JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
        "You have selected multiple nodes. Currently, it is only possible to breadcrumb a single node.")
    return
  }

  MenuUtils.executeMenuItems(['NewMapViewAction'])

  EventQueue.invokeLater(new Runnable() {
        @Override
        public void run() {
          if (!FilterController.getCurrentFilterController().getShowAncestors().isSelected()) {
            MenuUtils.executeMenuItems(['ShowAncestorsAction'])
          }
        }
      })

  EventQueue.invokeLater(new Runnable() {

        @Override
        public void run() {
          if (!FilterController.getCurrentFilterController().getShowDescendants().isSelected()) {
            MenuUtils.executeMenuItems(['ShowDescendantsAction'])
          }
        }
      })

  EventQueue.invokeLater(new Runnable() {

        @Override
        public void run() {
          MenuUtils.executeMenuItems([
            'ApplySelectedViewConditionAction'
          ])
        }
      })

  EventQueue.invokeLater(new Runnable() {

        @Override
        public void run() {
          MenuUtils.executeMenuItems([
            'MoveSelectedNodeAction.CENTER'
          ])
        }
      })
}