package org.springframework.cloud.sleuth.instrument.web;

import java.util.Map;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanTextMap;
import org.springframework.util.StringUtils;

/**
 * Default implementation of {@link HttpSpanInjector}, compatible with Zipkin propagation.
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
public class ZipkinHttpSpanInjector implements HttpSpanInjector {

	private static final String HEADER_DELIMITER = "-";

	@Override
	public void inject(Span span, SpanTextMap carrier) {
		setHeader(carrier, Span.TRACE_ID_NAME, span.traceIdString());
		setIdHeader(carrier, Span.SPAN_ID_NAME, span.getSpanId());
		setHeader(carrier, Span.SAMPLED_NAME, span.isExportable() ? Span.SPAN_SAMPLED : Span.SPAN_NOT_SAMPLED);
		setHeader(carrier, Span.SPAN_NAME_NAME, span.getName());
		setIdHeader(carrier, Span.PARENT_ID_NAME, getParentId(span));
		setHeader(carrier, Span.PROCESS_ID_NAME, span.getProcessId());
		for (Map.Entry<String, String> entry : span.baggageItems()) {
			carrier.put(prefixedKey(entry.getKey()), entry.getValue());
		}
	}

	private String prefixedKey(String key) {
		if (key.startsWith(Span.SPAN_BAGGAGE_HEADER_PREFIX + HEADER_DELIMITER)) {
			return key;
		}
		return Span.SPAN_BAGGAGE_HEADER_PREFIX + HEADER_DELIMITER + key;
	}

	private Long getParentId(Span span) {
		return !span.getParents().isEmpty() ? span.getParents().get(0) : null;
	}

	private void setIdHeader(SpanTextMap carrier, String name, Long value) {
		if (value != null) {
			setHeader(carrier, name, Span.idToHex(value));
		}
	}

	private void setHeader(SpanTextMap carrier, String name, String value) {
		if (StringUtils.hasText(value)) {
			carrier.put(name, value);
		}
	}

}
