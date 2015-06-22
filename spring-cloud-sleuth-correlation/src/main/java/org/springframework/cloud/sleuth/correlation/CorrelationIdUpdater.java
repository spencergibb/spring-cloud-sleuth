/*
 * Copyright 2012-2015 the original author or authors.
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
package org.springframework.cloud.sleuth.correlation;

import static org.springframework.cloud.sleuth.correlation.CorrelationIdHolder.CORRELATION_ID_HEADER;

import java.util.concurrent.Callable;

import lombok.extern.slf4j.Slf4j;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

/**
 * Class that takes care of updating all necessary components with new value of
 * correlation id. It sets correlationId on {@link ThreadLocal} in
 * {@link CorrelationIdHolder} and in {@link MDC}.
 *
 * @see CorrelationIdHolder
 * @see MDC
 *
 * @author Jakub Nabrdalik, 4financeIT
 * @author Michal Chmielarz, 4financeIT
 * @author Spencer Gibb
 */
@Slf4j
public class CorrelationIdUpdater {

	private final CorrelationProvider correlationProvider;

	public CorrelationIdUpdater(CorrelationProvider correlationProvider) {
		this.correlationProvider = correlationProvider;
	}

	public void updateCorrelationId(String correlationId) {
		if (StringUtils.hasText(correlationId)) {
			log.debug("Updating correlationId with value: [" + correlationId + "]");
			CorrelationIdHolder.set(correlationId);
			correlationProvider.correlationIdSet(correlationId);
		}
	}

	/**
	 * Temporarily updates correlation ID inside block of code.
	 * Makes sure previous ID is restored after block's execution
	 *
	 * @param temporaryCorrelationId - correlationID to passed to the executed block of code
	 * @param block Callable to be executed with new ID
	 * @return the result of Callable block execution
	 */
	public <T> T withId(String temporaryCorrelationId, Callable<T> block) {
		final String oldCorrelationId = CorrelationIdHolder.get();
		try {
			updateCorrelationId(temporaryCorrelationId);
			return block.call();
		} catch (RuntimeException e) {
			logException(e);
			throw e;
		} catch (Exception e) {
			logException(e);
			throw new RuntimeException(e);
		} finally {
			updateCorrelationId(oldCorrelationId);
		}

	}

	private static void logException(Throwable e) {
		log.error("Exception occurred while trying to execute the function", e);
	}

	/**
	 * Wraps given {@link Callable} with another {@link Callable Callable} propagating correlation ID inside nested
	 * Callable/Closure.
	 * <p/>
	 * <p/>
	 * Useful in a situation when a Callable should be executed in a separate thread, for example in aspects.
	 * <p/>
	 * <pre><code>
	 * &#64;Around('...')
	 * Object wrapWithCorrelationId(ProceedingJoinPoint pjp) throws Throwable {
	 *     Callable callable = pjp.proceed() as Callable
	 *     return CorrelationIdUpdater.wrapCallableWithId {
	 *         callable.call()
	 *     }
	 * }
	 * </code></pre>
	 * <p/>
	 * <b>Note</b>: Passing only one input parameter currently is supported.
	 *
	 * @param block code block to execute in a thread with a correlation ID taken from the original thread
	 * @return wrapping block as Callable
	 */
	@SuppressWarnings("unchecked")
	public <T> Callable<T> wrapCallableWithId(final Callable<T> block) {
		final String temporaryCorrelationId = CorrelationIdHolder.get();
		// unchecked assignment due to groovyc issues with <T>
		return new Callable() {
			@Override
			public Object call() throws Exception {
				final String oldCorrelationId = CorrelationIdHolder.get();
				try {
					updateCorrelationId(temporaryCorrelationId);
					return block.call();
				} finally {
					updateCorrelationId(oldCorrelationId);
				}
			}
		};
	}
}
