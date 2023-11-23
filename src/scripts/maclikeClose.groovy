// @ExecutionModes({ON_SINGLE_NODE="/main_menu/file"})

package scripts

/**
 * A small script to emulate in both Windows and MacOS how Macs deal with closing all the files of an app instance.
 * 
 * License: GPL-3.0 license (https://raw.githubusercontent.com/i-plasm/freeplane-scripts/main/LICENSE.txt)
 * 
 * Github: https://github.com/i-plasm/freeplane-scripts
 *
 * It gets installed on ` File -> Maclike Close `
 *
 * You may assign a shortcut of your choice at ` Tools -> Assign hot key `. For instance, you can assign 
 * ` CTRL + Q ` (or the Mac equivalent ` Command + Q `), effectively replacing the shortcut for the typical 
 * quit action. 
 *
 * How it works (For Windows): This script requests a "close all open maps" action - the user will be prompted 
 * to save changes if necessary -, and then minimizes Freeplane, effectively hiding the app while remaining active.
 *
 * How it works (For MacOS): This script requests a "close all open maps" action - the user will be prompted 
 * to save changes if necessary -, and then sends the Freeplane frame to the back, effectively hiding it if other 
 * apps are open.
 *
 * Note for Macs : A true emulation is currently not currently possible for MacOS since minimizaing the freeplane 
 * instance would have a negative consequence in a particular situation: if the user quits (fully exits) 
 * Freeplane whilst minimized, then when the user launches Freeplane again it will appear as a very tiny
 * almost invisible window, which is confusing.
 * 
 */

import java.awt.Frame
import org.freeplane.core.ui.components.UITools
import org.freeplane.core.util.Compat
import org.freeplane.core.util.MenuUtils

MenuUtils.executeMenuItems(Arrays.asList("CloseAllMapsAction"))

if (Compat.isMacOsX()) {
  UITools.getCurrentFrame().toBack()
}
else {
  UITools.getCurrentFrame().setExtendedState(Frame.ICONIFIED)
}
