package org.brts.common.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClassUtils {
	/**
	 * Return all field names of the given POJO, including inherited fields. The field names are returned as a set of
	 * strings.
	 *
	 * @param pojo
	 * @return
	 */
	public static <T extends Annotation> Map<String, T> findAllFieldsHavingAnnotation(Object pojo,
			Class<T> annotation) {
		var klass = pojo.getClass();
		Map<String, T> result = new HashMap<>();
		while (klass != null) {
			for (Field f : klass.getDeclaredFields()) {
				if (f.isAnnotationPresent(annotation)) {
					result.put(f.getName(), f.getAnnotation(annotation));
				}
			}
			klass = klass.getSuperclass();
		}
		return result;
	}

	/**
	 * Find the setter method for the given field name in the given POJO. The setter method is expected to have the name
	 * "setX" where X is the field name with the first letter capitalized, and to take a single parameter of any type.
	 * If no such method is found, return null.
	 *
	 * @param pojo
	 * @param fieldName
	 * @return
	 */
	public static Method resolveSetter(Object pojo, String fieldName) {
		String setterName = "set" + capitalize(fieldName);
		for (var method : pojo.getClass().getMethods()) {
			if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
				return method;
			}
		}
		return null;
	}

	private static String capitalize(String s) {
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	public static Method resolveGetter(Object pojo, String fieldName) {
		String getterName1 = "get" + capitalize(fieldName);
		String getterName2 = "is" + capitalize(fieldName);
		for (var method : pojo.getClass().getMethods()) {
			if ((method.getName().equals(getterName1) || method.getName().equals(getterName2))
					&& method.getParameterCount() == 0) {
				return method;
			}
		}
		return null;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Object convertValueForSetter(String valueAsString, Method setter) {
		Class<?> paramType = setter.getParameterTypes()[0];
		if (paramType == String.class) {
			return valueAsString;
		} else if (paramType == int.class || paramType == Integer.class) {
			if (valueAsString.startsWith("0x") || valueAsString.startsWith("0X")) {
				return (int) Long.parseLong(valueAsString.substring(2), 16);
			}
			return Integer.parseInt(valueAsString);
		} else if (paramType == long.class || paramType == Long.class) {
			if (valueAsString.startsWith("0x") || valueAsString.startsWith("0X")) {
				return Long.parseLong(valueAsString.substring(2), 16);
			}
			return Long.parseLong(valueAsString);
		} else if (paramType == double.class || paramType == Double.class) {
			return Double.parseDouble(valueAsString);
		} else if (paramType == float.class || paramType == Float.class) {
			return Float.parseFloat(valueAsString);
		} else if (paramType == boolean.class || paramType == Boolean.class) {
			return Boolean.parseBoolean(valueAsString);
		} else if (paramType.isEnum()) {
			return Enum.valueOf((Class<Enum>) paramType, valueAsString);
		} else {
			throw new IllegalArgumentException(
					"Unsupported parameter type for setter " + setter.getName() + ": " + paramType.getName());
		}
	}

	public static void setIfAbsent(Object pojo, String fieldName, String valueAsString) {
		Method getter = resolveGetter(pojo, fieldName);
		// check if value is already set on the pojo (non-null) - if so, skip it (properties should not override
		// explicitly set values)
		String className = pojo.getClass().getSimpleName();
		try {
			Object currentValue = getter.invoke(pojo);
			if (currentValue != null && !(currentValue instanceof Number && ((Number) currentValue).longValue() == 0)
					&& !(currentValue instanceof Boolean && ((Boolean) currentValue).booleanValue() == false)) {
				log.trace("Skipping field '{}' on {} since it is already set to '{}'", fieldName, className,
						currentValue);
				return;
			}
			Method setter = resolveSetter(pojo, fieldName);
			Object convertedValue = convertValueForSetter(valueAsString, setter);
			setter.invoke(pojo, convertedValue);
			log.debug("Set property '{}' on {} via setter {} with value '{}'", fieldName, className, setter.getName(),
					valueAsString);
		} catch (Exception e) {
			log.warn("Error checking/setting current value of field '{}' on {}: {}", fieldName, className, e);
			// this is actually a coding problem, it should not happen.,
			throw new RuntimeException(
					"Error checking/setting current value of field '" + fieldName + "' on " + className, e);
		}
	}
}
