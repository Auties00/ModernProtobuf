package it.auties.protobuf.serializer.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.std.CollectionDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.CollectionType;
import lombok.SneakyThrows;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

class RepeatedCollectionModule extends SimpleModule {
    public RepeatedCollectionModule(){
        setDeserializerModifier(new RepeatedCollectionDeserializerModifier());
    }

    @Override
    public String getModuleName() {
        return getClass().getSimpleName();
    }

    public static class RepeatedCollectionDeserializerModifier extends BeanDeserializerModifier {
        @Override
        public JsonDeserializer<?> modifyCollectionDeserializer(DeserializationConfig config, CollectionType type, BeanDescription beanDesc, JsonDeserializer<?> deserializer) {
            return new RepeatedCollectionDeserializer(type, deserializer);
        }
    }

    public static class RepeatedCollectionDeserializer extends StdDeserializer<Collection<Object>> implements ContextualDeserializer {
        private final ValueInstantiator valueInitiator;
        private final Map<UUID, Collection<Object>> entries;
        private JsonDeserializer<?> defaultDeserializer;

        public RepeatedCollectionDeserializer(CollectionType type, JsonDeserializer<?> defaultDeserializer) {
            super(type);
            this.defaultDeserializer = defaultDeserializer;
            this.valueInitiator = ((CollectionDeserializer) defaultDeserializer).getValueInstantiator();
            this.entries = new HashMap<>();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Collection<Object> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if(!(parser instanceof ProtobufParser protobufParser)){
                throw new IllegalArgumentException("Cannot use non-protobuf parser to deserialize a repeated collection");
            }

            if(protobufParser.lastField() == null || !protobufParser.lastField().repeated()){
                return (Collection<Object>) defaultDeserializer.deserialize(parser, context);
            }

            var entry = getEntry(protobufParser, context);
            entry.add(protobufParser.getCurrentValue());
            return entry;
        }

        @Override
        public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property) throws JsonMappingException {
            this.defaultDeserializer = ((ContextualDeserializer) defaultDeserializer).createContextual(context, property);
            return this;
        }

        @SneakyThrows
        @SuppressWarnings("unchecked")
        private Collection<Object> getEntry(ProtobufParser parser, DeserializationContext context) {
            var result = entries.get(parser.uuid());
            if(result != null){
                return result;
            }

            var entry = (Collection<Object>) valueInitiator.createUsingDefault(context);
            entries.put(parser.uuid(), entry);
            return entry;
        }
    }
}
