package net.corda.core.internal

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateRef
import net.corda.core.internal.concurrent.asCordaFuture
import net.corda.core.node.ServiceHub
import java.util.concurrent.CompletableFuture

/**
 * An [FlowAsyncOperation] which suspends a flow until the provided [StateRef]s have been updated.
 *
 * WARNING! Remember that the flow which uses this async operation will _NOT_ wake-up until all the supplied StateRefs
 * have been updated. If the node isn't aware of the supplied StateRefs or if the StateRefs are never updated, then
 * the calling flow will remain suspended.
 *
 * @property stateRefs the StateRefs which will be updated.
 * @property services a ServiceHub instance
 */
class WaitForStatesToUpdate(val stateRefs: Set<StateRef>, val services: ServiceHub) : FlowAsyncOperation<Unit> {
    override fun execute(): CordaFuture<Unit> {
        val futures = stateRefs.map { stateRef -> services.vaultService.whenConsumed(stateRef).toCompletableFuture() }
        // Hack alert!
        // CompletableFuture.allOf() returns a CompletableFuture<Void!>! and then returning a CordaFuture<Void>
        // breaks the state machine manager, so instead, we return a CordaFuture<Unit>, which works.
        // TODO: Fix the above.
        return CompletableFuture.allOf(*futures.toTypedArray()).thenApply { Unit }.asCordaFuture()
    }
}