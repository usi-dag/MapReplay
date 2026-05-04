package ch.usi.inf.dag.mapreplay.tracer.agent;

public class TracerActivator {
    public static void premain(String agentArgs) {
        jdk.internal.mapreplay.NativeTracer.ready = true;
    }
}