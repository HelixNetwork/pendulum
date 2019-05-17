package net.helix.hlx.storage;

import net.helix.hlx.utils.Pair;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Created by paul on 3/2/17 for iri.
 */

 /**
 * The Persistence Provider interface determines how the data is handled between the model and the database.
 * To save and update a model object in the database @see #save(Persistable, Indexable) and @see #update(Persistable, Indexable, String)
 * To get an entry from the database @see #get(Class<?>, Indexable)
 * To delete an entry from the database @see #delete(Class<?>, Indexable)
 */
public interface PersistenceProvider {

      void init() throws Exception;
      boolean isAvailable();
      void shutdown();
      boolean save(Persistable model, Indexable index) throws Exception;
      void delete(Class<?> model, Indexable  index) throws Exception;

      boolean update(Persistable model, Indexable index, String item) throws Exception;

      boolean exists(Class<?> model, Indexable key) throws Exception;

      Pair<Indexable, Persistable> latest(Class<?> model, Class<?> indexModel) throws Exception;

      Set<Indexable> keysWithMissingReferences(Class<?> modelClass, Class<?> otherClass) throws Exception;

      Persistable get(Class<?> model, Indexable index) throws Exception;

      boolean mayExist(Class<?> model, Indexable index) throws Exception;

      long count(Class<?> model) throws Exception;

      Set<Indexable> keysStartingWith(Class<?> modelClass, byte[] value);

      Persistable seek(Class<?> model, byte[] key) throws Exception;

      Pair<Indexable, Persistable> next(Class<?> model, Indexable index) throws Exception;
      Pair<Indexable, Persistable> previous(Class<?> model, Indexable index) throws Exception;

      Pair<Indexable, Persistable> first(Class<?> model, Class<?> indexModel) throws Exception;

      boolean saveBatch(List<Pair<Indexable, Persistable>> models) throws Exception;

      /**
       * Atomically delete all {@code models}.
       * @param models key value pairs that to be expunged from the db
       * @throws Exception
       */
      void deleteBatch(Collection<Pair<Indexable, ? extends Class<? extends Persistable>>> models) throws Exception;

      void clear(Class<?> column) throws Exception;
      void clearMetadata(Class<?> column) throws Exception;

      List<byte[]> loadAllKeysFromTable(Class<? extends Persistable> model);
 }
