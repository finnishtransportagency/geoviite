package fi.fta.geoviite.infra.integration

import fi.fta.geoviite.infra.util.DaoBase
import java.time.Duration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

enum class DatabaseLock {
    PUBLICATION,
    PUBLICATION_GEOMETRY_CHANGE_CALCULATION,
    RATKO,
    RATKO_OPERATING_POINTS_FETCH,
    ELEMENT_LIST_GEN,
    VERTICAL_GEOMETRY_LIST_GEN,
    PROJEKTIVELHO,
}

/**
 * Note, this DAO is intentionally non-transactional as running with the lock should not require an encompassing
 * transaction.
 */
@Component
class LockDao @Autowired constructor(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    fun <R> runWithLock(lockName: DatabaseLock, maxLockDuration: Duration, fn: () -> R): R? {
        val lock = obtainLock(lockName, maxLockDuration)

        if (lock == null) logger.info("Failed to obtain lock: name=$lockName")
        else logger.info("Obtained lock: name=$lockName maxDuration=$maxLockDuration")

        return lock?.let {
            try {
                fn()
            } finally {
                releaseLock(lockName)
            }
        }
    }

    private fun obtainLock(lockName: DatabaseLock, duration: Duration): DatabaseLock? {
        val sql =
            """
            insert into integrations.lock (name, locked_until, locked_at)
            values (:lock_name, now() + :lock_duration * interval '1 second', now())
            on conflict (name) do update
            set locked_until = excluded.locked_until,
                locked_at = excluded.locked_at
            where lock.locked_until < now();
        """
                .trimIndent()
        val params = mapOf("lock_name" to lockName.name, "lock_duration" to duration.seconds)

        val affectedRows = jdbcTemplate.update(sql, params)
        return if (affectedRows == 1) lockName else null
    }

    private fun releaseLock(lockName: DatabaseLock) {
        val sql =
            """
            update integrations.lock 
            set locked_until = now()
            where name = :lock_name
        """
                .trimIndent()

        jdbcTemplate.update(sql, mapOf("lock_name" to lockName.name))
    }
}
