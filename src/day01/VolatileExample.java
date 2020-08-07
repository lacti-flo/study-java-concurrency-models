package day01;

public class VolatileExample {
	static boolean answerReady = false;
	
	static Thread thread1 = new Thread(() -> answerReady = true);
	static Thread thread2 = new Thread(() -> {
		// It is OK?
		while (!answerReady) {}
		System.out.println("Thread2: OK! ");
	});
	
	public static void main(String[] args) throws InterruptedException {
		thread2.start(); thread1.start();
		thread2.join(); thread1.join();
	}
}
