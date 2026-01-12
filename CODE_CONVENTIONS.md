# Code Conventions

## Language

- Code in english
- Commit messages in english
- Review comments can be in a language of your choosing

## Documentation

- Write technical documentation as .md files in the repository
- Specific instructions for particular tasks can be done as extra README_HOWTO_X.md
    - If it's a CMD instruction, write it in .sh instead. Then you don't need to read it, you can just run it.
- Other documentation can be collected in confluence. Link to GitHub as needed.

## Naming and Packaging

- Avoid extraneous names that only state the obvious (...Object or ...Class in a classname is silly)
- Avoid generic names that state nothing at all (Util says nothing -- everything is an util).
    - Instead, group static functions by concept and name that (Clothoid, GeometryValidation, ...)
- Names should primarily tell the role of a variable, not just repeat its type
    - Good (Tells the role of the variable in this context): `trackToLink: LocationTrack`
    - Bad (Says nothing new): `locationTrack: LocationTrack`
- Names should be thought about in the context of the containing function/class -- not globally
    - The wider context comes when the function is called, and may differ by use case
    - Good:
      ```
      fun filterPoints(points: List<IPoint>) {..}
      ```
    - Bad:
      ```
      // Specifying it as trackGeometryPoints is irrelevant for generic point filtering. They could be any points.
      fun filterPoints(trackGeometryPoints: List<IPoint>) {..}
      ```
- Split packages by domain concepts, not technical ones
    - Good: inframodel, tracklayout, ...
    - Bad: views, database, ...
- Scripts and readmes `snake_case`
- Frontend packages, files and folders `kebab-case`
- Backend packages `camelCase`

## Code Formatting

- Use automatic tools to force project-wide style, formatting code on save: prettier on frontend and ktfmt on backend
- See setup instructions in `doc/koodin_formatointi.md` (in Finnish)

## Backend Structure

- Create own package for each domain area / API
- Create a separate one for each concept, keeping them small and to-the-point
- Basic structure and invocation path: Controller -> Service(s) -> DAO(s)

### Controller

- Terminates REST-requests
    - Input parameters
    - Output values
- Controllers should not contain any business logic: call services for that
- For external APIs
    - Controllers also include the swagger annotations
    - The Controllers as well as types that are returned or accepted in the API need to be versioned
    - For more, see [rajapintapalvelu.md](doc/rajapintapalvelu.md)

### Service

- Business logic
- Orchestration over multiple DAOs or modules

### DAO

- Stores and loads domain model objects
- The DAOs should be the only classes that know about the database
- DAOs should not contain any business logic -- they serve only to store and load data

### Domain Model

- Major domain concepts that are shared throughout the application
- Geometry: the geometry plans, each in their own coordinate system, geometry represented as lines, curves and spirals
- TrackLayout: the full national track network, in a single coordinate system, geometry represented as polylines
- Common: shared concepts like switch library, track addressing, etc.

### API Model

- Object models that are not part of the main domain model
- Each API may have their own additional types, and they should reside in said APIs package
- For example, the geometry validation API needs a model for describing the results: `GeometryValidationIssue`

### Error Handling

- Don't try to catch exceptions and handle error-situations in each API function separately
    - Instead, rely on the generic handling, detailed in `doc/virhekasittely.md`

## Frontend Structure

- Create own package for each domain area
- Domain concepts described in X-model.ts
- Redux store contains domain model objects, in X-store.ts
- API calls are always abstracted behind functions in X-api.ts, not used directly from the components

## Code Style

- Favor immutable objects and pure functions where possible
- Try to "Make illegal states unrepresentable" rather than creating separate validation and checks
    - = Use the type system to define your objects so that they cannot be built with invalid or partial values
- KTFMT & prettier is used for forced code formatting ([koodin_formatointi](doc/koodin_formatointi.md)): if your code
  doesn't format cleanly, try adjusting it via blocks, local variables etc.
- Use code comments where needed -- not by default
    - Don't say in comment what the code says (don't duplicate). This is redundant:
      ```
         // Find the first X from Y, that qualifies Z
         y.xList.find(::z)
      ```
    - If you can, replace a comment by naming things descriptively to say the same thing. For example:
      ```
         // Calculate the hypothenuse for the triangle width/height
         val z = sqrt(obj.x, obj.y)
      ```
      vs
      ```
         val hypotenuse = sqrt(triangle.width triangle.height)
      ```
    - If you use comments to segment a long code section, it's a code smell that indicates a need to split the logic
      into multiple functions:
      ```
      fun longFun() {
         // Get the current x values
         ...
         // Calculate y from the x values
         ...
         // Create the response object from y
         ...
      }
      ```
      ```
      fun notSolongFun() {
         val xValues = getXs()
         val y = calculateY(xValues)
         return createResponse(y)
      }
      
      fun getXs() { ... }
      fun calculateY(xValues: List<X>) { ... }
      fun createResponse(y) { ... }
      ```

### Kotlin

#### Coding

- Favor named variables in lambdas, rather than using the default "it"
    - Good: `myList.map { item -> item.name }`
    - Bad: `myList.map { it.name }`
- Favor immutable objects, especially Kotlin data classes
- Composition can typically do whatever inheritance can... with reduced headache
- Favor pure functions (outside service objects) for more complex logic
- Kotlin external functions are useful for expanding library APIs like JDBC and ResultSet: place these in a clearly
  named separate file, e.g. `ResultSetExternal.kt`
- Consider if using `let`, `map`, `takeIf` etc. chains would be cleaner than local variables or if-structures
- You can use `also` -blocks to group side-effecting code like assertions in tests without needing variables that are
  visible to the entire test
  - For example, the current codebase has plenty of code like this:
    ```kotlin
    @Test
    fun `should do the thing`() {
        val result = doTheThing()
        assertEquals(expectedValue, result.value)
        assertTrue(result.isValid)
        val result2 = doTheOtherThing()
        // If we use "result" again here by accident, we're asserting something wrong
        assertEquals(expectedValue, result2.value)
        assertTrue(result2.isValid)
    }
    ```
  - The same could be done like this, with tighter scoping and no need for numbered variables:
    ```kotlin
    @Test
    fun `should do the thing`() {
        doTheThing().also { result ->
            assertEquals(expectedValue, result.value)
            assertTrue(result.isValid)
        }
        doTheOtherThing().also { result ->
            // Now we can just reuse the name "result" as it's scoped.
            assertEquals(expectedValue, result.value)
            assertTrue(result.isValid)
        }
    }
    ```

#### Tests

- All tests written with Junit 5 (Jupiter)
    - Due to dependencies, Idea offers assertion imports from multiple sources -- pick the jupiter ones
- Avoid mocking
- Pure unit tests are in files ending `...Test.kt`
    - These cannot have dependencies outside the code, specifically no DB connections
    - You can use the Spring context by specifying "test" & "nodb" profiles
        - This won't init DB-connections, so running SQL will fail run-time
- Database tests are written in files ending `...IT.kt`
    - These can use the Spring context to access Controller/Service/DAO classes and use the DB
    - Test SQL with these -- don't mock the DB
- E2E tests are written in files ending `UI.kt`
    - Just like IT-tests, you can use the full Spring context, particularly for initializing data
    - Use Selenium for manipulating the browser
- Kotlin supports spaces in function names with backticks -- favor these in test names for readability:
    - Good:
      ```kotlin
      @Test
      fun `should do the thing when condition`() { /* impl */ }
      ```
    - Bad:
      ```kotlin
      @Test
      fun shouldDoTheThingWhenCondition() { /* impl */ }
      ```

### TypeScript

- Use strong types, not `any`
- Favor undefined over null in potentially missing values
    - Especially avoid `value | null | undefined`
- Class naming:
    - Domain concepts can be named as-is:
        - Track, Switch, etc.
    - View components should contain some view-related name, so it's not confused with the domain concept:
        - TrackView, SwitchLabel, etc.

### Frontend state (Redux)

- Persistent UI state is kept in redux store when needed... but try to avoid unnecessary state
    - The state belongs in Redux when:
        - When multiple components in different parts of the UI tree display the same state (e.g. map selection)
        - When component state should remain when navigating to another section or refreshing the page
    - The state DOESN'T belong in Redux when:
        - When it can already be deduced from existing redux state (for example: a nullable value and isValueSet flag)
        - A form state that is initialized from fetched data (won't update on cache refresh if stored in redux)
        - When the state is transient UI state, like whether a dropdown is open or closed or form or dialog open
        - In-flight status of requests (this can easily get borked if a refresh happens mid-flight)
- Don't store entire objects, but instead only IDs where possible
    - The objects are anyhow cached, so fetching them again is not a relevant cost
    - If the page gets refreshed, the redux state remains, but cache is cleared: that works better when only IDs are
      stored

### SQL

- Write SQL in DAO-code used with JDBC
    - Exception: Migrations as separate SQL files for Flyway
    - Keep JDBC params close to the SQL (in the function) rather than separate constants
- Don't shout keywords -- Leave it for the IDE to highlight them
    - Good: `select * from gvt.alignment where ...`
    - Bad: `SELECT * FROM gvt.alignment WHERE ...`
- Read data from ResultSet by column name, rather than index
    - Good: `jdbcTemplate.query("select id from gvt.alignment") { rs -> rs.getInt("id" ) }`
    - Bad: `jdbcTemplate.query("select id from gvt.alignment") { rs -> rs.getInt(1) }`
- Pass SQL parameters as named params -- don't string-concatenate
    - If you really, really must produce dynamic SQL, use hard-coded / enumerated values for the combined SQL string
- Favor `timestamptz` over `timestamp` for sql time stamps
