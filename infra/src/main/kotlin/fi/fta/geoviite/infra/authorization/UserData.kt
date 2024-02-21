package fi.fta.geoviite.infra.authorization

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.Code
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.assertSanitized
import org.springframework.security.core.GrantedAuthority

data class User(val details: UserDetails, val role: Role)

data class UserDetails(
    val userName: UserName,
    val firstName: AuthName?,
    val lastName: AuthName?,
    val organization: AuthName?,
)

data class Role(val code: Code, val name: AuthName, val privileges: List<Privilege>)

data class Privilege(val code: Code, val name: AuthName, val description: FreeText) : GrantedAuthority {
    @JsonIgnore
    override fun getAuthority(): String = code.toString()
}


val userNameLength = 3..20
val userNameRegex = Regex("^[A-Za-z0-9_]+\$")

data class UserName @JsonCreator(mode = DELEGATING) constructor(private val value: String)
    : Comparable<UserName>, CharSequence by value {
    init { assertSanitized<UserName>(value, userNameRegex, userNameLength) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: UserName): Int = value.compareTo(other.value)
}

val authNameLength = 1..30
val authNameRegex = Regex("^[A-ZÄÖÅa-zäöå0-9 _\\-]+\$")

data class AuthName @JsonCreator(mode = DELEGATING) constructor(private val value: String)
    : Comparable<AuthName>, CharSequence by value {
    init { assertSanitized<AuthName>(value, authNameRegex, authNameLength, allowBlank = false) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: AuthName): Int = value.compareTo(other.value)
}
