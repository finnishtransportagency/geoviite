package fi.fta.geoviite.infra.integration

import fi.fta.geoviite.infra.logging.daoCall
import fi.fta.geoviite.infra.util.DaoBase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.time.Duration

enum class DatabaseLock {
    RATKO,
}

@Service
class LockDao @Autowired constructor(
    jdbcTemplateParam: NamedParameterJdbcTemplate?
) : DaoBase(jdbcTemplateParam) {

    fun <R> runWithLock(lockName: DatabaseLock, maxLockDuration: Duration, fn: () -> R): R? {
        val lock = obtainLock(lockName, maxLockDuration)

        if (lock != null) logger.daoCall("Obtained lock $lockName for duration $maxLockDuration")
        else logger.daoCall("Could not obtain lock $lockName")

        return lock?.let {
            try {
                fn()
            } finally {
                releaseLock(lockName)
            }
        }
    }

    private fun releaseLock(lockName: DatabaseLock) {
        val sql = """
            update integrations.lock 
            set locked_until = now()
            where name = :lock_name
            """.trimIndent()

        jdbcTemplate.update(sql, mapOf("lock_name" to lockName.name))
    }

    private fun obtainLock(lockName: DatabaseLock, duration: Duration): DatabaseLock? {
        val sql = """
            insert into integrations.lock (name, locked_until, locked_at)
            values (:lock_name, now() + :lock_duration * interval '1 second', now())
            on conflict (name) do update
            set locked_until = excluded.locked_until,
                locked_at = excluded.locked_at
            where lock.locked_until < now();
        """.trimIndent()
        val params = mapOf(
            "lock_name" to lockName.name,
            "lock_duration" to duration.seconds
        )

        val affectedRows = jdbcTemplate.update(sql, params)
        return if (affectedRows == 1) lockName else null
    }
}
