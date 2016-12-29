package org.springframework.cloud.sleuth.metric;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Sleuth related metrics
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@ConfigurationProperties("spring.sleuth.metric")
public class SleuthMetricProperties {

	/**
	 * Enable calculation of accepted and dropped spans through {@link org.springframework.boot.actuate.metrics.CounterService}
	 */
	private boolean enabled = true;

	private Span span = new Span();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Span getSpan() {
		return this.span;
	}

	public void setSpan(Span span) {
		this.span = span;
	}

	public static class Span {

		private String acceptedName = "counter.span.accepted";

		private String droppedName = "counter.span.dropped";

		public String getAcceptedName() {
			return this.acceptedName;
		}

		public void setAcceptedName(String acceptedName) {
			this.acceptedName = acceptedName;
		}

		public String getDroppedName() {
			return this.droppedName;
		}

		public void setDroppedName(String droppedName) {
			this.droppedName = droppedName;
		}
	}
}
