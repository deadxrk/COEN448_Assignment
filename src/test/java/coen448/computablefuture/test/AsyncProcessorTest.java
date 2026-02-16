package coen448.computablefuture.test;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncProcessorTest {

    // ----------------------------
    // Helpers (Mockito-free fakes)
    // ----------------------------

    private static Microservice successService(String fixedResult) {
        return new Microservice("success") {
            @Override
            public CompletableFuture<String> retrieveAsync(String input) {
                return CompletableFuture.completedFuture(fixedResult);
            }
        };
    }

    private static Microservice failingService(RuntimeException ex) {
        return new Microservice("fail") {
            @Override
            public CompletableFuture<String> retrieveAsync(String input) {
                return CompletableFuture.failedFuture(ex);
            }
        };
    }

    // ----------------------------
    // Given behavior tests (kept)
    // ----------------------------

    @RepeatedTest(5)
    void testProcessAsyncSuccess_fixedServices() throws Exception {
        Microservice s1 = successService("Hello");
        Microservice s2 = successService("World");

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

        // Observed (not asserted) nondeterministic order
        System.out.println(order);

        assertEquals(3, order.size());
        assertTrue(order.stream().anyMatch(x -> x.startsWith("A:")));
        assertTrue(order.stream().anyMatch(x -> x.startsWith("B:")));
        assertTrue(order.stream().anyMatch(x -> x.startsWith("C:")));
    }

    // ----------------------------
    // Required policy tests (PDF)
    // ----------------------------

    @Test
    void failFast_failurePropagates() {
        AsyncProcessor processor = new AsyncProcessor();

        Microservice ok1 = successService("OK1");
        Microservice boom = failingService(new RuntimeException("boom"));
        Microservice ok3 = successService("OK3");

        CompletableFuture<String> f = processor.processAsyncFailFast(
                List.of(ok1, boom, ok3),
                List.of("m1", "m2", "m3")
        );

        // must await with timeout and assert failure propagates
        assertThrows(ExecutionException.class, () -> f.get(1, TimeUnit.SECONDS));
    }

    @Test
    void failPartial_returnsOnlySuccesses() throws Exception {
        AsyncProcessor processor = new AsyncProcessor();

        Microservice ok1 = successService("OK1");
        Microservice boom = failingService(new RuntimeException("boom"));
        Microservice ok3 = successService("OK3");

        CompletableFuture<List<String>> f = processor.processAsyncFailPartial(
                List.of(ok1, boom, ok3),
                List.of("m1", "m2", "m3")
        );

        List<String> results = f.get(1, TimeUnit.SECONDS);

        // successful results only, in original list order
        assertEquals(List.of("OK1", "OK3"), results);
    }

    @Test
    void failSoft_usesFallbackValues() throws Exception {
        AsyncProcessor processor = new AsyncProcessor();

        Microservice ok1 = successService("OK1");
        Microservice boom = failingService(new RuntimeException("boom"));
        Microservice ok3 = successService("OK3");

        CompletableFuture<String> f = processor.processAsyncFailSoft(
                List.of(ok1, boom, ok3),
                List.of("m1", "m2", "m3"),
                "FALLBACK"
        );

        String result = f.get(1, TimeUnit.SECONDS);

        // fallback in the failed position, preserving list order
        assertEquals("OK1 FALLBACK OK3", result);
    }

    @Test
    void sizeMismatch_throwsIllegalArgumentException() {
        AsyncProcessor processor = new AsyncProcessor();
        Microservice ok = successService("OK");

        assertThrows(IllegalArgumentException.class, () ->
                processor.processAsyncFailFast(List.of(ok), List.of("m1", "m2"))
        );
    }
}
