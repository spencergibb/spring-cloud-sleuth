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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;
import org.springframework.cloud.sleuth.autoconfig.otel.OtelAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.messaging.TracingChannelInterceptor;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.DelegatingWebSocketMessageBrokerConfiguration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} that enables tracing for WebSockets.
 *
 * @author Dave Syer
 * @since 1.0.0
 * @see AbstractWebSocketMessageBrokerConfigurer
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(DelegatingWebSocketMessageBrokerConfiguration.class)
@ConditionalOnBean(Tracer.class)
@ConditionalOnProperty(value = "spring.sleuth.integration.websockets.enabled", matchIfMissing = true)
@AutoConfigureAfter({ BraveAutoConfiguration.class, OtelAutoConfiguration.class })
class TraceWebSocketAutoConfiguration extends AbstractWebSocketMessageBrokerConfigurer {

	@Autowired
	Tracer tracer;

	@Autowired
	Propagator propagator;

	@Autowired
	Propagator.Setter<MessageHeaderAccessor> traceMessagePropagationSetter;

	@Autowired
	Propagator.Getter<MessageHeaderAccessor> traceMessagePropagationGetter;

	@Autowired
	SleuthMessagingProperties sleuthMessagingProperties;

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// The user must register their own endpoints
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.configureBrokerChannel().setInterceptors(new TracingChannelInterceptor(this.tracer, this.propagator,
				this.traceMessagePropagationSetter, this.traceMessagePropagationGetter,
				TraceSpringIntegrationAutoConfiguration.remoteServiceNameMapper(this.sleuthMessagingProperties)));
	}

	@Override
	public void configureClientOutboundChannel(ChannelRegistration registration) {
		registration.setInterceptors(new TracingChannelInterceptor(this.tracer, this.propagator,
				this.traceMessagePropagationSetter, this.traceMessagePropagationGetter,
				TraceSpringIntegrationAutoConfiguration.remoteServiceNameMapper(this.sleuthMessagingProperties)));
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.setInterceptors(new TracingChannelInterceptor(this.tracer, this.propagator,
				this.traceMessagePropagationSetter, this.traceMessagePropagationGetter,
				TraceSpringIntegrationAutoConfiguration.remoteServiceNameMapper(this.sleuthMessagingProperties)));
	}

}
