package com.yanglx.dubbo.test;

import java.io.Serializable;
import java.util.Objects;

/**
 * 收藏夹。支持任意层嵌套, {@link #parentId} 为 null 表示挂在根目录下。
 *
 * <p>只有收藏用得上文件夹, 历史记录始终平铺按时间倒序。
 */
public class FolderInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private String name;

    /** 父文件夹 id, null 表示根目录 */
    private String parentId;

    /** 同级内的排序位置, 由拖拽决定 */
    private int sortIndex;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public int getSortIndex() {
        return sortIndex;
    }

    public void setSortIndex(int sortIndex) {
        this.sortIndex = sortIndex;
    }

    /** 树上显示用, 名字为空时给个占位, 否则节点看起来是空白一行 */
    public String getDisplayName() {
        return name == null || name.isEmpty() ? "Unnamed" : name;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FolderInfo)) return false;
        return Objects.equals(id, ((FolderInfo) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}