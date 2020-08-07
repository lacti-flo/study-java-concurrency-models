package day04;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class Actor<T> {
	private final Queue<Function<T, T>> mapperQueue = new ConcurrentLinkedQueue<>();
	private final AtomicBoolean acquired = new AtomicBoolean(false);
	private T state;
	
	public Actor(T initial) {
		state = initial;
	}
	
	public void send(Function<T, T> mapper) {
		mapperQueue.add(mapper);
		tryToProcess();
	}
	
	public <R> CompletableFuture<R> query(Function<T, R> mapper) {
		final CompletableFuture<R> future = new CompletableFuture<>();
		send(state -> {
			future.complete(mapper.apply(state));
			return state;
		});
		return future;
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
	
	static class Counter {
		private final Actor<Integer> actor = new Actor<>(0);
		
		public void increment() {
			actor.send(v -> v + 1);
		}
		
		public CompletableFuture<Integer> get() {
			return actor.query(v -> v);
		}
	}
	
	public static void main(String[] args) {
		Counter counter = new Counter();
		counter.increment();
		counter.get().thenAccept(System.out::println);
	}
}
