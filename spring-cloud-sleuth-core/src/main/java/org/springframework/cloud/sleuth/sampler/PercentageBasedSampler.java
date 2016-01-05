package org.springframework.cloud.sleuth.sampler;

import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceAccessor;

/**
 * Sampler that based on the given percentage rate will allow sampling.
 *
 * A couple of assumptions have to take place in order for the algorithm to work properly:
 *
 * <ul>
 *     <li>We're taking the TraceID into consideration for sampling to be consistent</li>
 *     <li>We treat the TraceID as a UUID - a 128 bit number - like HTrace does - https://github.com/apache/incubator-htrace/blob/master/htrace-core4/src/main/java/org/apache/htrace/core/SpanId.java</li>
 *     <li>We apply the Finagle algorithm to define whether we should sample or not (we're doing a % 100 not greater) - https://github.com/openzipkin/zipkin-java/pull/55/files#diff-31b830705fabb2e936c73337dfa5f219R83</li>
 * </ul>
 *
 * The value provided from SamplerConfiguration in terms of percentage is an estimation. It might occur that amount
 * of data sampled differs from the provided percentage.
 *
 * @author Marcin Grzejszczak
 */
public class PercentageBasedSampler implements Sampler<Void> {

	private final SamplerConfiguration samplerConfiguration;
	private final TraceAccessor traceAccessor;

	public PercentageBasedSampler(SamplerConfiguration samplerConfiguration, TraceAccessor traceAccessor) {
		this.samplerConfiguration = samplerConfiguration;
		this.traceAccessor = traceAccessor;
	}

	@Override
	public boolean next(Void info) {
		Span currentSpan = traceAccessor.getCurrentSpan();
		if (currentSpan == null) {
			return false;
		}
		int comparison = new UuidTraceIdToThresholdComparable(samplerConfiguration.getPercentage())
				.compareTo(currentSpan.getTraceId());
		return comparison <= 0;
	}

}
