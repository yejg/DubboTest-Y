package com.yanglx.dubbo.test.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import com.yanglx.dubbo.test.action.CloseTabAction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TabBar extends JBEditorTabs implements TabsListener {

    private static final Map<Project, TabBar> instances = new ConcurrentHashMap<>();

    /** Tab 标题最大显示长度, 超出则截断, 全名放 tooltip */
    private static final int MAX_TAB_TITLE_LENGTH = 20;

    private final Map<String, TabInfo> tabsMap = new ConcurrentHashMap<>(32);
    private volatile String activeTabId;
    private final Project project;
    private final TreePanel leftTree;

    public TabBar(@Nullable Project project, TreePanel leftTree) {
        super(project, IdeFocusManager.getInstance(project), project);
        this.project = project;
        this.leftTree = leftTree;
        if (project != null) {
            instances.put(project, this);
        }
        this.addListener(this);
        this.setTabDraggingEnabled(true);
        this.addTab();
    }

    public Project getProject() {
        return this.project;
    }

    public static @Nullable TabBar getInstance(@Nullable Project project) {
        if (project == null) return null;
        return instances.get(project);
    }

    public static java.util.Collection<TabBar> getAllInstances() {
        return instances.values();
    }

    public static void removeInstance(@Nullable Project project) {
        if (project != null) {
            instances.remove(project);
        }
    }

    public void addTab() {
        String tabId = UUID.randomUUID().toString();
        addTab(tabId, null);
    }

    public void addTab(String tabId) {
        addTab(tabId, null);
    }

    /**
     * @param title 用作 Tab 标题, 传 null 则退回 Tab1/Tab2 这类序号命名
     */
    public void addTab(String tabId, String title) {
        DefaultActionGroup closeActionGroup = new DefaultActionGroup();
        closeActionGroup.add(new CloseTabAction(this, tabId));

        TabInfo tabInfo = tabsMap.get(tabId);
        if (tabInfo == null) {
            Tab tab = new Tab(this.project, tabId, this.leftTree);
            TabInfo newTabInfo = new TabInfo(tab);
            applyTitle(newTabInfo, title);
            newTabInfo.setIcon(AllIcons.General.Web);
            newTabInfo.setTabLabelActions(closeActionGroup, "EditorTab");
            tabsMap.put(tabId, newTabInfo);
            this.addTab(newTabInfo);

            tabInfo = newTabInfo;
        } else if (title != null) {
            // 已打开的 tab 再次双击, 名字可能已经改过
            applyTitle(tabInfo, title);
        }

        //显示tab 聚焦当前tab
        this.select(tabInfo, true);
    }

    /**
     * 设置 Tab 标题。名字过长会截断, 全名放 tooltip
     */
    private void applyTitle(TabInfo tabInfo, String title) {
        if (title == null || title.trim().isEmpty()) {
            // 注意: 必须在 addTab(newTabInfo) 之前调用, 否则序号会多算一个
            tabInfo.setText("Tab" + (this.getTabCount() + 1));
            tabInfo.setTooltipText(null);
            return;
        }
        String trimmed = title.trim();
        if (trimmed.length() > MAX_TAB_TITLE_LENGTH) {
            tabInfo.setText(trimmed.substring(0, MAX_TAB_TITLE_LENGTH) + "...");
            tabInfo.setTooltipText(trimmed);
        } else {
            tabInfo.setText(trimmed);
            tabInfo.setTooltipText(null);
        }
    }

    /**
     * 收藏改名后同步已打开的 Tab 标题。tabId 与收藏的 id 一致(双击打开时用的就是它)
     */
    public void renameTab(String tabId, String title) {
        TabInfo tabInfo = tabsMap.get(tabId);
        if (tabInfo != null) {
            applyTitle(tabInfo, title);
        }
    }

    /**
     * 改当前活动 Tab 的标题, 用于保存后同步。
     * 保存必然发生在活动 Tab 里, 所以不按 id 查 —— 「另存为新条目」会换掉 id, 按 id 查不到
     */
    public void renameSelectedTab(String title) {
        TabInfo tabInfo = getSelectionTabInfo();
        if (tabInfo != null) {
            applyTitle(tabInfo, title);
        }
    }

    public void closeTab(@NotNull String tabId) {
        TabInfo tabInfo = tabsMap.remove(tabId);
        if (tabInfo == null) {
            // 原来没判空, removeTab(null) 会 NPE
            return;
        }
        this.removeTab(tabInfo);
        disposeTabComponent(tabInfo);
    }

    /**
     * 释放 Tab 持有的资源。不释放会漏掉两个 FileEditor 和一个线程池
     */
    private void disposeTabComponent(TabInfo tabInfo) {
        if (tabInfo.getComponent() instanceof Tab) {
            Disposer.dispose((Tab) tabInfo.getComponent());
        }
    }

    /**
     * 工具窗口关闭时释放全部 Tab, 并把自己从 static 缓存里摘掉。
     * 不摘的话 instances 会一直持有 Project, 关掉项目后整个 Project 对象都泄漏
     */
    public void disposeAllTabs() {
        for (TabInfo tabInfo : tabsMap.values()) {
            disposeTabComponent(tabInfo);
        }
        tabsMap.clear();
        removeInstance(this.project);
    }


    @Override
    public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
        Tab tab = (Tab) newSelection.getComponent();
        tab.getDubboPanel().reset();
        activeTabId = tab.getId();
    }

    public TabInfo getSelectionTabInfo() {
        return tabsMap.get(activeTabId);
    }

    public TabInfo getTabInfo(String tabId) {
        return tabsMap.get(tabId);
    }
}
