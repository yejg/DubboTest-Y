package com.yanglx.dubbo.test.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
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
import com.yanglx.dubbo.test.FolderInfo;
import com.yanglx.dubbo.test.dubbo.DubboMethodEntity;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.DropMode;
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
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
        // 拖拽排序。INSERT 才会在行间画插入线, ON_OR_INSERT 兼顾「拖到文件夹上」
        tree.setDragEnabled(true);
        tree.setDropMode(DropMode.ON_OR_INSERT);
        tree.setTransferHandler(new CollectionTreeTransferHandler(this));
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
     * 当前是否允许拖拽排序。只有「收藏 + 无搜索关键字」时才允许:
     * 历史记录固定按时间倒序; 搜索状态下树上只是过滤后的子集,
     * 按树上的下标回写顺序会把没显示出来的条目挤乱
     */
    boolean isReorderable() {
        if (!TreeNodeTypeEnum.COLLECTIONS.equals(nowTreeNodeTypeEnum)) {
            return false;
        }
        return searchField == null || searchField.getText().trim().isEmpty();
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
        if (userObject instanceof FolderInfo) {
            // 双击文件夹只切换展开状态, 别去开 Tab
            TreePath path = new TreePath(selectedNode.getPath());
            if (tree.isExpanded(path)) {
                tree.collapsePath(path);
            } else {
                tree.expandPath(path);
            }
            return;
        }
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
        TreePath clickedPath = tree.getPathForLocation(x, y);
        // 右键落在未选中的节点上时改选它。
        // 原来完全不碰选中状态(为了保住多选删除), 结果是没先左键选过就右键删除时,
        // 选中集为空, 菜单点了毫无反应。落在已选中的节点上仍保持原样, 多选删除照常可用
        if (clickedPath != null && !tree.isPathSelected(clickedPath)) {
            tree.setSelectionPath(clickedPath);
        }
        JPopupMenu menu = createPopupMenu(x, y);
        menu.show(tree, x, y);
    }

    private JPopupMenu createPopupMenu(int x, int y) {
        JPopupMenu menu = new JPopupMenu();

        // 只改名字和描述, 不碰参数。收藏才有意义, 历史记录不提供。
        // 取光标下的节点而非选中项 —— 右键点空白处时选中项可能是别的行, 菜单该按光标位置决定
        if (TreeNodeTypeEnum.COLLECTIONS.equals(nowTreeNodeTypeEnum)) {
            TreePath clickedPath = tree.getPathForLocation(x, y);
            Object clickedNode = clickedPath == null ? null : clickedPath.getLastPathComponent();
            CacheInfo clicked = asCacheInfo(clickedNode);
            FolderInfo clickedFolder = asFolderInfo(clickedNode);

            JMenuItem editItem = new JMenuItem(DubboTestBundle.message("dubbo-test.tool.edit"));
            editItem.setEnabled(clicked != null);
            editItem.addActionListener(actionEvent -> editNameAndDescription(clicked));
            menu.add(editItem);

            menu.addSeparator();
            addFolderMenuItems(menu, clickedFolder, clicked);
            menu.addSeparator();
        }

        JMenuItem menuItem = new JMenuItem(DubboTestBundle.message("dubbo-test.tool.delete"));
        // 没有可删对象时置灰, 否则又是「能点但没反应」
        menuItem.setEnabled(hasDeletableSelection());
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

    /** 选中项里是否有真能删的东西。根节点选中不算 */
    private boolean hasDeletableSelection() {
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null) {
            return false;
        }
        for (TreePath path : paths) {
            Object node = path.getLastPathComponent();
            if (asCacheInfo(node) != null || asFolderInfo(node) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 文件夹相关菜单项。右键落在哪决定新文件夹建在哪:
     * 文件夹上 → 兄弟 + 子文件夹两个选项; 收藏上 → 建在它所在的文件夹里; 空白 → 建在根目录
     */
    private void addFolderMenuItems(JPopupMenu menu, FolderInfo clickedFolder, CacheInfo clickedItem) {
        String siblingParentId = clickedFolder != null
                ? clickedFolder.getParentId()
                : (clickedItem != null ? clickedItem.getFolderId() : null);

        JMenuItem newFolder = new JMenuItem(DubboTestBundle.message("dubbo-test.folder.new"), AllIcons.Actions.NewFolder);
        newFolder.addActionListener(e -> createFolder(siblingParentId));
        menu.add(newFolder);

        if (clickedFolder != null) {
            JMenuItem newChild = new JMenuItem(DubboTestBundle.message("dubbo-test.folder.new-child"));
            newChild.addActionListener(e -> createFolder(clickedFolder.getId()));
            menu.add(newChild);

            JMenuItem rename = new JMenuItem(DubboTestBundle.message("dubbo-test.folder.rename"));
            rename.addActionListener(e -> renameFolder(clickedFolder));
            menu.add(rename);
            // 删除文件夹走下面通用的「删除」, 那条已支持递归删除和多选, 不再单开一个入口
        }
    }

    private void createFolder(String parentId) {
        String name = Messages.showInputDialog(projectOrNull(),
                DubboTestBundle.message("dubbo-test.folder.name"),
                DubboTestBundle.message("dubbo-test.folder.new-title"),
                null,
                DubboTestBundle.message("dubbo-test.folder.default-name"),
                null);
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        FolderInfo folder = DubboSetingState.getInstance().addFolder(name.trim(), parentId);
        refresh();
        // 新建后把它展开并选中, 方便直接往里拖东西
        selectFolder(folder.getId());
    }

    private void renameFolder(FolderInfo folder) {
        String name = Messages.showInputDialog(projectOrNull(),
                DubboTestBundle.message("dubbo-test.folder.name"),
                DubboTestBundle.message("dubbo-test.folder.rename-title"),
                null,
                folder.getName(),
                null);
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        folder.setName(name.trim());
        refresh();
    }

    private Project projectOrNull() {
        return tabBar == null ? null : tabBar.getProject();
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
        TreePath[] selectedNodes = tree.getSelectionPaths();
        if (selectedNodes == null || selectedNodes.length == 0) {
            return;
        }

        DubboSetingState.CacheType type = TreeNodeTypeEnum.COLLECTIONS.equals(nowTreeNodeTypeEnum)
                ? DubboSetingState.CacheType.COLLECTIONS
                : DubboSetingState.CacheType.HISTORY;
        DubboSetingState state = DubboSetingState.getInstance();

        List<CacheInfo> items = new ArrayList<>();
        List<FolderInfo> folders = new ArrayList<>();
        for (TreePath path : selectedNodes) {
            Object lastPathComponent = path.getLastPathComponent();
            CacheInfo cacheInfo = asCacheInfo(lastPathComponent);
            if (cacheInfo != null) {
                items.add(cacheInfo);
                continue;
            }
            FolderInfo folder = asFolderInfo(lastPathComponent);
            if (folder != null) {
                folders.add(folder);
            }
        }

        // 选中里带文件夹时要连内容一起删, 没有撤销机制所以先算总数确认
        if (!folders.isEmpty()) {
            if (!confirmFolderDeletion(folders, items.size(), state)) {
                return;
            }
        }

        for (CacheInfo cacheInfo : items) {
            state.remove(cacheInfo, type);
        }
        for (FolderInfo folder : folders) {
            state.removeFolderRecursively(folder.getId());
        }
        refresh();
    }

    /**
     * 删文件夹前确认。单选报文件夹名, 多选只报个数 ——
     * 拼一串名字在确认框里既长又难读
     *
     * @param folders   待删的文件夹
     * @param extraItems 同时选中的散装收藏条数
     * @return 用户是否确认
     */
    private boolean confirmFolderDeletion(List<FolderInfo> folders, int extraItems, DubboSetingState state) {
        int affected = extraItems;
        for (FolderInfo folder : folders) {
            affected += state.countCollectionsRecursively(folder.getId());
        }
        String message;
        if (folders.size() == 1) {
            String name = folders.get(0).getDisplayName();
            message = affected > 0
                    ? DubboTestBundle.message("dubbo-test.folder.delete-confirm", name, affected)
                    : DubboTestBundle.message("dubbo-test.folder.delete-confirm-empty", name);
        } else {
            message = affected > 0
                    ? DubboTestBundle.message("dubbo-test.folder.delete-confirm-multi", folders.size(), affected)
                    : DubboTestBundle.message("dubbo-test.folder.delete-confirm-multi-empty", folders.size());
        }
        int answer = Messages.showYesNoDialog(projectOrNull(), message,
                DubboTestBundle.message("dubbo-test.folder.delete-confirm-title"), Messages.getWarningIcon());
        return answer == Messages.YES;
    }

    /**
     * 设置数据模型。整棵树是重建的, 所以要先记下展开的文件夹再还原,
     * 不然每次保存/改名都会把用户展开的文件夹全收起来
     */
    private void setModel() {
        Set<String> expandedFolderIds = collectExpandedFolderIds();
        String keyword = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        DubboSetingState instance = DubboSetingState.getInstance();
        DefaultMutableTreeNode root;

        if (TreeNodeTypeEnum.COLLECTIONS.equals(nowTreeNodeTypeEnum)) {
            root = new DefaultMutableTreeNode("Collections");
            appendFolderChildren(root, null, keyword, instance);
        } else {
            // 历史记录不分组, 保持原来的平铺 + 时间倒序
            root = new DefaultMutableTreeNode("History");
            for (CacheInfo cacheInfo : instance.getParamInfoCache(DubboSetingState.CacheType.HISTORY)) {
                if (matches(cacheInfo, keyword)) {
                    root.add(new DefaultMutableTreeNode(cacheInfo, false));
                }
            }
        }
        tree.setModel(new DefaultTreeModel(root, false));
        tree.updateUI();
        // 搜索时全展开, 否则命中项藏在折叠的文件夹里等于没搜到
        if (!keyword.isEmpty()) {
            expandAll();
        } else {
            restoreExpandedFolders(expandedFolderIds);
        }
    }

    /**
     * 递归挂上某个文件夹下的子文件夹和收藏。文件夹排在收藏前面,
     * 与 {@link CollectionTreeTransferHandler} 的下标换算保持一致
     *
     * @param parentId 父文件夹 id, null 表示根目录
     * @return 该子树内是否有命中关键字的收藏
     */
    private boolean appendFolderChildren(DefaultMutableTreeNode parentNode, String parentId,
                                         String keyword, DubboSetingState state) {
        boolean anyMatch = false;
        for (FolderInfo folder : state.getChildFolders(parentId)) {
            DefaultMutableTreeNode folderNode = new DefaultMutableTreeNode(folder, true);
            boolean childMatched = appendFolderChildren(folderNode, folder.getId(), keyword, state);
            // 搜索时保留命中项的父文件夹, 空文件夹在无关键字时照常显示
            boolean selfMatched = keyword.isEmpty() || contains(folder.getName(), keyword);
            if (childMatched || selfMatched) {
                parentNode.add(folderNode);
                anyMatch = true;
            }
        }
        for (CacheInfo cacheInfo : state.getCollectionsInFolder(parentId)) {
            if (matches(cacheInfo, keyword)) {
                parentNode.add(new DefaultMutableTreeNode(cacheInfo, false));
                anyMatch = true;
            }
        }
        return anyMatch;
    }

    /** 记录当前展开的文件夹 id, 供重建后还原 */
    private Set<String> collectExpandedFolderIds() {
        Set<String> ids = new HashSet<>();
        Object rootObj = tree.getModel() == null ? null : tree.getModel().getRoot();
        if (!(rootObj instanceof TreeNode)) {
            return ids;
        }
        Enumeration<TreePath> expanded = tree.getExpandedDescendants(new TreePath(rootObj));
        if (expanded == null) {
            return ids;
        }
        while (expanded.hasMoreElements()) {
            Object node = expanded.nextElement().getLastPathComponent();
            FolderInfo folder = asFolderInfo(node);
            if (folder != null) {
                ids.add(folder.getId());
            }
        }
        return ids;
    }

    private void restoreExpandedFolders(Set<String> folderIds) {
        if (folderIds.isEmpty()) {
            return;
        }
        Object root = tree.getModel().getRoot();
        if (root instanceof DefaultMutableTreeNode) {
            expandMatching((DefaultMutableTreeNode) root, folderIds);
        }
    }

    private void expandMatching(DefaultMutableTreeNode node, Set<String> folderIds) {
        FolderInfo folder = asFolderInfo(node);
        if (folder != null && folderIds.contains(folder.getId())) {
            tree.expandPath(new TreePath(node.getPath()));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            TreeNode child = node.getChildAt(i);
            if (child instanceof DefaultMutableTreeNode) {
                expandMatching((DefaultMutableTreeNode) child, folderIds);
            }
        }
    }

    private void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
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
                return;
            }
            FolderInfo folder = asFolderInfo(value);
            if (folder != null) {
                setIcon(AllIcons.Nodes.Folder);
                append(folder.getDisplayName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                int childCount = ((DefaultMutableTreeNode) value).getChildCount();
                if (childCount > 0) {
                    // 折叠状态下也能看出里面有多少条, 省得为了确认内容反复展开
                    append("  " + childCount, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
                setToolTipText(null);
                return;
            }
            // 根节点(Collections / History)
            append(value == null ? "" : value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            setToolTipText(null);
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

    /** 从树节点里取出 CacheInfo, 非 CacheInfo 节点(如根节点、文件夹)返回 null */
    private static CacheInfo asCacheInfo(Object node) {
        if (!(node instanceof DefaultMutableTreeNode)) {
            return null;
        }
        Object userObject = ((DefaultMutableTreeNode) node).getUserObject();
        return userObject instanceof CacheInfo ? (CacheInfo) userObject : null;
    }

    /** 从树节点里取出 FolderInfo, 非文件夹节点返回 null */
    private static FolderInfo asFolderInfo(Object node) {
        if (!(node instanceof DefaultMutableTreeNode)) {
            return null;
        }
        Object userObject = ((DefaultMutableTreeNode) node).getUserObject();
        return userObject instanceof FolderInfo ? (FolderInfo) userObject : null;
    }

    /**
     * 拖拽落地后刷新: 展开目标文件夹并重新选中被拖的条目。
     * 不做的话条目搬进折叠的文件夹后, 看起来像是被拖丢了
     *
     * @param targetFolderId 落点文件夹 id, null 表示根目录
     * @param moved          被拖动的 userObject(CacheInfo / FolderInfo)
     */
    void refreshAfterDrop(String targetFolderId, List<Object> moved) {
        refresh();
        if (targetFolderId != null) {
            selectFolder(targetFolderId);
        }
        reselect(moved);
    }

    /** 展开并选中指定文件夹 */
    private void selectFolder(String folderId) {
        DefaultMutableTreeNode node = findNodeByFolderId(folderId);
        if (node == null) {
            return;
        }
        TreePath path = new TreePath(node.getPath());
        tree.expandPath(path);
        tree.setSelectionPath(path);
        tree.scrollPathToVisible(path);
    }

    /** 按 userObject 重新选中节点, 用于刷新后保持选中态 */
    private void reselect(List<Object> userObjects) {
        if (userObjects == null || userObjects.isEmpty()) {
            return;
        }
        List<TreePath> paths = new ArrayList<>();
        for (Object userObject : userObjects) {
            DefaultMutableTreeNode node = findNodeByUserObject(userObject);
            if (node != null) {
                paths.add(new TreePath(node.getPath()));
            }
        }
        if (!paths.isEmpty()) {
            tree.setSelectionPaths(paths.toArray(new TreePath[0]));
            tree.scrollPathToVisible(paths.get(0));
        }
    }

    private DefaultMutableTreeNode findNodeByFolderId(String folderId) {
        return findNode(node -> {
            FolderInfo folder = asFolderInfo(node);
            return folder != null && folder.getId().equals(folderId);
        });
    }

    private DefaultMutableTreeNode findNodeByUserObject(Object userObject) {
        return findNode(node -> node.getUserObject() != null && node.getUserObject().equals(userObject));
    }

    /** 深度优先找第一个满足条件的节点 */
    private DefaultMutableTreeNode findNode(java.util.function.Predicate<DefaultMutableTreeNode> predicate) {
        Object root = tree.getModel() == null ? null : tree.getModel().getRoot();
        if (!(root instanceof DefaultMutableTreeNode)) {
            return null;
        }
        Enumeration<?> nodes = ((DefaultMutableTreeNode) root).depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            Object element = nodes.nextElement();
            if (element instanceof DefaultMutableTreeNode && predicate.test((DefaultMutableTreeNode) element)) {
                return (DefaultMutableTreeNode) element;
            }
        }
        return null;
    }

    /**
     * 当前选中位置对应的文件夹, 供保存新收藏时决定落在哪个分组。
     * 选中文件夹 → 该文件夹; 选中收藏 → 它所在的文件夹; 什么都没选 → 根目录
     */
    public String getSelectedFolderId() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return null;
        }
        FolderInfo folder = asFolderInfo(path.getLastPathComponent());
        if (folder != null) {
            return folder.getId();
        }
        CacheInfo cacheInfo = asCacheInfo(path.getLastPathComponent());
        return cacheInfo == null ? null : cacheInfo.getFolderId();
    }
}
