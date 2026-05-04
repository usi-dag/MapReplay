package ch.usi.inf.dag.mapreplay.postprocess;

import ch.usi.inf.dag.mapreplay.trace.TraceInstructionCodec;
import ch.usi.inf.dag.mapreplay.trace.TraceOpCodes;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntPriorityQueue;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.io.*;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


public class TracePostProcessor {

    public static final boolean WRITE_VERBOSE =
            "true".equals(System.getenv("LOG_TRACER")); // TODO other env var?

    public static final boolean PRINT_EVENT_COUNT =
            System.getProperty("print.trace.count", "false").equals("true");

    private static final int MISSING_INDEX = -1;

    public static void main(String[] args) {
        String inputTrace;
        String outputTrace;

        if (args.length == 0) {
            inputTrace = "trace.rip";
            outputTrace = inputTrace.replace(".rip", ".zip");
        } else if (args.length == 1) {
            inputTrace = args[0];
            outputTrace = inputTrace.replace(".rip", ".zip");
        } else if (args.length == 2) {
            inputTrace = args[0];
            outputTrace = args[1];
        } else {
            System.err.println("Usage: java TracePostProcessor <input file> <output file>");
            System.exit(1);
            return;
        }

        try {
            postprocess(inputTrace, outputTrace);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    record ParsedTrace(Event head, int eventCount) {}

    record SanitizedTrace(Event head) {}

    record IteratorID(long mapID, long iteratorID) {}

    /**
     * Reads a binary trace file, sanitizes it and writes the sanitized version
     * into the given output filename.
     */
    public static void postprocess(String inputTrace, String outputTrace) throws IOException {
        long startTime;
        long endTime;

        if (PRINT_EVENT_COUNT) {
            int nEvents = parseTrace(inputTrace).eventCount;
            System.out.println(inputTrace + "," + nEvents);
            System.exit(0);
        }

        System.out.print("Parsing trace: " + inputTrace + "... ");
        startTime = System.currentTimeMillis();
        ParsedTrace parsedTrace = parseTrace(inputTrace);
        endTime = System.currentTimeMillis();
        System.out.println("Done in " + (endTime - startTime) + "ms");

        System.out.print("Postprocessing trace... ");
        startTime = System.currentTimeMillis();
        SanitizedTrace sanitizedTrace = sanitize(parsedTrace);
        endTime = System.currentTimeMillis();
        System.out.println("Done in " + (endTime - startTime) + "ms");

        System.out.print("Writing trace: " + outputTrace + "... ");
        startTime = System.currentTimeMillis();
        emitPostProcessedTrace(outputTrace, sanitizedTrace);
        endTime = System.currentTimeMillis();
        System.out.println("Done in " + (endTime - startTime) + "ms");
    }

    private static ParsedTrace parseTrace(String inputTrace) throws IOException {
        Event head, last;

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(inputTrace)))) {

            head = last = parseEventOrNull(in);
            if (head == null) {
                throw new IOException("Empty trace input file");
            }
            int eventCount = 1;
            try {
                Event current;
                while ((current = parseEventOrNull(in)) != null) {
                    last.next = current;
                    last = current;
                    eventCount++;
                }
                return new ParsedTrace(head, eventCount);

            } catch (IOException e) {
                throw new IOException(
                        "Failed to parse trace after reading "
                                + eventCount
                                + " complete events: "
                                + e.getMessage(),
                        e
                );
            }
        }
    }

    /**
     * Parses a single event, or returns null if EOF is reached exactly at an
     * event boundary.
     */
    private static Event parseEventOrNull(DataInput in) throws IOException {
        final int opcode;

        try {
            opcode = in.readUnsignedByte();
        } catch (EOFException eof) {
            return null;
        }

        try {
            return parseEventPayload(opcode, in);
        } catch (EOFException eof) {
            EOFException truncated = new EOFException(
                    "Truncated event payload after opcode " + opcode
            );
            truncated.initCause(eof);
            throw truncated;
        }
    }

    /**
     * Parses a single event from the input stream.
     *
     * The first field is the opcode, encoded as an unsigned byte.
     */
    private static Event parseEventPayload(int opcode, DataInput in) throws IOException {
        return switch (opcode) {
            // ----------------- Constructors -----------------
            case TraceOpCodes.CONSTRUCTOR ->
                    new Constructor(in.readLong());

            case TraceOpCodes.CONSTRUCTOR_WITH_INPUT ->
                    new ConstructorWithInput(in.readLong(), in.readInt(), in.readFloat());

            case TraceOpCodes.CONSTRUCTOR_COPY_HASHMAP ->
                    new ConstructorCopyHashMap(in.readLong(), in.readLong());

            // ----------------- Read operations -----------------
            case TraceOpCodes.GET ->
                    new Get(in.readLong(), Math.toIntExact(in.readLong()), in.readInt());

            case TraceOpCodes.GET_OR_DEFAULT ->
                    new GetOrDefault(in.readLong(), Math.toIntExact(in.readLong()), in.readInt());

            case TraceOpCodes.CONTAINS_KEY ->
                    new ContainsKey(in.readLong(), Math.toIntExact(in.readLong()), in.readInt());

            // ----------------- Write operations -----------------
            case TraceOpCodes.PUT ->
                    new Put(in.readLong(), Math.toIntExact(in.readLong()), in.readInt());

            case TraceOpCodes.PUTALL_HASHMAP ->
                    new PutAllHashMap(in.readLong(), in.readLong());

            case TraceOpCodes.REMOVE ->
                    new Remove(in.readLong(), Math.toIntExact(in.readLong()), in.readInt());

            case TraceOpCodes.REPLACE ->
                    new Replace(in.readLong(), Math.toIntExact(in.readLong()), in.readInt());

            case TraceOpCodes.CLEAR ->
                    new Clear(in.readLong());

            case TraceOpCodes.PUT_IF_ABSENT ->
                    new PutIfAbsent(in.readLong(), Math.toIntExact(in.readLong()), in.readInt());

            // ----------------- Functional operations -----------------
            case TraceOpCodes.COMPUTE_IF_ABSENT_NON_NULL_MAPPING ->
                    new ComputeIfAbsentNotNullMapping(
                            in.readLong(),
                            Math.toIntExact(in.readLong()),
                            in.readInt()
                    );

            case TraceOpCodes.COMPUTE_IF_ABSENT_NULL_MAPPING ->
                    new ComputeIfAbsentNullMapping(
                            in.readLong(),
                            Math.toIntExact(in.readLong()),
                            in.readInt()
                    );

            // ----------------- Loops / bulk traversal -----------------
            case TraceOpCodes.FOR_EACH ->
                    new ForEach(in.readLong());

            // ----------------- Iterators -----------------
            case TraceOpCodes.KEY_ITERATOR ->
                    new KeyIterator(in.readLong(), in.readLong());

            case TraceOpCodes.VALUE_ITERATOR ->
                    new ValueIterator(in.readLong(), in.readLong());

            case TraceOpCodes.ENTRY_ITERATOR ->
                    new EntryIterator(in.readLong(), in.readLong());

            case TraceOpCodes.ITERATOR_NEXT ->
                    new IteratorNext(in.readLong(), in.readLong());

            case TraceOpCodes.ITERATOR_REMOVE ->
                    new IteratorRemove(in.readLong(), in.readLong());

            case TraceOpCodes.ABSTRACT_COLLECTION_REMOVE ->
                    new CollectionViewRemoveMarker(in.readLong());

            // ----------------- Unsupported / legacy direct operations -----------------
            case TraceOpCodes.CLONE ->
                    throw new IOException("Unsupported raw trace opcode: CLONE (" + opcode + ")");


            // ----------------- Spliterators -----------------
            case TraceOpCodes.ENTRIES_SPLITERATOR_FOREACH_REMAINING ->
                    new EntrySetSpliteratorForEachRemaining(in.readLong());

            case TraceOpCodes.KEY_SPLITERATOR_FOREACH_REMAINING ->
                    new KeysSpliteratorForEachRemaining(in.readLong());

            case TraceOpCodes.VALUES_SPLITERATOR_FOREACH_REMAINING ->
                    new ValuesSetSpliteratorForEachRemaining(in.readLong());

            case TraceOpCodes.SPLITERATOR_TRY_ADVANCE ->
                    new SpliteratorTryAdvance(in.readLong());

            case TraceOpCodes.SPLITERATOR_TRY_SPLIT ->
                    new SpliteratorTrySplit(in.readLong());

            default ->
                    throw new IOException("Unknown raw trace opcode: " + opcode);
        };
    }

    /**
     * SANITIZE pipeline:
     *
     * 0) remove all events Spliterator.tryAdvance or Spliterator.trySplit (not supported).
     * 1) remove all events whose mapID does not have a corresponding constructor event earlier in the trace.
     * 2) remove CollectionViewRemoveMarker events.
     *    The actual traversal/removal is represented by iterator events.
     * 3) compute the last usage for each map and inject FreeMapEvent(mapID) immediately after the last usage.
     * 4) coalesce iterator creation plus all IteratorNext events for iterators
     *    that do not perform Iterator.remove(), producing one MultiStepIteration(mapID, steps).
     *    This intentionally discards read-only interleavings to compact the replay trace.
     * 5) remove all iteration operations done in between a copy-constructor(HashMap) or putAll(HashMap).
     *    REMOVED: applied in the tracer.
     * 6) compute the last usage for each iterator and inject FreeIteratorEvent(iterID) immediately after the last usage.
     *
     * Returns the head of the sanitized linked list, or null for an empty trace.
     */
    private static SanitizedTrace sanitize(ParsedTrace parsedTrace) {
        Event head = parsedTrace.head;
        if (head == null) {
            return new SanitizedTrace(null);
        }

        // Note: it is possible that the head will be removed.
        // To avoid multiple checks, create a dummy node that acts as list head.
        head = new DummyHeadEvent(head);

        /*
         * Phase A:
         *  - save mapIDs of constructor events
         *  - delete events without a previous creation for the related mapID
         *  - remove CollectionViewRemoveMarker events
         *  - remove unsupported spliterator primitive events
         *  - store the last usage event for each map
         */

        LongOpenHashSet created = new LongOpenHashSet();
        Long2ObjectOpenHashMap<Event> lastMapUsageEvent = new Long2ObjectOpenHashMap<>();

        Event previous = head;

        // note: STEP 5 could be implemented in this loop to improve performance
        // however, the time-consuming postprocessing phase is the final ZIP writing
        // therefore, I suggest we keep this optimization for later, only after optimizing the output writing
        for (Event cur = head.next; cur != null; cur = cur.next) {
            Class<? extends Event> curClass = cur.getClass();
            long mapID = cur.mapID();

            if (curClass == SpliteratorTrySplit.class || curClass == SpliteratorTryAdvance.class) {
                previous.next = cur.next;
                continue;
            }

            if (isConstructorEvent(cur)) {
                created.add(cur.mapID());
                if (curClass == ConstructorCopyHashMap.class) {
                    lastMapUsageEvent.put(((ConstructorCopyHashMap) cur).otherMapID, cur);
                }
                lastMapUsageEvent.put(cur.mapID(), cur);
            } else {
                if (!created.contains(mapID)) {
                    previous.next = cur.next;
                    continue;
                }

                if (curClass == CollectionViewRemoveMarker.class) {
                    previous.next = cur.next;
                    continue;
                }

                lastMapUsageEvent.put(mapID, cur);

                if (curClass == PutAllHashMap.class) {
                    lastMapUsageEvent.put(((PutAllHashMap) cur).otherMapID, cur);
                }
            }

            previous = cur;
        }

        created = null;

        /*
         * Phase B:
         *  - complete steps 2 and 3
         *  - prepare step 4
         */

        ObjectOpenHashSet<IteratorID> iteratorsWithRemove = new ObjectOpenHashSet<>();
        int iteratorCreationCount = 0;


        for (Event cur = head.next; cur != null; cur = cur.next) {
            long mapID = cur.mapID();

            Event insertionPoint = cur;

            if (cur == lastMapUsageEvent.get(mapID)) {
                insertionPoint = insertAfter(insertionPoint, new FreeMapEvent(mapID));
            }

            if (cur instanceof ConstructorCopyHashMap copy) {
                long otherMapID = copy.otherMapID;

                if (otherMapID != mapID && cur == lastMapUsageEvent.get(otherMapID)) {
                    insertionPoint = insertAfter(insertionPoint, new FreeMapEvent(otherMapID));
                }
            } else if (cur instanceof PutAllHashMap putAll) {
                long otherMapID = putAll.otherMapID;

                if (otherMapID != mapID && cur == lastMapUsageEvent.get(otherMapID)) {
                    insertionPoint = insertAfter(insertionPoint, new FreeMapEvent(otherMapID));
                }
            }

            if (cur instanceof IteratorRemove ir) {
                iteratorsWithRemove.add(new IteratorID(mapID, ir.iteratorID));
            } else if (cur instanceof IteratorCreation) {
                iteratorCreationCount++;
            }

        }

        lastMapUsageEvent = null;

        /*
         * Phase C:
         *  - coalesce iterator-next primitives into MultiStepIteration
         *  - identify last usages for each iterator
         */

        HashMap<IteratorID, MultiStepIteration> multiStepIterations =
                HashMap.newHashMap(iteratorCreationCount);

        HashMap<IteratorID, Event> lastIteratorUsageEvent = new HashMap<>();

        previous = head;

        for (Event cur = head.next; cur != null; cur = cur.next) {
            long mapID = cur.mapID();

            if (cur instanceof IteratorEvent ie) {
                IteratorID iteratorID = new IteratorID(mapID, ie.iteratorID);

                if (!iteratorsWithRemove.contains(iteratorID)) {
                    if (cur instanceof IteratorCreation) {
                        MultiStepIteration multiStepIteration = new MultiStepIteration(mapID);
                        multiStepIterations.put(iteratorID, multiStepIteration);

                        previous.next = multiStepIteration;
                        multiStepIteration.next = cur.next;
                        previous = multiStepIteration;
                    } else if (cur instanceof IteratorNext) {
                        MultiStepIteration multiStepIteration = multiStepIterations.get(iteratorID);
                        if (multiStepIteration == null) {
                            throw new RuntimeException(
                                    "Unexpected trace state: IteratorNext without IteratorCreation"
                            );
                        }

                        multiStepIteration.steps++;
                        previous.next = cur.next;
                    } else {
                        throw new RuntimeException(
                                "Unexpected trace state: got iterator event "
                                        + cur.getClass()
                                        + " when coalescing iteration"
                        );
                    }

                    continue;
                } else {
                    lastIteratorUsageEvent.put(iteratorID, cur);
                }
            }

            previous = cur;
        }

        /*
         * Phase D:
         *  - inject FreeIteratorEvent after the last usage of each non-coalesced iterator
         */

        for (Event cur = head.next; cur != null; cur = cur.next) {
            long mapID = cur.mapID();

            if (cur instanceof IteratorEvent ie) {
                IteratorID iteratorID = new IteratorID(mapID, ie.iteratorID);

                if (cur == lastIteratorUsageEvent.get(iteratorID)) {
                    insertAfter(cur, new FreeIteratorEvent(mapID, iteratorID.iteratorID));
                }
            }
        }

        return new SanitizedTrace(head.next);
    }


    private static void emitPostProcessedTrace(String outputTraceFileName, SanitizedTrace trace)
            throws IOException {

        Event head = trace.head;
        int eventsCount = 0;

        /*
         * Replay trace format:
         *  - long[] ops.bin
         *      packed as <unsigned-byte opcode, int operand index>
         *  - int[] keyIDs.bin
         *  - int[] keyHashCodes.bin
         *  - trace-replay-sizes.csv
         *  - events-stats.csv
         */

        Long2IntOpenHashMap mapIdToIndex = new Long2IntOpenHashMap();
        mapIdToIndex.defaultReturnValue(MISSING_INDEX);

        IntPriorityQueue freeMapIndexes = new IntArrayFIFOQueue();
        int mapIndexSlots = 0;

        Object2IntOpenHashMap<IteratorID> iteratorIdToIndex = new Object2IntOpenHashMap<>();
        iteratorIdToIndex.defaultReturnValue(MISSING_INDEX);

        IntPriorityQueue freeIteratorIndexes = new IntArrayFIFOQueue();
        int iteratorIndexSlots = 0;

        class MutableInt {
            int val = 0;

            MutableInt() {}

            MutableInt(int v) {
                val = v;
            }
        }

        Set<Class<? extends Event>> eventTypes = getEventClasses();

        HashMap<Class<? extends Event>, MutableInt> traceStats =
                HashMap.newHashMap(eventTypes.size());

        eventTypes.forEach(e -> traceStats.put(e, new MutableInt()));

        for (Event cur = head; cur != null; cur = cur.next) {
            traceStats.get(cur.getClass()).val++;

            try {
                eventsCount = Math.incrementExact(eventsCount);
            } catch (ArithmeticException e) {
                throw new RuntimeException("Too many events: eventsCount overflow", e);
            }

            if (isConstructorEvent(cur)) {
                if (!freeMapIndexes.isEmpty()) {
                    mapIdToIndex.put(cur.mapID, freeMapIndexes.dequeueInt());
                } else {
                    mapIdToIndex.put(cur.mapID, mapIndexSlots++);
                }
            } else if (cur.getClass() == FreeMapEvent.class) {
                freeMapIndexes.enqueue(
                        requireMapIndex(mapIdToIndex, cur.mapID, "freeing map")
                );
            } else if (cur instanceof IteratorCreation ic) {
                IteratorID iteratorID = new IteratorID(cur.mapID, ic.iteratorID);

                if (!freeIteratorIndexes.isEmpty()) {
                    iteratorIdToIndex.put(iteratorID, freeIteratorIndexes.dequeueInt());
                } else {
                    iteratorIdToIndex.put(iteratorID, iteratorIndexSlots++);
                }
            } else if (cur instanceof FreeIteratorEvent fie) {
                freeIteratorIndexes.enqueue(
                        requireIteratorIndex(
                                iteratorIdToIndex,
                                new IteratorID(cur.mapID, fie.iteratorID),
                                "freeing iterator"
                        )
                );
            }
        }

        traceStats.put(Event.class, new MutableInt(eventsCount));

        freeMapIndexes = null;
        freeIteratorIndexes = null;

        long[] opCodesWithOperandIDIndex = new long[eventsCount];
        int[] keyIDs = new int[eventsCount];
        int[] keyHashCodes = new int[eventsCount];

        int i = 0;

        for (Event cur = head; cur != null; cur = cur.next) {
            final int operandIndex;

            if (cur instanceof IteratorEvent ie && !(cur instanceof IteratorCreation)) {
                operandIndex = requireIteratorIndex(
                        iteratorIdToIndex,
                        new IteratorID(cur.mapID, ie.iteratorID),
                        "encoding iterator operand for " + cur.getClass().getSimpleName()
                );
            } else {
                operandIndex = requireMapIndex(
                        mapIdToIndex,
                        cur.mapID,
                        "encoding map operand for " + cur.getClass().getSimpleName()
                );
            }

            opCodesWithOperandIDIndex[i] =
                    TraceInstructionCodec.encodeOpcodeOperand(cur.opcode(), operandIndex);

            keyIDs[i] = switch (cur) {
                case ComputeIfAbsentNotNullMapping e -> e.keyID;
                case ComputeIfAbsentNullMapping e -> e.keyID;
                case ContainsKey e -> e.keyID;
                case Get e -> e.keyID;
                case GetOrDefault e -> e.keyID;
                case Put e -> e.keyID;
                case PutIfAbsent e -> e.keyID;
                case Remove e -> e.keyID;
                case Replace e -> e.keyID;

                // HACKS -- use keyID to store non-key operands
                case ConstructorWithInput e -> e.param1;

                default -> 0;
            };

            keyHashCodes[i] = switch (cur) {
                case ComputeIfAbsentNotNullMapping e -> e.keyHashCode;
                case ComputeIfAbsentNullMapping e -> e.keyHashCode;
                case ContainsKey e -> e.keyHashCode;
                case Get e -> e.keyHashCode;
                case GetOrDefault e -> e.keyHashCode;
                case Put e -> e.keyHashCode;
                case PutIfAbsent e -> e.keyHashCode;
                case Remove e -> e.keyHashCode;
                case Replace e -> e.keyHashCode;

                // HACKS -- use keyHashCode to store non-key operands
                case ConstructorWithInput e -> Float.floatToIntBits(e.param2);
                case ConstructorCopyHashMap e ->
                        requireMapIndex(mapIdToIndex, e.otherMapID, "encoding copy-constructor source");

                case PutAllHashMap e ->
                        requireMapIndex(mapIdToIndex, e.otherMapID, "encoding putAll source");

                case MultiStepIteration e -> e.steps;

                case IteratorCreation e ->
                        requireIteratorIndex(
                                iteratorIdToIndex,
                                new IteratorID(cur.mapID, e.iteratorID),
                                "encoding iterator creation"
                        );

                default -> 0;
            };

            i++;
        }

        try (FileOutputStream fos = new FileOutputStream(outputTraceFileName);
             BufferedOutputStream bos = new BufferedOutputStream(fos, 1 << 25);
             ZipOutputStream zipOut = new ZipOutputStream(bos);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                     zipOut,
                     java.nio.charset.StandardCharsets.UTF_8))) {

            zipOut.setLevel(Deflater.BEST_SPEED);

            writeLongs(zipOut, writer, "ops.bin", opCodesWithOperandIDIndex);
            writeInts(zipOut, writer, "keyIDs.bin", keyIDs);
            writeInts(zipOut, writer, "keyHashCodes.bin", keyHashCodes);

            // ----------- events-stats.csv -----------
            zipOut.putNextEntry(new ZipEntry("events-stats.csv"));

            List<Map.Entry<Class<? extends Event>, MutableInt>> entries =
                    new ArrayList<>(traceStats.entrySet());

            entries.sort((a, b) -> {
                if (a.getKey() == Event.class) {
                    return -1;
                }
                if (b.getKey() == Event.class) {
                    return 1;
                }
                return a.getKey().getSimpleName().compareTo(b.getKey().getSimpleName());
            });

            for (Map.Entry<Class<? extends Event>, MutableInt> e : entries) {
                writeCsvLine(writer, e.getKey().getSimpleName(), e.getValue().val);
            }

            writer.flush();
            zipOut.closeEntry();

            // ----------- trace-replay-sizes.csv -----------
            zipOut.putNextEntry(new ZipEntry("trace-replay-sizes.csv"));
            writeCsvLine(writer, "events", eventsCount);
            writeCsvLine(writer, "maps", mapIndexSlots);
            writeCsvLine(writer, "iterators", iteratorIndexSlots);
            writer.flush();
            zipOut.closeEntry();
        }
    }

    private static void writeCsvLine(BufferedWriter writer, String name, int value)
            throws IOException {
        writeCsvLine(writer, name, Integer.toString(value));
    }

    private static void writeCsvLine(BufferedWriter writer, String name, String value)
            throws IOException {
        writer.write(name);
        writer.write(',');
        writer.write(value);
        writer.newLine();
    }

    private static void writeInts(
            ZipOutputStream zipOut,
            BufferedWriter writer,
            String filename,
            int[] values
    ) throws IOException {

        zipOut.putNextEntry(new ZipEntry(filename));

        final int chunkInts = 1 << 16;
        final byte[] buf = new byte[chunkInts * Integer.BYTES];

        int i = 0;
        while (i < values.length) {
            int n = Math.min(chunkInts, values.length - i);
            int p = 0;

            for (int j = 0; j < n; j++) {
                int v = values[i++];

                buf[p++] = (byte) (v >>> 24);
                buf[p++] = (byte) (v >>> 16);
                buf[p++] = (byte) (v >>> 8);
                buf[p++] = (byte) v;
            }

            zipOut.write(buf, 0, p);
        }

        zipOut.closeEntry();

        if (WRITE_VERBOSE) {
            zipOut.putNextEntry(new ZipEntry(filename + ".txt"));

            for (int v : values) {
                writer.write(Integer.toString(v));
                writer.newLine();
            }

            writer.flush();
            zipOut.closeEntry();
        }
    }

    private static void writeLongs(
            ZipOutputStream zipOut,
            BufferedWriter writer,
            String filename,
            long[] values
    ) throws IOException {

        zipOut.putNextEntry(new ZipEntry(filename));

        final int chunkLongs = 1 << 15;
        final byte[] buf = new byte[chunkLongs * Long.BYTES];

        int i = 0;
        while (i < values.length) {
            int n = Math.min(chunkLongs, values.length - i);
            int p = 0;

            for (int j = 0; j < n; j++) {
                long v = values[i++];

                buf[p++] = (byte) (v >>> 56);
                buf[p++] = (byte) (v >>> 48);
                buf[p++] = (byte) (v >>> 40);
                buf[p++] = (byte) (v >>> 32);
                buf[p++] = (byte) (v >>> 24);
                buf[p++] = (byte) (v >>> 16);
                buf[p++] = (byte) (v >>> 8);
                buf[p++] = (byte) v;
            }

            zipOut.write(buf, 0, p);
        }

        zipOut.closeEntry();

        if (WRITE_VERBOSE) {
            zipOut.putNextEntry(new ZipEntry(filename + ".txt"));

            for (long v : values) {
                writer.write(Long.toString(v));
                writer.newLine();
            }

            writer.flush();
            zipOut.closeEntry();
        }
    }

    private static int requireMapIndex(
            Long2IntOpenHashMap mapIdToIndex,
            long mapID,
            String context
    ) {
        int index = mapIdToIndex.get(mapID);

        if (index == MISSING_INDEX) {
            throw new IllegalStateException(
                    "Missing map index for mapID " + mapID + " while " + context
            );
        }

        return index;
    }

    private static int requireIteratorIndex(
            Object2IntOpenHashMap<IteratorID> iteratorIdToIndex,
            IteratorID iteratorID,
            String context
    ) {
        int index = iteratorIdToIndex.getInt(iteratorID);

        if (index == MISSING_INDEX) {
            throw new IllegalStateException(
                    "Missing iterator index for mapID "
                            + iteratorID.mapID()
                            + ", iteratorID "
                            + iteratorID.iteratorID()
                            + " while "
                            + context
            );
        }

        return index;
    }

    @SuppressWarnings("unchecked")
    private static Set<Class<? extends Event>> getEventClasses() {
        Set<Class<? extends Event>> eventClasses = new HashSet<>();
        Queue<Class<?>> queue = new LinkedList<>();

        queue.add(Event.class);

        while (!queue.isEmpty()) {
            Class<?> cur = queue.poll();
            eventClasses.add((Class<? extends Event>) cur);

            if (cur.isSealed()) {
                queue.addAll(List.of(cur.getPermittedSubclasses()));
            }
        }

        return eventClasses;
    }

    /*
     * Helper: determine whether the event is a map-creation event.
     *
     * Deliberately uses event types, not opcode ranges. CONSTRUCTOR_COPY_HASHMAP_END
     * is not a creation event.
     */
    private static boolean isConstructorEvent(Event e) {
        return e instanceof Constructor
                || e instanceof ConstructorWithInput
                || e instanceof ConstructorCopyHashMap;
    }


    private static Event insertAfter(Event insertionPoint, Event inserted) {
        inserted.next = insertionPoint.next;
        insertionPoint.next = inserted;
        return inserted;
    }
}