package day03;

import org.junit.jupiter.api.Test;
import support.Threaded;

public class AgentTests {
	
	@Test
	public void simpleCounter() {
		final Agent<Integer> counter = new Agent<>(0);
		Threaded.test(() -> counter.send(v -> v + 1))
				.shouldEqual(counter.get());
	}
}
