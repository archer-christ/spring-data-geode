/*
 * Copyright 2016 the original author or authors.
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
 *
 */

package org.springframework.data.gemfire.config.annotation;

import org.apache.geode.cache.server.CacheServer;
import org.springframework.data.gemfire.server.CacheServerFactoryBean;

/**
 * The {@link CacheServerConfigurer} interface defines a contract for implementations to customize the configuration
 * of a {@link CacheServerFactoryBean} used to construct, configure and initialize a {@link CacheServer} instance.
 *
 * @author John Blum
 * @see org.apache.geode.cache.server.CacheServer
 * @see org.springframework.data.gemfire.server.CacheServerFactoryBean
 * @since 1.9.0
 */
public interface CacheServerConfigurer {

	/**
	 * Callback method providing a reference to the SDG {@link CacheServerFactoryBean} used to construct, configure
	 * and initialize an instance of the GemFire/Geode {@link CacheServer}.
	 *
	 * @param beanName name of GemFire/Geode {@link CacheServer} bean declared in the Spring context.
	 * @param cacheServerFactoryBean reference to the {@link CacheServerFactoryBean}.
	 * @see org.springframework.data.gemfire.server.CacheServerFactoryBean
	 */
	void configure(String beanName, CacheServerFactoryBean cacheServerFactoryBean);

}
