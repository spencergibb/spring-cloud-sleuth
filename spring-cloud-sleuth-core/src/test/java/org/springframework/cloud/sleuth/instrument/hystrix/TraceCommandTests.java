package org.springframework.cloud.sleuth.instrument.hystrix;

import java.util.Random;

import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.HystrixPlugins;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.TraceKeys;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.context.ApplicationEventPublisher;

import static com.netflix.hystrix.HystrixCommand.Setter.withGroupKey;
import static com.netflix.hystrix.HystrixCommandGroupKey.Factory.asKey;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

public class TraceCommandTests {

	static final long EXPECTED_TRACE_ID = 1L;
	Tracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
			Mockito.mock(ApplicationEventPublisher.class), new DefaultSpanNamer());

	@Before
	public void setup() {
		HystrixPlugins.reset();
		TestSpanContextHolder.removeCurrentSpan();
	}

	@After
	public void cleanup() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void should_remove_span_from_thread_local_after_finishing_work()
			throws Exception {
		Span firstSpanFromHystrix = givenACommandWasExecuted(traceReturningCommand());

		Span secondSpanFromHystrix = whenCommandIsExecuted(traceReturningCommand());

		then(secondSpanFromHystrix.getTraceId()).as("second trace id")
				.isNotEqualTo(firstSpanFromHystrix.getTraceId()).as("first trace id");
		then(secondSpanFromHystrix.getSavedSpan())
				.as("saved span as remnant of first span").isNull();
	}
	@Test
	public void should_create_a_local_span_with_proper_tags_when_hystrix_command_gets_executed()
			throws Exception {
		Span spanFromHystrix = whenCommandIsExecuted(traceReturningCommand());

		then(spanFromHystrix)
				.isALocalComponentSpan()
				.hasNameEqualTo("traceCommandKey")
				.hasATag("commandKey", "traceCommandKey");
	}

	@Test
	public void should_run_Hystrix_command_with_span_passed_from_parent_thread() {
		givenATraceIsPresentInTheCurrentThread();
		TraceCommand<Span> command = traceReturningCommand();

		Span spanFromCommand = whenCommandIsExecuted(command);

		then(spanFromCommand).as("Span from the Hystrix Thread").isNotNull();
		then(spanFromCommand.getTraceId()).isEqualTo(EXPECTED_TRACE_ID);
	}

	private Span givenATraceIsPresentInTheCurrentThread() {
		return this.tracer.joinTrace("http:test",
				Span.builder().traceId(EXPECTED_TRACE_ID).build());
	}

	private TraceCommand<Span> traceReturningCommand() {
		return new TraceCommand<Span>(this.tracer, new TraceKeys(),
				withGroupKey(asKey("group"))
						.andThreadPoolPropertiesDefaults(HystrixThreadPoolProperties
								.Setter().withCoreSize(1).withMaxQueueSize(1))
						.andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
								.withExecutionTimeoutEnabled(false))
				.andCommandKey(HystrixCommandKey.Factory.asKey("traceCommandKey"))) {
			@Override
			public Span doRun() throws Exception {
				return TestSpanContextHolder.getCurrentSpan();
			}
		};
	}

	private Span whenCommandIsExecuted(TraceCommand<Span> command) {
		return command.execute();
	}

	private Span givenACommandWasExecuted(TraceCommand<Span> command) {
		return whenCommandIsExecuted(command);
	}
}