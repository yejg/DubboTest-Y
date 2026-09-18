package com.yanglx.dubbo.test.ui;

import com.yanglx.dubbo.test.CacheInfo;
import com.yanglx.dubbo.test.DubboSetingState;
import com.yanglx.dubbo.test.FolderInfo;

import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.List;

/**
 * 收藏树的拖拽: 同级调顺序, 跨文件夹搬家。只对收藏生效, 历史记录不挂这个 handler。
 *
 * <p>落点分两种:
 * <ul>
 *   <li>落在两行之间 —— 插到该位置, 父容器是 drop 路径指向的节点</li>
 *   <li>落在文件夹/根节点上 —— 追加到该文件夹末尾</li>
 * </ul>
 */
public class CollectionTreeTransferHandler extends TransferHandler {

    private final TreePanel treePanel;

    public CollectionTreeTransferHandler(TreePanel treePanel) {
        this.treePanel = treePanel;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return MOVE;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        if (!treePanel.isReorderable()) {
            return null;
        }
        JTree tree = (JTree) c;
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null || paths.length == 0) {
            return null;
        }
        List<Object> dragged = new ArrayList<>();
        for (TreePath path : paths) {
            Object userObject = userObjectOf(path.getLastPathComponent());
            // 根节点不可拖动
            if (userObject instanceof CacheInfo || userObject instanceof FolderInfo) {
                dragged.add(userObject);
            }
        }
        return dragged.isEmpty() ? null : new TreeNodeTransferable(dragged);
    }

    @Override
    public boolean canImport(TransferSupport support) {
        if (!support.isDrop() || !support.isDataFlavorSupported(TreeNodeTransferable.FLAVOR)) {
            return false;
        }
        // 历史记录固定按时间倒序, 不参与排序;
        // 搜索过滤时树上只是子集, childIndex 换算不出真实位置, 拖了会把顺序搅乱
        if (!treePanel.isReorderable()) {
            return false;
        }
        JTree.DropLocation location = (JTree.DropLocation) support.getDropLocation();
        TreePath path = location.getPath();
        if (path == null) {
            return false;
        }
        List<Object> dragged = extract(support);
        if (dragged.isEmpty()) {
            return false;
        }
        String targetParentId = resolveTargetFolderId(path, location.getChildIndex());
        if (targetParentId == null && !isRootContainer(path, location.getChildIndex())) {
            // 落点算不出容器(比如落在某个收藏条目正上方), 不接
            return false;
        }
        DubboSetingState state = DubboSetingState.getInstance();
        for (Object item : dragged) {
            // 文件夹不能拖进自己或自己的子孙里, 否则整棵子树会脱离根节点再也看不见
            if (item instanceof FolderInfo
                    && !state.canMoveFolder(((FolderInfo) item).getId(), targetParentId)) {
                return false;
            }
        }
        support.setShowDropLocation(true);
        return true;
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) {
            return false;
        }
        JTree.DropLocation location = (JTree.DropLocation) support.getDropLocation();
        TreePath path = location.getPath();
        int childIndex = location.getChildIndex();
        String targetFolderId = resolveTargetFolderId(path, childIndex);

        List<Object> dragged = extract(support);
        List<CacheInfo> draggedItems = new ArrayList<>();
        List<FolderInfo> draggedFolders = new ArrayList<>();
        for (Object item : dragged) {
            if (item instanceof CacheInfo) {
                draggedItems.add((CacheInfo) item);
            } else if (item instanceof FolderInfo) {
                draggedFolders.add((FolderInfo) item);
            }
        }

        DubboSetingState state = DubboSetingState.getInstance();
        // 文件夹和收藏在树上是两段(文件夹在前), 各自维护顺序, 所以分开重排
        if (!draggedFolders.isEmpty()) {
            List<FolderInfo> siblings = state.getChildFolders(targetFolderId);
            int insertAt = resolveInsertIndex(siblings, draggedFolders,
                    folderSectionIndex(path, childIndex, siblings.size()));
            siblings.removeAll(draggedFolders);
            siblings.addAll(Math.min(insertAt, siblings.size()), draggedFolders);
            state.reorderFolders(targetFolderId, siblings);
        }
        if (!draggedItems.isEmpty()) {
            List<CacheInfo> siblings = state.getCollectionsInFolder(targetFolderId);
            int insertAt = resolveInsertIndex(siblings, draggedItems,
                    itemSectionIndex(path, childIndex, siblings.size()));
            siblings.removeAll(draggedItems);
            siblings.addAll(Math.min(insertAt, siblings.size()), draggedItems);
            state.reorderCollections(targetFolderId, siblings);
        }

        // 拖完保持选中并展开目标文件夹, 否则条目搬进折叠的文件夹后像是凭空消失了
        treePanel.refreshAfterDrop(targetFolderId, dragged);
        return true;
    }

    /**
     * 落点所属的容器文件夹 id。null 既可能表示根目录, 也可能表示落点无效,
     * 调用方需配合 {@link #isRootContainer} 判断
     */
    private String resolveTargetFolderId(TreePath path, int childIndex) {
        Object node = path.getLastPathComponent();
        Object userObject = userObjectOf(node);
        if (childIndex == -1) {
            // 落在节点本身上: 文件夹和根节点收作容器, 落在收藏条目上则视为落进它所在的文件夹
            if (userObject instanceof FolderInfo) {
                return ((FolderInfo) userObject).getId();
            }
            if (userObject instanceof CacheInfo) {
                return ((CacheInfo) userObject).getFolderId();
            }
            return null; // 根节点
        }
        // 落在子节点之间: path 指向父容器
        if (userObject instanceof FolderInfo) {
            return ((FolderInfo) userObject).getId();
        }
        return null; // 根节点下
    }

    /** 落点的容器是否是根节点(区分「根目录」与「无效落点」) */
    private boolean isRootContainer(TreePath path, int childIndex) {
        Object userObject = userObjectOf(path.getLastPathComponent());
        // 根节点的 userObject 是 "Collections" 这个字符串, 不是 null, 所以按「非收藏非文件夹」判断
        if (!(userObject instanceof CacheInfo) && !(userObject instanceof FolderInfo)) {
            return true;
        }
        // 落在根目录下的收藏上/之间, 容器仍是根目录
        return userObject instanceof CacheInfo && ((CacheInfo) userObject).getFolderId() == null;
    }

    /**
     * 树上子节点是「文件夹在前, 收藏在后」。childIndex 是整段的下标,
     * 换算成文件夹段内的插入位置
     */
    private int folderSectionIndex(TreePath path, int childIndex, int siblingCount) {
        if (childIndex < 0) {
            return siblingCount; // 落在文件夹本身上 → 追加到末尾
        }
        return Math.min(childIndex, siblingCount);
    }

    /**
     * 收藏段的插入位置。childIndex 落在文件夹段里时算 0(收藏段的开头)
     */
    private int itemSectionIndex(TreePath path, int childIndex, int siblingCount) {
        if (childIndex < 0) {
            return siblingCount;
        }
        int itemIndex = childIndex - countFolderChildren(path.getLastPathComponent());
        if (itemIndex < 0) {
            return 0;
        }
        return Math.min(itemIndex, siblingCount);
    }

    /**
     * childIndex 是「含被拖条目」那棵树上的下标, 而重排时被拖条目会先被摘掉,
     * 所以要减去落点之前已被摘走的个数。不减的话同级往下拖会少挪一位, 拖到末尾更是纹丝不动
     *
     * @param siblings    未摘除被拖条目的同级列表
     * @param dragged     被拖动的条目
     * @param rawIndex    树上换算出的插入下标
     * @return 摘除后列表中的插入下标
     */
    private <T> int resolveInsertIndex(List<T> siblings, List<? extends T> dragged, int rawIndex) {
        int removedBefore = 0;
        for (int i = 0; i < rawIndex && i < siblings.size(); i++) {
            if (dragged.contains(siblings.get(i))) {
                removedBefore++;
            }
        }
        return rawIndex - removedBefore;
    }

    /** 该节点下有多少个文件夹子节点 */
    private int countFolderChildren(Object node) {
        if (!(node instanceof DefaultMutableTreeNode)) {
            return 0;
        }
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node;
        int count = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (userObjectOf(parent.getChildAt(i)) instanceof FolderInfo) {
                count++;
            }
        }
        return count;
    }

    private List<Object> extract(TransferSupport support) {
        try {
            Object data = support.getTransferable().getTransferData(TreeNodeTransferable.FLAVOR);
            if (data instanceof TreeNodeTransferable) {
                return ((TreeNodeTransferable) data).getUserObjects();
            }
        } catch (Exception e) {
            // 拖拽数据取不到就当拒绝, 没必要打断用户操作
        }
        return new ArrayList<>();
    }

    private static Object userObjectOf(Object node) {
        return node instanceof DefaultMutableTreeNode ? ((DefaultMutableTreeNode) node).getUserObject() : null;
    }
}
