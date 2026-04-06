# Лабораторная работа: базовые примитивы многопоточности в Java

## Цель работы

Отработать базовые навыки написания корректного многопоточного кода в Java:
- понять разницу между `Thread` и `Runnable`;
- разобраться с проблемами `visibility`, `atomicity`, `ordering`;
- понять смысл `happens-before` в Java Memory Model;
- использовать `volatile` для видимости;
- использовать `synchronized`, `wait()` и `notifyAll()` для координации потоков.

---

## Задание 1. Теория

### 1. Thread vs Runnable

`Thread` — это сам поток исполнения, а `Runnable` — это задача, которую поток должен выполнить.  
Обычно предпочитают `Runnable` или `ExecutorService`, потому что так удобнее отделять логику задачи от механизма запуска и не создавать потоки вручную каждый раз.

Пример:
```java
Runnable task = () -> System.out.println("Task is running");
Thread t = new Thread(task);
t.start();
```

---

### 2. Visibility / Atomicity / Ordering

#### Visibility
`Visibility` означает, что изменения переменной, сделанные одним потоком, должны быть видны другим потокам.  
Проблема возникает, если один поток изменил значение, а другой продолжает видеть старое.

Пример:
```java
class Example {
    boolean running = true;

    void stop() {
        running = false;
    }
}
```

Здесь другой поток может не сразу увидеть, что `running` стало `false`.

#### Atomicity
`Atomicity` означает, что операция выполняется как единое неделимое действие.  
Проблема в том, что `count++` на самом деле состоит из нескольких шагов: чтение, увеличение, запись.

Пример:
```java
count++;
```

Если два потока делают это одновременно, можно потерять обновление.

#### Ordering
`Ordering` связано с порядком выполнения инструкций.  
Из-за оптимизаций и особенностей памяти другой поток может увидеть действия не в том порядке, в каком они написаны в коде.

Пример:
```java
data = 42;
ready = true;
```

Если нет синхронизации, другой поток может увидеть `ready == true`, но ещё не увидеть новое значение `data`.

---

### 3. Happens-before (JMM)

`Happens-before` означает гарантию, что действия одного потока будут видны другому потоку в правильном порядке.  
Если между действиями есть отношение happens-before, то второй поток увидит результат первого корректно.

Два правила:

#### Volatile rule
Запись в `volatile` переменную happens-before чтению этой же `volatile` переменной из другого потока.  
Это нужно, чтобы изменения были видимы между потоками.

Пример:
```java
volatile boolean running = true;
```

#### Monitor lock rule
Выход из блока `synchronized` happens-before следующему входу в `synchronized` по тому же монитору.  
Это даёт согласованность данных между потоками.

Пример:
```java
synchronized (lock) {
    sharedValue = 10;
}
```

#### Start/join rule
Вызов `start()` гарантирует, что новый поток увидит всё, что было сделано до его запуска.  
А `join()` гарантирует, что после завершения потока другой поток увидит его результаты.

Пример:
```java
Thread t = new Thread(() -> result = 5);
t.start();
t.join();
System.out.println(result);
```

---

### 4. volatile

`volatile` хорошо подходит для флага остановки, потому что оно гарантирует видимость изменений между потоками.  
Но `volatile` не делает `count++` потокобезопасным, потому что это не одна операция, а несколько.

Пример с флагом:
```java
volatile boolean running = true;
```

Пример, где `volatile` не спасает:
```java
volatile int count = 0;
count++;
```

Здесь всё равно возможна гонка данных.

---

### 5. synchronized и wait/notify

`wait()` и `notify()` работают только внутри `synchronized`, потому что они связаны с монитором объекта.  
Поток должен владеть монитором, иначе Java выбросит `IllegalMonitorStateException`.

Условие ожидания проверяют через `while`, а не через `if`, потому что после пробуждения нужно заново убедиться, что условие действительно стало истинным.  
Кроме того, возможны ложные пробуждения или ситуация, когда другой поток успел изменить состояние раньше.

Пример:
```java
synchronized (lock) {
    while (!ready) {
        lock.wait();
    }
}
```

---

## Задание 2. Практика

Реализован потокобезопасный одноэлементный буфер `SingleSlotBuffer<T>` для обмена данными между потоками.

### Код класса `SingleSlotBuffer`

```java
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
```

### Что здесь происходит

- если буфер занят, `put()` ждёт, пока место освободится;
- если буфер пуст, `take()` ждёт, пока появится значение;
- `while` используется для корректной повторной проверки условия;
- `notifyAll()` будит ожидающие потоки после изменения состояния буфера.

---

## Демо-тест

В демонстрации:
- `producer` кладёт числа от 1 до 1000;
- `consumer` забирает 1000 чисел и суммирует их;
- в конце проверяется, что сумма равна:

```text
1000 * 1001 / 2 = 500500
```

Код демо:

```java
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
```

---

## Результат

Программа должна выводить:

```text
Expected sum: 500500
Actual sum: 500500
Test passed
```

Это подтверждает, что буфер работает корректно и обмен между потоками реализован правильно.

---

## Вывод

В ходе работы были разобраны базовые проблемы многопоточного программирования: видимость, атомарность и порядок выполнения.  
Также была реализована простая синхронизация потоков через `synchronized`, `wait()` и `notifyAll()` на примере одноэлементного буфера.
