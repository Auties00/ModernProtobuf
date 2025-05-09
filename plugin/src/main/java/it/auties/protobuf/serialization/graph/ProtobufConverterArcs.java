package it.auties.protobuf.serialization.graph;

import java.util.AbstractList;
import java.util.List;

final class ProtobufConverterArcs extends AbstractList<ProtobufConverterArc> {
    private final ProtobufConverterArc head;
    private final List<ProtobufConverterArc> tail;

    private ProtobufConverterArcs(ProtobufConverterArc head, List<ProtobufConverterArc> tail) {
        this.head = head;
        this.tail = tail;
    }

    static List<ProtobufConverterArc> of(ProtobufConverterArc head, List<ProtobufConverterArc> tail) {
        return new ProtobufConverterArcs(head, tail);
    }

    @Override
    public ProtobufConverterArc get(int index) {
        if (index == 0) {
            return head;
        } else {
            return tail.get(index - 1);
        }
    }

    @Override
    public int size() {
        return 1 + tail.size();
    }
}
