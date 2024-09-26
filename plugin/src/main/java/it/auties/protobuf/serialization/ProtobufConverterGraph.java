package it.auties.protobuf.serialization;

import it.auties.protobuf.serialization.generator.method.ProtobufMethodGenerator;
import it.auties.protobuf.serialization.support.Types;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.stream.Collectors;

public class ProtobufConverterGraph {
    private final Types types;
    private final Set<Node> nodes;
    private int maxGenericLevel;
    public ProtobufConverterGraph(Types types) {
        this.types = types;
        this.nodes = new HashSet<>();
    }

    public void link(TypeMirror from, TypeMirror to, TypeMirror rawGroupParent, ExecutableElement arc) {
        var node = new Node(from, to, rawGroupParent, arc);
        nodes.add(node);
        this.maxGenericLevel = Math.max(maxGenericLevel, count(from));
    }

    private int count(TypeMirror type) {
        if(type.getKind() == TypeKind.TYPEVAR || !(type instanceof DeclaredType declaredType)) {
            return 0;
        }

        var counter = 0;
        for(var param : declaredType.getTypeArguments()) {
            counter += 1 + count(param);
        }

        return counter;
    }

    public List<Element> get(TypeMirror from, TypeMirror to, List<TypeElement> mixins) {
        var mixinsNames = mixins.stream()
                .map(entry -> entry.getQualifiedName().toString())
                .collect(Collectors.toUnmodifiableSet());
        return get(from, from, to, mixinsNames);
    }

    private List<Element> get(TypeMirror originalFrom, TypeMirror currentFrom, TypeMirror currentTo, Set<String> mixins) {
        for(var entry : nodes) {
            if (!types.isAssignable(currentFrom, entry.from())) {
                continue;
            }

            if (isArcIllegal(originalFrom, currentFrom, currentTo, mixins, entry) || !types.isAssignable(currentTo, entry.to())) {
                var results = get(currentFrom, currentTo, mixins, entry, null);
                if (results.isEmpty()) {
                    continue;
                }

                return results;
            }

            if (!types.isParametrized(entry.arc())) {
                return List.of(new Element(entry.arc()));
            }

            var returnType = types.getReturnType(entry.arc(), List.of(currentFrom));
            if (types.isAssignable(currentTo, returnType, false)) {
                return List.of(new Element(entry.arc(), returnType));
            }

            var results = get(currentFrom, currentTo, mixins, entry, returnType);
            if (!results.isEmpty()) {
                return results;
            }
        }

        return List.of();
    }

    private boolean isArcIllegal(TypeMirror originalFrom, TypeMirror from, TypeMirror to, Set<String> mixins, Node entry) {
        var arcOwnerQualifiedName = getDeclaredTypeName(entry.arc().getEnclosingElement().asType());
        if(arcOwnerQualifiedName == null) {
            return true;
        }

        if(mixins.contains(arcOwnerQualifiedName)) {
            return false;
        }

        var fromQualifiedName = getDeclaredTypeName(from);
        var fromQualifiedSpecName = types.isObject(from) ? ProtobufMethodGenerator.getSpecFromObject(from) : null;
        if(Objects.equals(arcOwnerQualifiedName, fromQualifiedName) || Objects.equals(arcOwnerQualifiedName, fromQualifiedSpecName)) {
            return false;
        }

        var toQualifiedName = getDeclaredTypeName(to);
        var toQualifiedSpecName = types.isObject(to) ? ProtobufMethodGenerator.getSpecFromObject(to) : null;
        if (Objects.equals(arcOwnerQualifiedName, toQualifiedName) || Objects.equals(arcOwnerQualifiedName, toQualifiedSpecName)) {
            return false;
        }

        var rawGroupOwner = entry.rawGroupOwner();
        if(rawGroupOwner == null) {
            return true;
        }

        var originalFromQualifiedName = getDeclaredTypeName(originalFrom);
        var rawGroupQualifiedName = getDeclaredTypeName(rawGroupOwner);
        return !Objects.equals(originalFromQualifiedName, rawGroupQualifiedName)
                && !Objects.equals(toQualifiedName, rawGroupQualifiedName);
    }

    private String getDeclaredTypeName(TypeMirror mirror) {
        if (mirror instanceof DeclaredType declaredType && declaredType.asElement() instanceof TypeElement typeElement) {
            return typeElement.getQualifiedName().toString();
        }

        return null;
    }

    private List<Element> get(TypeMirror from, TypeMirror to, Set<String> mixins, Node entry, TypeMirror genericReturnType) {
        if (!types.isParametrized(entry.arc())) {
            var nested = get(from, entry.to(), to, mixins);
            if (nested.isEmpty()) {
                return List.of();
            }

            if(entry.rawGroupOwner() != null && isArcIllegal(from, from, to, mixins, entry)) {
                return List.of();
            }

            return getSteps(entry, entry.arc().getReturnType(), nested);
        }

        var returnType = genericReturnType != null ? genericReturnType : types.getReturnType(entry.arc(), List.of(from));
        if (count(returnType) > maxGenericLevel) {
            return List.of();
        }

        var nested = get(from, returnType, to, mixins);
        if (nested.isEmpty()) {
            return List.of();
        }

        if(entry.rawGroupOwner() != null && isArcIllegal(from, from, to, mixins, entry)) {
            return List.of();
        }

        return getSteps(entry, returnType, nested);
    }

    private ArrayList<Element> getSteps(Node entry, TypeMirror returnType, List<Element> nested) {
        var results = new ArrayList<Element>();
        results.add(new Element(entry.arc(), returnType));
        results.addAll(nested);
        return results;
    }

    public record Element(ExecutableElement method, TypeMirror returnType) {
        public Element(ExecutableElement method) {
            this(method, method.getReturnType());
        }
    }

    private record Node(TypeMirror from, TypeMirror to, TypeMirror rawGroupOwner, ExecutableElement arc) {
        @Override
        public int hashCode() {
            return (from + "_" + to).hashCode();
        }
    }
}
