package it.auties.protobuf.serialization.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.std.CollectionDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.CollectionType;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class RepeatedCollectionModule extends SimpleModule {
    protected RepeatedCollectionModule(){
        setDeserializerModifier(new RepeatedCollectionDeserializerModifier());
    }

    @Override
    public String getModuleName() {
        return getClass().getSimpleName();
    }

    private static class RepeatedCollectionDeserializerModifier extends BeanDeserializerModifier {
        @Override
        public JsonDeserializer<?> modifyCollectionDeserializer(DeserializationConfig config, CollectionType type, BeanDescription beanDesc, JsonDeserializer<?> defaultDeserializer) {
            return new RepeatedCollectionDeserializer(type, defaultDeserializer);
        }
    }

    protected static class RepeatedCollectionDeserializer extends StdDeserializer<Collection<Object>> implements ContextualDeserializer {
        private final ValueInstantiator valueInitiator;
        private final Map<Long, Collection<Object>> entriesMap;
        private JsonDeserializer<?> defaultDeserializer;
        private DeserializationContext context;

        public RepeatedCollectionDeserializer(CollectionType type, JsonDeserializer<?> defaultDeserializer) {
            super(type);
            this.defaultDeserializer = defaultDeserializer;
            this.valueInitiator = ((CollectionDeserializer) defaultDeserializer).getValueInstantiator();
            this.entriesMap = new ConcurrentHashMap<>();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Collection<Object> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!(parser instanceof ProtobufParser protobufParser)) {
                throw new IllegalArgumentException("Cannot use non-protobuf parser to deserialize a repeated collection");
            }

            if (protobufParser.lastField() == null || !protobufParser.lastField().repeated()) {
                return (Collection<Object>) defaultDeserializer.deserialize(parser, context);
            }

            this.context = context;
            var entries = getEntries(protobufParser.id());
            entries.add(protobufParser.getCurrentValue());
            entriesMap.put(protobufParser.id(), entries);
            return entries;
        }

        @SuppressWarnings("unchecked")
        private Collection<Object> getEntries(long id) throws IOException {
            var entries = entriesMap.get(id);
            if (entries != null) {
                return entries;
            }

            var newEntries =  (Collection<Object>) valueInitiator.createUsingDefault(context);
            entriesMap.put(id, newEntries);
            return newEntries;
        }

        @Override
        public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property) throws JsonMappingException {
            this.defaultDeserializer = ((ContextualDeserializer) defaultDeserializer).createContextual(context, property);
            return this;
        }
    }
}
