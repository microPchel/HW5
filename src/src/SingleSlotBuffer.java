public class SingleSlotBuffer<T> {
    private T slot = null;

    public synchronized void put(T value) throws InterruptedException {
        while (slot != null) {
            wait();
        }

        slot = value;
        notifyAll();
    }

    public synchronized T take() throws InterruptedException {
        while (slot == null) {
            wait();
        }

        T value = slot;
        slot = null;
        notifyAll();
        return value;
    }
}