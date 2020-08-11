package day04;

import org.junit.jupiter.api.Test;
import support.Threaded;

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
		final Actor<Counter> counter = new Actor<>(new Counter());
		Threaded.test(() -> counter.send(Counter::increment))
				.shouldEqual(counter.query(Counter::get));
	}
}
