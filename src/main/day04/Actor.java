package day04;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

public class Actor<T> {
	private final Queue<Consumer<T>> mailBox = new ConcurrentLinkedQueue<>();
	private final AtomicInteger mailCount = new AtomicInteger(0);
	private final T target;
	
	public Actor(T target) {
		this.target = target;
	}
	
	public void send(Consumer<T> mail) {
		mailBox.add(mail);
		if (mailCount.getAndIncrement() == 0) {
			processMails();
		}
	}
	
	public <R> CompletableFuture<R> query(Function<T, R> mapper) {
		final CompletableFuture<R> future = new CompletableFuture<>();
		send(state -> future.complete(mapper.apply(state)));
		return future;
	}
	
	private void processMails() {
		int processed;
		do {
			processed = 0;
			while (!mailBox.isEmpty()) {
				mailBox.poll().accept(target);
				++processed;
			}
		} while (mailCount.addAndGet(-processed) > 0);
	}
}
