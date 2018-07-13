package net.corda.docs

import net.corda.core.identity.Party
import net.corda.core.toFuture
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.*
import net.corda.finance.contracts.getCashBalances
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.internal.StartedNode
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetworkTest
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals

class FxTransactionBuildTutorialTest {

    companion object {
        private val mockNet: InternalMockNetwork by lazy { MockNetworkTest.mockNet }
        private val aliceNode: StartedNode<InternalMockNetwork.MockNode> by lazy { MockNetworkTest.aliceNode }
        private val bobNode: StartedNode<InternalMockNetwork.MockNode> by lazy { MockNetworkTest.bobNode }
        private val notary: Party = mockNet.defaultNotaryIdentity
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `Run ForeignExchangeFlow to completion`() {
        // Use aliceNode as issuer and create some dollars and wait for the flow to stop
        aliceNode.services.startFlow(CashIssueFlow(DOLLARS(1000),
                OpaqueBytes.of(0x01),
                notary)).resultFuture.getOrThrow()
        printBalances()

        // Using bobNode as Issuer create some pounds and wait for the flow to stop
        bobNode.services.startFlow(CashIssueFlow(POUNDS(1000),
                OpaqueBytes.of(0x01),
                notary)).resultFuture.getOrThrow()
        printBalances()

        // Setup some futures on the vaults to await the arrival of the exchanged funds at both nodes
        val aliceNodeVaultUpdate = aliceNode.services.vaultService.updates.toFuture()
        val bobNodeVaultUpdate = bobNode.services.vaultService.updates.toFuture()

        // Now run the actual Fx exchange and wait for the flow to finish
        aliceNode.services.startFlow(ForeignExchangeFlow("trade1",
                POUNDS(100).issuedBy(bobNode.info.singleIdentity().ref(0x01)),
                DOLLARS(200).issuedBy(aliceNode.info.singleIdentity().ref(0x01)),
                bobNode.info.singleIdentity(),
                weAreBaseCurrencySeller = false)).resultFuture.getOrThrow()
        // wait for the flow to finish and the vault updates to be done
        // Get the balances when the vault updates
        aliceNodeVaultUpdate.get()
        val balancesA = aliceNode.database.transaction {
            aliceNode.services.getCashBalances()
        }
        bobNodeVaultUpdate.get()
        val balancesB = bobNode.database.transaction {
            bobNode.services.getCashBalances()
        }

        println("BalanceA\n$balancesA")
        println("BalanceB\n$balancesB")
        // Verify the transfers occurred as expected
        assertEquals(POUNDS(100), balancesA[GBP])
        assertEquals(DOLLARS(1000 - 200), balancesA[USD])
        assertEquals(POUNDS(1000 - 100), balancesB[GBP])
        assertEquals(DOLLARS(200), balancesB[USD])
    }

    private fun printBalances() {
        // Print out the balances
        aliceNode.database.transaction {
            println("BalanceA\n" + aliceNode.services.getCashBalances())
        }
        bobNode.database.transaction {
            println("BalanceB\n" + bobNode.services.getCashBalances())
        }
    }
}
