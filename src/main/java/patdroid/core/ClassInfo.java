/*
* Copyright 2014 Mingyuan Xia (http://mxia.me) and contributors
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
* Contributors:
*   Mingyuan Xia
*   Lu Gong
*/

package patdroid.core;

import java.lang.reflect.Modifier;
import java.util.*;

import patdroid.util.Log;

/**
 * The class representation. Each class is uniquely identified by its full name.
 * So given a class full name, there is exactly one ClassInfo representing it.
 * ClassInfo works in a late-bind manner with ClassDetail.
 * A ClassInfo could just refer to a type without any details about its methods, fields, etc.
 * Then later on, when the class details become available, a ClassDetail iobject is created and
 * attached to the ClassInfo.
 * <p>
 * ClassInfos are obtained by find-series functions not created by constructors.
 */
public final class ClassInfo {
	public static final JavaScope globalScope = new JavaScope();
	public static ClassDetailLoader rootDetailLoader = new ClassDetailLoader();

	/**
	 * The Java canonical class name
	 */
	public final String fullName;
	boolean isMissing;
	ClassDetail details;

	/**
	 * Low-level constructor
	 * @param fullName the full name of the class
	 */
	ClassInfo(String fullName) {
		this.fullName = fullName;
	}

	public static Collection<ClassInfo> getAllClasses() {
		return globalScope.getAllClasses();
	}

	/**
	  * Find a class with given name
	  * @param fullName the full name of the class
	  * @return the class or null if not found
	  */
	public static ClassInfo findClass(String fullName) {
		return globalScope.findClass(fullName);
	}

	/**
	 * Find a class representation with give name. If not found, create one.
	 * @param fullName the full name of the class
	 * @return the class found or just created
	 */
	public static ClassInfo findOrCreateClass(String fullName) {
		ClassInfo u = globalScope.findClass(fullName);
		if (u == null) {
			// create a new class
			u = globalScope.createClass(fullName);
			// if it is an array, settle its base too
			if (u.isArray()) {
				findOrCreateClass(fullName.substring(1));
			}
		}
		return u;
	}

	public static ClassInfo[] findOrCreateClass(String[] fullNames) {
		final ClassInfo[] a = new ClassInfo[fullNames.length];
		for (int i = 0; i < fullNames.length; ++i) {
			a[i] = findOrCreateClass(fullNames[i]);
		}
		return a;
	}

	/**
	 * Dump the class hierarchy
	 * @return a String containing all the classes in the global scope
	 */
	public static String dumpClassHierarchy() {
		return globalScope.getAllClassNames().toString();
	}

	/**
	 * A framework class is a class that is not found in the apk being parsed
	 * <p>
	 * <b>Note:</b> this might start class loading if the class is not loaded yet
	 * @return if the class is a framework class
	 */
	public boolean isFrameworkClass() {
		return getDetails().isFrameworkClass();
	}

	/**
	 * Sometimes the apk has missing classes. A missing class is not
	 * a framework class and cannot be found in the apk
	 * @return if this class is missing
	 */
	public boolean isMissing() {
		return isMissing;
	}
	
	/**
	 * Get the type of a non-static field. This functions will look into the base class.
	 * <p>
	 * <b>Note:</b> this might start class loading if the class is not loaded yet
	 * @param fieldName the name of the field.
	 * @return the type, or null if not found or the class is missing
	 */
	public ClassInfo getFieldType(String fieldName) {
		return (isMissing ? null : getDetails().getFieldType(fieldName));
	}
	
	/**
	 * Get the type of a static field. This functions might look into its base class.
	 * <p>
	 * <b>Note:</b> this might start class loading if the class is not loaded yet
	 * @param fieldName the name of the static field
	 * @return the type of the static field, or null if not found or the class is missing
	 */
	public ClassInfo getStaticFieldType(String fieldName) {
		return (isMissing ? null : getDetails().getStaticFieldType(fieldName));
	}

	/**
	 * Get all the fields declared in this class.
	 * <p>
	 * <b>Note:</b> this might start class loading if the class is not loaded yet
	 * @return a key-value store mapping field name to their types 
	 */
	public HashMap<String, ClassInfo> getAllFieldsHere() {
		return (isMissing ? null : getDetails().getAllFieldsHere());
	}
	
	/**
	 * Get all the static fields declared in this class.
	 * <p>
	 * <b>Note:</b> this might start class loading if the class is not loaded yet
	 * @return a key-value store mapping static field name to their types 
	 */
	public HashMap<String, ClassInfo> getAllStaticFieldsHere() {
		return (isMissing ? null : getDetails().getAllStaticFieldsHere());
	}

	/**
	 *
	 * @return all methods in the class
     */
	public Collection<MethodInfo> getAllMethods() {
		if (isMissing) return null;
		return getDetails().getAllMethods();
	}

	/**
	 * Find a method declared in this class
	 * <p>
	 * <b>Note:</b> this might start class loading if the class is not loaded yet
	 * @param mproto the method prototype
	 * @return the method in this class, or null if not found or the class is missing
	 */
	public MethodInfo findMethodHere(MethodInfo mproto) {
		return (isMissing ? null : getDetails().findMethodHere(mproto));
	}
	
	/**
	 * Find a method declared in this class
	 * <p>
	 * <b>Note:</b> this might start class loading if the class is not loaded yet
	 * @param name the name of the method
	 * @param paramTypes parameter types
	 * @return the method representation, or null if not found or the class is missing
	 */
	public MethodInfo findMethodHere(String name, ClassInfo[] paramTypes) {
		return findMethodHere(MethodInfo.makePrototype(name, null, paramTypes, 0));
	}
	
	/**
	 * Find a method declared in this class
	 * <p>
	 * <b>Note:</b> this might start class loading if the class is not loaded yet
	 * @param name the name of the method
	 * @param paramTypes parameter types
	 * @return the method representation, or null if not found or the class is missing
	 */
	public MethodInfo findMethodHere(String name, Class<?>... paramTypes) {
		return findMethodHere(name, globalScope.findOrCreateClass(paramTypes));
	}
	
	/**
	 * Find all methods that have the give name and are declared in this class
	 * <p>
	 * <b>Note:</b> this might start class loading if the class is not loaded yet
	 * @param name the method name
	 * @return an array of methods, or null if the class is missing.
	 * An empty array will be returned in case of not finding any method
	 */
	public MethodInfo[] findMethodsHere(String name) {
		return (isMissing ? null : getDetails().findMethodsHere(name));
	}
	
	/**
	 * Find all methods that have the give name. This might need to look into base classes 
	 * <p>
	 * <b>Note:</b> this might start class loading if the class is not loaded yet
	 * @param name the method name
	 * @return an array of methods, or null if the class is missing.
	 * An empty array will be returned in case of not finding any method
	 */
	public MethodInfo[] findMethods(String name) {
		return (isMissing ? null : getDetails().findMethods(name));
	}

	/**
	 * Find a method with given function prototype. This might need to look into base classes 
	 * <p>
	 * <b>Note:</b> this might start class loading if the class is not loaded yet
	 * @param mproto the method prototype
	 * @return  the method representation, or null if not found or the class is missing
	 */
	public MethodInfo findMethod(MethodInfo mproto) {
		return (isMissing ? null : getDetails().findMethod(mproto));
	}

	/**
	 * Find a method. This might need to look into base classes 
	 * <p>
	 * <b>Note:</b> this might start class loading if the class is not loaded yet
	 * @param name the name of the method
	 * @param paramTypes parameter types
	 * @return the method representation, or null if not found or the class is missing
	 */
	public MethodInfo findMethod(String name, ClassInfo[] paramTypes) {
		return findMethod(MethodInfo.makePrototype(name, null, paramTypes, 0));
	}

	/**
	 * Find a method. This might need to look into base classes
	 * <p>
	 * <b>Note:</b> this might start class loading if the class is not loaded yet
	 * @param name the name of the method
	 * @param paramTypes parameter types
	 * @return the method representation, or null if not found or the class is missing
	 */
	public MethodInfo findMethod(String name, Class<?>... paramTypes) {
		return findMethod(name, globalScope.findOrCreateClass(paramTypes));
	}
	
	/**
	 * TypeA is convertible to TypeB if and only if TypeB is an indirect 
	 * superclass or an indirect interface of TypeA.
	 * <p>
	 * <b>Note:</b> this might start class loading if the class is not loaded yet
	 * @param type type B
	 * @return if this class can be converted to the other.
	 */
	public boolean isConvertibleTo(ClassInfo type) {
		if (type.isPrimitive()) {
			return (type == globalScope.primitiveVoid || isPrimitive());
		} else {
			return getDetails().isConvertibleTo(type);
		}
	}

	@Override
	public String toString() {
		return fullName;
	}
	
	@Override
	public int hashCode() {
		return fullName.hashCode();
	}

	/**
	 * @return if this class is an array type
	 */
	public boolean isArray() {
		return fullName.startsWith("[");
	}
	
	/**
	 * Return the element type given this class as an array type. 
	 * @return the element type
	 */
	public ClassInfo getElementClass() {
		Log.doAssert(isArray(), "Try getting the element class of a non-array class " + this);
		final char first = fullName.charAt(1);
		switch (first) {
		case 'C': return globalScope.primitiveChar;
		case 'I': return globalScope.primitiveInt;
		case 'B': return globalScope.primitiveByte;
		case 'Z': return globalScope.primitiveBoolean;
		case 'F': return globalScope.primitiveFloat;
		case 'D': return globalScope.primitiveDouble;
		case 'S': return globalScope.primitiveShort;
		case 'J': return globalScope.primitiveLong;
		case 'V': return globalScope.primitiveVoid;
		case 'L': return ClassInfo.findOrCreateClass(fullName.substring(2, fullName.length() - 1));
		case '[': return ClassInfo.findOrCreateClass(fullName.substring(1));
		default:
			Log.err("unknown element type for:" + fullName);
			return null;
		}
	}
	
	/**
	 *
	 * <b>Note:</b> this might cause class loading if the class is not loaded yet
	 * @return the super class, or null if this class is java.lang.Object
	 */
	public ClassInfo getSuperClass() {
		return getDetails().getSuperClass();
	}

	/**
	 * Get the interfaces that the current class implements
	 * @return interfaces
	 */
	public ClassInfo[] getInterfaces() { return getDetails().getInterfaces(); }

	/**
	 * Change the super class of this class to a new super class, the
	 * derivedClasses will be updated accordingly.
	 * @param superClass new super class for this class
	 */
	public void setSuperClass(ClassInfo superClass) {
		ClassDetail origDetails = getDetails();
		origDetails.removeDerivedClasses(this);
		details = origDetails.changeSuperClass(superClass);
		details.updateDerivedClasses(this);
	}

	/**
	 * @return if this class is an inner class
	 */
	public boolean isInnerClass() {
		return fullName.lastIndexOf('$') != -1;
	}
	
	/**
	 * @return the outer class
	 */
	public ClassInfo getOuterClass() {
		Log.doAssert(isInnerClass(), "Try getting the outer class from a non-inner class" + this);
		return ClassInfo.findOrCreateClass(fullName.substring(0, fullName.lastIndexOf('$')));
	}

	/**
	 * @return true if the class is a primitive type
	 */
	public boolean isPrimitive() {
		return globalScope.primitives.contains(this);
	}

	/**
	 * @return if the class is final
	 */
	public boolean isFinal() {
		return !isMissing && Modifier.isFinal(getDetails().accessFlags);
	}

	/**
	 * @return if the class is an interface
	 */
	public boolean isInterface() {
		return !isMissing && Modifier.isInterface(getDetails().accessFlags);
	}

	public boolean isAbstract() {
		return !isMissing && Modifier.isAbstract(getDetails().accessFlags);
	}
	
	/**
	 * Get the default constructor
	 * <p>
	 * <b>Note:</b> this might start class loading if the class is not loaded yet
	 * @return the default constructor, or null if not found
	 */
	public MethodInfo getDefaultConstructor() {
		return findMethodHere(MethodInfo.CONSTRUCTOR, new ClassInfo[] {this});
	}
	
	/**
	 * Find the static initializer method of the class
	 * <p>
	 * <b>Note:</b> this might start class loading if the class is not loaded yet
	 * @return the static initializer or null if not found
	 */
	public MethodInfo getStaticInitializer() {
		return findMethod(MethodInfo.STATIC_INITIALIZER);
	}

	/**
	 * Get the short name of the class. The short name is the part after the last
	 * '.' in the full name of the class
	 * @return the short name
	 */
	public String getShortName() {
		final int idx = fullName.lastIndexOf('.');
		if (idx == -1) {
			return fullName;
		} else {
			return fullName.substring(idx+1, fullName.length());
		}
	}

	/**
	 * Get the details of the class. As class loading is on-demand, this may trigger
	 * a class loading based on reflection
	 * <p>
	 * <b>Note:</b> this might start class loading if the class is not loaded yet
	 * @return the class detailed info
	 */
	public ClassDetail getDetails() {
		if (details != null) {
			return details;
		} else {
			try {
				rootDetailLoader.load(this);
				return details;
			} catch (ClassNotFoundException e) {
				Log.debug("Cannot find framework class: " + fullName);
			} catch (ExceptionInInitializerError e) {
				Log.warn("Framework class not visible: " + fullName);
			} catch (NoClassDefFoundError e) {
				Log.warn("Cannot find framework class def: " + fullName);
			}
			this.isMissing = true;
			return ClassDetail.missingDetail;
		}
	}


	/**
	 * An almost final class has no derived classes in the current class tree
	 * @return if a class is "almost final"
	 */
	public boolean isAlmostFinal() {
		return (getDetails().derivedClasses.size() == 0);
	}

}
