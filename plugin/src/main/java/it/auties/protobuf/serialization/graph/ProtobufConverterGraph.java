package it.auties.protobuf.serialization.graph;

import it.auties.protobuf.serialization.generator.method.ProtobufMethodGenerator;
import it.auties.protobuf.serialization.model.converter.ProtobufConverterMethod;
import it.auties.protobuf.serialization.support.Types;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

// Let's consider the following transformations:
//   - byte[] -> Message(annotated with @ProtobufMessage -> no conversions required)
//     The path resolver works as described here:
//       - receives as the expected input the byte[] type and as the expected out the Message type
//       - reaches a Node whose arc is a transformer that takes byte[] and returns Message
//       - determines that Message is assignable to Message
//       - returns the path: byte[]->Message
//     This case is very simple as it requires a simple step and no generics are used
//
//   - ProtobufString -> Message(contains a @ProtobufDeserializer that takes a String)
//     The path resolver works as described here:
//       - receives as the expected input the ProtobufString type and as the expected out the Message type
//       - reaches a Node whose arc is a transformer that takes ProtobufString and returns String
//       - determines that String is not assignable to Message
//       - reaches a Node whose arc is a transformer that takes String and returns Message
//       - determines that Message is assignable to Message
//       - returns the complete path: ProtobufString->String->Message
//     This case is more complex as it requires multiple steps, but no generics are used.
//
//   - byte[] -> CompletableFuture<Message>
//     The path resolver works as described here:
//       - receives as input byte[] and CompletableFuture<Message>
//       - reaches a Node whose arc is a transformer that takes byte[] and returns Message
//       - determines that Message is not assignable to CompletableFuture<Message>
//       - starts resolving a new path from Message to CompletableFuture<Message>
//       - reaches a Node whose arc is a transformer that takes T and returns CompletableFuture<T>
//       - resolves the generic signature of the transformer as Message->CompletableFuture<Message>
//       - determines that CompletableFuture<Message> is assignable to CompletableFuture<Message>
//       - returns the complete path: byte[]->Message->CompletableFuture<Message>
//     This case is again more complex as it requires two steps and generics are used.
//
//   - byte[] -> CompletableFuture<CompletableFuture<Message>>
//     The path resolver works as described here:
//       - receives as input byte[] and CompletableFuture<Message>
//       - reaches a Node whose arc is a transformer that takes byte[] and returns Message
//       - determines that Message is not assignable to CompletableFuture<CompletableFuture<Message>>
//       - starts resolving a new path from Message to CompletableFuture<CompletableFuture<Message>>
//       - reaches a Node whose arc is a transformer that takes T and returns CompletableFuture<T>
//       - resolves the generic signature of the transformer as Message->CompletableFuture<Message>
//       - determines that CompletableFuture<Message> is not assignable to CompletableFuture<CompletableFuture<Message>>
//       - reaches again a Node whose arc is a transformer that takes T and returns CompletableFuture<T>
//       - resolves the generic signature of the transformer as CompletableFuture<Message>->CompletableFuture<CompletableFuture<Message>>
//       - determines that CompletableFuture<CompletableFuture<Message>> is assignable to CompletableFuture<CompletableFuture<Message>>
//       - returns the complete path: byte[]->Message->CompletableFuture<Message>
//
//   - byte[] -> CompletableFuture<CompletableFuture<String>>
//      In this case no valid path exists, but the path resolver has to terminate in a finite amount of time.
public final class ProtobufConverterGraph {
    private final Types types;
    private final Set<Node> nodes;
    private int maxTypeArguments;
    public ProtobufConverterGraph(Types types) {
        this.types = types;
        this.nodes = new HashSet<>();
    }

    public void link(TypeMirror from, TypeMirror to, TypeMirror rawGroupOwner, ProtobufConverterMethod arc) {
        link(from, to, rawGroupOwner, arc, "");
    }

    public void link(TypeMirror from, TypeMirror to, TypeMirror rawGroupOwner, ProtobufConverterMethod arc, String warning) {
        var node = new Node(from, to, arc, rawGroupOwner, warning);
        if (!nodes.add(node)) {
            return;
        }

        var fromGenericLevel = countTypeArguments(from);
        if (fromGenericLevel > maxTypeArguments) {
            this.maxTypeArguments = fromGenericLevel;
        }

        var toGenericLevel = countTypeArguments(to);
        if (toGenericLevel > maxTypeArguments) {
            this.maxTypeArguments = toGenericLevel;
        }
    }

    public List<ProtobufConverterArc> findPath(TypeMirror from, TypeMirror to, List<TypeElement> mixins) {
        var mixinsSet = Set.copyOf(mixins);
        return findPath(from, from, to, mixinsSet);
    }

    // Finds a path using a Node from currentTo to currentFrom with a set of mixins
    private List<ProtobufConverterArc> findPath(Node node, TypeMirror originalFrom, TypeMirror currentFrom, TypeMirror currentTo, Set<TypeElement> mixins) {
        var arc = new ProtobufConverterArc(node.arc(), node.arc().returnType(), node.warning());
        if (types.isAssignable(currentTo, node.to()) && isPathLegal(node, originalFrom, currentFrom, currentTo, mixins)) {
            if (node.arc().parametrized()) {
                return findParametrizedPath(node, currentFrom, currentTo, mixins);
            } else {
                return List.of(arc);
            }
        } else {
            if (node.arc().parametrized()) {
                return findParametrizedPath(node, currentFrom, currentTo, mixins);
            }else {
                var nested = findPath(currentFrom, node.to(), currentTo, mixins);
                if (nested.isEmpty()) {
                    return List.of();
                }

                if (node.rawGroupOwner() != null && !isPathLegal(node, currentFrom, currentFrom, currentTo, mixins)) {
                    return List.of();
                }

                return ProtobufConverterArcs.of(arc, nested);
            }
        }
    }

    private List<ProtobufConverterArc> findParametrizedPath(Node node, TypeMirror currentFrom, TypeMirror currentTo, Set<TypeElement> mixins) {
        var returnType = node.arc()
                .element()
                .map(element -> types.getReturnType(element, List.of(currentFrom)))
                .orElse(node.arc().returnType());
        var arc = new ProtobufConverterArc(node.arc(), returnType, node.warning());
        if (types.isAssignable(currentTo, returnType, false)) {
            return List.of(arc);
        }

        var length = countTypeArguments(returnType);
        if (length > maxTypeArguments && length > countTypeArguments(currentTo) && length > countTypeArguments(currentFrom)) {
            return List.of();
        }

        var nested = findPath(currentFrom, returnType, currentTo, mixins);
        if (nested.isEmpty()) {
            return List.of();
        }

        if (node.rawGroupOwner() != null && !isPathLegal(node, currentFrom, currentFrom, currentTo, mixins)) {
            return List.of();
        }

        return ProtobufConverterArcs.of(arc, nested);
    }

    private final ConcurrentMap<PathOwner, List<ProtobufConverterArc>> path = new ConcurrentHashMap<>();

    private List<ProtobufConverterArc> findPath(TypeMirror originalFrom, TypeMirror currentFrom, TypeMirror currentTo, Set<TypeElement> mixins) {
        var owner = new PathOwner(originalFrom, currentFrom, currentTo, mixins);
        var cached = path.get(owner);
        if(cached != null) {
            return cached;
        }

        // FIXME: use parallelStream instead of stream
        //        for some reason this breaks compilation, even without a cache
        //        there is probably something not thread safe somewhere
        var result = nodes.stream()
                .map(entry -> {
                    if(!types.isAssignable(currentFrom, entry.from())) {
                        return null;
                    }

                    var value = findPath(entry, originalFrom, currentFrom, currentTo, mixins);
                    if(value.isEmpty()) {
                        return null;
                    }

                    return value;
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(List.of());
        path.put(owner, result);
        return result;
    }

    // Checks whether the call necessary to walk this path is legal
    // Doesn't check anything type related(i.e. is the conversion from->to compatible with the node's arc)
    private boolean isPathLegal(Node node, TypeMirror originalFrom, TypeMirror from, TypeMirror to, Set<TypeElement> mixins) {
        return isPathLegalThroughMixin(node, mixins)
                || isPathLegalThroughInterpretedObject(node, from)
                || isPathLegalThroughInterpretedObject(node, to)
                || isPathLegalThroughObject(node, from)
                || isPathLegalThroughObject(node, to)
                || isPathLegalThroughGroup(node, originalFrom, to);
    }

    // Checks whether the node's arc is inside a mixin
    // This check is necessary because even if a transformation is legal, if it comes from a mixin, it needs to be included in the mixins property of the annotation
    private boolean isPathLegalThroughMixin(Node node, Set<TypeElement> mixins) {
        return mixins.stream()
                .anyMatch(mixin -> Objects.equals(node.arc().ownerName(), types.erase(mixin.asType()).toString()));
    }

    // Checks whether the node's arc is inside the provided type
    // This check is necessary because non-object messages can be interpreted as messages if they contain a @ProtobufSerializer and @ProtobufDeserializer
    private boolean isPathLegalThroughInterpretedObject(Node node, TypeMirror type) {
        return isSameType(type, node.arc().ownerName());
    }

    // Checks whether the node's arc is inside the spec of the provided type
    // This check is necessary because objects can be converted from/to byte[] using decode/encode methods in the corresponding object spec class
    private boolean isPathLegalThroughObject(Node node, TypeMirror type) {
        return types.isObject(type)
                && Objects.equals(ProtobufMethodGenerator.getSpecFromObject(type), node.arc().ownerName());
    }

    // Checks whether the node's arc is inside the spec of the raw group type
    // This check is necessary because groups can be converted from/to Map<Integer, Object> using decode/encode methods in the corresponding raw group spec class
    private boolean isPathLegalThroughGroup(Node node, TypeMirror originalFrom, TypeMirror to) {
        return isSameType(originalFrom, node.rawGroupOwner())
                || isSameType(to, node.rawGroupOwner());
    }

    // Checks if firstType's qualified name matches secondType's
    private boolean isSameType(TypeMirror firstType, TypeMirror secondType) {
        return  secondType instanceof DeclaredType secondDeclaredType
                && secondDeclaredType.asElement() instanceof TypeElement secondTypeElement
                && isSameType(firstType, secondTypeElement.getQualifiedName());
    }

    // Checks if firstType's qualified name matches secondTypeQualifiedName
    private boolean isSameType(TypeMirror firstType, CharSequence secondTypeQualifiedName) {
        return secondTypeQualifiedName != null
                && firstType instanceof DeclaredType firstDeclaredType
                && firstDeclaredType.asElement() instanceof TypeElement firstTypeElement
                && firstTypeElement.getQualifiedName().contentEquals(secondTypeQualifiedName);
    }

    // Counts the number of type arguments(not parameters) in a type
    private int countTypeArguments(TypeMirror type) {
        var counter = 0;
        var queue = new LinkedList<TypeMirror>();
        queue.add(type);
        while (!queue.isEmpty()) {
            type = queue.pop();
            if (type.getKind() != TypeKind.TYPEVAR && type instanceof DeclaredType declaredType) {
                var args = declaredType.getTypeArguments();
                counter += args.size();
                queue.addAll(args);
            }
        }
        return counter;
    }

    // A node in the graph
    private record Node(
            TypeMirror from,
            TypeMirror to,
            ProtobufConverterMethod arc,
            TypeMirror rawGroupOwner,
            String warning
    ) {
        @Override
        public boolean equals(Object obj) {
            return obj instanceof Node that
                    && Objects.equals(from.toString(), that.from().toString())
                    && Objects.equals(to.toString(), that.to().toString())
                    && Objects.equals(arc, that.arc());
        }

        @Override
        public int hashCode() {
            return Objects.hash(from.toString(), to.toString(), arc);
        }
    }

    private static final class PathOwner {
        private final TypeMirror originalFrom;
        private final TypeMirror currentFrom;
        private final TypeMirror currentTo;
        private final Set<String> mixins;
        private final int hashCode;

        private PathOwner(
                TypeMirror originalFrom,
                TypeMirror currentFrom,
                TypeMirror currentTo,
                Set<TypeElement> mixins
        ) {
            this.originalFrom = originalFrom;
            this.currentFrom = currentFrom;
            this.currentTo = currentTo;
            this.mixins = mixins.stream()
                    .map(Objects::toString)
                    .collect(Collectors.toUnmodifiableSet());
            var hashCode = 1;
            hashCode = 31 * hashCode + originalFrom.toString().hashCode();
            hashCode = 31 * hashCode + currentFrom.toString().hashCode();
            hashCode = 31 * hashCode + currentTo.toString().hashCode();
            for(var mixin : this.mixins) {
                hashCode = 31 * hashCode + mixin.hashCode();
            }
            this.hashCode = hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof PathOwner that
                    && Objects.equals(originalFrom.toString(), that.originalFrom.toString())
                    && Objects.equals(currentFrom.toString(), that.currentFrom.toString())
                    && Objects.equals(currentTo.toString(), that.currentTo.toString())
                    && that.mixins.containsAll(mixins);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
