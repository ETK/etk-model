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
package org.etk.kernel.cache;

/**
 * Wraps a cache entry to provide meta information in addition of the entry value.
 *
 * Created by The eXo Platform SAS Author : Tuan Nguyen
 * tuan08@users.sourceforge.net Sep 19, 2005
 * @param <V> the value type
 */
public interface ObjectCacheInfo<V> {

  /**
   * Returns the expiration time of the entry in milli seconds.
   *
   * @return the expiration time
   */
  public long getExpireTime();

  /**
   * Returns the entry value which may be null.
   *
   * @return the entry value
   */
  public V get();
}
