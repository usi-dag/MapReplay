package ch.usi.inf.dag.mapreplay.postprocess;

import ch.usi.inf.dag.mapreplay.trace.TraceOpCodes;

public abstract sealed class Event
        permits Clear,
                Clone,
                ComputeIfAbsentNotNullMapping,
                ComputeIfAbsentNullMapping,
                Constructor,
                ConstructorCopyHashMap,
                ConstructorWithInput,
                ContainsKey,
                DummyHeadEvent,
                EntrySetSpliteratorForEachRemaining,
                ForEach,
                FreeMapEvent,
                Get,
                GetOrDefault,
                IteratorEvent,
                KeysSpliteratorForEachRemaining,
                MultiStepIteration,
                Put,
                PutAllHashMap,
                PutIfAbsent,
                Remove,
                Replace,
                SpliteratorTryAdvance,
                SpliteratorTrySplit,
        CollectionViewRemoveMarker,
                ValuesSetSpliteratorForEachRemaining {

    public final long mapID;
    public Event next; // not initialized

    protected Event(long mapID) {
        this.mapID = mapID;
    }

    /**
     * Returns the stable trace opcode for this event type.
     *
     * The value must come from TraceOpCodes and must not depend on enum ordinal().
     */
    public abstract int opcode();

    /** Returns the mapID associated with this event. */
    public long mapID() {
        return mapID;
    }
}

/* ********************************************************************
 * ********************** EVENT IMPLEMENTATIONS ***********************
 * ********************************************************************/

final class Constructor extends Event {
    public Constructor(long mapID) {
        super(mapID);
    }

    @Override
    public int opcode() {
        return TraceOpCodes.CONSTRUCTOR;
    }
}

final class ConstructorWithInput extends Event {
    public final int param1;
    public final float param2;

    public ConstructorWithInput(long mapID, int param1, float param2) {
        super(mapID);
        this.param1 = param1;
        this.param2 = param2;
    }

    @Override
    public int opcode() {
        return TraceOpCodes.CONSTRUCTOR_WITH_INPUT;
    }
}

final class ConstructorCopyHashMap extends Event {
    public final long otherMapID;

    public ConstructorCopyHashMap(long mapID, long otherMapID) {
        super(mapID);
        this.otherMapID = otherMapID;
    }

    @Override
    public int opcode() {
        return TraceOpCodes.CONSTRUCTOR_COPY_HASHMAP;
    }
}

final class Get extends Event {
    public final int keyID;
    public final int keyHashCode;

    public Get(long mapID, int keyID, int keyHashCode) {
        super(mapID);
        this.keyID = keyID;
        this.keyHashCode = keyHashCode;
    }

    @Override
    public int opcode() {
        return TraceOpCodes.GET;
    }
}

final class GetOrDefault extends Event {
    public final int keyID;
    public final int keyHashCode;

    public GetOrDefault(long mapID, int keyID, int keyHashCode) {
        super(mapID);
        this.keyID = keyID;
        this.keyHashCode = keyHashCode;
    }

    @Override
    public int opcode() {
        return TraceOpCodes.GET_OR_DEFAULT;
    }
}

final class ContainsKey extends Event {
    public final int keyID;
    public final int keyHashCode;

    public ContainsKey(long mapID, int keyID, int keyHashCode) {
        super(mapID);
        this.keyID = keyID;
        this.keyHashCode = keyHashCode;
    }

    @Override
    public int opcode() {
        return TraceOpCodes.CONTAINS_KEY;
    }
}

final class Put extends Event {
    public final int keyID;
    public final int keyHashCode;

    public Put(long mapID, int keyID, int keyHashCode) {
        super(mapID);
        this.keyID = keyID;
        this.keyHashCode = keyHashCode;
    }

    @Override
    public int opcode() {
        return TraceOpCodes.PUT;
    }
}

final class PutAllHashMap extends Event {
    public final long otherMapID;

    public PutAllHashMap(long mapID, long otherMapID) {
        super(mapID);
        this.otherMapID = otherMapID;
    }

    @Override
    public int opcode() {
        return TraceOpCodes.PUTALL_HASHMAP;
    }
}

final class Remove extends Event {
    public final int keyID;
    public final int keyHashCode;

    public Remove(long mapID, int keyID, int keyHashCode) {
        super(mapID);
        this.keyID = keyID;
        this.keyHashCode = keyHashCode;
    }

    @Override
    public int opcode() {
        return TraceOpCodes.REMOVE;
    }
}

final class Replace extends Event {
    public final int keyID;
    public final int keyHashCode;

    public Replace(long mapID, int keyID, int keyHashCode) {
        super(mapID);
        this.keyID = keyID;
        this.keyHashCode = keyHashCode;
    }

    @Override
    public int opcode() {
        return TraceOpCodes.REPLACE;
    }
}

final class Clear extends Event {
    public Clear(long mapID) {
        super(mapID);
    }

    @Override
    public int opcode() {
        return TraceOpCodes.CLEAR;
    }
}

final class PutIfAbsent extends Event {
    public final int keyID;
    public final int keyHashCode;

    public PutIfAbsent(long mapID, int keyID, int keyHashCode) {
        super(mapID);
        this.keyID = keyID;
        this.keyHashCode = keyHashCode;
    }

    @Override
    public int opcode() {
        return TraceOpCodes.PUT_IF_ABSENT;
    }
}

final class ComputeIfAbsentNotNullMapping extends Event {
    public final int keyID;
    public final int keyHashCode;

    public ComputeIfAbsentNotNullMapping(long mapID, int keyID, int keyHashCode) {
        super(mapID);
        this.keyID = keyID;
        this.keyHashCode = keyHashCode;
    }

    @Override
    public int opcode() {
        return TraceOpCodes.COMPUTE_IF_ABSENT_NON_NULL_MAPPING;
    }
}

final class ForEach extends Event {
    public ForEach(long mapID) {
        super(mapID);
    }

    @Override
    public int opcode() {
        return TraceOpCodes.FOR_EACH;
    }
}

abstract sealed class IteratorEvent extends Event
        permits FreeIteratorEvent,
                IteratorCreation,
                IteratorNext,
                IteratorRemove {

    public final long iteratorID;

    protected IteratorEvent(long mapID, long iteratorID) {
        super(mapID);
        this.iteratorID = iteratorID;
    }
}

abstract sealed class IteratorCreation extends IteratorEvent
        permits KeyIterator,
                ValueIterator,
                EntryIterator {

    protected IteratorCreation(long mapID, long iteratorID) {
        super(mapID, iteratorID);
    }
}

final class KeyIterator extends IteratorCreation {
    public KeyIterator(long mapID, long iteratorID) {
        super(mapID, iteratorID);
    }

    @Override
    public int opcode() {
        return TraceOpCodes.KEY_ITERATOR;
    }
}

final class ValueIterator extends IteratorCreation {
    public ValueIterator(long mapID, long iteratorID) {
        super(mapID, iteratorID);
    }

    @Override
    public int opcode() {
        return TraceOpCodes.VALUE_ITERATOR;
    }
}

final class EntryIterator extends IteratorCreation {
    public EntryIterator(long mapID, long iteratorID) {
        super(mapID, iteratorID);
    }

    @Override
    public int opcode() {
        return TraceOpCodes.ENTRY_ITERATOR;
    }
}

final class IteratorNext extends IteratorEvent {
    public IteratorNext(long mapID, long iteratorID) {
        super(mapID, iteratorID);
    }

    @Override
    public int opcode() {
        return TraceOpCodes.ITERATOR_NEXT;
    }
}

final class IteratorRemove extends IteratorEvent {
    public IteratorRemove(long mapID, long iteratorID) {
        super(mapID, iteratorID);
    }

    @Override
    public int opcode() {
        return TraceOpCodes.ITERATOR_REMOVE;
    }
}

final class CollectionViewRemoveMarker extends Event {
    public CollectionViewRemoveMarker(long mapID) {
        super(mapID);
    }

    @Override
    public int opcode() {
        return TraceOpCodes.ABSTRACT_COLLECTION_REMOVE;
    }
}

final class Clone extends Event {
    public Clone(long mapID) {
        super(mapID);
    }

    @Override
    public int opcode() {
        return TraceOpCodes.CLONE;
    }
}

/* -----------------------
 * Later-added tracer events
 * ----------------------- */

final class ComputeIfAbsentNullMapping extends Event {
    public final int keyID;
    public final int keyHashCode;

    public ComputeIfAbsentNullMapping(long mapID, int keyID, int keyHashCode) {
        super(mapID);
        this.keyID = keyID;
        this.keyHashCode = keyHashCode;
    }

    @Override
    public int opcode() {
        return TraceOpCodes.COMPUTE_IF_ABSENT_NULL_MAPPING;
    }
}

final class EntrySetSpliteratorForEachRemaining extends Event {
    public EntrySetSpliteratorForEachRemaining(long mapID) {
        super(mapID);
    }

    @Override
    public int opcode() {
        return TraceOpCodes.ENTRIES_SPLITERATOR_FOREACH_REMAINING;
    }
}

final class KeysSpliteratorForEachRemaining extends Event {
    public KeysSpliteratorForEachRemaining(long mapID) {
        super(mapID);
    }

    @Override
    public int opcode() {
        return TraceOpCodes.KEY_SPLITERATOR_FOREACH_REMAINING;
    }
}

final class ValuesSetSpliteratorForEachRemaining extends Event {
    public ValuesSetSpliteratorForEachRemaining(long mapID) {
        super(mapID);
    }

    @Override
    public int opcode() {
        return TraceOpCodes.VALUES_SPLITERATOR_FOREACH_REMAINING;
    }
}

final class SpliteratorTryAdvance extends Event {
    public SpliteratorTryAdvance(long mapID) {
        super(mapID);
    }

    @Override
    public int opcode() {
        return TraceOpCodes.SPLITERATOR_TRY_ADVANCE;
    }
}

final class SpliteratorTrySplit extends Event {
    public SpliteratorTrySplit(long mapID) {
        super(mapID);
    }

    @Override
    public int opcode() {
        return TraceOpCodes.SPLITERATOR_TRY_SPLIT;
    }
}

/* -----------------------
 * Synthetic / post-processed event types
 * ----------------------- */

final class FreeMapEvent extends Event {
    FreeMapEvent(long mapID) {
        super(mapID);
    }

    @Override
    public int opcode() {
        return TraceOpCodes.FREE_MAP;
    }
}

final class FreeIteratorEvent extends IteratorEvent {
    FreeIteratorEvent(long mapID, long iteratorID) {
        super(mapID, iteratorID);
    }

    @Override
    public int opcode() {
        return TraceOpCodes.FREE_ITERATOR;
    }
}

final class MultiStepIteration extends Event {
    int steps = 0;

    MultiStepIteration(long mapID) {
        super(mapID);
    }

    MultiStepIteration(long mapID, int steps) {
        super(mapID);
        this.steps = steps;
    }

    @Override
    public int opcode() {
        return TraceOpCodes.MULTI_STEP_ITERATION;
    }
}

/**
 * DummyHeadEvent: utility node used only to simplify linked-list rewriting.
 *
 * It is not a real trace event and must never be emitted.
 */
final class DummyHeadEvent extends Event {
    DummyHeadEvent(Event head) {
        super(-1);
        next = head;
    }

    @Override
    public int opcode() {
        throw new UnsupportedOperationException("DummyHeadEvent has no trace opcode");
    }
}