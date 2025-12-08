package com.yanglx.dubbo.test.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.JBSplitter;
import com.yanglx.dubbo.test.action.AddTabAction;
import com.yanglx.dubbo.test.action.CollectionsAbstractTabEditorAction;
import com.yanglx.dubbo.test.action.HistoryAbstractTabEditorAction;
import com.yanglx.dubbo.test.action.SettingAction;

import javax.swing.JComponent;

public class ToolBarPanel extends SimpleToolWindowPanel implements Disposable {

    private static final float SPLIT_PROPORTION = 0.1f;

    private ActionManager actionManager;

    private TabBar tabBar;

    private TreePanel leftTree;

    public ActionManager getActionManager() {
        return actionManager;
    }

    public void setActionManager(ActionManager actionManager) {
        this.actionManager = actionManager;
    }

    public TabBar getTabBar() {
        return tabBar;
    }

    public void setTabBar(TabBar tabBar) {
        this.tabBar = tabBar;
    }

    public TreePanel getLeftTree() {
        return leftTree;
    }

    public void setLeftTree(TreePanel leftTree) {
        this.leftTree = leftTree;
    }

    public ToolBarPanel(Project project, ToolWindow toolWindow) {
        super(false, true);

        this.actionManager = ActionManager.getInstance();
        if (this.actionManager == null) {
            throw new IllegalStateException("Failed to obtain ActionManager instance.");
        }

        buildMainLayout(project, toolWindow);
    }

    private void buildMainLayout(Project project, ToolWindow toolWindow) {
        JBSplitter contentSplitter = new JBSplitter();
        contentSplitter.setProportion(SPLIT_PROPORTION); // 按照1:9的比例进行分割

        // 左树结构，默认为收藏，占10%
        this.leftTree = new TreePanel(TreePanel.TreeNodeTypeEnum.COLLECTIONS);
        contentSplitter.setFirstComponent(this.leftTree);

        // tabBar
        this.tabBar = new TabBar(project, this.leftTree);
        contentSplitter.setSecondComponent(this.tabBar);

        this.leftTree.setTabBar(tabBar);
        this.leftTree.refresh(); // 确保界面已经添加到容器之后再刷新

        this.setToolbar(createToolbar());
        this.setContent(contentSplitter);
    }

    private JComponent createToolbar() {
        AddTabAction addTabAction = new AddTabAction(this.tabBar);

        HistoryAbstractTabEditorAction historyAction = new HistoryAbstractTabEditorAction(this.leftTree);
        CollectionsAbstractTabEditorAction collectionsAction = new CollectionsAbstractTabEditorAction(this.leftTree);
        SettingAction settingAction = new SettingAction();

        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(addTabAction);
        actionGroup.add(collectionsAction);
        actionGroup.add(historyAction);
        actionGroup.add(settingAction);
        ActionToolbar actionToolbar = this.actionManager.createActionToolbar("toolbar", actionGroup, false);
        actionToolbar.setTargetComponent(this.tabBar);
        return actionToolbar.getComponent();
    }

    @Override
    public void dispose() {

    }
}
