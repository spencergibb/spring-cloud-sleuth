package org.springframework.cloud.sleuth.sampler;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Marcin Grzejszczak
 * @author Adrian Cole
 */
@ConfigurationProperties("spring.sleuth.sampler")
public class SamplerProperties {

	/**
	 * Percentage of requests that should be sampled. E.g. 1.0 - 100% requests should be
	 * sampled. The precision is whole-numbers only (i.e. there's no support for 0.1% of
	 * the traces).
	 */
	private float percentage = 0.1f;

	public float getPercentage() {
		return this.percentage;
	}

	public void setPercentage(float percentage) {
		this.percentage = percentage;
	}
}
