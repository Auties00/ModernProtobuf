package it.auties.protobuf.serialization.performance;

import jdk.internal.access.SharedSecrets;
import jdk.internal.reflect.ConstantPool;
import lombok.experimental.UtilityClass;
import sun.reflect.annotation.AnnotationType;
import sun.reflect.generics.factory.CoreReflectionFactory;
import sun.reflect.generics.parser.SignatureParser;
import sun.reflect.generics.scope.ClassScope;
import sun.reflect.generics.visitor.Reifier;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Mirror from JDK, but without proxies or mismatch checks
// Needs the following options:
// --add-exports java.base/sun.reflect.annotation=ALL-UNNAMED
// --add-exports java.base/sun.reflect.generics.factory=ALL-UNNAMED
// --add-exports java.base/sun.reflect.generics.parser=ALL-UNNAMED
// --add-exports java.base/sun.reflect.generics.scope=ALL-UNNAMED
// --add-exports java.base/sun.reflect.generics.visitor=ALL-UNNAMED
// --add-exports java.base/jdk.internal.reflect=ALL-UNNAMED
// --add-exports java.base/jdk.internal.access=ALL-UNNAMED
// --add-exports java.base/sun.reflect.generics.tree=ALL-UNNAMED
// --add-opens java.base/java.lang.reflect=ALL-UNNAMED
@UtilityClass
public class Annotations {
    private static final Map<Integer, Map<Class<? extends Annotation>, Map<String, Object>>> cache = new ConcurrentHashMap<>();

    public Map<Class<? extends Annotation>, Map<String, Object>> getAnnotations(Field field) {
        try {
            var cached = cache.get(field.hashCode());
            if(cached != null){
                return cached;
            }

            var constantPool = SharedSecrets.getJavaLangAccess().getConstantPool(field.getDeclaringClass());
            var annotations = (byte[]) MethodHandles.privateLookupIn(Field.class, MethodHandles.lookup())
                    .findVarHandle(Field.class, "annotations", byte[].class)
                    .get(field);
            var result = parseAnnotation(ByteBuffer.wrap(annotations), constantPool, field.getDeclaringClass());
            cache.put(field.hashCode(), result);
            return result;
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            throw new RuntimeException("Cannot parse annotations", exception);
        }
    }

    public Map<String, Object> getAnnotation(Field field, Class<? extends Annotation> annotation) {
        return getAnnotations(field).get(annotation);
    }

    @SuppressWarnings({"unchecked"})
    private Map<Class<? extends Annotation>, Map<String, Object>> parseAnnotation(ByteBuffer buf, ConstantPool constPool, Class<?> container) {
        var numAnnotations = buf.getShort() & 0xFFFF;
        var results = new HashMap<Class<? extends Annotation>, Map<String, Object>>();
        for (int i = 0; i < numAnnotations; i++) {
            var typeIndex = buf.getShort() & 0xFFFF;
            var sig = constPool.getUTF8At(typeIndex);
            var annotationClass = (Class<? extends Annotation>)parseSig(sig, container);
            results.put(annotationClass, parseAnnotation(buf, constPool, container, annotationClass));
        }
        return results;
    }

    private Map<String, Object> parseAnnotation(ByteBuffer buf, ConstantPool constPool, Class<?> container, Class<? extends Annotation> annotationClass) {
        var type =  AnnotationType.getInstance(annotationClass);
        var memberTypes = type.memberTypes();
        var memberValues = new LinkedHashMap<>(type.memberDefaults());
        var numMembers = buf.getShort() & 0xFFFF;
        for (var i = 0; i < numMembers; i++) {
            var memberNameIndex = buf.getShort() & 0xFFFF;
            var memberName = constPool.getUTF8At(memberNameIndex);
            var memberType = memberTypes.get(memberName);
            memberValues.put(memberName, parseMemberValue(memberType, buf, constPool, container));
        }
        return memberValues;
    }

    private Object parseMemberValue(Class<?> memberType, ByteBuffer buf, ConstantPool constPool, Class<?> container) {
        var tag = buf.get();
        return switch (tag) {
            case 'e' -> parseEnumValue(memberType, buf, constPool);
            case 'c' -> parseClassValue(buf, constPool, container);
            case '@' -> parseAnnotation(buf, constPool, container);
            case '[' -> parseArray(memberType, buf, constPool, container);
            default -> parseConst(tag, buf, constPool);
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object parseEnumValue(Class<?> enumType, ByteBuffer buf, ConstantPool constPool) {
        var typeNameIndex = buf.getShort() & 0xFFFF;
        constPool.getUTF8At(typeNameIndex);
        var constNameIndex = buf.getShort() & 0xFFFF;
        var constName = constPool.getUTF8At(constNameIndex);
        return Enum.valueOf((Class<? extends Enum>) enumType, constName);
    }

    private Class<?> parseClassValue(ByteBuffer buf, ConstantPool constPool, Class<?> container) {
        var classIndex = buf.getShort() & 0xFFFF;
        var sig = constPool.getUTF8At(classIndex);
        return parseSig(sig, container);
    }

    @SuppressWarnings("unchecked")
    private Object parseArray(Class<?> arrayType, ByteBuffer buf, ConstantPool constPool, Class<?> container) {
        var length = buf.getShort() & 0xFFFF;
        var componentType = arrayType.getComponentType();
        if (componentType == byte.class) {
            return parseByteArray(length, buf, constPool);
        } else if (componentType == char.class) {
            return parseCharArray(length, buf, constPool);
        } else if (componentType == double.class) {
            return parseDoubleArray(length, buf, constPool);
        } else if (componentType == float.class) {
            return parseFloatArray(length, buf, constPool);
        } else if (componentType == int.class) {
            return parseIntArray(length, buf, constPool);
        } else if (componentType == long.class) {
            return parseLongArray(length, buf, constPool);
        } else if (componentType == short.class) {
            return parseShortArray(length, buf, constPool);
        } else if (componentType == boolean.class) {
            return parseBooleanArray(length, buf, constPool);
        } else if (componentType == String.class) {
            return parseStringArray(length, buf, constPool);
        } else if (componentType == Class.class) {
            return parseClassArray(length, buf, constPool, container);
        } else if (componentType.isEnum()) {
            return parseEnumArray(length, (Class<? extends Enum<?>>)componentType, buf, constPool, container);
        } else if (componentType.isAnnotation()) {
            return parseAnnotationArray(length, (Class <? extends Annotation>)componentType, buf, constPool, container);
        } else {
            throw new IllegalArgumentException();
        }
    }

    private Object parseByteArray(int length, ByteBuffer buf, ConstantPool constPool) {
        var result = new byte[length];
        for (var i = 0; i < length; i++) {
            var index = buf.getShort() & 0xFFFF;
            result[i] = (byte) constPool.getIntAt(index);
        }
        return result;
    }

    private Object parseCharArray(int length, ByteBuffer buf, ConstantPool constPool) {
        var result = new char[length];
        for (var i = 0; i < length; i++) {
            int index = buf.getShort() & 0xFFFF;
            result[i] = (char) constPool.getIntAt(index);
        }

        return result;
    }

    private Object parseDoubleArray(int length, ByteBuffer buf, ConstantPool constPool) {
        var result = new double[length];
        for (var i = 0; i < length; i++) {
            int index = buf.getShort() & 0xFFFF;
            result[i] = constPool.getDoubleAt(index);
        }

        return result;
    }

    private Object parseFloatArray(int length, ByteBuffer buf, ConstantPool constPool) {
        var result = new float[length];
        for (var i = 0; i < length; i++) {
            int index = buf.getShort() & 0xFFFF;
            result[i] = constPool.getFloatAt(index);
        }

        return result;
    }

    private Object parseIntArray(int length, ByteBuffer buf, ConstantPool constPool) {
        var result = new int[length];
        for (var i = 0; i < length; i++) {
            int index = buf.getShort() & 0xFFFF;
            result[i] = constPool.getIntAt(index);
        }

        return result;
    }

    private Object parseLongArray(int length, ByteBuffer buf, ConstantPool constPool) {
        var result = new long[length];
        for (var i = 0; i < length; i++) {
            var index = buf.getShort() & 0xFFFF;
            result[i] = constPool.getLongAt(index);
        }

        return result;
    }

    private Object parseShortArray(int length, ByteBuffer buf, ConstantPool constPool) {
        var result = new short[length];
        for (var i = 0; i < length; i++) {
            var index = buf.getShort() & 0xFFFF;
            result[i] = (short) constPool.getIntAt(index);
        }

        return result;
    }

    private Object parseBooleanArray(int length, ByteBuffer buf, ConstantPool constPool) {
        var result = new boolean[length];
        for (var i = 0; i < length; i++) {
            var index = buf.getShort() & 0xFFFF;
            result[i] = constPool.getIntAt(index) != 0;
        }

        return result;
    }

    private Object parseStringArray(int length, ByteBuffer buf,  ConstantPool constPool) {
        var result = new String[length];
        for (var i = 0; i < length; i++) {
            var index = buf.getShort() & 0xFFFF;
            result[i] = constPool.getUTF8At(index);
        }

        return result;
    }

    private Object parseClassArray(int length, ByteBuffer buf, ConstantPool constPool, Class<?> container) {
        var result = new Class<?>[length];
        for (var i = 0; i < result.length; i++) {
            var value = parseClassValue(buf, constPool, container);
            result[i] = value;
        }
        return result;
    }

    private Object parseEnumArray(int length, Class<? extends Enum<?>> enumType, ByteBuffer buf, ConstantPool constPool, Class<?> container) {
        var result = (Object[]) Array.newInstance(enumType, length);
        for (var i = 0; i < result.length; i++) {
            var value = parseClassValue(buf, constPool, container);
            result[i] = value;
        }
        return result;
    }

    private Object parseAnnotationArray(int length, Class<? extends Annotation> annotationType, ByteBuffer buf, ConstantPool constPool, Class<?> container) {
        var result = (Object[]) Array.newInstance(annotationType, length);
        for (var i = 0; i < result.length; i++) {
            var value = parseAnnotation(buf, constPool, container);
            result[i] = value;
        }
        return result;
    }

    private static Object parseConst(int tag, ByteBuffer buf, ConstantPool constPool) {
        var constIndex = buf.getShort() & 0xFFFF;
        return switch (tag) {
            case 'B' -> (byte) constPool.getIntAt(constIndex);
            case 'C' -> (char) constPool.getIntAt(constIndex);
            case 'D' -> constPool.getDoubleAt(constIndex);
            case 'F' -> constPool.getFloatAt(constIndex);
            case 'I' -> constPool.getIntAt(constIndex);
            case 'J' -> constPool.getLongAt(constIndex);
            case 'S' -> (short) constPool.getIntAt(constIndex);
            case 'Z' -> constPool.getIntAt(constIndex) != 0;
            case 's' -> constPool.getUTF8At(constIndex);
            default -> throw new IllegalStateException("Unexpected value: " + tag);
        };
    }

    private Class<?> parseSig(String sig, Class<?> container) {
        if (sig.equals("V")) return void.class;
        var parser = SignatureParser.make();
        var typeSig = parser.parseTypeSig(sig);
        var factory = CoreReflectionFactory.make(container, ClassScope.make(container));
        var reify = Reifier.make(factory);
        typeSig.accept(reify);
        return (Class<?>) reify.getResult();
    }
}
