package support;

import java.util.concurrent.*;

public class Threaded {
	
	public static AssertWith test(final Runnable doIn) {
		return test(179, 131, doIn);
	}
	
	public static AssertWith test(final int threadCount,
	                       final int testCount,
	                       final Runnable doIn) {
		final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		final CountDownLatch startSignal = new CountDownLatch(1);
		for (int i = 0; i < threadCount; ++i) {
			executor.submit(() -> {
				hideInterrupted(startSignal::await);
				for (int j = 0; j < testCount; ++j) {
					doIn.run();
				}
			});
		}
		startSignal.countDown();
		executor.shutdown();
		hideInterrupted(() -> executor.awaitTermination(5, TimeUnit.SECONDS));
		return new AssertWith(threadCount * testCount);
	}
	
	private static void hideInterrupted(final InterruptibleRun interruptible) {
		try {
			interruptible.run();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	
	private interface InterruptibleRun {
		void run() throws InterruptedException;
	}
	
	public static class AssertWith {
		private final int expected;
		
		private AssertWith(final int expected) {
			this.expected = expected;
		}
		
		public void shouldEqual(int actual) {
			if (expected != actual) {
				throw new RuntimeException(String.format("Broken! expected=%d, actual=%d", expected, actual));
			}
		}
		
		public void shouldEqual(CompletableFuture<Integer> future) {
			future.thenAccept(actual -> {
				if (expected != actual) {
					throw new RuntimeException(String.format("Broken! expected=%d, actual=%d", expected, actual));
				}
			}).join();
		}
	}
}
