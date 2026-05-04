import java.util.*;

public class Test {

    public static void main(String[] args) {

        // --- 1. Create maps using all public constructors ---
        HashMap<Long, String> map1 = new HashMap<>();
        HashMap<Long, String> map2 = new HashMap<>(10);
        HashMap<Long, String> map3 = new HashMap<>(10, 0.75f);
        
        // --- 2. Populate initial maps ---
        map1.put(1L, "A");
        map2.put(2L, "B");
        map3.put(3L, "C");

        HashMap<Long, String> map4 = new HashMap<>(map1);
        map4.put(4L, "D");

        // --- 3. Interleaved operations ---

        // Put / PutIfAbsent
        map1.put(5L, "E");
        map2.putIfAbsent(2L, "B-Alt");
        map3.put(6L, "F");
        map4.putIfAbsent(4L, "D-Alt");

        // Get / GetOrDefault
        map1.get(1L);
        map2.getOrDefault(2L, "Default");
        map3.get(6L);
        map4.getOrDefault(10L, "Default");

        // Remove (both versions)
        map1.remove(5L);
        map2.remove(2L, "B-Alt");
        map3.remove(6L);
        map4.remove(4L, "D");

        // Replace (both versions)
        map1.replace(1L, "A-Replaced");
        map2.replace(2L, "B", "B-New");

        // Compute / ComputeIfAbsent / ComputeIfPresent
        map1.compute(1L, (k, v) -> v + "-C");
        map2.computeIfAbsent(10L, k -> "X");
        map3.computeIfPresent(3L, (k, v) -> v + "-P");

        // merge
        map1.merge(1L, "-M", String::concat);
        map2.merge(10L, "Y", String::concat);
        map3.merge(3L, "-Z", String::concat);

        // putAll
        HashMap<Long, String> extra = new HashMap<>();
        extra.put(20L, "Z");
        extra.put(30L, "Y");
        map4.putAll(extra);

        // Iterators with remove
        Iterator<Long> kit = map1.keySet().iterator();
        if (kit.hasNext()) {
            kit.next();
            kit.remove();
        }

        Iterator<String> vit = map2.values().iterator();
        if (vit.hasNext()) {
            vit.next();
            vit.remove();
        }

        Iterator<Map.Entry<Long, String>> eit = map3.entrySet().iterator();
        if (eit.hasNext()) {
            eit.next();
            eit.remove();
        }

        // forEach
        map1.forEach((k, v) -> {
        });
        map2.forEach((k, v) -> {
        });
        map3.forEach((k, v) -> {
        });
        map4.forEach((k, v) -> {
        });

        // equals
        map1.equals(new HashMap<>(map1));
        map2.equals(new HashMap<>(map2));
        map3.equals(new HashMap<>(map3));
        map4.equals(new HashMap<>(map4));

        // Clear
        map1.clear();
        map2.clear();
        map3.clear();
        map4.clear();

        System.out.println("Deterministic HashMap test finished.");
    }
}
