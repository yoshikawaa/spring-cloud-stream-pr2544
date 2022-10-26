package com.example.demo;

import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.stream.binder.Binding;
import org.springframework.cloud.stream.binding.BindingsLifecycleController;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.integration.endpoint.SourcePollingChannelAdapter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StringUtils;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class DemoApplicationTests {

	@Test
	void testStopProducerBinding(@Autowired ApplicationContext context, CapturedOutput output) throws Exception {

		String destination = "testQueue";
		String bindingName = "supply-out-0";
		String uri = "/actuator/bindings/{bindingName}";

		ResponseEntity<Map<String, Object>> response;
		ParameterizedTypeReference<Map<String, Object>> bodyType = new ParameterizedTypeReference<>() {};
		TestRestTemplate rest = context.getBean(TestRestTemplate.class);

		// binding is running.
		response = rest.exchange(RequestEntity.get(uri, bindingName).build(), bodyType);
		Assertions.assertThat(response.getBody())
			.isNotNull()
			.containsEntry("state", "running");

		// stop binding.
		rest.exchange(RequestEntity.post(uri, bindingName).body(Map.of("state", "STOPPED")), bodyType);
		response = rest.exchange(RequestEntity.get(uri, bindingName).build(), bodyType);
		Assertions.assertThat(response.getBody())
			.isNotNull()
			.containsEntry("state", "stopped");

		// however... Supplier channel is still running.
		int count = StringUtils.countOccurrencesOf(output.getOut(), "supply-continue");
		Thread.sleep(3000L);
		Assertions.assertThat(StringUtils.countOccurrencesOf(output.getOut(), "supply-continue"))
			.isGreaterThan(count);

		// because binding#companion is null. -> failed to set at AbstractMessageChannelBinder#doBindProducer
		BindingsLifecycleController controller = context.getBean(BindingsLifecycleController.class);
		Binding<?> binding = controller.queryState(bindingName);
		Assertions.assertThat(ReflectionTestUtils.getField(binding, "lifecycle"))
			.isNotNull();
			Assertions.assertThat(ReflectionTestUtils.getField(binding, "companion"))
			.isNull();

		// because correct companion bean is not "destination_spca" but "bindingName_spca".
		Assertions.assertThat(context.containsBean(destination + "_spca"))
			.isFalse();
		Assertions.assertThat(context.containsBean(bindingName + "_spca"))
			.isTrue();

		// restart binding.
		rest.exchange(RequestEntity.post(uri, bindingName).body(Map.of("state", "STARTED")), bodyType);
		response = rest.exchange(RequestEntity.get(uri, bindingName).build(), bodyType);
		Assertions.assertThat(response.getBody())
			.isNotNull()
			.containsEntry("state", "running");

		// next case: set correct companion.
		SourcePollingChannelAdapter channelAdapter = context.getBean(bindingName + "_spca", SourcePollingChannelAdapter.class);
		ReflectionTestUtils.setField(binding, "companion", channelAdapter);
		
		// stop binding.
		rest.exchange(RequestEntity.post(uri, bindingName).body(Map.of("state", "STOPPED")), bodyType);
		response = rest.exchange(RequestEntity.get(uri, bindingName).build(), bodyType);
		Assertions.assertThat(response.getBody())
			.isNotNull()
			.containsEntry("state", "stopped");

		// good... Supplier channel is stopped.
		count = StringUtils.countOccurrencesOf(output.getOut(), "supply-continue");
		Thread.sleep(3000L);
		Assertions.assertThat(StringUtils.countOccurrencesOf(output.getOut(), "supply-continue"))
			.isEqualTo(count);
	}
}
