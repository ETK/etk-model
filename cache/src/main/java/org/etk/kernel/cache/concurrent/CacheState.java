/*
 * Copyright (C) 2003-2007 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.etk.kernel.cache.concurrent;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import org.etk.common.logging.Logger;

/**
 * Really the cache state (we need it because of the clear cache consistency).
 * @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a>
 * @version $Revision$
 */
class CacheState<K extends Serializable, V> {

  private final Logger log;
  private final ConcurrentFIFOExoCache<K, V> config;
  final ConcurrentHashMap<K, ObjectRef<K, V>> map;
  final Queue<ObjectRef<K, V>> queue;

  CacheState(ConcurrentFIFOExoCache<K, V> config, Logger log) {
    //this.log = (log == null) ? Logger.getLogger(CacheState.class) : log;
    this.log = log;
    
    this.config = config;
    this.map = new ConcurrentHashMap<K, ObjectRef<K, V>>();
    this.queue = new SynchronizedQueue<ObjectRef<K, V>>(log);
  }

  public void assertConsistency() {
    if (queue instanceof SynchronizedQueue) {
      ((SynchronizedQueue)queue).assertConsistency();
    }
    int mapSize = map.size();
    int effectiveQueueSize  = queue.size();
    if (effectiveQueueSize != mapSize) {
      throw new AssertionError("The map size is " + mapSize + " is different from the queue size " + effectiveQueueSize);
    }
  }

  public V get(Serializable name) {
    ObjectRef<K, V> entry = map.get(name);
    if (entry != null) {
      V o = entry.getObject();
      if (entry.isValid()) {
        config.hits++;
        config.onGet(entry.name, o);
        return o;
      } else {
        config.misses++;
        if (map.remove(name, entry)) {
          queue.remove(entry);
        }
        config.onExpire(entry.name, o);
      }
    }
    return null;
  }

  private boolean isTraceEnabled() {
    return log != null && log.isTraceEnabled();
  }

  private void trace(String message) {
    log.trace(message + " [" + Thread.currentThread().getName() + "]");
  }


  /**
   * Do a put with the provided expiration time.
   *
   * @param expirationTime the expiration time
   * @param name the cache key
   * @param obj the cached value
   */
  void put(long expirationTime, K name, V obj) {
    boolean trace = isTraceEnabled();
    ObjectRef<K, V> nextRef = new SimpleObjectRef<K, V>(expirationTime, name, obj);
    ObjectRef<K, V> previousRef = map.put(name, nextRef);
    //log.debug("map size = " + map.size());
    
    
    // Remove previous (promoted as first element)
    if (previousRef != null) {
      queue.remove(previousRef);
      if (trace) {
        trace("Replaced item=" + previousRef.serial + " with item=" + nextRef.serial + " in the map");
      }
    } else if (trace) {
      trace("Added item=" + nextRef.serial + " to map");
    }

    // Add to the queue
    queue.add(nextRef);

    // Perform eviction from queue
    ArrayList<ObjectRef<K, V>> evictedRefs = queue.trim(config.maxSize);
    if (evictedRefs != null) {
      for (ObjectRef<K, V> evictedRef : evictedRefs) {
        // We remove it from the map only if it was the same entry
        // it could have been removed concurrently by an explicit remove
        // or by a promotion
        map.remove(evictedRef.name, evictedRef);

        // Expiration callback
        config.onExpire(evictedRef.name, evictedRef.getObject());
      }
    }

    // Put callback
    config.onPut(name, obj);
  }

  public V remove(Serializable name) {
    boolean trace = isTraceEnabled();
    ObjectRef<K, V> item = map.remove(name);
    if (item != null) {
      if (trace) {
        trace("Removed item=" + item.serial + " from the map going to remove it");
      }
      boolean removed = queue.remove(item);
      boolean valid = removed && item.isValid();
      V object = item.getObject();
      if (valid) {
        config.onRemove(item.name, object);
        return object;
      } else {
        config.onExpire(item.name, object);
        return null;
      }
    } else {
      return null;
    }
  }
}
