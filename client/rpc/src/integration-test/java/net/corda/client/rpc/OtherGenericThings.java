package net.corda.client.rpc;

import net.corda.core.serialization.CordaSerializable;
import java.util.List;

@CordaSerializable
public class OtherGenericThings<S> {
    private final List<S> items;
    public OtherGenericThings(List<S> items) {
        this.items = items;
    }
    public List<S> getItems() {
        return items;
    }
}