package net.helix.sbx.storage;

import java.io.Serializable;

/**
 * Created by paul on 5/6/17.
 */

 /**
 * The Indexable interface enables reading in and out the byte stream of an comparable object.
 */
public interface Indexable extends Comparable<Indexable>, Serializable {

    /**
    * Get byte stream
    * @return a <code> byte[] </code>
    */
    byte[] bytes();

    /**
    * Set byte stream
    * @param bytes is a <code> byte[] </code>
    */
    void read(byte[] bytes);

    /**
    * Increment index
    * @return an <code> Indexable </code>
    */
    Indexable incremented();

    /**
    * Decrement index
    * @return an <code> Indexable </code>
    */
    Indexable decremented();
}
