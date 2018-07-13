package net.corda.docs

import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.*
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.docs.java.tutorial.helloworld.IOUFlow
import net.corda.finance.*
import net.corda.finance.contracts.getCashBalances
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.internal.StartedNode
import net.corda.node.services.vault.VaultSchemaV1
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetworkTest
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.startFlow
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.Assert
import org.junit.Test
import java.util.*

class CustomVaultQueryTest {

    companion object {
        private val mockNet: InternalMockNetwork by lazy { MockNetworkTest.mockNet }
        private val aliceNode: StartedNode<InternalMockNetwork.MockNode> by lazy { MockNetworkTest.aliceNode }
        private val bobNode: StartedNode<InternalMockNetwork.MockNode> by lazy { MockNetworkTest.bobNode }
        private val notary: Party = mockNet.defaultNotaryIdentity
    }

    @Test
    fun `query by max recorded time`() {

        aliceNode.services.startFlow(IOUFlow(1000, bobNode.info.singleIdentity())).resultFuture.getOrThrow()
        aliceNode.services.startFlow(IOUFlow(500, bobNode.info.singleIdentity())).resultFuture.getOrThrow()

        val max = builder { VaultSchemaV1.VaultStates::recordedTime.max() }
        val maxCriteria = QueryCriteria.VaultCustomQueryCriteria(max)

        val results = aliceNode.database.transaction {
            val pageSpecification = PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = DEFAULT_PAGE_SIZE)
            aliceNode.services.vaultService.queryBy<ContractState>(criteria = maxCriteria, paging = pageSpecification)
        }
        assertThatCode { results.otherResults.single() }.doesNotThrowAnyException()
    }

    @Test
    fun `test custom vault query`() {
        // issue some cash in several currencies
        issueCashForCurrency(POUNDS(1000))
        issueCashForCurrency(DOLLARS(900))
        issueCashForCurrency(SWISS_FRANCS(800))
        val (cashBalancesOriginal, _) = getBalances()

        // top up all currencies (by double original)
        topUpCurrencies()
        val (cashBalancesAfterTopup, _) = getBalances()

        Assert.assertEquals(cashBalancesOriginal[GBP]?.times(2), cashBalancesAfterTopup[GBP])
        Assert.assertEquals(cashBalancesOriginal[USD]?.times(2)  , cashBalancesAfterTopup[USD])
        Assert.assertEquals(cashBalancesOriginal[CHF]?.times( 2), cashBalancesAfterTopup[CHF])
    }

    private fun issueCashForCurrency(amountToIssue: Amount<Currency>) {
        // Use aliceNode as issuer and create some dollars
        aliceNode.services.startFlow(CashIssueFlow(amountToIssue,
                OpaqueBytes.of(0x01),
                notary)).resultFuture.getOrThrow()
    }

    private fun topUpCurrencies() {
        aliceNode.services.startFlow(TopupIssuerFlow.TopupIssuanceRequester(
                aliceNode.info.singleIdentity(),
                OpaqueBytes.of(0x01),
                aliceNode.info.singleIdentity(),
                notary)).resultFuture.getOrThrow()
    }

    private fun getBalances(): Pair<Map<Currency, Amount<Currency>>, Map<Currency, Amount<Currency>>> {
        // Print out the balances
        val balancesNodesA = aliceNode.database.transaction {
            aliceNode.services.getCashBalances()
        }
        println("BalanceA\n$balancesNodesA")

        val balancesNodesB = bobNode.database.transaction {
            bobNode.services.getCashBalances()
        }
        println("BalanceB\n$balancesNodesB")

        return Pair(balancesNodesA, balancesNodesB)
    }
}