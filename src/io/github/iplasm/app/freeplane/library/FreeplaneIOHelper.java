package io.github.iplasm.app.freeplane.library;

import java.awt.Desktop;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JOptionPane;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.Hyperlink;
import org.freeplane.features.link.LinkController;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.url.FreeplaneUriConverter;
import org.freeplane.features.url.NodeAndMapReference;
import org.freeplane.features.url.UrlManager;
import org.freeplane.main.application.Browser;
import org.freeplane.n3.nanoxml.XMLException;
import org.freeplane.n3.nanoxml.XMLParseException;
import io.github.iplasm.library.java.commons.SwingAwtTools;
import io.github.iplasm.library.java.commons.TextUtils;

/**
 * 
 * This class depends on the external lib:
 * 
 * java-commons (Website: https://github.com/i-plasm/java-commons/)
 * 
 */
public class FreeplaneIOHelper {

  public static void loadMindMap(URI uri) throws FileNotFoundException, XMLParseException,
      IOException, URISyntaxException, XMLException {
    FreeplaneUriConverter freeplaneUriConverter = new FreeplaneUriConverter();
    final URL url = freeplaneUriConverter.freeplaneUrl(uri);
    final ModeController modeController = Controller.getCurrentModeController();
    modeController.getMapController().openMap(url);
  }

  public static boolean doesFreeplaneAltBrowserMethodExist() {
    Class<?> browserClazz = null;
    try {
      browserClazz = Class.forName("org.freeplane.main.application.Browser");
    } catch (ClassNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    Method method = null;
    try {
      method = browserClazz.getDeclaredMethod("openDocumentNotSupportedByDesktop", Hyperlink.class);
    } catch (NoSuchMethodException | SecurityException e) {
      // TODO Auto-generated catch block
      return false;
    }
    return true;
    // method.setAccessible(true);
  }

  private static void refl_useFreeplaneAlternativeBrowser(URI uri)
      throws ClassNotFoundException, NoSuchMethodException, SecurityException,
      IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    Class<?> browserClazz = Class.forName("org.freeplane.main.application.Browser");
    Method method =
        browserClazz.getDeclaredMethod("openDocumentNotSupportedByDesktop", Hyperlink.class);
    method.setAccessible(true);
    method.invoke(new Browser(), new Hyperlink(uri));
  }

  private static void refl_loadNodeReferenceURI(NodeAndMapReference nodeAndMapReference)
      throws IllegalAccessException, IllegalArgumentException, InvocationTargetException,
      NoSuchMethodException, SecurityException, ClassNotFoundException {
    Class<?> clazz = Class.forName("org.freeplane.features.url.UrlManager");
    Method method = clazz.getDeclaredMethod("loadNodeReferenceURI", NodeAndMapReference.class);
    method.setAccessible(true);
    method.invoke(new UrlManager(), nodeAndMapReference);
  }

  public static void openResourceUsingFreeplaneBroswer(String urlOrPath, URI uri,
      URI uriForPossibleRelativePath)
      throws ClassNotFoundException, NoSuchMethodException, SecurityException,
      IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    if (doesFreeplaneAltBrowserMethodExist()) {
      attemptToOpenResourceWithFreeplaneAlternativeBrowser(urlOrPath, uri,
          uriForPossibleRelativePath);
    } else {
      if (uriForPossibleRelativePath != null) {
        new Browser().openDocument(new Hyperlink(uriForPossibleRelativePath));
      } else {
        throw new IllegalArgumentException();
      }
    }
  }

  public static void attemptToOpenResourceWithFreeplaneAlternativeBrowser(String urlOrPath, URI uri,
      URI uriForPossibleRelativePath)
      throws ClassNotFoundException, NoSuchMethodException, SecurityException,
      IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    try {
      refl_useFreeplaneAlternativeBrowser(uri);
    } catch (ClassNotFoundException | NoSuchMethodException | SecurityException
        | IllegalAccessException | IllegalArgumentException | InvocationTargetException e11) {
      if (uriForPossibleRelativePath != null) {
        refl_useFreeplaneAlternativeBrowser(uriForPossibleRelativePath);
      } else {
        throw new IllegalArgumentException();
      }
    }
  }

  public static void visitWebsite(String urlStr, String feedbackMessage) {
    URL url = null;
    try {
      url = new URL(urlStr);
    } catch (MalformedURLException e2) {
      // TODO Auto-generated catch block
      e2.printStackTrace();
    }
    URI uri = null;
    try {
      uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(),
          url.getPath(), url.getQuery(), url.getRef());
      uri = new URI(uri.toASCIIString());
    } catch (URISyntaxException e2) {
      // TODO Auto-generated catch block
      e2.printStackTrace();
    }

    openResource(uri.toString(), feedbackMessage);
  }

  public static void openResource(String urlOrPath, String feedbackMessage) {
    // Case: resource has script extension (.sh, .bat, , etc). These are not supported.
    final Set<String> execExtensions = new HashSet<String>(
        Arrays.asList(new String[] {"exe", "bat", "cmd", "sh", "command", "app"}));
    String extension = TextUtils.extractExtension(urlOrPath);
    if (execExtensions.contains(extension)) {
      JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
          "It seems you are attempting to launch an executable or script file with extension '."
              + extension + "'" + " This service is not adequate for that purpose.");
      return;
    }

    // Case: map local link to node
    if (FreeplaneUtils.isNodeID(urlOrPath)) {
      if (!urlOrPath.startsWith("#")) {
        urlOrPath = "#" + urlOrPath;
      }
      FreeplaneUtils.goToNodeById(urlOrPath.substring(1));
      return;
    }

    // Case: especial uri: link to menu item
    if (urlOrPath.startsWith("menuitem:_")) {
      FreeplaneUtils.executeMenuItem(
          urlOrPath.substring(urlOrPath.indexOf("menuitem:_") + "menuitem:_".length()));
      return;
    }

    // Validating map has been saved
    File mapFile = FreeplaneUtils.getMapFile();
    if (mapFile == null) {
      JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
          "It seems the currently focused map has never been saved. Please save it in order to use this service.");
      return;
    }
    File mapDir = mapFile.getParentFile();

    // When resource url begins with 'file:' scheme. Adjusting slashes for compatibility with most
    // OS
    if (urlOrPath.indexOf("file:/") == 0) {
      int numOfSlashes = 1;
      if (urlOrPath.startsWith("file:////")) {
        numOfSlashes = 4;
      } else if (urlOrPath.startsWith("file:///")) {
        numOfSlashes = 3;
      } else if (urlOrPath.startsWith("file://")) {
        numOfSlashes = 2;
      }

      urlOrPath =
          "file:///" + urlOrPath.substring("file:".length() + numOfSlashes, urlOrPath.length());
    }

    // Constructing the options for uri
    URI uri = null;
    URI uriForPossibleRelativePath =
        new Hyperlink(tryToResolveAndNormalizeRelativePath(urlOrPath, mapDir)).getUri();
    try {
      uri = atttemptToGetValidURI(urlOrPath, mapDir, uri);
    } catch (URISyntaxException e2) {
      JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
          feedbackMessage + System.lineSeparator() + System.lineSeparator() + urlOrPath
              + System.lineSeparator() + System.lineSeparator() + e2.getMessage());
      return;
    }

    // Case: mindmap, without node reference
    if (extension.equalsIgnoreCase("mm")) {
      try {
        loadMindMap(uri);
      } catch (IOException | URISyntaxException | XMLException e) {
        try {
          loadMindMap(uriForPossibleRelativePath);
        } catch (IOException | URISyntaxException | XMLException e1) {
          JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
              "External MindMap could not be loaded." + System.lineSeparator() + urlOrPath
                  + System.lineSeparator() + e1.getMessage());
        }
      }
      return;
    }

    // Case: mindmap, with node reference
    final NodeAndMapReference nodeAndMapReference = new NodeAndMapReference(urlOrPath);
    if (nodeAndMapReference.hasNodeReference()) {
      try {
        refl_loadNodeReferenceURI(nodeAndMapReference);
      } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
          | NoSuchMethodException | SecurityException | ClassNotFoundException e) {
        JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
            "MindMap with node reference could not be loaded." + System.lineSeparator() + urlOrPath
                + System.lineSeparator() + e.getMessage());
        e.printStackTrace();
      }
      return;
    }

    // General case (i.e, not a node or a Freeplane mindmap)
    URI defaultURI = uri;
    String userInput = urlOrPath;
    // Thread for opening the resource
    Thread thread = new Thread(new Runnable() {
      URI uri = defaultURI;
      String urlOrPath = userInput;

      @Override
      public void run() {
        if (uri == null && uriForPossibleRelativePath == null) {
          showResourceCouldNotBeOpenPrompt(null);
        }
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
            && (Compat.isMacOsX() || Compat.isWindowsOS())) {
          try {
            SwingAwtTools.browseURLOrPathViaDesktop(uri);
          } catch (IOException e) {
            if (uriForPossibleRelativePath != null) {

              try {
                SwingAwtTools.browseURLOrPathViaDesktop(uriForPossibleRelativePath);
              } catch (IOException e1) {
                // Now attempting with the Freeplane method for browsing resources
                try {
                  openResourceUsingFreeplaneBroswer(urlOrPath, uri, uriForPossibleRelativePath);
                } catch (ClassNotFoundException | NoSuchMethodException | SecurityException
                    | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e2) {
                  showResourceCouldNotBeOpenPrompt(e2);
                }
              }
            } else {
              // Now attempting with the Freeplane methods for browsing resources
              try {
                openResourceUsingFreeplaneBroswer(urlOrPath, uri, uriForPossibleRelativePath);
              } catch (ClassNotFoundException | NoSuchMethodException | SecurityException
                  | IllegalAccessException | IllegalArgumentException
                  | InvocationTargetException e1) {
                showResourceCouldNotBeOpenPrompt(e1);
              }
            }
          }
        } else {
          try {
            openResourceUsingFreeplaneBroswer(urlOrPath, uri, uriForPossibleRelativePath);
          } catch (ClassNotFoundException | NoSuchMethodException | SecurityException
              | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            showResourceCouldNotBeOpenPrompt(e);
          }
        }
      }

      public void showResourceCouldNotBeOpenPrompt(Exception e) {
        String exceptionMsg = e == null ? "" : e.getMessage();
        JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
            feedbackMessage + System.lineSeparator() + System.lineSeparator() + urlOrPath
                + System.lineSeparator() + System.lineSeparator() + exceptionMsg);
      }

    });
    thread.setName("FreeplaneUtils: " + "OPEN_RESOURCE");
    thread.start();
  }

  public static URI atttemptToGetValidURI(String urlOrPath, File mapDir, URI uri)
      throws URISyntaxException {
    // First uri attempt
    try {
      uri = LinkController.createHyperlink(urlOrPath).getUri();
    } catch (URISyntaxException e) {
      // Second uri attempt
      URL url;
      try {
        url = new URL(urlOrPath);
        try {
          uri = new URI(url.getProtocol(), url.getHost(), url.getPath(), url.getQuery(),
              url.getRef());
        } catch (URISyntaxException e1) {
          // Third uri attempt
          url = new Hyperlink(tryToResolveAndNormalizeRelativePath(urlOrPath, mapDir)).toUrl();
          uri = new URI(url.getProtocol(), url.getHost(), url.getPath(), url.getQuery(),
              url.getRef());
          // e1.printStackTrace();
        }
      } catch (MalformedURLException e2) {
        // e2.printStackTrace();
      }
      // uri = new Hyperlink(new File(urlOrPath).toURI().normalize()).getUri();
      // e.printStackTrace();
    }
    return uri;
  }

  public static URI tryToResolveAndNormalizeRelativePath(String url, File baseDir) {
    URI uri = null;
    try {
      uri = new URL(baseDir.toURL(), url).toURI().normalize();
    } catch (Exception e) {
      try {
        uri = new URL(baseDir.toURL(), LinkController.createHyperlink(url).getUri().toString())
            .toURI().normalize();
      } catch (Exception e1) {
      }
    }
    return uri;
  }



}
