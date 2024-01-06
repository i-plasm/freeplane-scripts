// @ExecutionModes({ON_SINGLE_NODE="/main_menu/i-plasm/miscUtils"})

package scripts

/*
 * FreeplaneErrorDialog2Clipboard: script that detects whenever a JOptionPane dialog displaying an
 * error message appears, and allows the user to copy the error to clipboard
 *
 * Github & Updates: https://github.com/i-plasm/freeplane-scripts
 * 
 * License: GPL-3.0 license (https://github.com/i-plasm/freeplane-scripts/blob/main/LICENSE.txt)
 * 
 * Location (if installed as a regular, i.e not an init, script): i-plasm > Misc Utils > Freeplane
 * Error Dialog2Clipboard
 * 
 * ## How to Use
 *
 * Every time a target error dialog is detected, the script will ask if you want to copy the error
 * message to clipboard.
 * 
 * NOTE: By default, it is set to only consider error message dialogs whose title equals "Freeplane"
 * - this is the standard title of the error dialogs used by the Freeplane application. This
 * behaviour may be changed or made more flexible by modifying this script.
 *
 * ## Starting and Stopping monitoring
 * 
 * In order to detect Freeplane error dialogs, the monitoring must be activated
 * 
 * - If you install as a regular script, you must invoke the script once in order to start
 * monitoring. 
 * 
 * - If you install as an init script, monitoring is started automatically when
 * freeplane is launched. To install as an init script, simply place it on the
 * `<user_dir>/scripts/init/` folder
 * 
 * If you want to stop the monitoring, you need to wait until the next time an error detection
 * occurs, and click the "Stop monitoring" button of the options offered.
 * 
 */

import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.Window
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.stream.Collectors
import java.util.stream.Stream
import javax.swing.JDialog
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

FreeplaneED2C.ErrorDialog2Clipboard.defaultInit("Freeplane")


class FreeplaneED2C {

  /**
   * This class is derived/copied from the external "jhelperutils" library:
   *
   * Github: https://github.com/i-plasm/jhelperutils/ 
   * License: https://github.com/i-plasm/jhelperutils/blob/main/LICENSE
   */
  static class ErrorDialog2Clipboard implements PropertyChangeListener {
    enum TitleCriterium {
      BEGINS, EQUALS, CONTAINS
    }

    private String targetErrorDialogTitle
    private boolean shouldShowStopMonitoringButton

    private JDialog lastJDialog = null
    private String errorString = ""

    static void defaultInit(String targetErrorDialogTitle) {
      Window fosusedWindow = null
      for (Window w : Window.getWindows()) {
        if (w.isFocused()) {
          fosusedWindow = w
        }
      }
      List<PropertyChangeListener> filteresListeners =
          ErrorDialog2Clipboard.countAllActivatedListeners()

      if (filteresListeners.size() == 0) {
        ErrorDialog2Clipboard l = new ErrorDialog2Clipboard(targetErrorDialogTitle, true)
        l.activateListener()
      } else if (filteresListeners.size() == 0) {
        JOptionPane.showMessageDialog(fosusedWindow, "ErrorDialog2Clipboard is already running",
            "ErrorDialog2Clipboard", JOptionPane.ERROR_MESSAGE)
      } else {
        JOptionPane.showMessageDialog(fosusedWindow,
            "There is a listener already registered. There should not be more than one registered ErrorDialog2Clipboard listeners! ",
            "ErrorDialog2Clipboard", JOptionPane.ERROR_MESSAGE)
      }
    }

    public ErrorDialog2Clipboard(String targetErrorDialogTitle,  boolean shouldShowStopMonitoringButton) {
      super()
      this.targetErrorDialogTitle = targetErrorDialogTitle
      this.shouldShowStopMonitoringButton =  shouldShowStopMonitoringButton
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      if (evt.getNewValue() != null) {

        // Error dialog is currently being displayed
        if (lastJDialog != null && lastJDialog.isShowing()) {
          return
        }

        // Error dialog has been just closed.
        if (lastJDialog != null) {
          // lastDialog must be nullified before invoking the method
          lastJDialog = null
          showCopyToClipboardMessage(errorString)
          errorString = null
          return
        }

        // Detecting a newly opened and focused error dialog
        Window window = SwingUtilities.getWindowAncestor((Component) evt.getNewValue())

        if (window instanceof JDialog
            && ((JDialog) window).getTitle().equalsIgnoreCase(targetErrorDialogTitle)
            && !(((JDialog) window).getTitle().equalsIgnoreCase("ErrorDialog2Clipboard"))) {

          JDialog currentDialog = (JDialog) window

          if (currentDialog.getContentPane().getComponentCount() == 1
              && currentDialog.getContentPane().getComponent(0) instanceof JOptionPane) {

            JOptionPane joptionPane = (JOptionPane) currentDialog.getContentPane().getComponent(0)
            if (joptionPane.getMessageType() == JOptionPane.ERROR_MESSAGE) {
              // A target error dialog has been found!
              lastJDialog = currentDialog
              errorString = joptionPane.getMessage().toString()
            }
          }
        }
      }
    }

    public static List<PropertyChangeListener> countAllActivatedListeners() {
      List<PropertyChangeListener> filteresListeners = Stream
          .of(KeyboardFocusManager.getCurrentKeyboardFocusManager()
          .getPropertyChangeListeners("permanentFocusOwner"))
          .filter{it -> it.getClass().getName().contains(ErrorDialog2Clipboard.class.getName())}
          .collect(Collectors.toList())
      return filteresListeners
    }

    public void activateListener() {
      KeyboardFocusManager.getCurrentKeyboardFocusManager()
          .addPropertyChangeListener("permanentFocusOwner", this)
    }

    public void removeListener() {
      KeyboardFocusManager.getCurrentKeyboardFocusManager()
          .removePropertyChangeListener("permanentFocusOwner", this)
    }

    public void showCopyToClipboardMessage(String errorString) {

      String msg = "Error message detected:" +
          System.lineSeparator() + System.lineSeparator() +
          (errorString.length() > 100 ? errorString.substring(0, 100) + "..." : errorString)

      Window fosusedWindow = null
      for (Window w : Window.getWindows()) {
        if (w.isFocused()) {
          fosusedWindow = w
        }
      }

      String[] options

      if (shouldShowStopMonitoringButton) {
        options = [
          "Copy to Clipboard",
          "No",
          "Stop monitoring"
        ]
      } else {
        options = [
          "Copy to Clipboard",
          "No"
        ]
      }

      int result = JOptionPane.showOptionDialog(fosusedWindow, msg, "ErrorDialog2Clipboard",
          JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0])

      if (result == 0) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard()
        if (clipboard == null) {
          JOptionPane.showMessageDialog(fosusedWindow, "Failed to copy to clipboard",
              "ErrorDialog2Clipboard", JOptionPane.ERROR_MESSAGE)
        }
        StringSelection sel = new StringSelection(errorString)
        try {
          clipboard.setContents(sel, null)
        } catch (Exception e) {
          JOptionPane.showMessageDialog(fosusedWindow, "Failed to copy to clipboard",
              "ErrorDialog2Clipboard", JOptionPane.ERROR_MESSAGE)
          e.printStackTrace()
        }
      } else if (result == 2) {
        removeListener()
      }
    }
  }
}