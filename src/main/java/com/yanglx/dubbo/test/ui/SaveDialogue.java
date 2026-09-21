package com.yanglx.dubbo.test.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.yanglx.dubbo.test.DubboTestBundle;

import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;

/**
 * 保存收藏时收集名字和描述。按场景提供不同的动作按钮:
 * <ul>
 *   <li>{@link Mode#CREATE} 新建条目: 保存 / 取消</li>
 *   <li>{@link Mode#UPDATE} 已有条目: 更新 / 另存为新条目 / 取消</li>
 *   <li>{@link Mode#EDIT} 仅改名字和描述, 不碰参数: 确定 / 取消</li>
 * </ul>
 */
public class SaveDialogue extends DialogWrapper {

    public enum Mode {
        CREATE,
        UPDATE,
        EDIT
    }

    private final Mode mode;
    private final String initialName;
    private final String initialDescription;

    private JBTextField nameField;
    private JBTextArea descriptionArea;

    /**
     * 是否点的是「另存为新条目」。两个确认动作都以 OK_EXIT_CODE 关闭,
     * 否则 showAndGet() 会把自定义退出码当成取消
     */
    private boolean saveAsNew;

    public SaveDialogue(@Nullable Project project, Mode mode, String initialName, String initialDescription) {
        super(project);
        this.mode = mode;
        this.initialName = initialName == null ? "" : initialName;
        this.initialDescription = initialDescription == null ? "" : initialDescription;
        this.init();
        this.setTitle(DubboTestBundle.message(Mode.EDIT.equals(mode)
                ? "dubbo-test.save.edit-title"
                : "dubbo-test.save.title"));
        // 确定按钮文案随场景变化, EDIT 用平台默认的「OK」
        if (Mode.CREATE.equals(mode)) {
            this.setOKButtonText(DubboTestBundle.message("dubbo-test.tool.save"));
        } else if (Mode.UPDATE.equals(mode)) {
            this.setOKButtonText(DubboTestBundle.message("dubbo-test.save.update"));
        }
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setPreferredSize(new Dimension(520, 260));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.weighty = 0;
        panel.add(new JLabel(DubboTestBundle.message("dubbo-test.save.name")), gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1;
        nameField = new JBTextField(this.initialName);
        panel.add(nameField, gbc);

        // 描述标签顶部对齐, 否则会垂直居中在整个 textArea 旁边
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(DubboTestBundle.message("dubbo-test.save.description")), gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        descriptionArea = new JBTextArea(this.initialDescription);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setRows(8);
        // 描述在 tooltip 里按 HTML 渲染, 这里输入的是源码, 所以不做转义也不预览
        descriptionArea.setToolTipText(DubboTestBundle.message("dubbo-test.save.description.html-hint"));
        panel.add(new JBScrollPane(descriptionArea), gbc);

        return panel;
    }

    /** 打开时聚焦名字输入框, 方便直接改名 */
    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return nameField;
    }

    /** 记住用户调整过的弹框尺寸 */
    @Override
    protected @Nullable String getDimensionServiceKey() {
        return "com.yanglx.dubbo.test.ui.SaveDialogue";
    }

    @Override
    protected Action[] createActions() {
        if (Mode.UPDATE.equals(mode)) {
            return new Action[]{getOKAction(), new SaveAsNewAction(), getCancelAction()};
        }
        return new Action[]{getOKAction(), getCancelAction()};
    }

    public String getInputName() {
        return nameField.getText();
    }

    public String getInputDescription() {
        return descriptionArea.getText();
    }

    /**
     * 是否点了「另存为新条目」。在 {@link #showAndGet()} 返回 true 之后判断,
     * 因为它和「更新」一样都算确认
     */
    public boolean isSaveAsNew() {
        return saveAsNew;
    }

    private class SaveAsNewAction extends DialogWrapperAction {

        SaveAsNewAction() {
            super(DubboTestBundle.message("dubbo-test.save.as-new"));
        }

        @Override
        protected void doAction(ActionEvent e) {
            saveAsNew = true;
            close(OK_EXIT_CODE, true);
        }
    }
}
