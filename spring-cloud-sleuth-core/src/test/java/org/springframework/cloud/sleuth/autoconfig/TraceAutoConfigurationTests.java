/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.autoconfig;

import java.util.List;

import brave.Tracing;
import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.propagation.B3SinglePropagation;
import brave.propagation.Propagation;
import brave.propagation.TraceContextOrSamplingFlags;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import zipkin2.reporter.InMemoryReporterMetrics;
import zipkin2.reporter.ReporterMetrics;
import zipkin2.reporter.metrics.micrometer.MicrometerReporterMetrics;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class TraceAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(
					AutoConfigurations.of(PropertyBasedBaggageConfiguration.class,
							TraceAutoConfiguration.class));

	@Test
	public void should_apply_micrometer_reporter_metrics_when_meter_registry_bean_present() {
		this.contextRunner.withUserConfiguration(WithMeterRegistry.class)
				.run((context) -> {
					ReporterMetrics bean = context.getBean(ReporterMetrics.class);

					BDDAssertions.then(bean)
							.isInstanceOf(MicrometerReporterMetrics.class);
				});
	}

	@Test
	public void should_apply_in_memory_metrics_when_meter_registry_bean_missing() {
		this.contextRunner.run((context) -> {
			ReporterMetrics bean = context.getBean(ReporterMetrics.class);

			BDDAssertions.then(bean).isInstanceOf(InMemoryReporterMetrics.class);
		});
	}

	@Test
	public void should_apply_in_memory_metrics_when_meter_registry_class_missing() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(MeterRegistry.class))
				.run((context) -> {
					ReporterMetrics bean = context.getBean(ReporterMetrics.class);

					BDDAssertions.then(bean).isInstanceOf(InMemoryReporterMetrics.class);
				});
	}

	@Test
	public void should_use_B3Propagation_factory_if_no_have_any_config() {
		this.contextRunner.run((context -> {
			final Propagation.Factory bean = context.getBean(Propagation.Factory.class);
			BDDAssertions.then(bean).isInstanceOf(Propagation.Factory.class);
		}));
	}

	@Test
	public void should_use_baggageBean() {
		this.contextRunner.withUserConfiguration(WithBaggageBeans.class, Baggage.class)
				.run((context -> {
					final Baggage bean = context.getBean(Baggage.class);
					BDDAssertions.then(bean.fields).containsOnly(
							BaggageField.create("userId"),
							BaggageField.create("userName"));
				}));
	}

	@Test
	public void should_use_local_keys_from_properties() {
		this.contextRunner.withPropertyValues("spring.sleuth.local-keys=test-key")
				.withUserConfiguration(Baggage.class).run((context -> {
					final Baggage bean = context.getBean(Baggage.class);
					BDDAssertions.then(bean.fields)
							.containsExactly(BaggageField.create("test-key"));
				}));
	}

	@Test
	public void should_combine_baggage_beans_and_properties() {
		this.contextRunner.withPropertyValues("spring.sleuth.local-keys=test-key")
				.withUserConfiguration(WithBaggageBeans.class, Baggage.class)
				.run((context -> {
					final Baggage bean = context.getBean(Baggage.class);
					BDDAssertions.then(bean.fields).containsOnly(
							BaggageField.create("userId"),
							BaggageField.create("userName"),
							BaggageField.create("test-key"));
				}));
	}

	@Test
	public void should_use_baggagePropagationFactoryBuilder_bean() {
		// BaggagePropagation.FactoryBuilder unwraps itself if there are no baggage fields
		// defined
		this.contextRunner
				.withUserConfiguration(WithBaggagePropagationFactoryBuilderBean.class)
				.run((context -> BDDAssertions
						.then(context.getBean(Propagation.Factory.class))
						.isSameAs(B3SinglePropagation.FACTORY)));
	}

	@Configuration
	static class Baggage {

		List<BaggageField> fields;

		@Autowired
		Baggage(Tracing tracing) {
			// When predefined baggage fields exist, the result !=
			// TraceContextOrSamplingFlags.EMPTY
			TraceContextOrSamplingFlags emptyExtraction = tracing.propagation()
					.extractor((c, k) -> null).extract(Boolean.TRUE);
			fields = BaggageField.getAll(emptyExtraction);
		}

	}

	@Configuration
	static class WithBaggageBeans {

		@Bean
		BaggagePropagationConfig userId() {
			return SingleBaggageField.remote(BaggageField.create("userId"));
		}

		@Bean
		BaggagePropagationConfig userName() {
			return SingleBaggageField.remote(BaggageField.create("userName"));
		}

	}

	@Configuration
	static class WithMeterRegistry {

		@Bean
		MeterRegistry meterRegistry() {
			return new SimpleMeterRegistry();
		}

	}

	@Configuration
	static class WithBaggagePropagationFactoryBuilderBean {

		@Bean
		BaggagePropagation.FactoryBuilder baggagePropagationFactoryBuilderBean() {
			return BaggagePropagation.newFactoryBuilder(B3SinglePropagation.FACTORY);
		}

	}

}
