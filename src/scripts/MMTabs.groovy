// @ExecutionModes({ON_SINGLE_NODE="/main_menu/i-plasm/Search-Filter-Associations"})

package scripts

/*
 * Info & Discussion: https://github.com/freeplane/freeplane/discussions/2843
 *
 * Last Update: 2026-02-28
 *
 * ---------
 *
 * MMTabs: Freelane tool for navigating through open map tabs.
 *
 * Copyright (C) 2026 bbarbosa
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop
import java.awt.FlowLayout;
import java.awt.Font
import java.awt.Frame
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.Toolkit
import java.awt.Window;
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.Callable
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors
import javax.swing.AbstractAction;
import javax.swing.Box
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel;
import javax.swing.JPopupMenu
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.freeplane.core.ui.components.UITools
import org.freeplane.core.util.MenuUtils
import org.freeplane.features.ui.IMapViewChangeListener;
import org.freeplane.view.swing.map.MapView;

FreeplaneMapTabs.displayList(UITools.getCurrentFrame())

class FreeplaneMapTabs {
  private static ActionableItemsList itemsList

  private static final String PLUGIN_NAME = "MMTabs"
  private static final String PLUGIN_VERSION = "0.4.1"
  private static final int MAX_TOP_ITEMS = 5;
  private static final Map<String, MapView>  LAST_SELECTED_TABS_TRACKER = new LinkedHashMap<String, MapView>(5, 0.75f, false) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, MapView> eldest) {
      return size() > 5;
    }
  };
  private static boolean mapViewChangeListenerHasBeenRegistered = false

  public static void displayList(Frame frame) {
    itemsList = new ActionableItemsList(frame, MAX_TOP_ITEMS)
    itemsList.displayListableActionsDialog(fetchItems(), fetchTopItems(), PLUGIN_NAME);
    itemsList.getAboutBtn().addActionListener({l -> displayAbout(UITools.getCurrentFrame())})
    itemsList.getAboutBtn().setIcon(MenuUtils.getMenuItemIcon('IconAction.' + "emoji-2139"))
    itemsList.getAboutBtn().repaint()

    if (!mapViewChangeListenerHasBeenRegistered) {
      IMapViewChangeListener myMapViewChangeListener = new IMapViewChangeListener() {
            public void afterViewClose(Component oldView) {
              LAST_SELECTED_TABS_TRACKER.remove(oldView.getName())
            }

            public void afterViewChange(final Component oldView, final Component newView) {
              if (newView == null) {
                return
              }

              String name = newView.getName()
              LAST_SELECTED_TABS_TRACKER.remove(name)
              LAST_SELECTED_TABS_TRACKER.put(name, newView)
              SwingUtilities.invokeLater({itemsList.refreshModel(fetchTopItems(), fetchItems())})
            }
          }
      org.freeplane.features.mode.Controller.currentController.mapViewManager.addMapViewChangeListener(myMapViewChangeListener)
      mapViewChangeListenerHasBeenRegistered = true
    }
  }

  private  static List<ListableAction> fetchItems() {
    List<ListableAction> listableAction = getMapViews()
        .stream().map({ e ->
          new ListableAction(e.getName(), e.getName(), {
            changeToMapView(e.getName())
          })
        })
        .collect(Collectors.toList());

    listableAction.sort(Comparator.comparing({ item ->
      item.getDescription()
    }));
    return listableAction
  }

  private  static List<ListableAction> fetchTopItems() {
    List<ListableAction> topItems = LAST_SELECTED_TABS_TRACKER.entrySet().stream().map({ e ->
      e.getKey()
    })
    .map({ cat ->
      new ListableAction(cat, cat, {
        changeToMapView(cat)
      }, null, null, null, true
      )
    })
    .collect(Collectors.toList());

    Collections.reverse(topItems);
    return topItems
  }

  private static JMenuItem createLinkMenuItem(String url, String menuText) {
    String feedbackMessage = "It was not possible to locate or resolve the linked resource. " +
        System.lineSeparator() + url
    return createHTMLMenuItem(menuText, "", {
      browseToDiscussion(url)
    })
  }

  private static JMenuItem createHTMLMenuItem(String menuText, String name, Runnable runnable) {
    return MenuHelper.makeMenuItem(menuText, "", runnable)
  }

  private static void displayAbout(Component comp) {
    String discussionUrl = "https://github.com/freeplane/freeplane/discussions/2843"
    MenuHelper.FloatingMsgPopup floatingPopup = new MenuHelper.FloatingMsgPopup()

    JMenuItem contentsMenuItem = MenuHelper.createHeaderNoticeMenuItem("<b>" + PLUGIN_NAME +
        "</b>" + "<br><i>version " + PLUGIN_VERSION + "</i><br><br>" +
        "Freelane tool for navigating through open map tabs.",
        "About " + PLUGIN_NAME)
    floatingPopup.add(contentsMenuItem)

    String visitWebsite = MenuHelper.floatingMenuItemUnderlinedActionHTML("Discussion & Updates:",
        discussionUrl)
    JMenuItem visitWebsiteItem =
        createLinkMenuItem(discussionUrl, visitWebsite)
    floatingPopup.add(visitWebsiteItem)
    floatingPopup.add(visitWebsiteItem)

    JMenuItem licenseMenuItem = MenuHelper.createContentNoticeMenuItem(PLUGIN_NAME + " " +
        PLUGIN_VERSION +
        " - Freelane tool for navigating through open map tabs." +
        "<br><br>Copyright (C) 2026 bbarbosa" +
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

  private static void browseToDiscussion(String uri) {
    try {
      browseViaDesktop(new URI(uri))
    } catch (Exception e) {
      Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard()
      if (clipboard == null) {
        return
      }
      StringSelection sel = new StringSelection(uri)
      try {
        clipboard.setContents(sel, null)
        JOptionPane.showMessageDialog(UITools.getCurrentFrame(), "Your configuration currently does not allow browsing." +
            " The webpage URL has been copied to clipboard:\n" + uri, "",
            JOptionPane.INFORMATION_MESSAGE)
      } catch (Exception e2) {
        e.printStackTrace()
      }
    }
  }

  public static void browseViaDesktop(URI uri) throws IOException {
    Desktop desktop = Desktop.getDesktop()
    if (Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.BROWSE)) {
      desktop.browse(uri)
    }
  }

  // ------------------------ Freeplane helper methods ------------------------

  private static List<MapView> getMapViews() {
    // return (List<MapView>) org.freeplane.features.mode.Controller.getCurrentController().getViewController().getMapViewVector()
    try {
      return (List<MapView>) org.freeplane.features.mode.Controller.getCurrentController()
          .getMapViewManager().getMapViewVector();
    } catch (Exception e) {
      return (List<MapView>) org.freeplane.features.mode.Controller.getCurrentController()
          .getMapViewManager().getMapViews();
    }
  }

  private static void changeToMapView(String mapViewDisplayName) {
    org.freeplane.features.mode.Controller.getCurrentController().getMapViewManager()
        .changeToMapView(mapViewDisplayName);
  }

  // ---------------------- STATIC CLASSES -----------------------------

  /**
   * This class is derived/copied from the external "jhelperutils" library:
   *
   * Github: https://github.com/i-plasm/jhelperutils/
   * 
   */
  private static class ListableAction {
    private String id;
    private String description;
    private Runnable action;
    private String iconString;
    private Color bgColor;
    private Color fgColor;
    private boolean topItem;

    public ListableAction(String id, String description, Runnable action, String iconString, Color bgColor, Color fgColor, boolean topItem) {
      this.id = id;
      this.description = description;
      this.action = action;
      this.iconString = iconString;
      this.bgColor = bgColor;
      this.fgColor = fgColor;
      this.topItem = topItem;
    }

    public ListableAction(String id, String description, Runnable action) {
      this.id = id;
      this.description = description;
      this.action = action;
    }
    public String getDescription() {
      return description;
    }

    public Color getBgColor() {
      return bgColor;
    }

    public Color getFgColor() {
      return fgColor;
    }

    public Runnable getAction() {
      return action;
    }

    public String getIconString() {
      return iconString;
    }

    public boolean isTopItem() {
      return topItem;
    }

    public String getId() {
      return id;
    }
  }

  /**
   * This class is derived/copied from the external "jhelperutils" library:
   *
   * Github: https://github.com/i-plasm/jhelperutils/
   *
   */
  private static class ActionableItemsList {

    private final Frame frame;
    private final int maxTopItems;
    def iconGenerator //private final TriFunction<String, Color, Color, Icon> iconGenerator;
    private Consumer<ListableAction> sendToTopItems
    private Window requestsDialog
    private DefaultListModel<ListableAction> requestsDialogModel
    private List<ListableAction> topItems = new ArrayList<>()
    private List<ListableAction> items = new ArrayList<>()
    private JTextField filterField
    private JButton aboutBtn

    private static Color BG_TOP_ITEMS = new Color(92, 214, 153);
    private static Color FG_TOP_ITEMS = Color.BLACK;

    public ActionableItemsList(Frame frame, int maxTopItems) {
      this.frame = frame;
      this.maxTopItems = maxTopItems;
    }

    public void sendToTopItems(ListableAction item) {
      sendToTopItems.accept(item)
    }

    public void refreshModel(List<ListableAction> topItems, List<ListableAction> items) {
      if (this.topItems == null) {
        this.topItems = topItems
      }
      this.topItems.clear()
      this.topItems.addAll(topItems)
      this.items.clear()
      this.items.addAll(items)
      requestsDialogModel.removeAllElements()
      requestsDialogModel.addAll(topItems)
      requestsDialogModel.addAll(items)
      String filterTxt = filterField.getText()
      filterField.setText("")
      filterField.setText(filterTxt)
    }

    public void displayListableActionsDialog(List<ListableAction> items, List<ListableAction> topItems, String title) {
      TopItemsConfig topItemsConfig = new TopItemsConfig();
      topItemsConfig.addTopItemsDynamically= true;
      topItemsConfig.bgTopItems = BG_TOP_ITEMS;
      topItemsConfig.fgTopItems = FG_TOP_ITEMS;
      topItemsConfig.maxTopItems = maxTopItems;
      displayListableActionsDialog(items, topItems, title, false, null, null, null, null, topItemsConfig);
    }

    public void displayListableActionsDialog(List<ListableAction> allItems, List<ListableAction> theTopItems, String title, boolean undecorate, Rectangle bounds, Supplier<String> querySupplier, JTextField externalTextField, Runnable doOnClose, TopItemsConfig topItemsConfig) {
      this.items.addAll(allItems)
      boolean addTopItemsDynamically = topItemsConfig == null ? false : topItemsConfig.addTopItemsDynamically;
      filterField = externalTextField == null ? new JTextField() : externalTextField;
      requestsDialogModel = new DefaultListModel<>();
      if (theTopItems != null) {
        topItems.addAll(theTopItems);
        requestsDialogModel.addAll(topItems);
      }
      requestsDialogModel.addAll(items);

      JList<ListableAction> jList = new JList<>(requestsDialogModel);

      if (topItems != null && topItems.size() >= 2) {
        jList.setSelectedIndex(1);
      } else if (items.size()>0 ) {
        jList.setSelectedIndex(0);
      }

      JLabel statusLabel = new JLabel();
      Font font = jList.getFont().deriveFont((float) (jList.getFont().getSize()*1.2f))

      jList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
              JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected,
                  cellHasFocus);
              setFont(font)
              ListableAction item = (ListableAction) value;
              label.setText(item.getDescription());
              Icon icon = iconGenerator != null ? iconGenerator.apply(item.getIconString(), item.getIconBgColor(), item.getIconFgColor()) : null;
              label.setIcon(icon);
              label.setToolTipText(item.getDescription());

              if (item.getBgColor() != null && !isSelected) {
                label.setBackground(item.getBgColor());
                label.setForeground(item.getFgColor());
              } else if (item.isTopItem()) {
                label.setBackground(BG_TOP_ITEMS);
                label.setForeground(FG_TOP_ITEMS);
              }

              if (isSelected) {
                label.setBackground(Color.BLUE);
              }
              return label;
            }
          });

      Runnable filterAction = {
        String str = filterField.getText().trim();
        requestsDialogModel.clear();
        boolean selectedAnElement = false;
        if (topItems != null && str.isBlank()) {
          requestsDialogModel.addAll(topItems);
          if (topItems.size() >= 2) {
            jList.setSelectedIndex(1);
            selectedAnElement = true;
          }
        }
        for (ListableAction item : items) {
          if(item.getDescription().toLowerCase().contains(str.toLowerCase())){
            requestsDialogModel.addElement(item);
            if (!selectedAnElement) {
              jList.setSelectedIndex(0);
              selectedAnElement = true;
            }
          }
        }
      };

      DocumentListener docListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
              filter();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
              filter();
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
              filter();
            }

            private void filter() {
              filterAction.run();
            }
          };
      filterField.getDocument().addDocumentListener(docListener);

      JPanel panel = new JPanel(new BorderLayout());
      JPanel optnsPanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
      JCheckBox makeStickyCB = new JCheckBox("Make sticky");
      JButton closeBtn = new JButton("Close");
      aboutBtn = new JButton();

      optnsPanel.add(statusLabel);
      if (!undecorate) {
        optnsPanel.add(makeStickyCB);
      } else if (externalTextField != null) {
        optnsPanel.add(closeBtn);
      }
      optnsPanel.add(aboutBtn)
      JScrollPane scrollPane = new JScrollPane(jList);
      if (externalTextField == null) {
        panel.add(filterField, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(optnsPanel, BorderLayout.SOUTH);
      } else {
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(optnsPanel, BorderLayout.NORTH);
      }

      scrollPane.setOpaque(true);
      jList.setOpaque(true);

      sendToTopItems =  { currentItem ->
        boolean itemAlreadyPresent = topItems.stream().anyMatch({item -> item.getId().equals(currentItem.getId())});
        if ((topItemsConfig.maxTopItems > 0) && (topItems.size() == topItemsConfig.maxTopItems) && !itemAlreadyPresent) {
          int toRemoveOldItem = topItems.size() - 1;
          topItems.remove(toRemoveOldItem);
          if (filterField.getText().isBlank()) {
            requestsDialogModel.removeElementAt(toRemoveOldItem);
          }
        };
        int currItemInTopItemsIndex =  itemAlreadyPresent ? topItems.stream().filter({item -> item.getId().equals(currentItem.getId())}).map({item -> topItems.indexOf(item)}).findFirst().orElse(-1)
        : -1;
        topItems.removeIf({item ->item.getId().equals(currentItem.getId())});
        ListableAction topItem = new ListableAction(currentItem.getId(), currentItem.getDescription(), currentItem.getAction(), currentItem.getIconString(),
            topItemsConfig.bgTopItems, topItemsConfig.fgTopItems, true);
        topItems.add(0, topItem);

        if (filterField.getText().isBlank()) {
          if (currItemInTopItemsIndex != -1) {
            requestsDialogModel.removeElementAt(currItemInTopItemsIndex);
          }
          requestsDialogModel.add(0, topItem);
        }
      };

      requestsDialog = new JDialog(frame) //externalTextField == null ?  new JDialog(frame) : new WindowBasedPopup(frame);

      if (externalTextField == null) {
        ((JDialog) requestsDialog).setModal(false);
        if (undecorate) {
          ((JDialog) requestsDialog).setUndecorated(true);
        } else {
          ((JDialog) requestsDialog).setTitle(title);
        }
      } else {
        // ... omitted: irrelevant for this program
      }

      if (bounds == null) {
        requestsDialog.setSize(400, 400);
        requestsDialog.setLocationRelativeTo(frame);
      } else {
        requestsDialog.setBounds(bounds);
      }
      requestsDialog.add(panel);

      if (externalTextField == null) {
        final JDialog dialog = (JDialog) requestsDialog;
        setBasicStandardListNavKeyBindings(dialog, scrollPane, false, false, { makeStickyCB.isSelected()} , {
          ((ListableAction) jList.getSelectedValue()).getAction().run();
          dialog.setVisible(false);
          dialog.dispose();
          frame.toFront();
          SwingUtilities.invokeLater({frame.requestFocusInWindow()});
        }, {
          ListableAction currentItem = (ListableAction) jList.getSelectedValue();
          currentItem.getAction().run();
          if (addTopItemsDynamically) {
            sendToTopItems.accept(currentItem);
          }
          frame.toFront();
          SwingUtilities.invokeLater({frame.requestFocusInWindow()});
        }, {
          dialog.setVisible(false);
          dialog.dispose();
        });

        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        requestsDialog.addWindowFocusListener(new WindowAdapter() {
              @Override
              public void windowLostFocus(WindowEvent e) {
                super.windowLostFocus(e);
                if (undecorate || !makeStickyCB.isSelected()) {
                  Window theDialog = (Window) e.getSource();
                  theDialog.setVisible(false);
                  theDialog.dispose();
                }
              }
            }
            );
      }

      jList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
              Window dialog = (Window) SwingUtilities.getWindowAncestor((Component) e.getSource());
              int index = jList.locationToIndex(e.getPoint());
              if (index >= 0) {
                if (e.getClickCount() == 2 || (e.getClickCount() == 1 && !makeStickyCB.isSelected())) {
                  ListableAction currentItem = jList.getModel().getElementAt(index);
                  currentItem.getAction().run();
                  if (addTopItemsDynamically) {
                    sendToTopItems.accept(currentItem);
                  }
                }
                if ((externalTextField == null && !makeStickyCB.isSelected()) || (externalTextField != null && e.getClickCount() == 2)) {
                  dialog.setVisible(false);
                  dialog.dispose();
                }
              }
            }
          });

      requestsDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
              if (externalTextField != null) {
                e.getWindow().dispose();
              }

              filterField.getDocument().removeDocumentListener(docListener);
            }

            @Override
            public void windowClosed(WindowEvent e) {
              e.getWindow().removeWindowListener(this);

              if (externalTextField != null) {
                filterField.getDocument().removeDocumentListener(docListener);
              }
              if (doOnClose != null)
                doOnClose.run();
            }

            @Override
            public void windowOpened(WindowEvent e) {
              super.windowOpened(e);
              SwingUtilities.invokeLater({ filterField.requestFocusInWindow()});
            }
          });

      requestsDialog.setVisible(true);

      SwingUtilities.invokeLater({
        if (!filterField.getText().isBlank()) {
          filterAction.run();
        }
        SwingUtilities.invokeLater({
          filterField.requestFocusInWindow();
          if (querySupplier != null && externalTextField == null) {
            filterField.setText(querySupplier.get());
          }
        });
      });
    }

    public static void setBasicStandardListNavKeyBindings(RootPaneContainer frame, JScrollPane itemsListOrTableScrollPane, boolean useTabsForNavigation, boolean alwaysSticky, Supplier<Boolean> isSticky, Runnable navigateAndClose, Runnable navigateWithoutClosing, Runnable exit) {
      JComponent itemsListOrTable = (JComponent) itemsListOrTableScrollPane.getViewport().getView();
      itemsListOrTableScrollPane.setFocusable(false);
      itemsListOrTable.setFocusable(false);

      frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
          "exit");
      frame.getRootPane().getActionMap().put("exit", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
              exit.run();
            }
          });

      if (useTabsForNavigation) {
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK),
            "goToSelectedAndClose");
      }

      frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
          "applyEnter");

      frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK),
          "applyCtrlEnter");

      frame.getRootPane().getActionMap().put("applyEnter", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
              if (isSticky.get() && !alwaysSticky) {
                navigateWithoutClosing.run();
              } else navigateAndClose.run();
            }
          });

      frame.getRootPane().getActionMap().put("applyCtrlEnter", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
              if (!isSticky.get()) {
                navigateAndClose.run();
              } else navigateWithoutClosing.run();
            }
          });

      frame.getRootPane().getActionMap().put("goToSelectedAndClose", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
              navigateAndClose.run();
            }
          });

      frame.getRootPane().getActionMap().put("goToSelectedWithoutClosing", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
              navigateWithoutClosing.run();
            }
          });

      frame.getRootPane().setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, Collections.emptySet());
      frame.getRootPane().setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, Collections.emptySet());
      InputMap im = itemsListOrTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      // Disabling default forward and backward table navigation in order to use our own action
      im.put(KeyStroke.getKeyStroke("TAB"), "none");
      im.put(KeyStroke.getKeyStroke("shift TAB"), "none");
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "none");
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "none");
      InputMap resultsScrollPane = itemsListOrTableScrollPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      resultsScrollPane.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "none");
      resultsScrollPane.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "none");

      if (useTabsForNavigation) {
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0),
            "selectNextResult");
      }
      frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0),
          "selectNextResult");
      frame.getRootPane().getActionMap().put("selectNextResult", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
              selectNextResult(itemsListOrTable);
            }
          });

      frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK),
          "selectPreviousResult");
      frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0),
          "selectPreviousResult");
      frame.getRootPane().getActionMap().put("selectPreviousResult", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
              selectPreviousResult(itemsListOrTable);
            }
          });
    }

    public static void selectPreviousResult(JComponent itemsListOrTable) {
      if (itemsListOrTable instanceof JTable) {
        JTable resultsTable = (JTable) itemsListOrTable;
        if (resultsTable.getRowCount() > 0) {
          int selectedRow = resultsTable.getSelectedRow();//resultsTable.convertRowIndexToView(resultsTable.getSelectedRow())
          int toSelect = //resultsTable.getRowCount() == 1 ? 0 :
              selectedRow > 0 ? selectedRow - 1 :
              resultsTable.getRowCount() - 1;
          resultsTable.setRowSelectionInterval(toSelect, toSelect);
        }
      }else if (itemsListOrTable instanceof JList) {
        JList resultsList = (JList) itemsListOrTable;
        if (resultsList.getModel().getSize() > 0) {
          int selectedRow =  resultsList.getSelectedIndex();
          int toSelect = selectedRow > 0 ? selectedRow - 1 :
              resultsList.getModel().getSize() - 1;
          resultsList.setSelectedIndex(toSelect);
        }
      }
    }

    public static void selectNextResult(JComponent itemsListOrTable) {
      if (itemsListOrTable instanceof JTable) {
        JTable resultsTable = (JTable) itemsListOrTable;
        if (resultsTable.getRowCount() > 0) {
          int selectedRow = resultsTable.getSelectedRow();
          int toSelect = resultsTable.getRowCount() == 1 ? selectedRow : selectedRow < resultsTable.getRowCount() - 1? selectedRow + 1 : 0;
          resultsTable.setRowSelectionInterval(toSelect, toSelect);
        }
      } else if (itemsListOrTable instanceof JList) {
        JList resultsList = (JList) itemsListOrTable;
        if (resultsList.getModel().getSize() > 0) {
          int selectedRow =  resultsList.getSelectedIndex();
          int toSelect = resultsList.getModel().getSize() == 1 ? selectedRow : selectedRow < resultsList.getModel().getSize() - 1? selectedRow + 1 : 0;
          resultsList.setSelectedIndex(toSelect);
        }
      }
    }

    public static class TopItemsConfig {
      private boolean addTopItemsDynamically;
      private Color bgTopItems;
      private Color fgTopItems;
      private int maxTopItems;
    }

    public JButton getAboutBtn() {
      return aboutBtn;
    }
  }


  /**
   * The methods in this class are derived/copied from the "jhelperutils" library:
   *
   * Github: https://github.com/i-plasm/jhelperutils/
   */
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
}