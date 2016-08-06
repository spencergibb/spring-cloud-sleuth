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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;

/**
 * Abstract class for logging the client received event
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
abstract class FeignEventPublisher {

	protected final BeanFactory beanFactory;
	private Tracer tracer;

	protected FeignEventPublisher(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	protected void finish() {
		Span span = getTracer().getCurrentSpan();
		if (span != null) {
			span.logEvent(Span.CLIENT_RECV);
			getTracer().close(span);
		}
	}

	Tracer getTracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}
}
