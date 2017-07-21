package org.springframework.cloud.sleuth.instrument.reactor;

import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.async.TraceableScheduledExecutorService;
import org.springframework.context.annotation.Configuration;

import reactor.core.Fuseable;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * to enable tracing of Reactor components via Spring Cloud Sleuth.
 *
 * @author Stephane Maldini
 * @author Marcin Grzejszczak
 * @since 1.3.0
 */
@Configuration
@ConditionalOnProperty(value="spring.sleuth.reactor.enabled", matchIfMissing=true)
@ConditionalOnClass(Mono.class)
@AutoConfigureAfter(TraceAutoConfiguration.class)
public class TraceReactorAutoConfiguration {

	@Configuration
	@ConditionalOnBean(Tracer.class)
	static class TraceReactorConfiguration {
		@Autowired Tracer tracer;
		@Autowired TraceKeys traceKeys;
		@Autowired SpanNamer spanNamer;

		@PostConstruct
		public void setupHooks() {
			Hooks.onNewSubscriber((pub, sub) -> {
				//do not trace fused flows or simple just/error/empty
				if(pub instanceof Fuseable && sub instanceof Fuseable.QueueSubscription
						|| pub instanceof Fuseable.ScalarCallable){
					return sub;
				}
				return new SpanSubscriber(sub, sub.currentContext(), this.tracer, pub
						.toString());
			});
			Schedulers.setFactory(new Schedulers.Factory() {
				@Override public ScheduledExecutorService decorateScheduledExecutorService(
						String schedulerType,
						Supplier<? extends ScheduledExecutorService> actual) {
					return new TraceableScheduledExecutorService(actual.get(),
							TraceReactorConfiguration.this.tracer,
							TraceReactorConfiguration.this.traceKeys,
							TraceReactorConfiguration.this.spanNamer);
				}
			});
		}
	}
}