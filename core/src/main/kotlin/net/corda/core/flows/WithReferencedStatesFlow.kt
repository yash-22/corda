package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateRef
import net.corda.core.internal.executeAsync

/**
 * Given a flow which uses [ReferenceState]s, the [WithReferencedStatesFlow] will execute the the flow as a subFlow.
 * If the flow fails due to a [NotaryError.Conflict] for a [ReferenceState], then it will be suspended until the
 * state refs for the [ReferenceState]s are updated.
 *
 * @param flowLogic a flow which uses reference states.
 */
class WithReferencedStatesFlow<T : Any>(val flowLogic: FlowLogic<T>) : FlowLogic<T>() {

    private sealed class FlowResult {
        data class Success<T : Any>(val value: T) : FlowResult()
        data class Conflict(val stateRefs: Set<StateRef>) : FlowResult()
    }

    @Suspendable
    override fun call(): T {
        // Try starting the flow (as a subFlow) which uses the reference states.
        val result = try {
            subFlow(flowLogic)
        } catch (e: FlowException) {
            e
        }

        // Process the flow result. We don't care about anything other than NotaryExceptions. If it is a
        // NotaryException but not a Conflict, then just rethrow. It's it's a Conflict, then extract the reference
        // input state refs. Otherwise, if the flow completes successfully then return the result.
        val processedResult = when (result) {
            is NotaryException -> {
                val error = result.error
                if (error is NotaryError.Conflict) {
                    val conflictingReferenceStateRefs = error.consumedStates.filter {
                        it.value.type == ConsumedStateType.REFERENCE_INPUT_STATE
                    }.map { it.key }.toSet()
                    FlowResult.Conflict(conflictingReferenceStateRefs)
                } else {
                    throw result
                }
            }
            is FlowException -> throw result
            else -> FlowResult.Success(result)
        }

        // Return the flow result or wait for the StateRefs to be updated and try again.
        @Suppress("UNCHECKED_CAST")
        return when (processedResult) {
            is FlowResult.Success<*> -> processedResult.value as T  // Will always be T.
            is FlowResult.Conflict -> {
                executeAsync(WaitForStatesToUpdate(processedResult.stateRefs, serviceHub))
                // The flow has now woken up because the conflicting
                // reference states have been updated.
                subFlow(flowLogic)
            }
        }
    }

}