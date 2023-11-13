package io.github.iplasm.app.freeplane.library;

import java.awt.EventQueue;
import java.io.File;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.freeplane.api.FreeplaneVersion;
import org.freeplane.api.Node;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.MenuUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.script.proxy.ScriptUtils;
import org.freeplane.view.swing.map.MapView;

public class FreeplaneUtils {


  public static MapView getMapView() {
    return (MapView) org.freeplane.features.mode.Controller.getCurrentController()
        .getMapViewManager().getMapViewComponent();
  }

  public static void executeMenuItem(String menuComand) {
    MenuUtils.executeMenuItems(Arrays.asList(menuComand));
  }

  public static void executeMenuItemLater(String menuComand) {
    EventQueue.invokeLater(new Runnable() {

      @Override
      public void run() {
        executeMenuItem(menuComand);

      }
    });
  }

  public static Node getNodeById(String id) {
    if (id.startsWith("#")) {
      id = id.substring(1);
    }
    return ScriptUtils.node().getMap().node(id);

  }

  public static void goToNodeById(String id) {
    ScriptUtils.c().select(getNodeById(id));
  }

  public static String getSelectedNodeText() {
    return ScriptUtils.c().getSelected().getPlainText();
  }

  public static String getFreeplaneUserdir() {
    return ResourceController.getResourceController().getFreeplaneUserDirectory();
  }

  public static boolean getBooleanProperty(String string) {
    return ResourceController.getResourceController().getBooleanProperty(string);
  }

  public static void setProperty(String property, boolean value) {
    ResourceController.getResourceController().setProperty(property, value);
  }

  public static FreeplaneVersion getFreeplaneVersion(String version) {
    return org.freeplane.core.util.FreeplaneVersion.getVersion(version);
  }

  public static FreeplaneVersion getCurrentFreeplaneVersion() {
    return org.freeplane.core.util.FreeplaneVersion.getVersion();
  }

  public static boolean isNodeID(String strToEvaluate) {
    return Pattern.compile("(?i)#?ID_[0-9]+").matcher(strToEvaluate).matches();
  }

  public static File getMapFile() {
    return Controller.getCurrentController().getMap().getFile();
  }

}
