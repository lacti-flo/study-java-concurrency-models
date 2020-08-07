package day03;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class Agent<T> {
	private final Queue<Function<T, T>> mapperQueue = new ConcurrentLinkedQueue<>();
	private final AtomicBoolean acquired = new AtomicBoolean(false);
	private T state;
	
	public Agent(T initial) {
		state = initial;
	}
	
	public void send(Function<T, T> mapper) {
		mapperQueue.add(mapper);
		tryToProcess();
	}
	
	private void tryToProcess() {
		if (!acquired.compareAndSet(false, true)) {
			return;
		}
		// Safe area
		while (!mapperQueue.isEmpty()) {
			state = mapperQueue.poll().apply(state);
		}
		if (!acquired.compareAndSet(true, false)) {
			throw new RuntimeException("Broken!");
		}
		// Unsafe area
		if (!mapperQueue.isEmpty()) {
			// 왜 이걸 해야 하나?
			tryToProcess();
		}
	}
	
	public T get() {
		return state;
	}
	
	public static void main(String[] args) throws InterruptedException {
		final Agent<Integer> counter = new Agent<>(0);
		final int threadCount = 256;
		final int testCount = 12345;
		final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		for (int i = 0; i < threadCount; ++i) {
			executor.submit(() -> {
				for (int j = 0; j < testCount; ++j) {
					counter.send(v -> v + 1);
				}
			});
		}
		executor.shutdown();
		executor.awaitTermination(5, TimeUnit.SECONDS);
		
		if (counter.get() != threadCount * testCount) {
			throw new RuntimeException("Broken!");
		}
	}
}
