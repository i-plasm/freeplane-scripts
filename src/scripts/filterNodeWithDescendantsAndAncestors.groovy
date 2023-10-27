// @ExecutionModes({ON_SINGLE_NODE="/main_menu/i-plasm/filtering"})

/*
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

import org.freeplane.core.util.MenuUtils
import org.freeplane.features.filter.FilterController

filter()

def filter() {

  if (!FilterController.getCurrentFilterController().getShowAncestors().isSelected()) {
    MenuUtils.executeMenuItems(['ShowAncestorsAction'])
  }
  if (!FilterController.getCurrentFilterController().getShowDescendants().isSelected()) {
    MenuUtils.executeMenuItems(['ShowDescendantsAction'])
  }
  MenuUtils.executeMenuItems([
    'ApplySelectedViewConditionAction'
  ])
}