package coen448.computablefuture.test;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncProcessorTest {

    /**
     * A Mockito-free "fake microservice":
     * returns a fixed string immediately, ignoring the input message.
     */
    private static Microservice fixedService(String fixedResult) {
        return new Microservice("fixed") {
            @Override
            public CompletableFuture<String> retrieveAsync(String input) {
                return CompletableFuture.completedFuture(fixedResult);
            }
        };
    }

    @RepeatedTest(5)
    void testProcessAsyncSuccess_fixedServices() throws Exception {
        Microservice s1 = fixedService("Hello");
        Microservice s2 = fixedService("World");

        AsyncProcessor processor = new AsyncProcessor();

        CompletableFuture<String> resultFuture =
                processor.processAsync(List.of(s1, s2), null);

        String result = resultFuture.get(1, TimeUnit.SECONDS);
        assertEquals("Hello World", result);
    }

    @ParameterizedTest
    @CsvSource({
            "hi, Hello:HI World:HI",
            "cloud, Hello:CLOUD World:CLOUD",
            "async, Hello:ASYNC World:ASYNC"
    })
    void testProcessAsync_withDifferentMessages(String message, String expectedResult) throws Exception {
        Microservice service1 = new Microservice("Hello");
        Microservice service2 = new Microservice("World");

        AsyncProcessor processor = new AsyncProcessor();

        CompletableFuture<String> resultFuture =
                processor.processAsync(List.of(service1, service2), message);

        String result = resultFuture.get(1, TimeUnit.SECONDS);
        assertEquals(expectedResult, result);
    }

    @RepeatedTest(20)
    void showNondeterminism_completionOrderVaries() throws Exception {
        Microservice s1 = new Microservice("A");
        Microservice s2 = new Microservice("B");
        Microservice s3 = new Microservice("C");

        AsyncProcessor processor = new AsyncProcessor();

        List<String> order = processor
                .processAsyncCompletionOrder(List.of(s1, s2, s3), "msg")
                .get(1, TimeUnit.SECONDS);

        // Not asserting a fixed order (intentionally nondeterministic)
        System.out.println(order);

        // Minimal sanity check: all three must be present
        assertEquals(3, order.size());
        assertTrue(order.stream().anyMatch(x -> x.startsWith("A:")));
        assertTrue(order.stream().anyMatch(x -> x.startsWith("B:")));
        assertTrue(order.stream().anyMatch(x -> x.startsWith("C:")));
    }
}
//ff