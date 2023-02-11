package it.auties.protobuf.serialization.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.impl.JDKValueInstantiators;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.io.IOException;
import java.util.Collection;

class RepeatedCollectionModule extends SimpleModule {
    protected RepeatedCollectionModule(){
        setDeserializerModifier(new CollectionModifier());
    }

    @Override
    public String getModuleName() {
        return getClass().getSimpleName();
    }

    private static class CollectionModifier extends BeanDeserializerModifier {
        @Override
        public JsonDeserializer<?> modifyCollectionDeserializer(DeserializationConfig config, CollectionType type, BeanDescription beanDesc, JsonDeserializer<?> defaultDeserializer) {
            return new CollectionDeserializer(config, type, defaultDeserializer);
        }
    }

    protected static class CollectionDeserializer extends StdDeserializer<Collection<Object>> implements ContextualDeserializer {
        private final ValueInstantiator valueInitiator;
        private JsonDeserializer<?> defaultDeserializer;

        public CollectionDeserializer(DeserializationConfig config, CollectionType type, JsonDeserializer<?> defaultDeserializer) {
            super(type);
            this.defaultDeserializer = defaultDeserializer;
            this.valueInitiator = JDKValueInstantiators.findStdValueInstantiator(config, type.getRawClass());
        }

        @Override
        @SuppressWarnings("unchecked")
        public Collection<Object> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!(parser instanceof ProtobufParser protobufParser)) {
                throw new IllegalArgumentException("Cannot use non-protobuf parser to deserialize a repeated collection");
            }

            var field = protobufParser.lastField();
            if (field == null || !field.repeated()) {
                return (Collection<Object>) defaultDeserializer.deserialize(parser, context);
            }

            var attribute = getCollection(context, field);
            attribute.add(protobufParser.getCurrentValue());
            return attribute;
        }

        @SuppressWarnings("unchecked")
        private Collection<Object> getCollection(DeserializationContext context, ProtobufField field) throws IOException {
            var attribute = (Collection<Object>) context.getAttribute(field.name());
            if (attribute != null) {
                return attribute;
            }

            var collection = (Collection<Object>) valueInitiator.createUsingDefault(context);
            context.setAttribute(field.name(), collection);
            return collection;
        }

        @Override
        public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property) throws JsonMappingException {
            this.defaultDeserializer = ((ContextualDeserializer) defaultDeserializer).createContextual(context, property);
            return this;
        }
    }
}
