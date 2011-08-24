/*
 * Copyright (C) 2003-2011 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.etk.kernel.container.management;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.etk.kernel.container.KernelContainer;
import org.etk.kernel.container.component.RequestLifeCycle;
import org.etk.kernel.management.ManagementAware;
import org.etk.kernel.management.annotations.ManagedBy;
import org.etk.kernel.management.spi.ManagedResource;
import org.etk.kernel.management.spi.ManagedTypeMetaData;
import org.etk.kernel.management.spi.ManagementContext;
import org.etk.kernel.management.spi.ManagementProvider;


/**
 * Created by The eXo Platform SAS Author : eXoPlatform exo@exoplatform.com Jul
 * 28, 2011
 */
public class ManagementContextImpl implements ManagementContext, ManagedResource {

  /** . */
  private final Map<Class<?>, Object> scopingDataList;

  /** The registrations done by this mbean. */
  private final Map<Object, ManagementContextImpl> registrations;

  /** . */
  final Map<ManagementProvider, Object> managedSet;

  /** . */
  private final ManagementContextImpl parent;

  /** . */
  private final Object resource;

  /** . */
  private final ManagedTypeMetaData typeMD;

  /**
   * An optional container setup when the management context is attached to a
   * container.
   */
  final ManageableContainer container;

  public ManagementContextImpl(ManageableContainer container) {
    if (container == null) {
      throw new NullPointerException();
    }

    //
    Object resource = null;
    ManagedTypeMetaData typeMD = null;
    MetaDataBuilder builder = new MetaDataBuilder(container.getClass());
    if (builder.isBuildable()) {
      resource = container;
      typeMD = builder.build();
    }

    //
    this.managedSet = new HashMap<ManagementProvider, Object>();
    this.registrations = new HashMap<Object, ManagementContextImpl>();
    this.parent = null;
    this.scopingDataList = new HashMap<Class<?>, Object>();
    this.resource = resource;
    this.typeMD = typeMD;
    this.container = container;
  }

  public ManagementContextImpl(ManagementContextImpl parent, ManageableContainer container) {
    if (parent == null) {
      throw new NullPointerException();
    }
    if (container == null) {
      throw new NullPointerException();
    }

    //
    Object resource = null;
    ManagedTypeMetaData typeMD = null;
    MetaDataBuilder builder = new MetaDataBuilder(container.getClass());
    if (builder.isBuildable()) {
      resource = container;
      typeMD = builder.build();
    }

    //
    this.managedSet = new HashMap<ManagementProvider, Object>();
    this.registrations = new HashMap<Object, ManagementContextImpl>();
    this.parent = parent;
    this.scopingDataList = new HashMap<Class<?>, Object>();
    this.resource = resource;
    this.typeMD = typeMD;
    this.container = container;
  }

  public ManagementContextImpl(ManagementContextImpl parent,
                               Object resource,
                               ManagedTypeMetaData typeMD) {
    if (parent == null) {
      throw new NullPointerException();
    }
    if ((resource != null && typeMD == null) && (resource == null && typeMD != null)) {
      throw new IllegalArgumentException("Can't have resource null and meta data not null or the converse");
    }

    //
    this.managedSet = new HashMap<ManagementProvider, Object>();
    this.registrations = new HashMap<Object, ManagementContextImpl>();
    this.parent = parent;
    this.scopingDataList = new HashMap<Class<?>, Object>();
    this.resource = resource;
    this.typeMD = typeMD;
    this.container = null;
  }

  public ManagementContext getParent() {
    return parent;
  }

  public <S> void setScopingData(Class<S> scopeType, S scopingData) {
    this.scopingDataList.put(scopeType, scopingData);
  }

  public void register(Object o) {
    Object resource = null;

    // Apply managed by annotation
    ManagedBy managedBy = o.getClass().getAnnotation(ManagedBy.class);
    if (managedBy != null) {
      try {
        Class managedByClass = managedBy.value();
        Constructor<?> blah = managedByClass.getConstructor(o.getClass());
        resource = blah.newInstance(o);
      } catch (NoSuchMethodException e) {
        e.printStackTrace();
      } catch (InstantiationException e) {
        e.printStackTrace();
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      } catch (InvocationTargetException e) {
        e.printStackTrace();
      }
    } else {
      resource = o;
    }

    //
    if (resource != null) {

      MetaDataBuilder builder = new MetaDataBuilder(resource.getClass());
      if (builder.isBuildable()) {
        ManagedTypeMetaData metaData = builder.build();

        //
        ManagementContextImpl managementContext;
        if (resource instanceof ManageableContainer) {
          managementContext = ((ManageableContainer) resource).managementContext;
        } else {
          managementContext = new ManagementContextImpl(this, resource, metaData);
        }

        //
        registrations.put(resource, managementContext);

        //
        ManageableContainer container = findContainer();

        // Install for all the providers related
        for (ManagementProvider provider : container.getProviders()) {
          Object name = provider.manage(managementContext);
          if (name != null) {
            managementContext.managedSet.put(provider, name);
          }
        }

        // Allow for more resource management
        if (resource instanceof ManagementAware) {
          ((ManagementAware) resource).setContext(managementContext);
        }
      }
    }
  }

  public void unregister(Object o) {
    ManagementContextImpl context = registrations.remove(o);
    if (context != null) {
      for (Map.Entry<ManagementProvider, Object> entry : context.managedSet.entrySet()) {
        entry.getKey().unmanage(entry.getValue());
      }
    }
  }

  public <S> List<S> getScopingData(Class<S> scopeType) {
    ArrayList<S> list = new ArrayList<S>();
    for (ManagementContextImpl current = this; current != null; current = current.parent) {
      Object scopedData = current.scopingDataList.get(scopeType);
      if (scopedData != null) {
        // It must be that type since we put it
        list.add((S) scopedData);
      }
    }
    return list;
  }

  public KernelContainer findContainer() {
    for (ManagementContextImpl current = this; true; current = current.parent) {
      if (current.container instanceof KernelContainer) {
        return (KernelContainer) current.container;
      } else if (current.parent == null) {
        return null;
      }
    }
  }

  public void beforeInvoke(Object managedResource) {
    KernelContainer container = findContainer();
    if (container != null) {
      RequestLifeCycle.begin(container);
    }
  }

  public void afterInvoke(Object managedResource) {
    RequestLifeCycle.end();
  }

  @Override
  public String toString() {
    return "ManagementContextImpl[container=" + container + "]";
  }

  public Object getResource() {
    return resource;
  }

  public ManagedTypeMetaData getMetaData() {
    return typeMD;
  }

  void install(ManagementProvider provider) {

    // Install the current resource if necessary
    if (resource != null && typeMD != null) {
      Object name = provider.manage(this);
      if (name != null) {
        managedSet.put(provider, name);
      }
    }

    // Install the children except the container ones
    for (ManagementContextImpl registration : registrations.values()) {
      registration.install(provider);
    }
  }
}
