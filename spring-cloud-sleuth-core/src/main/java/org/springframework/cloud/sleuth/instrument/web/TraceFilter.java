/*
 * Copyright 2013-2016 the original author or authors.
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
package org.springframework.cloud.sleuth.instrument.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Span.SpanBuilder;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.NeverSampler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import static org.springframework.cloud.sleuth.instrument.web.ServletUtils.hasHeader;
import static org.springframework.util.StringUtils.hasText;

/**
 * Filter that takes the value of the {@link Span#SPAN_ID_NAME} and
 * {@link Span#TRACE_ID_NAME} header from either request or response and uses them to
 * create a new span.
 *
 * <p>
 * In order to keep the size of spans manageable, this only add tags defined in
 * {@link TraceKeys}. If you need to add additional tags, such as headers subtype this and
 * override {@link #addRequestTags} or {@link #addResponseTags}.
 *
 * @author Jakub Nabrdalik, 4financeIT
 * @author Tomasz Nurkiewicz, 4financeIT
 * @author Marcin Grzejszczak
 * @author Spencer Gibb
 * @author Dave Syer
 *
 * @see Tracer
 * @see TraceKeys
 * @see TraceWebAutoConfiguration#traceFilter
 *
 * @since 1.0.0
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class TraceFilter extends OncePerRequestFilter {

	private static final String HTTP_COMPONENT = "http";

	protected static final String TRACE_REQUEST_ATTR = TraceFilter.class.getName()
			+ ".TRACE";

	public static final String DEFAULT_SKIP_PATTERN =
			"/api-docs.*|/autoconfig|/configprops|/dump|/health|/info|/metrics.*|/mappings|/trace|/swagger.*|.*\\.png|.*\\.css|.*\\.js|.*\\.html|/favicon.ico|/hystrix.stream";

	private final Tracer tracer;
	private final TraceKeys traceKeys;
	private final Pattern skipPattern;
	private final SpanReporter spanReporter;

	private UrlPathHelper urlPathHelper = new UrlPathHelper();

	public TraceFilter(Tracer tracer, TraceKeys traceKeys, SpanReporter spanReporter) {
		this(tracer, traceKeys, Pattern.compile(DEFAULT_SKIP_PATTERN), spanReporter);
	}

	public TraceFilter(Tracer tracer, TraceKeys traceKeys, Pattern skipPattern,
			SpanReporter spanReporter) {
		this.tracer = tracer;
		this.traceKeys = traceKeys;
		this.skipPattern = skipPattern;
		this.spanReporter = spanReporter;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request,
			HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String uri = this.urlPathHelper.getPathWithinApplication(request);
		boolean skip = this.skipPattern.matcher(uri).matches()
				|| ServletUtils.getHeader(request, response, Span.NOT_SAMPLED_NAME) != null;
		Span spanFromRequest = (Span) request.getAttribute(TRACE_REQUEST_ATTR);
		if (spanFromRequest != null) {
			this.tracer.continueSpan(spanFromRequest);
		}
		else if (skip) {
			addToResponseIfNotPresent(response, Span.NOT_SAMPLED_NAME, "");
		}
		String name = HTTP_COMPONENT + ":" + uri;
		spanFromRequest = createSpan(request, response, skip, spanFromRequest, name);
		Throwable exception = null;
		try {
			addRequestTags(request);
			// Add headers before filter chain in case one of the filters flushes the
			// response...
			addResponseHeaders(response, spanFromRequest);
			filterChain.doFilter(request, response);
		}
		catch (Throwable e) {
			exception = e;
			throw e;
		}
		finally {
			if (isAsyncStarted(request) || request.isAsyncStarted()) {
				this.tracer.detach(spanFromRequest);
				// TODO: how to deal with response annotations and async?
				return;
			}
			if (skip) {
				addToResponseIfNotPresent(response, Span.NOT_SAMPLED_NAME, "");
			}
			if (spanFromRequest != null) {
				addResponseTags(response, exception);
				if (spanFromRequest.hasSavedSpan()) {
					Span parent =  spanFromRequest.getSavedSpan();
					if (parent != null && parent.isRemote()) {
						parent.logEvent(Span.SERVER_SEND);
						this.spanReporter.report(parent);
					}
				}
				// Double close to clean up the parent (remote span as well)
				this.tracer.close(spanFromRequest);
			}
		}
	}

	/**
	 * Creates a span and appends it as the current request's attribute
	 */
	private Span createSpan(HttpServletRequest request, HttpServletResponse response,
			boolean skip, Span spanFromRequest, String name) {
		if (spanFromRequest == null) {
			if (hasHeader(request, response, Span.TRACE_ID_NAME)) {
				SpanBuilder spanBuilder = this.tracer
						.join(new HttpServletDataHolder(request, response));
				Span parent = spanBuilder.build();
				spanFromRequest = this.tracer.createSpan(name, parent);
				if (parent != null && parent.isRemote()) {
					parent.logEvent(Span.SERVER_RECV);
				}
				request.setAttribute(TRACE_REQUEST_ATTR, spanFromRequest);
			}
			else {
				if (skip) {
					spanFromRequest = this.tracer.createSpan(name, NeverSampler.INSTANCE);
				}
				else {
					spanFromRequest = this.tracer.createSpan(name);
				}
				request.setAttribute(TRACE_REQUEST_ATTR, spanFromRequest);
			}
		}
		return spanFromRequest;
	}

	private void addResponseHeaders(HttpServletResponse response, Span span) {
		if (span != null) {
			if (!response.containsHeader(Span.SPAN_ID_NAME)) {
				response.addHeader(Span.SPAN_ID_NAME, Span.idToHex(span.getSpanId()));
				response.addHeader(Span.TRACE_ID_NAME, Span.idToHex(span.getTraceId()));
			}
		}
	}

	/** Override to add annotations not defined in {@link TraceKeys}. */
	protected void addRequestTags(HttpServletRequest request) {
		String uri = this.urlPathHelper.getPathWithinApplication(request);
		this.tracer.addTag(this.traceKeys.getHttp().getUrl(), getFullUrl(request));
		this.tracer.addTag(this.traceKeys.getHttp().getHost(), request.getServerName());
		this.tracer.addTag(this.traceKeys.getHttp().getPath(), uri);
		this.tracer.addTag(this.traceKeys.getHttp().getMethod(), request.getMethod());
		for (String name : this.traceKeys.getHttp().getHeaders()) {
			Enumeration<String> values = request.getHeaders(name);
			if (values.hasMoreElements()) {
				String key = this.traceKeys.getHttp().getPrefix() + name.toLowerCase();
				ArrayList<String> list = Collections.list(values);
				String value = list.size() == 1 ? list.get(0)
						: StringUtils.collectionToDelimitedString(list, ",", "'", "'");
				this.tracer.addTag(key, value);
			}
		}
	}

	/** Override to add annotations not defined in {@link TraceKeys}. */
	protected void addResponseTags(HttpServletResponse response, Throwable e) {
		int httpStatus = response.getStatus();
		if (httpStatus == HttpServletResponse.SC_OK && e != null) {
			// Filter chain threw exception but the response status may not have been set
			// yet, so we have to guess.
			this.tracer.addTag(this.traceKeys.getHttp().getStatusCode(),
					String.valueOf(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
		}
		else if ((httpStatus < 200) || (httpStatus > 299)) {
			this.tracer.addTag(this.traceKeys.getHttp().getStatusCode(),
					String.valueOf(response.getStatus()));
		}
	}

	private void addToResponseIfNotPresent(HttpServletResponse response, String name,
			String value) {
		if (!hasText(response.getHeader(name))) {
			response.addHeader(name, value);
		}
	}

	@Override
	protected boolean shouldNotFilterAsyncDispatch() {
		return false;
	}

	private String getFullUrl(HttpServletRequest request) {
		StringBuffer requestURI = request.getRequestURL();
		String queryString = request.getQueryString();
		if (queryString == null) {
			return requestURI.toString();
		}
		else {
			return requestURI.append('?').append(queryString).toString();
		}
	}
}
