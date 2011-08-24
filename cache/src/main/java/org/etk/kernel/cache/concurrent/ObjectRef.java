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

import org.etk.kernel.cache.ObjectCacheInfo;

import java.io.Serializable;

/**
 * An reference to an object.
 *
 * @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a>
 * @version $Revision$
 */
abstract class ObjectRef<K extends Serializable, V> extends Item implements ObjectCacheInfo<V> {

  protected final long expirationTime;
  protected final K name;

  protected ObjectRef(long expirationTime, K name) {
    this.name = name;
    this.expirationTime = expirationTime;
  }

  public abstract boolean isValid();

  public abstract V getObject();

  // ObjectCacheInfo impl

  public long getExpireTime() {
    return expirationTime;
  }

  public V get() {
    return getObject();
  }
}
