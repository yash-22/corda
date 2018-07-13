package net.corda.core.flows

import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.issuedBy
import net.corda.node.internal.StartedNode
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetworkTest
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.startFlow
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FinalityFlowTests {

    companion object {
        private val CHARLIE = TestIdentity(CHARLIE_NAME, 90).party
        private val mockNet: InternalMockNetwork by lazy { MockNetworkTest.mockNet }
        private val aliceNode: StartedNode<InternalMockNetwork.MockNode> by lazy { MockNetworkTest.aliceNode }
        private val bobNode: StartedNode<InternalMockNetwork.MockNode> by lazy { MockNetworkTest.bobNode }

        private lateinit var alice: Party
        private lateinit var bob: Party
        private lateinit var notary: Party

        @BeforeClass
        @JvmStatic
        fun setup() {
            alice = aliceNode.info.singleIdentity()
            bob = bobNode.info.singleIdentity()
            notary = mockNet.defaultNotaryIdentity
        }
    }

    @Test
    fun `finalise a simple transaction`() {
        val amount = 1000.POUNDS.issuedBy(alice.ref(0))
        val builder = TransactionBuilder(notary)
        Cash().generateIssue(builder, amount, bob, notary)
        val stx = aliceNode.services.signInitialTransaction(builder)
        val flow = aliceNode.services.startFlow(FinalityFlow(stx))
        mockNet.runNetwork()
        val notarisedTx = flow.resultFuture.getOrThrow()
        notarisedTx.verifyRequiredSignatures()
        val transactionSeenByB = bobNode.database.transaction {
            bobNode.services.validatedTransactions.getTransaction(notarisedTx.id)
        }
        assertEquals(notarisedTx, transactionSeenByB)
    }

    @Test
    fun `reject a transaction with unknown parties`() {
        val amount = 1000.POUNDS.issuedBy(alice.ref(0))
        val fakeIdentity = CHARLIE // Charlie isn't part of this network, so node A won't recognise them
        val builder = TransactionBuilder(notary)
        Cash().generateIssue(builder, amount, fakeIdentity, notary)
        val stx = aliceNode.services.signInitialTransaction(builder)
        val flow = aliceNode.services.startFlow(FinalityFlow(stx))
        mockNet.runNetwork()
        assertFailsWith<IllegalArgumentException> {
            flow.resultFuture.getOrThrow()
        }
    }
}