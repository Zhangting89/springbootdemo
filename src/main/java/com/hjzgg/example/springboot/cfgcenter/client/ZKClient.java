package com.hjzgg.example.springboot.cfgcenter.client;

import com.google.common.eventbus.AsyncEventBus;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

import java.util.concurrent.Executors;

/**
 * @author hujunzheng
 * @create 2019-04-01 1:22
 **/
public class ZKClient {
    private static final Log log = LogFactory.getLog(ZKClient.class);

    public static AsyncEventBus EVENT_BUS = new AsyncEventBus(Executors.newFixedThreadPool(8));//NOSONAR

    protected static ApplicationEventPublisher EVENT_PUBLISHER;
    protected static ZookeeperPropertySourceLocator PROPERTY_SOURCE_LOCATOR;
    protected static ConfigWatcher CONFIG_WATCHER;
    protected static Environment ENVIRONMENT;
    protected static CuratorFramework CURATOR;

    public static void init(ZookeeperProperties zookeeperProperties, ZookeeperConfigProperties zookeeperConfigProperties) throws Exception {
        CuratorFramework curator = curatorFramework(exponentialBackoffRetry(zookeeperProperties), zookeeperProperties);
        ZKClient.CURATOR = curator;
        ZookeeperPropertySourceLocator propertySourceLocator = zookeeperPropertySourceLocator(curator, zookeeperConfigProperties);
        ZKClient.PROPERTY_SOURCE_LOCATOR = propertySourceLocator;
        ConfigWatcher configWatcher = configWatcher(zookeeperConfigProperties, curator);
        configWatcher.start();
        ZKClient.CONFIG_WATCHER = configWatcher;
    }

    private static ZookeeperPropertySourceLocator zookeeperPropertySourceLocator(
            CuratorFramework zookeeperClientCurator, ZookeeperConfigProperties properties) {
        return new ZookeeperPropertySourceLocator(zookeeperClientCurator, properties);
    }

    private static ConfigWatcher configWatcher(ZookeeperConfigProperties zookeeperConfigProperties
            , CuratorFramework zookeeperClientCurator) {
        return new ConfigWatcher(zookeeperConfigProperties.getContext(), zookeeperClientCurator);
    }


    private static CuratorFramework curatorFramework(RetryPolicy retryPolicy, ZookeeperProperties properties) throws Exception {
        CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder();
        builder.connectString(properties.getConnectString());
        CuratorFramework curator = builder.retryPolicy(retryPolicy).build();
        curator.start();
        log.trace("blocking until connected to zookeeper for " + properties.getBlockUntilConnectedWait() + properties.getBlockUntilConnectedUnit());
        curator.blockUntilConnected(properties.getBlockUntilConnectedWait(), properties.getBlockUntilConnectedUnit());
        log.trace("connected to zookeeper");
        return curator;
    }

    private static RetryPolicy exponentialBackoffRetry(ZookeeperProperties properties) {
        return new ExponentialBackoffRetry(properties.getBaseSleepTimeMs(),
                properties.getMaxRetries(),
                properties.getMaxSleepMs());
    }

    public static boolean isConnected() {
        return CURATOR != null && CURATOR.getZookeeperClient().isConnected();
    }

    public static PropertySource resolvePropertySource() {
        if (PROPERTY_SOURCE_LOCATOR == null) {
            return null;
        }
        return PROPERTY_SOURCE_LOCATOR.getWmhCfgcenterPropertySource(ENVIRONMENT);
    }

    public static CuratorFramework getCURATOR() {
        return CURATOR;
    }
}