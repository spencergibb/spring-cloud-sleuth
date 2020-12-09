/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.autoconfig.instrument.messaging;

import java.util.function.Function;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;
import org.springframework.cloud.sleuth.autoconfig.otel.OtelAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.messaging.DefaultMessageSpanCustomizer;
import org.springframework.cloud.sleuth.instrument.messaging.MessageSpanCustomizer;
import org.springframework.cloud.sleuth.instrument.messaging.TracingChannelInterceptor;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.integration.channel.interceptor.GlobalChannelInterceptorWrapper;
import org.springframework.integration.config.GlobalChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} that registers a Sleuth version of the
 * {@link org.springframework.messaging.support.ChannelInterceptor}.
 *
 * @author Spencer Gibb
 * @since 1.0.0
 * @see TracingChannelInterceptor
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(GlobalChannelInterceptor.class)
@ConditionalOnBean(Tracer.class)
@AutoConfigureAfter({ BraveAutoConfiguration.class, OtelAutoConfiguration.class,
		TraceSpringMessagingAutoConfiguration.class })
@ConditionalOnProperty(value = "spring.sleuth.messaging.enabled", matchIfMissing = true)
@EnableConfigurationProperties(SleuthIntegrationMessagingProperties.class)
@Conditional(TracingChannelInterceptorCondition.class)
public class TraceSpringIntegrationAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	MessageSpanCustomizer defaultMessageSpanCustomizer() {
		return new DefaultMessageSpanCustomizer();
	}

	@Bean
	GlobalChannelInterceptorWrapper traceGlobalChannelInterceptorWrapper(TracingChannelInterceptor interceptor,
			SleuthIntegrationMessagingProperties properties) {
		GlobalChannelInterceptorWrapper wrapper = new GlobalChannelInterceptorWrapper(interceptor);
		wrapper.setPatterns(properties.getPatterns());
		return wrapper;
	}

	@Bean
	TracingChannelInterceptor traceChannelInterceptor(Tracer tracer, Propagator propagator,
			Propagator.Setter<MessageHeaderAccessor> traceMessagePropagationSetter,
			Propagator.Getter<MessageHeaderAccessor> traceMessagePropagationGetter,
			SleuthMessagingProperties properties, MessageSpanCustomizer messageSpanCustomizer) {
		return new TracingChannelInterceptor(tracer, propagator, traceMessagePropagationSetter,
				traceMessagePropagationGetter, remoteServiceNameMapper(properties), messageSpanCustomizer);
	}

	static Function<String, String> remoteServiceNameMapper(SleuthMessagingProperties properties) {
		return s -> {
			if (!StringUtils.hasText(s)) {
				return null;
			}
			if (s.startsWith("amqp") || s.startsWith("rabbit")) {
				return properties.getMessaging().getRabbit().getRemoteServiceName();
			}
			else if (s.startsWith("kafka")) {
				return properties.getMessaging().getKafka().getRemoteServiceName();
			}
			return null;
		};
	}

}

final class TracingChannelInterceptorCondition extends AnyNestedCondition {

	private TracingChannelInterceptorCondition() {
		super(ConfigurationPhase.REGISTER_BEAN);
	}

	@ConditionalOnMissingClass("org.springframework.cloud.function.context.FunctionCatalog")
	@ConditionalOnProperty(value = "spring.sleuth.integration.enabled", matchIfMissing = true)
	static class OnFunctionMissing {

	}

	@ConditionalOnClass(FunctionCatalog.class)
	@Conditional(OnEnableBindingCondition.class)
	@ConditionalOnProperty(value = "spring.sleuth.integration.enabled", matchIfMissing = true)
	static class OnFunctionPresentAndEnableBinding {

	}

	@ConditionalOnClass(FunctionCatalog.class)
	@Conditional(OnEnableBindingMissingCondition.class)
	@ConditionalOnProperty(value = "spring.sleuth.integration.enabled", havingValue = "true")
	static class OnFunctionPresentEnableBindingOffAndIntegrationExplicitlyOn {

	}

}

class OnEnableBindingCondition implements ConfigurationCondition {

	@Override
	public ConfigurationPhase getConfigurationPhase() {
		return ConfigurationPhase.PARSE_CONFIGURATION;
	}

	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		Class clazz;
		try {
			clazz = Class.forName("org.springframework.cloud.stream.annotation.EnableBinding");
		}
		catch (ClassNotFoundException e) {
			return false;
		}
		return !ObjectUtils.isEmpty(context.getBeanFactory().getBeanNamesForAnnotation(clazz));
	}

}

class OnEnableBindingMissingCondition extends OnEnableBindingCondition {

	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		return !super.matches(context, metadata);
	}

}
