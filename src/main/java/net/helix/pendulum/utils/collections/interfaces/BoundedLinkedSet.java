package net.helix.pendulum.utils.collections.interfaces;

import java.util.List;

/**
 * A double-sided queue (e.g. can work as a stack and as a queue)
 * with bounded capacity and without duplicates. Similar to <code>LinkedHashSet</code>
 * but with following differences:
 *
 *  -- thread-safe
 *  -- A copy iterator never throws <code>ConcurrentModificationException</code>
 *  as a new copy is created
 *
 * Date: 2019-11-15
 * Author: zhelezov
 */
public interface BoundedLinkedSet<E> extends BoundedCollection<E> {

    /**
     * Drains the queue into a copy
     * @return List of the elements in the same order as they were in the queue
     */
    List<E> drain();

    /**
     * Retrieves the first element
     * @return <code>null</code> if empty
     */
    E pop();

    /**
     * Returns the first element without removal
     * @return <code>null</code> if empty
     */
    E peek();


    /**
     * Adds the top of the queue
     * @return <code>true</code> if added
     */
    boolean push(E element);

    /**
     * Retrieves up to <code>size</code> top elements from the queue
     * @param size the maximal number of elements to retrieve
     * @return the list containing the elements
     */
    List<E> batchPop(int size);

}
