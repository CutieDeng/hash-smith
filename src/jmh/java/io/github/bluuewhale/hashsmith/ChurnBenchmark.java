package io.github.bluuewhale.hashsmith;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

/**
 * Steady-size churn: every operation removes a present key and puts an absent one, both timed, so the
 * map keeps {@code size} entries. MapBenchmark's PutMiss does the remove in a per-invocation setup and
 * times only the put, which leaves out the tombstone clean-up SwissMap runs inside remove().
 *
 * <p>Keys and misses are those of MapBenchmark.PutMissState (UUID strings, Random(456)). Each fork builds
 * a single map, so the measured map does not share the heap with the others.
 */
@Fork(
	value = 2,
	jvmArgsAppend = {
		"--add-modules=jdk.incubator.vector",
		"--enable-preview"
	}
)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ChurnBenchmark {

	private static String randomUuidString(Random rnd) {
		return new UUID(rnd.nextLong(), rnd.nextLong()).toString();
	}

	private static void generateKeysAndMisses(Random rnd, String[] keys, String[] misses) {
		int size = keys.length;
		var set = new java.util.HashSet<String>(size * 2);
		for (int i = 0; i < size; i++) {
			String k;
			do { k = randomUuidString(rnd); } while (!set.add(k));
			keys[i] = k;
		}
		for (int i = 0; i < size; i++) {
			String miss;
			do { miss = randomUuidString(rnd); } while (set.contains(miss));
			misses[i] = miss;
			set.add(miss);
		}
	}

	@State(Scope.Thread)
	public static class JdkState {
		@Param({ "12000", "48000", "196000", "784000" }) // load factor equals to 74.x% (right before resizing)
		int size;

		int idx;
		String[] keys;   // keys currently present in the map
		String[] misses; // keys currently absent from the map
		HashMap<String, Object> jdk;

		@Setup(Level.Trial)
		public void initKeys() {
			keys = new String[size];
			misses = new String[size];
			generateKeysAndMisses(new Random(456), keys, misses);
		}

		@Setup(Level.Iteration)
		public void resetMap() {
			idx = 0;
			jdk = new HashMap<>();
			for (int i = 0; i < size; i++) jdk.put(keys[i], "dummy");
		}
	}

	@State(Scope.Thread)
	public static class SwissState {
		@Param({ "12000", "48000", "196000", "784000" }) // load factor equals to 74.x% (right before resizing)
		int size;

		int idx;
		String[] keys;   // keys currently present in the map
		String[] misses; // keys currently absent from the map
		SwissMap<String, Object> swiss;

		@Setup(Level.Trial)
		public void initKeys() {
			keys = new String[size];
			misses = new String[size];
			generateKeysAndMisses(new Random(456), keys, misses);
		}

		@Setup(Level.Iteration)
		public void resetMap() {
			idx = 0;
			swiss = new SwissMap<>();
			for (int i = 0; i < size; i++) swiss.put(keys[i], "dummy");
		}
	}

	@Benchmark
	public Object jdkChurn(JdkState s) {
		int i = s.idx;
		String evict = s.keys[i], add = s.misses[i];
		s.jdk.remove(evict);
		Object r = s.jdk.put(add, "dummy");
		s.keys[i] = add;
		s.misses[i] = evict;
		s.idx = i + 1 == s.size ? 0 : i + 1;
		return r;
	}

	@Benchmark
	public Object swissChurn(SwissState s) {
		int i = s.idx;
		String evict = s.keys[i], add = s.misses[i];
		s.swiss.remove(evict);
		Object r = s.swiss.put(add, "dummy");
		s.keys[i] = add;
		s.misses[i] = evict;
		s.idx = i + 1 == s.size ? 0 : i + 1;
		return r;
	}
}
