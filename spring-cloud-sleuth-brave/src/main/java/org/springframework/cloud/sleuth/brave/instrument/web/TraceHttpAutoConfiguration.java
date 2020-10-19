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

package org.springframework.cloud.sleuth.brave.instrument.web;

import java.util.List;
import java.util.regex.Pattern;

import brave.Tracing;
import brave.http.HttpRequest;
import brave.http.HttpTracing;
import brave.http.HttpTracingCustomizer;
import brave.sampler.SamplerFunction;
import brave.sampler.SamplerFunctions;
import org.jetbrains.annotations.NotNull;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.api.http.HttpRequestParser;
import org.springframework.cloud.sleuth.api.http.HttpResponseParser;
import org.springframework.cloud.sleuth.brave.autoconfig.TraceBraveAutoConfiguration;
import org.springframework.cloud.sleuth.brave.bridge.BraveSamplerFunction;
import org.springframework.cloud.sleuth.brave.bridge.http.BraveHttpRequestParser;
import org.springframework.cloud.sleuth.brave.bridge.http.BraveHttpResponseParser;
import org.springframework.cloud.sleuth.brave.bridge.http.TraceBraveHttpBridgeAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.HttpClientRequestParser;
import org.springframework.cloud.sleuth.instrument.web.HttpClientResponseParser;
import org.springframework.cloud.sleuth.instrument.web.HttpClientSampler;
import org.springframework.cloud.sleuth.instrument.web.HttpServerRequestParser;
import org.springframework.cloud.sleuth.instrument.web.HttpServerResponseParser;
import org.springframework.cloud.sleuth.instrument.web.HttpServerSampler;
import org.springframework.cloud.sleuth.instrument.web.SkipPatternConfiguration;
import org.springframework.cloud.sleuth.instrument.web.SkipPatternProvider;
import org.springframework.cloud.sleuth.instrument.web.SleuthHttpProperties;
import org.springframework.cloud.sleuth.instrument.web.SleuthWebProperties;
import org.springframework.cloud.sleuth.instrument.web.client.feign.TraceFeignClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} related to HTTP based communication.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = { "spring.sleuth.http.enabled", "spring.sleuth.web.enabled" }, havingValue = "true",
		matchIfMissing = true)
@ConditionalOnBean(Tracing.class)
@ConditionalOnClass(HttpTracing.class)
@AutoConfigureAfter({ TraceBraveAutoConfiguration.class, SkipPatternConfiguration.class })
@EnableConfigurationProperties({ SleuthWebProperties.class, SleuthHttpProperties.class })
// public allows @AutoConfigureAfter(TraceHttpAutoConfiguration)
// for components needing HttpTracing
@AutoConfigureBefore({ TraceFeignClientAutoConfiguration.class, TraceBraveHttpBridgeAutoConfiguration.class })
public class TraceHttpAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	// NOTE: stable bean name as might be used outside sleuth
	HttpTracing httpTracing(Tracing tracing, @Nullable SkipPatternProvider provider,
			@Nullable brave.http.HttpClientParser clientParser, @Nullable brave.http.HttpServerParser serverParser,
			BeanFactory beanFactory, @Nullable List<HttpTracingCustomizer> httpTracingCustomizers) {
		HttpTracing.Builder builder = httpTracingBuilder(tracing, provider, beanFactory);
		brave.http.HttpRequestParser httpClientRequestParser = httpRequestParser(beanFactory,
				HttpClientRequestParser.NAME);
		brave.http.HttpResponseParser httpClientResponseParser = httpResponseParser(beanFactory,
				HttpClientResponseParser.NAME);
		brave.http.HttpRequestParser httpServerRequestParser = httpRequestParser(beanFactory,
				HttpServerRequestParser.NAME);
		brave.http.HttpResponseParser httpServerResponseParser = httpResponseParser(beanFactory,
				HttpServerResponseParser.NAME);

		if (httpClientRequestParser != null || httpClientResponseParser != null) {
			if (httpClientRequestParser != null) {
				builder.clientRequestParser(httpClientRequestParser);
			}
			if (httpClientResponseParser != null) {
				builder.clientResponseParser(httpClientResponseParser);
			}
		}
		else if (clientParser != null) { // consider deprecated last
			builder.clientParser(clientParser);
		}

		if (httpServerRequestParser != null || httpServerResponseParser != null) {
			if (httpServerRequestParser != null) {
				builder.serverRequestParser(httpServerRequestParser);
			}
			if (httpServerResponseParser != null) {
				builder.serverResponseParser(httpServerResponseParser);
			}
		}
		else if (serverParser != null) { // consider deprecated last
			builder.serverParser(serverParser);
		}

		if (httpTracingCustomizers != null) {
			for (HttpTracingCustomizer customizer : httpTracingCustomizers) {
				customizer.customize(builder);
			}
		}
		return builder.build();
	}

	private brave.http.HttpRequestParser httpRequestParser(BeanFactory beanFactory, String name) {
		return beanFactory.containsBean(name) ? toBraveHttpRequestParser(beanFactory, name) : null;
	}

	private brave.http.HttpResponseParser httpResponseParser(BeanFactory beanFactory, String name) {
		return beanFactory.containsBean(name) ? toBraveHttpResponseParser(beanFactory, name) : null;
	}

	@NotNull
	private HttpTracing.Builder httpTracingBuilder(Tracing tracing, @Nullable SkipPatternProvider provider,
			BeanFactory beanFactory) {
		SamplerFunction<HttpRequest> httpClientSampler = toBraveSampler(beanFactory, HttpClientSampler.NAME);
		SamplerFunction<HttpRequest> httpServerSampler = httpServerSampler(beanFactory);
		SamplerFunction<HttpRequest> combinedSampler = combineUserProvidedSamplerWithSkipPatternSampler(
				httpServerSampler, provider);
		return HttpTracing.newBuilder(tracing).clientSampler(httpClientSampler).serverSampler(combinedSampler);
	}

	@org.jetbrains.annotations.Nullable
	private SamplerFunction<HttpRequest> httpServerSampler(BeanFactory beanFactory) {
		return beanFactory.containsBean(HttpServerSampler.NAME) ? toBraveSampler(beanFactory, HttpServerSampler.NAME)
				: null;
	}

	private brave.http.HttpRequestParser toBraveHttpRequestParser(BeanFactory beanFactory, String beanName) {
		Object bean = beanFactory.getBean(beanName);
		brave.http.HttpRequestParser parser = bean instanceof brave.http.HttpRequestParser
				? (brave.http.HttpRequestParser) bean
				: bean instanceof HttpRequestParser ? BraveHttpRequestParser.toBrave((HttpRequestParser) bean) : null;
		return returnOrThrow(bean, parser, beanName, brave.http.HttpRequestParser.class, HttpRequestParser.class);
	}

	private brave.http.HttpResponseParser toBraveHttpResponseParser(BeanFactory beanFactory, String beanName) {
		Object bean = beanFactory.getBean(beanName);
		brave.http.HttpResponseParser parser = bean instanceof brave.http.HttpResponseParser
				? (brave.http.HttpResponseParser) bean : bean instanceof HttpResponseParser
						? BraveHttpResponseParser.toBrave((HttpResponseParser) bean) : null;
		return returnOrThrow(bean, parser, beanName, brave.http.HttpResponseParser.class, HttpResponseParser.class);
	}

	private SamplerFunction<brave.http.HttpRequest> toBraveSampler(BeanFactory beanFactory, String beanName) {
		Object bean = beanFactory.getBean(beanName);
		SamplerFunction<brave.http.HttpRequest> braveSampler = bean instanceof SamplerFunction
				? (SamplerFunction<brave.http.HttpRequest>) bean
				: bean instanceof org.springframework.cloud.sleuth.api.SamplerFunction
						? BraveSamplerFunction.toHttpBrave(
								(org.springframework.cloud.sleuth.api.SamplerFunction<org.springframework.cloud.sleuth.api.http.HttpRequest>) bean)
						: null;
		return returnOrThrow(bean, braveSampler, beanName, SamplerFunction.class,
				org.springframework.cloud.sleuth.api.SamplerFunction.class);
	}

	@NotNull
	private <T> T returnOrThrow(Object bean, T convertedBean, String name, Class brave, Class sleuth) {
		if (convertedBean == null) {
			throw new IllegalStateException(
					"Bean with name [" + name + "] is of type [" + bean.getClass() + "] and only ["
							+ brave.getCanonicalName() + "] and [" + sleuth.getCanonicalName() + "] are supported");
		}
		return convertedBean;
	}

	private SamplerFunction<brave.http.HttpRequest> combineUserProvidedSamplerWithSkipPatternSampler(
			@Nullable SamplerFunction<brave.http.HttpRequest> serverSampler, @Nullable SkipPatternProvider provider) {
		SamplerFunction<brave.http.HttpRequest> skipPatternSampler = provider != null
				? new SkipPatternHttpServerSampler(provider) : null;
		if (serverSampler == null && skipPatternSampler == null) {
			return SamplerFunctions.deferDecision();
		}
		else if (serverSampler == null) {
			return skipPatternSampler;
		}
		else if (skipPatternSampler == null) {
			return serverSampler;
		}
		return new CompositeHttpSampler(skipPatternSampler, serverSampler);
	}

	@Bean
	@ConditionalOnMissingBean(name = HttpClientSampler.NAME)
	SamplerFunction<brave.http.HttpRequest> sleuthHttpClientSampler(SleuthWebProperties sleuthWebProperties) {
		String skipPattern = sleuthWebProperties.getClient().getSkipPattern();
		if (skipPattern == null) {
			return SamplerFunctions.deferDecision();
		}

		return new SkipPatternHttpClientSampler(Pattern.compile(skipPattern));
	}

}

/**
 * Composite Http Sampler.
 *
 * @author Adrian Cole
 */
final class CompositeHttpSampler implements SamplerFunction<brave.http.HttpRequest> {

	final SamplerFunction<brave.http.HttpRequest> left;

	final SamplerFunction<brave.http.HttpRequest> right;

	CompositeHttpSampler(SamplerFunction<brave.http.HttpRequest> left, SamplerFunction<brave.http.HttpRequest> right) {
		this.left = left;
		this.right = right;
	}

	@Override
	public Boolean trySample(brave.http.HttpRequest request) {
		// If either decision is false, return false
		Boolean leftDecision = this.left.trySample(request);
		if (Boolean.FALSE.equals(leftDecision)) {
			return false;
		}
		Boolean rightDecision = this.right.trySample(request);
		if (Boolean.FALSE.equals(rightDecision)) {
			return false;
		}
		// If either decision is null, return the other
		if (leftDecision == null) {
			return rightDecision;
		}
		if (rightDecision == null) {
			return leftDecision;
		}
		// Neither are null and at least one is true
		return rightDecision;
	}

}

/**
 * Http Sampler that looks at paths.
 *
 * @author Marcin Grzejszczak
 */
final class SkipPatternHttpServerSampler extends SkipPatternSampler {

	private final SkipPatternProvider provider;

	SkipPatternHttpServerSampler(SkipPatternProvider provider) {
		this.provider = provider;
	}

	@Override
	Pattern getPattern() {
		return this.provider.skipPattern();
	}

}

final class SkipPatternHttpClientSampler extends SkipPatternSampler {

	private final Pattern skipPattern;

	SkipPatternHttpClientSampler(Pattern skipPattern) {
		this.skipPattern = skipPattern;
	}

	@Override
	Pattern getPattern() {
		return skipPattern;
	}

}
