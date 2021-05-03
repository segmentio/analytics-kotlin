package sovran.kotlin

interface Action<StateT: State> {
    /**
     * The reducer for this action.  Reducer implementations should be constructed as pure functions,
     * such that the returned state is only determined by its input values, without observable
     * side effects. We recommend using the data class `copy()` function to create a copy before
     * working on the state
     *
     * example:
     * ```
     * class SomeAction : Action<SomeState> {
     *      override fun reduce(state: SomeState): SomeState {
     *          return state.copy()
     *      }
     * }
     * ```
     **/
    fun reduce(state: StateT): StateT
}

interface AsyncAction<StateT: State, ResultT> {
    /**
    * The asynchronous operation for this Action.
    *
    * The state provided here will almost certainly be different than what the
    * reducer sees at a later date.  Also, if the completion closure is not called,
    * the action is simply dropped.
    *
    * example:
    * ```
    * class ToggleAction: AsyncAction<SwitchState, NetworkResult> {
    *   override fun operation(state: SwitchState, completion: (NetworkResult?) -> Unit) {
    *       network.async(myRequest) { it
    *           completion(it)
    *       }
    *   }
    *
    *   override fun reduce(state: SwitchState, operationResult: NetworkResult): SwitchState {
    *       val newState = state.copy()
    *       if (networkResult.allowed) {
    *           newState.isOn = value
    *       }
    *       return newState
    *   }
    * }
    * ```
    */
    fun operation(state: StateT, completion: (ResultT?) -> Unit)

    /**
     * The reducer for this action. Reducer implementations should be constructed such
     * that the returned state is only determined by its input values, without observable
     * side effects.
     *
     * example:
     * ```
     * class ToggleAction: AsyncAction<SwitchState, NetworkResult> {
     *   override fun operation(state: SwitchState, completion: (NetworkResult?) -> Unit) {
     *       network.async(myRequest) { it
     *           completion(it)
     *       }
     *   }
     *
     *   override fun reduce(state: SwitchState, operationResult: NetworkResult): SwitchState {
     *       val newState = state.copy()
     *       if (networkResult.allowed) {
     *           newState.isOn = value
     *       }
     *       return newState
     *   }
     * }
     * ```
     */
    fun reduce(state: StateT, operationResult: ResultT?): StateT
}
