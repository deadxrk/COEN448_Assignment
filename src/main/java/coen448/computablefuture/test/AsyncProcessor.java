package coen448.computablefuture.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AsyncProcessor {

    // Given code (unchanged)
    public CompletableFuture<String> processAsync(List<Microservice> microservices, String message) {

        List<CompletableFuture<String>> futures = microservices.stream()
                .map(client -> client.retrieveAsync(message))
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.joining(" ")));
    }

    // Given code (unchanged) — used to observe nondeterministic completion order
    public CompletableFuture<List<String>> processAsyncCompletionOrder(
            List<Microservice> microservices, String message) {

        List<String> completionOrder =
                Collections.synchronizedList(new ArrayList<>());

        List<CompletableFuture<Void>> futures = microservices.stream()
                .map(ms -> ms.retrieveAsync(message)
                        .thenAccept(completionOrder::add))
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> completionOrder);
    }

    // ----------------------------
    // Assignment: Policy methods
    // ----------------------------

    /**
     * Task A — Fail-Fast (Atomic Policy)
     * - Uses CompletableFuture.allOf
     * - Propagates exceptions
     * - No partial result returned
     */
    public CompletableFuture<String> processAsyncFailFast(
            List<Microservice> services,
            List<String> messages) {

        validateServicesAndMessages(services, messages);

        List<CompletableFuture<String>> futures = IntStream.range(0, services.size())
                .mapToObj(i -> services.get(i).retrieveAsync(messages.get(i)))
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.joining(" ")));
    }

    /**
     * Task B — Fail-Partial (Best-Effort Policy)
     * - Handles failures per service
     * - Returns successful results only
     * - No exception escapes to the caller
     */
    public CompletableFuture<List<String>> processAsyncFailPartial(
            List<Microservice> services,
            List<String> messages) {

        validateServicesAndMessages(services, messages);

        // Turn each call into a "never-throw" future: failures become null.
        List<CompletableFuture<String>> safeFutures = IntStream.range(0, services.size())
                .mapToObj(i -> services.get(i).retrieveAsync(messages.get(i))
                        .handle((value, ex) -> ex == null ? value : null))
                .collect(Collectors.toList());

        return CompletableFuture.allOf(safeFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> safeFutures.stream()
                        .map(CompletableFuture::join)
                        .filter(Objects::nonNull) // keep successes only
                        .collect(Collectors.toList()));
    }

    /**
     * Task C — Fail-Soft (Fallback Policy)
     * - Uses fallback values for failures
     * - Always completes normally
     * - Must document risks of masking failures (in docs/failure-semantics.md)
     */
    public CompletableFuture<String> processAsyncFailSoft(
            List<Microservice> services,
            List<String> messages,
            String fallbackValue) {

        validateServicesAndMessages(services, messages);
        Objects.requireNonNull(fallbackValue, "fallbackValue");

        // Convert failures into fallback values so the aggregate never fails.
        List<CompletableFuture<String>> safeFutures = IntStream.range(0, services.size())
                .mapToObj(i -> services.get(i).retrieveAsync(messages.get(i))
                        .exceptionally(ex -> fallbackValue))
                .collect(Collectors.toList());

        return CompletableFuture.allOf(safeFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> safeFutures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.joining(" ")));
    }

    private static void validateServicesAndMessages(List<Microservice> services, List<String> messages) {
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(messages, "messages");
        if (services.size() != messages.size()) {
            throw new IllegalArgumentException("services and messages must have the same size (index-to-index pairing).");
        }
    }
}
