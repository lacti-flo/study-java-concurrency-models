package day04;

import java.util.List;
import java.util.function.Consumer;

public interface ActorQueue<A> {
	// Consumer<A>를 remote 환경에서도 확장할 수 있는가?
	Enqueued enqueue(Consumer<A> mail);
	class Enqueued {
		final boolean acquired;
		public Enqueued(final boolean acquired) {
			this.acquired = acquired;
		}
	}
	
	// flush 연산이 실제로 도움이 되는가?
	// 좀 더 효율적으로 개선할 수 있는 방법이 있을까?
	List<Consumer<A>> flush();
	
	Completed complete(int processedCount);
	class Completed {
		final boolean hasMore;
		public Completed(final boolean hasMore) {
			this.hasMore = hasMore;
		}
	}
}
