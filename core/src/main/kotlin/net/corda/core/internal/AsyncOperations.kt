package net.corda.core.internal

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.internal.concurrent.asCordaFuture
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.contextLogger
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

    companion object {
        val logger = contextLogger()
    }

    @Synchronized
    override fun execute(): CordaFuture<Unit> {
        // Determine if any of the states have already been consumed.
        val query = QueryCriteria.VaultQueryCriteria(stateRefs = stateRefs.toList())
        val results = services.vaultService.queryBy<ContractState>(query).statesMetadata
        val notYetConsumed = results.filter { it.consumedTime == null }.map { it.ref }

        // If "notYetConsumed" is empty then all the states have already been consumed. Otherwise,
        // if the list is non empty, then watch the vault for updates to the "notYetConsumed" StateRefs.
        return if (notYetConsumed.isEmpty()) {
            logger.debug("All StateRefs have already been consumed. No need to suspend.")
            doneFuture(Unit)
        } else {
            logger.debug("Suspending flow. Flow will wake up when the supplied StateRefs are consumed: $stateRefs.")
            val futures = notYetConsumed.map { services.vaultService.whenConsumed(it).toCompletableFuture() }
            // HACK ALERT! CompletableFuture.allOf() returns a CompletableFuture<Void!>! and then returning a
            // CordaFuture<Void> breaks the state machine manager. Instead, we return a CordaFuture<Unit>, which works.
            CompletableFuture.allOf(*futures.toTypedArray()).thenApply { Unit }.asCordaFuture()
        }
    }
}