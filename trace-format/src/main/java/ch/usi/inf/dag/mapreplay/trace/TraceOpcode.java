package ch.usi.inf.dag.mapreplay.trace;

import java.util.Arrays;

/**
 * Metadata view over the stable numeric trace opcodes.
 * <p>
 * The numeric opcode values are defined in {@link TraceOpCodes}.
 * This enum is for readability, diagnostics, parsing, validation, and
 * documentation. The trace format must not depend on enum ordinal().
 */

public enum TraceOpcode {

    // ---------------------------------------------------------------------
    // Constructors
    // ---------------------------------------------------------------------

    CONSTRUCTOR(
            TraceOpCodes.CONSTRUCTOR,
            Category.CONSTRUCTION,
            Origin.TRACER,
            "HashMap()"
    ),

    CONSTRUCTOR_WITH_INPUT(
            TraceOpCodes.CONSTRUCTOR_WITH_INPUT,
            Category.CONSTRUCTION,
            Origin.TRACER,
            "HashMap(int,float)"
    ),

    CONSTRUCTOR_COPY_HASHMAP(
            TraceOpCodes.CONSTRUCTOR_COPY_HASHMAP,
            Category.CONSTRUCTION,
            Origin.TRACER,
            "HashMap(HashMap)"
    ),

    // ---------------------------------------------------------------------
    // Read operations
    // ---------------------------------------------------------------------

    GET(
            TraceOpCodes.GET,
            Category.READ,
            Origin.TRACER,
            "get"
    ),

    GET_OR_DEFAULT(
            TraceOpCodes.GET_OR_DEFAULT,
            Category.READ,
            Origin.TRACER,
            "getOrDefault"
    ),

    CONTAINS_KEY(
            TraceOpCodes.CONTAINS_KEY,
            Category.READ,
            Origin.TRACER,
            "containsKey"
    ),

    // ---------------------------------------------------------------------
    // Write operations
    // ---------------------------------------------------------------------

    PUT(
            TraceOpCodes.PUT,
            Category.WRITE,
            Origin.TRACER,
            "put"
    ),

    PUTALL_HASHMAP(
            TraceOpCodes.PUTALL_HASHMAP,
            Category.WRITE,
            Origin.TRACER,
            "putAll(HashMap)"
    ),

    REMOVE(
            TraceOpCodes.REMOVE,
            Category.WRITE,
            Origin.TRACER,
            "remove(Object)"
    ),

    REPLACE(
            TraceOpCodes.REPLACE,
            Category.WRITE,
            Origin.TRACER,
            "replace(K,V)"
    ),

    CLEAR(
            TraceOpCodes.CLEAR,
            Category.WRITE,
            Origin.TRACER,
            "clear"
    ),

    PUT_IF_ABSENT(
            TraceOpCodes.PUT_IF_ABSENT,
            Category.WRITE,
            Origin.TRACER,
            "putIfAbsent"
    ),

    // ---------------------------------------------------------------------
    // Functional operations
    // ---------------------------------------------------------------------

    COMPUTE_IF_ABSENT_NON_NULL_MAPPING(
            TraceOpCodes.COMPUTE_IF_ABSENT_NON_NULL_MAPPING,
            Category.FUNCTIONAL,
            Origin.TRACER,
            "computeIfAbsent(non-null mapping)"
    ),

    COMPUTE_IF_ABSENT_NULL_MAPPING(
            TraceOpCodes.COMPUTE_IF_ABSENT_NULL_MAPPING,
            Category.FUNCTIONAL,
            Origin.TRACER,
            "computeIfAbsent(null mapping)"
    ),

    // ---------------------------------------------------------------------
    // Loops / bulk traversal
    // ---------------------------------------------------------------------

    FOR_EACH(
            TraceOpCodes.FOR_EACH,
            Category.BULK_TRAVERSAL,
            Origin.TRACER,
            "forEach"
    ),

    // ---------------------------------------------------------------------
    // Iterators
    // ---------------------------------------------------------------------

    KEY_ITERATOR(
            TraceOpCodes.KEY_ITERATOR,
            Category.ITERATOR,
            Origin.TRACER,
            "KeyIterator()"
    ),

    VALUE_ITERATOR(
            TraceOpCodes.VALUE_ITERATOR,
            Category.ITERATOR,
            Origin.TRACER,
            "ValueIterator()"
    ),

    ENTRY_ITERATOR(
            TraceOpCodes.ENTRY_ITERATOR,
            Category.ITERATOR,
            Origin.TRACER,
            "EntryIterator()"
    ),

    ITERATOR_NEXT(
            TraceOpCodes.ITERATOR_NEXT,
            Category.ITERATOR,
            Origin.TRACER,
            "HashIterator.next()"
    ),

    ITERATOR_REMOVE(
            TraceOpCodes.ITERATOR_REMOVE,
            Category.ITERATOR,
            Origin.TRACER,
            "HashIterator.remove()"
    ),

    ABSTRACT_COLLECTION_REMOVE(
            TraceOpCodes.ABSTRACT_COLLECTION_REMOVE,
            Category.VIEW,
            Origin.TRACER,
            "INVALIDATE-MAP-VALUES-REMOVE"
    ),

    // ---------------------------------------------------------------------
    // Other direct operations
    // ---------------------------------------------------------------------

    CLONE(
            TraceOpCodes.CLONE,
            Category.OTHER,
            Origin.TRACER,
            "clone"
    ),

    // ---------------------------------------------------------------------
    // Spliterators
    // ---------------------------------------------------------------------

    ENTRIES_SPLITERATOR_FOREACH_REMAINING(
            TraceOpCodes.ENTRIES_SPLITERATOR_FOREACH_REMAINING,
            Category.BULK_TRAVERSAL,
            Origin.TRACER,
            "entrySet().forEachRemaining()"
    ),

    KEY_SPLITERATOR_FOREACH_REMAINING(
            TraceOpCodes.KEY_SPLITERATOR_FOREACH_REMAINING,
            Category.BULK_TRAVERSAL,
            Origin.TRACER,
            "keys().forEachRemaining()"
    ),

    VALUES_SPLITERATOR_FOREACH_REMAINING(
            TraceOpCodes.VALUES_SPLITERATOR_FOREACH_REMAINING,
            Category.BULK_TRAVERSAL,
            Origin.TRACER,
            "values().forEachRemaining()"
    ),

    SPLITERATOR_TRY_ADVANCE(
            TraceOpCodes.SPLITERATOR_TRY_ADVANCE,
            Category.BULK_TRAVERSAL,
            Origin.TRACER,
            "spliterator().tryAdvance()"
    ),

    SPLITERATOR_TRY_SPLIT(
            TraceOpCodes.SPLITERATOR_TRY_SPLIT,
            Category.BULK_TRAVERSAL,
            Origin.TRACER,
            "spliterator().trySplit()"
    ),

    // ---------------------------------------------------------------------
    // Synthetic / post-processed events
    // ---------------------------------------------------------------------

    FREE_MAP(
            TraceOpCodes.FREE_MAP,
            Category.SYNTHETIC,
            Origin.POST_PROCESSOR,
            "synthetic: free map"
    ),

    FREE_ITERATOR(
            TraceOpCodes.FREE_ITERATOR,
            Category.SYNTHETIC,
            Origin.POST_PROCESSOR,
            "synthetic: free iterator"
    ),

    MULTI_STEP_ITERATION(
            TraceOpCodes.MULTI_STEP_ITERATION,
            Category.SYNTHETIC,
            Origin.POST_PROCESSOR,
            "synthetic: multi-step iteration"
    );

    private static final TraceOpcode[] BY_OPCODE = new TraceOpcode[256];

    static {
        Arrays.fill(BY_OPCODE, null);

        for (TraceOpcode value : values()) {
            int opcode = value.opcode;

            if (!TraceOpCodes.isValidUnsignedByteOpcode(opcode)) {
                throw new ExceptionInInitializerError(
                        "Opcode out of unsigned byte range: " + opcode + " for " + value
                );
            }

            TraceOpcode previous = BY_OPCODE[opcode];
            if (previous != null) {
                throw new ExceptionInInitializerError(
                        "Duplicate opcode " + opcode + ": " + previous + " and " + value
                );
            }

            BY_OPCODE[opcode] = value;
        }
    }

    public final int opcode;
    public final Category category;
    public final Origin origin;
    public final String method;

    TraceOpcode(int opcode, Category category, Origin origin, String method) {
        this.opcode = opcode;
        this.category = category;
        this.origin = origin;
        this.method = method;
    }

    public int opcode() {
        return opcode;
    }

    public byte encodedByte() {
        return (byte) opcode;
    }

    public Category category() {
        return category;
    }

    public Origin origin() {
        return origin;
    }

    public String method() {
        return method;
    }

    public boolean isSynthetic() {
        return category == Category.SYNTHETIC;
    }

    public static boolean isKnownOpcode(int opcode) {
        return opcode >= 0 && opcode <= 255 && BY_OPCODE[opcode] != null;
    }

    public static TraceOpcode fromOpcode(int opcode) {
        if (opcode < 0 || opcode > 255) {
            throw new IllegalArgumentException("Opcode out of unsigned byte range: " + opcode);
        }

        TraceOpcode value = BY_OPCODE[opcode];
        if (value == null) {
            throw new IllegalArgumentException("Unknown trace opcode: " + opcode);
        }

        return value;
    }

    public static TraceOpcode fromEncodedByte(byte encodedOpcode) {
        return fromOpcode(Byte.toUnsignedInt(encodedOpcode));
    }

    public enum Category {
        CONSTRUCTION,
        READ,
        WRITE,
        FUNCTIONAL,
        BULK_TRAVERSAL,
        ITERATOR,
        VIEW,
        OTHER,
        SYNTHETIC
    }

    public enum Origin {
        TRACER,
        POST_PROCESSOR
    }
}