/* 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2009 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific 
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at legal/LICENSE.TXT.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 * 
 */ 
package org.glassfish.gmbal.generic ;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List ;
import java.util.Map ;
import java.util.ArrayList ;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Algorithms {
    private Algorithms() {}
    
    public static <T> List<T> list( T... arg ) {
        List<T> result = new ArrayList<T>() ;
        for (T obj : arg) {
            result.add( obj ) ;
        }
        return result ;
    }

    public static <S,T> Pair<S,T> pair( S first, T second ) {
        return new Pair<S,T>( first, second ) ;
    }
    
    public static <K,V> Map<K,V> map( Pair<K,V>... pairs ) {
        Map<K,V> result = new HashMap<K,V>() ;
        for (Pair<K,V> pair : pairs ) {
            result.put( pair.first(), pair.second() ) ;
        }
        return result ;
    }
        
    public static <A,R> UnaryFunction<A,R> mapToFunction( final Map<A,R> map ) {
        return new UnaryFunction<A,R>() {
            public R evaluate( A arg ) {
                return map.get( arg ) ;
            }
        } ;
    }

    public static <A,R> void map( final Collection<A> arg, 
        final Collection<R> result,
        final UnaryFunction<A,R> func ) {

        for (A a : arg) {
            final R newArg = func.evaluate( a ) ;
            if (newArg != null) {
                result.add( newArg ) ;
            }
        }
    }

    public static <K,A,R> Map<K,R> map( final Map<K,A> arg,
        final UnaryFunction<A,R> func ) {
        
        Map<K,R> result = new HashMap<K,R>() ;
        for (Map.Entry<K,A> entry : arg.entrySet()) {
            result.put( entry.getKey(), 
                func.evaluate( entry.getValue())) ;
        }
        
        return result ;
    }
    
    public static <A,R> List<R> map( final List<A> arg, 
        final UnaryFunction<A,R> func ) {

	final List<R> result = new ArrayList<R>() ;
	map( arg, result, func ) ;
	return result ;
    }

    public static <A> Predicate<A> and( 
        final Predicate<A> arg1,
        final Predicate<A> arg2 ) {
    
        return new Predicate<A>() {
            public boolean evaluate( A arg ) {
                return arg1.evaluate( arg ) && arg2.evaluate( arg ) ;
            }
        } ;
    }
    
    public static <A> Predicate<A> or( 
        final Predicate<A> arg1,
        final Predicate<A> arg2 ) {

        return new Predicate<A>() {
            public boolean evaluate( A arg ) {
                return arg1.evaluate( arg ) || arg2.evaluate( arg ) ;
            }
        } ;
    }
    
    public static <T> Predicate<T> FALSE( Class<T> cls
        ) {
        
        return new Predicate<T>() {
            public boolean evaluate( T arg ) {
                return false ;
            }
        } ;
    } ;
    
    public static <T> Predicate<T> TRUE( Class<T> cls
        ) {
        
        return new Predicate<T>() {
            public boolean evaluate( T arg ) {
                return true ;
            }
        } ;
    } ;
    
    public static <A> Predicate<A> not( 
        final Predicate<A> arg1 ) {
        
        return new Predicate<A>() {
            public boolean evaluate( A arg ) {
                return !arg1.evaluate( arg ) ;
            } 
        } ;
    }
        
    public static <A> void filter( final List<A> arg, final List<A> result,
	final Predicate<A> predicate ) {

	final UnaryFunction<A,A> filter = new UnaryFunction<A,A>() {
	    public A evaluate( A arg ) { 
		return predicate.evaluate( arg ) ? arg : null ; } } ;

	map( arg, result, filter ) ;
    }

    public static <A> List<A> filter( List<A> arg, Predicate<A> predicate ) {
	List<A> result = new ArrayList<A>() ;
	filter( arg, result, predicate ) ;
	return result ;
    }

    public static <A> A find( List<A> arg, Predicate<A> predicate ) {
	for (A a : arg) {
	    if (predicate.evaluate( a )) {
		return a ;
	    }
	}

	return null ;
    }

    public static <A,R> R fold( List<A> list, R initial, BinaryFunction<R,A,R> func ) {
        R result = initial ;
        for (A elem : list) {
            result = func.evaluate( result, elem ) ;
        }
        return result ;
    }
    
    /** Flatten the results of applying map to list into a list of T.
     * 
     * @param <S> Type of elements of list.
     * @param <T> Type of elements of result.
     * @param list List of elements of type S.
     * @param map function mapping S to List<T>.
     * @return List<T> containg results of applying map to each element of list.
     */
    public static <S,T> List<T> flatten( final List<S> list,
        final UnaryFunction<S,List<T>> map ) {        
        
        return fold( list, new ArrayList<T>(), 
            new BinaryFunction<List<T>,S,List<T>>() {
                public List<T> evaluate( List<T> arg1, S arg2 ) {
                    arg1.addAll( map.evaluate( arg2 ) ) ;
                    return arg1 ;
                }
        } ) ;     
    }

    /** Return the first element of the list, or invoke handleEmptyList if
     * list is empty.
     * @param <T> The type of the list element.
     * @param list The list 
     * @param handleEmptyList A runnable to call when the list is empty. Typically 
     * throws an exception.
     * @return The first element of the list, if any.
     */
    public static <T> T getFirst( Collection<T> list, Runnable handleEmptyList ) {
        for (T element : list) {
            return element ;
        }

        handleEmptyList.run();
        return null ;
    }

    /** Converts obj from an Array to a List, if obj is an array.
     * Otherwise just returns a List containing obj.
     */
    public static List convertToList( Object arg ) {
        List result = new ArrayList() ;
        if (arg != null) {
            Class cls = arg.getClass() ;
            if (cls.isArray()) {
                Class cclass = cls.getComponentType() ;
                if (cclass.equals( int.class )) {
                    for (int elem : (int[])arg) {
                        result.add( elem ) ;
                    }
                } else if (cclass.equals( byte.class )) {
                    for (byte elem : (byte[])arg) {
                        result.add( elem ) ;
                    }
                } else if (cclass.equals( boolean.class )) {
                    for (boolean elem : (boolean[])arg) {
                        result.add( elem ) ;
                    }
                } else if (cclass.equals( char.class )) {
                    for (char elem : (char[])arg) {
                        result.add( elem ) ;
                    }
                } else if (cclass.equals( short.class )) {
                    for (short elem : (short[])arg) {
                        result.add( elem ) ;
                    }
                } else if (cclass.equals( long.class )) {
                    for (long elem : (long[])arg) {
                        result.add( elem ) ;
                    }
                } else if (cclass.equals( float.class )) {
                    for (float elem : (float[])arg) {
                        result.add( elem ) ;
                    }
                } else if (cclass.equals( double.class )) {
                    for (double elem : (double[])arg) {
                        result.add( elem ) ;
                    }
                } else {
                    return Arrays.asList( (Object[])arg ) ;
                }
            } else {
                result.add( arg ) ;
                return result ;
            }
        }

        return result ;
    }

    /** Convert argument to String, either by toString, ot Arrays.toString.
     *
     * @param arg Object to convert.
     */
    public static String convertToString( Object arg ) {
        if (arg == null)
            return "<NULL>" ;

        Class cls = arg.getClass() ;
        if (cls.isArray()) {
            Class cclass = cls.getComponentType() ;
            if (cclass.equals( int.class ))
                        return Arrays.toString( (int[])arg ) ;
            if (cclass.equals( byte.class ))
                        return Arrays.toString( (byte[])arg ) ;
            if (cclass.equals( boolean.class ))
                        return Arrays.toString( (boolean[])arg ) ;
            if (cclass.equals( char.class ))
                        return Arrays.toString( (char[])arg ) ;
            if (cclass.equals( short.class ))
                        return Arrays.toString( (short[])arg ) ;
            if (cclass.equals( long.class ))
                        return Arrays.toString( (long[])arg ) ;
            if (cclass.equals( float.class ))
                        return Arrays.toString( (float[])arg ) ;
            if (cclass.equals( double.class ))
                        return Arrays.toString( (double[])arg ) ;
            return Arrays.toString( (Object[])arg ) ;
        } else {
            return arg.toString() ;
        }
    }

    private static Set<String> annotationMethods ;
    static {
        annotationMethods = new HashSet<String>() ;
        for (Method m : Annotation.class.getDeclaredMethods()) {
            annotationMethods.add( m.getName()) ;
        }
    }

    /** Given an annotation, return a Map that maps each field (given by a 
     * method name) to its value in the annotation.  If the value is an 
     * annotation, that value is recursively converted into a Map in the
     * same way.
     * 
     * @param ann The annotation to examine.
     * @param convertArraysToLists true if annotation values of array type
     * should be converted to an appropriate list.  This is often MUCH more
     * useful, but some contexts require arrays.
     * @return A map of annotation fields to their values.
     */
    public static Map<String,Object> getAnnotationValues( Annotation ann,
        boolean convertArraysToLists ) {
        // We must ignore all of the methods defined in the java.lang.Annotation API.
        Map<String,Object> result = new HashMap<String,Object>() ;
        for (Method m : ann.getClass().getDeclaredMethods()) {
            String name = m.getName() ;
            if (!annotationMethods.contains( name ) ) {
                Object value = null ;
                // Note: the following invoke should never fail
                try {
                    value = m.invoke(ann);
                } catch (IllegalAccessException ex) {
                    Logger.getLogger(Algorithms.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IllegalArgumentException ex) {
                    Logger.getLogger(Algorithms.class.getName()).log(Level.SEVERE, null, ex);
                } catch (InvocationTargetException ex) {
                    Logger.getLogger(Algorithms.class.getName()).log(Level.SEVERE, null, ex);
                }

                if (value != null) {
                    Class valueClass = value.getClass() ;
                    if (valueClass.isAnnotation()) {
                        value = getAnnotationValues( (Annotation)value,
                            convertArraysToLists ) ;
                    } else if (convertArraysToLists && valueClass.isArray()) {
                        value = convertToList(value) ;
                    }
                }

                result.put( name, value ) ;
            }
        }

        return result ;
    }
}
