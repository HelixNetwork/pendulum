package net.helix.pendulum.utils.collections.impl;

import net.helix.pendulum.utils.collections.interfaces.BoundedLinkedSet;
import org.apache.commons.collections4.set.ListOrderedSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Date: 2019-11-15
 * Author: zhelezov
 */
public class BoundedLinkedListImpl<E> implements BoundedLinkedSet<E> {

    private static final Logger log = LoggerFactory.getLogger(BoundedLinkedListImpl.class);


    private ListOrderedSet<E> queue = ListOrderedSet.listOrderedSet(new LinkedList<E>());
    private ReentrantLock lock = new ReentrantLock(true);
    private int maxCapacity;
    private Random random = new Random();

    private final static float DROP_PROBILITY = 0.1f;
    private final static float DROP_THRESHOLD = 0.9f;

    public BoundedLinkedListImpl(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    @Override
    public LinkedList<E> drain() {
        LinkedList<E> drainedTo = new LinkedList<>();
        lock.lock();
        try {
            drainedTo.addAll(queue);
            queue.clear();
        } finally {
            lock.unlock();
        }
        return drainedTo;
    }

    @Override
    public E poll() {
        lock.lock();
        E first = null;
        try {
            Iterator<E> it = queue.iterator();
            if (it.hasNext()) {
                first = it.next();
                it.remove();
            }
        } finally {
            lock.unlock();
        }
        return first;
    }

    @Override
    public E peek() {
        lock.lock();
        E first = null;
        try {
            Iterator<E> it = queue.iterator();
            if (it.hasNext()) {
                first = it.next();
            }
        } finally {
            lock.unlock();
        }
        return first;
    }

    @Override
    public boolean push(E element) {
        lock.lock();
        try {
            if (queue.contains(element)) {
                return false;
            }

            if (queue.size() >= maxCapacity) {
                // TODO: different eviction policies
                log.debug("The queue reached it max capacity, dropping the last element");
                queue.remove(queue.size()-1);
            }

            queue.add(0, element);
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<E> batchPoll(int size) {
        lock.lock();
        List<E> popped = new LinkedList<>();
        try {
            Iterator<E> it = queue.iterator();
            int counter = 0;
            while ((counter < size) && it.hasNext()) {
                popped.add(it.next());
                it.remove();
                counter++;
            }
            return popped;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int getMaxSize() {
        return maxCapacity;
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        lock.lock();
        try {
            return queue.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean contains(Object o) {
        lock.lock();
        try {
            return queue.contains(o);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Iterator<E> iterator() {
        lock.lock();
        try {
            List<E> copy = new ArrayList<>(queue);
            return copy.iterator();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Object[] toArray() {
        lock.lock();
        try {
            return queue.toArray();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public <T> T[] toArray(T[] a) {
        lock.lock();
        try {
            return queue.toArray(a);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean add(E e) {
        lock.lock();
        try {
            if (queue.size() >= maxCapacity) {
                // TODO: different eviction policies
                log.warn("The queue reached it max capacity, dropping first element");
                queue.remove(0);
            }
            if (queue.size() >= DROP_THRESHOLD * maxCapacity) {
                if (random.nextFloat() < DROP_PROBILITY) {
                    log.warn("Randomly dropping the first element due to increased occupation");
                    queue.remove(0);
                }
            }
            return queue.add(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean remove(Object o) {
        lock.lock();
        try {
            return queue.remove(o);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        lock.lock();
        try {
            return queue.containsAll(c);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        throw new UnsupportedOperationException("Add all not supported");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("Remove all not supported");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("Retain all not supported");
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            queue.clear();
        } finally {
            lock.unlock();
        }
    }
}
