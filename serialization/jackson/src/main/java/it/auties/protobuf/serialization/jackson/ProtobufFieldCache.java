package it.auties.protobuf.serialization.jackson;

import it.auties.protobuf.base.ProtobufMessage;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
class ProtobufFieldCache {
  private static final ConcurrentMap<Class<?>, ConcurrentMap<Integer, ProtobufField>> fieldsCache
      = new ConcurrentHashMap<>();

  public ProtobufField getField(Class<? extends ProtobufMessage> type, int index) {
    return fieldsCache.get(type).get(index);
  }

  public Map<Integer, ProtobufField> cacheFields(@NonNull Class<?> clazz) {
    var cached = fieldsCache.get(clazz);
    if (cached != null) {
      return cached;
    }

    var fields = createFields(clazz);
    fieldsCache.put(clazz, fields);
    if (clazz.getSuperclass() == null || !ProtobufMessage.isMessage(clazz.getSuperclass())) {
      return fields;
    }

    fields.putAll(cacheFields(clazz.getSuperclass()));
    return fields;
  }

  private ConcurrentMap<Integer, ProtobufField> createFields(Class<?> type) {
    return Stream.of(type.getDeclaredFields(), type.getFields())
        .flatMap(Arrays::stream)
        .filter(ProtobufField::isProperty)
        .map(ProtobufField::ofRead)
        .collect(Collectors.toConcurrentMap(ProtobufField::index, Function.identity()));
  }
}
