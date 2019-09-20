package net.helix.pendulum.storage;

import java.io.Serializable;

/**
 * Created by paul on 5/6/17.
 */

 /**
 * The Persistable interface enables reading in and out the byte stream of an object and its meta data.
 */
public interface Persistable extends Serializable {

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
    * Get metadata as byte stream
    * @return a <code> byte[] </code>
    */
    byte[] metadata();

    /**
    * Set metadata as byte stream
    * @param bytes is a <code> byte[] </code>
    */
    void readMetadata(byte[] bytes);

    /**
    * Merge
    * @return a <code> boolean </code>
    */
    boolean merge();
}
