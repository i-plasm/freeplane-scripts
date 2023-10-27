// @ExecutionModes({ON_SINGLE_NODE="/main_menu/i-plasm"})


/*
 * https://github.com/i-plasm/freeplane-scripts 
 * 
 * Sketcher: Freeplane Script for quickly sketching drawings on a node.
 * 
 * Select a node, call the script and a new blank image or an existing one will be open in a
 * external image editor. Changes to the image will be refreshed in your mindmap.
 * 
 * Script location: 'i-plasm -> Sketcher'
 * 
 * You can specify the viewer for editing your image by setting a environment variable. Call this
 * variable 'sketcher' (lowercase) and specify the binary in its value. If your viewer can be
 * understood by a short command, you may use that. For instance, for Microsoft Paint, you can set
 * the value to simply: 'mspaint'. But if your environment does not have such command, then you must
 * specify the full path to the binary. For instance, a GIMP 2.10 installation in Windows may be:
 * 'C:\Program Files\GIMP 2\bin\gimp-2.10.exe'.
 * 
 * AFTER SETTING THE ENVIRONMENT VARIABLE YOU MUST RESTART FREEPLANE!
 * 
 * If the 'sketcher' environment variable is not specified or can not be successfully used, then the
 * script will open the image in the default image viewer (or in the case of Windows, in Paint).
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
import javax.imageio.ImageIO
import javax.swing.JOptionPane
import org.freeplane.api.Node
import org.freeplane.core.ui.components.UITools
import org.freeplane.core.util.Compat
import org.freeplane.core.util.Hyperlink
import org.freeplane.features.map.NodeModel
import org.freeplane.features.mode.Controller
import org.freeplane.features.url.UrlManager
import org.freeplane.plugin.script.proxy.ScriptUtils
import org.freeplane.view.swing.features.filepreview.ExternalResource
import org.freeplane.view.swing.features.filepreview.ViewerController
import org.freeplane.view.swing.features.progress.mindmapmode.ProgressIcons

QuickSketch.run()

public class QuickSketch {

  public static String editorBinary = System.getenv().get("sketcher")
  public static final String DEFAULT_WINDOWS_EDITOR = "mspaint"
  // public static boolean shouldMonitorExtProcess = false;

  public static void run() {
    File file = null

    BufferedImage img = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB)
    Graphics2D g2d = img.createGraphics()
    g2d.setColor(Color.WHITE)
    g2d.fillRect(0, 0, 400, 400)

    // String userDir = ResourceController.getResourceController().getFreeplaneUserDirectory();
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

      UITools.getFrame().requestFocusInWindow()
      Process process = null
      try {
        process = openImageInEditor(file, editorBinary)
      } catch (IOException e) {
        // shouldMonitorExtProcess = false;
        if (Compat.isWindowsOS()) {
          process = openImageInEditor(file, DEFAULT_WINDOWS_EDITOR)
          // e.printStackTrace();
        } else {
          UrlManager urlManager =
              Controller.getCurrentModeController().getExtension(UrlManager.class)
          urlManager.loadHyperlink(new Hyperlink(file.toURI()))
        }
      }
      if (process == null) {
        return
      }

      Executors.newScheduledThreadPool(1).schedule(new RefreshRunnable(selectedNodeModel), 5,
          TimeUnit.SECONDS)
    } catch (IOException e) {
      e.printStackTrace()
      JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
          "It was not possible to create a new sketch image. The reason could be related to script permissions. Please check your Tools -> Preferences -> Plug-ins section, and make sure scripts have sufficient permissions. Another reason may be the folder where the image is to be created doesn't have write permissions - in that case you'd have to modify folder permissions, or move your mindmap to a folder that does have enough permissions.")
    }

    // System.out.println(file.toString());
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
                  "Click 'OK' if you finished editing your skectch. Please save any changes beforehand.")
              addExtImage(file, nodeModel)
            }
          })
    }
  }


  private static Process openImageInEditor(File file, String imageEditor) throws IOException {
    Process process = null
    Runtime runTime = Runtime.getRuntime()
    String[] cmd = [
      imageEditor + " ",
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
                  
                  
                                           