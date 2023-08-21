package com.wfs.safecache.config;

import com.wfs.safecache.properties.MyRedisProperties;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableConfigurationProperties(value = MyRedisProperties.class)
public class MyRedisAutoConfiguration {//TODO 完善jedis

    private final boolean COMMONS_POOL2_AVAILABLE = ClassUtils.isPresent("org.apache.commons.pool2.ObjectPool",
            MyRedisAutoConfiguration.class.getClassLoader());//TODO 删了吧，没用
    private static final String REDIS_PROTOCOL_PREFIX = "redis://";
    private static final String REDISS_PROTOCOL_PREFIX = "rediss://";

    @Autowired
    private MyRedisProperties myRedisProperties;

    @Autowired
    private ObjectProvider<LettuceClientConfigurationBuilderCustomizer> builderCustomizers;//TODO 这玩意是空

//    @Autowired
//    private ClientResources clientResources;

    //redisTemplate 和 redissonClient
    private StringRedisTemplate redisTemplate;
    private RedissonClient redissonClient;
    private LettuceConnectionFactory lettuceConnectionFactory;//要手动关闭这个工厂

    /**
     * 获取自定义配置的StringRedisTemplate，（单例模式）
     */
    public synchronized StringRedisTemplate getMyRedisTemplate() {
        if (this.redisTemplate == null) {
            //获取配置
            DefaultClientResources clientResources = DefaultClientResources.create();
            LettuceClientConfiguration clientConfig = getLettuceClientConfiguration(builderCustomizers, clientResources, myRedisProperties.getLettuce().getPool());

            //创建工厂
            this.lettuceConnectionFactory = createLettuceConnectionFactory(clientConfig);
            lettuceConnectionFactory.afterPropertiesSet();

            //创建redisTemplate
            StringRedisTemplate redisTemplate = new StringRedisTemplate(lettuceConnectionFactory);
            redisTemplate.afterPropertiesSet();

            this.redisTemplate = redisTemplate;
        }
        return this.redisTemplate;
    }
    public LettuceConnectionFactory getLettuceConnectionFactory() {
        return lettuceConnectionFactory;
    }

    /**
     * 获取自定义配置的RedissonClient，（单例模式）
     */
    public synchronized RedissonClient getMyRedissonClient() {
        if (this.redissonClient == null) {
            Config config;
            Method clusterMethod = ReflectionUtils.findMethod(MyRedisProperties.class, "getCluster");
            Method timeoutMethod = ReflectionUtils.findMethod(MyRedisProperties.class, "getTimeout");
            Object timeoutValue = ReflectionUtils.invokeMethod(timeoutMethod, myRedisProperties);
            int timeout;
            if (null == timeoutValue) {
                timeout = 10000;
            } else if (!(timeoutValue instanceof Integer)) {
                Method millisMethod = ReflectionUtils.findMethod(timeoutValue.getClass(), "toMillis");
                timeout = ((Long) ReflectionUtils.invokeMethod(millisMethod, timeoutValue)).intValue();
            } else {
                timeout = (Integer) timeoutValue;
            }

            if (myRedisProperties.getSentinel() != null) {
                Method nodesMethod = ReflectionUtils.findMethod(MyRedisProperties.Sentinel.class, "getNodes");
                Object nodesValue = ReflectionUtils.invokeMethod(nodesMethod, myRedisProperties.getSentinel());

                String[] nodes;
                if (nodesValue instanceof String) {
                    nodes = convert(Arrays.asList(((String) nodesValue).split(",")));
                } else {
                    nodes = convert((List<String>) nodesValue);
                }

                config = new Config();
                config.useSentinelServers()
                        .setMasterName(myRedisProperties.getSentinel().getMaster())
                        .addSentinelAddress(nodes)
                        .setDatabase(myRedisProperties.getDatabase())
                        .setConnectTimeout(timeout)
                        .setPassword(myRedisProperties.getPassword());
            } else if (clusterMethod != null && ReflectionUtils.invokeMethod(clusterMethod, myRedisProperties) != null) {
                Object clusterObject = ReflectionUtils.invokeMethod(clusterMethod, myRedisProperties);
                Method nodesMethod = ReflectionUtils.findMethod(clusterObject.getClass(), "getNodes");
                List<String> nodesObject = (List) ReflectionUtils.invokeMethod(nodesMethod, clusterObject);

                String[] nodes = convert(nodesObject);

                config = new Config();
                config.useClusterServers()
                        .addNodeAddress(nodes)
                        .setConnectTimeout(timeout)
                        .setPassword(myRedisProperties.getPassword());
            } else {
                config = new Config();
                String prefix = REDIS_PROTOCOL_PREFIX;
                Method method = ReflectionUtils.findMethod(MyRedisProperties.class, "isSsl");
                if (method != null && (Boolean) ReflectionUtils.invokeMethod(method, myRedisProperties)) {
                    prefix = REDISS_PROTOCOL_PREFIX;
                }

                config.useSingleServer()
                        .setAddress(prefix + myRedisProperties.getHost() + ":" + myRedisProperties.getPort())
                        .setConnectTimeout(timeout)
                        .setDatabase(myRedisProperties.getDatabase())
                        .setPassword(myRedisProperties.getPassword());
            }

            this.redissonClient = Redisson.create(config);
        }
        return redissonClient;
    }

    /********************************************************/

    private String[] convert(List<String> nodesObject) {
        List<String> nodes = new ArrayList<String>(nodesObject.size());
        for (String node : nodesObject) {
            if (!node.startsWith(REDIS_PROTOCOL_PREFIX) && !node.startsWith(REDISS_PROTOCOL_PREFIX)) {
                nodes.add(REDIS_PROTOCOL_PREFIX + node);
            } else {
                nodes.add(node);
            }
        }
        return nodes.toArray(new String[nodes.size()]);
    }

    /********************************************************/
    /********************************************************/

    private LettuceClientConfiguration getLettuceClientConfiguration(
            ObjectProvider<LettuceClientConfigurationBuilderCustomizer> builderCustomizers,
            ClientResources clientResources, MyRedisProperties.Pool pool) {
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = createBuilder(pool);
        applyProperties(builder);
        if (StringUtils.hasText(myRedisProperties.getUrl())) {
            customizeConfigurationFromUrl(builder);
        }
        builder.clientOptions(createClientOptions());
        builder.clientResources(clientResources);

//        Iterator<LettuceClientConfigurationBuilderCustomizer> iterator = builderCustomizers.orderedStream().iterator();
//        while (iterator.hasNext()){ //iterator.hasNext(),迭代里还有元素为true
//            LettuceClientConfigurationBuilderCustomizer next = iterator.next();//返回下一个元素
//            System.out.println(next);
//            iterator.remove();//删除当前元素，必须先调用next()
//        }

        builderCustomizers.orderedStream().forEach((customizer) -> customizer.customize(builder));
        return builder.build();
    }

    /********************************************************/

    private LettuceClientConfiguration.LettuceClientConfigurationBuilder createBuilder(MyRedisProperties.Pool pool) {
        if (isPoolEnabled(pool)) {
            return new PoolBuilderFactory().createBuilder(pool);
        }
        return LettuceClientConfiguration.builder();
    }

    private boolean isPoolEnabled(MyRedisProperties.Pool pool) {
        Boolean enabled = pool.getEnabled();
        return (enabled != null) ? enabled : COMMONS_POOL2_AVAILABLE;
    }

    /**
     * Inner class to allow optional commons-pool2 dependency.
     */
    private static class PoolBuilderFactory {

        LettuceClientConfiguration.LettuceClientConfigurationBuilder createBuilder(MyRedisProperties.Pool properties) {
            return LettucePoolingClientConfiguration.builder().poolConfig(getPoolConfig(properties));
        }

        private GenericObjectPoolConfig<?> getPoolConfig(MyRedisProperties.Pool properties) {
            GenericObjectPoolConfig<?> config = new GenericObjectPoolConfig<>();
            config.setMaxTotal(properties.getMaxActive());
            config.setMaxIdle(properties.getMaxIdle());
            config.setMinIdle(properties.getMinIdle());
            if (properties.getTimeBetweenEvictionRuns() != null) {
                config.setTimeBetweenEvictionRuns(properties.getTimeBetweenEvictionRuns());
            }
            if (properties.getMaxWait() != null) {
                config.setMaxWait(properties.getMaxWait());
            }
            return config;
        }
    }

    /********************************************************/

    private void applyProperties(
            LettuceClientConfiguration.LettuceClientConfigurationBuilder builder) {
        if (myRedisProperties.isSsl()) {
            builder.useSsl();
        }
        if (myRedisProperties.getTimeout() != null) {
            builder.commandTimeout(myRedisProperties.getTimeout());
        }
        if (myRedisProperties.getLettuce() != null) {
            MyRedisProperties.Lettuce lettuce = myRedisProperties.getLettuce();
            if (lettuce.getShutdownTimeout() != null && !lettuce.getShutdownTimeout().isZero()) {
                builder.shutdownTimeout(myRedisProperties.getLettuce().getShutdownTimeout());
            }
        }
        if (StringUtils.hasText(myRedisProperties.getClientName())) {
            builder.clientName(myRedisProperties.getClientName());
        }
    }

    /********************************************************/


    private void customizeConfigurationFromUrl(LettuceClientConfiguration.LettuceClientConfigurationBuilder builder) {
        MyRedisAutoConfiguration.ConnectionInfo connectionInfo = parseUrl(myRedisProperties.getUrl());
        if (connectionInfo.isUseSsl()) {
            builder.useSsl();
        }
    }

    /********************************************************/

    private ClientOptions createClientOptions() {
        ClientOptions.Builder builder = initializeClientOptionsBuilder();
        Duration connectTimeout = myRedisProperties.getConnectTimeout();
        if (connectTimeout != null) {
            builder.socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build());
        }
        return builder.timeoutOptions(TimeoutOptions.enabled()).build();
    }

    private ClientOptions.Builder initializeClientOptionsBuilder() {
        if (myRedisProperties.getCluster() != null) {
            ClusterClientOptions.Builder builder = ClusterClientOptions.builder();
            MyRedisProperties.Lettuce.Cluster.Refresh refreshProperties = myRedisProperties.getLettuce().getCluster().getRefresh();
            ClusterTopologyRefreshOptions.Builder refreshBuilder = ClusterTopologyRefreshOptions.builder()
                    .dynamicRefreshSources(refreshProperties.isDynamicRefreshSources());
            if (refreshProperties.getPeriod() != null) {
                refreshBuilder.enablePeriodicRefresh(refreshProperties.getPeriod());
            }
            if (refreshProperties.isAdaptive()) {
                refreshBuilder.enableAllAdaptiveRefreshTriggers();
            }
            return builder.topologyRefreshOptions(refreshBuilder.build());
        }
        return ClientOptions.builder();
    }

    /********************************************************/

    private LettuceConnectionFactory createLettuceConnectionFactory(LettuceClientConfiguration clientConfiguration) {
        if (getSentinelConfig() != null) {
            return new LettuceConnectionFactory(getSentinelConfig(), clientConfiguration);
        }
        if (getClusterConfiguration() != null) {
            return new LettuceConnectionFactory(getClusterConfiguration(), clientConfiguration);
        }
        return new LettuceConnectionFactory(getStandaloneConfig(), clientConfiguration);
    }

    private RedisSentinelConfiguration getSentinelConfig() {
        MyRedisProperties.Sentinel sentinelProperties = myRedisProperties.getSentinel();
        if (sentinelProperties != null) {
            RedisSentinelConfiguration config = new RedisSentinelConfiguration();
            config.master(sentinelProperties.getMaster());
            config.setSentinels(createSentinels(sentinelProperties));
            config.setUsername(myRedisProperties.getUsername());
            if (myRedisProperties.getPassword() != null) {
                config.setPassword(RedisPassword.of(myRedisProperties.getPassword()));
            }
            config.setSentinelUsername(sentinelProperties.getUsername());
            if (sentinelProperties.getPassword() != null) {
                config.setSentinelPassword(RedisPassword.of(sentinelProperties.getPassword()));
            }
            config.setDatabase(myRedisProperties.getDatabase());
            return config;
        }
        return null;
    }

    private List<RedisNode> createSentinels(MyRedisProperties.Sentinel sentinel) {
        List<RedisNode> nodes = new ArrayList<>();
        for (String node : sentinel.getNodes()) {
            try {
                nodes.add(RedisNode.fromString(node));
            } catch (RuntimeException ex) {
                throw new IllegalStateException("Invalid redis sentinel property '" + node + "'", ex);
            }
        }
        return nodes;
    }

    private RedisClusterConfiguration getClusterConfiguration() {
        if (myRedisProperties.getCluster() == null) {
            return null;
        }
        MyRedisProperties.Cluster clusterProperties = myRedisProperties.getCluster();
        RedisClusterConfiguration config = new RedisClusterConfiguration(clusterProperties.getNodes());
        if (clusterProperties.getMaxRedirects() != null) {
            config.setMaxRedirects(clusterProperties.getMaxRedirects());
        }
        config.setUsername(myRedisProperties.getUsername());
        if (myRedisProperties.getPassword() != null) {
            config.setPassword(RedisPassword.of(myRedisProperties.getPassword()));
        }
        return config;
    }

    private RedisStandaloneConfiguration getStandaloneConfig() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        if (StringUtils.hasText(myRedisProperties.getUrl())) {
            ConnectionInfo connectionInfo = parseUrl(myRedisProperties.getUrl());
            redisStandaloneConfiguration.setHostName(connectionInfo.getHostName());
            redisStandaloneConfiguration.setPort(connectionInfo.getPort());
            redisStandaloneConfiguration.setUsername(connectionInfo.getUsername());
            redisStandaloneConfiguration.setPassword(RedisPassword.of(connectionInfo.getPassword()));
        } else {
            redisStandaloneConfiguration.setHostName(myRedisProperties.getHost());
            redisStandaloneConfiguration.setPort(myRedisProperties.getPort());
            redisStandaloneConfiguration.setPassword(myRedisProperties.getPassword());
            redisStandaloneConfiguration.setUsername(myRedisProperties.getUsername());
        }
        redisStandaloneConfiguration.setDatabase(myRedisProperties.getDatabase());
        return redisStandaloneConfiguration;
    }


    private ConnectionInfo parseUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (!"redis".equals(scheme) && !"rediss".equals(scheme)) {
                throw new MyRedisUrlSyntaxException(url);
            }
            boolean useSsl = ("rediss".equals(scheme));
            String username = null;
            String password = null;
            if (uri.getUserInfo() != null) {
                String candidate = uri.getUserInfo();
                int index = candidate.indexOf(':');
                if (index >= 0) {
                    username = candidate.substring(0, index);
                    password = candidate.substring(index + 1);
                } else {
                    password = candidate;
                }
            }
            return new ConnectionInfo(uri, useSsl, username, password);
        } catch (URISyntaxException ex) {
            throw new MyRedisUrlSyntaxException(url, ex);
        }
    }

    @Data
    @AllArgsConstructor
    static class ConnectionInfo {
        private final URI uri;
        private final boolean useSsl;
        private final String username;
        private final String password;

        String getHostName() {
            return uri.getHost();
        }

        int getPort() {
            return uri.getPort();
        }
    }

    static class MyRedisUrlSyntaxException extends RuntimeException {

        private final String url;

        public MyRedisUrlSyntaxException(String url, Exception cause) {
            super(buildMessage(url), cause);
            this.url = url;
        }

        public MyRedisUrlSyntaxException(String url) {
            super(buildMessage(url));
            this.url = url;
        }

        String getUrl() {
            return this.url;
        }

        private static String buildMessage(String url) {
            return "Invalid Redis URL '" + url + "'";
        }
    }
}