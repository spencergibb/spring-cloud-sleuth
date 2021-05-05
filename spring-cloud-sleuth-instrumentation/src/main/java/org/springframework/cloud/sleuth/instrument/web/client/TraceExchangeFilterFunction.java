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

package org.springframework.cloud.sleuth.instrument.web.client;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Scannable;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;
import reactor.util.context.Context;

import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.http.HttpClientHandler;
import org.springframework.cloud.sleuth.http.HttpClientRequest;
import org.springframework.cloud.sleuth.http.HttpClientResponse;
import org.springframework.cloud.sleuth.instrument.reactor.TraceContextPropagator;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Trace representation of {@link ExchangeFilterFunction}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.2
 */
public final class TraceExchangeFilterFunction implements ExchangeFilterFunction {

	private static final Log log = LogFactory.getLog(TraceExchangeFilterFunction.class);

	private static final String URI_TEMPLATE_ATTRIBUTE = WebClient.class.getName() + ".uriTemplate";

	final ConfigurableApplicationContext springContext;

	// Lazy initialized fields
	HttpClientHandler handler;

	CurrentTraceContext currentTraceContext;

	TraceExchangeFilterFunction(ConfigurableApplicationContext springContext) {
		this.springContext = springContext;
	}

	public static ExchangeFilterFunction create(ConfigurableApplicationContext springContext) {
		return new TraceExchangeFilterFunction(springContext);
	}

	@Override
	public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
		return new MonoWebClientTrace(next, request, this);
	}

	CurrentTraceContext currentTraceContext() {
		if (this.currentTraceContext == null) {
			this.currentTraceContext = this.springContext.getBean(CurrentTraceContext.class);
		}
		return this.currentTraceContext;
	}

	HttpClientHandler handler() {
		if (this.handler == null) {
			this.handler = this.springContext.getBean(HttpClientHandler.class);
		}
		return this.handler;
	}

	private static final class MonoWebClientTrace extends Mono<ClientResponse>
			implements Scannable, TraceContextPropagator {

		final ExchangeFunction next;

		final ClientRequest request;

		final HttpClientHandler handler;

		final CurrentTraceContext currentTraceContext;

		MonoWebClientTrace(ExchangeFunction next, ClientRequest request, TraceExchangeFilterFunction filterFunction) {
			this.next = next;
			this.request = request;
			this.handler = filterFunction.handler();
			this.currentTraceContext = filterFunction.currentTraceContext();
		}

		@Override
		public void subscribe(CoreSubscriber<? super ClientResponse> subscriber) {
			Context context = subscriber.currentContext();
			if (log.isTraceEnabled()) {
				log.trace("Got the following context [" + context + "]");
			}
			ClientRequestWrapper wrapper = new ClientRequestWrapper(this.request);
			TraceContext parent = context.hasKey(TraceContext.class) ? context.get(TraceContext.class) : null;
			if (parent == null) {
				parent = this.currentTraceContext.context();
			}
			Span span = handler.handleSend(wrapper, parent);
			if (log.isTraceEnabled()) {
				log.trace("HttpClientHandler::handleSend: " + span);
			}
			// NOTE: We are starting the client span for the request here, but it could be
			// canceled prior to actually being invoked. TraceWebClientSubscription will
			// abandon this span, if cancel() happens before request().
			this.next.exchange(wrapper.buildRequest())
					.subscribe(new TraceWebClientSubscriber(subscriber, context, span, parent, this));
		}

		@Nullable
		@Override
		public Object scanUnsafe(Attr key) {
			if (key == Attr.RUN_STYLE) {
				return Attr.RunStyle.SYNC;
			}
			return null;
		}

	}

	/**
	 * Subscriber for WebClient.
	 */
	static final class TraceWebClientSubscriber extends AtomicReference<Span>
			implements CoreSubscriber<ClientResponse>, Scannable {

		final CoreSubscriber<? super ClientResponse> actual;

		final Context context;

		@Nullable
		final TraceContext parent;

		final HttpClientHandler handler;

		final CurrentTraceContext currentTraceContext;

		final HttpMethod method;

		final String httpRoute;

		TraceWebClientSubscriber(CoreSubscriber<? super ClientResponse> actual, Context ctx, Span clientSpan,
				TraceContext parent, MonoWebClientTrace mono) {
			this.actual = actual;
			this.parent = parent;
			this.handler = mono.handler;
			this.currentTraceContext = mono.currentTraceContext;
			this.method = mono.request.method();
			this.httpRoute = (String) mono.request.attribute(URI_TEMPLATE_ATTRIBUTE).orElse(null);
			this.context = this.parent != null && !this.parent.equals(ctx.getOrDefault(TraceContext.class, null))
					? ctx.put(TraceContext.class, this.parent) : ctx;
			set(clientSpan);
		}

		@Override
		public void onSubscribe(Subscription subscription) {
			this.actual.onSubscribe(new TraceWebClientSubscription(subscription, this));
		}

		@Override
		public void onNext(ClientResponse response) {
			try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(parent)) {
				if (log.isTraceEnabled()) {
					log.trace("OnNext");
				}
				// decorate response body
				this.actual.onNext(response);
			}
			finally {
				Span span = getAndSet(null);
				if (span != null) {
					if (log.isTraceEnabled()) {
						log.trace("OnNext finally");
					}
					this.handler.handleReceive(new ClientResponseWrapper(response, this.method, this.httpRoute), span);
				}
			}
		}

		@Override
		public void onError(Throwable t) {
			try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(parent)) {
				if (log.isTraceEnabled()) {
					log.trace("OnError");
				}
				this.actual.onError(t);
			}
			finally {
				Span span = getAndSet(null);
				if (span != null) {
					if (log.isTraceEnabled()) {
						log.trace("OnError finally");
					}
					span.error(t);
					span.end();
				}
			}
		}

		@Override
		public void onComplete() {
			try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(parent)) {
				if (log.isTraceEnabled()) {
					log.trace("OnComplete");
				}
				this.actual.onComplete();
			}
			finally {
				Span span = getAndSet(null);
				if (span != null) {
					// TODO: backfill empty test:
					// https://github.com/spring-cloud/spring-cloud-sleuth/issues/1570
					if (log.isTraceEnabled()) {
						log.trace("Reached OnComplete without finishing [" + span + "]");
					}
					span.abandon();
				}
			}
		}

		@Override
		public Context currentContext() {
			return this.context;
		}

		@Override
		public Object scanUnsafe(Attr key) {
			if (key == Attr.RUN_STYLE) {
				return Attr.RunStyle.SYNC;
			}
			return null;
		}

	}

	static class TraceWebClientSubscription implements Subscription {

		static final Exception CANCELLED_ERROR = new CancellationException("CANCELLED") {
			@Override
			public Throwable fillInStackTrace() {
				return this; // stack trace doesn't add value here
			}
		};

		final AtomicReference<Span> pendingSpan;

		final Subscription delegate;

		volatile boolean requested;

		TraceWebClientSubscription(Subscription delegate, AtomicReference<Span> pendingSpan) {
			this.delegate = delegate;
			this.pendingSpan = pendingSpan;
		}

		@Override
		public void request(long n) {
			requested = true;
			delegate.request(n); // Not scoping to save overhead
		}

		@Override
		public void cancel() {
			delegate.cancel(); // Not scoping to save overhead

			// Check to see if Subscription.cancel() happened after request(),
			// but before another signal (like onComplete) completed the span.
			Span span = pendingSpan.getAndSet(null);
			if (span != null) {
				if (log.isTraceEnabled()) {
					log.trace("Subscription was cancelled. TraceWebClientBeanPostProcessor Will close the span [" + span
							+ "]");
				}

				if (!requested) { // Abandon the span.
					span.abandon();
				}
				else { // Request was canceled in-flight
					span.error(CANCELLED_ERROR);
					span.end();
				}
			}
		}

	}

	private static final class ClientRequestWrapper implements HttpClientRequest {

		final ClientRequest delegate;

		final ClientRequest.Builder builder;

		ClientRequestWrapper(ClientRequest delegate) {
			this.delegate = delegate;
			this.builder = ClientRequest.from(delegate);
		}

		@Override
		public Collection<String> headerNames() {
			return this.delegate.headers().keySet();
		}

		@Override
		public Object unwrap() {
			return delegate;
		}

		@Override
		public String method() {
			return delegate.method().name();
		}

		@Override
		public String path() {
			return delegate.url().getPath();
		}

		@Override
		public String route() {
			return (String) delegate.attribute(URI_TEMPLATE_ATTRIBUTE).orElse(null);
		}

		@Override
		public String url() {
			return delegate.url().toString();
		}

		@Override
		public String header(String name) {
			return delegate.headers().getFirst(name);
		}

		@Override
		public void header(String name, String value) {
			builder.header(name, value);
		}

		ClientRequest buildRequest() {
			return builder.build();
		}

	}

	static final class ClientResponseWrapper implements HttpClientResponse {

		final ClientResponse delegate;

		final HttpMethod method;

		final String httpRoute;

		ClientResponseWrapper(ClientResponse delegate, HttpMethod method, String httpRoute) {
			this.delegate = delegate;
			this.method = method;
			this.httpRoute = httpRoute;
		}

		@Override
		public String method() {
			return method.name();
		}

		@Override
		public String route() {
			return httpRoute;
		}

		@Override
		public Collection<String> headerNames() {
			return this.delegate.headers().asHttpHeaders().keySet();
		}

		@Override
		public Object unwrap() {
			return delegate;
		}

		@Override
		public int statusCode() {
			// unlike statusCode(), this doesn't throw
			return Math.max(delegate.rawStatusCode(), 0);
		}

		@Override
		public String header(String header) {
			List<String> headers = delegate.headers().header(header);
			if (headers.isEmpty()) {
				return null;
			}
			return headers.get(0);
		}

	}

}
