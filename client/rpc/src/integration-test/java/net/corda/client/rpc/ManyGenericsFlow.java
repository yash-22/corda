package net.corda.client.rpc;

import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.StartableByRPC;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

@StartableByRPC
public class ManyGenericsFlow extends FlowLogic<OtherGenericThings<String>> {

    private final OtherGenericThings<String> input;

    public ManyGenericsFlow(OtherGenericThings<String> input) {
        this.input = input;
    }

    @Override
    public OtherGenericThings<String> call() {
        return new OtherGenericThings<>(IntStream.of(100).mapToObj((i) -> input.toString() + "" + i).collect(Collectors.toList()));
    }
}