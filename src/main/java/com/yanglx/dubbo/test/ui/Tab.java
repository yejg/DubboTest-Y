package com.yanglx.dubbo.test.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
import java.awt.BorderLayout;

public class Tab extends JPanel implements Disposable {

    private String id;

    private DubboPanel dubboPanel;

    public Tab(@NotNull Project project, @NotNull String id, @NotNull TreePanel leftTree) {
        dubboPanel = new DubboPanel(project, leftTree);
        // 关闭 tab 时由 TabBar 触发本对象释放, 再级联到 DubboPanel 及其编辑器、线程池
        Disposer.register(this, dubboPanel);
        this.setLayout(new BorderLayout());
        this.add(dubboPanel, BorderLayout.CENTER, 0);
        this.id = id;
    }

    /** 释放动作都在子 Disposable 上, 这里无需额外处理 */
    @Override
    public void dispose() {
    }

    public final String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public DubboPanel getDubboPanel() {
        return dubboPanel;
    }

    public void setDubboPanel(DubboPanel dubboPanel) {
        this.dubboPanel = dubboPanel;
    }
}
