package mirujam.nekomemo.domain.model

import android.content.Context

sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val userMessage: UserMessage = UserMessage.Raw(message)
    ) : AppResult<Nothing>()
}

sealed class UserMessage {
    data class Raw(val text: String) : UserMessage()
    data class Resource(val resId: Int, val args: Array<Any> = emptyArray()) : UserMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Resource) return false
            return resId == other.resId && args.contentEquals(other.args)
        }
        override fun hashCode(): Int = 31 * resId + args.contentHashCode()
    }

    fun asString(context: Context): String = when (this) {
        is Raw -> text
        is Resource -> context.getString(resId, *args)
    }
}

fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Error -> this
}
