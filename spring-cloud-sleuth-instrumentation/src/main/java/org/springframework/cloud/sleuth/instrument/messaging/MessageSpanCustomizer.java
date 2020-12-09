/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.messaging;

import org.springframework.cloud.sleuth.Span;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.ExecutorChannelInterceptor;

/**
 * Allows customization of messaging spans.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public interface MessageSpanCustomizer {

	/**
	 * Customizes the span created when
	 * {@link ExecutorChannelInterceptor#beforeHandle(Message, MessageChannel, MessageHandler)}
	 * gets called.
	 * @param spanCustomizer current span to customize
	 * @param message received or sent message
	 * @param messageChannel channel from / to which the message was sent
	 * @return customized span
	 */
	Span customizeHandle(Span spanCustomizer, Message<?> message, @Nullable MessageChannel messageChannel);

	/**
	 * Customizes the span created when
	 * {@link ExecutorChannelInterceptor#beforeHandle(Message, MessageChannel, MessageHandler)}
	 * gets called for a span retrieved from the incoming headers.
	 * @param builder current span to customize
	 * @param message received or sent message
	 * @param messageChannel channel from / to which the message was sent
	 * @return customized span
	 */
	Span.Builder customizeHandle(Span.Builder builder, Message<?> message, @Nullable MessageChannel messageChannel);

	/**
	 * Customizes the span created when
	 * {@link ExecutorChannelInterceptor#postReceive(Message, MessageChannel)} gets
	 * called.
	 * @param builder current span to customize
	 * @param message received or sent message
	 * @param messageChannel channel from / to which the message was sent
	 * @return customized span
	 */
	Span.Builder customizeReceive(Span.Builder builder, Message<?> message, @Nullable MessageChannel messageChannel);

	/**
	 * Customizes the span created when
	 * {@link ExecutorChannelInterceptor#preSend(Message, MessageChannel)} gets called.
	 * @param builder current span to customize
	 * @param message received or sent message
	 * @param messageChannel channel from / to which the message was sent
	 * @return customized span
	 */
	Span.Builder customizeSend(Span.Builder builder, Message<?> message, @Nullable MessageChannel messageChannel);

}
