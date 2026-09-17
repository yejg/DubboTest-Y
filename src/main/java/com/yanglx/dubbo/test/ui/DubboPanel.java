package com.yanglx.dubbo.test.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import com.yanglx.dubbo.test.CacheInfo;
import com.yanglx.dubbo.test.DubboSetingState;
import com.yanglx.dubbo.test.DubboTestBundle;
import com.yanglx.dubbo.test.PluginConstants;
import com.yanglx.dubbo.test.dubbo.DubboApiLocator;
import com.yanglx.dubbo.test.dubbo.DubboMethodEntity;
import com.yanglx.dubbo.test.utils.JsonUtils;
import com.yanglx.dubbo.test.utils.StrUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.intellij.openapi.Disposable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class DubboPanel extends JBPanel implements Disposable {
    private static final long serialVersionUID = -8541227582365214834L;

    private JPanel mainPanel;
    private JButton invokeBtn;
    private JTextField interfaceNameTextField;
    private JLabel tip;
    private JComboBox<CacheInfo> addressBox;
    private JTextField methodNameTextField;
    private JTextField timeoutTextField;
    private JTextField versionTextField;
    private JTextField groupTextField;
    private JButton saveBtn;
    private JPanel editorPane;
    private JsonEditor jsonEditorReq;
    private JsonEditor jsonEditorResp;
    private Project project;
    private final DubboMethodEntity dubboMethodEntity = new DubboMethodEntity();
    private ExecutorService executorService = Executors.newFixedThreadPool(2);

    public DubboPanel(Project project, TreePanel leftTree) {
        this.project = project;
        this.setLayout(new BorderLayout());
        initComponents();
        this.add(mainPanel, BorderLayout.CENTER);
        initEventListeners(leftTree);
        reset();
    }

    // 初始化组件。DubboPanel.form极其不稳定，只好手动绘制UI
    private void initComponents() {
        // 主面板
        mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        // 1. 顶部表单区域
        JPanel topFormPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 1.1 Address行
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        topFormPanel.add(new JLabel(DubboTestBundle.message("dubbo-test.tool.address")), gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        gbc.weightx = 1;
        addressBox = new JComboBox<>();
        topFormPanel.add(addressBox, gbc);

        // 1.2 version行
        gbc.gridx = 4;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        topFormPanel.add(new JLabel(DubboTestBundle.message("dubbo-test.tool.version")), gbc);

        gbc.gridx = 5;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0.5;
        versionTextField = new JBTextField();
        topFormPanel.add(versionTextField, gbc);

        // 1.3 group行
        gbc.gridx = 6;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        topFormPanel.add(new JLabel(DubboTestBundle.message("dubbo-test.tool.group")), gbc);

        gbc.gridx = 7;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0.5;
        groupTextField = new JBTextField();
        topFormPanel.add(groupTextField, gbc);

        // 1.4 保存按钮。原来的「另存为」已合并进保存弹框, 不再单独占位
        gbc.gridx = 8;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        saveBtn = new JButton(DubboTestBundle.message("dubbo-test.tool.save"));
        topFormPanel.add(saveBtn, gbc);

        // 2 InterfaceName行
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        topFormPanel.add(new JLabel(DubboTestBundle.message("dubbo-test.tool.interface-name")), gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 8;
        gbc.weightx = 1;
        interfaceNameTextField = new JBTextField();
        topFormPanel.add(interfaceNameTextField, gbc);

        // 3 MethodName行
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        topFormPanel.add(new JLabel(DubboTestBundle.message("dubbo-test.tool.method-name")), gbc);

        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.gridwidth = 8;
        gbc.weightx = 1;
        methodNameTextField = new JBTextField();
        topFormPanel.add(methodNameTextField, gbc);

        // 4 超时时间
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        topFormPanel.add(new JLabel(DubboTestBundle.message("dubbo-test.tool.timeout.seconds")), gbc);
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.gridwidth = 8;
        gbc.weightx = 1;
        timeoutTextField = new JBTextField(String.valueOf(PluginConstants.DEFAULT_TIMEOUT_SECOND));
        topFormPanel.add(timeoutTextField, gbc);

        mainPanel.add(topFormPanel, BorderLayout.NORTH);

        // 5 中间编辑区（Params和Response）
        editorPane = new JPanel(new BorderLayout());

        JBSplitter splitter = new JBSplitter();
        splitter.setProportion(0.5f);

        // 5.1 Params编辑器
        jsonEditorReq = new JsonEditor(project, false);
        jsonEditorReq.setBorder(BorderFactory.createTitledBorder(DubboTestBundle.message("dubbo-test.tool.params")));
        jsonEditorReq.setText("{}");

        // 5.2 Response编辑器
        jsonEditorResp = new JsonEditor(project, true);
        jsonEditorResp.setBorder(BorderFactory.createTitledBorder(DubboTestBundle.message("dubbo-test.tool.response")));
        jsonEditorResp.setText("");

        splitter.setFirstComponent(jsonEditorReq);
        splitter.setSecondComponent(jsonEditorResp);
        editorPane.add(splitter, BorderLayout.CENTER);

        mainPanel.add(editorPane, BorderLayout.CENTER);

        // 6 底部状态栏
        JPanel bottomPanel = new JPanel(new BorderLayout());
        tip = new JLabel(DubboTestBundle.message("dubbo-test.invoke.cost.time") + "0");
        tip.setBorder(new EmptyBorder(2, 5, 2, 5));

        invokeBtn = new JButton(DubboTestBundle.message("dubbo-test.tool.run"));
        bottomPanel.add(tip, BorderLayout.WEST);
        bottomPanel.add(invokeBtn, BorderLayout.EAST);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
    }

    // 事件监听初始化
    private void initEventListeners(TreePanel leftTree) {
        invokeBtn.addActionListener(e -> {
            refreshDubboMethodEntity();
            if (isBlankEntity()) return;

            DubboSetingState.getInstance().add(
                    CacheInfo.of(UUID.randomUUID().toString(),
                            dubboMethodEntity.getMethodName() + "#" + dubboMethodEntity.getInterfaceName(),
                            dubboMethodEntity),
                    DubboSetingState.CacheType.HISTORY
            );
            leftTree.refresh();

            String runText = DubboTestBundle.message("dubbo-test.tool.run");
            String loadingText = DubboTestBundle.message("dubbo-test.invoking.tooltip");

            invokeBtn.setEnabled(false);
            invokeBtn.setText(loadingText);
            jsonEditorResp.setText(loadingText);

            // 异步调用Dubbo
            Future<Object> submit = executorService.submit(() ->
                    new DubboApiLocator().invoke(dubboMethodEntity)
            );

            // 处理响应结果
            executorService.submit(() -> {
                long start = System.currentTimeMillis();
                try {
                    Object result = submit.get(dubboMethodEntity.getTimeout(), TimeUnit.SECONDS);
                    SwingUtilities.invokeLater(() -> {
                        jsonEditorResp.setText(JsonUtils.toPrettyJSONString(result));
                        tip.setText(DubboTestBundle.message("dubbo-test.invoke.cost.time") + (System.currentTimeMillis() - start));
                        invokeBtn.setText(runText);
                        invokeBtn.setEnabled(true);
                    });
                } catch (Exception ex) {
                    String error = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                    SwingUtilities.invokeLater(() -> {
                        jsonEditorResp.setText(error);
                        tip.setText(DubboTestBundle.message("dubbo-test.invoke.cost.time") + (System.currentTimeMillis() - start) + " (error)");
                        invokeBtn.setText(runText);
                        invokeBtn.setEnabled(true);
                    });
                }
            });
        });

        saveBtn.addActionListener(e -> {
            refreshDubboMethodEntity();
            if (isBlankEntity()) return;

            DubboSetingState state = DubboSetingState.getInstance();
            String currentId = dubboMethodEntity.getId();
            CacheInfo existing = state.findCollectionById(currentId);

            SaveDialogue.Mode mode = existing != null ? SaveDialogue.Mode.UPDATE : SaveDialogue.Mode.CREATE;
            String initName = existing != null ? existing.getDisplayName() : defaultName();
            String initDesc = existing != null ? existing.getDescription() : null;

            SaveDialogue dialogue = new SaveDialogue(project, mode, initName, initDesc);
            if (!dialogue.showAndGet()) return;

            String name = StrUtils.isBlank(dialogue.getInputName()) ? defaultName() : dialogue.getInputName();
            String description = dialogue.getInputDescription();

            String targetId;
            if (existing != null && !dialogue.isSaveAsNew()) {
                // 更新已有条目, id 不变
                targetId = currentId;
            } else if (StrUtils.isBlank(currentId) || dialogue.isSaveAsNew()) {
                // 新建, 或从已有条目另存出一份副本
                targetId = UUID.randomUUID().toString();
            } else {
                // 从 History 双击过来: id 非空但收藏里没有, 沿用该 id 保持当前 Tab 一致
                targetId = currentId;
            }

            // 回写 id, 否则接着再点保存会覆盖到原来那条
            dubboMethodEntity.setId(targetId);
            state.add(
                    CacheInfo.of(targetId, name, description, dubboMethodEntity),
                    DubboSetingState.CacheType.COLLECTIONS
            );
            // 当前 Tab 的标题跟着新名字走
            TabBar tabBar = TabBar.getInstance(project);
            if (tabBar != null) {
                tabBar.renameSelectedTab(name);
            }
            leftTree.refresh();
        });

        // 地址下拉框事件
        addressBox.addItemListener(e -> {
            CacheInfo item = (CacheInfo) e.getItem();
            if (item != null) {
                versionTextField.setText(item.getVersion());
                groupTextField.setText(item.getGroup());
            }
        });
    }

    public void refreshDubboMethodEntity() {
        dubboMethodEntity.setMethodName(methodNameTextField.getText());
        dubboMethodEntity.setInterfaceName(interfaceNameTextField.getText());

        CacheInfo selectedItem = (CacheInfo) addressBox.getSelectedItem();
        if (selectedItem != null) {
            dubboMethodEntity.setAddress(selectedItem.getAddress());
        }
        dubboMethodEntity.setVersion(versionTextField.getText());
        dubboMethodEntity.setGroup(groupTextField.getText());
        dubboMethodEntity.setTimeout(timeoutTextField.getText());

        try {
            String reqText = jsonEditorReq.getDocumentText();
            if (StrUtils.isNotBlank(reqText)) {
                DubboMethodEntity temp = JsonUtils.toJava(reqText, DubboMethodEntity.class);
                dubboMethodEntity.setMethodType(temp.getMethodType());
                dubboMethodEntity.setParam(temp.getParam());
            } else {
                dubboMethodEntity.setParam(new Object[]{});
                dubboMethodEntity.setMethodType(new String[]{});
            }
        } catch (Exception e) {
            dubboMethodEntity.setParam(new Object[]{});
            dubboMethodEntity.setMethodType(new String[]{});
        }
    }

    public static void refreshUI(DubboPanel dubboPanel, DubboMethodEntity dubboMethodEntity) {
        dubboPanel.dubboMethodEntity.setId(dubboMethodEntity.getId());
        dubboPanel.interfaceNameTextField.setText(dubboMethodEntity.getInterfaceName());
        dubboPanel.methodNameTextField.setText(dubboMethodEntity.getMethodName());
        dubboPanel.versionTextField.setText(dubboMethodEntity.getVersion());
        dubboPanel.groupTextField.setText(dubboMethodEntity.getGroup());
        dubboPanel.timeoutTextField.setText(dubboMethodEntity.getTimeout() + "");

        // 选择地址
        JComboBox<CacheInfo> addressBox = dubboPanel.addressBox;
        for (int i = 0; i < addressBox.getItemCount(); i++) {
            CacheInfo item = addressBox.getItemAt(i);
            String itemKey = item.getAddress() + item.getVersion() + item.getGroup();
            String targetKey = dubboMethodEntity.getAddress() + dubboMethodEntity.getVersion() + dubboMethodEntity.getGroup();
            if (itemKey.equals(targetKey)) {
                addressBox.setSelectedIndex(i);
                break;
            }
        }

        // 设置请求参数
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("param", dubboMethodEntity.getParam());
        paramMap.put("methodType", dubboMethodEntity.getMethodType());
        dubboPanel.jsonEditorReq.setText(JsonUtils.toPrettyJSONString(paramMap));
        dubboPanel.updateUI();
    }

    public void reset() {
        addressBox.removeAllItems();
        List<CacheInfo> configs = DubboSetingState.getInstance().getDubboConfigs();
        for (CacheInfo config : configs) {
            addressBox.addItem(config);
        }
    }

    private boolean isBlankEntity() {
        return StrUtils.isBlank(dubboMethodEntity.getMethodName())
                || StrUtils.isBlank(dubboMethodEntity.getInterfaceName());
    }

    /** 未自定义名字时的默认名, 与历史记录的命名保持一致 */
    private String defaultName() {
        return dubboMethodEntity.getMethodName() + "#" + dubboMethodEntity.getInterfaceName();
    }

    // Getter方法保持不变
    public JPanel getMainPanel() {
        return mainPanel;
    }

    public JButton getInvokeBtn() {
        return invokeBtn;
    }

    public JTextField getInterfaceNameTextField() {
        return interfaceNameTextField;
    }

    public JLabel getTip() {
        return tip;
    }

    public JComboBox<CacheInfo> getAddressBox() {
        return addressBox;
    }

    public JTextField getMethodNameTextField() {
        return methodNameTextField;
    }

    public JTextField getVersionTextField() {
        return versionTextField;
    }

    public JTextField getGroupTextField() {
        return groupTextField;
    }

    public JButton getSaveBtn() {
        return saveBtn;
    }

    public JPanel getEditorPane() {
        return editorPane;
    }

    public JsonEditor getJsonEditorReq() {
        return jsonEditorReq;
    }

    public JsonEditor getJsonEditorResp() {
        return jsonEditorResp;
    }

    public Project getProject() {
        return project;
    }

    public DubboMethodEntity getDubboMethodEntity() {
        return dubboMethodEntity;
    }

    @Override
    public void dispose() {
        executorService.shutdownNow();
    }
}