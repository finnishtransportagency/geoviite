# DB Migrations and Version History

- Every table in Geoviite has a `*_version` companion table (audit trail via DB triggers). When a migration changes values in `plan`, `location_track`, etc., it must also update the corresponding `*_version` rows — old history rows must remain readable with current code.
- This applies especially to enum value removals or renames: if you remove an enum value from Kotlin, ensure no `*_version` rows still carry that value.
- The principle: current code must be able to read any historical row from any version table.
- **Postgres enum additions and data use must be in separate migration files.** `ALTER TYPE ... ADD VALUE` cannot be used in the same transaction as a statement that references the new value. Flyway runs each file in one transaction. Pattern: VN adds the enum value(s) only; V(N+1) does the data migration that uses those values. See V153 (adds values) + V154 (consumes them) as the established example.
- **Kotlin migrations that parse InfraModel XML should use existing parse infrastructure** (`toInfraModel(toInfraModelFile(...))`) rather than raw XPath/DOM. Access `GeometryProfile.groupNumber` etc. directly from the parsed Kotlin objects instead of re-implementing the XPath queries. The canonical extraction logic lives in `InfraModelConversion.kt`. `V152` is a counterexample that did it the raw way.
