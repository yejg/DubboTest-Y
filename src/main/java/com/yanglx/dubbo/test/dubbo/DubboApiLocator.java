package com.yanglx.dubbo.test.dubbo;

import com.google.common.collect.Lists;
import com.yanglx.dubbo.test.PluginConstants;
import com.yanglx.dubbo.test.common.AddressTypeEnum;
import com.yanglx.dubbo.test.utils.JsonUtils;
import com.yanglx.dubbo.test.utils.StrUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.AbstractInterfaceConfig;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.config.context.ConfigManager;
import org.apache.dubbo.config.utils.ReferenceConfigCache;
import org.apache.dubbo.config.utils.ReferenceConfigCache.KeyGenerator;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.service.GenericService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>Description: </p>
 *
 * @author yanglx
 * @version 1.0.0
 * @email "mailto:dev_ylx@163.com"
 * @date 2021.02.20 15:57
 * @since 1.0.0
 */
public class DubboApiLocator {

    /**
     * application
     */
    private static final ApplicationConfig application = new ApplicationConfig();
    static {
        // 设置Dubbo版本避免空指针异常
        if (System.getProperty("dubbo.version") == null) {
            System.setProperty("dubbo.version", "3.0.0");
        }
        application.setName(PluginConstants.PLUGIN_NAME);
        application.setQosEnable(false);  // 禁用QoS，用不到QoS、避免报端口占用错误
        DubboBootstrap bootstrap = DubboBootstrap.getInstance();
        bootstrap.application(application);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DubboApiLocator.class);
    private static final String CACHE_NAME = PluginConstants.PLUGIN_NAME;

    /** ConfigManager 中做唯一性校验的私有 map, 见 ConfigManager#checkDuplicatedInterfaceConfig */
    private static final String REFERENCE_CONFIG_CACHE_FIELD = "referenceConfigCache";

    /** 按接口(group/interface:version)加锁, 避免并发初始化同一接口时相互覆盖登记项 */
    private static final Map<String, Object> INIT_LOCKS = new ConcurrentHashMap<>();

    /**
     * change key when dubboTest config changed
     */
    private static final KeyGenerator generator = (referenceConfig) -> {
        // interfaceName
        String interfaceName = referenceConfig.getInterface();
        if (StringUtils.isBlank(interfaceName)) {
            Class<?> clazz = referenceConfig.getInterfaceClass();
            interfaceName = clazz.getName();
        }
        if (StringUtils.isBlank(interfaceName)) {
            throw new IllegalArgumentException("No interface info in ReferenceConfig" + referenceConfig);
        }

        // get other unique param
        String group = StringUtils.defaultString(referenceConfig.getGroup());
        String version = StringUtils.defaultString(referenceConfig.getVersion());
        String url = StringUtils.defaultString(referenceConfig.getUrl());
        String registries = StringUtils.defaultString(
                JsonUtils.toJSONString(referenceConfig.getRegistries()));

        List<String> uniqueParams = Lists.newArrayList(interfaceName, group, version, url, registries);
        return String.join("_", uniqueParams);
    };

    /**
     * Invoke
     *
     * @param dubboMethodEntity dubbo method entity
     * @return the object
     * @since 1.0.0
     */
    public Object invoke(DubboMethodEntity dubboMethodEntity) {
        LOGGER.debug("invoke method, {}", JsonUtils.toJSONString(dubboMethodEntity));

        if (dubboMethodEntity == null
                || StrUtils.isBlank(dubboMethodEntity.getAddress())
                || StrUtils.isBlank(dubboMethodEntity.getMethodName())
                || StrUtils.isBlank(dubboMethodEntity.getInterfaceName())) {
            return "";
        }
//        Thread.currentThread().setContextClassLoader(DubboMethodEntity.class.getClassLoader());
        ReferenceConfig<GenericService> referenceConfig = this.getReferenceConfig(dubboMethodEntity);
        ReferenceConfigCache cache = ReferenceConfigCache.getCache(CACHE_NAME, generator);
        GenericService genericService = this.referGenericService(cache, referenceConfig);
        return genericService.$invoke(dubboMethodEntity.getMethodName(),
                dubboMethodEntity.getMethodType(),
                dubboMethodEntity.getParam());
    }

    /**
     * 获取泛化调用代理.
     *
     * <p>ReferenceConfig#init() 一开头就调 bootstrap.reference(this), 把自己登记进全局
     * ConfigManager(按 group/interface:version 唯一); 而 ReferenceConfigCache#get 只在
     * ReferenceConfig#get() 成功返回后才写入代理缓存。两者不同步会导致:
     *
     * <ul>
     *   <li>init 阶段失败(连不上注册中心、no provider available、初始化超时): 代理缓存是空的,
     *       ConfigManager 里却留下了失败的 config, 下次调用新建 ReferenceConfig 就撞唯一性校验;</li>
     *   <li>调用成功后改地址/改参数: cache key 变化会触发新的 init, 同样撞上仍在册的旧 config。</li>
     * </ul>
     *
     * <p>两种情况都会抛 {@code IllegalStateException: Found multiple ReferenceConfigs...},
     * 且现有 API 清不掉(destroy 不碰 ConfigManager, removeConfig 只清 configsCache),
     * 结果是该接口永久不可用, 只能重启 IDE。故这里在会触发真正 init 之前先摘除历史登记项,
     * init 失败后也把本次的登记项清掉。
     *
     * @param cache           代理缓存
     * @param referenceConfig 本次新建的 reference
     * @return 泛化调用代理
     */
    private GenericService referGenericService(ReferenceConfigCache cache,
                                               ReferenceConfig<GenericService> referenceConfig) {
        String cacheKey = generator.generateKey(referenceConfig);
        // 已有代理可直接命中, 不会走 init, 无需干预
        if (cache.getReferredReferences().containsKey(cacheKey)) {
            return cache.get(referenceConfig);
        }

        String uniqueServiceName = referenceConfig.getUniqueServiceName();
        Object lock = INIT_LOCKS.computeIfAbsent(uniqueServiceName, k -> new Object());
        synchronized (lock) {
            // 双重检查: 等锁期间可能已被其他线程初始化完成
            if (cache.getReferredReferences().containsKey(cacheKey)) {
                return cache.get(referenceConfig);
            }
            // 本次必然触发 init, 先摘掉同名的历史登记项
            unregisterFromConfigManager(uniqueServiceName);
            try {
                return cache.get(referenceConfig);
            } catch (Throwable t) {
                // init 失败, 把本次刚登记进去的项也清掉, 否则下次调用必撞唯一性校验
                unregisterFromConfigManager(uniqueServiceName);
                throw t;
            }
        }
    }

    /**
     * 从全局 ConfigManager 中摘除指定接口的 ReferenceConfig 登记项.
     *
     * <p>只影响后续的唯一性校验, 不影响已经建好的代理(那些由 ReferenceConfigCache 独立持有)。
     * 唯一性校验用的是 ConfigManager 的私有字段 referenceConfigCache, 公开的 removeConfig()
     * 只清 configsCache, clear() 又会连 ApplicationConfig 一起清掉, 故此处用反射精准移除。
     * 反射失败时降级为不清理, 行为退回修复前, 不影响正常调用链路。
     *
     * @param uniqueServiceName group/interface:version
     */
    private void unregisterFromConfigManager(String uniqueServiceName) {
        if (StrUtils.isBlank(uniqueServiceName)) {
            return;
        }
        try {
            ConfigManager configManager = ApplicationModel.getConfigManager();
            Field field = ConfigManager.class.getDeclaredField(REFERENCE_CONFIG_CACHE_FIELD);
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, AbstractInterfaceConfig> referenceConfigCache =
                    (Map<String, AbstractInterfaceConfig>) field.get(configManager);
            AbstractInterfaceConfig stale = referenceConfigCache.remove(uniqueServiceName);
            if (stale != null) {
                // removeConfig 按引用相等判定, 必须传入从 ConfigManager 里取出的那个实例
                configManager.removeConfig(stale);
                LOGGER.debug("removed stale ReferenceConfig from ConfigManager: {}", uniqueServiceName);
            }
        } catch (Throwable t) {
            LOGGER.warn("clean stale ReferenceConfig failed: {}", uniqueServiceName, t);
        }
    }

    /**
     * Get reference config
     *
     * @param dubboMethodEntity dubbo method entity
     * @return the reference config
     * @since 1.0.0
     */
    private ReferenceConfig<GenericService> getReferenceConfig(DubboMethodEntity dubboMethodEntity) {
        ReferenceConfig<GenericService> reference = new ReferenceConfig<>();
        // reference.setApplication(application);
        reference.setInterface(dubboMethodEntity.getInterfaceName());
        reference.setCheck(false);
        reference.setGeneric("true");
        reference.setRetries(0);
        reference.setTimeout(dubboMethodEntity.getTimeout() * 1000);
        if (dubboMethodEntity.getAddress().startsWith(AddressTypeEnum.dubbo.name())) {
            reference.setUrl(dubboMethodEntity.getAddress());
        } else {
            RegistryConfig registryConfig = this.getRegistryConfig(dubboMethodEntity);
            reference.setRegistry(registryConfig);
        }
        if (StrUtils.isNotBlank(dubboMethodEntity.getVersion())) {
            reference.setVersion(dubboMethodEntity.getVersion());
        }
        if (StrUtils.isNotBlank(dubboMethodEntity.getGroup())) {
            reference.setGroup(dubboMethodEntity.getGroup());
        }
        return reference;
    }

    /**
     * Gets registry config *
     *
     * @param dubboMethodEntity dubboMethodEntity
     * @return the registry config
     * @since 1.0.0
     */
    private RegistryConfig getRegistryConfig(DubboMethodEntity dubboMethodEntity) {
        String address = dubboMethodEntity.getAddress();
        RegistryConfig registryConfig = new RegistryConfig();
        Map<String, String> param = new HashMap<>();
        param.put("dubbo.application.service-discovery.migration", "APPLICATION_FIRST");
        registryConfig.setParameters(param);
        registryConfig.setAddress(address);
        return registryConfig;
    }

}
