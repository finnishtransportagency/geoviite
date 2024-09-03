package fi.fta.geoviite.infra.authorization

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertLength
import fi.fta.geoviite.infra.util.assertSanitized
import fi.fta.geoviite.infra.util.isSanitized
import fi.fta.geoviite.infra.util.removeLogUnsafe
import org.springframework.security.core.GrantedAuthority

data class User(val details: UserDetails, val role: Role, val availableRoles: List<Role>)

data class UserDetails(
    val userName: UserName,
    val firstName: AuthName?,
    val lastName: AuthName?,
    val organization: AuthName?,
)

data class Role(val code: AuthCode, val privileges: List<Privilege>)

data class Privilege(val code: AuthCode) : GrantedAuthority {
    @JsonIgnore
    override fun getAuthority(): String = code.toString()
}

val userNameLength = 3..300

data class UserName private constructor(private val value: String)
    : Comparable<UserName>, CharSequence by value {
    companion object {
        @JvmStatic
        @JsonCreator
        fun of(name: String) = UserName(removeLogUnsafe(name))
    }
    init { assertLength<UserName>(value, userNameLength) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: UserName): Int = value.compareTo(other.value)
}

val authNameLength = 1..300

data class AuthName private constructor(private val value: String)
    : Comparable<AuthName>, CharSequence by value {
    companion object {
        @JvmStatic
        @JsonCreator
        fun of(name: String) = AuthName(removeLogUnsafe(name))
    }
    init { assertLength<AuthName>(value, authNameLength) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: AuthName): Int = value.compareTo(other.value)
}

data class AuthCode @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<AuthCode>, CharSequence by value {

    companion object {
        val sanitizer = Regex("^[A-Za-z0-9_\\-.]+\$")
    }

    init { assertSanitized<AuthCode>(value, sanitizer) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: AuthCode): Int = value.compareTo(other.value)
}

fun isValidCode(source: String): Boolean = isSanitized(source, AuthCode.sanitizer, allowBlank = false)
