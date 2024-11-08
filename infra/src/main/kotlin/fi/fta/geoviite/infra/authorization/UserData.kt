package fi.fta.geoviite.infra.authorization

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.StringSanitizer
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
    @JsonIgnore override fun getAuthority(): String = code.toString()
}

data class UserName private constructor(private val value: String) : Comparable<UserName>, CharSequence by value {
    companion object {
        val allowedLength = 3..300
        const val ALLOWED_CHARACTERS = "A-Za-z0-9_\\-"
        val sanitizer = StringSanitizer(UserName::class, ALLOWED_CHARACTERS, allowedLength)

        @JvmStatic @JsonCreator fun of(name: String) = UserName(sanitizer.sanitize(name))
    }

    init {
        sanitizer.assertSanitized(value)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: UserName): Int = value.compareTo(other.value)
}

data class AuthName private constructor(private val value: String) : Comparable<AuthName>, CharSequence by value {
    companion object {
        val allowedLength = 1..300
        const val ALLOWED_CHARACTERS = "A-ZÄÖÅa-zäöå0-9 _\\-+:.,'/"
        val sanitizer = StringSanitizer(UserName::class, ALLOWED_CHARACTERS, allowedLength)

        @JvmStatic @JsonCreator fun of(name: String) = AuthName(sanitizer.sanitize(name))
    }

    init {
        sanitizer.assertSanitized(value)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: AuthName): Int = value.compareTo(other.value)
}

data class AuthCode @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<AuthCode>, CharSequence by value {

    companion object {
        const val ALLOWED_CHARACTERS = "A-Za-z0-9_\\-."
        val allowedLength = 1..20
        val sanitizer = StringSanitizer(AuthCode::class, ALLOWED_CHARACTERS, allowedLength)
    }

    init {
        sanitizer.assertSanitized(value)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: AuthCode): Int = value.compareTo(other.value)
}

fun isValidCode(source: String): Boolean = AuthCode.sanitizer.isSanitized(source)
