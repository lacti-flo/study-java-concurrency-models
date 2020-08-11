package day03;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
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
			// 왜 이걸 해야 하나? 이게 최선입니까?
			tryToProcess();
		}
	}
	
	public T get() {
		return state;
	}
}
