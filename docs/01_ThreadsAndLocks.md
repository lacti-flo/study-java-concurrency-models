# 스레드와 잠금장치

- 용어 되새기기
  - mutual exclusion, memory model
  - race condition, deadlock
- `Thread.yield()`는 무얼하는가?
- `java.util.concurrent` package를 잘 알고 있는가?

## [read-modify-write](https://en.wikipedia.org/wiki/Read%E2%80%93modify%E2%80%93write)

```java
class Counter {
    private int count = 0;
    public void increment() { ++count; }
}
final Counter counter = new Counter();
counter.increment(); // Decompile
```

```text
getfield #1
iconst_1
iadd
putfield #2
```

### 간단한 해결법

```java
public synchronized void increment() { ++count; }
```

하지만 이 방법이 효율적인가?

## 메모리 가시성

- [JSR-133](https://download.oracle.com/otndocs/jcp/memory_model-1.0-pfd-spec-oth-JSpec/)
- [Synchronization Order](https://docs.oracle.com/javase/specs/jls/se8/html/jls-17.html#jls-17.4.4)
- [Happens-before Order](https://docs.oracle.com/javase/specs/jls/se8/html/jls-17.html#jls-17.4.5)

```java
/* volatile */ boolean answerReady = false;
new Thread(() -> answerReady = true).start();
new Thread(() -> {
    while (!answerReady);
    System.out.println("OK!");
}).start();
```

순서도 보장해야 하고, 가시성도 확보하기 위한 효율적인 방법을 찾아보자.

예를 들어, 좀 더 작은 Lock. 왼쪽 포크와 오른쪽 포크의 Lock을 분리한다.

```java
private Chopstick left, right;
while (true) {
    synchronized (left) {
        synchronized (right) {
            // eat
        }
    }
}
```

효율적인가?

## 1일차 정리

- 공유되는 변수에 대한 접근을 반드시 동기화
- Read, Write thread 모두 동기화 필요
- 여러 개의 Lock을 사용할 경우 순서가 필요
- Lock을 획득하고 바깥일을 하지 말자. 바깥에서 뭘 할지 모른다.
- Lock걸면 최대한 빨리 나온다.

### 더 공부해볼 내용

- 초기화 안전성에 대한 확인
- double-checked locking이 왜 anti-pattern인가? [Java에서의 복잡한 구현](https://en.wikipedia.org/wiki/Double-checked_locking#Usage_in_Java)

## 데드락

```java
final Object lock1 = new Object();
final Object lock2 = new Object();
new Thread(() -> { synchronized (lock1) { synchronized (lock2) {} } }).start();
new Thread(() -> { synchronized (lock2) { synchronized (lock1) {} } }).start();
```

- Lock의 순서를 줘서 회피
- 이미 걸린 상황에서 피할 수 있나? -> 포기

### ReentrantLock

```java
final ReentrantLock lock = new ReentrantLock();
lock.lockInterruptibly();
```

적어도 interrupt로 빠져나갈 수 있게 만들면 Deadlock detector를 만들어서 해결할 수도 있다.

```java
lock.tryLock(1000, TimeUnit.MILLISECONDS);
```

혹은 timeout을 사용한다.

### Condition

```java
final ReentrantLock lock = new ReentrantLock();
final Condition condition = lock.newCondition();

lock.lock();
try {
  // ...
  condition.await();
}
```

충분히 효율적인가?

## 2일차 정리

- interruptible한 Lock을 사용하는 것이 더 안전하다.
- timeout도 고려하면 더욱 좋다.
- 순서에 늘 주의하자.
- Condition을 사용해 좀 더 효율적으로 기다려보자.
- Atomic 변수를 사용해보자.

### 더 공부해볼 내용

- `ReentrantLock`의 구현을 확인하여 fairness가 어떤 상황에서 효율에 도움을 줄 수 있는지 확인해보자.
- `ReentrantReadWriteLock`을 써보고 왜 `upgrade`가 빠졌는지 고민해보자.
- spurious wakeup을 줄이기 위한 방법을 고민해보자.
- `CAS`와 그의 친구들을 공부해보자.


## ExecutorService

- threadPool을 쉽게 구성할 수 있다.
- 다양한 정책을 보고 언제 어떤게 효율적일지 고민해보자.
- thread 개수를 어떻게 운영하는게 효율적일지 고민해보자.

## java.util.concurrent 

- `CopyOnWriteArrayList`는 어떤 상황에서 효율적일까?
- `BlockingQueue`의 구현은 어떤게 있고 각각의 장단점은 무엇일까? `ConcurrentLinkedQueue`보다 어떤 상황에서 더 나을 수 있을까?
- `ConcurrentHashMap`의 함정.

```java
class Word { String word; int count; }
final ConcurrentHashMap<String, Word> wordMap = new ConcurrentHashMap<>();

// 다음 함수를 multi-thread에서 실행하면 문제가 없나?
void updateWordCount(String word) {
  if (wordMap.containsKey(word)) {
    ++wordMap.get(word).count;
  } else {
    wordMap.put(word, new Word(word, 1));
  }
}
```

좀 더 고쳐보면?

```java
// 이 함수는 올바르게 동작하는가? 그리고 효율적인가?
void updateWordCount(String word) {
  Word maybe = wordMap.putIfAbsent(word, new Word(word, 1));
  if (maybe != null) {
    ++maybe.count;
  }
}
```

경합을 줄이기 위해 어떤 전략을 추가로 더 사용할 수 있을까?
  - 분할 정복 / map-reduce
  - 공유 자원을 최대한 만들지 않는게 좋다.

## 3일차 정리

- Thread를 직접 쓰는 대신 ThreadPool을 사용하자
- `CopyOnWriteArrayList`를 통해 경합 특성에 따른 효율적인 자료구조를 이해하자.
- `ArrayBlockingQueue`가 Producer-Consumer model에서 좀 더 효율적일 수 있는 상황을 고민하자.
- `ConcurrentHashMap`은 좋지만 함정을 조심하자.

### 더 공부해볼 내용

- `ForkJoinPool`은 어떤 구조인가?
- `Work stealing`은 무엇이고 어떤 도움을 주는가?
- `CountDownLatch`와 `CyclicBarrier`와 같이 동시성 제어를 위한 자료 구조를 많이 봐두자. Javadoc에 친절하게 예제까지 다 있다!

## 최종 정리

### 장점

- 폭 넓은 적용 범위
- 기계 자체에 가깝기 때문에 잘 쓰면 매우 효율적

### 단점

- 비결정성이라는 괴물을 만들어냄
- 분산 시스템 지원을 위한 추가 장치 필요
- 테스트가 어렵다.

## 기타

어떻게 공부해볼 수 있을까?

1. java.util.concurrent package에 들어간다.
2. Javadoc을 읽는다.
3. **Doug Lea** 교수님께 감사드린다.
4. 부족하면 JSR-133을 읽는다.
5. 다른 언어를 본다.
