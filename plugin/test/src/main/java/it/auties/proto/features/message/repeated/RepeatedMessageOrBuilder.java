package it.auties.proto.features.message.repeated;

import java.util.List;

public interface RepeatedMessageOrBuilder extends com.google.protobuf.MessageLiteOrBuilder {
    List<Integer> getContentList();

    int getContentCount();

    int getContent(int index);
}
