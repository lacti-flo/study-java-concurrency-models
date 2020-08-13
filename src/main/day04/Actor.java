package day04;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

public class Actor<A> {
	// queue만 주입해서 분산 actor로 확장 가능할까?
	// actor system 수준으로 필요한 것은 무엇이 있을까?
	private final ActorQueue<A> queue;
	private final A target;
	
	public Actor(final ActorQueue<A> queue,
	             final A target) {
		this.queue = queue;
		this.target = target;
	}
	
	// 메시지가 아니고 Consumer이어도 되는가? 문제가 없을까?
	public void run(final Consumer<A> task) {
		if (queue.enqueue(task).acquired) {
			processTasks();
		}
	}
	
	// 메시지가 아니고 Function이어도 되는가? 문제가 없을까?
	public <R> CompletableFuture<R> query(final Function<A, R> mapper) {
		final CompletableFuture<R> future = new CompletableFuture<>();
		run(state -> future.complete(mapper.apply(state)));
		return future;
	}
	
	private void processTasks() {
		// fairness 주의
		while (true) {
			final List<Consumer<A>> tasks = queue.flush();
			// 효과적인 에러 처리를 위해서는 어떻게 하는게 좋을까?
			// 에러 처리 전략은 actor와 분리될 수 있는가?
			tasks.forEach(task -> task.accept(target));
			if (!queue.complete(tasks.size()).hasMore) {
				break;
			}
		}
	}
}
