package org.etk.kernel.container;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.picocontainer.ComponentAdapter;
import org.picocontainer.Parameter;
import org.picocontainer.PicoContainer;
import org.picocontainer.PicoException;
import org.picocontainer.PicoRegistrationException;
import org.picocontainer.PicoVisitor;
import org.picocontainer.defaults.ComponentAdapterFactory;
import org.picocontainer.defaults.DuplicateComponentKeyRegistrationException;

public class CachingContainer extends ConcurrentContainer {
	/**
	 * Serial Version UID
	 */
	private static final long serialVersionUID = 316388590860241305L;

	private final ConcurrentMap<Class, ComponentAdapter> adapterByType = new ConcurrentHashMap<Class, ComponentAdapter>();

	private final ConcurrentMap<Class, Object> instanceByType = new ConcurrentHashMap<Class, Object>();

	private final ConcurrentMap<Object, Object> instanceByKey = new ConcurrentHashMap<Object, Object>();

	private final ConcurrentMap<Class, List> adaptersByType = new ConcurrentHashMap<Class, List>();

	private final ConcurrentMap<Class, List> instancesByType = new ConcurrentHashMap<Class, List>();

	public CachingContainer(ComponentAdapterFactory componentAdapterFactory, PicoContainer parent) {
		super(componentAdapterFactory, parent);
	}

	public CachingContainer(PicoContainer parent) {
		super(parent);
	}

	public CachingContainer(ComponentAdapterFactory componentAdapterFactory) {
		super(componentAdapterFactory);
	}

	public CachingContainer() {
	}

	public ComponentAdapter getComponentAdapterOfType(Class componentType) {
		ComponentAdapter adapter = adapterByType.get(componentType);
		if (adapter == null) {
			adapter = super.getComponentAdapterOfType(componentType);
			if (adapter != null) {
				adapterByType.put(componentType, adapter);
			}
		}
		return adapter;
	}

	public List getComponentAdaptersOfType(Class componentType) {
		List adapters = adaptersByType.get(componentType);
		if (adapters == null) {
			adapters = super.getComponentAdaptersOfType(componentType);
			if (adapters != null) {
				adaptersByType.put(componentType, adapters);
			}
		}
		return adapters;
	}

	public List getComponentInstancesOfType(Class componentType) throws PicoException {
		List instances = instancesByType.get(componentType);
		if (instances == null) {
			instances = super.getComponentInstancesOfType(componentType);
			if (instances != null) {
				instancesByType.put(componentType, instances);
			}
		}
		return instances;
	}

	public Object getComponentInstance(Object componentKey) throws PicoException {
		Object instance = instanceByKey.get(componentKey);
		if (instance == null) {
			instance = super.getComponentInstance(componentKey);
			if (instance != null) {
				instanceByKey.put(componentKey, instance);
			}
		}
		return instance;
	}

	/**
	 * 
	 */
	public Object getComponentInstanceOfType(Class componentType) {
		Object instance = instanceByType.get(componentType);
		if (instance == null) {
			instance = super.getComponentInstanceOfType(componentType);
			if (instance != null) {
				instanceByType.put(componentType, instance);
			}
		}
		return instance;
	}

	private static final PicoVisitor invalidator = new ContainerVisitor() {
		public void visitContainer(PicoContainer pico) {
			if (pico instanceof CachingContainer) {
				CachingContainer caching = (CachingContainer) pico;
				caching.adapterByType.clear();
				caching.adaptersByType.clear();
				caching.instanceByKey.clear();
				caching.adaptersByType.clear();
				caching.instancesByType.clear();
			}
		}
	};

	private void invalidate() {
		accept(invalidator);
	}

	//

	public ComponentAdapter registerComponent(ComponentAdapter componentAdapter) throws DuplicateComponentKeyRegistrationException {
		ComponentAdapter adapter = super.registerComponent(componentAdapter);
		invalidate();
		return adapter;
	}

	public ComponentAdapter unregisterComponent(Object componentKey) {
		ComponentAdapter adapter = super.unregisterComponent(componentKey);
		invalidate();
		return adapter;
	}

	public ComponentAdapter registerComponentInstance(Object component) throws PicoRegistrationException {
		ComponentAdapter adapter = super.registerComponentInstance(component);
		invalidate();
		return adapter;
	}

	public ComponentAdapter registerComponentInstance(Object componentKey, Object componentInstance) throws PicoRegistrationException {
		ComponentAdapter adapter = super.registerComponentInstance(componentKey, componentInstance);
		invalidate();
		return adapter;
	}

	public ComponentAdapter registerComponentImplementation(Class componentImplementation) throws PicoRegistrationException {
		ComponentAdapter adapter = super.registerComponentImplementation(componentImplementation);
		invalidate();
		return adapter;
	}

	public ComponentAdapter registerComponentImplementation(Object componentKey, Class componentImplementation)
			throws PicoRegistrationException {
		ComponentAdapter adapter = super.registerComponentImplementation(componentKey, componentImplementation);
		invalidate();
		return adapter;
	}

	public ComponentAdapter registerComponentImplementation(Object componentKey, Class componentImplementation, Parameter[] parameters) throws PicoRegistrationException {
		ComponentAdapter adapter = super.registerComponentImplementation(componentKey, componentImplementation, parameters);
		invalidate();
		return adapter;
	}

	public ComponentAdapter registerComponentImplementation(
			Object componentKey, Class componentImplementation, List parameters)
			throws PicoRegistrationException {
		ComponentAdapter adapter = super.registerComponentImplementation(
				componentKey, componentImplementation, parameters);
		invalidate();
		return adapter;
	}

}
