package fi.fta.geoviite.infra.authorization

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.configuration.USER_HEADER
import fi.fta.geoviite.infra.util.Code
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.assertSanitized
import org.slf4j.MDC
import org.springframework.core.convert.converter.Converter
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
    override fun getAuthority(): String = code.value
}

fun getCurrentUserName() = MDC.get(USER_HEADER)?.let(::UserName) ?: throw IllegalStateException("No user in context")

val userNameLength = 3..20
val userNameRegex = Regex("^[A-Za-z0-9_]+\$")

data class UserName @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(val value: String) : Comparable<UserName>,
    CharSequence by value {
    @JsonValue
    override fun toString(): String = value

    init {
        assertSanitized<UserName>(value, userNameRegex, userNameLength)
    }

    override fun compareTo(other: UserName): Int = value.compareTo(other.value)
}

class StringToUserNameConverter : Converter<String, UserName> {
    override fun convert(source: String): UserName = UserName(source)
}

class UserNameToStringConverter : Converter<UserName, String> {
    override fun convert(source: UserName): String = source.toString()
}

val authNameLength = 1..30
val authNameRegex = Regex("^[A-ZÄÖÅa-zäöå0-9 _\\-]+\$")

data class AuthName @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(val value: String)
    : Comparable<AuthName>, CharSequence by value {
    @JsonValue
    override fun toString(): String = value
    init { assertSanitized<AuthName>(value, authNameRegex, authNameLength, allowBlank = false) }
    override fun compareTo(other: AuthName): Int = value.compareTo(other.value)
}

class StringToAuthNameConverter : Converter<String, AuthName> {
    override fun convert(source: String): AuthName = AuthName(source)
}

class AuthNameToStringConverter : Converter<AuthName, String> {
    override fun convert(source: AuthName): String = source.toString()
}
