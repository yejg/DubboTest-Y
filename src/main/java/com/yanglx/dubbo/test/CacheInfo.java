package com.yanglx.dubbo.test;

import com.yanglx.dubbo.test.dubbo.DubboMethodEntity;
import com.yanglx.dubbo.test.utils.JsonUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class CacheInfo implements Serializable {

    /**
     * Interface name
     */
    private String interfaceName;

    /**
     * Method name
     */
    private String methodName;

    /**
     * Version
     */
    private String version;

    /**
     * Group
     */
    private String group;

    /**
     * Method type
     */
    private String methodTypeJson;

    /**
     * Param obj
     */
    private String paramObjJson;

    /**
     * Address
     */
    private String address;

    private String name;

    /**
     * 简单描述, 仅收藏用。旧数据没有该节点, 反序列化后为 null
     */
    private String description;

    private String id;

    private Date date;

    /** Timeout(second) */
    private int timeout = PluginConstants.DEFAULT_TIMEOUT_SECOND;

    /**
     * 所属收藏夹 id, null 表示根目录。仅收藏用, 历史记录不分组。
     * 旧数据没有该节点, 反序列化后为 null, 正好落在根目录
     */
    private String folderId;

    /**
     * 同级内的排序位置, 由拖拽决定。
     * 旧数据反序列化后全是 0, 由 {@code DubboSetingState} 首次读取时按日期倒序补齐
     */
    private int sortIndex;

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getMethodTypeJson() {
        return methodTypeJson;
    }

    public void setMethodTypeJson(String methodTypeJson) {
        this.methodTypeJson = methodTypeJson;
    }

    public String getParamObjJson() {
        return paramObjJson;
    }

    public void setParamObjJson(String paramObjJson) {
        this.paramObjJson = paramObjJson;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 左侧树上显示用的名字。旧数据 name 可能为空, 回退到 方法名#接口名
     *
     * @return 显示名
     */
    public String getDisplayName() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return methodName + "#" + interfaceName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getFolderId() {
        return folderId;
    }

    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }

    public int getSortIndex() {
        return sortIndex;
    }

    public void setSortIndex(int sortIndex) {
        this.sortIndex = sortIndex;
    }

    public static CacheInfo of(String id, String name, DubboMethodEntity dubboMethodEntity) {
        return of(id, name, null, dubboMethodEntity);
    }

    public static CacheInfo of(String id, String name, String description, DubboMethodEntity dubboMethodEntity) {
        CacheInfo cacheInfo = new CacheInfo();
        cacheInfo.setId(id);
        cacheInfo.setName(name);
        cacheInfo.setDescription(description);
        cacheInfo.setInterfaceName(dubboMethodEntity.getInterfaceName());
        cacheInfo.setMethodName(dubboMethodEntity.getMethodName());
        cacheInfo.setVersion(dubboMethodEntity.getVersion());
        cacheInfo.setGroup(dubboMethodEntity.getGroup());
        cacheInfo.setMethodTypeJson(JsonUtils.toJSONString(dubboMethodEntity.getMethodType()));
        cacheInfo.setParamObjJson(JsonUtils.toJSONString(dubboMethodEntity.getParam()));
        cacheInfo.setAddress(dubboMethodEntity.getAddress());
        cacheInfo.setDate(new Date());
        cacheInfo.setTimeout(dubboMethodEntity.getTimeout());
        return cacheInfo;
    }

    /**
     * 将CacheInfo转换DubboMethodEntity并返回
     *
     * @return
     */
    public DubboMethodEntity getDubboMethodEntity() {
        DubboMethodEntity dubboMethodEntity = new DubboMethodEntity();
        dubboMethodEntity.setId(this.getId());
        dubboMethodEntity.setInterfaceName(this.getInterfaceName());
        dubboMethodEntity.setMethodName(this.getMethodName());
        dubboMethodEntity.setVersion(this.getVersion());
        dubboMethodEntity.setGroup(this.getGroup());
        dubboMethodEntity.setTimeout(this.getTimeout());

        List<String> stringList = JsonUtils.toJavaList(this.getMethodTypeJson(), String.class);
        String[] methodTypes = new String[stringList.size()];
        for (int i = 0; i < stringList.size(); i++) {
            methodTypes[i] = stringList.get(i);
        }
        dubboMethodEntity.setMethodType(methodTypes);
        Object[] array = JsonUtils.toJava(this.getParamObjJson(), Object[].class);
        dubboMethodEntity.setParam(array);
        dubboMethodEntity.setAddress(this.getAddress());
        return dubboMethodEntity;
    }

    @Override
    public String toString() {
        if (name == null || name.isEmpty()) {
            return address != null ? address : "";
        }
        if (address != null && !address.isEmpty()) {
            return name + " - " + address;
        }
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CacheInfo)) return false;
        CacheInfo cacheInfo = (CacheInfo) o;
        return Objects.equals(id, cacheInfo.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
