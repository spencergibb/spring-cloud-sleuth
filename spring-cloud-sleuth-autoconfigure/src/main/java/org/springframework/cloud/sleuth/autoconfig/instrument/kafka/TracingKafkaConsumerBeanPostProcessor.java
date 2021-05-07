/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.autoconfig.instrument.kafka;

import org.apache.kafka.clients.consumer.Consumer;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.sleuth.instrument.kafka.TracingKafkaConsumer;

/**
 * Bean post processor for {@link org.apache.kafka.clients.consumer.Consumer}.
 *
 * @author Anders Clausen
 * @author Flaviu Muresan
 * @since 3.1.0
 */
public class TracingKafkaConsumerBeanPostProcessor implements BeanPostProcessor {

	private final BeanFactory beanFactory;

	public TracingKafkaConsumerBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof Consumer && !(bean instanceof TracingKafkaConsumer)) {
			return new TracingKafkaConsumer<>((Consumer) bean, beanFactory);
		}
		return bean;
	}

}
