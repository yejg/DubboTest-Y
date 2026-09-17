package com.yanglx.dubbo.test.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.treeStructure.Tree;
import com.yanglx.dubbo.test.CacheInfo;
import com.yanglx.dubbo.test.DubboSetingState;
import com.yanglx.dubbo.test.DubboTestBundle;
import com.yanglx.dubbo.test.dubbo.DubboMethodEntity;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;
import java.util.Locale;

public class TreePanel extends JPanel {

    private static final Logger LOGGER = LoggerFactory.getLogger(TreePanel.class);

    private Tree tree;

    private TreeNodeTypeEnum nowTreeNodeTypeEnum;

    private TabBar tabBar;

    private final MouseAdapter treeMouseAdapter;

    private JBTextField searchField;

    public TreePanel(TreeNodeTypeEnum treeNodeTypeEnum) {
        this.nowTreeNodeTypeEnum = treeNodeTypeEnum;
        tree = new Tree();
        tree.getSelectionModel().setSelectionMode(javax.swing.tree.TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);// 支持多选
        tree.setCellRenderer(new CacheInfoTreeCellRenderer());
        // 不注册的话 renderer 里返回的 tooltip 不会生效
        ToolTipManager.sharedInstance().registerComponent(tree);
        this.treeMouseAdapter = createTreeMouseAdapter();
        JBScrollPane jScrollBar = new JBScrollPane(tree);
        this.setLayout(new BorderLayout());
        this.add(createSearchField(), BorderLayout.NORTH);
        this.add(jScrollBar, BorderLayout.CENTER);
        // this.repaint();
        // this.validate();
    }

    /**
     * 顶部搜索框, 对收藏和历史都生效
     */
    private JComponent createSearchField() {
        searchField = new JBTextField();
        searchField.getEmptyText().setText(DubboTestBundle.message("dubbo-test.tool.search"));
        searchField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                setModel();
            }
        });
        return searchField;
    }

    public void setTabBar(TabBar tabBar) {
        this.tabBar = tabBar;
    }

    /**
     * 刷新数据并添加事件
     */
    public void refresh(TreeNodeTypeEnum treeNodeTypeEnum) {
        boolean typeChanged = !treeNodeTypeEnum.equals(this.nowTreeNodeTypeEnum);
        this.nowTreeNodeTypeEnum = treeNodeTypeEnum;
        // 切换收藏/历史时清空关键字, 否则看到的是被上一个关键字过滤后的残缺列表, 容易误判成数据丢了
        if (typeChanged && searchField != null) {
            searchField.setText("");
        }
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
            tabBar.addTab(cacheInfo.getId(), cacheInfo.getDisplayName());

            TabInfo selectedTabInfo = tabBar.getSelectionTabInfo();
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
        // TreePath pathForLocation = tree.getPathForLocation(x, y);// 根据鼠标点击的坐标 (x, y) 获取对应位置的树节点路径
        // if (pathForLocation == null) {
        //     return;
        // }
        // tree.setSelectionPath(pathForLocation);// 这行代码会将该节点设为唯一选中节点，覆盖了之前选中的多个节点
        JPopupMenu menu = createPopupMenu(x, y);
        menu.show(tree, x, y);
    }

    private JPopupMenu createPopupMenu(int x, int y) {
        JPopupMenu menu = new JPopupMenu();

        // 只改名字和描述, 不碰参数。收藏才有意义, 历史记录不提供。
        // 取光标下的节点而非选中项 —— 右键不改变选中状态(为了保住多选删除), 靠选中项会让菜单项灰掉
        if (TreeNodeTypeEnum.COLLECTIONS.equals(nowTreeNodeTypeEnum)) {
            TreePath clickedPath = tree.getPathForLocation(x, y);
            CacheInfo clicked = clickedPath == null ? null : asCacheInfo(clickedPath.getLastPathComponent());
            JMenuItem editItem = new JMenuItem(DubboTestBundle.message("dubbo-test.tool.edit"));
            editItem.setEnabled(clicked != null);
            editItem.addActionListener(actionEvent -> editNameAndDescription(clicked));
            menu.add(editItem);
        }

        JMenuItem menuItem = new JMenuItem(DubboTestBundle.message("dubbo-test.tool.delete"));
        menuItem.addActionListener(actionEvent -> deleteSelectedNodes());
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
     * 只改名字和描述, 不动参数、地址、超时。
     * 用途是名字起得不好想改一下, 不必先把请求参数还原成保存时的样子
     */
    private void editNameAndDescription(CacheInfo cacheInfo) {
        if (cacheInfo == null) {
            return;
        }
        Project project = tabBar == null ? null : tabBar.getProject();
        SaveDialogue dialogue = new SaveDialogue(project, SaveDialogue.Mode.EDIT,
                cacheInfo.getDisplayName(), cacheInfo.getDescription());
        if (!dialogue.showAndGet()) {
            return;
        }
        String name = dialogue.getInputName();
        if (name != null && !name.trim().isEmpty()) {
            cacheInfo.setName(name.trim());
        }
        cacheInfo.setDescription(dialogue.getInputDescription());
        // 该收藏若正开着 tab, 标题也要跟着改, 否则显示的是改名前的旧名字
        if (tabBar != null) {
            tabBar.renameTab(cacheInfo.getId(), cacheInfo.getDisplayName());
        }
        // getParamInfoCache() 返回的是原 list 引用, 节点 userObject 与 list 中是同一对象,
        // 直接改即可, PersistentStateComponent 会自动持久化
        refresh();
    }

    private void deleteSelectedNodes() {
        Object[] selectedNodes = tree.getSelectionPaths();
        if (selectedNodes == null || selectedNodes.length == 0) {
            return;
        }

        DubboSetingState.CacheType type = TreeNodeTypeEnum.COLLECTIONS.equals(nowTreeNodeTypeEnum)
                ? DubboSetingState.CacheType.COLLECTIONS
                : DubboSetingState.CacheType.HISTORY;

        for (Object pathObj : selectedNodes) {
            if (pathObj instanceof TreePath) {
                TreePath path = (TreePath) pathObj;
                Object lastPathComponent = path.getLastPathComponent();
                if (lastPathComponent instanceof DefaultMutableTreeNode) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) lastPathComponent;
                    Object userObject = node.getUserObject();
                    if (userObject instanceof CacheInfo) {
                        CacheInfo cacheInfo = (CacheInfo) userObject;
                        DubboSetingState.getInstance().remove(cacheInfo, type);
                    }
                }
            }
        }
        refresh();
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
        String keyword = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        for (CacheInfo dubboMethodEntity : paramInfoCache) {
            if (!matches(dubboMethodEntity, keyword)) {
                continue;
            }
            DefaultMutableTreeNode defaultMutableTreeNode = new DefaultMutableTreeNode(dubboMethodEntity, true);
            root.add(defaultMutableTreeNode);
        }
        tree.setModel(new DefaultTreeModel(root, false));
        tree.updateUI();
    }

    /**
     * 关键字匹配。除名字外也匹配描述、接口名、方法名 ——
     * 旧数据没有自定义中文名, 只按名字搜等于搜不到存量收藏
     */
    private boolean matches(CacheInfo cacheInfo, String keyword) {
        if (keyword.isEmpty()) {
            return true;
        }
        return contains(cacheInfo.getName(), keyword)
                || contains(cacheInfo.getDescription(), keyword)
                || contains(cacheInfo.getInterfaceName(), keyword)
                || contains(cacheInfo.getMethodName(), keyword);
    }

    private boolean contains(String value, String lowerCaseKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerCaseKeyword);
    }

    public enum TreeNodeTypeEnum {
        HISTORY,
        COLLECTIONS
    }

    /**
     * 树上只显示名字, 描述等信息放 tooltip。
     * 这里不能靠改 CacheInfo.toString() 实现 —— 地址下拉框也依赖它
     */
    private static class CacheInfoTreeCellRenderer extends ColoredTreeCellRenderer {

        @Override
        public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded,
                                          boolean leaf, int row, boolean hasFocus) {
            CacheInfo cacheInfo = asCacheInfo(value);
            if (cacheInfo != null) {
                append(cacheInfo.getDisplayName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                setToolTipText(buildTooltip(cacheInfo));
            } else {
                // 根节点(Collections / History)
                append(value == null ? "" : value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                setToolTipText(null);
            }
        }

        /**
         * 上半部分是用户自己写的描述, 按 HTML 渲染(所以不转义);
         * 下半部分是自动追加的接口和地址, 用 hr 分隔。
         */
        private String buildTooltip(CacheInfo cacheInfo) {
            StringBuilder sb = new StringBuilder("<html><body style='width:360px'>");

            String description = cacheInfo.getDescription();
            if (description != null && !description.trim().isEmpty()) {
                // 不转义: 需求要求描述支持 HTML。
                // 但裸换行在 HTML 里会被忽略, 所以补成 <br>, 这样纯文本多行描述也能正常显示
                sb.append(description.trim().replace("\r\n", "<br>").replace("\n", "<br>"));
                sb.append("<hr>");
            }

            sb.append(escape(cacheInfo.getMethodName())).append("#")
                    .append(escape(cacheInfo.getInterfaceName()));
            if (cacheInfo.getAddress() != null && !cacheInfo.getAddress().isEmpty()) {
                sb.append("<br>").append(escape(cacheInfo.getAddress()));
            }
            return sb.append("</body></html>").toString();
        }

        /** 接口名/地址是数据不是文案, 里面可能带尖括号(如泛型), 必须转义 */
        private String escape(String text) {
            if (text == null) {
                return "";
            }
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    /** 从树节点里取出 CacheInfo, 非 CacheInfo 节点(如根节点)返回 null */
    private static CacheInfo asCacheInfo(Object node) {
        if (!(node instanceof DefaultMutableTreeNode)) {
            return null;
        }
        Object userObject = ((DefaultMutableTreeNode) node).getUserObject();
        return userObject instanceof CacheInfo ? (CacheInfo) userObject : null;
    }
}
