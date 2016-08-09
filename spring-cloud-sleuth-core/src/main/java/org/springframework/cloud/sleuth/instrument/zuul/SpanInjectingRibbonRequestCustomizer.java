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

package org.springframework.cloud.sleuth.instrument.zuul;

import java.lang.invoke.MethodHandles;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.netflix.ribbon.support.RibbonRequestCustomizer;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.TraceHeaders;
import org.springframework.cloud.sleuth.Tracer;

/**
 * Abstraction over customization of Ribbon Requests. All clients will inject the span
 * into their respective context. The only difference is how those contexts set the headers.
 * In order to add a new implementation of the {@link RibbonRequestCustomizer} it's
 * necessary only to provide the {@link RibbonRequestCustomizer#accepts(Class)} method
 * with the context class name and {@link SpanInjectingRibbonRequestCustomizer#setHeader(Object, String, String)}
 * to tell Sleuth how to set a header using the particular library.
 *
 * @author Marcin Grzejszczak
 * @since 1.1.0
 */
abstract class SpanInjectingRibbonRequestCustomizer<T> implements RibbonRequestCustomizer<T>,
		SpanInjector<T> {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private final Tracer tracer;
	private final TraceHeaders traceHeaders;

	SpanInjectingRibbonRequestCustomizer(Tracer tracer, TraceHeaders traceHeaders) {
		this.tracer = tracer;
		this.traceHeaders = traceHeaders;
	}

	@Override
	public void customize(T context) {
		Span span = getCurrentSpan();
		inject(span, context);
		span.logEvent(Span.CLIENT_SEND);
		if (log.isDebugEnabled()) {
			log.debug("Span in the RibbonRequestCustomizer is" + span);
		}
	}

	@Override
	public void inject(Span span, T carrier) {
		if (span == null) {
			setHeader(carrier, this.traceHeaders.getSampled(), Span.SPAN_NOT_SAMPLED);
			return;
		}
		setHeader(carrier, this.traceHeaders.getSampled(), span.isExportable() ?
				Span.SPAN_SAMPLED : Span.SPAN_NOT_SAMPLED);
		setHeader(carrier, this.traceHeaders.getTraceId(), Span.idToHex(span.getTraceId()));
		setHeader(carrier, this.traceHeaders.getSpanId(), Span.idToHex(span.getSpanId()));
		setHeader(carrier, Span.SPAN_NAME_NAME, span.getName());
		if (getParentId(span) != null) {
			setHeader(carrier, this.traceHeaders.getParentId(),
					Span.idToHex(getParentId(span)));
		}
		setHeader(carrier, Span.PROCESS_ID_NAME,
				span.getProcessId());
	}

	private Long getParentId(Span span) {
		return !span.getParents().isEmpty()
				? span.getParents().get(0) : null;
	}

	private Span getCurrentSpan() {
		return this.tracer.getCurrentSpan();
	}

	abstract void setHeader(T builder, String name, String value);
}
