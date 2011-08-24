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

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.etk.common.logging.Logger;

/**
 * @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a>
 * @version $Revision$
 */
public class SynchronizedQueue<I extends Item> implements Queue<I> {

  private final Logger log;
  private final Item head;
  private final Item tail;
  volatile int queueSize; // The queue size cached (which can be an estimate)
  private final Lock queueLock = new ReentrantLock();
  private volatile AtomicBoolean trimming = new AtomicBoolean();

  public SynchronizedQueue(Logger log) {
    this.log = log;
    this.head = new Item();
    this.tail = new Item();
    this.queueSize = 0;

    //
    head.next = tail;
    tail.previous = head;

    //
    if (isTraceEnabled()) {
      trace("Queue initialized with first=" + head.serial + " and last=" + tail.serial);
    }
  }

  private boolean isTraceEnabled() {
    return log != null && log.isTraceEnabled();
  }

  private void trace(String message) {
    log.trace(message + " [" + Thread.currentThread().getName() + "]");
  }

  public int size() {
    return queueSize;
  }

  public void assertConsistency() {
    int cachedQueueSize = queueSize;
    int effectiveQueueSize = 0;
    for (Item item = head.next;item != tail;item = item.next) {
      effectiveQueueSize++;
    }

    //
    if (effectiveQueueSize != cachedQueueSize) {
      throw new AssertionError("The cached queue size " + cachedQueueSize + "  is different from the effective queue size" + effectiveQueueSize);
    }
  }


  /**
   * Attempt to remove an item from the queue.
   *
   * @param item the item to remove
   * @return true if the item was removed by this thread
   */
  public boolean remove(I item) {
    boolean trace = isTraceEnabled();
    queueLock.lock();
    try {
      Item previous = item.previous;
      Item next = item.next;
      if (previous != null && next != null) {
        previous.next = next;
        next.previous = previous;
        item.previous = null;
        item.next = null;
        int newSize = --queueSize;
        if (trace) {
          trace("Removed item=" + item.serial + " with previous=" + previous.serial + " and next=" + next.serial +
            " with queue=" + newSize + "");
        }
        return true;
      } else {
        if (trace) {
          trace("Attempt to remove item=" + item.serial + " concurrently removed");
        }
        return false;
      }
    }
    finally {
      queueLock.unlock();
    }
  }

  /**
   * Add the item to the head of the list.
   *
   * @param item the item to add
   */
  public void add(I item) {
    queueLock.lock();
    try {
      Item next = head.next;
      item.next = next;
      next.previous = item;
      head.next = item;
      item.previous = head;
      int newSize = ++queueSize;
      if (isTraceEnabled()) {
        trace("Added item=" + item.serial + " with next=" + next.serial + " and queue=" + newSize);
      }
    }
    finally {
      queueLock.unlock();
    }
  }

  /**
   * Attempt to trim the queue. Trim will occur if no other thread is already performing a trim
   * and the queue size is greater than the provided size.
   *
   * @param size the wanted size
   * @return the list of evicted items
   */
  public ArrayList<I> trim(int size) {
    if (trimming.compareAndSet(false, true)) {
      try {
        queueLock.lock();
        try {
          if (queueSize > size) {
            ArrayList<I> evictedItems = new ArrayList<I>(queueSize - size);
            while (queueSize > size) {
              I last = (I)tail.previous;
              remove(last);
              evictedItems.add(last);
            }
            return evictedItems;
          }
        }
        finally {
          queueLock.unlock();
        }
      } finally {
        trimming.set(false);
      }
    }

    //
    return null;
  }
}
