package sovran.kotlin

/**
 * Generic state protocol.  All state structures must conform to this. It is highly
 * recommended that *only* data classes conform to this protocol. The system relies
 * on data class's built-in copy mechanism to function. Behavior when applied to classes
 * is currently undefined and will likely result in errors.
 */
interface State { }

/**
 * Typealias for state handlers implemented by subscribers.  T represents the type
 * of state desired. The language does not support bounds on the Generic Type but
 * it is assumed that the bound is of type State (we enforce the bounds during usage)
 *
 * example:
 * ```
 *     store.subscribe(self) { (state: MyState) in
 *         // MyState was updated, react to it in some way.
 *         print(state)
 *     }
 * ```
 * In the example above, `T` represents `MyState`.
 */
typealias Handler<StateT> = (StateT) -> Unit
