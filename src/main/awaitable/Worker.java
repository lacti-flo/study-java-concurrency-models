package awaitable;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class Worker implements Runnable {
	private static final AtomicInteger threadSerial = new AtomicInteger(0);
	
	private final ArrayBlockingQueue<Runnable> tasks;
	volatile boolean running = true;
	
	Worker(final ArrayBlockingQueue<Runnable> tasks) {
		this.tasks = tasks;
	}
	
	public void shutdown() {
		running = false;
	}
	
	@Override
	public void run() {
		final String workerName = "awaiter-worker-" + threadSerial.getAndIncrement();
		Thread.currentThread().setName(workerName);
		try {
			while (running) {
				pollAndProcessTask(workerName);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	private void pollAndProcessTask(final String workerName) throws InterruptedException {
		final Runnable task = tasks.poll(1, TimeUnit.SECONDS);
		if (task != null) {
			System.out.println("Worker[" + workerName + "] would process a task: " + task.toString());
			processTaskOne(task);
		}
	}
	
	private void processTaskOne(Runnable task) {
		try {
			task.run();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}