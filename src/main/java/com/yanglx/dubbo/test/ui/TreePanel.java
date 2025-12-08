package com.yanglx.dubbo.test.ui;

import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.treeStructure.Tree;
import com.yanglx.dubbo.test.CacheInfo;
import com.yanglx.dubbo.test.DubboSetingState;
import com.yanglx.dubbo.test.DubboTestBundle;
import com.yanglx.dubbo.test.dubbo.DubboMethodEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;

public class TreePanel extends JPanel {

    private static final Logger LOGGER = LoggerFactory.getLogger(TreePanel.class);

    private Tree tree;

    private TreeNodeTypeEnum nowTreeNodeTypeEnum;

    private TabBar tabBar;

    private final MouseAdapter treeMouseAdapter;

    public TreePanel(TreeNodeTypeEnum treeNodeTypeEnum) {
        this.nowTreeNodeTypeEnum = treeNodeTypeEnum;
        tree = new Tree();
        this.treeMouseAdapter = createTreeMouseAdapter();
        JBScrollPane jScrollBar = new JBScrollPane(tree);
        this.setLayout(new BorderLayout());
        this.add(jScrollBar, BorderLayout.CENTER);
        // this.repaint();
        // this.validate();
    }

    public void setTabBar(TabBar tabBar) {
        this.tabBar = tabBar;
    }

    /**
     * 刷新数据并添加事件
     */
    public void refresh(TreeNodeTypeEnum treeNodeTypeEnum) {
        this.nowTreeNodeTypeEnum = treeNodeTypeEnum;
        this.refresh();
    }

    /**
     * 刷新数据并添加事件
     */
    public void refresh() {
        //添加数据模型
        this.setModel();
        this.resetTreeMouseListener();
    }

    /**
     * 重置树形鼠标监听器（避免重复绑定）
     */
    private void resetTreeMouseListener() {
        for (MouseListener listener : tree.getMouseListeners()) {
            if (listener == treeMouseAdapter) {
                tree.removeMouseListener(listener);
            }
        }
        tree.addMouseListener(treeMouseAdapter);
    }

    /**
     * 创建树形鼠标事件适配器
     */
    private MouseAdapter createTreeMouseAdapter() {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (e.getClickCount() == 1) {
                        handleLeftSingleClick(e);
                    } else if (e.getClickCount() == 2) {
                        handleLeftDoubleClick(e);
                    }
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    handleRightClick(e);
                }
            }
        };
    }

    /**
     * 处理左键单击事件
     */
    private void handleLeftSingleClick(MouseEvent e) {
        // LOGGER.info("鼠标左键单击事件");
    }

    /**
     * 处理左键双击事件
     */
    private void handleLeftDoubleClick(MouseEvent e) {
        // LOGGER.info("鼠标左键双击事件");
        Object selectedNodeObj = tree.getLastSelectedPathComponent();
        if (!(selectedNodeObj instanceof DefaultMutableTreeNode) || tabBar == null) {
            return;
        }
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectedNodeObj;
        Object userObject = selectedNode.getUserObject();
        if (userObject instanceof CacheInfo) {
            CacheInfo cacheInfo = (CacheInfo) userObject;
            tabBar.addTab(cacheInfo.getId());

            TabInfo selectedTabInfo = TabBar.getSelectionTabInfo();
            if (selectedTabInfo != null) {
                Tab component = (Tab) selectedTabInfo.getComponent();
                DubboMethodEntity methodEntity = cacheInfo.getDubboMethodEntity();
                DubboPanel.refreshUI(component.getDubboPanel(), methodEntity);
            }
        }
    }

    /**
     * 处理右键单击事件
     */
    private void handleRightClick(MouseEvent e) {
        // LOGGER.info("鼠标右键事件");

        int x = e.getX();
        int y = e.getY();
        TreePath pathForLocation = tree.getPathForLocation(x, y);
        if (pathForLocation == null) {
            return;
        }
        tree.setSelectionPath(pathForLocation);
        JPopupMenu menu = createPopupMenu();
        menu.show(tree, x, y);
    }

    private JPopupMenu createPopupMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem menuItem = new JMenuItem(DubboTestBundle.message("dubbo-test.tool.delete"));
        menuItem.addActionListener(actionEvent -> {
            Object nodeObj = tree.getLastSelectedPathComponent();
            if (!(nodeObj instanceof DefaultMutableTreeNode)) {
                return;
            }
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) nodeObj;
            Object userObject = selectedNode.getUserObject();
            if (userObject instanceof CacheInfo) {
                CacheInfo cacheInfo = (CacheInfo) userObject;
                DubboSetingState.CacheType type = TreeNodeTypeEnum.COLLECTIONS.equals(nowTreeNodeTypeEnum)
                        ? DubboSetingState.CacheType.COLLECTIONS
                        : DubboSetingState.CacheType.HISTORY;
                DubboSetingState.getInstance().remove(cacheInfo, type);
                refresh();
            }
        });
        menu.add(menuItem);

        if (TreeNodeTypeEnum.HISTORY.equals(nowTreeNodeTypeEnum)) {
            JMenuItem menuItemAll = new JMenuItem(DubboTestBundle.message("dubbo-test.tool.delete-all"));
            menuItemAll.addActionListener(actionEvent -> {
                DubboSetingState.getInstance().historyParamInfoCacheList.clear();
                refresh();
            });
            menu.add(menuItemAll);
        }
        return menu;
    }

    /**
     * 设置数据模型
     */
    private void setModel() {
        List<CacheInfo> paramInfoCache;
        DefaultMutableTreeNode root;
        DubboSetingState instance = DubboSetingState.getInstance();
        if (TreeNodeTypeEnum.COLLECTIONS.equals(nowTreeNodeTypeEnum)) {
            root = new DefaultMutableTreeNode("Collections");
            paramInfoCache = instance.getParamInfoCache(DubboSetingState.CacheType.COLLECTIONS);
        } else {
            root = new DefaultMutableTreeNode("History");
            paramInfoCache = instance.getParamInfoCache(DubboSetingState.CacheType.HISTORY);
        }
        for (CacheInfo dubboMethodEntity : paramInfoCache) {
            DefaultMutableTreeNode defaultMutableTreeNode = new DefaultMutableTreeNode(dubboMethodEntity, true);
            root.add(defaultMutableTreeNode);
        }
        tree.setModel(new DefaultTreeModel(root, false));
        tree.updateUI();
    }

    public enum TreeNodeTypeEnum {
        HISTORY,
        COLLECTIONS
    }
}
