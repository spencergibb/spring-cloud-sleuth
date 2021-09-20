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

package org.springframework.cloud.sleuth.autoconfig.brave.instrument.messaging;

import brave.handler.SpanHandler;
import brave.messaging.MessagingRequest;
import brave.messaging.MessagingRuleSampler;
import brave.sampler.Matchers;
import brave.sampler.RateLimitingSampler;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;
import brave.test.TestSpanHandler;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayMetricsAutoConfiguration;
import org.springframework.cloud.sleuth.brave.instrument.messaging.ConsumerSampler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static brave.messaging.MessagingRequestMatchers.channelNameEquals;
import static org.assertj.core.api.BDDAssertions.then;

@SpringBootTest(classes = BraveMessagingAutoConfigurationIntegrationTests.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.sleuth.tracer.mode=BRAVE")
public class BraveMessagingAutoConfigurationIntegrationTests {

	@Autowired
	@ConsumerSampler
	SamplerFunction<MessagingRequest> sampler;

	@Test
	public void should_inject_messaging_sampler() {
		then(this.sampler).isNotNull();
	}

	@EnableAutoConfiguration(
			exclude = { GatewayClassPathWarningAutoConfiguration.class, GatewayAutoConfiguration.class,
					GatewayMetricsAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class,
					MongoAutoConfiguration.class, QuartzAutoConfiguration.class, R2dbcAutoConfiguration.class,
					CassandraAutoConfiguration.class },
			excludeName = "org.springframework.cloud.gateway.config.GatewayRedisAutoConfiguration")
	@Configuration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		SpanHandler testSpanHandler() {
			return new TestSpanHandler();
		}

		// tag::custom_messaging_consumer_sampler[]
		@Bean(name = ConsumerSampler.NAME)
		SamplerFunction<MessagingRequest> myMessagingSampler() {
			return MessagingRuleSampler.newBuilder().putRule(channelNameEquals("alerts"), Sampler.NEVER_SAMPLE)
					.putRule(Matchers.alwaysMatch(), RateLimitingSampler.create(100)).build();
		}
		// end::custom_messaging_consumer_sampler[]

	}

}
