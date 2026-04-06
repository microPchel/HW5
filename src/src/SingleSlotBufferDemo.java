public class SingleSlotBufferDemo {
    public static void main(String[] args) {
        SingleSlotBuffer<Integer> buffer = new SingleSlotBuffer<>();
        final int n = 1000;
        final int expectedSum = n * (n + 1) / 2;

        final int[] sum = {0};

        Thread producer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (int i = 1; i <= n; i++) {
                        buffer.put(i);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Producer interrupted");
                }
            }
        });

        Thread consumer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (int i = 1; i <= n; i++) {
                        sum[0] += buffer.take();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Consumer interrupted");
                }
            }
        });

        producer.start();
        consumer.start();

        try {
            producer.join();
            consumer.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Main thread interrupted");
        }

        System.out.println("Expected sum: " + expectedSum);
        System.out.println("Actual sum: " + sum[0]);

        if (sum[0] == expectedSum) {
            System.out.println("Test passed");
        } else {
            System.out.println("Test failed");
        }
    }
}