package org.springframework.cloud.sleuth.instrument.messaging;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanExtractor;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.TraceHeaders;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.integration.context.IntegrationObjectSupport;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptorAdapter;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.ClassUtils;

/**
 * Abstraction over classes related to channel intercepting
 *
 * @author Marcin Grzejszczak
 */
abstract class AbstractTraceChannelInterceptor extends ChannelInterceptorAdapter
		implements ExecutorChannelInterceptor {

	/**
	 * If a span comes from messaging components then it will have this value as a prefix
	 * to its name.
	 * <p>
	 * Example of a Span name: {@code message:foo}
	 * <p>
	 * Where {@code message} is the prefix and {@code foo} is the channel name
	 */
	protected static final String MESSAGE_COMPONENT = "message";

	private final Tracer tracer;
	private final TraceKeys traceKeys;
	private final SpanExtractor<Message<?>> spanExtractor;
	private final SpanInjector<MessageBuilder<?>> spanInjector;
	private final TraceHeaders traceHeaders;
	private final TraceMessageHeaders traceMessageHeaders;

	protected AbstractTraceChannelInterceptor(Tracer tracer, TraceKeys traceKeys,
			SpanExtractor<Message<?>> spanExtractor,
			SpanInjector<MessageBuilder<?>> spanInjector, TraceHeaders traceHeaders,
			TraceMessageHeaders traceMessageHeaders) {
		this.tracer = tracer;
		this.traceKeys = traceKeys;
		this.spanExtractor = spanExtractor;
		this.spanInjector = spanInjector;
		this.traceHeaders = traceHeaders;
		this.traceMessageHeaders = traceMessageHeaders;
	}

	protected Tracer getTracer() {
		return this.tracer;
	}

	protected TraceKeys getTraceKeys() {
		return this.traceKeys;
	}

	protected SpanInjector<MessageBuilder<?>> getSpanInjector() {
		return this.spanInjector;
	}

	protected TraceHeaders getTraceHeaders() {
		return this.traceHeaders;
	}

	protected TraceMessageHeaders getTraceMessageHeaders() {
		return this.traceMessageHeaders;
	}

	/**
	 * Returns a span given the message and a channel. Returns {@code null} if ids are
	 * missing.
	 */
	protected Span buildSpan(Message<?> message) {
		return this.spanExtractor.joinTrace(message);
	}

	String getChannelName(MessageChannel channel) {
		String name = null;
		if (ClassUtils.isPresent(
				"org.springframework.integration.context.IntegrationObjectSupport",
				null)) {
			if (channel instanceof IntegrationObjectSupport) {
				name = ((IntegrationObjectSupport) channel).getComponentName();
			}
			if (name == null && channel instanceof AbstractMessageChannel) {
				name = ((AbstractMessageChannel) channel).getFullChannelName();
			}
		}
		if (name == null) {
			name = channel.toString();
		}
		return name;
	}

	String getMessageChannelName(MessageChannel channel) {
		return MESSAGE_COMPONENT + ":" + getChannelName(channel);
	}

}
