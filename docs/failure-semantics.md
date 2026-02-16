# Failure Semantics (Fail-Fast vs Fail-Partial vs Fail-Soft)

## 1) Why we even need “failure semantics” in concurrent code
In this assignment we’re doing a common pattern: one component sends requests to multiple microservices **at the same time** (fan-out), then combines the results (fan-in).

Two important things happen because it’s concurrent:
- **Completion order is nondeterministic**: services can finish in different orders each run (random delay, scheduling, etc.).
- We still have to decide what the *overall* call should do if one service fails. That decision is the **failure semantics**.

Basically: it’s not enough to say “one service failed.” We have to define what the caller should get back.

-----------------------------------------------------------------------------------------

## 2) Policy A — Fail-Fast (Atomic)
### Definition
**Fail-Fast = all-or-nothing.**  
If any microservice call fails, the overall operation fails too.

### What the caller sees
- The returned `CompletableFuture` completes exceptionally
- You don’t get a combined result string

### When it makes sense
Fail-Fast is best when partial results would be incorrect or unsafe:
- Payments / checkout: if authorization fails, you shouldn’t continue.
- Access control: if one permission check fails, returning partial “OK” can be dangerous.
- Any “atomic” workflow where the result is only valid if every part is valid.

### Downside
It can reduce availability. One flaky dependency can make the entire request fail even if most services are fine.

-----------------------------------------------------------------------------------------

## 3) Policy B — Fail-Partial (Best-Effort)
### Definition
**Fail-Partial = return whatever succeeded.**  
All services are still invoked concurrently, but failures are handled per-service so the overall call still completes normally.

### What the caller sees
- The returned future completes normally
- The result contains only successful outputs

### When it makes sense
Good when partial output is still useful:
- Dashboards / analytics: 4 graphs are better than none
- Aggregation screens: show what is available
- Optional features: if “related items” fails, the main page can still work

### Risk (quietly hiding missing data)
This policy can make the output look “valid” even when it’s incomplete.
Example: a “Top 10 trending” endpoint merges results from 5 sources. If one fails, returning the other 4 still looks fine, but the result is biased/incomplete. If you don’t log/monitor failures, you may not notice.

-----------------------------------------------------------------------------------------

## 4) Policy C — Fail-Soft (Fallback)
### Definition
**Fail-Soft = replace failures with a fallback value.**  
Instead of letting a service failure crash the whole call, we substitute a predefined fallback value.

### What the caller sees
- The returned future completes normally
- The output still has the expected “shape” (same number of parts), because failures become fallbacks

### When it makes sense
Best when availability matters and degraded output is acceptable:
- UI placeholders like `"N/A"` or `"Unavailable"`
- Recommendation: if personalized recs fail, show popular items
- Non-critical enrichment fields: missing extra info shouldn’t break the main response

### Biggest risk: masking real problems
Fail-Soft can hide issues because everything “looks successful” externally.
Two common problems:
1) Fallback looks like a real value (bad choice). Example: fallback `"0"` for a price service could accidentally display `$0.00`.
2) People stop noticing failures because users don’t see crashes. This is why fail-soft should be paired with logs/metrics and obvious fallback markers.

-----------------------------------------------------------------------------------------

## 5) Why hiding failures is extra tricky with concurrency
In sequential code, failures usually show up in a predictable order. With concurrency:
- completion order changes run-to-run,
- partial/fallback outputs can still look normal,
- and failures can become intermittent, making debugging harder.

That’s why these policies need to be explicit.

-----------------------------------------------------------------------------------------

## 6) How this assignment’s implementation behaves (design decisions)
These are the choices I used in the implementation:

- **Service/message pairing:** policy methods take `List<Microservice> services` and `List<String> messages`.  
  I pair them by index: service `i` gets message `i`.  
  If the list sizes differ, the method throws `IllegalArgumentException` because it’s a caller/config error.

- **Ordering:** even though completion order is nondeterministic, aggregated results are produced in the original list order (not completion order).  
  There is a separate method that returns completion order just to demonstrate nondeterminism.

- **Fail-Partial output:** Fail-Partial returns only successful results (no markers).  
  That means the caller must not assume completeness unless it checks.

- **Fail-Soft output:** Fail-Soft replaces failures with the provided fallback value in the output position of the failed service.

-----------------------------------------------------------------------------------------

## 7) Quick comparison table

| Policy | Does the overall call fail if one service fails? | Output | Good for | Main risk |
|---|---|---|---|---|
| Fail-Fast | Yes | Exception (no result) | correctness-critical flows | lower availability |
| Fail-Partial | No | Partial results (subset) | dashboards / aggregation | missing data may be unnoticed |
| Fail-Soft | No | Full-shaped output with fallback | high availability UX | failures can be masked |

-----------------------------------------------------------------------------------------

## 8) Small concrete example
Suppose 3 services run concurrently:
- S1 succeeds with `"OK1"`
- S2 fails
- S3 succeeds with `"OK3"`

- **Fail-Fast:** overall future fails (exception).
- **Fail-Partial:** returns `["OK1", "OK3"]`.
- **Fail-Soft** with fallback `"FALLBACK"`: returns `"OK1 FALLBACK OK3"`.

-----------------------------------------------------------------------------------------

## Takeaway
Concurrency isn’t just “things run at the same time.” Failures happen at the same time too.  
Fail-Fast, Fail-Partial, and Fail-Soft are different choices about whether we prioritize correctness, usefulness of partial data, or availability—and how visible failures are to the caller.
