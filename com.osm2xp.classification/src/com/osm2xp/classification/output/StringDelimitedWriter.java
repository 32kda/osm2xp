package com.osm2xp.classification.output;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.osm2xp.classification.annotations.Ignore;
import com.osm2xp.classification.annotations.Positive;
import com.osm2xp.classification.annotations.Present;
import com.osm2xp.classification.annotations.Result;
import com.osm2xp.core.logging.Osm2xpLogger;

public abstract class StringDelimitedWriter<T> implements Closeable{
	
	private static final String doubleFormat = "%.2f";
	
	private final static Set<Class<?>> NUMBER_REFLECTED_PRIMITIVES;
	static {
	    Set<Class<?>> s = new HashSet<>();
	    s.add(byte.class);
	    s.add(short.class);
	    s.add(int.class);
	    s.add(long.class);
	    s.add(float.class);
	    s.add(double.class);
	    NUMBER_REFLECTED_PRIMITIVES = s;
	}

	protected PrintWriter writer;
	
	protected List<Field> numFields;
	protected Set<Field> positiveFields;
	protected List<Field> boolFields;
	protected List<Field> stringFields;
	protected List<Field> analyzedFields = new ArrayList<>();
	protected Field resultField;

	protected String id;

	private String resultFieldName;
	
	public StringDelimitedWriter(File file, String id) throws IOException {
		this(file, id, null);
	}
	
	public StringDelimitedWriter(File file, String id, String resultFieldName) throws IOException {
		this.id = id;
		this.resultFieldName = resultFieldName;
		writer = new PrintWriter(new BufferedWriter(new FileWriter(file)));
	}
	
	public void write (T[] data) {
		for (T t : data) {
			write(t);
		}
	}
	
	public void write (Collection<T> data) {
		for (T t : data) {
			write(t);
		}
	}
	
	public void write (T data) {
		String str = getString(data);
		writer.println(str);
	}

	public String getString(T data) {
		if (numFields == null) {
			init(data.getClass());
		}
		StringBuilder builder = new StringBuilder();
		for (Field fld : numFields) {
			try {
				if (builder.length() > 0) {
					builder.append(',');
				}
				double value = fld.getDouble(data);
				if (positiveFields.contains(fld) && value <= 0) {
					builder.append('0');
					if (shouldBePresent(fld)) {
						builder.append(",0");
					}
				} else {
					builder.append(String.format(Locale.ROOT, doubleFormat, value));
					if (shouldBePresent(fld)) {
						builder.append(",1");
					}
				}
			} catch (IllegalArgumentException e) {
				Osm2xpLogger.log(e);
			} catch (IllegalAccessException e) {
				Osm2xpLogger.log(e);
			}
		}
		for (Field fld : boolFields) {
			try {
				if (builder.length() > 0) {
					builder.append(',');
				}
				boolean value = fld.getBoolean(data);
				builder.append(value?1:0);
			} catch (IllegalArgumentException e) {
				Osm2xpLogger.log(e);
			} catch (IllegalAccessException e) {
				Osm2xpLogger.log(e);
			}
		}
		for (Field fld : stringFields) {
			try {
				if (builder.length() > 0) {
					builder.append(',');
				}
				Object value = fld.get(data);
				boolean present = value != null;
				String res = "?";
				if (present) {
					res = '"' + value.toString() + '"';
				}
				builder.append(res);
				if (shouldBePresent(fld)) {
					builder.append(present?",1":",0");
				}
			} catch (IllegalArgumentException e) {
				Osm2xpLogger.log(e);
			} catch (IllegalAccessException e) {
				Osm2xpLogger.log(e);
			}
		}
		try {
			builder.append(',');
			Object value = resultField.get(data);
			builder.append(value != null?value.toString():"?");
		} catch (IllegalArgumentException e) {
			Osm2xpLogger.log(e);
		} catch (IllegalAccessException e) {
			Osm2xpLogger.log(e);
		}
		return builder.toString();
	}
	
	private boolean shouldBePresent(Field fld) {
		return fld.getAnnotation(Present.class) != null;
	}

	protected void init(Class<? extends Object> clazz) {
		List<Field> allFields = new ArrayList<Field>();
		getAllFields(allFields, clazz);
		for (Field fld : allFields) {
			fld.setAccessible(true);
		}
		allFields = allFields.stream().filter(field -> !isIgnored(field)).collect(Collectors.toList());
		Optional<Field> classFieldOp = allFields.stream().filter(field -> isResult(field)).findFirst();
		if (!classFieldOp.isPresent()) {
			if (resultFieldName != null) {
				throw new IllegalArgumentException("Result field " + resultFieldName + " not found in " + clazz.getSimpleName() + ".");
			}
			throw new IllegalArgumentException("No resulting class field specified in type " + clazz.getSimpleName() + ". Use @Result annotation on Enum value");
		}
		resultField = classFieldOp.get();
		allFields.remove(resultField);
		
		numFields = allFields.stream().filter(field -> isNumeric(field)).collect(Collectors.toList());
		positiveFields = numFields.stream().filter(field -> isPoistive(field)).collect(Collectors.toSet());
		boolFields = allFields.stream().filter(field -> isBool(field)).collect(Collectors.toList());
//		if (!resultField.getType().isEnum()) {
//			throw new IllegalArgumentException("Resulting class field (" + clazz.getSimpleName() + ") should be an enum.");
//		}
		stringFields = new ArrayList<>(allFields);
		stringFields.removeAll(numFields);
		stringFields.removeAll(boolFields);
		analyzedFields.addAll(numFields);
		analyzedFields.addAll(boolFields);
		analyzedFields.addAll(stringFields);
		analyzedFields.add(resultField);
		writeHeader();
	}
	
	protected abstract void writeHeader();

	private boolean isIgnored(Field field) {
		return field.isAnnotationPresent(Ignore.class);
	}

	protected boolean isNumeric(Field field) {
		return isReflectedAsNumber(field.getType());
	}
	
	protected boolean isBool(Field field) {
		Class<?> type = field.getType();
		return Boolean.class.isAssignableFrom(type) || boolean.class.equals(type);
	}

	protected boolean isResult(Field field) {
		if (resultFieldName != null) {
			return field.getName().equals(resultFieldName);
		}
		return field.isAnnotationPresent(Result.class);
	}
	
	protected boolean isPoistive(Field field) {
		return field.isAnnotationPresent(Positive.class);
	}
	
	protected  static boolean isReflectedAsNumber(Class<?> type) {
	    return Number.class.isAssignableFrom(type) || NUMBER_REFLECTED_PRIMITIVES.contains(type);
	}

	protected static List<Field> getAllFields(List<Field> fields, Class<?> type) {
	    fields.addAll(Arrays.asList(type.getDeclaredFields()));

	    if (type.getSuperclass() != null) {
	        getAllFields(fields, type.getSuperclass());
	    }

	    return fields;
	}


	@Override
	public void close() throws IOException {
		writer.close();
	}
	
	
	
}
