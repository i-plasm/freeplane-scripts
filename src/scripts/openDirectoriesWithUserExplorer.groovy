// @ExecutionModes({ON_SELECTED_NODE="/main_menu/i-plasm/fileUtils"})
package scripts

/*
 * openDirectoriesWithUserExplorer: (Currently only for Windows) opens, if it exists, the linked folder 
 * of selected node(s) using the 'start' command. 
 * 
 * It may be useful if the user has set a file explorer other than Window's default one.
 * 
 * Github & Updates: https://github.com/i-plasm/freeplane-scripts
 *
 * License: GPL-3.0 license (https://github.com/i-plasm/freeplane-scripts/blob/main/LICENSE.txt)
 *
 * Location: i-plasm > File Utils > Open Directories With User Explorer
 *
 * ## How to Use
 *
 * Select one or more nodes whose link points to a local directory, and invoke the script to open
 * the directory(ies). 
 * 
 * If a link is not a directory, it will be ignored without error. If a link is a directory,
 * but it does not exist or could not be opened for any reason, an error will be logged.
 *   
 * ## Permissions
 *
 * The following script permissions are required (set them in Preferences -> Plug-ins): 
 * 1- Permit File read; 2- Permit to execute other applications
 * 
 */

import java.nio.file.Files
import java.nio.file.Path
import org.freeplane.core.ui.components.UITools

if (node.getLink() == null || node.getLink().getFile() == null) {
  return
}
File file = node.getLink().getFile()
//path = file.toPath().normalize().toAbsolutePath().toString()
Path path

try {
  path = file.toPath().toRealPath()
  if (!Files.isDirectory(path)) {
    return
  }

  String[] cmd = [
    "cmd",
    "/c",
    "start",
    "\"\"" ,
    path
    //"C:\\Program Files"
  ]

  Process builder = Runtime.getRuntime().exec(cmd)
} catch (IOException e) {
  UITools.errorMessage("The path " + file.toPath().toString() + " could not be opened or does not exist.")
  System.err.println("The path " + file.toPath().toString() + " could not be opened or does not exist.")
  e.printStackTrace()
}
