package com.yanglx.dubbo.test.copy;

import com.intellij.lang.Language;

/**
 * <pre>
 * 完全从com.intellij.json.JsonLanguage拷贝而来。
 * 在idea2024.3的版本中，jetBrains官方的JsonLanguage类被删除了，独立到一个单独的module中[com.intellij.modules.json]
 * 见：https://plugins.jetbrains.com/docs/intellij/api-changes-list-2024.html?from=DevkitPluginXmlInspection#json-plugin-new-20243
 * </pre>
 *
 * @author yejingang
 * @since 2025-12-15
 */
public class JsonLanguage extends Language {
    public static final JsonLanguage INSTANCE = new JsonLanguage();

    protected JsonLanguage(String ID, String... mimeTypes) {
        super(INSTANCE, ID, mimeTypes);
    }

    private JsonLanguage() {
        super("JSON", "application/json", "application/vnd.api+json", "application/hal+json", "application/ld+json");
    }

    @Override
    public boolean isCaseSensitive() {
        return true;
    }

    public boolean hasPermissiveStrings() {
        return false;
    }
}
