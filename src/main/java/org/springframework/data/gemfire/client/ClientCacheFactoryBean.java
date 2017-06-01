/*
 * Copyright 2011-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.data.gemfire.client;

import static java.util.stream.StreamSupport.stream;
import static org.springframework.data.gemfire.util.ArrayUtils.nullSafeArray;
import static org.springframework.data.gemfire.util.CollectionUtils.nullSafeCollection;
import static org.springframework.data.gemfire.util.CollectionUtils.nullSafeIterable;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.geode.cache.CacheClosedException;
import org.apache.geode.cache.GemFireCache;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.Pool;
import org.apache.geode.cache.client.PoolManager;
import org.apache.geode.distributed.DistributedSystem;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.data.gemfire.GemfireUtils;
import org.springframework.data.gemfire.client.support.DefaultableDelegatingPoolAdapter;
import org.springframework.data.gemfire.client.support.DelegatingPoolAdapter;
import org.springframework.data.gemfire.config.annotation.ClientCacheConfigurer;
import org.springframework.data.gemfire.config.xml.GemfireConstants;
import org.springframework.data.gemfire.support.ConnectionEndpoint;
import org.springframework.data.gemfire.support.ConnectionEndpointList;
import org.springframework.data.gemfire.util.SpringUtils;
import org.springframework.util.StringUtils;

/**
 * Spring {@link org.springframework.beans.factory.FactoryBean} used to create a Pivotal GemFire/Apache Geode
 * {@link ClientCache}.
 *
 * @author Costin Leau
 * @author Lyndon Adams
 * @author John Blum
 * @see java.net.InetSocketAddress
 * @see org.apache.geode.cache.GemFireCache
 * @see org.apache.geode.cache.client.ClientCache
 * @see org.apache.geode.cache.client.ClientCacheFactory
 * @see org.apache.geode.cache.client.Pool
 * @see org.apache.geode.cache.client.PoolManager
 * @see org.apache.geode.distributed.DistributedSystem
 * @see org.apache.geode.pdx.PdxSerializer
 * @see org.springframework.beans.factory.BeanFactory
 * @see org.springframework.context.ApplicationContext
 * @see org.springframework.context.ApplicationListener
 * @see org.springframework.context.event.ContextRefreshedEvent
 * @see org.springframework.data.gemfire.CacheFactoryBean
 * @see org.springframework.data.gemfire.config.annotation.ClientCacheConfigurer
 * @see org.springframework.data.gemfire.support.ConnectionEndpoint
 * @see org.springframework.data.gemfire.support.ConnectionEndpointList
 */
@SuppressWarnings("unused")
public class ClientCacheFactoryBean extends CacheFactoryBean implements ApplicationListener<ContextRefreshedEvent> {

	private Boolean keepAlive = false;
	private Boolean multiUserAuthentication;
	private Boolean prSingleHopEnabled;
	private Boolean readyForEvents;
	private Boolean subscriptionEnabled;
	private Boolean threadLocalConnections;

	private ConnectionEndpointList locators = new ConnectionEndpointList();
	private ConnectionEndpointList servers = new ConnectionEndpointList();

	private Integer durableClientTimeout;
	private Integer freeConnectionTimeout;
	private Integer loadConditioningInterval;
	private Integer maxConnections;
	private Integer minConnections;
	private Integer readTimeout;
	private Integer retryAttempts;
	private Integer socketBufferSize;
	private Integer statisticsInterval;
	private Integer subscriptionAckInterval;
	private Integer subscriptionMessageTrackingTimeout;
	private Integer subscriptionRedundancy;

	private List<ClientCacheConfigurer> clientCacheConfigurers = Collections.emptyList();

	private Long idleTimeout;
	private Long pingInterval;

	private Pool pool;

	private String durableClientId;
	private String poolName;
	private String serverGroup;

	private final ClientCacheConfigurer compositeClientCacheConfigurer = (beanName, bean) ->
		nullSafeCollection(clientCacheConfigurers).forEach(clientCacheConfigurer ->
			clientCacheConfigurer.configure(beanName, bean));

	/**
	 * Post processes this {@link ClientCacheFactoryBean} before cache initialization.
	 *
	 * This is also the point at which any configured {@link ClientCacheConfigurer} beans are called.
	 *
	 * @param gemfireProperties {@link Properties} used to configure Pivotal GemFire/Apache Geode.
	 * @see org.springframework.data.gemfire.config.annotation.ClientCacheConfigurer
	 * @see java.util.Properties
	 */
	@Override
	protected void postProcessBeforeCacheInitialization(Properties gemfireProperties) {
		applyClientCacheConfigurers();
	}

	/* (non-Javadoc) */
	private void applyClientCacheConfigurers() {
		applyClientCacheConfigurers(this.compositeClientCacheConfigurer);
	}

	/**
	 * Null-safe operation to apply the given array of {@link ClientCacheConfigurer ClientCacheConfigurers}
	 * to this {@link ClientCacheFactoryBean}.
	 *
	 * @param clientCacheConfigurers array of {@link ClientCacheConfigurer ClientCacheConfigurers} applied to
	 * this {@link ClientCacheFactoryBean}.
	 * @see org.springframework.data.gemfire.config.annotation.ClientCacheConfigurer
	 * @see #applyClientCacheConfigurers(Iterable)
	 */
	protected void applyClientCacheConfigurers(ClientCacheConfigurer... clientCacheConfigurers) {
		applyClientCacheConfigurers(Arrays.asList(nullSafeArray(clientCacheConfigurers, ClientCacheConfigurer.class)));
	}

	/**
	 * Null-safe operation to apply the given {@link Iterable} of {@link ClientCacheConfigurer ClientCacheConfigurers}
	 * to this {@link ClientCacheFactoryBean}.
	 *
	 * @param clientCacheConfigurers {@link Iterable} of {@link ClientCacheConfigurer ClientCacheConfigurers}
	 * applied to this {@link ClientCacheFactoryBean}.
	 * @see org.springframework.data.gemfire.config.annotation.ClientCacheConfigurer
	 * @see java.lang.Iterable
	 */
	protected void applyClientCacheConfigurers(Iterable<ClientCacheConfigurer> clientCacheConfigurers) {
		stream(nullSafeIterable(clientCacheConfigurers).spliterator(), false)
			.forEach(clientCacheConfigurer -> clientCacheConfigurer.configure(getBeanName(), this));
	}

	/**
	 * Fetches an existing {@link ClientCache} instance from the {@link ClientCacheFactory}.
	 *
	 * @param <T> parameterized {@link Class} type extension of {@link GemFireCache}.
	 * @return an existing {@link ClientCache} instance if available.
	 * @throws org.apache.geode.cache.CacheClosedException if an existing {@link ClientCache} instance does not exist.
	 * @see org.apache.geode.cache.client.ClientCacheFactory#getAnyInstance()
	 * @see org.apache.geode.cache.GemFireCache
	 * @see #getCache()
	 */
	@Override
	@SuppressWarnings("unchecked")
	protected <T extends GemFireCache> T fetchCache() {
		return (T) Optional.ofNullable(getCache()).orElseGet(ClientCacheFactory::getAnyInstance);
	}

	/**
	 * Resolves the Pivotal GemFire/Apache Geode {@link Properties} used to configure the {@link ClientCache}.
	 *
	 * @return the resolved Pivotal GemFire/Apache Geode {@link Properties} used to configure the {@link ClientCache}.
	 * @see org.apache.geode.distributed.DistributedSystem#getProperties()
	 * @see #getDistributedSystem()
	 */
	@Override
	protected Properties resolveProperties() {

		Properties gemfireProperties = super.resolveProperties();
		DistributedSystem distributedSystem = getDistributedSystem();

		if (GemfireUtils.isConnected(distributedSystem)) {
			Properties distributedSystemProperties = (Properties) distributedSystem.getProperties().clone();
			distributedSystemProperties.putAll(gemfireProperties);
			gemfireProperties = distributedSystemProperties;
		}

		GemfireUtils.configureDurableClient(gemfireProperties, getDurableClientId(), getDurableClientTimeout());

		return gemfireProperties;
	}

	/**
	 * Returns the {@link DistributedSystem} formed from cache initialization.
	 *
	 * @param <T> {@link Class} type of the {@link DistributedSystem}.
	 * @return an instance of the {@link DistributedSystem}.
	 * @see org.apache.geode.distributed.DistributedSystem
	 */
	<T extends DistributedSystem> T getDistributedSystem() {
		return GemfireUtils.getDistributedSystem();
	}

	/**
	 * Constructs a new instance of {@link ClientCacheFactory} initialized with the given Pivotal GemFire/Apache Geode
	 * {@link Properties} used to create an instance of a {@link ClientCache}.
	 *
	 * @param gemfireProperties {@link Properties} used by the {@link ClientCacheFactory}
	 * to configure the {@link ClientCache}.
	 * @return a new instance of {@link ClientCacheFactory} initialized with the given Pivotal GemFire/Apache Geode
	 * {@link Properties}.
	 * @see org.apache.geode.cache.client.ClientCacheFactory
	 * @see java.util.Properties
	 */
	@Override
	protected Object createFactory(Properties gemfireProperties) {
		return new ClientCacheFactory(gemfireProperties);
	}

	/**
	 * Prepares and initializes the {@link ClientCacheFactory} used to create the {@link ClientCache}.
	 *
	 * Sets PDX options specified by the user.
	 *
	 * @param factory {@link ClientCacheFactory} used to create the {@link ClientCache}.
	 * @return the prepared and initialized {@link ClientCacheFactory}.
	 * @see #initializePdx(ClientCacheFactory)
	 */
	@Override
	protected Object prepareFactory(Object factory) {
		return initializePool(initializePdx((ClientCacheFactory) factory));
	}

	/**
	 * Configure PDX for the {@link ClientCacheFactory}.
	 *
	 * @param clientCacheFactory {@link ClientCacheFactory} used to configure PDX.
	 * @return the given {@link ClientCacheFactory}
	 * @see org.apache.geode.cache.client.ClientCacheFactory
	 */
	ClientCacheFactory initializePdx(ClientCacheFactory clientCacheFactory) {

		Optional.ofNullable(getPdxSerializer()).ifPresent(clientCacheFactory::setPdxSerializer);

		Optional.ofNullable(getPdxDiskStoreName()).filter(StringUtils::hasText)
			.ifPresent(clientCacheFactory::setPdxDiskStore);

		Optional.ofNullable(getPdxIgnoreUnreadFields()).ifPresent(clientCacheFactory::setPdxIgnoreUnreadFields);

		Optional.ofNullable(getPdxPersistent()).ifPresent(clientCacheFactory::setPdxPersistent);

		Optional.ofNullable(getPdxReadSerialized()).ifPresent(clientCacheFactory::setPdxReadSerialized);

		return clientCacheFactory;
	}

	/**
	 * Configure the {@literal DEFAULT} {@link Pool} configuration settings with the {@link ClientCacheFactory}
	 * using a given {@link Pool} instance or a named {@link Pool}.
	 *
	 * @param clientCacheFactory {@link ClientCacheFactory} use to configure the {@literal DEFAULT} {@link Pool}.
	 * @see org.apache.geode.cache.client.ClientCacheFactory
	 * @see org.apache.geode.cache.client.Pool
	 */
	ClientCacheFactory initializePool(ClientCacheFactory clientCacheFactory) {

		DefaultableDelegatingPoolAdapter pool = DefaultableDelegatingPoolAdapter.from(
			DelegatingPoolAdapter.from(resolvePool())).preferDefault();

		clientCacheFactory.setPoolFreeConnectionTimeout(pool.getFreeConnectionTimeout(getFreeConnectionTimeout()));
		clientCacheFactory.setPoolIdleTimeout(pool.getIdleTimeout(getIdleTimeout()));
		clientCacheFactory.setPoolLoadConditioningInterval(pool.getLoadConditioningInterval(getLoadConditioningInterval()));
		clientCacheFactory.setPoolMaxConnections(pool.getMaxConnections(getMaxConnections()));
		clientCacheFactory.setPoolMinConnections(pool.getMinConnections(getMinConnections()));
		clientCacheFactory.setPoolMultiuserAuthentication(pool.getMultiuserAuthentication(getMultiUserAuthentication()));
		clientCacheFactory.setPoolPRSingleHopEnabled(pool.getPRSingleHopEnabled(getPrSingleHopEnabled()));
		clientCacheFactory.setPoolPingInterval(pool.getPingInterval(getPingInterval()));
		clientCacheFactory.setPoolReadTimeout(pool.getReadTimeout(getReadTimeout()));
		clientCacheFactory.setPoolRetryAttempts(pool.getRetryAttempts(getRetryAttempts()));
		clientCacheFactory.setPoolServerGroup(pool.getServerGroup(getServerGroup()));
		clientCacheFactory.setPoolSocketBufferSize(pool.getSocketBufferSize(getSocketBufferSize()));
		clientCacheFactory.setPoolStatisticInterval(pool.getStatisticInterval(getStatisticsInterval()));
		clientCacheFactory.setPoolSubscriptionAckInterval(pool.getSubscriptionAckInterval(getSubscriptionAckInterval()));
		clientCacheFactory.setPoolSubscriptionEnabled(pool.getSubscriptionEnabled(getSubscriptionEnabled()));
		clientCacheFactory.setPoolSubscriptionMessageTrackingTimeout(pool.getSubscriptionMessageTrackingTimeout(getSubscriptionMessageTrackingTimeout()));
		clientCacheFactory.setPoolSubscriptionRedundancy(pool.getSubscriptionRedundancy(getSubscriptionRedundancy()));
		clientCacheFactory.setPoolThreadLocalConnections(pool.getThreadLocalConnections(getThreadLocalConnections()));

		final AtomicBoolean noServers = new AtomicBoolean(getServers().isEmpty());

		boolean hasServers = !noServers.get();
		boolean noLocators = getLocators().isEmpty();
		boolean hasLocators = !noLocators;

		if (hasServers || noLocators) {
			Iterable<InetSocketAddress> servers = pool.getServers(getServers().toInetSocketAddresses());

			stream(servers.spliterator(), false).forEach(server -> {
				clientCacheFactory.addPoolServer(server.getHostName(), server.getPort());
				noServers.set(false);
			});
		}

		if (hasLocators || noServers.get()) {
			Iterable<InetSocketAddress> locators = pool.getLocators(getLocators().toInetSocketAddresses());

			stream(locators.spliterator(), false).forEach(locator ->
				clientCacheFactory.addPoolLocator(locator.getHostName(), locator.getPort()));
		}

		return clientCacheFactory;
	}

	/**
	 * Resolves an appropriate {@link Pool} from the Spring container that will be used to configure
	 * the {@link ClientCache}.
	 *
	 * @return the resolved {@link Pool}.
	 * @see org.apache.geode.cache.client.Pool
	 * @see #findPool(String)
	 */
	Pool resolvePool() {
		Pool localPool = getPool();

		if (localPool == null) {

			String poolName = Optional.ofNullable(getPoolName()).filter(StringUtils::hasText)
				.orElse(GemfireConstants.DEFAULT_GEMFIRE_POOL_NAME);

			localPool = findPool(poolName);

			if (localPool == null) {

				BeanFactory beanFactory = getBeanFactory();

				if (beanFactory instanceof ListableBeanFactory) {
					try {
						Map<String, PoolFactoryBean> poolFactoryBeanMap =
							((ListableBeanFactory) beanFactory).getBeansOfType(PoolFactoryBean.class, false, false);

						String dereferencedPoolName = SpringUtils.dereferenceBean(poolName);

						if (poolFactoryBeanMap.containsKey(dereferencedPoolName)) {
							return poolFactoryBeanMap.get(dereferencedPoolName).getPool();
						}
					}
					catch (BeansException e) {
						logInfo("Unable to resolve bean of type [%1$s] with name [%2$s]",
							PoolFactoryBean.class.getName(), poolName);
					}
				}
			}
		}

		return localPool;
	}

	/**
	 * Attempts to find a {@link Pool} with the given {@link String name}.
	 *
	 * @param name {@link String} containing the name of the {@link Pool} to find.
	 * @return a {@link Pool} instance with the given {@link String name} registered in GemFire/Geode
	 * or {@literal null} if no {@link Pool} with the given {@link String name} exists.
	 * @see org.apache.geode.cache.client.PoolManager#find(String)
	 * @see org.apache.geode.cache.client.Pool
	 */
	Pool findPool(String name) {
		return PoolManager.find(name);
	}

	/**
	 * Creates a new {@link ClientCache} instance using the provided factory.
	 *
	 * @param <T> parameterized {@link Class} type extension of {@link GemFireCache}.
	 * @param factory instance of {@link ClientCacheFactory}.
	 * @return a new instance of {@link ClientCache} created by the provided factory.
	 * @see org.apache.geode.cache.client.ClientCacheFactory#create()
	 * @see org.apache.geode.cache.GemFireCache
	 */
	@Override
	@SuppressWarnings("unchecked")
	protected <T extends GemFireCache> T createCache(Object factory) {
		return (T) ((ClientCacheFactory) factory).create();
	}

	/**
	 * Inform the Pivotal GemFire/Apache Geode cluster that this cache client is ready to receive events
	 * iff the client is non-durable.
	 *
	 * @param event {@link ApplicationContextEvent} fired when the {@link ApplicationContext} is refreshed.
	 * @see org.apache.geode.cache.client.ClientCache#readyForEvents()
	 * @see #isReadyForEvents()
	 * @see #fetchCache()
	 */
	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		if (isReadyForEvents()) {
			try {
				this.<ClientCache>fetchCache().readyForEvents();
			}
			catch (IllegalStateException | CacheClosedException ignore) {
				// thrown if clientCache.readyForEvents() is called on a non-durable client
			}
		}
	}

	/**
	 * Null-safe internal method used to close the {@link ClientCache} and preserve durability.
	 *
	 * @param cache {@link GemFireCache} to close.
	 * @see org.apache.geode.cache.client.ClientCache#close(boolean)
	 * @see #isKeepAlive()
	 */
	@Override
	protected void close(GemFireCache cache) {
		((ClientCache) cache).close(isKeepAlive());
	}

	/**
	 * Returns the {@link Class} type of the {@link GemFireCache} produced by this {@link ClientCacheFactoryBean}.
	 *
	 * @return the {@link Class} type of the {@link GemFireCache} produced by this {@link ClientCacheFactoryBean}.
	 * @see org.springframework.beans.factory.FactoryBean#getObjectType()
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Class<? extends GemFireCache> getObjectType() {
		return Optional.ofNullable(getCache()).map(Object::getClass).orElse((Class) ClientCache.class);
	}

	/* (non-Javadoc) */
	public void addLocators(ConnectionEndpoint... locators) {
		this.locators.add(locators);
	}

	/* (non-Javadoc) */
	public void addLocators(Iterable<ConnectionEndpoint> locators) {
		this.locators.add(locators);
	}

	/* (non-Javadoc) */
	public void addServers(ConnectionEndpoint... servers) {
		this.servers.add(servers);
	}

	/* (non-Javadoc) */
	public void addServers(Iterable<ConnectionEndpoint> servers) {
		this.servers.add(servers);
	}

	/**
	 * Null-safe operation to set an array of {@link ClientCacheConfigurer ClientCacheConfigurers} used to apply
	 * additional configuration to this {@link ClientCacheFactoryBean} when using Annotation-based configuration.
	 *
	 * @param clientCacheConfigurers array of {@link ClientCacheConfigurer ClientCacheConfigurers} used to apply
	 * additional configuration to this {@link ClientCacheFactoryBean}.
	 * @see org.springframework.data.gemfire.config.annotation.ClientCacheConfigurer
	 * @see #setClientCacheConfigurers(List)
	 */
	public void setClientCacheConfigurers(ClientCacheConfigurer... clientCacheConfigurers) {
		setClientCacheConfigurers(Arrays.asList(nullSafeArray(clientCacheConfigurers, ClientCacheConfigurer.class)));
	}

	/**
	 * Null-safe operation to set an {@link Iterable} of {@link ClientCacheConfigurer ClientCacheConfigurers} to apply
	 * additional configuration to this {@link ClientCacheFactoryBean} when using Annotation-based configuration.
	 *
	 * @param peerCacheConfigurers {@link Iterable} of {@link ClientCacheConfigurer ClientCacheConfigurers} used to apply
	 * additional configuration to this {@link ClientCacheFactoryBean}.
	 * @see org.springframework.data.gemfire.config.annotation.ClientCacheConfigurer
	 */
	public void setClientCacheConfigurers(List<ClientCacheConfigurer> peerCacheConfigurers) {
		this.clientCacheConfigurers = Optional.ofNullable(peerCacheConfigurers).orElseGet(Collections::emptyList);
	}

	/**
	 * Returns a reference to the Composite {@link ClientCacheConfigurer} used to apply additional configuration
	 * to this {@link ClientCacheFactoryBean} on Spring container initialization.
	 *
	 * @return the Composite {@link ClientCacheConfigurer}.
	 * @see org.springframework.data.gemfire.config.annotation.ClientCacheConfigurer
	 */
	public ClientCacheConfigurer getCompositeClientCacheConfigurer() {
		return this.compositeClientCacheConfigurer;
	}

	/**
	 * Set the GemFire System property 'durable-client-id' to indicate to the server that this client is durable.
	 *
	 * @param durableClientId a String value indicating the durable client id.
	 */
	public void setDurableClientId(String durableClientId) {
		this.durableClientId = durableClientId;
	}

	/**
	 * Gets the value of the GemFire System property 'durable-client-id' indicating to the server whether
	 * this client is durable.
	 *
	 * @return a String value indicating the durable client id.
	 */
	public String getDurableClientId() {
		return this.durableClientId;
	}

	/**
	 * Set the GemFire System property 'durable-client-timeout' indicating to the server how long to track events
	 * for the durable client when disconnected.
	 *
	 * @param durableClientTimeout an Integer value indicating the timeout in seconds for the server to keep
	 * the durable client's queue around.
	 */
	public void setDurableClientTimeout(Integer durableClientTimeout) {
		this.durableClientTimeout = durableClientTimeout;
	}

	/**
	 * Get the value of the GemFire System property 'durable-client-timeout' indicating to the server how long
	 * to track events for the durable client when disconnected.
	 *
	 * @return an Integer value indicating the timeout in seconds for the server to keep
	 * the durable client's queue around.
	 */
	public Integer getDurableClientTimeout() {
		return this.durableClientTimeout;
	}

	/* (non-Javadoc) */
	@Override
	public final void setEnableAutoReconnect(Boolean enableAutoReconnect) {
		throw new UnsupportedOperationException("Auto-reconnect does not apply to clients");
	}

	/* (non-Javadoc) */
	@Override
	public final Boolean getEnableAutoReconnect() {
		return Boolean.FALSE;
	}

	/* (non-Javadoc) */
	public void setFreeConnectionTimeout(Integer freeConnectionTimeout) {
		this.freeConnectionTimeout = freeConnectionTimeout;
	}

	/* (non-Javadoc) */
	public Integer getFreeConnectionTimeout() {
		return freeConnectionTimeout;
	}

	/* (non-Javadoc) */
	public void setIdleTimeout(Long idleTimeout) {
		this.idleTimeout = idleTimeout;
	}

	/* (non-Javadoc) */
	public Long getIdleTimeout() {
		return idleTimeout;
	}

	/**
	 * Sets whether the server(s) should keep the durable client's queue alive for the duration of the timeout
	 * when the client voluntarily disconnects.
	 *
	 * @param keepAlive a boolean value indicating to the server to keep the durable client's queues alive.
	 */
	public void setKeepAlive(Boolean keepAlive) {
		this.keepAlive = keepAlive;
	}

	/**
	 * Gets the user specified value for whether the server(s) should keep the durable client's queue alive
	 * for the duration of the timeout when the client voluntarily disconnects.
	 *
	 * @return a boolean value indicating whether the server should keep the durable client's queues alive.
	 */
	public Boolean getKeepAlive() {
		return keepAlive;
	}

	/**
	 * Determines whether the server(s) should keep the durable client's queue alive for the duration of the timeout
	 * when the client voluntarily disconnects.
	 *
	 * @return a boolean value indicating whether the server should keep the durable client's queues alive.
	 */
	public boolean isKeepAlive() {
		return Boolean.TRUE.equals(getKeepAlive());
	}

	/* (non-Javadoc) */
	public void setLoadConditioningInterval(Integer loadConditioningInterval) {
		this.loadConditioningInterval = loadConditioningInterval;
	}

	/* (non-Javadoc) */
	public Integer getLoadConditioningInterval() {
		return loadConditioningInterval;
	}

	/* (non-Javadoc) */
	public void setLocators(ConnectionEndpoint[] locators) {
		setLocators(ConnectionEndpointList.from(locators));
	}

	/* (non-Javadoc) */
	public void setLocators(Iterable<ConnectionEndpoint> locators) {
		getLocators().clear();
		addLocators(locators);
	}

	/* (non-Javadoc) */
	protected ConnectionEndpointList getLocators() {
		return locators;
	}

	/* (non-Javadoc) */
	public void setMaxConnections(Integer maxConnections) {
		this.maxConnections = maxConnections;
	}

	/* (non-Javadoc) */
	public Integer getMaxConnections() {
		return maxConnections;
	}

	/* (non-Javadoc) */
	public void setMinConnections(Integer minConnections) {
		this.minConnections = minConnections;
	}

	/* (non-Javadoc) */
	public Integer getMinConnections() {
		return minConnections;
	}

	/* (non-Javadoc) */
	public void setMultiUserAuthentication(Boolean multiUserAuthentication) {
		this.multiUserAuthentication = multiUserAuthentication;
	}

	/* (non-Javadoc) */
	public Boolean getMultiUserAuthentication() {
		return multiUserAuthentication;
	}

	/**
	 * Sets the {@link Pool} used by this cache client to obtain connections to the GemFire cluster.
	 *
	 * @param pool the GemFire {@link Pool} used by this {@link ClientCache} to obtain connections
	 * to the GemFire cluster.
	 * @throws IllegalArgumentException if the {@link Pool} is null.
	 */
	public void setPool(Pool pool) {
		this.pool = pool;
	}

	/**
	 * Gets the {@link Pool} used by this cache client to obtain connections to the GemFire cluster.
	 *
	 * @return the GemFire {@link Pool} used by this {@link ClientCache} to obtain connections
	 * to the GemFire cluster.
	 */
	public Pool getPool() {
		return this.pool;
	}

	/**
	 * Sets the name of the {@link Pool} used by this cache client to obtain connections to the GemFire cluster.
	 *
	 * @param poolName set the name of the GemFire {@link Pool} used by this GemFire {@link ClientCache}.
	 * @throws IllegalArgumentException if the {@link Pool} name is unspecified.
	 */
	public void setPoolName(String poolName) {
		this.poolName = poolName;
	}

	/**
	 * Gets the name of the GemFire {@link Pool} used by this GemFire cache client.
	 *
	 * @return the name of the GemFire {@link Pool} used by this GemFire cache client.
	 */
	public String getPoolName() {
		return poolName;
	}

	/* (non-Javadoc) */
	public void setPingInterval(Long pingInterval) {
		this.pingInterval = pingInterval;
	}

	/* (non-Javadoc) */
	public Long getPingInterval() {
		return pingInterval;
	}

	/* (non-Javadoc) */
	public void setPrSingleHopEnabled(Boolean prSingleHopEnabled) {
		this.prSingleHopEnabled = prSingleHopEnabled;
	}

	/* (non-Javadoc) */
	public Boolean getPrSingleHopEnabled() {
		return prSingleHopEnabled;
	}

	/* (non-Javadoc) */
	public void setReadTimeout(Integer readTimeout) {
		this.readTimeout = readTimeout;
	}

	/* (non-Javadoc) */
	public Integer getReadTimeout() {
		return readTimeout;
	}

	/**
	 * Sets the readyForEvents property to indicate whether the cache client should notify the server
	 * that it is ready to receive updates.
	 *
	 * @param readyForEvents sets a boolean flag to notify the server that this durable client
	 * is ready to receive updates.
	 * @see #getReadyForEvents()
	 */
	public void setReadyForEvents(Boolean readyForEvents){
		this.readyForEvents = readyForEvents;
	}

	/**
	 * Gets the user-specified value for the readyForEvents property.
	 *
	 * @return a boolean value indicating the state of the 'readyForEvents' property.
	 */
	public Boolean getReadyForEvents(){
		return readyForEvents;
	}

	/**
	 * Determines whether this GemFire cache client is ready for events.  If 'readyForEvents' was explicitly set,
	 * then it takes precedence over all other considerations (e.g. durability).
	 *
	 * @return a boolean value indicating whether this GemFire cache client is ready for events.
	 * @see org.springframework.data.gemfire.GemfireUtils#isDurable(ClientCache)
	 * @see #getReadyForEvents()
	 */
	public boolean isReadyForEvents() {
		Boolean readyForEvents = getReadyForEvents();

		if (readyForEvents != null) {
			return Boolean.TRUE.equals(readyForEvents);
		}
		else {
			try {
				return GemfireUtils.isDurable((ClientCache) fetchCache());
			}
			catch (Throwable ignore) {
				return false;
			}
		}
	}

	/* (non-Javadoc) */
	public void setRetryAttempts(Integer retryAttempts) {
		this.retryAttempts = retryAttempts;
	}

	/* (non-Javadoc) */
	public Integer getRetryAttempts() {
		return retryAttempts;
	}

	/* (non-Javadoc) */
	public void setServerGroup(String serverGroup) {
		this.serverGroup = serverGroup;
	}

	/* (non-Javadoc) */
	public String getServerGroup() {
		return serverGroup;
	}

	/* (non-Javadoc) */
	public void setServers(ConnectionEndpoint[] servers) {
		setServers(ConnectionEndpointList.from(servers));
	}

	/* (non-Javadoc) */
	public void setServers(Iterable<ConnectionEndpoint> servers) {
		getServers().clear();
		addServers(servers);
	}

	/* (non-Javadoc) */
	protected ConnectionEndpointList getServers() {
		return servers;
	}

	/* (non-Javadoc) */
	public void setSocketBufferSize(Integer socketBufferSize) {
		this.socketBufferSize = socketBufferSize;
	}

	/* (non-Javadoc) */
	public Integer getSocketBufferSize() {
		return socketBufferSize;
	}

	/* (non-Javadoc) */
	public void setStatisticsInterval(Integer statisticsInterval) {
		this.statisticsInterval = statisticsInterval;
	}

	/* (non-Javadoc) */
	public Integer getStatisticsInterval() {
		return statisticsInterval;
	}

	/* (non-Javadoc) */
	public void setSubscriptionAckInterval(Integer subscriptionAckInterval) {
		this.subscriptionAckInterval = subscriptionAckInterval;
	}

	/* (non-Javadoc) */
	public Integer getSubscriptionAckInterval() {
		return subscriptionAckInterval;
	}

	/* (non-Javadoc) */
	public void setSubscriptionEnabled(Boolean subscriptionEnabled) {
		this.subscriptionEnabled = subscriptionEnabled;
	}

	/* (non-Javadoc) */
	public Boolean getSubscriptionEnabled() {
		return subscriptionEnabled;
	}

	/* (non-Javadoc) */
	public void setSubscriptionMessageTrackingTimeout(Integer subscriptionMessageTrackingTimeout) {
		this.subscriptionMessageTrackingTimeout = subscriptionMessageTrackingTimeout;
	}

	/* (non-Javadoc) */
	public Integer getSubscriptionMessageTrackingTimeout() {
		return subscriptionMessageTrackingTimeout;
	}

	/* (non-Javadoc) */
	public void setSubscriptionRedundancy(Integer subscriptionRedundancy) {
		this.subscriptionRedundancy = subscriptionRedundancy;
	}

	/* (non-Javadoc) */
	public Integer getSubscriptionRedundancy() {
		return subscriptionRedundancy;
	}

	/* (non-Javadoc) */
	public void setThreadLocalConnections(Boolean threadLocalConnections) {
		this.threadLocalConnections = threadLocalConnections;
	}

	/* (non-Javadoc) */
	public Boolean getThreadLocalConnections() {
		return threadLocalConnections;
	}

	/* (non-Javadoc) */
	@Override
	public final void setUseClusterConfiguration(Boolean useClusterConfiguration) {
		throw new UnsupportedOperationException("Cluster-based Configuration is not applicable for clients");
	}

	/* (non-Javadoc) */
	@Override
	public final Boolean getUseClusterConfiguration() {
		return Boolean.FALSE;
	}
}
