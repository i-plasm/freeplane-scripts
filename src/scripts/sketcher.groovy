// @ExecutionModes({ON_SINGLE_NODE="/main_menu/i-plasm/miscUtils"})

package scripts

QuickSketch.run()

/*
 * # Sketcher: Freeplane Script for quickly sketching drawings on a node.
 *
 * Github: https://github.com/i-plasm/freeplane-scripts
 *
 * Discussion: https://github.com/freeplane/freeplane/discussions/1496
 *
 * Instructions: see sections below.
 *
 * ---
 *
 * News 2023-11-16:
 * Sketcher as a standalone script is now superseded by the Sketcher service bundle within IntelliFlow - an intelligent service-provider
 * menu for Freeplane -. Any updates and maintainance will go toward the Sketcher IntelliFlow service bundle:
 * https://github.com/freeplane/freeplane/discussions/1534
 *
 * Update 2023-11-16:
 * Bug fix: if node core text had certain special characters and/or line breaks, Sketcher would fail
 * to create a new blank image
 *
 * Update 2023-10-28:
 * It now supports handling images with relative paths, and respects the Freeplane preference for relative vs. absolute path.
 *
 * ---
 *
 * Sketcher: Freeplane Script for quickly sketching drawings on a node.
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
 * ## Generalities
 *
 * Sketcher is a Freeplane Script for quickly sketching drawings on a node.
 *
 * Select a node, call the script and a new blank image or an existing one will be open in a
 * external image editor. Changes to the image will be refreshed in your mindmap.
 *
 * Script location: 'i-plasm -> Misc Utils -> Sketcher'
 *
 * ---------
 *
 * ## The external viewer
 *
 * You may or may not specify a viewer
 *
 * If the viewer is not specified or can not be successfully used, then the script will open the
 * image in the default image viewer.
 *
 * If you'd like to specify the viewer, follow these steps:
 *
 * 1) In your Freeplane 'scripts' directory, Create a file named "sketcher.txt" (lowercase).
 *
 * 2) inside the "sketcher.txt" write the binary/command to your viewer (respecting upper and lower
 * cases). For instance, for Microsoft Paint, the command is: 'mspaint'. But if your environment
 * does not have such command, then you must specify the full path to the binary. For instance, the
 * GIMP 2.10 binary in Windows might look like 'C:\Program Files\GIMP 2\bin\gimp-2.10.exe', and in
 * MacOS it might look like '/Applications/Gimp-2.10.app/Contents/MacOS/gimp' (NOTE ON MacOS: if
 * GIMP is the desired viewer, it seems it is best to **NOT** specify the binary in the
 * 'sketcher.txt' file, but rather to set the system to open images with GIMP by default. To do so
 * go to Finder, right click any PNG file (also repeat the process for .JPG, etc, if you'd like
 * those associated as well), and select "Get info". In the Info window click the arrow next to
 * "Open with", then click the menu and select GIMP, and finally click the "Change all..." button.
 * At least on the MacOS I tested, this was the only way to have the sketch images open in a
 * existing GIMP instance, not having to launch a new GIMP every time).
 *
 * 3) SAVE CHANGES, AND AFTER CREATING THE FILE "sketcher.txt" FOR THE FIRST TIME, RESTART
 * FREEPLANE.
 *
 * 4) TIP: TO INDICATE THAT YOU PREFER TO USE THE SYSTEM-ASSOCIATED APPLICATION, WRITE 'default' ON
 * THE FIRST LINE OF 'sketcher.txt'. ANOTHER FEATURE IS YOU CAN KEEP AS MANY LINES AS YOU WANT IN
 * THE 'sketcher.txt' FILE, EACH INDICATING A VIEWER BINARY. THE KEY IS TO REMEMBER THAT THE FIRST
 * NON-BLANK LINE OF THE 'sketcher.txt' FILE WILL BE TAKEN AS YOUR CONFIGURATION OF CHOICE. YOU CAN
 * KEEP THE REST OF LINES FOR EASY REFERENCE IN CASE YOU SWITCH BACK AND FORTH.
 *
 * ---------
 *
 * ## Permissions
 *
 * The following script permissions are required (set it in Preferences -> Plug-ins): 1- Permit File
 * read; 2- Permit File write; 3- Permit to execute other applications
 *
 * ---
 *
 * ## More specs
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
import org.freeplane.features.link.LinkController
import org.freeplane.features.map.NodeModel
import org.freeplane.features.mode.Controller
import org.freeplane.features.url.UrlManager
import org.freeplane.plugin.script.proxy.ScriptUtils
import org.freeplane.view.swing.features.filepreview.ExternalResource
import org.freeplane.view.swing.features.filepreview.ViewerController

public class QuickSketch {

  private static String editorBinary = ""
  public static final String DEFAULT_VIEWER_ASSOCIATED_KEY = "default"
  // public static boolean shouldMonitorExtProcess = false;

  public static void run() {
    String userDir = ResourceController.getResourceController().getFreeplaneUserDirectory()
    try {
      List<String> lines = Files.readAllLines(Paths.get(userDir, "scripts", "sketcher.txt"))
      lines = lines.stream().filter{it -> !it.trim().equals("")}.collect(Collectors.toList())
      if (lines.size() > 0) {
        editorBinary = lines.get(0).trim()
        if (editorBinary.equalsIgnoreCase(DEFAULT_VIEWER_ASSOCIATED_KEY)) {
          editorBinary = ""
        }
      }
    } catch (IOException e1) {
    }

    File imageFile = null

    File mapFile = Controller.getCurrentController().getMap().getFile()
    if (mapFile == null) {
      JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
          "It seems the currently focused map has never been saved. Please save it in order to use Sketcher.")
      return
    }

    File mapDir = mapFile.getParentFile()
    String mapName = Controller.getCurrentController().getMap().getFile().getName().trim()
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

      if (getExternalResource(selectedNodeModel) == null) {
        imageFile = new File(imageDir.toFile(),
            "sketch_" + System.currentTimeMillis() + "_" + mapName + ".png")
        createNewPNG(imageFile)
        final URI uriRelativeOrAbsoluteAccordingToMapPrefs =
            LinkController.toLinkTypeDependantURI(mapFile, imageFile)
        addImageToNode(uriRelativeOrAbsoluteAccordingToMapPrefs, selectedNodeModel)
      } else {
        URI uri = getExternalResource(selectedNodeModel).getUri()
        uri = uri.isAbsolute() ? uri
            : mapDir.toURI().resolve(getExternalResource(selectedNodeModel).getUri())
        imageFile = new File(uri)

        refreshImageInMap(node)
      }

      Process process = null
      try {
        process = openImageInEditor(imageFile, editorBinary)
      } catch (IOException e) {
        // shouldMonitorExtProcess = false;
        System.out.println("SKETCHER_WARNING: The Image could not be opened in the indicated editor '"
            + editorBinary + "'" + ". MESSAGE: " + e.getMessage())
        UrlManager urlManager =
            Controller.getCurrentModeController().getExtension(UrlManager.class)
        urlManager.loadHyperlink(new Hyperlink(imageFile.toURI()))
      }

      showRefreshPrompt(node)
    } catch (IOException e) {
      e.printStackTrace()
      JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
          "It was not possible to create a new sketch image. The reason could be related to script permissions. Please check your Tools -> Preferences -> Plug-ins section, and make sure scripts have sufficient permissions. Another reason may be the folder where the image is to be created doesn't have write permissions - in that case you'd have to modify folder permissions, or move your mindmap to a folder that does have enough permissions.")
    }

    // System.out.println(file.toString());
  }



  private static void createNewPNG(File file) throws IOException {
    BufferedImage img = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB)
    Graphics2D g2d = img.createGraphics()
    g2d.setColor(Color.WHITE)
    g2d.fillRect(0, 0, 400, 400)

    // file.getParentFile().mkdirs();
    ImageIO.write(img, "PNG", file)
  }



  private static void refreshImageInMap(Node node) {
    float zoom = node.getExternalObject().getZoom()
    node.getExternalObject().setZoom((float)(zoom - 0.05f))
    EventQueue.invokeLater{ node.getExternalObject().setZoom(zoom)}
  }



  private static void showRefreshPrompt(Node node) {
    Executors.newScheduledThreadPool(1).schedule(new RefreshRunnable(node), 0, TimeUnit.SECONDS)
  }


  private static class RefreshRunnable implements Runnable {
    Node node

    RefreshRunnable(Node node) {
      this.node = node
    }

    @Override
    public void run() {

      EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {
              JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
                  "<html><body width='400px'; style=\"font-size: 13px\">"
                  + "CLICK 'OK' once you finish editing your sketch. Please save any image changes beforehand."
                  + "<br><br>" + "Please check your taskbar if you can't see the application."
                  + "</body></html>")
              // (Suggestion: If you don't see the editor yet, press 'alt + TAB' (Windows/Linux) or 'cmd
              // + TAB' (MacOS))
              refreshImageInMap(node)
            }
          })
    }
  }


  private static Process openImageInEditor(File file, String imageEditor) throws IOException {
    Process process = null
    Runtime runTime = Runtime.getRuntime()
    String[] cmd = [imageEditor, file.toString()]
    process = runTime.exec(cmd)
    return process
  }


  private static void addImageToNode(URI uri, NodeModel selectedNode) {
    ViewerController viewer =
        Controller.getCurrentController().getModeController().getExtension(ViewerController.class)
    if (selectedNode == null)
      return
    ExternalResource extRes = new ExternalResource(uri)
    if (extRes == null)
      return
    viewer.paste(uri, selectedNode)
  }

  private static ExternalResource getExternalResource(NodeModel nodeModel) {
    return nodeModel.getExtension(ExternalResource.class)
  }
}