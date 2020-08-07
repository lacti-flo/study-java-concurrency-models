package day03;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class STM {
	
	boolean start(Consumer<Transaction> prepare, Consumer<Transaction> execute) {
		final Transaction tx = new Transaction();
		synchronized (this) {
			prepare.accept(tx);
		}
		execute.accept(tx);
		synchronized (this) {
			return tx.commit();
		}
	}
	
	static class Transaction {
		final Map<Ref<?>, Integer> versions = new HashMap<>();
		final Map<Ref<?>, Object> updates = new HashMap<>();
		
		public void join(Ref<?>... refs) {
			for (final Ref<?> ref : refs) {
				versions.put(ref, ref.version);
			}
		}
		
		public <T> void update(Ref<T> source, T newValue) {
			updates.put(source, newValue);
		}
		
		public boolean commit() {
			for (final Map.Entry<Ref<?>, Integer> pair : versions.entrySet()) {
				if (pair.getKey().version != pair.getValue()) {
					return false;
				}
			}
			for (final Map.Entry<Ref<?>, Object> pair : updates.entrySet()) {
				pair.getKey().update(pair.getValue());
			}
			return true;
		}
	}
	
	static class Ref<T> {
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
}
