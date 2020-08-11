package day03;

import java.util.HashMap;
import java.util.Map;
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
		int version = 0;
		T value;
		
		public Ref(T captured) {
			value = captured;
		}
		
		public synchronized void update(Object newValue) {
			//noinspection unchecked
			value = (T) newValue;
			version++;
		}
	}
}
