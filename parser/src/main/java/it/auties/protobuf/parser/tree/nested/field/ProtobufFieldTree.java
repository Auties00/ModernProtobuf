package it.auties.protobuf.parser.tree.nested.field;

import it.auties.protobuf.parser.tree.ProtobufNamedTree;
import it.auties.protobuf.parser.tree.nested.ProtobufNestedTree;
import it.auties.protobuf.parser.tree.nested.option.ProtobufOptionTree;
import it.auties.protobuf.parser.tree.nested.option.ProtobufOptionedTree;

import java.util.*;

public abstract sealed class ProtobufFieldTree extends ProtobufNestedTree implements ProtobufNamedTree, ProtobufOptionedTree permits ProtobufEnumConstantTree, ProtobufGroupableFieldTree {
    protected String name;
    protected Integer index;
    protected final LinkedHashMap<String, ProtobufOptionTree> options;
    private ProtobufOptionTree lastOption;
    protected ProtobufFieldTree() {
        this.options = new LinkedHashMap<>();
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    @Override
    public Collection<ProtobufOptionTree> options() {
        return Collections.unmodifiableCollection(options.values());
    }

    @Override
    public ProtobufFieldTree addOption(String name) {
        var option = new ProtobufOptionTree(name);
        options.put(name, option);
        this.lastOption = option;
        return this;
    }

    @Override
    public Optional<ProtobufOptionTree> lastOption() {
        return Optional.ofNullable(lastOption);
    }

    public ProtobufFieldTree setName(String name) {
        this.name = name;
        return this;
    }

    public OptionalInt index() {
        return index == null ? OptionalInt.empty() : OptionalInt.of(index);
    }

    public ProtobufFieldTree setIndex(Integer index) {
        this.index = index;
        return this;
    }
    
    @Override
    public boolean isAttributed() {
        return index != null && name != null;
    }

    public static sealed abstract class Modifier {
        public static Modifier of(String name) {
            if(name.equals(Required.INSTANCE.token())) {
                return Required.INSTANCE;
            }

            if(name.equals(Optional.INSTANCE.token())) {
                return Optional.INSTANCE;
            }

            if(name.equals(Repeated.INSTANCE.token())) {
                return Repeated.INSTANCE;
            }

            return new MaybeNothing(name);
        }

        public abstract String token();

        public abstract Type type();

        @Override
        public int hashCode() {
            return Objects.hashCode(token());
        }

        @Override
        public String toString() {
            return token();
        }

        public static final class MaybeNothing extends Modifier {
            private final String token;
            private MaybeNothing(String token) {
                this.token = token;
            }

            @Override
            public String token() {
                return token;
            }

            @Override
            public Type type() {
                return Type.NOTHING;
            }

            @Override
            public boolean equals(Object obj) {
                return obj instanceof MaybeNothing that
                        && Objects.equals(this.token(), that.token());
            }

            @Override
            public int hashCode() {
                return Objects.hashCode(token);
            }
        }

        public static final class Required extends Modifier {
            private static final Required INSTANCE = new Required();

            @Override
            public String token() {
                return "required";
            }

            @Override
            public Type type() {
                return Type.REQUIRED;
            }

            @Override
            public boolean equals(Object obj) {
                return obj instanceof MaybeNothing that
                        && Objects.equals(this.token(), that.token());
            }
        }

        public static final class Optional extends Modifier {
            private static final Optional INSTANCE = new Optional();

            @Override
            public String token() {
                return "optional";
            }

            @Override
            public Type type() {
                return Type.OPTIONAL;
            }

            @Override
            public boolean equals(Object obj) {
                return obj instanceof Optional;
            }
        }

        public static final class Repeated extends Modifier {
            private static final Repeated INSTANCE = new Repeated();

            @Override
            public String token() {
                return "repeated";
            }

            @Override
            public Type type() {
                return Type.REPEATED;
            }

            @Override
            public boolean equals(Object obj) {
                return obj instanceof Repeated;
            }
        }

        public enum Type {
            NOTHING,
            REQUIRED,
            OPTIONAL,
            REPEATED
        }
    }
}
