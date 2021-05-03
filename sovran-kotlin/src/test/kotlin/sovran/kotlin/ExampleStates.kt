package sovran.kotlin

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.URL

data class Message(var from: String, var to: String, var content: String, var photos: List<URL>)

data class MessagesState(
        var unreadCount: Int = 0,
        var outgoingCount: Int = 0,
        var messages: List<Message> = listOf(),
        var outgoing: List<Message> = listOf()
) : State

data class UserState(
        var username: String? = null,
        var token: String? = null
) : State

data class NotProvidedState(
        var value: Int = 0
) : State

class MessagesUnreadAction(var value: Int) : Action<MessagesState> {
    override fun reduce(state: MessagesState): MessagesState {
        val newState = state.copy()
        newState.unreadCount = value
        return newState
    }
}

data class MyResultType(var value: Int)

class MessagesUnreadAsyncAction(var drop: Boolean, var value: Int) : AsyncAction<MessagesState, MyResultType> {

    override fun operation(state: MessagesState, completion: (MyResultType?) -> Unit) {
        runBlocking {
            delay(1000L)
            val result = MyResultType(value)
            completion(result)
        }
    }

    override fun reduce(state: MessagesState, operationResult: MyResultType?): MessagesState {
        val newState = state.copy()
        if (!drop) {
            operationResult?.let {
                newState.unreadCount = it.value
            }
        }
        return newState
    }

}

class NotProvidedAction : Action<NotProvidedState> {
    override fun reduce(state: NotProvidedState): NotProvidedState {
        return state
    }
}

class NotProvidedAsyncAction : AsyncAction<NotProvidedState, MyResultType> {
    override fun operation(state: NotProvidedState, completion: (MyResultType?) -> Unit) {}

    override fun reduce(state: NotProvidedState, operationResult: MyResultType?): NotProvidedState {
        return state
    }
}
