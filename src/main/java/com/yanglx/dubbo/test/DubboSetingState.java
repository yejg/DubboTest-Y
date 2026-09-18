package com.yanglx.dubbo.test;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * <p>Description: </p>
 *
 * @author yanglx
 * @version 1.0.0
 * @email "mailto:dev_ylx@163.com"
 * @date 2021.02.20 15:57
 * @since 1.0.0
 */
@State(
    name = "com.yanglx.dubbo.test.DubboSetingState",
    storages = {@Storage("dubbo.test.configs.xml")}
)
public class DubboSetingState implements PersistentStateComponent<DubboSetingState> {

    /** 存放收藏 */
    public LinkedList<CacheInfo> paramInfoCacheList = new LinkedList<>();
    /** 存放历史 */
    public LinkedList<CacheInfo> historyParamInfoCacheList = new LinkedList<>();

    /** 收藏夹, 支持任意层嵌套。只作用于收藏, 历史记录不分组 */
    public List<FolderInfo> folders = new ArrayList<>();

    /**
     * 收藏的手工排序是否已初始化。
     * 加文件夹之前收藏是按日期倒序展示的, 没有 sortIndex; 升级后需要按日期补一次编号,
     * 否则全是 0, 用户看到的顺序会跟升级前不一样
     */
    public boolean collectionsOrderInitialized = false;

    public List<CacheInfo> dubboConfigs = new ArrayList<>();
    //限制最大历史记录条数
    private static final int MAX_HISTORY_SIZE = 200;

    /** 按日期倒序。旧版本存的条目可能缺 date, 用 nullsLast 兜住避免 NPE */
    private static final Comparator<CacheInfo> BY_DATE_DESC =
            Comparator.comparing(CacheInfo::getDate, Comparator.nullsLast(Comparator.reverseOrder()));

    /** 收藏按手工顺序。sortIndex 相同时用日期兜底, 保证每次展示顺序一致 */
    private static final Comparator<CacheInfo> BY_SORT_INDEX =
            Comparator.comparingInt(CacheInfo::getSortIndex).thenComparing(BY_DATE_DESC);

    private static final Comparator<FolderInfo> FOLDER_BY_SORT_INDEX =
            Comparator.comparingInt(FolderInfo::getSortIndex)
                    .thenComparing(FolderInfo::getDisplayName, String.CASE_INSENSITIVE_ORDER);

    /**
     * Gets address *
     *
     * @return the address
     * @since 1.0.0
     */
    public List<CacheInfo> getParamInfoCache(CacheType cacheType) {
        if (CacheType.COLLECTIONS.equals(cacheType)) {
            migrateCollectionsOrderIfNeeded();
            // 返回副本: 排序不再落到底层 list 上, 免得别处遍历时顺序被悄悄改掉
            List<CacheInfo> sorted = new ArrayList<>(paramInfoCacheList);
            sorted.sort(BY_SORT_INDEX);
            return sorted;
        }else {
            historyParamInfoCacheList.sort(BY_DATE_DESC);
            return historyParamInfoCacheList;
        }
    }

    /**
     * 取某个文件夹的直接子收藏, 已按手工顺序排好。
     *
     * @param folderId 文件夹 id, null 表示根目录
     */
    public List<CacheInfo> getCollectionsInFolder(String folderId) {
        migrateCollectionsOrderIfNeeded();
        List<CacheInfo> result = new ArrayList<>();
        for (CacheInfo cacheInfo : paramInfoCacheList) {
            if (sameFolder(cacheInfo.getFolderId(), folderId)) {
                result.add(cacheInfo);
            }
        }
        result.sort(BY_SORT_INDEX);
        return result;
    }

    /**
     * 取某个文件夹的直接子文件夹, 已按手工顺序排好。
     *
     * @param parentId 父文件夹 id, null 表示根目录
     */
    public List<FolderInfo> getChildFolders(String parentId) {
        List<FolderInfo> result = new ArrayList<>();
        for (FolderInfo folder : folders) {
            if (sameFolder(folder.getParentId(), parentId)) {
                result.add(folder);
            }
        }
        result.sort(FOLDER_BY_SORT_INDEX);
        return result;
    }

    public FolderInfo findFolderById(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (FolderInfo folder : folders) {
            if (id.equals(folder.getId())) {
                return folder;
            }
        }
        return null;
    }

    /**
     * 新建文件夹, 挂到 parentId 末尾。
     *
     * @param name     文件夹名
     * @param parentId 父文件夹 id, null 表示根目录
     * @return 新建的文件夹
     */
    public FolderInfo addFolder(String name, String parentId) {
        FolderInfo folder = new FolderInfo();
        folder.setId(UUID.randomUUID().toString());
        folder.setName(name);
        // 父文件夹可能刚被别处删掉, 校验一下免得建出挂在幽灵父节点下的孤儿
        folder.setParentId(findFolderById(parentId) == null ? null : parentId);
        folder.setSortIndex(nextFolderSortIndex(folder.getParentId()));
        folders.add(folder);
        return folder;
    }

    /**
     * 删除文件夹, 连同其所有子孙文件夹和里面的收藏一起删。
     *
     * @param folderId 要删的文件夹 id
     * @return 被删掉的收藏条数, 供调用方做确认提示
     */
    public int removeFolderRecursively(String folderId) {
        FolderInfo target = findFolderById(folderId);
        if (target == null) {
            return 0;
        }
        Set<String> doomed = collectSelfAndDescendants(folderId);
        int removedItems = 0;
        for (CacheInfo cacheInfo : new ArrayList<>(paramInfoCacheList)) {
            if (cacheInfo.getFolderId() != null && doomed.contains(cacheInfo.getFolderId())) {
                paramInfoCacheList.remove(cacheInfo);
                removedItems++;
            }
        }
        folders.removeIf(folder -> doomed.contains(folder.getId()));
        return removedItems;
    }

    /** 统计文件夹(含子孙)里的收藏条数, 用于删除前的确认提示 */
    public int countCollectionsRecursively(String folderId) {
        Set<String> scope = collectSelfAndDescendants(folderId);
        int count = 0;
        for (CacheInfo cacheInfo : paramInfoCacheList) {
            if (cacheInfo.getFolderId() != null && scope.contains(cacheInfo.getFolderId())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 自己 + 所有子孙文件夹的 id。用迭代而非递归, 数据万一被外部编辑成环也不会栈溢出
     */
    private Set<String> collectSelfAndDescendants(String folderId) {
        Set<String> scope = new HashSet<>();
        if (folderId == null) {
            return scope;
        }
        scope.add(folderId);
        boolean grown = true;
        while (grown) {
            grown = false;
            for (FolderInfo folder : folders) {
                String parentId = folder.getParentId();
                if (parentId != null && scope.contains(parentId) && scope.add(folder.getId())) {
                    grown = true;
                }
            }
        }
        return scope;
    }

    /**
     * folderId 能否移动到 targetParentId 之下。
     * 不能移到自己或自己的子孙里, 否则那棵子树会从树上脱落, 变成再也看不到的孤儿
     */
    public boolean canMoveFolder(String folderId, String targetParentId) {
        if (folderId == null) {
            return false;
        }
        if (targetParentId == null) {
            return true;
        }
        return !collectSelfAndDescendants(folderId).contains(targetParentId);
    }

    /**
     * 重排某个文件夹下的收藏。传入的顺序即最终顺序, 顺带把 folderId 归位(跨文件夹拖拽也走这里)
     */
    public void reorderCollections(String folderId, List<CacheInfo> ordered) {
        int index = 0;
        for (CacheInfo cacheInfo : ordered) {
            cacheInfo.setFolderId(folderId);
            cacheInfo.setSortIndex(index++);
        }
        collectionsOrderInitialized = true;
    }

    /** 重排某个父节点下的子文件夹 */
    public void reorderFolders(String parentId, List<FolderInfo> ordered) {
        int index = 0;
        for (FolderInfo folder : ordered) {
            folder.setParentId(parentId);
            folder.setSortIndex(index++);
        }
    }

    /** 收藏在同级中的下一个排序号 */
    private int nextItemSortIndex(String folderId) {
        int max = -1;
        for (CacheInfo cacheInfo : paramInfoCacheList) {
            if (sameFolder(cacheInfo.getFolderId(), folderId)) {
                max = Math.max(max, cacheInfo.getSortIndex());
            }
        }
        return max + 1;
    }

    /** 文件夹在同级中的下一个排序号 */
    private int nextFolderSortIndex(String parentId) {
        int max = -1;
        for (FolderInfo folder : folders) {
            if (sameFolder(folder.getParentId(), parentId)) {
                max = Math.max(max, folder.getSortIndex());
            }
        }
        return max + 1;
    }

    /** null 和空串都当作根目录 */
    private static boolean sameFolder(String a, String b) {
        String left = a == null || a.isEmpty() ? null : a;
        String right = b == null || b.isEmpty() ? null : b;
        return left == null ? right == null : left.equals(right);
    }

    /**
     * 老数据迁移: 按日期倒序补 sortIndex, 让升级后看到的顺序与升级前一致。
     * 只跑一次, 之后顺序由用户拖拽决定
     */
    private void migrateCollectionsOrderIfNeeded() {
        if (collectionsOrderInitialized) {
            return;
        }
        List<CacheInfo> byDate = new ArrayList<>(paramInfoCacheList);
        byDate.sort(BY_DATE_DESC);
        int index = 0;
        for (CacheInfo cacheInfo : byDate) {
            cacheInfo.setSortIndex(index++);
        }
        collectionsOrderInitialized = true;
    }

    /**
     * 按 id 在收藏里查, 供保存时判断是更新已有条目还是新建
     *
     * @param id 条目 id
     * @return 找到的条目, 没有则返回 null
     */
    public CacheInfo findCollectionById(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (CacheInfo cacheInfo : this.paramInfoCacheList) {
            if (id.equals(cacheInfo.getId())) {
                return cacheInfo;
            }
        }
        return null;
    }

    public void setDubboConfigs(List<CacheInfo> cacheInfo){
        this.dubboConfigs.clear();
        this.dubboConfigs.addAll(cacheInfo);
    }

    public List<CacheInfo> getDubboConfigs(){
        if (this.dubboConfigs.isEmpty()) {
            CacheInfo cacheInfo = new CacheInfo();
            cacheInfo.setAddress("zookeeper://127.0.0.1:2181");
            cacheInfo.setName("Default");
            cacheInfo.setVersion("1.0.0");
            this.dubboConfigs.add(cacheInfo);
        }
        return this.dubboConfigs;
    }

    /**
     * 添加
     *
     * @param cacheInfo
     * @since 1.0.0
     */
    public void add(CacheInfo cacheInfo, CacheType cacheType) {
        if (CacheType.COLLECTIONS.equals(cacheType)) {
            migrateCollectionsOrderIfNeeded();
            CacheInfo existing = findCollectionById(cacheInfo.getId());
            if (existing != null) {
                // 更新已有条目: 继承它当前的文件夹和位置。
                // 原来是 remove + add, 条目会跳到末尾, 有了手工排序就不能这么干了
                cacheInfo.setFolderId(existing.getFolderId());
                cacheInfo.setSortIndex(existing.getSortIndex());
                this.paramInfoCacheList.set(this.paramInfoCacheList.indexOf(existing), cacheInfo);
            } else {
                // 新条目挂到目标文件夹末尾。folderId 由调用方带进来(比如在某文件夹里另存为)
                String folderId = findFolderById(cacheInfo.getFolderId()) == null ? null : cacheInfo.getFolderId();
                cacheInfo.setFolderId(folderId);
                cacheInfo.setSortIndex(nextItemSortIndex(folderId));
                this.paramInfoCacheList.add(cacheInfo);
            }
        }else {
            this.historyParamInfoCacheList.addFirst(cacheInfo);
            if (historyParamInfoCacheList.size() > MAX_HISTORY_SIZE) {
                this.historyParamInfoCacheList.removeLast();
            }
        }
    }

    /**
     * 移除缓存
     */
    public void remove(CacheInfo cacheInfo,CacheType cacheType){
        if (CacheType.COLLECTIONS.equals(cacheType)) {
            this.paramInfoCacheList.remove(cacheInfo);
        }else {
            this.historyParamInfoCacheList.remove(cacheInfo);
        }

    }

    /**
     * Gets instance *
     *
     * @return the instance
     * @since 1.0.0
     */
    public static DubboSetingState getInstance() {
        return ApplicationManager.getApplication().getService(DubboSetingState.class);
    }

    /**
     * Gets state *
     *
     * @return the state
     * @since 1.0.0
     */
    @Nullable
    @Override
    public DubboSetingState getState() {
        return this;
    }

    /**
     * Load state
     *
     * @param state state
     * @since 1.0.0
     */
    @Override
    public void loadState(@NotNull DubboSetingState state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public enum CacheType{
        HISTORY,
        COLLECTIONS
    }
}
