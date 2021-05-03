package sovran.kotlin

import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import kotlin.reflect.KClass

typealias SubscriptionID = Int

class Store {

    internal val states: MutableList<Container>
    internal val subscriptions: MutableList<Subscription<out State>>
    internal val syncQueue = Dispatchers.Default + CoroutineName("state.sync.sovran.com")
    internal val updateQueue = Dispatchers.Default + CoroutineName("state.update.sovran.com")

    init {
        states = arrayListOf()
        subscriptions = arrayListOf()
    }

    /**
     * Subscribe a closure to a particular type of state.
     * Note: Subscribers are weakly held and will be discarded automatically when no longer present.
     *
     * @param subscriber    The object subscribing to a given state type.  Must conform to `Subscriber`.
     * @param stateClazz    Type of state to which you want to subscribe. Must conform to `State`
     * @param initialState  Specifies that the handler should be called with current state upon subscribing. Default is `false`
     * @param queue         A CoroutineDispatcher on which the handler is run. Default is `Dispatcher.Default`
     * @param handler       A closure to be executed when the specified state type is modified.
     * @return subscriberId that can be used to unsubscribe at a later time.
     *
     * example
     * ```
     *  store.subscribe(subscriber = this, stateClazz = MyState::class, initialState = false, queue = Dispatchers.Main) { state ->
     *      print(state)
     *  }
     * ```
     */
    fun <StateT : State> subscribe(
            subscriber: Subscriber,
            stateClazz: KClass<StateT>,
            initialState: Boolean = false,
            queue: CoroutineDispatcher = Dispatchers.Default,
            handler: Handler<StateT>
    ): SubscriptionID {
        val subscription = Subscription(obj = subscriber, handler = handler, key = stateClazz, queue = queue)
        runBlocking(syncQueue) {
            subscriptions.add(subscription)
        }
        if (initialState) {
            currentState(stateClazz)?.let {
                notify(listOf(subscription), it)
            }
        }
        return 0
    }

    /**
     * Unsubscribe from state updates. The supplied SubscriptionID will be used to perform the
     * lookup and removal of a given subscription.
     *
     * @param subscriptionID The subscriberID given as a result from a previous subscribe() call.
     */
    fun unsubscribe(subscriptionID: SubscriptionID) {
        runBlocking(syncQueue) {
            subscriptions.removeAll {
                it.subscriptionID != subscriptionID
            }
        }
    }

    /**
     * Provides an instance of StateT as state within the system. If a state type is
     * provided more than once, it is simply ignored.
     *
     * @param state A class instance conforming to `State`.
     */
    fun <StateT : State> provide(state: StateT) {
        val exists = statesMatching(state::class)
        if (exists.isNotEmpty()) {
            return
        }
        val container = Container(state)
        runBlocking(syncQueue) {
            states.add(container)
        }
    }

    /**
     * Synchronously dispatch an Action with the intent of changing the state. Reducers are run
     * on a serial queue in the order the attached Actions are received.
     *
     * @param action        The action to be dispatched.  Must conform to `Action`.
     * @param stateClazz    The type of message this action acts upon. Must conform to `State`
     */
    fun <ActionT : Action<StateT>, StateT : State> dispatch(action: ActionT, stateClazz: KClass<StateT>) {
        // check if we have the instance type requested.
        val target = statesMatching(stateClazz).firstOrNull()
        // type the current state to match.
        var state = target?.state as? StateT ?: return

        runBlocking(updateQueue) {
            // perform data reduction.
            state = action.reduce(state)
            // state is final now, apply it back to storage.
            target.state = state
        }

        // get any handlers that work against T.StateType
        val subs = subscribersForState(stateClazz)
        notify(subs, state)
    }


    /**
     * Asynchronously dispatch an Action with the intent of changing the state. Reducers are run on
     * a serial queue in the order their operations complete.
     *
     * @param action        The action to be dispatched.  Must conform to `AsyncAction`.
     * @param stateClazz    The type of message this action acts upon. Must conform to `State`
     */
    fun <ActionT : AsyncAction<StateT, ResultT>, StateT : State, ResultT> dispatch(action: ActionT, stateClazz: KClass<StateT>) {
        // check if we have the instance type requested.
        val target = statesMatching(stateClazz).firstOrNull()
        // type the current state to match.
        var state = target?.state as? StateT ?: return

        // perform async operation.
        action.operation(state) { result: ResultT? ->
            runBlocking(updateQueue) {
                // perform data reduction.
                state = action.reduce(state, result)
                // state is final now, apply it back to storage.
                target.state = state
            }

            // get any handlers that work against T.StateType
            val subs = subscribersForState(stateClazz)
            notify(subs, state)
        }
    }

    /**
     * Retrieves the current state of a given type from the Store
     *
     * Example:
     * ```
     *      val state = store.currentState(MessagesState::class)
     * ```
     */
    fun <StateT : State> currentState(clazz: KClass<StateT>): StateT? {
        val matchingStates = statesMatching(clazz)
        return if (matchingStates.isNotEmpty())
            matchingStates[0].state as? StateT
        else
            null
    }

    /* Internal Functions */

    // Retrieves all subscribers for a particular type of state
    private fun <StateT : State> subscribersForState(stateClazz: KClass<StateT>): List<Subscription<out State>> {
        return subscriptions.filter {
            it.key == stateClazz
        }
    }

    // Returns any state instances matching T::class.
    private fun <T : State> statesMatching(clazz: KClass<T>): List<Container> = runBlocking(updateQueue) {
        return@runBlocking withContext(this.coroutineContext) {
            states.filter {
                it.state::class == clazz
            }
        }
    }

    // Notify any subscribers with the new state.
    private fun <StateT : State> notify(subscribers: List<Subscription<out StateT>>, state: StateT) {
        for (sub in subscribers) {
            val handler = sub.handler as? Handler<StateT> ?: continue
            // call said handlers to inform them of the new state.
            if (sub.owner.get() != null) {
                // call the handlers asynchronously
                runBlocking(sub.queue) {
                    launch { handler(state) }
                }
            }
        }
        clean()
    }

    // Removes any expired subscribers.
    private fun clean() {
        runBlocking(syncQueue) {
            subscriptions.removeAll {
                it.owner.get() == null
            }
        }
    }

    data class Container(var state: State)

    internal class Subscription<StateT : State>(
            obj: Any, val handler: Handler<StateT>,
            val key: KClass<StateT>,
            val queue: CoroutineDispatcher
    ) {
        val subscriptionID: SubscriptionID = createNextSubscriptionID()
        val owner: WeakReference<Any> = WeakReference(obj)

        companion object {
            private var nextSubscriptionID = 1
            fun createNextSubscriptionID(): SubscriptionID {
                val result = nextSubscriptionID
                nextSubscriptionID += 1
                return result
            }
        }
    }
}
