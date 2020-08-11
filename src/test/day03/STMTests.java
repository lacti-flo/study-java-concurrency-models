package day03;

import org.junit.jupiter.api.Test;
import support.Threaded;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class STMTests {
	@Test
	public void simpleTransaction() {
		final int testCount = 193;
		for (int i = 0; i < testCount; ++i) {
			final long initialA = 10000L;
			final long initialB = 20000L;
			final STM.Ref<Long> a = new STM.Ref<>(initialA);
			final STM.Ref<Long> b = new STM.Ref<>(initialB);
			
			final STM stm = new STM();
			final List<Boolean> results = new CopyOnWriteArrayList<>();
			Threaded.test(16, 4, () -> {
				boolean success = stm.start(
						(tx) -> tx.join(a, b),
						(tx) -> {
							tx.update(a, a.value + 100);
							tx.update(b, b.value - 100);
						});
				results.add(success);
			});
			
			final long successCount = results.stream().filter(e -> e).count();
			System.out.println("Success count: " + successCount);
			final long expectedA = initialA + successCount * 100;
			if (a.value != initialA + successCount * 100) {
				throw new RuntimeException(String.format("Broken A: expected=%d, actual=%d", expectedA, a.value));
			}
			final long expectedB = initialB - successCount * 100;
			if (b.value != initialB - successCount * 100) {
				throw new RuntimeException(String.format("Broken B: expected=%d, actual=%d", expectedB, b.value));
			}
		}
	}
}
