package jdk.internal.mapreplay;

import ch.usi.inf.dag.mapreplay.trace.TraceOpCodes;
import ch.usi.inf.dag.mapreplay.trace.TraceOpcode;

public final class NativeTracer {

    /*******************************
     ****** EVENT DEFINITION *******
     ******************************/

    public static final long NO_KEY_ID = -1;
    public static final int NO_HASHCODE = -1;

    /*****************************************
     ****** TRACER/LOGGER DEF AND INIT *******
     ****************************************/

    public static volatile boolean ready = false; // TODO: later move activation to JVMTI VMInit
    private static volatile NativeTracer tracer;
    private boolean initialized = false;
    private boolean logEnabled;

    private NativeTracer() {
    }

    public static synchronized NativeTracer getTracer() {
        if (tracer == null) {
            tracer = new NativeTracer();
        }
        if (ready) {
            tracer.inizialize();
        }
        return tracer;
    }

    private synchronized void inizialize() {
        if (initialized) {
            return;
        }

        try {
            nativeInit();
            logEnabled = "true".equals(System.getProperty("tracer.logEnabled", "false"));
            initialized = true;
        } catch (Throwable t) {
            System.err.println("Failed to initialize native methods:");
            t.printStackTrace();
        }
    }

    /********************************
     *** NATIVE WRITING METHODS  ****
     ********************************/

    private static native void nativeInit();
    private static native void nativeTraceByte(byte b);
    private static native void nativeTraceLong(long v);
    private static native void nativeTraceInt(int v);
    private static native void nativeTraceFloat(float f);
    private static native void nativeLog(String s);
    private static native void nativeClose();

    /********************************
     **** JAVA WRITING WRAPPERS *****
     ********************************/

    private void handleException(Throwable t) {
        System.err.println("Exception when writing event:");
        t.printStackTrace();
    }

    private void traceOpcode(int opcode) {
        try {
            nativeTraceByte((byte) opcode);
        } catch (Throwable t) {
            handleException(t);
        }
    }

    private void trace(long v) {
        try {
            nativeTraceLong(v);
        } catch (Throwable t) {
            handleException(t);
        }
    }

    private void trace(int v) {
        try {
            nativeTraceInt(v);
        } catch (Throwable t) {
            handleException(t);
        }
    }

    private void trace(float v) {
        try {
            nativeTraceFloat(v);
        } catch (Throwable t) {
            handleException(t);
        }
    }

    private void trace(boolean v) {
        try {
            nativeTraceByte(v ? (byte) 1 : (byte) 0);
        } catch (Throwable t) {
            handleException(t);
        }
    }

    private void log(int opcode, Object... data) {
        if (logEnabled) {
            try {
                StringBuilder sb = new StringBuilder();

                sb.append(TraceOpcode.fromOpcode(opcode).method());
                sb.append('[');

                for (Object o : data) {
                    sb.append(',').append(o);
                }

                sb.append(']');
                sb.append('\n');

                nativeLog(sb.toString());
            } catch (Throwable t) {
                handleException(t);
            }
        }
    }

    public synchronized void close() {
        try {
            nativeClose();
        } catch (Throwable t) {
            handleException(t);
        }
    }

    /*****************************
     ****** EVENT HANDLING *******
     *****************************/

    public synchronized void registerConstructor(long mapID) {
        if (!ready) {
            return;
        }

        traceOpcode(TraceOpCodes.CONSTRUCTOR);
        trace(mapID);
        log(TraceOpCodes.CONSTRUCTOR, mapID);
    }

    public synchronized void registerConstructorWithParameters(long mapID, int param1, float param2) {
        if (!ready) {
            return;
        }

        traceOpcode(TraceOpCodes.CONSTRUCTOR_WITH_INPUT);
        trace(mapID);
        trace(param1);
        trace(param2);
        log(TraceOpCodes.CONSTRUCTOR_WITH_INPUT, mapID, param1, param2);
    }

    public synchronized void registerCopyConstructorHashMap(long mapID, long otherMapID) {
        if (!ready) {
            return;
        }

        traceOpcode(TraceOpCodes.CONSTRUCTOR_COPY_HASHMAP);
        trace(mapID);
        trace(otherMapID);
        log(TraceOpCodes.CONSTRUCTOR_COPY_HASHMAP, mapID, otherMapID);
    }

    public synchronized void registerClone(long mapID) {
        if (!ready) {
            return;
        }

        traceOpcode(TraceOpCodes.CLONE);
        trace(mapID);
        log(TraceOpCodes.CLONE, mapID);
    }

    public synchronized void registerGet(long mapID, long keyID, int keyHash) {
        if (!ready) {
            return;
        }

        traceOpcode(TraceOpCodes.GET);
        trace(mapID);
        trace(keyID);
        trace(keyHash);
        log(TraceOpCodes.GET, mapID, keyID, keyHash);
    }

    public synchronized void registerGetOrDefault(long mapID, long keyID, int keyHash) {
        if (!ready) {
            return;
        }

        traceOpcode(TraceOpCodes.GET_OR_DEFAULT);
        trace(mapID);
        trace(keyID);
        trace(keyHash);
        log(TraceOpCodes.GET_OR_DEFAULT, mapID, keyID, keyHash);
    }

    public synchronized void registerContainsKey(long mapID, long keyID, int keyHash) {
        if (!ready) {
            return;
        }

        traceOpcode(TraceOpCodes.CONTAINS_KEY);
        trace(mapID);
        trace(keyID);
        trace(keyHash);
        log(TraceOpCodes.CONTAINS_KEY, mapID, keyID, keyHash);
    }

    public synchronized void registerPut(long mapID, long keyID, int keyHash) {
        if (!ready) {
            return;
        }

        traceOpcode(TraceOpCodes.PUT);
        trace(mapID);
        trace(keyID);
        trace(keyHash);
        log(TraceOpCodes.PUT, mapID, keyID, keyHash);
    }

    public synchronized void registerPutAllHashMap(long mapID, long otherMapID) {
        if (!ready) {
            return;
        }

        traceOpcode(TraceOpCodes.PUTALL_HASHMAP);
        trace(mapID);
        trace(otherMapID);
        log(TraceOpCodes.PUTALL_HASHMAP, mapID, otherMapID);
    }

    public synchronized void registerPutIfAbsent(long mapID, long keyID, int keyHash) {
        if (!ready) {
            return;
        }

        traceOpcode(TraceOpCodes.PUT_IF_ABSENT);
        trace(mapID);
        trace(keyID);
        trace(keyHash);
        log(TraceOpCodes.PUT_IF_ABSENT, mapID, keyID, keyHash);
    }

    public synchronized void registerRemove(long mapID, long keyID, int keyHash) {
        if (!ready) {
            return;
        }

        traceOpcode(TraceOpCodes.REMOVE);
        trace(mapID);
        trace(keyID);
        trace(keyHash);
        log(TraceOpCodes.REMOVE, mapID, keyID, keyHash);
    }

    public synchronized void registerClear(long mapID) {
        if (!ready) {
            return;
        }

        traceOpcode(TraceOpCodes.CLEAR);
        trace(mapID);
        log(TraceOpCodes.CLEAR, mapID);
    }

    public synchronized void registerReplace(long mapID, long keyID, int keyHash) {
        if (!ready) {
            return;
        }

        traceOpcode(TraceOpCodes.REPLACE);
        trace(mapID);
        trace(keyID);
        trace(keyHash);
        log(TraceOpCodes.REPLACE, mapID, keyID, keyHash);
    }

    public synchronized void registerForEach(long mapID) {
        if (!ready) {
            return;
        }

        traceOpcode(TraceOpCodes.FOR_EACH);
        trace(mapID);
        log(TraceOpCodes.FOR_EACH, mapID);
    }

    public synchronized void registerKeyIterator(long mapID, long iteratorID) {
        if (!ready) {
            return;
        }

        traceOpcode(TraceOpCodes.KEY_ITERATOR);
        trace(mapID);
        trace(iteratorID);
        log(TraceOpCodes.KEY_ITERATOR, mapID, iteratorID);
    }

    public synchronized void registerValueIterator(long mapID, long iteratorID) {
        if (!ready) {
            return;
        }

        traceOpcode(TraceOpCodes.VALUE_ITERATOR);
        trace(mapID);
        trace(iteratorID);
        log(TraceOpCodes.VALUE_ITERATOR, mapID, iteratorID);
    }

    public synchronized void registerEntryIterator(long mapID, long iteratorID) {
        if (!ready) {
            return;
        }

        traceOpcode(TraceOpCodes.ENTRY_ITERATOR);
        trace(mapID);
        trace(iteratorID);
        log(TraceOpCodes.ENTRY_ITERATOR, mapID, iteratorID);
    }

    public synchronized void registerIteratorNext(long mapID, long iteratorID) {
        if (!ready) {
            return;
        }

        traceOpcode(TraceOpCodes.ITERATOR_NEXT);
        trace(mapID);
        trace(iteratorID);
        log(TraceOpCodes.ITERATOR_NEXT, mapID, iteratorID);
    }

    public synchronized void registerIteratorRemove(long mapID, long iteratorID) {
        if (!ready) {
            return;
        }

        traceOpcode(TraceOpCodes.ITERATOR_REMOVE);
        trace(mapID);
        trace(iteratorID);
        log(TraceOpCodes.ITERATOR_REMOVE, mapID, iteratorID);
    }

    public synchronized void registerAbstractCollectionRemove(long mapID) {
        if (!ready) {
            return;
        }

        traceOpcode(TraceOpCodes.ABSTRACT_COLLECTION_REMOVE);
        trace(mapID);
        log(TraceOpCodes.ABSTRACT_COLLECTION_REMOVE, mapID);
    }

    public synchronized void registerComputeIfAbsent(
            long mapID,
            long keyID,
            int keyHashCode,
            boolean mappingToNull
    ) {
        if (!ready) {
            return;
        }

        int opcode = mappingToNull
                ? TraceOpCodes.COMPUTE_IF_ABSENT_NULL_MAPPING
                : TraceOpCodes.COMPUTE_IF_ABSENT_NON_NULL_MAPPING;

        traceOpcode(opcode);
        trace(mapID);
        trace(keyID);
        trace(keyHashCode);
        log(opcode, mapID, keyID, keyHashCode);
    }

    public synchronized void registerEntriesSpliteratorForEachRemaining(long mapID) {
        if (!ready) {
            return;
        }

        int opcode = TraceOpCodes.ENTRIES_SPLITERATOR_FOREACH_REMAINING;

        traceOpcode(opcode);
        trace(mapID);
        log(opcode, mapID);
    }

    public synchronized void registerKeySpliteratorForEachRemaining(long mapID) {
        if (!ready) {
            return;
        }

        int opcode = TraceOpCodes.KEY_SPLITERATOR_FOREACH_REMAINING;

        traceOpcode(opcode);
        trace(mapID);
        log(opcode, mapID);
    }

    public synchronized void registerValuesSpliteratorForEachRemaining(long mapID) {
        if (!ready) {
            return;
        }

        int opcode = TraceOpCodes.VALUES_SPLITERATOR_FOREACH_REMAINING;

        traceOpcode(opcode);
        trace(mapID);
        log(opcode, mapID);
    }

    public synchronized void registerSpliteratorTryAdvance(long mapID) {
        if (!ready) {
            return;
        }

        int opcode = TraceOpCodes.SPLITERATOR_TRY_ADVANCE;

        traceOpcode(opcode);
        trace(mapID);
        log(opcode, mapID);
    }

    public synchronized void registerSpliteratorTrySplit(long mapID) {
        if (!ready) {
            return;
        }

        int opcode = TraceOpCodes.SPLITERATOR_TRY_SPLIT;

        traceOpcode(opcode);
        trace(mapID);
        log(opcode, mapID);
    }
}