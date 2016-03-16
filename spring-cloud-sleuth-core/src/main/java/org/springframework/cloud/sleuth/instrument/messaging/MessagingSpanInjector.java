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

package org.springframework.cloud.sleuth.instrument.messaging;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;
import org.springframework.util.StringUtils;

/**
 * Creates a {@link Span.SpanBuilder} from {@link Message}
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
public class MessagingSpanInjector implements SpanInjector<MessageBuilder> {

	public static final String SPAN_HEADER = "X-Current-Span";

	private final TraceKeys traceKeys;

	public MessagingSpanInjector(TraceKeys traceKeys) {
		this.traceKeys = traceKeys;
	}

	@Override
	public void inject(Span span, MessageBuilder carrier) {
		Message initialMessage = carrier.build();
		MessageHeaderAccessor accessor = MessageHeaderAccessor
				.getMutableAccessor(initialMessage);
		if (span == null) {
			if (!Span.SPAN_SAMPLED.equals(initialMessage.getHeaders().get(Span.SAMPLED_NAME))) {
				accessor.setHeader(Span.SAMPLED_NAME, Span.SPAN_NOT_SAMPLED);
				carrier.setHeaders(accessor);
				return;
			}
			return;
		}
		Map<String, String> headers = new HashMap<>();
		addHeader(headers, Span.TRACE_ID_NAME, Span.idToHex(span.getTraceId()));
		addHeader(headers, Span.SPAN_ID_NAME, Span.idToHex(span.getSpanId()));
		if (span.isExportable()) {
			addAnnotations(this.traceKeys, initialMessage, span);
			Long parentId = getFirst(span.getParents());
			if (parentId != null) {
				addHeader(headers, Span.PARENT_ID_NAME, Span.idToHex(parentId));
			}
			addHeader(headers, Span.SPAN_NAME_NAME, span.getName());
			addHeader(headers, Span.PROCESS_ID_NAME, span.getProcessId());
			addHeader(headers, Span.SAMPLED_NAME, Span.SPAN_SAMPLED);
		}
		else {
			addHeader(headers, Span.SAMPLED_NAME, Span.SPAN_NOT_SAMPLED);
		}
		accessor.setHeader(SPAN_HEADER, span);
		accessor.copyHeaders(headers);
		if (accessor instanceof NativeMessageHeaderAccessor) {
			NativeMessageHeaderAccessor nativeAccessor = (NativeMessageHeaderAccessor) accessor;
			for (String name : headers.keySet()) {
				nativeAccessor.setNativeHeader(name, headers.get(name));
			}
		}
		carrier.setHeaders(accessor);
	}

	private void addAnnotations(TraceKeys traceKeys, Message<?> message,
			Span span) {
		for (String name : traceKeys.getMessage().getHeaders()) {
			if (message.getHeaders().containsKey(name)) {
				String key = traceKeys.getMessage().getPrefix() + name.toLowerCase();
				Object value = message.getHeaders().get(name);
				if (value == null) {
					value = "null";
				}
				tagIfEntryMissing(span, key, value.toString()); // TODO: better way to serialize?
			}
		}
		addPayloadAnnotations(traceKeys, message.getPayload(), span);
	}

	private void addPayloadAnnotations(TraceKeys traceKeys, Object payload, Span span) {
		if (payload != null) {
			tagIfEntryMissing(span, traceKeys.getMessage().getPayload().getType(),
					payload.getClass().getCanonicalName());
			if (payload instanceof String) {
				tagIfEntryMissing(span, traceKeys.getMessage().getPayload().getSize(),
						String.valueOf(((String) payload).length()));
			}
			else if (payload instanceof byte[]) {
				tagIfEntryMissing(span, traceKeys.getMessage().getPayload().getSize(),
						String.valueOf(((byte[]) payload).length));
			}
		}
	}

	private void tagIfEntryMissing(Span span, String key, String value) {
		if (!span.tags().containsKey(key)) {
			span.tag(key, value);
		}
	}

	private void addHeader(Map<String, String> headers, String name,
			String value) {
		if (StringUtils.hasText(value)) {
			headers.put(name, value);
		}
	}

	private Long getFirst(List<Long> parents) {
		return parents.isEmpty() ? null : parents.get(0);
	}
}
