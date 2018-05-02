package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateRef
import net.corda.core.internal.uncheckedCast
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.contextLogger

/**
 * Given a flow which uses reference states, the [WithReferencedStatesFlow] will execute the the flow as a subFlow.
 * If the flow fails due to a [NotaryError.Conflict] for a reference state, then it will be suspended until the
 * state refs for the reference states are updated, the node will then retry running the flow
 *
 * @param flowLogic a flow which uses reference states.
 */
class WithReferencedStatesFlow<T : Any>(
        val flowLogic: FlowLogic<T>,
        override val progressTracker: ProgressTracker = WithReferencedStatesFlow.tracker()
) : FlowLogic<T>() {

    companion object {
        val logger = contextLogger()

        object ATTEMPT : ProgressTracker.Step("Attempting to run flow which uses reference states.")
        object RETRYING : ProgressTracker.Step("Reference states are out of date! Waiting for updated states...")
        object SUCCESS : ProgressTracker.Step("Flow was ran successfully.")

        @JvmStatic
        fun tracker() = ProgressTracker(ATTEMPT, RETRYING, SUCCESS)
    }

    private sealed class FlowResult {
        data class Success<T : Any>(val value: T) : FlowResult()
        data class Conflict(val stateRefs: Set<StateRef>) : FlowResult()
    }

    // Return a successful flow result or a FlowException.
    @Suspendable
    fun runFlow(): Any = try {
        subFlow(flowLogic)
    } catch (e: FlowException) {
        e
    }

    // Process the flow result. We don't care about anything other than NotaryExceptions. If it is a
    // NotaryException but not a Conflict, then just rethrow. It's it's a Conflict, then extract the reference
    // input state refs. Otherwise, if the flow completes successfully then return the result.
    private fun processFlowResult(result: Any): FlowResult {
        return when (result) {
            is NotaryException -> {
                val error = result.error
                if (error is NotaryError.Conflict) {
                    val conflictingReferenceStateRefs = error.consumedStates.filter {
                        it.value.type == StateConsumptionDetails.ConsumedStateType.REFERENCE_INPUT_STATE
                    }.map { it.key }.toSet()
                    FlowResult.Conflict(conflictingReferenceStateRefs)
                } else {
                    throw result
                }
            }
            is FlowException -> throw result
            else -> FlowResult.Success(result)
        }
    }

    @Suspendable
    override fun call(): T {
        progressTracker.currentStep = ATTEMPT

        // Loop until the flow successfully completes. We need to
        // do this because there might be consecutive update races.
        while (true) {
            logger.debug("Attempting to run the supplied flow. ${flowLogic.javaClass.canonicalName}")
            val result = runFlow()
            val processedResult = processFlowResult(result)

            // Return the flow result or wait for the StateRefs to be updated and try again.
            // 1. If a success, we can always cast the return type to T.
            // 2. If there is a conflict, then suspend this flow, only waking it up when the conflicting reference
            //    states have been updates.
            @Suppress("UNCHECKED_CAST")
            when (processedResult) {
                is FlowResult.Success<*> -> {
                    logger.debug("Flow completed successfully.")
                    progressTracker.currentStep = SUCCESS
                    return uncheckedCast(processedResult.value)
                }
                is FlowResult.Conflict -> {
                    val conflicts = processedResult.stateRefs
                    logger.debug("Flow ${flowLogic.javaClass.name} failed due to reference state conflicts: $conflicts.")

                    // Only set currentStep to FAILURE once.
                    if (progressTracker.currentStep != RETRYING) {
                        progressTracker.currentStep = RETRYING
                    }

                    // Suspend this flow.
                    waitForStatesToUpdate(conflicts)
                    logger.debug("All referenced states have been updated. Retrying flow...")
                }
            }
        }
    }
}