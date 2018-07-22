package net.corda.node.services.persistence

import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.services.api.NodePropertiesStore
import net.corda.node.services.api.NodePropertiesStore.FlowsDrainingModeOperations
import net.corda.node.utilities.PersistentMap
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import org.slf4j.Logger
import rx.subjects.PublishSubject
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/**
 * Simple node properties key value store in DB.
 */
class NodePropertiesPersistentStore(readPhysicalNodeId: () -> String, database: CordaPersistence) : NodePropertiesStore {
    private companion object {
        val logger = contextLogger()
    }

    override val flowsDrainingMode = FlowsDrainingModeOperationsImpl(readPhysicalNodeId, database, logger)

    fun start() {
        flowsDrainingMode.map.preload()
    }

    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}properties")
    class DBNodeProperty(
            @Id
            @Column(name = "property_key", nullable = false)
            val key: String = "",

            @Column(name = "property_value", nullable = true)
            var value: String? = ""
    )
}

class FlowsDrainingModeOperationsImpl(readPhysicalNodeId: () -> String, private val persistence: CordaPersistence, logger: Logger) : FlowsDrainingModeOperations {
    private val nodeSpecificFlowsExecutionModeKey = "${readPhysicalNodeId()}_flowsExecutionMode"

    init {
        logger.debug { "Node's flow execution mode property key: $nodeSpecificFlowsExecutionModeKey" }
    }

    internal val map = PersistentMap(
            { key -> key },
            { entity -> entity.key to entity.value!! },
            NodePropertiesPersistentStore::DBNodeProperty,
            NodePropertiesPersistentStore.DBNodeProperty::class.java
    )

    override val values = PublishSubject.create<Pair<Boolean, Boolean>>()!!

    override fun setEnabled(enabled: Boolean) {
        var oldValue: Boolean? = null
        persistence.transaction {
            oldValue = map.put(nodeSpecificFlowsExecutionModeKey, enabled.toString())?.toBoolean() ?: false
        }
        values.onNext(oldValue!! to enabled)
    }

    override fun isEnabled(): Boolean {
        return persistence.transaction {
            map[nodeSpecificFlowsExecutionModeKey]?.toBoolean() ?: false
        }
    }
}
