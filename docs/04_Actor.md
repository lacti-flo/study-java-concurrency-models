# Actor model

- 상태를 완전히 격리하고
- 모든 것은 message 기반으로 처리한다.

```java
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
```

`ActorQueue`를 사용해서 `processTasks`의 단일 수행을 보장하고 있다.

```java
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
```

가장 간단한 local 구현체를 보자.

```java
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
```

- 이를 분산으로 확장할 수 있을까?
- 만약 그렇다면 무엇을 추가로 더 고민해야 할까?

Redis와 적당한 MessageFormatter를 사용하여 분산으로 확장해볼 수 있다. 코드를 공유하지 않는 일반적인 Actor를 구현하려면 좀 더 까다로운 부분이 있다.

- 혹시 스크립트 언어는 이를 쉽게 해결할 수 있는가?

다음과 같은 `Counter`가 있을 때 Actor를 사용하여 multi threading 환경에서도 동시성 제어 문제 없이 사용할 수 있다.

```java
class Counter {
  private int count = 0;
  
  public void increment() {
    ++count;
  }
  
  public int get() {
    return count;
  }
}
```

이제 이렇게 사용할 수 있다.

```java
public static void main(String[] args) {
  final Actor<Counter> counter = new Actor<>(new ActorLocalQueue<>(), new Counter());
  Thread t1 = new Thread(() -> {
    for (int i = 0; i < 1000; ++i) counter.run(Counter::increment);
  });
  Thread t2 = new Thread(() -> {
    for (int i = 0; i < 1000; ++i) counter.run(Counter::increment);
  });
  t1.start(); t2.start();
  t2.join(); t1.join();
  System.out.println(counter.query(Counter::get).get());
}
```

이는 어떤 점에서 장점이 있는가?

- state를 완전히 분리하여 외부에서 접근하지 못하도록 동시성 제어를 할 수 있다.
- 하지만 추가적인 객체 wrapper를 만들어서 도메인 처리를 위한 interface를 만들어주어야 한다.

이는 정말 lock보다 효율적인가?

- 반드시 그 결과를 동기적으로 기다려야 하는 경우가 아니고
- 혹은 기다리지 않고 그 시점에 처리할 수 있는 다른 작업이 있다면

이 모델은 더 효율적이다.

## 분산 환경으로의 확장

Actor를 여러 node로 퍼뜨려보자.

- Queue는 이제 분산 상황에서 접근할 수 있는 Q가 되어야 한다. RabbitMQ도 좋고 Redis도 좋다. 혹은 Q에 대한 interface만 RestAPI로 만들어주어도 좋다.
- `send`와 `query`의 인자가 `Serializable`해야 하므로 이에 대한 _message format_ 을 정의한다.
- 어떤 `Actor`가 어디에 있는지 알기 위한 `addressor`를 추가한다.
- `CompletableFuture`를 network 너머로 전파하기 위한 구조를 추가한다.

네트워크 IO를 수반하므로 다음 내용도 같이 고민해보면 좋다.

- 전달하는 데이터가 `command`보다 `data-payload`가 더 크면 차라리 `actor`가 움직이는게 더 효율적일 수도 있다.
- [어쨌든 네트워크로 전달되므로 가용성을 높이기 위해 고민할 것이 많다.](https://aws.amazon.com/ko/builders-library/challenges-with-distributed-systems/)
  - 믿을 수 있는 시스템은 어디까지인가?

## 장애 대응

- 처리에 대한 대응
  - `actor`가 crash 되었을 때 감시자에서 이를 처리
  - 재시작할 것인가? 재시작 전략으로는 어떤게 있을까?
- `message`에 대한 대응
  - 어떤 `message`가 잘 처리되었는지 확인하고
  - 전달이 잘 되었는지 확인하고
  - Timeout이 필요하면 처리

실제 로직을 처리하는 부분이 아니라 Actor를 다루는 부분에서 이에 대한 결정을 할 수 있기 때문에 좀 더 깔끔하게 처리해줄 수 있다.

## 에러-커널 패턴

> 소프트웨어를 설계하는 데는 두 방법이 있다. 하나는 단순하게 만들어서 아무 결함이 없도록 만드는 것이고, 다른 하나는 복잡하게 만들어서 눈에 드러나는 결함이 없도록 만드는 것이다. - Sir Tony Hoare

![https://getakka.net/images/ErrorKernel.png](https://getakka.net/images/ErrorKernel.png)

> In a supervision hierarchy, keep important application state or functionality near the root while delegating risky operations towards the leaves.

관리할 수 있는 에러만 직접 관리하고 안 되겠으면 위 계층으로 전파한다.

- 분산 actor model에서 에러를 어떻게 전파할 수 있을까? -> 에러도 반환 값이다.
- 전파되는 error에 추가적인 정보를 담아보면 이점이 있을까? -> MVCC 상에서의 routing 효율화 등

## 책 내용 정리

책 내용은 Elixir 기반이므로 그 언어 종속적인 내용은 빼고 Java스럽게 다시 정리한다.

- Actor를 생성하고 비동기 메시지 전송하기
- Actor가 정상적/비정상적으로 종료되는 이벤트를 처리할 수 있는가?
- Actor끼리 메시지 주고 받기
- Actor에 요청하는 메시지에 대한 Timeout을 처리해보자.
- Actor에 전달한 메시지가 반드시 도착했음을 보장할 수 있는가?
- Actor가 영속성을 보장하기 위해서는 어떤 전략을 취할 수 있을까?
- 모든 시스템을 Actor로 설계하여 SPOF를 제거할 수 있을까?

Elixir는 Actor model이 언어에 반영되어 있기 때문에 이러한 프로그래밍을 좀 더 간편하고 직관적으로 할 수 있다. 이렇듯 어떤 동시성/비동기 모델이 언어 철학에 반영되어 있다면 그 언어를 공부함으로써 해당 모델을 같이 공부할 수 있고, 좀 더 편하게 사용할 수 있다.


## 최종 정리

### 장점

- 상태 공유를 하지 않고 단일 Actor 내의 메시지 처리 순서가 보장되므로 메시지를 주고 받을 때만 동시성을 고려하면 된다.
- 장애 허용에 대한 명확한 기반을 구축할 수 있다.
- 분산 설계도 지원한다.

### 단점

- Deadlock이 발생하거나 Mailbox가 넘칠 수 있다.
- 직접적인 병렬성을 지원하지는 않는다. 또한 비결정성을 야기할 수 있다.
