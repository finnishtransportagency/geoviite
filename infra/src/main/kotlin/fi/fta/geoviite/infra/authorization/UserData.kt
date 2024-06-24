package fi.fta.geoviite.infra.authorization

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.*
import org.springframework.security.core.GrantedAuthority

data class User(val details: UserDetails, val role: Role, val availableRoles: List<Role>)

data class UserDetails(
    val userName: UserName,
    val firstName: AuthName?,
    val lastName: AuthName?,
    val organization: AuthName?,
)

data class Role(val code: Code, val privileges: List<Privilege>)

data class Privilege(val code: Code) : GrantedAuthority {
    @JsonIgnore
    override fun getAuthority(): String = code.toString()
}

val userNameLength = 3..300

data class UserName @JsonCreator(mode = DELEGATING) private constructor(private val value: String)
    : Comparable<UserName>, CharSequence by value {
    companion object {
        fun of(name: String) = UserName(removeLogUnsafe(name))
    }
    init { assertLength<UserName>(value, userNameLength) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: UserName): Int = value.compareTo(other.value)
}

val authNameLength = 1..300

data class AuthName @JsonCreator(mode = DELEGATING) private constructor(private val value: String)
    : Comparable<AuthName>, CharSequence by value {
    companion object {
        fun of(name: String) = AuthName(removeLogUnsafe(name))
    }
    init { assertLength<AuthName>(value, authNameLength) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: AuthName): Int = value.compareTo(other.value)
}
