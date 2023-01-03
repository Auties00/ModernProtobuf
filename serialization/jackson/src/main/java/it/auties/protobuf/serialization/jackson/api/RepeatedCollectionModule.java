package it.auties.protobuf.serialization.jackson.api;

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

class RepeatedCollectionModule extends SimpleModule {
    private final RepeatedCollectionDeserializerModifier modifier;

    protected RepeatedCollectionModule(){
        this.modifier = new RepeatedCollectionDeserializerModifier();
        setDeserializerModifier(modifier);
    }

    protected Collection<Object> entries() {
        return modifier.deserializer() != null ? modifier.deserializer().entries() : null;
    }

    protected void setEntries(Collection<Object> entries) {
        if(modifier.deserializer() == null){
            return;
        }

        modifier.deserializer().setEntries(entries);
    }

    @Override
    public String getModuleName() {
        return getClass().getSimpleName();
    }

    private static class RepeatedCollectionDeserializerModifier extends BeanDeserializerModifier {
        private RepeatedCollectionDeserializer deserializer;

        @Override
        public JsonDeserializer<?> modifyCollectionDeserializer(DeserializationConfig config, CollectionType type, BeanDescription beanDesc, JsonDeserializer<?> defaultDeserializer) {
            return this.deserializer = new RepeatedCollectionDeserializer(type, defaultDeserializer);
        }

        private RepeatedCollectionDeserializer deserializer() {
            return deserializer;
        }
    }

    protected static class RepeatedCollectionDeserializer extends StdDeserializer<Collection<Object>> implements ContextualDeserializer {
        private final ValueInstantiator valueInitiator;
        private Collection<Object> entries;
        private JsonDeserializer<?> defaultDeserializer;
        private DeserializationContext context;

        public RepeatedCollectionDeserializer(CollectionType type, JsonDeserializer<?> defaultDeserializer) {
            super(type);
            this.defaultDeserializer = defaultDeserializer;
            this.valueInitiator = ((CollectionDeserializer) defaultDeserializer).getValueInstantiator();
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
            if(entries == null) {
                createEntries();
            }

            entries.add(protobufParser.getCurrentValue());
            return entries;
        }

        @Override
        public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property) throws JsonMappingException {
            this.defaultDeserializer = ((ContextualDeserializer) defaultDeserializer).createContextual(context, property);
            return this;
        }

        @SuppressWarnings("unchecked")
        protected void createEntries() throws IOException {
            this.entries = (Collection<Object>) valueInitiator.createUsingDefault(context);
        }

        private Collection<Object> entries() {
            return entries;
        }

        private void setEntries(Collection<Object> entries) {
            this.entries = entries;
        }
    }
}
