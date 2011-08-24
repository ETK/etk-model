package org.etk.kernel.container.xml.object;

import java.util.Collection;
import java.util.Date;
import java.util.Map;

public class XMLBaseObject {

	final static public String STRING = "string";

	final static public String INT = "int";

	final static public String LONG = "long";

	final static public String BOOLEAN = "boolean";

	final static public String FLOAT = "float";

	final static public String DOUBLE = "double";

	final static public String DATE = "date";

	final static public String MAP = "map";

	final static public String COLLECTION = "collection";

	final static public String ARRAY = "array";

	final static public String NATIVE_ARRAY = "native-array";

	final static public String OBJECT = "object";

	protected String type;

	protected Object value;

	public XMLBaseObject() {
	}

	public XMLBaseObject(Class objecttype, Object val) throws Exception {
		if (val == null) {
			setType(objecttype);
		} else {
			setObjectValue(val);
		}
	}

	public String getType() {
		return type;
	}

	public void setType(String s) {
		type = s;
	}

	public void setType(Class clazz) {
		if (String.class.equals(clazz))
			type = STRING;
		else if (Integer.class.equals(clazz))
			type = INT;
		else if (Long.class.equals(clazz))
			type = LONG;
		else if (Float.class.equals(clazz))
			type = FLOAT;
		else if (Double.class.equals(clazz))
			type = DOUBLE;
		else if (Boolean.class.equals(clazz))
			type = BOOLEAN;
		else if (Date.class.equals(clazz))
			type = DATE;
		else if (Map.class.equals(clazz))
			type = MAP;
		else if (Collection.class.equals(clazz))
			type = COLLECTION;
		else if (XMLNativeArray.isNativeArray(clazz))
			type = NATIVE_ARRAY;
		else
			type = OBJECT;
	}

	public Object getValue() {
		return value;
	}

	public void setValue(Object v) {
		value = v;
	}

	public Object getObjectValue() throws Exception {
		if (value instanceof XMLCollection)
			return ((XMLCollection) value).getCollection();
		if (value instanceof XMLMap)
			return ((XMLMap) value).getMap();
		if (value instanceof XMLNativeArray)
			return ((XMLNativeArray) value).getValue();
		if (value instanceof XMLObject)
			return ((XMLObject) value).toObject();
		return value;
	}

	public void setObjectValue(Object o) throws Exception {
		if (o instanceof String)
			setString((String) o);
		else if (o instanceof Integer)
			setInt((Integer) o);
		else if (o instanceof Long)
			setLong((Long) o);
		else if (o instanceof Float)
			setFloat((Float) o);
		else if (o instanceof Double)
			setDouble((Double) o);
		else if (o instanceof Boolean)
			setBoolean((Boolean) o);
		else if (o instanceof Date)
			setDate((Date) o);
		else if (o instanceof Map)
			setMapValue((Map) o);
		else if (o instanceof Collection)
			setCollectiontValue((Collection) o);
		else if (o instanceof XMLObject)
			setObject((XMLObject) o);
		else if (XMLNativeArray.isNativeArray(o))
			setNativeArrayValue(o);
		else
			setObject(new XMLObject(o));
	}

	public XMLObject getObject() {
		if (value instanceof XMLObject)
			return (XMLObject) value;
		return null;
	}

	public void setObject(XMLObject o) {
		if (o == null)
			return;
		type = OBJECT;
		value = o;
	}

	public String getString() {
		if (value instanceof String)
			return (String) value;
		return null;
	}

	public void setString(String s) {
		if (s == null)
			return;
		type = STRING;
		value = s;
	}

	public Integer getInt() {
		if (value instanceof Integer)
			return (Integer) value;
		return null;
	}

	public void setInt(Integer i) {
		if (i == null)
			return;
		type = INT;
		value = i;
	}

	public Long getLong() {
		if (value instanceof Long)
			return (Long) value;
		return null;
	}

	public void setLong(Long l) {
		if (l == null)
			return;
		type = LONG;
		value = l;
	}

	public Float getFloat() {
		if (value instanceof Float)
			return (Float) value;
		return null;
	}

	public void setFloat(Float f) {
		if (f == null)
			return;
		type = DOUBLE;
		value = f;
	}

	public Double getDouble() {
		if (value instanceof Double)
			return (Double) value;
		return null;
	}

	public void setDouble(Double d) {
		if (d == null)
			return;
		type = FLOAT;
		value = d;
	}

	public Boolean getBoolean() {
		if (value instanceof Boolean)
			return (Boolean) value;
		return null;
	}

	public void setBoolean(Boolean b) {
		if (b == null)
			return;
		type = BOOLEAN;
		value = b;
	}

	public Date getDate() {
		if (value instanceof Date)
			return (Date) value;
		return null;
	}

	public void setDate(Date date) {
		if (date == null)
			return;
		type = DATE;
		value = date;
	}

	public XMLMap getMap() {
		if (value instanceof XMLMap)
			return (XMLMap) value;
		return null;
	}

	public void setMapValue(Map map) throws Exception {
		if (map == null)
			return;
		type = MAP;
		value = new XMLMap(map);
	}

	public void setMap(XMLMap map) {
		if (map == null)
			return;
		type = MAP;
		value = map;
	}

	public XMLCollection getCollection() {
		if (value instanceof XMLCollection)
			return (XMLCollection) value;
		return null;
	}

	public void setCollectiontValue(Collection collection) throws Exception {
		if (collection == null)
			return;
		type = COLLECTION;
		value = new XMLCollection(collection);
	}

	public void setCollection(XMLCollection collection) {
		if (collection == null)
			return;
		type = COLLECTION;
		value = collection;
	}

	public XMLNativeArray getNativeArray() {
		if (value instanceof XMLNativeArray)
			return (XMLNativeArray) value;
		return null;
	}

	public void setNativeArrayValue(Object array) throws Exception {
		if (array == null)
			return;
		type = NATIVE_ARRAY;
		value = new XMLNativeArray(array);
	}

	public void setNativeArray(XMLNativeArray array) {
		if (array == null)
			return;
		type = NATIVE_ARRAY;
		value = array;
	}
}
