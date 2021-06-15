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

package org.springframework.cloud.sleuth.autoconfig.actuate;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.sleuth.exporter.FinishedSpan;

import static org.mockito.Mockito.mock;

class BufferingSpanReporterTests {

	@Test
	void should_overwrite_the_oldest_element_with_the_newest_when_queue_is_full() {
		BufferingSpanReporter reporter = new BufferingSpanReporter(2);
		FinishedSpan oldest = mock(FinishedSpan.class, "oldest");
		FinishedSpan second = mock(FinishedSpan.class, "second");
		FinishedSpan youngest = mock(FinishedSpan.class, "youngest");

		reporter.report(oldest);
		reporter.report(second);
		reporter.report(youngest);

		BDDAssertions.then(reporter.spans).containsExactly(second, youngest);
	}

}
