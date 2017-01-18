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

import org.apache.geode.cache.Cache;
import org.springframework.data.gemfire.CacheFactoryBean;

/**
 * The {@link PeerCacheConfigurer} interface defines a contract for implementations to customize the configuration
 * of a {@link CacheFactoryBean} used to construct, configure and initialize a peer {@link Cache} instance.
 *
 * @author John Blum
 * @see org.springframework.data.gemfire.CacheFactoryBean
 * @see org.apache.geode.cache.Cache
 * @since 1.8.0
 */
public interface PeerCacheConfigurer {

	/**
	 * Callback method providing a reference to the SDG {@link CacheFactoryBean} used to construct, configure
	 * and initialize an instance of the GemFire/Geode {@link Cache}.
	 *
	 * @param beanName name of GemFire/Geode {@link Cache} bean declared in the Spring context.
	 * @param cacheFactoryBean reference to the {@link CacheFactoryBean}.
	 * @see org.springframework.data.gemfire.CacheFactoryBean
	 */
	void configure(String beanName, CacheFactoryBean cacheFactoryBean);

}
