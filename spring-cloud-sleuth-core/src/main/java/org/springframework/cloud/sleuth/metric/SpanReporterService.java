/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.cloud.sleuth.metric;

/**
 * @author Marcin Grzejszczak
 */
public interface SpanReporterService {

	/**
	 * Called when spans are submitted to SpanCollector for processing.
	 *
	 * @param quantity the number of spans accepted.
	 */
	void incrementAcceptedSpans(long quantity);

	/**
	 * Called when spans become lost for any reason and won't be delivered to the target collector.
	 *
	 * @param quantity the number of spans dropped.
	 */
	void incrementDroppedSpans(long quantity);
}
