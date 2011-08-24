package org.etk.kernel.container.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class Tools {

	/**
	 * Instantiates a {@link HashSet} object and fills it with the provided
	 * element array.
	 * 
	 * @param elements
	 *            the list of elements to add
	 * @param <E>
	 *            the element type
	 * @return the set of elements
	 * @throws NullPointerException
	 *             if the element array is null
	 */
	public static <E> Set<E> set(E... elements) throws NullPointerException {
		if (elements == null) {
			throw new NullPointerException("No null element array accepted");
		}
		HashSet<E> set = new HashSet<E>();
		if (elements.length > 0) {
			for (E element : elements) {
				set.add(element);
			}
		}
		return set;
	}

	/**
	 * Parse the provided list according to the comma separator. The string is
	 * sliced first using the {@link String#split(String)} method with the
	 * <code>","</code> argument. Each chunk is then trimmed and if its length
	 * is not zero then it is added to the returned set.
	 * 
	 * @param s
	 *            the list to parse
	 * @return the set of string found in the list
	 * @throws NullPointerException
	 *             if the string argument is null
	 */
	public static Set<String> parseCommaList(String s)
			throws NullPointerException {
		if (s == null) {
			throw new NullPointerException("No null string list accepted");
		}
		Set<String> set = new HashSet<String>();
		for (String v : s.split(",")) {
			v = v.trim();
			if (v.length() > 0) {
				set.add(v);
			}
		}
		return set;
	}

	/**
	 * Copy the provided map and returns it as a modifiable properties object.
	 * 
	 * @param map
	 *            the map to copy
	 * @return the properties copy
	 * @throws NullPointerException
	 *             if the map argument is null
	 */
	public static Properties asProperties(Map<String, String> map)
			throws NullPointerException {
		if (map == null) {
			throw new NullPointerException("No null map accepted");
		}
		Properties props = new Properties();
		for (Map.Entry<String, String> entry : map.entrySet()) {
			props.setProperty(entry.getKey(), entry.getValue());
		}
		return props;
	}

	/**
	 * Copy the properties state and returns it as a modifiable map. Only the
	 * key and value of type string are copied.
	 * 
	 * @param props
	 *            the properties object to copy
	 * @return the properties copy as a map
	 * @throws NullPointerException
	 *             if the props argument is null
	 */
	public static Map<String, String> asMap(Properties props)
			throws NullPointerException {
		if (props == null) {
			throw new NullPointerException("No null properties accepted");
		}
		Map<String, String> map = new HashMap<String, String>();
		for (Object key : props.keySet()) {
			Object value = props.get(key);
			if (key instanceof String && value instanceof String) {
				map.put((String) key, (String) value);
			}
		}
		return map;
	}

	/**
	 * Returns true if the string s ends with the end string ignoring the case.
	 * 
	 * @param s
	 *            the string to test
	 * @param end
	 *            the string suffix
	 * @return true if the string s ends with the end string ignoring the case
	 * @throws NullPointerException
	 *             if any string is null
	 */
	public static boolean endsWithIgnoreCase(String s, String end)
			throws NullPointerException {
		if (s == null) {
			throw new NullPointerException();
		}
		if (end == null) {
			throw new NullPointerException();
		}
		if (s.length() < end.length()) {
			return false;
		}
		String suffix = s.substring(s.length() - end.length());
		return suffix.equalsIgnoreCase(end);
	}
}
