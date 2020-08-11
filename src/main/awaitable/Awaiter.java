package awaitable;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public class Awaiter {
	private final ArrayBlockingQueue<Runnable> tasks = new ArrayBlockingQueue<>(1024);
	private final Manager manager;
	
	public Awaiter(int desiredWorkerCount) {
		manager = new Manager(desiredWorkerCount, tasks);
	}
	
	public void start() {
		manager.start();
	}
	
	public void shutdown() {
		manager.shutdown();
	}
	
	public <T> T await(CompletableFuture<T> future) {
		try {
			manager.onWorkerBlocked();
			System.out.println("[Awaiter] This thread would be blocked: " + Thread.currentThread().getName());
			return future.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new AwaiterException(e);
		} finally {
			System.out.println("[Awaiter] This thread would be resumed: " + Thread.currentThread().getName());
			manager.onWorkerResumed();
		}
	}
	
	public void sleep(long millis) {
		try {
			manager.onWorkerBlocked();
			System.out.println("[Awaiter] This thread would sleep: " + Thread.currentThread().getName());
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			throw new AwaiterException(e);
		} finally {
			System.out.println("[Awaiter] This thread would awake: " + Thread.currentThread().getName());
			manager.onWorkerResumed();
		}
	}
	
	public <T> CompletableFuture<T> async(final Supplier<T> supplier) {
		final CompletableFuture<T> future = new CompletableFuture<>();
		tasks.offer(() -> future.complete(supplier.get()));
		return future;
	}
	
	public static class AwaiterException extends RuntimeException {
		public AwaiterException(Exception e) {
			super(e);
		}
	}
}
