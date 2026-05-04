package ch.usi.inf.dag.mapreplay.replay;

import ch.usi.inf.dag.mapreplay.trace.TraceInstructionCodec;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import ch.usi.inf.dag.mapreplay.trace.TraceOpCodes;

@State(Scope.Benchmark)
@Fork(5)
@Warmup(
        iterations = 5,
        time = 10,
        timeUnit = TimeUnit.SECONDS
)
@Measurement(
        iterations = 5,
        time = 10,
        timeUnit = TimeUnit.SECONDS
)
public class JMHBenchmark {

    // -------------------
    // Static inner class
    // -------------------
    public static class MockKey {
        public final int key; // used only for an operand hack...
        public final int hash;

        public MockKey(int key, int hash) {
            this.key = key;
            this.hash = hash;
        }

        @Override
        public int hashCode() {
            return hash;
        }

    }

    // -------------------
    // Benchmark parameters and fields
    // -------------------

    /** Path to the zipped trace file (set via JMH param). */
    @Param("")
    public String zipPath;

    public int eventsCount;

    public long[] opCodesWithOperandIDIndex;
    public MockKey[] keys;

    public HashMap[] maps;
    public Iterator[] iterators;

    // -------------------
    // Setup
    // -------------------

    @Setup(Level.Trial)
    public void setup() throws IOException {
        try (ZipFile zipFile = new ZipFile(zipPath)) {

            // --- read sizes metadata from trace-replay-sizes.csv ---
            int mapsCount = 0;
            int iteratorsCount = 0;
            ZipEntry sizesEntry = zipFile.getEntry("trace-replay-sizes.csv");
            if (sizesEntry != null) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(zipFile.getInputStream(sizesEntry)))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] parts = line.split(",");
                        String name = parts[0].trim();
                        int value = Integer.parseInt(parts[1].trim());
                        if (name.equalsIgnoreCase("events")) eventsCount = value;
                        else if (name.equalsIgnoreCase("maps")) mapsCount = value;
                        else if (name.equalsIgnoreCase("iterators")) iteratorsCount = value;
                    }
                }
            }

            // Allocate arrays
            opCodesWithOperandIDIndex = new long[eventsCount];
            keys = new MockKey[eventsCount];
            maps = new HashMap[mapsCount];
            iterators = new Iterator[iteratorsCount];

            // --- read ops.bin ---
            ZipEntry opsEntry = zipFile.getEntry("ops.bin");
            if (opsEntry == null)
                throw new FileNotFoundException("ops.bin missing in " + zipPath);
            try (DataInputStream in = new DataInputStream(zipFile.getInputStream(opsEntry))) {
                for (int i = 0; i < eventsCount; i++) {
                    opCodesWithOperandIDIndex[i] = in.readLong();
                }
            }

            // --- read keyIDs.bin & keyHashCodes.bin ---
            ZipEntry keyIDsEntry = zipFile.getEntry("keyIDs.bin");
            ZipEntry keyHashCodesEntry = zipFile.getEntry("keyHashCodes.bin");
            if (keyIDsEntry == null || keyHashCodesEntry == null)
                throw new FileNotFoundException("keyIDs.bin or keyHashCodes.bin missing in " + zipPath);


            record KeyID(int id, int hash) {}
            HashMap<KeyID, MockKey> keyMap = new HashMap<>();

            try (DataInputStream idIn = new DataInputStream(zipFile.getInputStream(keyIDsEntry));
                 DataInputStream hashIn = new DataInputStream(zipFile.getInputStream(keyHashCodesEntry))
            ) {
                for (int i = 0; i < eventsCount; i++) {
                    int id = idIn.readInt();
                    int hash = hashIn.readInt();
                    KeyID keyID = new KeyID(id, hash);
                    MockKey key = keyMap.computeIfAbsent(keyID, k -> new MockKey(id, hash));
                    keys[i] = key;
                }
            }

        }

        System.gc();
    }


    @Benchmark
    public void replay(Blackhole bh) throws Throwable {
        Object value = "";

        for (int i = 0; i < eventsCount; i++) {
            long encoded = opCodesWithOperandIDIndex[i];

            int opcode = TraceInstructionCodec.decodeOpcode(encoded);
            int idx = TraceInstructionCodec.decodeOperandIndex(encoded);
            MockKey key = keys[i];

            switch (opcode) {

                // ----------------- Constructors -----------------

                case TraceOpCodes.CONSTRUCTOR ->
                        maps[idx] = new HashMap();

                case TraceOpCodes.CONSTRUCTOR_WITH_INPUT ->
                    // HACK: constructor operands are stored in MockKey fields.
                        maps[idx] = new HashMap(key.key, Float.intBitsToFloat(key.hash));

                case TraceOpCodes.CONSTRUCTOR_COPY_HASHMAP ->
                        maps[idx] = new HashMap(maps[key.hash]);

                // ----------------- Read operations -----------------

                case TraceOpCodes.GET ->
                        bh.consume(maps[idx].get(key));

                case TraceOpCodes.GET_OR_DEFAULT ->
                        bh.consume(maps[idx].getOrDefault(key, value));

                case TraceOpCodes.CONTAINS_KEY ->
                        bh.consume(maps[idx].containsKey(key));

                // ----------------- Write operations -----------------

                case TraceOpCodes.PUT ->
                        maps[idx].put(key, value);

                case TraceOpCodes.PUTALL_HASHMAP ->
                        maps[idx].putAll(maps[key.hash]);

                case TraceOpCodes.REMOVE ->
                        maps[idx].remove(key);

                case TraceOpCodes.REPLACE ->
                        maps[idx].replace(key, value);

                case TraceOpCodes.CLEAR ->
                        maps[idx].clear();

                case TraceOpCodes.PUT_IF_ABSENT ->
                        maps[idx].putIfAbsent(key, value);

                // ----------------- Functional operations -----------------

                case TraceOpCodes.COMPUTE_IF_ABSENT_NON_NULL_MAPPING ->
                        maps[idx].computeIfAbsent(key, k -> value);

                case TraceOpCodes.COMPUTE_IF_ABSENT_NULL_MAPPING ->
                        maps[idx].computeIfAbsent(key, k -> null);

                // ----------------- Loops / bulk traversal -----------------

                case TraceOpCodes.FOR_EACH ->
                        maps[idx].forEach((k, v) -> {
                            bh.consume(k);
                            bh.consume(v);
                        });

                // ----------------- Iterators -----------------
                // For iterator creation events, key.hash stores the replay iterator slot.

                case TraceOpCodes.KEY_ITERATOR ->
                        iterators[key.hash] = maps[idx].keySet().iterator();

                case TraceOpCodes.VALUE_ITERATOR ->
                        iterators[key.hash] = maps[idx].values().iterator();

                case TraceOpCodes.ENTRY_ITERATOR ->
                        iterators[key.hash] = maps[idx].entrySet().iterator();

                case TraceOpCodes.ITERATOR_NEXT -> {
                    if (iterators[idx].hasNext()) {
                        bh.consume(iterators[idx].next());
                    }
                }

                case TraceOpCodes.ITERATOR_REMOVE ->
                        iterators[idx].remove();

                case TraceOpCodes.ABSTRACT_COLLECTION_REMOVE ->
                        throw new IllegalStateException(
                                "CollectionViewRemoveMarker found in replay trace"
                        );

                // ----------------- Unsupported / legacy events -----------------

                case TraceOpCodes.CLONE ->
                        throw new IllegalStateException("CLONE found in replay trace");

                // ----------------- Spliterators / bulk traversal -----------------

                case TraceOpCodes.ENTRIES_SPLITERATOR_FOREACH_REMAINING ->
                        maps[idx].entrySet().stream().forEach(bh::consume);

                case TraceOpCodes.KEY_SPLITERATOR_FOREACH_REMAINING ->
                        maps[idx].keySet().stream().forEach(bh::consume);

                case TraceOpCodes.VALUES_SPLITERATOR_FOREACH_REMAINING ->
                        maps[idx].values().stream().forEach(bh::consume);

                case TraceOpCodes.SPLITERATOR_TRY_ADVANCE ->
                        throw new IllegalStateException(
                                "SPLITERATOR_TRY_ADVANCE found in replay trace"
                        );

                case TraceOpCodes.SPLITERATOR_TRY_SPLIT ->
                        throw new IllegalStateException(
                                "SPLITERATOR_TRY_SPLIT found in replay trace"
                        );

                // ----------------- Synthetic / post-processed events -----------------

                case TraceOpCodes.FREE_MAP ->
                        maps[idx] = null;

                case TraceOpCodes.FREE_ITERATOR ->
                        iterators[idx] = null;

                case TraceOpCodes.MULTI_STEP_ITERATION -> {
                    Iterator<?> it = maps[idx].keySet().iterator();
                    int stop = key.hash;

                    if (stop < 0) {
                        throw new IllegalStateException(
                                "Negative multi-step iteration count: " + stop
                        );
                    }

                    while (stop-- > 0 && it.hasNext()) {
                        bh.consume(it.next());
                    }
                }

                default ->
                        throw new RuntimeException("Unrecognized opcode " + opcode);
            }
        }
    }


}