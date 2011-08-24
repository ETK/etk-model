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
package org.etk.kernel.cache.impl;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.etk.kernel.cache.CacheService;
import org.etk.kernel.cache.ExoCache;
import org.etk.kernel.cache.ExoCacheConfig;
import org.etk.kernel.cache.ExoCacheConfigPlugin;
import org.etk.kernel.cache.SimpleExoCache;
import org.etk.kernel.container.component.ComponentPlugin;
import org.etk.kernel.container.xml.InitParams;

/**
 * Created by The eXo Platform SAS. Author : Tuan Nguyen
 * tuan08@users.sourceforge.net Sat, Sep 13, 2003 @ Time: 1:12:22 PM
 */
public class CacheServiceImpl implements CacheService {
  private HashMap<String, ExoCacheConfig> configs_  = new HashMap<String, ExoCacheConfig>();

  private final ConcurrentHashMap<String, ExoCache<? extends Serializable, ?>> cacheMap_ = new ConcurrentHashMap<String, ExoCache<? extends Serializable, ?>>();

  private ExoCacheConfig                  defaultConfig_;

  private LoggingCacheListener            loggingListener_;

  public CacheServiceImpl(InitParams params) throws Exception {
    List<ExoCacheConfig> configs = params.getObjectParamValues(ExoCacheConfig.class);
    for (ExoCacheConfig config : configs) {
      configs_.put(config.getName(), config);
    }
    defaultConfig_ = configs_.get("default");
    loggingListener_ = new LoggingCacheListener();
  }

  public void addExoCacheConfig(ComponentPlugin plugin) {
    addExoCacheConfig((ExoCacheConfigPlugin) plugin);
  }

  public void addExoCacheConfig(ExoCacheConfigPlugin plugin) {
    List<ExoCacheConfig> configs = plugin.getConfigs();
    for (ExoCacheConfig config : configs) {
      configs_.put(config.getName(), config);
    }
  }

  public <K extends Serializable, V> ExoCache<K, V> getCacheInstance(String region) {
    if (region == null) {
      throw new NullPointerException("region cannot be null");
    }
    if (region.length() == 0) {
      throw new IllegalArgumentException("region cannot be empty");
    }
    ExoCache<? extends Serializable, ?> cache = cacheMap_.get(region);
    if (cache == null) {
      try {
        cache = createCacheInstance(region);
        ExoCache<? extends Serializable, ?> existing = cacheMap_.putIfAbsent(region, cache);
        if (existing != null) {
          cache = existing;
        }
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
    return (ExoCache<K,V>)cache;
  }

  synchronized private ExoCache<? extends Serializable, ?> createCacheInstance(String region) throws Exception {
    ExoCacheConfig config = configs_.get(region);
    if (config == null)
      config = defaultConfig_;
    ExoCache<? extends Serializable, ?> cache;
    if (config.getImplementation() == null) {
      cache = new SimpleExoCache<Serializable, Object>();
    } else {
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      Class<ExoCache<? extends Serializable, ?>> clazz = (Class<ExoCache<? extends Serializable,?>>)cl.loadClass(config.getImplementation());
      cache = clazz.newInstance();
    }
    cache.setName(region);
    cache.setLabel(config.getLabel());
    cache.setMaxSize(config.getMaxSize());
    cache.setLiveTime(config.getLiveTime());
    cache.setLogEnabled(config.isLogEnabled());
    if (cache.isLogEnabled()) {
      cache.addCacheListener(loggingListener_);
    }

    //
    return cache;
  }

  public Collection<ExoCache<? extends Serializable, ?>> getAllCacheInstances() {
    return cacheMap_.values();
  }
}
