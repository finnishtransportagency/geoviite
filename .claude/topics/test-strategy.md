# Test Strategy

Geoviite strongly prefers real DB over mocks. Before reaching for a mock, ask: is this testing the right thing? Would an IT test (real DB) give more confidence? Could the dependency be injected as a lambda instead?

- Unit tests (`*Test.kt`) are for pure logic with no DB.
- Integration tests (`*IT.kt`) hit a real DB and are the preferred way to test anything involving persistence or queries.
- Mocking is a last resort — if you find yourself mocking a DAO or service, reconsider the test design.

When comparing collections in tests, use direct equality (`assertEquals(listOf("a", "b"), actual)`) rather than checking size and individual items separately. Kotlin's collection `equals` gives better failure messages showing both lists in full.
