import RequestProperty.Type.CORRELATION_ID_HEADER
import RequestProperty.Type.ROLE
import RequestProperty.Type.USER
import fi.fta.geoviite.infra.authorization.AuthCode
import fi.fta.geoviite.infra.authorization.UserName
import org.apache.logging.log4j.ThreadContext

private class RequestProperty<T>(val type: Type, private val init: (String) -> T) : IRequestProperty<T> {
    override fun set(newValue: T) = ThreadContext.put(type.header, newValue.toString())

    override fun clear() = ThreadContext.remove(type.header)

    override fun get(): T =
        requireNotNull(getOrNull()) { "Value for ${type.name} (header ${type.header}) not found in context" }

    override fun getOrNull(): T? = ThreadContext.get(type.header)?.let(init)

    enum class Type(val header: String) {
        CORRELATION_ID_HEADER("correlationId"),
        USER("user"),
        ROLE("role"),
    }
}

interface IRequestProperty<T> {
    fun set(newValue: T)

    fun clear()

    fun get(): T

    fun getOrNull(): T?
}

val currentUser: IRequestProperty<UserName> = RequestProperty(USER, UserName::of)
val currentUserRole: IRequestProperty<AuthCode> = RequestProperty(ROLE, ::AuthCode)
val correlationId: IRequestProperty<String> = RequestProperty(CORRELATION_ID_HEADER) { it }

fun <T> withUser(user: UserName, op: () -> T): T {
    currentUser.set(user)
    return try {
        op()
    } finally {
        currentUser.clear()
    }
}
