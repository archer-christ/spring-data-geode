/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.data.gemfire.config.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.server.CacheServer;
import org.apache.geode.cache.server.ClientSubscriptionConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.gemfire.server.CacheServerFactoryBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Integration tests for {@link CacheServerConfigurerBeanPostProcessorConfiguration}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.apache.geode.cache.server.CacheServer
 * @see org.springframework.context.annotation.Configuration
 * @see org.springframework.data.gemfire.config.annotation.CacheServerConfigurer
 * @see org.springframework.data.gemfire.config.annotation.CacheServerConfigurerBeanPostProcessorConfiguration
 * @see org.springframework.data.gemfire.config.annotation.EnableCacheServer
 * @see org.springframework.data.gemfire.config.annotation.EnableCacheServers
 * @see org.springframework.data.gemfire.server.CacheServerFactoryBean
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @since 1.1.0
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
@SuppressWarnings("unused")
public class CacheServerConfigurerBeanPostProcessorConfigurationIntegrationTests {

	@Autowired
	@Qualifier("configurerOne")
	private TestCacheServerConfigurer configurerOne;

	@Autowired
	@Qualifier("configurerTwo")
	private TestCacheServerConfigurer configurerTwo;

	@Autowired
	@Qualifier("configurerThree")
	private TestCacheServerConfigurer configurerThree;

	protected void assertCacheServerConfigurerCalled(TestCacheServerConfigurer configurer,
			String... cacheServerBeanNames) {

		assertThat(configurer).isNotNull();
		assertThat(configurer).hasSize(cacheServerBeanNames.length);
		assertThat(configurer).contains(cacheServerBeanNames);
	}

	@Test
	public void configurerOneCalledSuccessfully() {
		assertCacheServerConfigurerCalled(configurerOne,
			"marsServer", "saturnServer", "venusServer");
	}

	@Test
	public void configurerTwoCalledSuccessfully() {
		assertCacheServerConfigurerCalled(configurerTwo,
			"marsServer", "saturnServer", "venusServer");
	}

	@Test
	public void configurerThreeCalledSuccessfully() {
		assertCacheServerConfigurerCalled(configurerThree,
			"marsServer", "saturnServer", "venusServer");
	}

	@Configuration
	@Import(CacheServerConfigurerBeanPostProcessorConfiguration.class)
	@EnableCacheServers(servers = {
		@EnableCacheServer(name = "marsServer"),
		@EnableCacheServer(name = "saturnServer"),
		@EnableCacheServer(name = "venusServer"),
	})
	static class TestConfiguration {

		@Bean
		Cache gemfireCache() {
			Cache mockCache = mock(Cache.class);

			when(mockCache.addCacheServer()).thenAnswer(invocation -> {
				CacheServer mockCacheServer = mock(CacheServer.class);
				ClientSubscriptionConfig mockClientSubscriptionConfig = mock(ClientSubscriptionConfig.class);

				when(mockCacheServer.getClientSubscriptionConfig()).thenReturn(mockClientSubscriptionConfig);

				return mockCacheServer;
			});

			return mockCache;
		}

		@Bean
		TestCacheServerConfigurer configurerOne() {
			return new TestCacheServerConfigurer();
		}

		@Bean
		TestCacheServerConfigurer configurerTwo() {
			return new TestCacheServerConfigurer();
		}

		@Bean
		TestCacheServerConfigurer configurerThree() {
			return new TestCacheServerConfigurer();
		}

		@Bean
		Object nonRelevantBean() {
			return "test";
		}
	}

	static class TestCacheServerConfigurer implements CacheServerConfigurer, Iterable<String> {

		private final Set<String> beanNames = new HashSet<>();

		@Override
		public void configure(String beanName, CacheServerFactoryBean bean) {
			this.beanNames.add(beanName);
		}

		@Override
		public Iterator<String> iterator() {
			return Collections.unmodifiableSet(this.beanNames).iterator();
		}
	}
}
