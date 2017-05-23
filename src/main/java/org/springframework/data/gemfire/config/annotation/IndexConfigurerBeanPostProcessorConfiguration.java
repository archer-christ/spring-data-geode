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
import org.springframework.data.gemfire.IndexFactoryBean;
import org.springframework.data.gemfire.search.lucene.LuceneIndexFactoryBean;

/**
 * The {@link IndexConfigurerBeanPostProcessorConfiguration} class is a Spring {@link Configuration} annotated class
 * used to register a Spring {@link BeanPostProcessor} that will post process any {@link IndexFactoryBean} beans
 * and {@link LuceneIndexFactoryBean} beans registered in the Spring application context by
 * the {@link EnableIndexing} SDG annotation in order to apply additional, custom configuration specified in
 * 1 or more user-defined {@link IndexConfigurer} beans also registered in the Spring application context.
 *
 * This Spring {@link Configuration} class and the defined Spring {@link BeanPostProcessor} bean are only registered
 * and applied when the user has also specified the {@link EnableEntityDefinedRegions}.
 *
 * @author John Blum
 * @see org.apache.geode.cache.lucene.LuceneIndex
 * @see org.apache.geode.cache.query.Index
 * @see org.springframework.beans.factory.config.BeanPostProcessor
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.annotation.Configuration
 * @see org.springframework.data.gemfire.IndexFactoryBean
 * @see org.springframework.data.gemfire.config.annotation.EnableIndexing
 * @see org.springframework.data.gemfire.config.annotation.IndexConfigurer
 * @see org.springframework.data.gemfire.search.lucene.LuceneIndexFactoryBean
 * @since 1.1.0
 */
@Configuration
@SuppressWarnings("unused")
public class IndexConfigurerBeanPostProcessorConfiguration {

	@Autowired(required = false)
	private List<IndexConfigurer> indexConfigurers = Collections.emptyList();

	private final IndexConfigurer compositeIndexConfigurer = new IndexConfigurer() {

		@Override
		public void configure(String beanName, IndexFactoryBean bean) {
			nullSafeList(indexConfigurers).forEach(indexConfigurer -> indexConfigurer.configure(beanName, bean));
		}

		@Override
		public void configure(String beanName, LuceneIndexFactoryBean bean) {
			nullSafeList(indexConfigurers).forEach(indexConfigurer -> indexConfigurer.configure(beanName, bean));
		}
	};

	/**
	 * Bean definition registering a Spring {@link BeanPostProcessor} to post process before initialization
	 * all {@link EnableIndexing}, {@link IndexFactoryBean} and {@link LuceneIndexFactoryBean} beans
	 * registered in the Spring application context by delegating to user-defined {@link IndexConfigurer} beans
	 * also registered in the Spring application context that will apply the additional, custom configuration.
	 *
	 * @return the Spring {@link BeanPostProcessor}.
	 * @see org.springframework.beans.factory.config.BeanPostProcessor
	 */
	@Bean
	public BeanPostProcessor indexConfigurerBeanPostProcessor() {

		return new BeanPostProcessor() {

			private final IndexConfigurer configurer =
				IndexConfigurerBeanPostProcessorConfiguration.this.compositeIndexConfigurer;

			@Override
			public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {

				if (bean instanceof IndexFactoryBean) {
					this.configurer.configure(beanName, (IndexFactoryBean) bean);
				}
				else if (bean instanceof LuceneIndexFactoryBean) {
					this.configurer.configure(beanName, (LuceneIndexFactoryBean) bean);
				}

				return bean;
			}
		};
	}
}
