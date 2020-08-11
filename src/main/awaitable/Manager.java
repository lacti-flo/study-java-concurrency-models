package awaitable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class Manager extends Thread {
	private static final AtomicInteger threadSerial = new AtomicInteger(0);
	
	private final ArrayBlockingQueue<ManagerTask> managerTasks = new ArrayBlockingQueue<>(128);
	private final List<Worker> workers = new ArrayList<>();
	private final ExecutorService executor = Executors.newCachedThreadPool();
	
	private final ArrayBlockingQueue<Runnable> tasks;
	
	Manager(final int desiredWorkerCount,
	        final ArrayBlockingQueue<Runnable> tasks) {
		super("awaiter-manager-" + threadSerial.getAndIncrement());
		this.tasks = tasks;
		for (int i = 0; i < desiredWorkerCount; ++i) {
			managerTasks.offer(ManagerTask.Hire);
		}
	}
	
	@Override
	public void run() {
		try {
			while (true) {
				final ManagerTask task = managerTasks.poll(1, TimeUnit.MINUTES);
				if (task == null) {
					continue;
				}
				if (!dispatchTask(task)) {
					return;
				}
			}
		} catch (InterruptedException ignored) {
		}
	}
	
	private boolean dispatchTask(final ManagerTask task) throws InterruptedException {
		switch (task) {
			case Hire:
				final Worker newWorker = new Worker(this.tasks);
				workers.add(newWorker);
				System.out.println("[Manager] Hire new worker");
				executor.submit(newWorker);
				return true;
			case Fire:
				if (!workers.isEmpty()) {
					System.out.println("[Manager] Fire old worker");
					final Worker oldWorker = workers.remove(0);
					oldWorker.shutdown();
				}
				return true;
			case Shutdown:
				System.out.println("[Manager] Shutdown all workers: " + workers.size());
				for (final Worker worker : workers) {
					worker.shutdown();
				}
				executor.shutdown();
				executor.awaitTermination(1, TimeUnit.MINUTES);
				System.out.println("[Manager] Executor is terminated");
				return false;
		}
		return true;
	}
	
	public void onWorkerBlocked() {
		managerTasks.offer(ManagerTask.Hire);
	}
	
	public void onWorkerResumed() {
		managerTasks.offer(ManagerTask.Fire);
	}
	
	public void shutdown() {
		managerTasks.offer(ManagerTask.Shutdown);
	}
	
	private enum ManagerTask {
		Hire, Fire, Shutdown
	}
}