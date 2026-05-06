# MapReplay

MapReplay is an experimental Java HashMap trace-replay framework.

It records selected `java.util.HashMap` operations from a target JVM execution, post-processes the raw trace into a compact replay format, and replays the resulting trace as a benchmark workload.

The project is intended for research and benchmarking, not as a general-purpose production profiler.

## Status

This is an initial public release.

The implementation currently targets a specific Java version and relies on patching `java.base`. Compatibility with arbitrary JDK versions is not guaranteed.

## Repository layout

```text
.
├── README.md
├── LICENSE
├── CHANGELOG.md
├── CONTRIBUTING.md
├── trace-format/
│   └── Shared opcode and trace-format definitions
├── tracer/
│   ├── src/java-base-patch/
│   ├── src/java-agent/
│   ├── src/native/
│   ├── bin/mapreplay-java
│   └── build.xml
├── postprocessor/
│   └── Raw-trace post-processing tool
└── replay/
    └── Trace replay and benchmark execution
```

## Components

### Trace format

The `trace-format` module contains shared trace constants and metadata used by the tracer, postprocessor, and replay components.

The opcode values are intentionally explicit constants, not enum ordinals. This avoids accidental trace-format changes when source declarations are reordered.

### Tracer

The tracer records HashMap operations from a target Java application.

It consists of:

* a patched `java.base` module containing an instrumented `java.util.HashMap`;
* a Java activation agent that enables tracing after early JVM initialization;
* a native JVMTI/JNI agent that writes binary trace data;
* a Java launcher wrapper, `mapreplay-java`, that runs the target application with the required JVM options.

The tracer produces a raw `.rip` trace file.

### Postprocessor

The postprocessor converts the raw `.rip` trace into the replay format.

It performs trace sanitation and compaction, including:

* removing unsupported trace events;
* removing events for maps that were never seen as constructed;
* removing marker-only events that are not replay operations;
* inserting synthetic map and iterator free events;
* compacting read-only iterator traversal into multi-step iteration events.

The postprocessor produces a `.zip` replay trace.

### Replay

The replay component loads the post-processed trace and executes the encoded HashMap operations.

The replay is intended to reproduce the HashMap workload shape of the original execution, not the complete target application semantics.

## Build

### Requirements

* Linux
* JDK 21 or the JDK version matching the patched `HashMap` source
* Apache Ant, for the tracer
* Apache Maven, for the Maven modules
* `gcc`, for the native JVMTI/JNI agent

`JAVA_HOME` must point to the JDK used to build and run the tracer.

### Build all Maven modules

From the repository root:

```bash
mvn clean package
```

### Build the tracer

From the `tracer/` directory:

```bash
ant clean build
```

Expected tracer output:

```text
tracer/dist/bin/mapreplay-java
tracer/dist/lib/mapreplay-patch.jar
tracer/dist/lib/mapreplay-agent.jar
tracer/dist/lib/libmapreplay-tracer.so
```

## Basic usage

### 1. Trace a Java application

Use the `mapreplay-java` wrapper instead of `java`:

```bash
TRACE_FILE=trace ./tracer/dist/bin/mapreplay-java -cp target/classes com.example.Main
```

This writes:

```text
trace.rip
```

Optional verbose tracing:

```bash
LOG_TRACER=true TRACE_FILE=trace ./tracer/dist/bin/mapreplay-java -cp target/classes com.example.Main
```

This writes:

```text
trace.rip
trace.log
```

### 2. Post-process the raw trace

```bash
java -jar postprocessor/target/postprocessor.jar trace.rip trace.zip
```

### 3. Replay the processed trace

```bash
java -jar replay/target/replay.jar \
  -p zipPath=trace.zip \
  -f 5 \
  -w 10 -wi 5 \
  -i 5 -r 10 \
  -rf JSON -rff out.json
  ```

Exact replay arguments depend on the current replay benchmark entry point. See `docs/usage.md`.

## Trace files

### Raw trace

The raw `.rip` file is a compact binary event stream written by the native tracer.

Each event starts with a one-byte opcode followed by opcode-specific operands.

### Replay trace

The post-processed `.zip` file contains columnar binary arrays used by the replay engine, typically including:

```text
ops.bin
keyIDs.bin
keyHashCodes.bin
events-stats.csv
trace-replay-sizes.csv
```

See `docs/trace-format.md` for details.

## Design constraints

MapReplay deliberately trades generality for low-overhead, HashMap-specific tracing.

Important consequences:

* only selected `HashMap` operations are traced;
* the tracer modifies `java.base` and is therefore JDK-sensitive;
* the trace is not a complete JVM execution trace;
* replay preserves the HashMap workload structure, not full application semantics;
* unsupported operations may be removed, approximated, or rejected by the postprocessor.

## Limitations

Known limitations include:

* Linux-only native build in the current Ant configuration;
* tight coupling to the patched JDK `HashMap` source version;
* incomplete coverage of all `Map` and collection-view operations;
* unsupported or partially supported spliterator operations;
* no guarantee that traces produced with one opcode layout remain compatible with future experimental formats unless explicitly versioned.

See `docs/limitations.md` for a more complete list.

## Development notes

The trace opcode definitions are shared through the `trace-format` module.

Do not duplicate opcode definitions between tracer, postprocessor, and replay. If a new trace event is added, update the shared opcode definitions first, then update:

1. the patched `HashMap` instrumentation;
2. `NativeTracer`;
3. raw-trace parsing in the postprocessor;
4. postprocessor sanitation and emission logic;
5. replay dispatch logic;
6. trace-format documentation.

## License

MapReplay is distributed under the GNU General Public License version 2 only,
with the Classpath Exception:

`GPL-2.0-only WITH Classpath-exception-2.0`

Some files under `tracer/src/java-base-patch/` are modified OpenJDK source files
and retain their original Oracle/OpenJDK copyright notices and license headers.
