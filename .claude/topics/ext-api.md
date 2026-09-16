# Ext-API Conventions (`fi.fta.geoviite.api.tracklayout.v1`)

These apply whenever working in the ext-api package:

- **Enum string values follow the FI_ const pattern**: define `const val FI_FOO = "fi_value"` in `ExtTrackLayoutResponseDataV1.kt`, then use `@JsonValue val value: String` in the enum. Do not use `@JsonProperty` directly on enum entries.
- **M-values and lengths use `BigDecimal`**, not `Double`, to avoid floating-point noise in output. Coordinates (x/y) stay as `Double`.
- **Check for existing types before adding new ones**: `ExtCoordinateV1` (x/y pair) and `ExtMeasuredAddressPointV1` (x/y/m/address) already exist — don't create duplicates.
- **Field name constants go in `ExtTrackLayoutConstantsV1.kt`**, not inline in data class files.
- **New endpoints must be registered in `ExtTestTrackLayoutV1IT`** for versioning test coverage.
- **Error throws use helpers from `ExtTrackLayoutExceptionsV1.kt`** (`throwOidTargetNotFound`, `throwLocationTrackNotFound`, etc.) — don't inline equivalent `error()` or `throw` logic.
