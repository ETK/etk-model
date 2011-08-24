package org.etk.kernel.container;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * This class is used to merge different {@link ClassLoader} to create one
 * single one. This ClassLoader is used only for the resources the class will be
 * loaded from the ContextClassLoader. For each resources, it will always
 * consider that the {@link ClassLoader} with the highest priority has always
 * right, in other words for example in the method getResource, it will try to
 * get the resource in the {@link ClassLoader} of the highest priority, if it
 * cans not find it, it will try the {@link ClassLoader} with the second highest
 * priority and so on. The priority of the {@link ClassLoader} is the order
 * given in the constructor, the last {@link ClassLoader} is the one with the
 * highest priority.
 * 
 * Created by The eXo Platform SAS Author : Nicolas Filotto
 * nicolas.filotto@exoplatform.com 18 sept. 2009
 */
class UnifiedClassLoader extends ClassLoader {

	/**
	 * The list of all the {@link ClassLoader} to merge together
	 */
	private final ClassLoader[] cls;

	/**
	 * @param cls
	 *            the list of all the {@link ClassLoader} to merge ordered by
	 *            priority. The last {@link ClassLoader} has highest priority.
	 */
	UnifiedClassLoader(ClassLoader... cls) {
		super(Thread.currentThread().getContextClassLoader());
		if (cls == null || cls.length == 0) {
			throw new IllegalArgumentException(
					"The array of ClassLoader cannot be empty");
		}
		this.cls = cls;
	}

	/**
	 * Allows to override the list of {@link ClassLoader} if it is a dynamic
	 * list
	 * 
	 * @return the list of the {@link ClassLoader} to merge
	 */
	protected ClassLoader[] getClassLoaders() {
		return cls;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public URL getResource(String name) {
		final ClassLoader[] cls = getClassLoaders();
		for (int i = cls.length - 1; i >= 0; i--) {
			final ClassLoader cl = cls[i];
			URL url = cl.getResource(name);
			if (url != null) {
				return url;
			}
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		final ClassLoader[] cls = getClassLoaders();
		final Set<URL> urls = new LinkedHashSet<URL>();
		for (int i = 0, length = cls.length; i < length; i++) {
			Enumeration<URL> eUrls = cls[i].getResources(name);
			if (eUrls != null && eUrls.hasMoreElements()) {
				// Prevent duplicates
				urls.addAll(Collections.list(eUrls));
			}
		}
		return Collections.enumeration(urls);
	}
}
