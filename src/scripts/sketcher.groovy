// @ExecutionModes({ON_SINGLE_NODE="/main_menu/i-plasm"})

package scripts

QuickSketch.run()

/*
 * Sketcher: Freeplane Script for quickly sketching drawings on a node.
 *
 * WEBSITE: https://github.com/i-plasm/freeplane-scripts
 *
 * Copyright (C) 2023 bbarbosa
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
 * ---------
 * 
 * 
 * Select a node, call the script and a new blank image or an existing one will be open in a
 * external image editor. Changes to the image will be refreshed in your mindmap.
 * 
 * Script location: 'i-plasm -> Sketcher'
 * 
 * ---------
 * 
 * The external viewer
 * 
 * You may or may not specify a viewer
 * 
 * If the viewer is not specified or can not be successfully used, then the script will open the
 * image in the default image viewer (or in the case of Windows, in Paint).
 * 
 * If you'd like to specify the viewer, follow these steps:
 * 
 * 1) In your Freeplane 'scripts' directory, Create a file named "sketcher.txt" (lowercase).
 * 
 * 2) inside the "sketcher.txt" indicate the binary/command to your viewer. For instance, for
 * Microsoft Paint, the command is: 'mspaint'. But if your environment does not have such command,
 * then you must specify the full path to the binary. For instance, the GIMP 2.10 binary in Windows
 * might look like 'C:\Program Files\GIMP 2\bin\gimp-2.10.exe', and in MacOS it might look like
 * '/Applications/Gimp-2.10.app/Contents/MacOS/gimp' (that said, on MacOS, if GIMP is the desired
 * viewer, it seems it is best to NOT specify the binary in the 'sketcher.txt' file, but rather to
 * set all .PNG files to be opened with GIMP. To do so go to Finder, right click any PNG file,
 * select "Get info" and there in the "Open with" section select GIMP. Then press the
 * "Change all..." button. At least on the MacOS I tested, this was the only way to have the sketch
 * images open in a existing GIMP instance, not having to launch a new GIMP every time).
 * 
 * RESPECT LOWER AND UPPERCASES!
 * 
 * 3) AFTER CREATING THE FILE "sketcher.txt" FOR THE FIRST TIME, RESTART FREEPLANE.
 * 
 * ---------
 * 
 * The following script permissions are required (set it in Preferences -> Plug-ins): 1- Permit File
 * read; 2- Permit File write; 3- Permit to execute other applications
 * 
 * If a new image was created, it will be stored in the folder 'MyMapName_files', and this folder
 * will be located on the folder containing the mindmap.
 * 
 * This script may be developed more and converted into an add-on. Feel free to check for updates
 * and leave feedback!
 * 
 */

import java.awt.Color
import java.awt.EventQueue
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import javax.imageio.ImageIO
import javax.swing.JOptionPane
import org.freeplane.api.Node
import org.freeplane.core.resources.ResourceController
import org.freeplane.core.ui.components.UITools
import org.freeplane.core.util.Hyperlink
import org.freeplane.features.map.NodeModel
import org.freeplane.features.mode.Controller
import org.freeplane.features.url.UrlManager
import org.freeplane.plugin.script.proxy.ScriptUtils
import org.freeplane.view.swing.features.filepreview.ExternalResource
import org.freeplane.view.swing.features.filepreview.ViewerController
import org.freeplane.view.swing.features.progress.mindmapmode.ProgressIcons


public class QuickSketch {

  public static String editorBinary = null
  public static final String DEFAULT_WINDOWS_EDITOR = "mspaint"
  // public static boolean shouldMonitorExtProcess = false;

  public static void run() {
    String userDir = ResourceController.getResourceController().getFreeplaneUserDirectory()
    try {
      List<String> lines = Files.readAllLines(Paths.get(userDir, "scripts", "sketcher.txt"))
      lines = lines.stream().filter{it -> !it.trim().equals("")}.collect(Collectors.toList())
      if (lines.size() > 0) {
        editorBinary = lines.get(0)
      }
    } catch (IOException e1) {
    }

    File file = null

    BufferedImage img = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB)
    Graphics2D g2d = img.createGraphics()
    g2d.setColor(Color.WHITE)
    g2d.fillRect(0, 0, 400, 400)

    File mapFile = Controller.getCurrentController().getMap().getFile()
    if (mapFile == null) {
      JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
          "It seems the currently focused map has never been saved. Please save it in order to use QuickSketch.")
      return
    }

    File mapDir = mapFile.getParentFile()
    String mapName = Controller.getCurrentController().getMap().getFile().getName()
    String suffix = mapName.substring(mapName.length() - ".mm".length(), mapName.length())
    if (suffix.equals(".mm")) {
      mapName = mapName.substring(0, mapName.length() - ".mm".length())
    }

    Path imageDir = null
    try {
      imageDir = Files.createDirectories(Paths.get(mapDir.toPath().toString(), mapName + "_files"))
    } catch (IOException e) {
      e.printStackTrace()
    }

    Node node = ScriptUtils.c().getSelected()
    ScriptUtils.c().select(node)
    NodeModel selectedNodeModel =
        Controller.getCurrentModeController().getMapController().getSelectedNode()
    try {
      file = new File(imageDir.toFile(), "sketch_" + node.getShortText() + "_"
          + System.currentTimeMillis() + "_" + mapName + ".png")

      if (getExternalResource(selectedNodeModel) == null) {
        // file.getParentFile().mkdirs();
        ImageIO.write(img, "PNG", file)
      } else {
        file = new File(getExternalResource(selectedNodeModel).getUri())
      }

      // If the node already has an image, this will refresh it. Otherwise, it will add a blank one.
      addExtImage(file, selectedNodeModel)

      Process process = null
      try {
        process = openImageInEditor(file, editorBinary)
      } catch (IOException e) {
        // shouldMonitorExtProcess = false;
        // e.printStackTrace();
        System.out.println("SKETCHER_WARNING: The Image could not be open in the indicated editor '"
            + editorBinary + "'" + ". MESSAGE: " + e.getMessage())
        UrlManager urlManager =
            Controller.getCurrentModeController().getExtension(UrlManager.class)
        urlManager.loadHyperlink(new Hyperlink(file.toURI()))
      }

      showRefreshPrompt(selectedNodeModel)
    } catch (IOException e) {
      e.printStackTrace()
      JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
          "It was not possible to create a new sketch image. The reason could be related to script permissions. Please check your Tools -> Preferences -> Plug-ins section, and make sure scripts have sufficient permissions. Another reason may be the folder where the image is to be created doesn't have write permissions - in that case you'd have to modify folder permissions, or move your mindmap to a folder that does have enough permissions.")
    }

    // System.out.println(file.toString());
  }


  private static void showRefreshPrompt(NodeModel selectedNodeModel) {
    Executors.newScheduledThreadPool(1).schedule(new RefreshRunnable(selectedNodeModel), 0,
        TimeUnit.SECONDS)
  }


  private static class RefreshRunnable implements Runnable {
    NodeModel nodeModel

    RefreshRunnable(NodeModel nodeModel) {
      this.nodeModel = nodeModel
    }

    @Override
    public void run() {

      EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {
              File file = new File(getExternalResource(nodeModel).getUri())
              JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
                  "<html><body width='400px'; style=\"font-size: 13px\">"
                  + "CLICK 'OK' once you finish editing your skectch. Please save any changes beforehand."
                  + "</body></html>")
              // (Suggestion: If you don't see the editor yet, press 'alt + TAB' (Windows/Linux) or 'cmd
              // + TAB' (MacOS))
              addExtImage(file, nodeModel)
            }
          })
    }
  }


  private static Process openImageInEditor(File file, String imageEditor) throws IOException {
    Process process = null
    Runtime runTime = Runtime.getRuntime()
    String[] cmd = [
      imageEditor,
      file.toString()
    ]
    process = runTime.exec(cmd)
    return process
  }


  private static void addExtImage(File file, NodeModel selectedNode) {
    ViewerController viewer =
        Controller.getCurrentController().getModeController().getExtension(ViewerController.class)
    if (selectedNode == null)
      return
    ExternalResource extRes = createExtension(selectedNode, file)
    if (extRes == null)
      return
    URI absoluteUri = extRes.getAbsoluteUri(selectedNode.getMap())
    if (absoluteUri == null)
      return
    viewer.paste(absoluteUri, selectedNode)
  }

  private static ExternalResource getExternalResource(NodeModel nodeModel) {
    return nodeModel.getExtension(ExternalResource.class)
  }


  private static ExternalResource createExtension(final NodeModel node, File input) {
    ExternalResource preview = new ExternalResource(input.toURI())
    ProgressIcons.updateExtendedProgressIcons(node, input.getName())
    return preview
  }
}
