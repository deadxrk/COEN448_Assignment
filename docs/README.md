## Test run evidence (Maven)

I ran the full test suite using:

`mvn clean test`

Maven reported **BUILD SUCCESS** and all **32 tests passed** (0 failures, 0 errors).

During the run, the test output prints the order of received results multiple times, and the order changes across iterations (e.g., `[A:MSG, B:MSG, C:MSG]` vs `[C:MSG, A:MSG, B:MSG]`). This is expected because microservice completion order is nondeterministic in concurrent execution, and our tests observe this nondeterminism rather than asserting a fixed completion order.
