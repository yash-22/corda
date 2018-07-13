package net.corda.docs

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.toFuture
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.node.MockNetworkTest
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.startFlow
import org.junit.Test
import kotlin.test.assertEquals

class WorkflowTransactionBuildTutorialTest {

    companion object {
        private val mockNet: InternalMockNetwork by lazy { MockNetworkTest.mockNet }
        private val aliceNode: StartedNode<InternalMockNetwork.MockNode> by lazy { MockNetworkTest.aliceNode }
        private val bobNode: StartedNode<InternalMockNetwork.MockNode> by lazy { MockNetworkTest.bobNode }
        private val notary: Party = mockNet.defaultNotaryIdentity
        private val alice = aliceNode.services.myInfo.identityFromX500Name(ALICE_NAME)
        private val bob = bobNode.services.myInfo.identityFromX500Name(BOB_NAME)
    }

    // Helper method to locate the latest Vault version of a LinearState
    private inline fun <reified T : LinearState> ServiceHub.latest(ref: UniqueIdentifier): StateAndRef<T> {
        val linearHeads = vaultService.queryBy<T>(QueryCriteria.LinearStateQueryCriteria(uuid = listOf(ref.id)))
        return linearHeads.states.single()
    }


    @Test
    fun `Run workflow to completion`() {
        // Setup a vault subscriber to wait for successful upload of the proposal to NodeB
        val nodeBVaultUpdate = bobNode.services.vaultService.updates.toFuture()
        // Kick of the proposal flow
        val flow1 = aliceNode.services.startFlow(SubmitTradeApprovalFlow("1234", bob))
        // Wait for the flow to finish
        val proposalRef = flow1.resultFuture.getOrThrow()
        val proposalLinearId = proposalRef.state.data.linearId
        // Wait for NodeB to include it's copy in the vault
        nodeBVaultUpdate.get()
        // Fetch the latest copy of the state from both nodes
        val latestFromA = aliceNode.database.transaction {
            aliceNode.services.latest<TradeApprovalContract.State>(proposalLinearId)
        }
        val latestFromB = bobNode.database.transaction {
            bobNode.services.latest<TradeApprovalContract.State>(proposalLinearId)
        }
        // Confirm the state as as expected
        assertEquals(WorkflowState.NEW, proposalRef.state.data.state)
        assertEquals("1234", proposalRef.state.data.tradeId)
        assertEquals(alice, proposalRef.state.data.source)
        assertEquals(bob, proposalRef.state.data.counterparty)
        assertEquals(proposalRef, latestFromA)
        assertEquals(proposalRef, latestFromB)
        // Setup a vault subscriber to pause until the final update is in NodeA and NodeB
        val nodeAVaultUpdate = aliceNode.services.vaultService.updates.toFuture()
        val secondNodeBVaultUpdate = bobNode.services.vaultService.updates.toFuture()
        // Run the manual completion flow from NodeB
        val flow2 = bobNode.services.startFlow(SubmitCompletionFlow(latestFromB.ref, WorkflowState.APPROVED))
        // wait for the flow to end
        val completedRef = flow2.resultFuture.getOrThrow()
        // wait for the vault updates to stabilise
        nodeAVaultUpdate.get()
        secondNodeBVaultUpdate.get()
        // Fetch the latest copies from the vault
        val finalFromA = aliceNode.database.transaction {
            aliceNode.services.latest<TradeApprovalContract.State>(proposalLinearId)
        }
        val finalFromB = bobNode.database.transaction {
            bobNode.services.latest<TradeApprovalContract.State>(proposalLinearId)
        }
        // Confirm the state is as expected
        assertEquals(WorkflowState.APPROVED, completedRef.state.data.state)
        assertEquals("1234", completedRef.state.data.tradeId)
        assertEquals(alice, completedRef.state.data.source)
        assertEquals(bob, completedRef.state.data.counterparty)
        assertEquals(completedRef, finalFromA)
        assertEquals(completedRef, finalFromB)
    }
}
