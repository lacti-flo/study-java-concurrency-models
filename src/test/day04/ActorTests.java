package day04;

import org.junit.jupiter.api.Test;
import support.Threaded;

import java.util.concurrent.ExecutionException;

public class ActorTests {
	
	static class Counter {
		private int count = 0;
		
		public void increment() {
			++count;
		}
		
		public int get() {
			return count;
		}
	}
	
	@Test
	public void simpleCounter() {
		final Actor<Counter> counter = new Actor<>(new ActorLocalQueue<>(), new Counter());
		Threaded.test(() -> counter.run(Counter::increment))
				.shouldEqual(counter.query(Counter::get));
	}
}
