package com.yanglx.dubbo.test.ui;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.Collections;
import java.util.List;

/**
 * 收藏树内部拖拽用的载荷。只在插件自己的树里流转, 所以直接传节点引用,
 * 不做序列化 —— DataFlavor 用 JVM 本地对象类型即可
 */
public class TreeNodeTransferable implements Transferable {

    /** 只在当前 JVM 内有效, 不支持跨进程拖拽 */
    public static final DataFlavor FLAVOR =
            new DataFlavor(TreeNodeTransferable.class, "DubboTest collection nodes");

    private static final DataFlavor[] FLAVORS = {FLAVOR};

    private final List<Object> userObjects;

    /**
     * @param userObjects 被拖动节点的 userObject, 元素为 CacheInfo 或 FolderInfo
     */
    public TreeNodeTransferable(List<Object> userObjects) {
        this.userObjects = userObjects == null ? Collections.emptyList() : userObjects;
    }

    public List<Object> getUserObjects() {
        return userObjects;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return FLAVORS.clone();
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return FLAVOR.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!FLAVOR.equals(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }
        return this;
    }
}
