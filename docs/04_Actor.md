# Actor model

- 상태를 완전히 격리하고
- 모든 것은 message 기반으로 처리한다.

```java
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
	
    // 조회를 위해 Future를 반환하는 함수가 추가되었다.
	public <R> CompletableFuture<R> query(Function<T, R> mapper) {
		final CompletableFuture<R> future = new CompletableFuture<>();
		send(state -> {
			future.complete(mapper.apply(state));
			return state;
		});
		return future;
	}
	
    // agent 때의 구현과 동일하다.
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
}
```

이제 이를 이용해서 `Counter`를 만들어보자.

```java
class Counter {
    private final Actor<Integer> actor = new Actor<>(0);
    
    public void increment() {
        actor.send(v -> v + 1);
    }
    
    public CompletableFuture<Integer> get() {
        return actor.query(v -> v);
    }
}
```

이제 이렇게 사용할 수 있다.

```java
public static void main(String[] args) {
    Counter counter = new Counter();
    counter.increment();
    counter.get().thenAccept(System.out::println);
}
```

이는 어떤 점에서 장점이 있는가?

- state를 완전히 분리하여 외부에서 접근하지 못하도록 동시성 제어를 할 수 있다.
- 하지만 추가적인 객체 wrapper를 만들어서 도메인 처리를 위한 interface를 만들어주어야 한다.

## 분산 환경으로의 확장

Actor를 여러 node로 퍼뜨려보자.

- Queue는 이제 분산 상황에서 접근할 수 있는 Q가 되어야 한다. RabbitMQ도 좋고 Redis도 좋다. 혹은 Q에 대한 interface만 RestAPI로 만들어주어도 좋다.
- `send`와 `query`의 인자가 `Serializable`해야 하므로 이에 대한 _message format_ 을 정의한다.
- 어떤 `Actor`가 어디에 있는지 알기 위한 `addressor`를 추가한다.
- `CompletableFuture`를 network 너머로 전파하기 위한 구조를 추가한다.

## 장애 대응

- 어떤 `message`가 잘 처리되었는지 확인하고 