# 아이덴티티를 상태에서 분리하기

## Atomic 연산

어떤 연산을 Atomic하게 할 수 있는가?

- 연산에는 어떤게 있나?
- `java.util.concurrent.atomic`으로 해결할 수 있는가?

Atomic한 연산이 보장해주는 것은 무엇인가?

## ~~영속적인~~ versioning 자료구조

- 불변성을 유지하며 자료구조에 수정을 가해보자.
- `CopyOnWriteArrayList`는 훌륭하다. 하지만 모든 상황에서 효율적인가?

불변성이 기본으로 유지되는 세상에서는 어쩔 수 없이 versioning이 된다고 볼 수 있다. 하지만 그렇지 않은 세상에서는 더욱 힘든 법.

## identity

어떤 변수가 그 자체로 identity를 확립한다고 볼 수 있는가?

```java
int a = 10;
```

`a`는 `10`이다. `10`은 상태다. 그럼 `a`는 identity가 될 수 있나? 불변성을 사용해 주자.

```java
final int a = 10;
```

이제 `a`는 identity를 가진다고 할 수 있을까?

> 똑같은 강물에 두 번 발을 담글 수 없다. 다른 물결이 계속 다가오기 때문이다. - 헤라클로이토스

혹은 쉬운 방법은 시점과 상태를 고정하는 것이다.

```typescript
(timestamp, state)
```

이제 _특정 시점에 특정 상태를 갖는다_ 는 불변이 보장되므로 이는 identity를 가진다고 할 수 있다.

## CompareAndSwap

- strong
- weak: [하지만 정말 자신있을 때 쓰자.](https://stackoverflow.com/a/25200031)

성공할 때까지 계속 시도한다. 최악의 경우 victim은 끝나지 않을 수도 있다. 하지만 deadlock은 없다.

### AtomicReference

- primitive variable에 대한 atomic operation은 intrinsic으로 지원하므로 효율적.
- object 수준을 관리하려면 그 reference에 대한 atomic operation을 수행해야 함

object 내에 어떤 값이 변했는지 확인하여 바꾸는게 아니라 reference의 값의 변화만을 보고 수행. 이로 인한 의도치 않은 동작에 주의 필요.

- 이 방법을 쓰는게 더 효율적인 경우도 있고
- Concurrent 자료구조를 쓰는게 더 효율적일 수도 있다.

어떤 상황에 더 효율적인지 고민.

- 자료구조가 `flush`를 지원할 수 있는 경우라면?
- 즉, 자료구조의 사용이 `SPMC`, `MPSC`, `MPMC` 중 어느 곳에 속하는가?

## 1일차 정리

- 불변성을 위한 값 복제
- identity와 연관된 상태가 시간의 흐름에 따라 변하는 값의 열이다.

## Agent

local에서 쓸 수 있는 제한적인 actor clojure 구현체. 대충 Java로 옮겨보면 다음과 같다.

```java
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
}
```

- 이것은 효율적인가?
- 어떤 이점이 있는가?
- `ConcurrentLinkedQueue`를 쓰는 것이 정말 좋을까?
- `get`이 `Future`를 반환하도록 하면 도움이 될까?
- 에러 처리 측면에서 문제 없는가?
- 값이 변할 때에 추가적인 동작을 수행할 수 있을까?
- 분산 환경에서도 쓸 수 있을까?

actor의 소형 모델이므로 actor model을 제대로 썼을 때 좀 더 효과적으로 사용할 수 있다.

## STM

in memory에서 software 구현으로 transactional memory를 만든다. durability하지 않기 때문에 atomic하고 consistent하고 isolated하다.

### Ref

먼저 값마다 version을 관리할 자료구조를 추가한다.

```java
class Ref<T> {
	static final AtomicInteger serial = new AtomicInteger(0);

	int version;
	T value;
	
	public Ref(T captured) {
		version = serial.getAndIncrement() % Integer.MAX_VALUE;
		value = captured;
	}
	
	public synchronized void update(Object newValue) {
		//noinspection unchecked
		value = (T) newValue;
		version++;
	}
}
```

### Transaction

transaction이 시작될 때 이 transaction에 참여할 대상에 대해 version을 미리 준비해둔다. 이후 적용할 변경점을 모아두었다가 commit하는 시점에 이 version이 모두 일치할 때에만 갱신을 수행해준다.

```java
class Transaction {
    final Map<Ref<?>, Integer> versions = new HashMap<>();
    final Map<Ref<?>, Object> updates = new HashMap<>();
    
    public void join(Ref<?>... refs) {
        for (Ref<?> ref : refs) {
            versions.put(ref, ref.version);
        }
    }
    
    public <T> void update(Ref<T> source, T newValue) {
        updates.put(source, newValue);
    }
    
    public boolean commit() {
        for (Entry<Ref<?>, Integer> pair : versions.entrySet()) {
            if (pair.getKey().version != pair.getValue()) {
                return false;
            }
        }
        for (Entry<Ref<?>, Object> pair : updates.entrySet()) {
            pair.getKey().update(pair.getValue());
        }
        return true;
    }
}
```

### STM

STM을 진행할 때 동기화되어야 하는 부분은 다음과 같다.

- version을 확인하는 부분
- commit하는 부분

그 외에 transaction을 사용하여 값을 갱신하는 부분은 보호 구간에서 제외할 수 있다. 즉, 기본적으로 lock 구간을 조절함으로써 효율을 추구하는 방법이다.

```java
public class STM {
	
	public boolean start(Consumer<Transaction> prepare,
                         Consumer<Transaction> execute) {
		final Transaction tx = new Transaction();
		synchronized (this) {
			prepare.accept(tx);
		}
		execute.accept(tx);
		synchronized (this) {
			return tx.commit();
		}
	}
```

이제 다음과 같이 쓸 수 있다.

```java
public static void main(String[] args) {
    final Ref<Long> a = new Ref<>(1000L);
    final Ref<Long> b = new Ref<>(2000L);
    
    boolean success = new STM().start(
            (tx) -> tx.join(a, b),
            (tx) -> {
                tx.update(a, a.value + 500);
                tx.update(b, b.value - 500);
            });
    System.out.println(success);
}
```

- 이는 효율적인가?
- 정말 문제없이 잘 동작하는가?
- STM마다 lock을 따로 관리한다는건, 다른 STM이 서로 같은 `Ref`를 참조할 경우 동시성 보장이 안 될 수 있다는 뜻이다. 이를 해결하기 위한 좋은 방법이 있을까?
- 변경점을 따로 모아두기 때문에 발생하는 메모리 비효율은 없을까?
- 실패했을 때 어떤 전략을 취하는 것이 좋을까?

## 정리

- 동시성 모델은 필요에 따라 직접 구현해서 사용할 수 있다. 심지어 가끔은 그게 더 효율적일 수 있다.
- 하지만 과연 그게 안전할까? 더 효율적일까? 더 확장성이 있을까?
- 그리고 그게 미래에도 유지될까?

모델을 논리적으로 잘 이해하는 것은 다른 라이브러리를 사용할 때에도 효율과 부작용을 세세히 파악할 수 있으므로 아주 중요하다. 하지만 그렇다고 직접 만들어 사용하는 것은 가급적이면 권장하지 않는다.

