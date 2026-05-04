package ch.usi.inf.dag.mapreplay.trace;

/**
 * Stable numeric opcode constants for the MapReplay trace format.
 * <p>
 * These values intentionally preserve the legacy opcode assignment that was
 * previously derived from TracerEvent.ordinal().
 * <p>
 * Do not reorder, renumber, or reuse existing opcodes after public release.
 */


public final class TraceOpCodes {

    private TraceOpCodes() {
        throw new AssertionError("No instances");
    }

    public static final int MIN_OPCODE = 0x00;
    public static final int MAX_OPCODE = 0xFF;

    // ---------------------------------------------------------------------
    // Constructors
    // Legacy ordinal range: 0..2
    // ---------------------------------------------------------------------

    public static final int CONSTRUCTOR = 0;
    public static final int CONSTRUCTOR_WITH_INPUT = 1;
    public static final int CONSTRUCTOR_COPY_HASHMAP = 2;

    // ---------------------------------------------------------------------
    // Read operations
    // Legacy ordinal range: 3..5
    // ---------------------------------------------------------------------

    public static final int GET = 3;
    public static final int GET_OR_DEFAULT = 4;
    public static final int CONTAINS_KEY = 5;

    // ---------------------------------------------------------------------
    // Write operations
    // Legacy ordinal range: 6..11
    // ---------------------------------------------------------------------

    public static final int PUT = 6;
    public static final int PUTALL_HASHMAP = 7;
    public static final int REMOVE = 8;
    public static final int REPLACE = 9;
    public static final int CLEAR = 10;
    public static final int PUT_IF_ABSENT = 11;

    // ---------------------------------------------------------------------
    // Functional operations
    // Legacy ordinal: 12
    // ---------------------------------------------------------------------

    public static final int COMPUTE_IF_ABSENT_NON_NULL_MAPPING = 12;

    // ---------------------------------------------------------------------
    // Loops / bulk traversal
    // Legacy ordinal: 13
    // ---------------------------------------------------------------------

    public static final int FOR_EACH = 13;

    // ---------------------------------------------------------------------
    // Iterators
    // Legacy ordinal range: 14..19
    // ---------------------------------------------------------------------

    public static final int KEY_ITERATOR = 14;
    public static final int VALUE_ITERATOR = 15;
    public static final int ENTRY_ITERATOR = 16;
    public static final int ITERATOR_NEXT = 17;
    public static final int ITERATOR_REMOVE = 18;

    /**
     * Legacy name preserved from the original TracerEvent enum.
     *
     * This represents a remove operation through a collection view, such as
     * values().remove(...), depending on the exact instrumentation point.
     */
    public static final int ABSTRACT_COLLECTION_REMOVE = 19;

    /**
     * More descriptive alias for new code.
     *
     * Do not use this as a distinct trace opcode. It is the same value as
     * ABSTRACT_COLLECTION_REMOVE.
     */
    public static final int COLLECTION_VIEW_REMOVE = ABSTRACT_COLLECTION_REMOVE;

    // ---------------------------------------------------------------------
    // Other direct HashMap operations
    // Legacy ordinal: 20
    // ---------------------------------------------------------------------

    public static final int CLONE = 20;

    // ---------------------------------------------------------------------
    // Later-added tracer events
    // Legacy ordinal range: 21..23
    // ---------------------------------------------------------------------

    public static final int COMPUTE_IF_ABSENT_NULL_MAPPING = 23;

    // ---------------------------------------------------------------------
    // Spliterators
    // Legacy ordinal range: 24..28
    // ---------------------------------------------------------------------

    public static final int ENTRIES_SPLITERATOR_FOREACH_REMAINING = 24;
    public static final int KEY_SPLITERATOR_FOREACH_REMAINING = 25;
    public static final int VALUES_SPLITERATOR_FOREACH_REMAINING = 26;
    public static final int SPLITERATOR_TRY_ADVANCE = 27;
    public static final int SPLITERATOR_TRY_SPLIT = 28;

    // ---------------------------------------------------------------------
    // Synthetic / post-processed events
    // These were not part of the original TracerEvent enum.
    // ---------------------------------------------------------------------

    public static final int FREE_MAP = 100;
    public static final int FREE_ITERATOR = 101;
    public static final int MULTI_STEP_ITERATION = 105;

    public static boolean isValidUnsignedByteOpcode(int opcode) {
        return opcode >= MIN_OPCODE && opcode <= MAX_OPCODE;
    }
}