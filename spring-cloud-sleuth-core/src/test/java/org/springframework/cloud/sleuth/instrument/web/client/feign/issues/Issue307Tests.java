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

package org.springframework.cloud.sleuth.instrument.web.client.feign.issues;

import java.util.ArrayList;
import java.util.List;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.BDDAssertions.then;

public class Issue307Tests {

	@Before
	public void setup() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void should_start_context() {
		try (ConfigurableApplicationContext applicationContext = SpringApplication
				.run(SleuthSampleApplication.class, "--spring.jmx.enabled=false")) {
		}
		then(ExceptionUtils.getLastException()).isNull();
	}
}

@SpringBootApplication
@RestController
@EnableFeignClients
@EnableCircuitBreaker
class SleuthSampleApplication {

	private static final Logger LOG = Logger.getLogger(SleuthSampleApplication.class.getName());

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private ParticipantsBean participantsBean;

	@Bean
	public RestTemplate getRestTemplate() {
		return new RestTemplate();
	}

	@Bean
	public AlwaysSampler defaultSampler() {
		return new AlwaysSampler();
	}

	@RequestMapping("/")
	public String home() {
		LOG.log(Level.INFO, "you called home");
		return "Hello World";
	}

	@RequestMapping("/callhome")
	public String callHome() {
		LOG.log(Level.INFO, "calling home");
		return restTemplate.getForObject("http://localhost:8080", String.class);
	}
}

@Component
class ParticipantsBean {
	@Autowired
	private ParticipantsClient participantsClient;

	@HystrixCommand(fallbackMethod = "defaultParticipants")
	public List<Object> getParticipants(String raceId) {
		return participantsClient.getParticipants(raceId);
	}

	public List<Object> defaultParticipants(String raceId) {
		return new ArrayList<>();
	}
}

@FeignClient("participants")
interface ParticipantsClient {

	@RequestMapping(method = RequestMethod.GET, value="/races/{raceId}")
	List<Object> getParticipants(@PathVariable("raceId") String raceId);

}
