package awaitable;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class AwaitableTests {
	final Awaiter awaiter = new Awaiter(4);
	final int testCount = 100;
	
	@Test
	public void simpleAwaiter() throws ExecutionException, InterruptedException {
		awaiter.start();
		final int actual = sumIntegerAsync(testCount).get();
		awaiter.shutdown();
		if (actual != (testCount * (testCount + 1)) / 2) {
			throw new RuntimeException("Broken! expected=1000, actual=" + actual);
		}
	}
	
	private CompletableFuture<Integer> sumIntegerAsync(int remain) {
		return awaiter.async(() -> {
			if (remain == 0) {
				return 0;
			}
			final int current = awaiter.await(supplyIntegerAsync(remain, (testCount - remain) * 2));
			final int recurred = awaiter.await(sumIntegerAsync(remain - 1));
			return current + recurred;
		});
	}
	
	private CompletableFuture<Integer> supplyIntegerAsync(int value, int delayMillis) {
		return awaiter.async(() -> {
			System.out.println("[Tests] Would sleep: " + delayMillis);
			awaiter.sleep(delayMillis);
			System.out.println("[Tests] Value resolved: " + value);
			return value;
		});
	}
}
