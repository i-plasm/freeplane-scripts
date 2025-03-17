// @ExecutionModes({ON_SINGLE_NODE="/main_menu/i-plasm/Search-Filter-Associations"})

package scripts

/*
 * Info & Discussion: https://github.com/freeplane/freeplane/discussions/2344
 *
 * Last Update: 2025-03-17
 *
 * ---------
 *
 * MapCrawler: Freeplane tool for searching across different map scopes, and quick inspection of results
 *
 * Copyright (C) 2025 bbarbosa
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
 *
 */

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.LayoutManager
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Collectors
import java.util.zip.CRC32
import javax.swing.AbstractAction
import javax.swing.AbstractButton
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.ButtonModel
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JProgressBar
import javax.swing.JRadioButton
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.JSeparator
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JTree
import javax.swing.JViewport
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.border.Border
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellRenderer
import javax.swing.tree.TreePath
import org.freeplane.api.Loader
import org.freeplane.api.MindMap
import org.freeplane.api.Node
import org.freeplane.core.resources.ResourceController
import org.freeplane.core.ui.components.HSLColorConverter
import org.freeplane.core.ui.components.TagIcon
import org.freeplane.core.ui.components.UITools
import org.freeplane.core.util.FreeplaneVersion
import org.freeplane.core.util.Hyperlink
import org.freeplane.core.util.MenuUtils
import org.freeplane.features.icon.IconController
import org.freeplane.features.icon.NamedIcon
import org.freeplane.features.icon.mindmapmode.MIconController
import org.freeplane.features.map.NodeModel
import org.freeplane.features.mode.mindmapmode.MModeController
import org.freeplane.features.styles.LogicalStyleController.StyleOption
import org.freeplane.features.url.UrlManager
import org.freeplane.plugin.script.proxy.MapProxy
import org.freeplane.plugin.script.proxy.ScriptUtils
import org.freeplane.view.swing.map.MapView
import groovy.transform.Field

@Field static FreeplaneMapCrawler mapCrawler
@Field static boolean emojisNotDetectedInitially = false
@Field static boolean isReadNotPermittedInitially = false
@Field static String minimumFreeplaneVersionSupported = "1.12.6"

if (!validateEnvironment()) return

  if (mapCrawler == null) {
    mapCrawler =  new FreeplaneMapCrawler()
    return
  }

mapCrawler.show()

boolean validateEnvironment() {
  if (FreeplaneVersion.getVersion().isOlderThan(FreeplaneVersion.getVersion(minimumFreeplaneVersionSupported))) {
    JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "This tool currently supports only freeplane versions starting from 1.12.6. However, in future releases older freeplane versions will probably be supported.",
        FreeplaneMapCrawler.PLUGIN_NAME, JOptionPane.WARNING_MESSAGE)
    return false
  }

  if (!ResourceController.getResourceController().getBooleanProperty("execute_scripts_without_file_restriction")) {
    isReadNotPermittedInitially = true
    JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "You need to activate the following script permissions: Please check the option 'Preferences\u2026->Plugins->Scripting->Permit file/read operations'. Then restart Freeplane.",
        FreeplaneMapCrawler.PLUGIN_NAME + " - Action required", JOptionPane.WARNING_MESSAGE)
    return false
  } else if (isReadNotPermittedInitially) {
    JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "Please restart Freeplane.",
        FreeplaneMapCrawler.PLUGIN_NAME + " - Action required", JOptionPane.WARNING_MESSAGE)
    return false
  }

  if (!ResourceController.getResourceController().getBooleanProperty("add_emojis_to_menu")) {
    emojisNotDetectedInitially = true
    JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "You need to activate emojis in order to use this tool. Please check the option 'Tools->Preferences\u2026->Appearance->Icons->Add emojis to menu'. Then restart Freeplane.",
        FreeplaneMapCrawler.PLUGIN_NAME + " - Action required", JOptionPane.WARNING_MESSAGE)
    return false
  } else if (emojisNotDetectedInitially) {
    JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "Please restart Freeplane.",
        FreeplaneMapCrawler.PLUGIN_NAME + " - Action required", JOptionPane.WARNING_MESSAGE)
    return false
  }

  return true
}

class FreeplaneMapCrawler {
  private String baseDir = ""
  private JDialog frame
  private JTextField searchField
  private JButton searchButton
  private JButton aboutButton
  private JTable resultsTable
  private JScrollPane resultsScrollPane
  private PreviewPane contentsPreviewer
  private JScrollPane previewScrollPane
  private JPanel previewPane
  private JPanel tagViewer
  private JLabel statusLabel
  private JPanel statusPane
  private JPanel progressPane
  private  JProgressBar progressBar
  private ButtonGroup searchTargetBtnGroup
  private JPanel breadCrumbPanel
  private JPanel centerPanel

  private boolean matchCase
  private boolean wholeWord

  private static boolean isDev = false

  private static final PLUGIN_NAME = "MapCrawler"
  private static final PLUGIN_VERSION = "0.7.0"
  private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor()
  private static final Map<String, MindMap> LOADED_MAPS = new ConcurrentHashMap()
  private static final Map<String, Long> LOADED_MAPS_MODIFICATION_DATE = new ConcurrentHashMap()
  private static final Map<String, Color> PATH_COLORS = new HashMap()

  enum SearchTarget{
    ALL, CORE, DETAILS, NOTE
  }

  FreeplaneMapCrawler() {
    render()
  }

  void render() {
    frame = new JDialog(UITools.getCurrentFrame())
    frame.setModal(false)

    resultsScrollPane = new JScrollPane()
    resultsTable = createResultsList()
    resultsScrollPane.setViewportView(resultsTable)

    Color topBorderColor = isLightLaF() ? Color.GRAY : Color.DARK_GRAY
    Border border = BorderFactory.createMatteBorder(1, 0, 0, 0,
        new Color(topBorderColor.getRed(),topBorderColor.getGreen(), topBorderColor.getBlue(),100))

    resultsScrollPane.setBorder(border)
    resultsScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS)

    Color previewPanelBG = isLightLaF() ? Color.WHITE : UIManager.getColor("Panel.background").darker().darker().darker()
    previewScrollPane = new JScrollPane()
    contentsPreviewer = new PreviewPane(isLightLaF(), previewPanelBG, {node -> goToNode(getNodeModel(node))})
    previewScrollPane.setViewportView(contentsPreviewer)
    previewScrollPane.setMinimumSize(new Dimension(300, 200))
    previewScrollPane.setPreferredSize(new Dimension(300, 200))
    previewScrollPane.getViewport().setBackground(previewPanelBG)
    previewScrollPane.setBorder(BorderFactory.createEmptyBorder())
    tagViewer = new JPanel(new WrapLayout(FlowLayout.CENTER))
    tagViewer.setBackground(previewPanelBG)
    tagViewer.setBorder( BorderFactory.createEmptyBorder(5, 0, 0, 0))
    previewPane = new JPanel(new BorderLayout())
    previewPane.setBackground(previewPanelBG)
    previewScrollPane.setBackground(previewPanelBG)

    previewPane.setBorder(border)

    breadCrumbPanel = new JPanel(new BorderLayout())

    previewPane.add(previewScrollPane, BorderLayout.CENTER)
    previewPane.add(tagViewer, BorderLayout.NORTH)

    centerPanel = new JPanel(new BorderLayout())
    centerPanel.add(breadCrumbPanel, BorderLayout.NORTH)
    centerPanel.add(previewPane, BorderLayout.EAST)
    centerPanel.add(resultsScrollPane, BorderLayout.CENTER)

    JPanel searchPanel = new JPanel(new BorderLayout())
    searchPanel.setBorder( BorderFactory.createEmptyBorder(0, 0, 7, 0))
    searchField = new JTextField(20)
    searchField.addActionListener({ l -> requestSearch()})
    searchButton = new JButton(MenuUtils.getMenuItemIcon('IconAction.' + "emoji-1F50D"))

    JPanel northSearchPanel = new JPanel()
    northSearchPanel.add(searchField)
    northSearchPanel.add(searchButton)

    JPanel searchOptnsPanel = new JPanel(new WrapLayout())

    JRadioButton allBtn = new JRadioButton("all")
    allBtn.setActionCommand(SearchTarget.ALL.toString())
    JRadioButton coreBtn = new JRadioButton("core")
    coreBtn.setActionCommand(SearchTarget.CORE.toString())
    JRadioButton noteBtn = new JRadioButton("note")
    noteBtn.setActionCommand(SearchTarget.NOTE.toString())
    JRadioButton detailsBtn = new JRadioButton("details")
    detailsBtn.setActionCommand(SearchTarget.DETAILS.toString())

    allBtn.setSelected(true)

    searchTargetBtnGroup = new ButtonGroup()
    searchTargetBtnGroup.add(allBtn)
    searchTargetBtnGroup.add(coreBtn)
    searchTargetBtnGroup.add(noteBtn)
    searchTargetBtnGroup.add(detailsBtn)

    searchOptnsPanel.add(allBtn)
    searchOptnsPanel.add(coreBtn)
    searchOptnsPanel.add(noteBtn)
    searchOptnsPanel.add(detailsBtn)

    JSeparator separator = new JSeparator(SwingConstants.VERTICAL)
    separator.setMinimumSize(new Dimension(2, 15))
    separator.setPreferredSize(new Dimension(2, 15))

    searchOptnsPanel.add(separator)

    JCheckBox matchCaseCheck = new JCheckBox("Aa")
    matchCaseCheck.setToolTipText("Match case")
    matchCaseCheck.addActionListener({ l ->
      matchCase = ((JCheckBox) l.getSource()).isSelected()
    })
    JCheckBox wholeWordCheck = new JCheckBox("WW")
    wholeWordCheck.setToolTipText("Whole word")
    wholeWordCheck.addActionListener({ l ->
      wholeWord = ((JCheckBox) l.getSource()).isSelected()
    })

    searchOptnsPanel.add(matchCaseCheck)
    searchOptnsPanel.add(wholeWordCheck)

    searchPanel.add(northSearchPanel, BorderLayout.NORTH)
    searchPanel.add(searchOptnsPanel, BorderLayout.SOUTH)

    searchButton.addActionListener{ l ->
      requestSearch()
    }
    statusLabel = new JLabel(" ")
    statusLabel.setForeground(isLightLaF()? Color.BLACK : Color.WHITE)
    aboutButton = new JButton(MenuUtils.getMenuItemIcon('IconAction.' + "emoji-2139"))
    aboutButton.addActionListener({l -> displayAbout(frame)} )

    statusPane = new JPanel(new FlowLayout(FlowLayout.LEFT))
    //statusPane.setLayout(new BoxLayout(statusPane, BoxLayout.Y_AXIS))

    JButton folderBtn = new JButton(MenuUtils.getMenuItemIcon('IconAction.' + "emoji-1F4C2"))
    folderBtn.setText(baseDir.isBlank()? "Select folder..." : "..." + File.separator + new File(baseDir).getName())
    folderBtn.addActionListener({l -> showFolderChooseDialog(folderBtn)})
    folderBtn.setToolTipText(baseDir)
    statusPane.add(aboutButton)
    statusPane.add(folderBtn)
    statusPane.add(statusLabel)

    progressPane = new JPanel(new BorderLayout())
    progressBar = new JProgressBar()
    progressBar.setString("processing...")
    progressBar.setStringPainted(true)
    progressBar.putClientProperty("JProgressBar.largeHeight", true)
    progressBar.putClientProperty("JProgressBar.repaintInterval", 200)

    progressPane.add(progressBar, BorderLayout.CENTER)

    frame.getContentPane().add(searchPanel, BorderLayout.NORTH)
    frame.getContentPane().add(centerPanel, BorderLayout.CENTER)
    frame.getContentPane().add(statusPane, BorderLayout.SOUTH)

    UITools.getCurrentFrame().addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent e) {
            frame.dispose()
          }
        }
        )

    frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK),
        "focusSearch")
    frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("F1"),
        "focusSearch")
    frame.getRootPane().getActionMap().put("focusSearch", new AbstractAction() {
          public void actionPerformed(ActionEvent e) {
            focusSearchField()
          }
        })

    frame.addWindowFocusListener(new WindowAdapter() {
          @Override
          public void windowGainedFocus(WindowEvent e) {
            super.windowGainedFocus(e)
            // This will trigger an update of the contents of each individual cell of the results list (according to its table cell renderer)
            frame.revalidate()
            frame.repaint()
          }
        })

    frame.setTitle(PLUGIN_NAME)
    frame.setSize(new Dimension(600, 600))
    //frame.pack()
    frame.setLocationRelativeTo(null)
    frame.setVisible(true)
  }

  private void requestSearch() {
    if (searchField.getText().isBlank()) {
      return
    }
    searchButton.setEnabled(false)
    searchField.setEnabled(false)
    ((DefaultTableModel) resultsTable.getModel()).setRowCount(0)
    statusLabel.setText(" ")
    contentsPreviewer.removeAll()
    tagViewer.removeAll()
    previewPane.revalidate()
    setActivateAndResetProgressBar(true)
    SearchTarget searchTarget = SearchTarget.valueOf(searchTargetBtnGroup.getSelection().getActionCommand())
    progressBar.setValue(10)
    progressBar.revalidate()
    progressBar.repaint()
    SwingUtilities.invokeLater({searchConcurrent(searchField.getText().trim(), searchTarget)})
  }

  private void showFolderChooseDialog(JButton callerBtn) {
    JPanel panel = new JPanel()
    JButton choiceBtn = new JButton("Select folder...")
    JTextField choice = new JTextField(50)
    choice.setText(baseDir)
    panel.add(choice)
    panel.add(choiceBtn)

    choiceBtn.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            JFileChooser fileChooser = new JFileChooser()
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY)
            int option = fileChooser.showOpenDialog(frame)
            if(option == JFileChooser.APPROVE_OPTION){
              File file = fileChooser.getSelectedFile()
              choice.setText(file.toString())
            }
          }
        })

    int resp = JOptionPane.showConfirmDialog(frame, panel, "Select folder...", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null)
    if (resp == JOptionPane.OK_OPTION) {
      String canditate = choice.getText().trim()
      if (canditate.isBlank()) {
        JOptionPane.showMessageDialog(frame, "Please indicate a valid path", "Bad input", JOptionPane.ERROR_MESSAGE)
        return
      }
      baseDir = canditate

      callerBtn.setText("..." + File.separator + new File(baseDir).getName())
      callerBtn.setToolTipText(baseDir)
    }
  }

  private void searchConcurrent(String keyword, SearchTarget target) {
    List<Path> mms
    try {
      mms = findByFileExtension(Paths.get(baseDir), ".mm")
    } catch ( Exception e) {
      doWhenSearchHasFinished()
      JOptionPane.showMessageDialog(frame, "Provided path cannot be accessed or processed: " + baseDir + " . Please set a valid base directory. " + "\n\nCause: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE)
      return
    }

    if (mms.isEmpty()) {
      doWhenSearchHasFinished()
      JOptionPane.showMessageDialog(frame, "No map was found in the provided directory: " + baseDir)
      return
    }

    for (Path path : mms) {
      progressBar.setValue((int) (100/(mms.size())*(mms.indexOf(path)+1)))
      progressBar.repaint()

      MindMap map
      map = LOADED_MAPS.compute(path.toString(), { key, val ->
        return getMindMap(path.toFile())
      })
      LOADED_MAPS_MODIFICATION_DATE.compute(path.toString(), { key, val ->
        return LOADED_MAPS.get(key).getFile().lastModified()
      })

      PATH_COLORS.putIfAbsent(path.toString(), determineStringColor(path.toString()))

      // We can not use the loop variable path since it is mutable
      final Path loadedPath = path
      EXECUTOR.execute({
        SwingUtilities.invokeLater({
          progressBar.setValue((int) (100/(mms.size())*(mms.indexOf(loadedPath)+1)))
          progressBar.repaint()
        })

        try {
          List<? extends Node> foundNodes = map.getRoot().findAll()
              .stream().filter{ mapNode ->
                return isNodeAMatch(keyword, mapNode, target)
              }.collect(Collectors.toList())

          List<NodeModel>  items = foundNodes.stream().map{ match ->
            ((MapProxy) match.getMindMap()).getDelegate().getNodeForID(match.getId())
          }.collect(Collectors.toList())

          SwingUtilities.invokeLater({
            int initialRowCount = resultsTable.getModel().getRowCount()
            for (NodeModel item : items) {
              appendMatch(item)
            }
            if (initialRowCount == 0 && !items.isEmpty()) {
              resultsTable.setRowSelectionInterval(0, 0)
              SwingUtilities.invokeLater({resultsTable.requestFocusInWindow()})
            }
            if (mms.indexOf(loadedPath) == mms.size() - 1) {
              SwingUtilities.invokeLater({
                doWhenSearchHasFinished()
              })
            }
          })

          return
        } catch (Exception e) {
          doWhenSearchHasFinished()
          JOptionPane.showMessageDialog(frame, "An unexpected error occured while searching map at " + loadedPath + "\n\nCause: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE)
          throw new RuntimeException(e)
        }
      })
    }
  }

  private void doWhenSearchHasFinished() {
    if (resultsTable.getModel().getRowCount() == 0) {
      breadCrumbPanel.removeAll()
      breadCrumbPanel.revalidate()
      breadCrumbPanel.repaint()
    }
    searchButton.setEnabled(true)
    searchField.setEnabled(true)
    setActivateAndResetProgressBar(false)
  }

  private boolean isNodeAMatch(String query, Node mapNode, SearchTarget target) {
    boolean coreMatches = false
    boolean noteMatches = false
    boolean detailsMatches = false

    if (target == SearchTarget.ALL || target == SearchTarget.CORE) {
      String baseTxt = mapNode.getPlainText()
      coreMatches = textContainsMatch(baseTxt, query)
      if (coreMatches) {
        return true
      }
    }

    if (target == SearchTarget.ALL || target == SearchTarget.DETAILS) {
      String baseTxt = mapNode.details == null ? "" : mapNode.details.plain
      detailsMatches = textContainsMatch(baseTxt, query)
      if (detailsMatches) {
        return true
      }
    }

    if (target == SearchTarget.ALL || target == SearchTarget.NOTE) {
      String baseTxt = mapNode.note == null ? "" : mapNode.note.plain
      noteMatches =  textContainsMatch(baseTxt, query)
      if (noteMatches) {
        return true
      }
    }
    return false
  }

  private boolean textContainsMatch(String baseTxt, String query) {
    if (!matchCase) {
      baseTxt = baseTxt.toLowerCase()
      query = query.toLowerCase()
    }
    if (wholeWord) {
      return baseTxt.matches(".*\\b\\Q" + query + "\\E\\b.*")
    } else {
      return baseTxt.contains(query)
    }
  }

  void focusSearchField() {
    SwingUtilities.invokeLater({
      searchField.requestFocusInWindow()
      searchField.selectAll()
    })
  }

  void show() {
    frame.setVisible(true)
    focusSearchField()
  }

  private JTable createResultsList() {
    JTable table = new JTable(0, 1)
    table.setDefaultEditor(Object.class, null)
    table.setIntercellSpacing(new Dimension(0, 1))
    table.setTableHeader(null)
    int cellMargin = 5
    Color bgColor = isLightLaF() ? UIManager.getColor("Table.background") : UIManager.getColor("Table.background").darker().darker() //Color.WHITE :UIManager.getColor("Panel.background").darker()
    Color fgColor = UIManager.getColor("Table.foreground")
    Color selectedBgColor = UIManager.getColor("Table.selectionBackground")
    Color selectedFgColor = UIManager.getColor("Table.selectionForeground")
    Color intercellBorderColor = isLightLaF() ? UIManager.getColor("Table.gridColor").darker() :UIManager.getColor("Table.gridColor").brighter()
    table.setBackground(bgColor)
    table.setGridColor(intercellBorderColor)
    table.getColumnModel().getColumn(0).setCellRenderer(new NodeCellWrapRenderer(cellMargin, bgColor, selectedBgColor, fgColor, selectedFgColor))
    //table.setIntercellSpacing(new Dimension(0, cellMargin));
    table.setBorder(BorderFactory.createEmptyBorder())
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)

    table.addMouseListener(new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
              JTable source = (JTable) e.getSource()
              int row = source.getSelectedRow()
              NodeModel selectedNode = (NodeModel) source.getModel().getValueAt(row, 0)[0]
              goToNode(selectedNode)
            }
          }
        })

    ListSelectionModel selectionModel = table.getSelectionModel()

    selectionModel.addListSelectionListener(new ListSelectionListener() {
          public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting())
              return
            ListSelectionModel lsm = (ListSelectionModel)e.getSource()
            int selectedRow
            if (lsm.isSelectionEmpty()) {
              selectedRow = -1
            } else {
              selectedRow = lsm.getMinSelectionIndex()
              NodeModel selectedNode = (NodeModel) table.getModel().getValueAt(selectedRow, 0)[0]
              List<TagIcon> tags = FreeplaneMapCrawler.iconController().getTagIcons(selectedNode)
              Collection<NamedIcon> icons = FreeplaneMapCrawler.iconController().getIcons(selectedNode, StyleOption.FOR_UNSELECTED_NODE)

              String path = selectedNode.getMap().getFile().toString()
              Node apiNode = FreeplaneMapCrawler.LOADED_MAPS.get(path).node(selectedNode.createID())

              List<BreadcrumbDetails> breadCrumbStringsWithToolTips = apiNode.getPathToRoot().stream()
                  .map({ node ->
                    return new BreadcrumbDetails( org.freeplane.core.util.TextUtils.getShortText(node.getPlainText(), 8, "\u2026"),//"\u2026"
                        node.getPlainText(), {
                          List<TagIcon> tagsFromSelectedBreadcrumb = FreeplaneMapCrawler.iconController().getTagIcons(getNodeModel(node))
                          Collection<NamedIcon> iconsFromSelectedBreadcrumb = FreeplaneMapCrawler.iconController().getIcons(getNodeModel(node), StyleOption.FOR_UNSELECTED_NODE)
                          previewNodeAction(node, getNodeModel(node))
                          previewTagsAndIconsAction(tagsFromSelectedBreadcrumb, iconsFromSelectedBreadcrumb)
                          frame.getContentPane().revalidate()
                          frame.getContentPane().repaint()
                        })
                  })
                  .collect(Collectors.toList())

              breadCrumbPanel.removeAll()
              breadCrumbPanel.add(BreadcrumbList.makeBreadcrumbListWithToolTip(breadCrumbStringsWithToolTips, isLightLaF() ?  UIManager.getColor("ProgressBar.foreground").brighter() : UIManager.getColor("ProgressBar.foreground").darker()), BorderLayout.CENTER)

              breadCrumbPanel.revalidate()
              breadCrumbPanel.repaint()

              statusLabel.setText(path.substring(path.indexOf(baseDir)+ baseDir.length()))

              previewNodeAction(apiNode, selectedNode)
              previewTagsAndIconsAction(tags, icons)

              frame.getContentPane().revalidate()
              frame.getContentPane().repaint()
            }
          }
        })
    return table
  }

  private void previewTagsAndIconsAction(List<TagIcon> tags, Collection<NamedIcon> icons) {
    tagViewer.removeAll()

    if (tags.isEmpty() && icons.isEmpty()) {
      previewPane.remove(tagViewer)
    } else {
      previewPane.add(tagViewer, BorderLayout.NORTH)
      for (NamedIcon icon :  icons) {
        tagViewer.add(new JLabel(icon.getIcon()))
      }
      for (TagIcon tag :  tags) {
        tagViewer.add(new JLabel(tag))
      }
      tagViewer.revalidate()
      tagViewer.repaint()
    }
  }

  private void previewNodeAction(Node apiNode, NodeModel nodeModel) {
    contentsPreviewer.previewNode( apiNode,
        nodeModel, false)
    JScrollBar bar = previewScrollPane.getVerticalScrollBar()
    SwingUtilities.invokeLater({bar.setValue(bar.getMinimum())})
  }

  private void setActivateAndResetProgressBar(boolean shouldActivate) {
    progressBar.setValue(0)
    if (shouldActivate) {
      frame.getContentPane().remove(statusPane)
      frame.getContentPane().add(progressPane, BorderLayout.SOUTH)
    } else {
      frame.getContentPane().remove(progressPane)
      frame.getContentPane().add(statusPane, BorderLayout.SOUTH)
    }
    frame.revalidate()
    frame.repaint()
  }

  private void appendMatch(NodeModel match) {
    DefaultTableModel model = (DefaultTableModel) resultsTable.getModel()
    model.addRow([match])
  }

  private void displayInfo(Component comp) {
    MenuHelper.FloatingMsgPopup floatingPopup = new MenuHelper.FloatingMsgPopup()

    JMenuItem contentsMenuItem = MenuHelper.createHeaderNoticeMenuItem("<b>" + PLUGIN_NAME +
        "</b>" + "<br><i>version " + PLUGIN_VERSION + "</i>",
        PLUGIN_NAME + " Info & Updates")
    floatingPopup.add(contentsMenuItem)

    String visitWebsite = MenuHelper.floatingMenuItemUnderlinedActionHTML("Discussion & Updates:",
        "https://github.com/freeplane/freeplane/discussions/2344")
    JMenuItem visitWebsiteItem =
        createLinkMenuItem("https://github.com/freeplane/freeplane/discussions/2344", visitWebsite)
    floatingPopup.add(visitWebsiteItem)

    floatingPopup.show(comp, 0, 0)
  }

  private void displayAbout(Component comp) {
    MenuHelper.FloatingMsgPopup floatingPopup = new MenuHelper.FloatingMsgPopup()

    JMenuItem contentsMenuItem = MenuHelper.createHeaderNoticeMenuItem("<b>" + PLUGIN_NAME +
        "</b>" + "<br><i>version " + PLUGIN_VERSION + "</i><br><br>" +
        "Freelane Tool for searching across different map scopes, and quick inspection of results.",
        "About " + PLUGIN_NAME)
    floatingPopup.add(contentsMenuItem)

    String visitWebsite = MenuHelper.floatingMenuItemUnderlinedActionHTML("Discussion & Updates:",
        "https://github.com/freeplane/freeplane/discussions/2344")
    JMenuItem visitWebsiteItem =
        createLinkMenuItem("https://github.com/freeplane/freeplane/discussions/2344", visitWebsite)
    floatingPopup.add(visitWebsiteItem)
    floatingPopup.add(visitWebsiteItem)

    JMenuItem licenseMenuItem = MenuHelper.createContentNoticeMenuItem(PLUGIN_NAME + " " +
        PLUGIN_VERSION +
        " - Freeplane tool for searching across different map scopes, and quick inspection of results." +
        "<br><br>Copyright (C) 2025 bbarbosa" +
        "<br>This program is free software: you can redistribute it and/or modify it under the terms of the" +
        " GNU General Public License as published by the Free Software Foundation, either version 3 of the" +
        " License, or (at your option) any later version." +
        "<br>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without" +
        " even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU" +
        " General Public License for more details." +
        "<br>You should have received a copy of the GNU General Public License along with this program. If" +
        " not, see &lt;https://www.gnu.org/licenses/&gt;.", "License")
    floatingPopup.add(licenseMenuItem)

    floatingPopup.show(comp, 0, 0)
  }

  static class PreviewPane extends ScrollablePanel {
    private JTextArea textAreaCore
    private JTextArea textAreaDetails
    private JTextArea textAreaNote
    private Color bgColor
    private boolean isLightLaf

    private Node currentNode
    private Consumer<Node> coreAction

    public PreviewPane(boolean isLightLaf, Color bgColor, Consumer<Node> coreAction) {
      super()
      this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS))//
      this.isLightLaf= isLightLaf
      this.bgColor = bgColor
      this.coreAction = coreAction

      this.setScrollableWidth( PreviewPane.ScrollableSizeHint.FIT )

      textAreaCore = new JTextArea()
      textAreaCore.setLineWrap(true)
      textAreaCore.setWrapStyleWord(true)

      textAreaCore.setMargin(new Insets(5, 5, 5, 5))
      textAreaCore.setEditable(false)
      Font originalFont = textAreaCore.getFont()
      textAreaCore.setFont(originalFont.deriveFont((float) (originalFont.getSize()*1.3f)))

      Color textAreaBG = bgColor
      textAreaCore.setBackground(textAreaBG)
      textAreaCore.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20))
      textAreaCore.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
              textAreaCore.setBackground(isLightLaf? new Color(166, 217, 217): bgColor.brighter().brighter()) //UIManager.getColor("ScrollBar.hoverThumbColor")
            }
            @Override
            public void mouseExited(MouseEvent e) {
              textAreaCore.setBackground(textAreaBG)
            }
            @Override
            public void mouseClicked(MouseEvent e) {
              coreAction.accept(currentNode)
            }
          })

      textAreaNote = new JTextArea()
      textAreaNote.setLineWrap(true)
      textAreaNote.setWrapStyleWord(true)
      textAreaNote.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20))
      textAreaNote.setBackground(textAreaBG)
      textAreaNote.setEditable(false)
      this.textAreaDetails = new JTextArea()
      textAreaDetails.setText("Details")
      textAreaDetails.setLineWrap(true)
      textAreaDetails.setLineWrap(true)
      textAreaDetails.setBackground(textAreaBG)
      textAreaDetails.setBorder(BorderFactory.createEmptyBorder(20, 20, 0, 20))
      textAreaDetails.setMargin(new Insets(13, 13, 13, 13))
      textAreaDetails.setEditable(false)
      textAreaDetails.setFont(originalFont.deriveFont(Font.ITALIC))

      setBackground(textAreaBG)
    }

    void previewNode(Node node, NodeModel nodeModel, boolean addBeginningSeparator) {
      currentNode = node
      Component[] comps = getComponents()
      for (Component comp : getComponents()) {
        if (comp instanceof PanelSeparator) {
          remove(comp)
        }
      }
      Color pathColor = FreeplaneMapCrawler.PATH_COLORS.get( node.mindMap.getFile().toString())

      if (addBeginningSeparator) {
        add(new PanelSeparator(pathColor))
      }

      // Core
      textAreaCore.setText(node.getPlainText())//HtmlUtils.htmlToPlain(node.getTransformedText().trim(), true, false)
      add(textAreaCore)

      // Details
      String detailsTxt  = node.getDetails()!= null ? node.getDetails().getPlain().trim() :""
      if (!detailsTxt.isEmpty()) {
        textAreaDetails.setText(detailsTxt)
        add(new PanelSeparator(pathColor))
        add(textAreaDetails)
      }else {
        remove(textAreaDetails)
      }

      // Note
      String noteTxt  = node.getNote() != null ? node.getNote().getPlain().trim() :""
      if (!noteTxt.isEmpty()) {
        textAreaNote.setText(noteTxt)
        if (detailsTxt.isEmpty()) add(new PanelSeparator(pathColor))
        add(textAreaNote)
      }else {
        remove(textAreaNote)
      }

      revalidate()
      repaint()
    }
  }

  /**
   * 
   *  Modification of: ResultsCellWrapRenderer, from the "jhelperutils" library
   *  
   *  https://github.com/i-plasm/jhelperutils/
   *  
   *  ---
   *
   *  Note: NodeCellWrapRenderer can only be used for a single table column
   */
  static class NodeCellWrapRenderer extends JTextArea implements TableCellRenderer {
    private int margin
    private Color bgColor
    private Color fgColor
    private Color selectedBgColor
    private Color selectedFgColor

    private static final Map<String, Border> BORDER_CACHE = new HashMap()
    private static final Function<String, Border> BORDER_FUNCTION = { key ->
      return getBorderForColor(FreeplaneMapCrawler.PATH_COLORS.get(key))
    }

    public NodeCellWrapRenderer(int margin, Color bgColor, Color selectedBgColor, Color fgColor, Color selectedFgColor) {
      setLineWrap(true)
      setWrapStyleWord(true)
      this.margin = margin
      this.bgColor = bgColor
      this.selectedBgColor = selectedBgColor
      this.selectedFgColor = selectedFgColor
      this.fgColor = fgColor
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      NodeModel nodeItem = (NodeModel) value[0]
      File mapFile = nodeItem.map.getFile()
      String mapName = mapFile.getName()
      Border border = BORDER_CACHE.computeIfAbsent(mapFile.toString(), BORDER_FUNCTION)
      Node apiNode = FreeplaneMapCrawler.LOADED_MAPS.get(mapFile.toString()).node(nodeItem.createID())
      String coreStr = apiNode.getPlainText().replaceAll("\\v+", " ")
      String shortCoreStr = org.freeplane.core.util.TextUtils.getShortText(coreStr, 50, "\u2026")
      setText( (row + 1)  + (row >= 9 ? "  " : "   ") + shortCoreStr + "\n" + "(" + mapName + ") ")
      setToolTipText(coreStr)

      setSize(table.getColumnModel().getColumn(column).getWidth(), table.getRowHeight(row))
      int preferredHeight = getPreferredSize().height + margin
      if (table.getRowHeight(row) != preferredHeight) {
        table.setRowHeight(row, preferredHeight)
      }

      setBorder(border)
      if (isSelected) {
        setBackground(selectedBgColor)
        setForeground(selectedFgColor)
      } else {
        setBackground(bgColor)
        setForeground(fgColor)
      }

      return this
    }

    private static Border getBorderForColor(Color color) {
      Border border = BorderFactory.createMatteBorder(0, 5, 0, 0, color)// BorderFactory.createLineBorder(Color.RED)
      return BorderFactory.createCompoundBorder(border,
          BorderFactory.createEmptyBorder(0, 10, 0, 10))
    }
  }

  /**
   *  A panel that implements the Scrollable interface. This class allows you
   *  to customize the scrollable features by using newly provided setter methods
   *  so you don't have to extend this class every time.
   *
   *  Scrollable amounts can be specifed as a percentage of the viewport size or
   *  as an actual pixel value. The amount can be changed for both unit and block
   *  scrolling for both horizontal and vertical scrollbars.
   *
   *  The Scrollable interface only provides a boolean value for determining whether
   *  or not the viewport size (width or height) should be used by the scrollpane
   *  when determining if scrollbars should be made visible. This class supports the
   *  concept of dynamically changing this value based on the size of the viewport.
   *  In this case the viewport size will only be used when it is larger than the
   *  panels size. This has the effect of ensuring the viewport is always full as
   *  components added to the panel will be size to fill the area available,
   *  based on the rules of the applicable layout manager of course.
   *  
   *  @author Rob Camick
   */
  static class ScrollablePanel extends JPanel
  implements Scrollable, SwingConstants {
    public enum ScrollableSizeHint {
      NONE,
      FIT,
      STRETCH
    }

    public enum IncrementType {
      PERCENT,
      PIXELS
    }

    private ScrollableSizeHint scrollableHeight = ScrollableSizeHint.NONE
    private ScrollableSizeHint scrollableWidth  = ScrollableSizeHint.NONE

    private IncrementInfo horizontalBlock
    private IncrementInfo horizontalUnit
    private IncrementInfo verticalBlock
    private IncrementInfo verticalUnit

    /**
     *  Default constructor that uses a FlowLayout
     */
    public ScrollablePanel() {
      this( new FlowLayout() )
    }

    /**
     *  Constuctor for specifying the LayoutManager of the panel.
     *
     *  @param layout the LayountManger for the panel
     */
    public ScrollablePanel(LayoutManager layout) {
      super( layout )

      IncrementInfo block = new IncrementInfo(IncrementType.PERCENT, 100)
      IncrementInfo unit = new IncrementInfo(IncrementType.PERCENT, 10)

      setScrollableBlockIncrement(HORIZONTAL, block)
      setScrollableBlockIncrement(VERTICAL, block)
      setScrollableUnitIncrement(HORIZONTAL, unit)
      setScrollableUnitIncrement(VERTICAL, unit)
    }

    /**
     *  Get the height ScrollableSizeHint enum
     *
     *  @return the ScrollableSizeHint enum for the height
     */
    public ScrollableSizeHint getScrollableHeight() {
      return scrollableHeight
    }

    /**
     *  Set the ScrollableSizeHint enum for the height. The enum is used to
     *  determine the boolean value that is returned by the
     *  getScrollableTracksViewportHeight() method. The valid values are:
     *
     *  ScrollableSizeHint.NONE - return "false", which causes the height
     *      of the panel to be used when laying out the children
     *  ScrollableSizeHint.FIT - return "true", which causes the height of
     *      the viewport to be used when laying out the children
     *  ScrollableSizeHint.STRETCH - return "true" when the viewport height
     *      is greater than the height of the panel, "false" otherwise.
     *
     *  @param scrollableHeight as represented by the ScrollableSizeHint enum.
     */
    public void setScrollableHeight(ScrollableSizeHint scrollableHeight) {
      this.scrollableHeight = scrollableHeight
      revalidate()
    }

    /**
     *  Get the width ScrollableSizeHint enum
     *
     *  @return the ScrollableSizeHint enum for the width
     */
    public ScrollableSizeHint getScrollableWidth() {
      return scrollableWidth
    }

    /**
     *  Set the ScrollableSizeHint enum for the width. The enum is used to
     *  determine the boolean value that is returned by the
     *  getScrollableTracksViewportWidth() method. The valid values are:
     *
     *  ScrollableSizeHint.NONE - return "false", which causes the width
     *      of the panel to be used when laying out the children
     *  ScrollableSizeHint.FIT - return "true", which causes the width of
     *      the viewport to be used when laying out the children
     *  ScrollableSizeHint.STRETCH - return "true" when the viewport width
     *      is greater than the width of the panel, "false" otherwise.
     *
     *  @param scrollableWidth as represented by the ScrollableSizeHint enum.
     */
    public void setScrollableWidth(ScrollableSizeHint scrollableWidth) {
      this.scrollableWidth = scrollableWidth
      revalidate()
    }

    /**
     *  Get the block IncrementInfo for the specified orientation
     *
     *  @return the block IncrementInfo for the specified orientation
     */
    public IncrementInfo getScrollableBlockIncrement(int orientation) {
      return orientation == SwingConstants.HORIZONTAL ? horizontalBlock : verticalBlock
    }

    /**
     *  Specify the information needed to do block scrolling.
     *
     *  @param orientation  specify the scrolling orientation. Must be either:
     *      SwingContants.HORIZONTAL or SwingContants.VERTICAL.
     *  @paran type  specify how the amount parameter in the calculation of
     *      the scrollable amount. Valid values are:
     *      IncrementType.PERCENT - treat the amount as a % of the viewport size
     *      IncrementType.PIXEL - treat the amount as the scrollable amount
     *  @param amount  a value used with the IncrementType to determine the
     *      scrollable amount
     */
    public void setScrollableBlockIncrement(int orientation, IncrementType type, int amount) {
      IncrementInfo info = new IncrementInfo(type, amount)
      setScrollableBlockIncrement(orientation, info)
    }

    /**
     *  Specify the information needed to do block scrolling.
     *
     *  @param orientation  specify the scrolling orientation. Must be either:
     *      SwingContants.HORIZONTAL or SwingContants.VERTICAL.
     *  @param info  An IncrementInfo object containing information of how to
     *      calculate the scrollable amount.
     */
    public void setScrollableBlockIncrement(int orientation, IncrementInfo info) {
      switch(orientation) {
        case SwingConstants.HORIZONTAL:
          horizontalBlock = info
          break
        case SwingConstants.VERTICAL:
          verticalBlock = info
          break
        default:
          throw new IllegalArgumentException("Invalid orientation: " + orientation)
      }
    }

    /**
     *  Get the unit IncrementInfo for the specified orientation
     *
     *  @return the unit IncrementInfo for the specified orientation
     */
    public IncrementInfo getScrollableUnitIncrement(int orientation) {
      return orientation == SwingConstants.HORIZONTAL ? horizontalUnit : verticalUnit
    }

    /**
     *  Specify the information needed to do unit scrolling.
     *
     *  @param orientation  specify the scrolling orientation. Must be either:
     *      SwingContants.HORIZONTAL or SwingContants.VERTICAL.
     *  @paran type  specify how the amount parameter in the calculation of
     *               the scrollable amount. Valid values are:
     *               IncrementType.PERCENT - treat the amount as a % of the viewport size
     *               IncrementType.PIXEL - treat the amount as the scrollable amount
     *  @param amount  a value used with the IncrementType to determine the
     *                 scrollable amount
     */
    public void setScrollableUnitIncrement(int orientation, IncrementType type, int amount) {
      IncrementInfo info = new IncrementInfo(type, amount)
      setScrollableUnitIncrement(orientation, info)
    }

    /**
     *  Specify the information needed to do unit scrolling.
     *
     *  @param orientation  specify the scrolling orientation. Must be either:
     *      SwingContants.HORIZONTAL or SwingContants.VERTICAL.
     *  @param info  An IncrementInfo object containing information of how to
     *               calculate the scrollable amount.
     */
    public void setScrollableUnitIncrement(int orientation, IncrementInfo info) {
      switch(orientation) {
        case SwingConstants.HORIZONTAL:
          horizontalUnit = info
          break
        case SwingConstants.VERTICAL:
          verticalUnit = info
          break
        default:
          throw new IllegalArgumentException("Invalid orientation: " + orientation)
      }
    }

    //  Implement Scrollable interface

    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize()
    }

    public int getScrollableUnitIncrement(
        Rectangle visible, int orientation, int direction) {
      switch(orientation) {
        case SwingConstants.HORIZONTAL:
          return getScrollableIncrement(horizontalUnit, (int) visible.width)
        case SwingConstants.VERTICAL:
          return getScrollableIncrement(verticalUnit, (int) visible.height)
        default:
          throw new IllegalArgumentException("Invalid orientation: " + orientation)
      }
    }

    public int getScrollableBlockIncrement(
        Rectangle visible, int orientation, int direction) {
      switch(orientation) {
        case SwingConstants.HORIZONTAL:
          return getScrollableIncrement(horizontalBlock, (int) visible.width)
        case SwingConstants.VERTICAL:
          return getScrollableIncrement(verticalBlock, (int) visible.height)
        default:
          throw new IllegalArgumentException("Invalid orientation: " + orientation)
      }
    }

    protected int getScrollableIncrement(IncrementInfo info, int distance) {
      if (info.getIncrement() == IncrementType.PIXELS)
        return info.getAmount()
      else
        return distance * info.getAmount() / 100
    }

    public boolean getScrollableTracksViewportWidth() {
      if (scrollableWidth == ScrollableSizeHint.NONE)
        return false

      if (scrollableWidth == ScrollableSizeHint.FIT)
        return true

      //  STRETCH sizing, use the greater of the panel or viewport width

      if (getParent() instanceof JViewport) {
        return (((JViewport)getParent()).getWidth() > getPreferredSize().width)
      }

      return false
    }

    public boolean getScrollableTracksViewportHeight() {
      if (scrollableHeight == ScrollableSizeHint.NONE)
        return false

      if (scrollableHeight == ScrollableSizeHint.FIT)
        return true

      //  STRETCH sizing, use the greater of the panel or viewport height

      if (getParent() instanceof JViewport) {
        return (((JViewport)getParent()).getHeight() > getPreferredSize().height)
      }

      return false
    }

    /**
     *  Helper class to hold the information required to calculate the scroll amount.
     */
    static class IncrementInfo {
      private IncrementType type
      private int amount

      public IncrementInfo(IncrementType type, int amount) {
        this.type = type
        this.amount = amount
      }

      public IncrementType getIncrement() {
        return type
      }

      public int getAmount() {
        return amount
      }

      public String toString() {
        return
        "ScrollablePanel[" +
            type + ", " +
            amount + "]"
      }
    }
  }

  /**
   *  FlowLayout subclass that fully supports wrapping of components.
   *  
   *  Contributed from http://tips4java.wordpress.com/2008/11/06/wrap-layout/
   *  
   *  @author Rob Camick
   */
  static class WrapLayout extends FlowLayout {
    private Dimension preferredLayoutSize

    /**
     * Constructs a new <code>WrapLayout</code> with a left
     * alignment and a default 5-unit horizontal and vertical gap.
     */
    public WrapLayout() {
      super()
    }

    /**
     * Constructs a new <code>FlowLayout</code> with the specified
     * alignment and a default 5-unit horizontal and vertical gap.
     * The value of the alignment argument must be one of
     * <code>WrapLayout</code>, <code>WrapLayout</code>,
     * or <code>WrapLayout</code>.
     * @param align the alignment value
     */
    public WrapLayout(int align) {
      super(align)
    }

    /**
     * Creates a new flow layout manager with the indicated alignment
     * and the indicated horizontal and vertical gaps.
     * <p>
     * The value of the alignment argument must be one of
     * <code>WrapLayout</code>, <code>WrapLayout</code>,
     * or <code>WrapLayout</code>.
     * @param align the alignment value
     * @param hgap the horizontal gap between components
     * @param vgap the vertical gap between components
     */
    public WrapLayout(int align, int hgap, int vgap) {
      super(align, hgap, vgap)
    }

    /**
     * Returns the preferred dimensions for this layout given the
     * <i>visible</i> components in the specified target container.
     * @param target the component which needs to be laid out
     * @return the preferred dimensions to lay out the
     * subcomponents of the specified container
     */
    @Override
    public Dimension preferredLayoutSize(Container target) {
      return layoutSize(target, true)
    }

    /**
     * Returns the minimum dimensions needed to layout the <i>visible</i>
     * components contained in the specified target container.
     * @param target the component which needs to be laid out
     * @return the minimum dimensions to lay out the
     * subcomponents of the specified container
     */
    @Override
    public Dimension minimumLayoutSize(Container target) {
      Dimension minimum = layoutSize(target, false)
      minimum.width -= (getHgap() + 1)
      return minimum
    }

    /**
     * Returns the minimum or preferred dimension needed to layout the target
     * container.
     *
     * @param target target to get layout size for
     * @param preferred should preferred size be calculated
     * @return the dimension to layout the target container
     */
    private Dimension layoutSize(Container target, boolean preferred) {
      synchronized (target.getTreeLock()) {
        //  Each row must fit with the width allocated to the containter.
        //  When the container width = 0, the preferred width of the container
        //  has not yet been calculated so lets ask for the maximum.

        int targetWidth = target.getSize().width
        Container container = target

        while (container.getSize().width == 0 && container.getParent() != null) {
          container = container.getParent()
        }

        targetWidth = container.getSize().width

        if (targetWidth == 0)
          targetWidth = Integer.MAX_VALUE

        int hgap = getHgap()
        int vgap = getVgap()
        Insets insets = target.getInsets()
        int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2)
        int maxWidth = targetWidth - horizontalInsetsAndGap

        //  Fit components into the allowed width

        Dimension dim = new Dimension(0, 0)
        int rowWidth = 0
        int rowHeight = 0

        int nmembers = target.getComponentCount()

        for (int i = 0; i < nmembers; i++) {
          Component m = target.getComponent(i)

          if (m.isVisible()) {
            Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize()

            //  Can't add the component to current row. Start a new row.

            if (rowWidth + d.width > maxWidth) {
              addRow(dim, rowWidth, rowHeight)
              rowWidth = 0
              rowHeight = 0
            }

            //  Add a horizontal gap for all components after the first

            if (rowWidth != 0) {
              rowWidth += hgap
            }

            rowWidth += d.width
            rowHeight = Math.max(rowHeight, d.height)
          }
        }

        addRow(dim, rowWidth, rowHeight)

        dim.width += horizontalInsetsAndGap
        dim.height += insets.top + insets.bottom + vgap * 2

        //  When using a scroll pane or the DecoratedLookAndFeel we need to
        //  make sure the preferred size is less than the size of the
        //  target containter so shrinking the container size works
        //  correctly. Removing the horizontal gap is an easy way to do this.

        Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, target)

        if (scrollPane != null && target.isValid()) {
          dim.width -= (hgap + 1)
        }

        return dim
      }
    }

    /*
     *  A new row has been completed. Use the dimensions of this row
     *  to update the preferred size for the container.
     *
     *  @param dim update the width and height when appropriate
     *  @param rowWidth the width of the row to add
     *  @param rowHeight the height of the row to add
     */
    private void addRow(Dimension dim, int rowWidth, int rowHeight) {
      dim.width = Math.max(dim.width, rowWidth)

      if (dim.height > 0) {
        dim.height += getVgap()
      }

      dim.height += rowHeight
    }
  }



  //-------------------------------------------------------------------------------
  //---------- RESOURCES COPIED/MODIFIED FROM THE "jhelperutils" LIBRARY ----------
  //----------------- https://github.com/i-plasm/jhelperutils/ --------------------
  //-------------------------------------------------------------------------------

  static class BreadcrumbDetails {
    String text
    String tooltip
    Runnable runnable

    BreadcrumbDetails(String text, String tooltip, Runnable runnable) {
      this.text = text
      this.tooltip = tooltip
      this.runnable = runnable
    }
  }

  /**
   * 
   * Modified by: bbarbosa
   * 
   * ---
   *
   * The MIT License (MIT)
   *
   * Copyright (c) 2015 TERAI Atsuhiro
   *
   */
  static class BreadcrumbList extends JPanel {

    private static Container makeContainer(int overlap) {
      JPanel p = new JPanel(new WrapLayout(FlowLayout.LEADING, -overlap, 5)) {
            @Override
            public boolean isOptimizedDrawingEnabled() {
              return false
            }
          }
      p.setBorder(BorderFactory.createEmptyBorder(4, overlap + 4, 4, 4))
      p.setOpaque(false)
      return p
    }

    private static Component makeBreadcrumbList(List<String> list) {
      Container p = makeContainer(10 + 1)
      ButtonGroup bg = new ButtonGroup()
      list.forEach({title ->
        AbstractButton b = makeButton(null, new TreePath(title), Color.PINK)
        p.add(b)
        bg.add(b)
      })
      return p
    }

    private static Component makeBreadcrumbListWithToolTip(List<BreadcrumbDetails> list, Color hoverColor) {
      Container p = makeContainer(10 + 1)
      ButtonGroup bg = new ButtonGroup()
      list.forEach({listItem ->
        AbstractButton b = makeButton(null, new TreePath(listItem.getText()), hoverColor)
        b.setToolTipText(listItem.getTooltip())
        b.addActionListener({ l -> listItem.getRunnable().run()})
        p.add(b)
        bg.add(b)
      })
      return p
    }

    private static AbstractButton makeButton(JTree tree, TreePath path, Color color) {
      AbstractButton b = new JRadioButton(path.getLastPathComponent().toString()) {
            @Override
            public boolean contains(int x, int y) {
              return Optional.ofNullable(getIcon()).filter({ it -> ArrowToggleButtonBarCellIcon.class.isInstance(it)})
              .map({i -> ((ArrowToggleButtonBarCellIcon) i).getShape()}).map({s -> ((Shape) s).contains(x, y)})
              .orElseGet({super.contains(x, y)})
            }
          }
      if (Objects.nonNull(tree)) {
        b.addActionListener({e ->
          JRadioButton r = (JRadioButton) e.getSource()
          tree.setSelectionPath(path)
          r.setSelected(true)
        })
      }
      b.setIcon(new ArrowToggleButtonBarCellIcon())
      b.setContentAreaFilled(false)
      b.setBorder(BorderFactory.createEmptyBorder())
      b.setVerticalAlignment(SwingConstants.CENTER)
      b.setVerticalTextPosition(SwingConstants.CENTER)
      b.setHorizontalAlignment(SwingConstants.CENTER)
      b.setHorizontalTextPosition(SwingConstants.CENTER)
      b.setFocusPainted(false)
      b.setOpaque(false)
      b.setBackground(color)
      return b
    }
  }

  /**
   * 
   * Modified by: bbarbosa
   * 
   * ---
   *
   * The MIT License (MIT)
   *
   * Copyright (c) 2015 TERAI Atsuhiro
   *
   */
  static class ArrowToggleButtonBarCellIcon implements Icon {
    public static final int TH = 10 // The height of a triangle
    private static final int HEIGHT = TH * 2 + 1
    private static final int WIDTH = 100
    private Shape shape

    public Shape getShape() {
      return shape
    }

    protected Shape makeShape(Container parent, Component c, int x, int y) {
      int w = c.getWidth() - 1
      int h = c.getHeight() - 1
      double h2 = Math.round(h * 0.5)
      double w2 = TH
      Path2D p = new Path2D.Double()
      p.moveTo(0d, 0d)
      p.lineTo(w - w2, 0d)
      p.lineTo(w, h2)
      p.lineTo(w - w2, h)
      p.lineTo(0d, h)
      if (!Objects.equals(c, parent.getComponent(0))) {
        p.lineTo(w2, h2)
      }
      p.closePath()
      return AffineTransform.getTranslateInstance(x, y).createTransformedShape(p)
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Container parent = c.getParent()
      if (Objects.isNull(parent)) {
        return
      }
      shape = makeShape(parent, c, x, y)

      Color bgc = parent.getBackground()
      Color borderColor = Color.GRAY.brighter()
      if (c instanceof AbstractButton) {
        ButtonModel m = ((AbstractButton) c).getModel()
        if (m.isSelected() || m.isRollover()) {
          bgc = c.getBackground()
          borderColor = Color.GRAY
        }
      }
      Graphics2D g2 = (Graphics2D) g.create()
      g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
          RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,
          RenderingHints.VALUE_COLOR_RENDER_QUALITY)
      g2.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE)
      g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
          RenderingHints.VALUE_FRACTIONALMETRICS_ON)
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
          RenderingHints.VALUE_INTERPOLATION_BILINEAR)
      g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

      g2.setPaint(bgc)
      g2.fill(shape)
      g2.setPaint(borderColor)
      g2.draw(shape)
      g2.dispose()
    }

    @Override
    public int getIconWidth() {
      return WIDTH
    }

    @Override
    public int getIconHeight() {
      return HEIGHT
    }
  }

  static class PanelSeparator extends JPanel {
    public PanelSeparator(Color color) {
      Border border = BorderFactory.createMatteBorder(3, 0, 0, 0, color)
      setBorder(BorderFactory.createCompoundBorder(border,
          BorderFactory.createEmptyBorder(3, 0, 0, 0)))
      setBackground(new Color(0,0,0,2))
      Dimension size = new Dimension(120, 6)
      setSize(size)
      setPreferredSize(new Dimension(size))
      setMaximumSize(new Dimension(size))
    }
  }

  static class MenuHelper {

    public static class FloatingMsgPopup extends JPopupMenu {}

    public static JMenuItem makeMenuItem(String text, String name, Runnable doThis) {
      JMenuItem menuItem = new JMenuItem(text)
      menuItem.setName(name)
      menuItem.addActionListener{ l ->
        doThis.run()
      }
      return menuItem
    }

    public static String getMenuItemValidation(String defaultMenuItemText, String name,
        Runnable doThis, Callable<String> prevalidator) {
      String validation = defaultMenuItemText
      try {
        String prevalidation = prevalidator.call()
        if (prevalidation == null || !prevalidation.equals(""))
          validation = prevalidation
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace()
      }
      return validation
    }

    public static JMenuItem makeMenuItem(String text, String name, Runnable doThis,
        int maxTextLength) {
      if (text.length() > maxTextLength)
        text = text.substring(0, maxTextLength) + "..."
      JMenuItem menuItem = new JMenuItem(text)
      menuItem.setName(name)
      menuItem.addActionListener{ l ->
        doThis.run()
      }
      return menuItem
    }

    public static JMenuItem createHeaderNoticeMenuItem(String message, String title) {
      message = "<html><body width='600px'; style=\"font-size: 12px\">" +
          "[x CLOSE]&nbsp;&nbsp;&nbsp;&nbsp;" + title + "<br><br>" + message + "<br><br>" +
          "</body></html>"
      JMenuItem menuItem = new JMenuItem(message)
      return menuItem
    }

    public static JMenuItem createContentNoticeMenuItem(String message, String title) {
      message = "<html><body width='600px'; style=\"font-size: 12px\">" + "<br>" + title +
          "<br><br>" + message + "<br><br>" + "</body></html>"
      JMenuItem menuItem = new JMenuItem(message)
      return menuItem
    }

    public static String floatingMenuItemUnderlinedActionHTML(String legend,
        String actionDisplayTitle) {
      return String.format("%s%s%s", "<html><body width='600px'; style=\"font-size: 12px\">",
          legend + "<br> <a href=\"" + actionDisplayTitle + "\">" + actionDisplayTitle + "</a>",
          "</body></html>")
    }

    public static JPanel createFloatingActionPanel(String menuText, String name, String buttonText,
        Runnable runnable) {
      Box b1 = Box.createHorizontalBox()
      JPanel panel = new JPanel()
      JLabel label = new JLabel(menuText)
      JButton actionButton = new JButton(buttonText)
      panel.add(label)
      panel.add(actionButton)
      b1.add(panel)
      b1.add(Box.createHorizontalGlue())
      actionButton.addActionListener{ l ->
        actionButton.setEnabled(false)
        runnable.run()
      }
      actionButton.setFocusable(false)
      return panel
    }
  }

  public static List<Path> findByFileExtension(Path path, String fileExtension)
  throws IOException {
    if (!Files.isDirectory(path) || path.toString().trim().isEmpty()) {
      throw new IllegalArgumentException("Invalid path: it either does not exist or is not a directory.")
    }

    if (!Files.isReadable(path)) {
      throw new IllegalArgumentException("Provided directory must have read permissions")
    }
    List<Path> result = new ArrayList()

    Iterator<Path> itr = Files.walk(path).iterator()
    while(true) {
      try {
        if(itr.hasNext()) {
          Path next = itr.next()
          if (next.toString().endsWith(fileExtension) && !next.getFileName().toString().startsWith(".")) {
            result.add(next)
          }
        } else {
          break
        }
      }catch(Exception e) {
        System.err.print(e.getLocalizedMessage())
      }
    }
    return result
  }

  private static JMenuItem createHTMLMenuItem(String menuText, String name, Runnable runnable) {
    return MenuHelper.makeMenuItem(menuText, "", runnable)
  }

  private static JMenuItem createLinkMenuItem(String url, String menuText) {
    String feedbackMessage = "It was not possible to locate or resolve the linked resource. " +
        System.lineSeparator() + url
    return createHTMLMenuItem(menuText, "", {
      Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null
      if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
        try {
          desktop.browse(new URI(url))
        } catch (Exception e) {
          //e.printStackTrace()
          Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard()
          if (clipboard == null) {
            return
          }
          StringSelection sel = new StringSelection(url)
          try {
            clipboard.setContents(sel, null)
            JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "Your configuration currently does not allow browsing." +
                " The webpage URL has been copied to clipboard:\n" + url, "",
                JOptionPane.INFORMATION_MESSAGE)
          } catch (Exception e2) {
            e.printStackTrace()
          }
        }
      }
    })
  }

  //----------------------------------------------
  //---------- FREEPLANE STATIC METHODS ----------


  public static MIconController iconController() {
    return (MIconController) getModeController().getExtension(IconController.class)
  }

  public static MModeController getModeController() {
    return org.freeplane.features.mode.mindmapmode.MModeController.getMModeController()
  }

  public static NodeModel getNodeModel(Node node) {
    // return org.freeplane.features.mode.Controller.getCurrentController().getMap().getNodeForID(node.getId())
    return  ((MapProxy) node.getMindMap()).getDelegate().getNodeForID(node.getId())
  }

  public static MindMap getMindMap(File file) {
    if (file == null) {
      throw new IllegalArgumentException("Null file")
    }
    MindMap source
    try {
      Loader loader = ScriptUtils.c()
          .mapLoader(file)
      source =  loader.getMindMap()
    } catch(Exception e) {
      throw new RuntimeException("Error loading mindmap from file: " + file.toString())
    }
    return source
  }

  private static Color determineStringColor(String str) {
    CRC32 crc = new CRC32()
    crc.update(str.getBytes(StandardCharsets.UTF_8))
    return  HSLColorConverter.generateColorFromLong(crc.getValue())
  }


  private static goToNode(NodeModel nodeModel) {
    String path = nodeModel.getMap().getFile().toString()
    String uriStr = nodeModel.getMap().getFile().toURI().toString() + "#" + nodeModel.getID()
    try {
      Node node = FreeplaneMapCrawler.LOADED_MAPS.get(path).node(nodeModel.createID())
      URI uri = new URI(uriStr)
      Hyperlink link = new Hyperlink(uri)
      UrlManager.getController().loadHyperlink(link)
      ScriptUtils.c().select(node)
    }catch (Exception e1) {
      UITools.errorMessage(
          "It was not possible to go to node at " + uriStr + "\n\n" + e1.getMessage())
      e1.printStackTrace()
    }
  }

  public static List<MapView> refreshMindMapReferences(Map<String, MindMap> loadedMaps, Map<String, MindMap> loadedMapsModificationDate) {
    for (String mapPath :  loadedMaps.keySet()) {
      boolean isVisible = getNodeModel(loadedMaps.get(mapPath).getRoot()).hasViewers()
      long modifDate = loadedMaps.get(mapPath).getFile().lastModified()
      boolean modificationDateChanged = !loadedMapsModificationDate.get(mapPath).equals(modifDate)
      loadedMaps.compute(mapPath, { k, v ->
        isVisible || modificationDateChanged? loadedMaps.get(k).getRoot().getMindMap() : loadedMaps.get(k)
      })
      loadedMapsModificationDate.compute(mapPath, { k, v ->
        modificationDateChanged? modifDate : loadedMapsModificationDate.get(k)
      })
      if (isVisible && isDev) ScriptUtils.c().setStatusInfo("modification changed: " + modificationDateChanged)
    }
  }

  private static boolean isLightLaF() {
    return UITools.isLightLookAndFeelInstalled()
  }
}