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

package org.springframework.cloud.sleuth.assertions;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.assertj.core.api.AbstractAssert;
import org.springframework.cloud.sleuth.Span;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class ListOfSpansAssert extends AbstractAssert<ListOfSpansAssert, ListOfSpans> {

	private static final Log log = LogFactory.getLog(ListOfSpansAssert.class);

	private final ObjectMapper objectMapper = new ObjectMapper();

	public ListOfSpansAssert(ListOfSpans actual) {
		super(actual, ListOfSpansAssert.class);
	}

	public static ListOfSpansAssert then(ListOfSpans actual) {
		return new ListOfSpansAssert(actual);
	}

	public ListOfSpansAssert everyParentIdHasItsCorrespondingSpan() {
		isNotNull();
		printSpans();
		List<Long> parentSpanIds = this.actual.spans.stream().flatMap(span -> span.getParents().stream())
				.distinct().collect(toList());
		List<Long> spanIds = this.actual.spans.stream()
				.map(Span::getSpanId).distinct()
				.collect(toList());
		List<Long> difference = new ArrayList<>(parentSpanIds);
		difference.removeAll(spanIds);
		log.info("Difference between parent ids and span ids " +
				difference.stream().map(span -> "id as long [" + span + "] and as hex [" + Span.idToHex(span) + "]").collect(
						joining("\n")));
		assertThat(spanIds).containsAll(parentSpanIds);
		return this;
	}

	public ListOfSpansAssert clientSideSpanWithNameHasTags(String name, Map<String, String> tags) {
		isNotNull();
		printSpans();
		List<Span> matchingSpans = this.actual.spans.stream()
				.filter(span -> span.getName().equals(name) && span.logs().stream().filter(entry ->
						entry.getEvent().equals(Span.CLIENT_SEND)).findAny().isPresent()).collect(toList());
		assertThat(matchingSpans).isNotEmpty();
		List<Map<String, String>> matchingSpansTags = matchingSpans.stream().map(Span::tags).collect(
				toList());
		Map<String, String> spanTags = new HashMap<>();
		matchingSpansTags.forEach(spanTags::putAll);
		assertThat(spanTags.entrySet()).containsAll(tags.entrySet());
		return this;
	}

	public ListOfSpansAssert hasASpanWithTagKeyEqualTo(String tagKey) {
		isNotNull();
		printSpans();
		if (!spanWithKeyTagExists(tagKey)) {
			failWithMessage("Expected spans \n <%s> \nto contain at least one span with tag key "
					+ "equal to <%s>", spansToString(), tagKey);
		}
		return this;
	}

	private boolean spanWithKeyTagExists(String tagKey) {
		for (Span span : this.actual.spans) {
			if (span.tags().containsKey(tagKey)) {
				return true;
			}
		}
		return false;
	}

	public ListOfSpansAssert hasASpanWithTagEqualTo(String tagKey, String tagValue) {
		isNotNull();
		printSpans();
		List<Span> matchingSpans = this.actual.spans.stream()
				.filter(span -> tagValue.equals(span.tags().get(tagKey)))
				.collect(toList());
		if (matchingSpans.isEmpty()) {
			failWithMessage("Expected spans \n <%s> \nto contain at least one span with tag key "
					+ "equal to <%s> and value equal to <%s>.\n\n", spansToString(), tagKey, tagValue);
		}
		return this;
	}

	private String spansToString() {
		return this.actual.spans.stream().map(span ->  "\nSPAN: " + span.toString() + " with name [" + span.getName() + "] " +
				"\nwith tags " + span.tags() + "\nwith logs " + span.logs()).collect(joining("\n"));
	}

	public ListOfSpansAssert doesNotHaveASpanWithName(String name) {
		isNotNull();
		printSpans();
		List<Span> matchingSpans = findSpansWithName(name);
		if (!matchingSpans.isEmpty()) {
			failWithMessage("Expected spans \n <%s> \nnot to contain a span with name <%s>", spansToString(), name);
		}
		return this;
	}

	private List<Span> findSpansWithName(String name) {
		return this.actual.spans.stream()
				.filter(span -> span.getName().equals(name))
				.collect(toList());
	}

	public ListOfSpansAssert hasASpanWithName(String name) {
		isNotNull();
		printSpans();
		List<Span> matchingSpans = findSpansWithName(name);
		if (matchingSpans.isEmpty()) {
			failWithMessage("Expected spans <%s> to contain a span with name <%s>", spansToString(), name);
		}
		return this;
	}

	public ListOfSpansAssert hasRpcTagsInProperOrder() {
		isNotNull();
		printSpans();
		RpcLogKeeper rpcLogKeeper = findRpcLogs();
		log.info("Rpc logs [" + rpcLogKeeper.toString() + "]");
		rpcLogKeeper.assertThatAllBelongToSameTraceAndSpan();
		rpcLogKeeper.assertThatFullRpcCycleTookPlace();
		rpcLogKeeper.assertThatRpcLogsTookPlaceInOrder();
		return this;
	}

	public ListOfSpansAssert hasRpcWithoutSeverSideDueToException() {
		isNotNull();
		printSpans();
		RpcLogKeeper rpcLogKeeper = findRpcLogs();
		log.info("Rpc logs [" + rpcLogKeeper.toString() + "]");
		rpcLogKeeper.assertThatAllButBelongToSameTraceAndSpan();
		rpcLogKeeper.assertThatClientSideEventsTookPlace();
		rpcLogKeeper.assertThatCliendLogsTookPlaceInOrder();
		return this;
	}

	private void printSpans() {
		try {
			log.info("Stored spans " + this.objectMapper.writeValueAsString(new ArrayList<>(this.actual.spans)));
		}
		catch (JsonProcessingException e) {
		}
	}

	@Override
	protected void failWithMessage(String errorMessage, Object... arguments) {
		log.error(String.format(errorMessage, arguments));
		super.failWithMessage(errorMessage, arguments);
	}

	RpcLogKeeper findRpcLogs() {
		final RpcLogKeeper rpcLogKeeper = new RpcLogKeeper();
		this.actual.spans.forEach(span -> span.logs().forEach(log -> {
			switch (log.getEvent()) {
			case Span.CLIENT_SEND:
				rpcLogKeeper.cs = log;
				rpcLogKeeper.csSpanId = span.getSpanId();
				rpcLogKeeper.csTraceId = span.getTraceId();
				break;
			case Span.SERVER_RECV:
				rpcLogKeeper.sr = log;
				rpcLogKeeper.srSpanId = span.getSpanId();
				rpcLogKeeper.srTraceId = span.getTraceId();
				break;
			case Span.SERVER_SEND:
				rpcLogKeeper.ss = log;
				rpcLogKeeper.ssSpanId = span.getSpanId();
				rpcLogKeeper.ssTraceId = span.getTraceId();
				break;
			case Span.CLIENT_RECV:
				rpcLogKeeper.cr = log;
				rpcLogKeeper.crSpanId = span.getSpanId();
				rpcLogKeeper.crTraceId = span.getTraceId();
				break;
			default:
				break;
			}
		}));
		return rpcLogKeeper;
	}
}

class RpcLogKeeper {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	org.springframework.cloud.sleuth.Log cs;
	long csSpanId;
	long csTraceId;
	org.springframework.cloud.sleuth.Log sr;
	long srSpanId;
	long srTraceId;
	org.springframework.cloud.sleuth.Log ss;
	long ssSpanId;
	long ssTraceId;
	org.springframework.cloud.sleuth.Log cr;
	long crSpanId;
	long crTraceId;

	void assertThatFullRpcCycleTookPlace() {
		log.info("Checking if Client Send took place");
		assertThat(this.cs).describedAs("Client Send log").isNotNull();
		log.info("Checking if Server Received took place");
		assertThat(this.sr).describedAs("Server Received log").isNotNull();
		log.info("Checking if Server Send took place");
		assertThat(this.ss).describedAs("Server Send log").isNotNull();
		log.info("Checking if Client Received took place");
		assertThat(this.cr).describedAs("Client Received log").isNotNull();
	}

	void assertThatClientSideEventsTookPlace() {
		log.info("Checking if Client Send took place");
		assertThat(this.cs).describedAs("Client Send log").isNotNull();
		log.info("Checking if Client Received took place");
		assertThat(this.cr).describedAs("Client Received log").isNotNull();
	}

	void assertThatAllBelongToSameTraceAndSpan() {
		log.info("Checking if RPC spans are coming from the same span");
		assertThat(this.csSpanId).describedAs("All logs should come from the same span")
				.isEqualTo(this.srSpanId).isEqualTo(this.ssSpanId).isEqualTo(this.crSpanId);
		log.info("Checking if RPC spans have the same trace id");
		assertThat(this.csTraceId).describedAs("All logs should come from the same trace")
				.isEqualTo(this.srTraceId).isEqualTo(this.ssTraceId).isEqualTo(this.crTraceId);
	}

	void assertThatAllButBelongToSameTraceAndSpan() {
		log.info("Checking if CR/CS spans are coming from the same span");
		assertThat(this.csSpanId).describedAs("All logs should come from the same span").isEqualTo(this.crSpanId);
		log.info("Checking if CR/CS spans have the same trace id");
		assertThat(this.csTraceId).describedAs("All logs should come from the same trace").isEqualTo(this.crTraceId);
	}

	void assertThatRpcLogsTookPlaceInOrder() {
		long csTimestamp = this.cs.getTimestamp();
		long srTimestamp = this.sr.getTimestamp();
		long ssTimestamp = this.ss.getTimestamp();
		long crTimestamp = this.cr.getTimestamp();
		log.info("Checking if CR is before SR");
		assertThat(csTimestamp).as("CS timestamp should be before SR timestamp").isLessThanOrEqualTo(srTimestamp);
		log.info("Checking if SR is before SS");
		assertThat(srTimestamp).as("SR timestamp should be before SS timestamp").isLessThanOrEqualTo(ssTimestamp);
		log.info("Checking if SS is before CR");
		assertThat(ssTimestamp).as("SS timestamp should be before CR timestamp").isLessThanOrEqualTo(crTimestamp);
	}

	void assertThatCliendLogsTookPlaceInOrder() {
		long csTimestamp = this.cs.getTimestamp();
		long crTimestamp = this.cr.getTimestamp();
		log.info("Checking if CS is before CR");
		assertThat(csTimestamp).as("CS timestamp should be before CR timestamp").isLessThanOrEqualTo(crTimestamp);
	}

	@Override public String toString() {
		return "RpcLogKeeper{" + "cs=" + cs + ", csSpanId=" + csSpanId + ", csTraceId="
				+ csTraceId + ", sr=" + sr + ", srSpanId=" + srSpanId + ", srTraceId="
				+ srTraceId + ", ss=" + ss + ", ssSpanId=" + ssSpanId + ", ssTraceId="
				+ ssTraceId + ", cr=" + cr + ", crSpanId=" + crSpanId + ", crTraceId="
				+ crTraceId + '}';
	}
}