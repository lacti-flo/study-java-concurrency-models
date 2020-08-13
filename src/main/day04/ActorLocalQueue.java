package day04;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ActorLocalQueue<A> implements ActorQueue<A> {
	
	private final Queue<Consumer<A>> taskQueue = new ConcurrentLinkedQueue<>();
	private final AtomicInteger taskCount = new AtomicInteger(0);
	
	@Override
	public Enqueued enqueue(Consumer<A> mail) {
		taskQueue.add(mail);
		final boolean acquired = taskCount.getAndIncrement() == 0;
		return new Enqueued(acquired);
	}
	
	@Override
	public List<Consumer<A>> flush() {
		final List<Consumer<A>> tasks = new ArrayList<>();
		while (!taskQueue.isEmpty()) {
			tasks.add(taskQueue.poll());
		}
		return tasks;
	}
	
	@Override
	public Completed complete(int processedCount) {
		final boolean hasMore = taskCount.addAndGet(-processedCount) > 0;
		return new Completed(hasMore);
	}
}
