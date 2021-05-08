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

import org.junit.jupiter.api.Test;
import reactor.kafka.receiver.KafkaReceiver;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceNoOpAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.kafka.TracingKafkaConsumerFactory;
import org.springframework.cloud.sleuth.instrument.kafka.TracingKafkaProducerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class TraceReactorKafkaAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.sleuth.noop.enabled=true")
			.withConfiguration(AutoConfigurations.of(TraceNoOpAutoConfiguration.class,
					TracingKafkaAutoConfiguration.class, TracingReactorKafkaAutoConfiguration.class));

	@Test
	void should_not_create_factories_when_reactor_kafka_not_on_classpath() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(KafkaReceiver.class))
				.run(context -> assertThat(context).doesNotHaveBean(TracingKafkaProducerFactory.class)
						.doesNotHaveBean(TracingKafkaConsumerFactory.class));
	}

	@Test
	void should_create_factories_when_reactor_kafka_on_classpath() {
		this.contextRunner.run(context -> assertThat(context).hasSingleBean(TracingKafkaProducerFactory.class)
				.hasSingleBean(TracingKafkaConsumerFactory.class));
	}

}
