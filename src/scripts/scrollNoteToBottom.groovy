/*
 * ScrollNoteToBottom: freeplane utility script that scrolls the note to the bottom (if the note panel is visible)
 * 
 * Useful when notes are updated programmatically and the new information is appended at the end of the note.
 * 
 * Github: https://github.com/i-plasm/freeplane-scripts
 * 
 * License: GPL-3.0 license (https://github.com/i-plasm/freeplane-scripts/blob/main/LICENSE.txt)
 * 
 * Location: Tools > Scripts > Scroll Note To Bottom
 * 
 * ---
 * 
 * To invoke this script from another script, add this code to your script:
 * 
 * SwingUtilities.invokeLater{
 *      MenuUtils.executeMenuItems([
 *          'ScrollNoteToBottom_on_selected_node'
 *      ])
 * }
 * 
 * (the wrapping in `SwingUtilities.invokeLater`...` can be omitted if the call already comes from the Event Dispatch Thread)
 * 
 */

package scripts

import java.awt.Component
import java.awt.Container
import java.lang.ref.WeakReference
import java.util.stream.Collectors
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent
import org.freeplane.core.ui.components.UITools
import org.freeplane.view.swing.map.MapView
import groovy.transform.Field

@Field static WeakReference notePanel
@Field static WeakReference scrollPane


notePaneScrollToBottom()

def notePaneScrollToBottom() {
  if (notePanel == null || notePanel.get() == null || !((Component)notePanel.get()).isShowing() ) {
    notePanel = new WeakReference(findNotePanel(UITools.getFrame(), null))
  }

  if (scrollPane == null || scrollPane.get() == null || !((Component)scrollPane.get()).isShowing() ) {
    scrollPane = new WeakReference(findNoteScrollPane())
  }

  JScrollPane jScrollPane = scrollPane.get()
  if (jScrollPane == null) {
    return
  }

  JTextComponent textComp = (JTextComponent) jScrollPane.getViewport().getView()
  JScrollBar bar = jScrollPane.getVerticalScrollBar()
  bar.setValue(bar.getMaximum())

  SwingUtilities.invokeLater{
    textComp.setCaretPosition(textComp.getDocument().getLength())
  }
}

private JScrollPane findNoteScrollPane() {


  if (notePanel.get() != null) {
    return findNoteScrollPane(notePanel.get(), null)
  }
  return null
}

private JPanel findNotePanel(Container c, JPanel foundNotePanel) {
  if (foundNotePanel != null) {
    return foundNotePanel
  }
  Component[] components = c.getComponents()
  for (Component com : components) {
    // System.out.println(com.getClass().getName());
    if (com.getClass().getName().contains("NotePanel")) {
      return (JPanel) com
    } else if (com instanceof Container && !(com instanceof MapView)) {
      foundNotePanel = findNotePanel((Container) com, null)
      if (foundNotePanel != null) {
        return foundNotePanel
      }
    }
  }
  return null
}

private JScrollPane findNoteScrollPane(Container c, JScrollPane foundScrollPane) {
  if (foundScrollPane != null) {
    return foundScrollPane
  }
  Component[] components = c.getComponents()
  List<Component> compList =Arrays.asList(components).stream().filter{ it.isShowing()}.collect(Collectors.toList())

  for (Component com : compList) {
    if (com instanceof JScrollPane) {
      return (JScrollPane) com
    } else if (com instanceof Container) {
      foundScrollPane = findNoteScrollPane((Container) com, null)
      if (foundScrollPane != null) {
        return foundScrollPane
      }
    }
  }
  return null
}