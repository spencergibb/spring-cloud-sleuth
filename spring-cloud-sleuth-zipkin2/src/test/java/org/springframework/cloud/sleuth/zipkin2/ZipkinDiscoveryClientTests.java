package org.springframework.cloud.sleuth.zipkin2;

import static org.assertj.core.api.BDDAssertions.then;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import okhttp3.mockwebserver.MockWebServer;
import org.awaitility.Awaitility;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequest;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ZipkinDiscoveryClientTests.Config.class, properties = {
		"spring.zipkin.baseUrl=http://zipkin/",
		"spring.zipkin.sender.type=web" // override default priority which picks rabbit due to classpath
})
public class ZipkinDiscoveryClientTests {

	@ClassRule public static MockWebServer ZIPKIN_RULE = new MockWebServer();

	@Autowired SpanReporter spanReporter;

	@Test
	public void shouldUseDiscoveryClientToFindZipkinUrlIfPresent() throws Exception {
		Span span = Span.builder().traceIdHigh(1L).traceId(2L).spanId(3L).name("foo")
				.build();

		this.spanReporter.report(span);

		Awaitility.await().untilAsserted(() -> then(ZIPKIN_RULE.getRequestCount()).isGreaterThan(0));
	}

	@Configuration
	@EnableAutoConfiguration
	static class Config {

		@Bean LoadBalancerClient loadBalancerClient() {
			return new LoadBalancerClient() {
				@Override public <T> T execute(String serviceId,
						LoadBalancerRequest<T> request) throws IOException {
					return null;
				}

				@Override public <T> T execute(String serviceId,
						ServiceInstance serviceInstance, LoadBalancerRequest<T> request)
						throws IOException {
					return null;
				}

				@Override public URI reconstructURI(ServiceInstance instance,
						URI original) {
					return null;
				}

				@Override public ServiceInstance choose(String serviceId) {
					return new ServiceInstance() {
						@Override
						public String getServiceId() {
							return "zipkin";
						}

						@Override
						public String getHost() {
							return "localhost";
						}

						@Override
						public int getPort() {
							return ZIPKIN_RULE.url("/").port();
						}

						@Override
						public boolean isSecure() {
							return false;
						}

						@Override
						public URI getUri() {
							return ZIPKIN_RULE.url("/").uri();
						}

						@Override
						public Map<String, String> getMetadata() {
							return null;
						}
					};
				}
			};
		}
	}
}