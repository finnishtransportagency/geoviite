# Code Conventions

## Language
- Code in english
- Commit messages in english
- Review comments can be in a language of your choosing

## Documentation
- Use code comments where needed - not by default 
  - Don't say in comment what the code says (don't duplicate). This is redundant:
   ```
      // Find the first X from Y, that qualifies Z
      y.xList.find(::z)
   ```
  - If you can replace a comment by refactorings/renamings, do that instead. For example:
   ```
      // Calculate the hypothenuse for the triangle width/height
      val z = sqrt(obj.x, obj.y)
      yXs.find { x -> z(x) }
   ```
- Write technical documentation as .md files in the repository
- Specific instructions for particular tasks can be done as extra README_HOWTO_X.md
  - If it's a CMD instruction, write it in .sh instead. Then you don't need to read it, you can just run it.
- Other documentation can be collected in confluence. Link to github as needed.

## Naming and Packaging
- Avoid extraneous names that only state the obvious (...Object or ...Class in a classname is silly)
- Avoid generic names that state nothing at all (Util says nothing - everything is an util). 
  - Instead, group static functions by concept and name that (Clothoid, GeometryValidation, ...)
- Split packages by domain concepts, not technical ones
  - Good: inframodel, tracklayout, ...
  - Bad: views, database, ...

## Backend Structure
- Create own package for each domain area / API
- Create a separate one for each concept, keeping them small and to-the-point
- Basic structure and invocation path: Controller -> Service(s) -> DAO(s)

### Controller
- Terminates REST-requests
  - Input parameters
  - Output values
- Controllers should not contain any business logic: call services for that

### Service
- Business logic
- Orchestration over multiple DAOs or modules

### DAO
- Stores and loads domain model objects
- The DAOs should be the only classes that know about the database 
- DAOs should not contain any business logic - they server only to store and load data

### Domain Model
- Major domain concepts that are shared throughout the application
- Geometry: the geometry plans, each in their own coordinate system, geometry represented as lines, curves and spirals
- TrackLayout: the full national track network, in a single coordinate system, geometry represented as polylines
- Common: shared concepts like switch library, track addressing, etc.

### API Model
- Object models that are not part of the main domain model
- Each API may have their own additional types, and they should reside in said APIs package
- For example, the geometry validation API needs a model for describing the results: `ValidationError`

### Error Handling
- Don't try to catch exceptions and handle error-situations in each API function separately. Instead, rely on the generic handling.
- All exceptions are caught with a generic error handler: see `fi/fta/geoviite/infra/error`
  - Exceptions are automatically logged; *you don't need to catch them for logging*
  - A JSON response with a proper HTTP 400/500 status code is automatically returned; *you don't need to catch to return a specific error to client*
    - This logic can be customized with the conversion logic (see below)
  - *4xx: Client errors*: there was something wrong with the request
    - Examples: bug in frontend-code, using old data, wrong request path, bad argument, authentication issue, bad user input, etc.
    - Can contain localized message-key for displaying a user-friendly error message on UI
    - The response tells the client what was wrong about the request
  - *5xx: Server errors*: request was fine, but something bad happened and backend can't serve it 
    - Typically unexpected. Examples: backend bugs, environment config issues, database connection failures, etc.
    - No extra information is given to client, only `500 server error`. To find the cause, you need to see server logs. 
      - This is to avoid leaking something sensitive in unexpected error situations
- Conversion logic and customizing
  - Spring exceptions that clearly indicate their nature (API errors like wrong path, etc.) are converted to 4xx errors to client
    - This is especially useful for request arguments: validate the API-function input arguments in their constructors and any failure will automatically fall in this category. *You don't need to implement anything else!*
    - Customizable in `ErrorHandling.kt` - note that this is by-error-type, so only customize if you know that all exceptions of that type need to be handled in a particular way. For call-site specific logic, use `ClientException`.
  - Any exception that is defined as a `ClientException` is converted to 4xx errors to client
    - You can inherit this exception (or use generic one) and throw it anywhere in your logic to produce the desired error-response. *You don't need to pass error-info in the return values throughout the call-chain*
    - You can provide ClientException with a localized message key that will be passed to the UI for displaying user-understandable error messages
  - The rest (fallback) are considered unexpected, and are returned as `500 server error`

## Frontend Structure
- Create own package for each domain area
- Domain concepts described in X-model.ts
- Redux store containd domain model objects, in X-store.ts

## Code Style
- Favor immutable objects and pure functions where possible
- Try to "Make illegal states unrepresentable" rather than creating separate validation and checks
  - = Use the type system to define your objects so that they cannot be built with invalid or partial values

### Kotlin
- Favor named variables in lambdas, rather than using the default "it"
  - Good: `myList.map { item -> item.name }`
  - Bad: `myList.map { it.name }`
- Favor immutable objects, especially Kotlin data classes
- Favor pure functions (outside service objects) for more complex logic

#### Tests
- All tests written with Junit 5 (Jupiter)
- Avoid mocking
- Pure unit tests are in files ending `...Test.kt`
  - These cannot have dependencies outside the code, specifically no DB connections
  - You can use the Spring context by specifying "test" & "nodb" profiles
    - This won't init DB-connections, so running SQL will fail run-time
- Database tests are written in files ending `...IT.kt`
  - These can use the Spring context to access Controller/Service/DAO classes and use the DB
  - Test SQL with these - don't mock the DB
- E2E tests are written in files ending `UI.kt`
  - Just like IT-tests, you can use the full Spring context, particularly for initializing data
  - Use Selenium for manipulating the browser

### TypeScript
- Favor undefined over null in potentially missing values
  - Especially avoid `value | null | undefined`
- Class naming:
  - Domain concepts can be named as-is:
    - Track, Switch, etc.
  - View components should contain some view-related name, so it's not confused with the domain concept:
    - TrackView, SwitchLabel, etc.

### SQL
- Write SQL in DAO-code used with JDBC
  - Exception: Migrations as separate SQL files for Flyway
  - Keep JDBC params close to the SQL (in the function) rather than separate constants
- Don't shout keywords - Leave it for the IDE to highlight them
  - Good: `select * from gvt.alignment where ...`
  - Bad: `SELECT * FROM gvt.alignment WHERE ...`
- Read data from ResultSet by column name, rather than index
  - Good: `jdbcTemplate.query("select id from gvt.alignment") { rs -> rs.getInt("id" ) }`
  - Bad: `jdbcTemplate.query("select id from gvt.alignment") { rs -> rs.getInt(1) }`
- Pass SQL parameters as named params - don't string-concatenate
- Favor `timestamptz` over `timestamp` for sql time stamps
