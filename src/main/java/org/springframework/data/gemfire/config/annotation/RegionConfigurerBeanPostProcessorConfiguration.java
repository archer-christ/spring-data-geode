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

import static org.springframework.data.gemfire.util.CollectionUtils.nullSafeList;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.gemfire.RegionFactoryBean;
import org.springframework.data.gemfire.client.ClientRegionFactoryBean;

/**
 * The {@link RegionConfigurerBeanPostProcessorConfiguration} class is a Spring {@link Configuration} annotated class
 * used to register a Spring {@link BeanPostProcessor} that will post process any {@link RegionFactoryBean} beans
 * and {@link ClientRegionFactoryBean} beans registered in the Spring application context by
 * the {@link EnableEntityDefinedRegions} SDG annotation in order to apply additional, custom configuration specified in
 * 1 or more user-defined {@link RegionConfigurer} beans also registered in the Spring application context.
 *
 * This Spring {@link Configuration} class and the defined Spring {@link BeanPostProcessor} bean are only registered
 * and applied when the user has also specified the {@link EnableEntityDefinedRegions}.
 *
 * @author John Blum
 * @see org.apache.geode.cache.Region
 * @see org.springframework.beans.factory.config.BeanPostProcessor
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.annotation.Configuration
 * @see org.springframework.data.gemfire.RegionFactoryBean
 * @see org.springframework.data.gemfire.client.ClientRegionFactoryBean
 * @see org.springframework.data.gemfire.config.annotation.EnableEntityDefinedRegions
 * @see org.springframework.data.gemfire.config.annotation.RegionConfigurer
 * @since 1.1.0
 */
@Configuration
public class RegionConfigurerBeanPostProcessorConfiguration {

	@Autowired(required = false)
	private List<RegionConfigurer> regionConfigurers = Collections.emptyList();

	private final RegionConfigurer compositeRegionConfigurer = new RegionConfigurer() {

		@Override
		public void configure(String beanName, RegionFactoryBean<?, ?> bean) {
			nullSafeList(regionConfigurers).forEach(regionConfigurer -> regionConfigurer.configure(beanName, bean));
		}

		@Override
		public void configure(String beanName, ClientRegionFactoryBean<?, ?> bean) {
			nullSafeList(regionConfigurers).forEach(regionConfigurer -> regionConfigurer.configure(beanName, bean));
		}
	};

	/**
	 * Bean definition registering a Spring {@link BeanPostProcessor} to post process before initialization
	 * all {@link EnableEntityDefinedRegions}, {@link RegionFactoryBean} and {@link ClientRegionFactoryBean} beans
	 * registered in the Spring application context by delegating to user-defined {@link CacheServerConfigurer} beans
	 * also registered in the Spring application context that will apply the additional, custom configuration.
	 *
	 * @return the Spring {@link BeanPostProcessor}.
	 * @see org.springframework.beans.factory.config.BeanPostProcessor
	 */
	@Bean
	@SuppressWarnings("unused")
	public BeanPostProcessor regionConfigurerBeanPostProcessor() {

		return new BeanPostProcessor() {

			private final RegionConfigurer configurer =
				RegionConfigurerBeanPostProcessorConfiguration.this.compositeRegionConfigurer;

			@Override
			public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {

				if (bean instanceof RegionFactoryBean) {
					this.configurer.configure(beanName, (RegionFactoryBean<?, ?>) bean);
				}
				else if (bean instanceof ClientRegionFactoryBean) {
					this.configurer.configure(beanName, (ClientRegionFactoryBean<?, ?>) bean);
				}

				return bean;
			}
		};
	}
}
