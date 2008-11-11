/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2007 Sun Microsystems, Inc. All rights reserved.
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
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
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
 */

package com.sun.jmxa.impl ;

import com.sun.jmxa.generic.ClassAnalyzer;
import java.util.List ;
import java.util.Arrays ;
import java.util.ArrayList ;
import java.util.Map ;
import java.util.HashMap ;
import java.util.Set ;
import java.util.HashSet ;
import java.util.Iterator ;
import java.util.concurrent.atomic.AtomicLong ;

import java.lang.reflect.Method ;
import java.lang.reflect.Type ;

import javax.management.Attribute ;
import javax.management.AttributeList ;
import javax.management.MBeanException ;
import javax.management.InvalidAttributeValueException ;
import javax.management.AttributeNotFoundException ;
import javax.management.ReflectionException ;
import javax.management.MBeanInfo ;
import javax.management.MBeanOperationInfo ;
import javax.management.MBeanParameterInfo ;

import javax.management.openmbean.OpenMBeanAttributeInfo ;
import javax.management.openmbean.OpenMBeanAttributeInfoSupport ;
import javax.management.openmbean.OpenMBeanOperationInfo ;
import javax.management.openmbean.OpenMBeanOperationInfoSupport ;
import javax.management.openmbean.OpenMBeanParameterInfo ;
import javax.management.openmbean.OpenMBeanParameterInfoSupport ;
import javax.management.openmbean.OpenMBeanInfoSupport ;
import javax.management.NotificationBroadcasterSupport ;
import javax.management.AttributeChangeNotification ;

import com.sun.jmxa.generic.BinaryFunction ;
import com.sun.jmxa.ObjectNameKey ;
import com.sun.jmxa.ManagedOperation ;
import com.sun.jmxa.ManagedObject ;
import com.sun.jmxa.ParameterNames ;

import com.sun.jmxa.generic.DprintUtil;
import com.sun.jmxa.generic.DumpIgnore;
import com.sun.jmxa.generic.Pair ;
import com.sun.jmxa.generic.DumpToString ;

import com.sun.jmxa.generic.FacetAccessor;
import javax.management.JMException;

public class MBeanSkeleton {
    // Object evaluate( Object, List<Object> ) 
    // (or Result evaluate( Target, ArgList ))
    public interface Operation
        extends BinaryFunction<FacetAccessor,List<Object>,Object> {} ;

    private final String type ;
    @DumpToString
    private final AtomicLong sequenceNumber ;
    private final MBeanInfo mbInfo ;
    @DumpToString
    private final ManagedObjectManagerInternal mom ;
    @DumpIgnore
    private final DprintUtil dputil ;
    private final Map<String,AttributeDescriptor> setters ;
    private final Map<String,AttributeDescriptor> getters ;
    private AttributeDescriptor nameAttributeDescriptor ;
    private final Map<String,Map<List<String>,Operation>> operations ;
    private final List<OpenMBeanAttributeInfo> mbeanAttributeInfoList ; 
    private final List<OpenMBeanOperationInfo> mbeanOperationInfoList ; 
 
    private <K,L,V> void addToCompoundMap( Map<K,Map<L,V>> source, Map<K,Map<L,V>> dest ) {
        for (Map.Entry<K,Map<L,V>> entry : source.entrySet()) {
            Map<L,V> map = entry.getValue() ;
            if (map == null) {
                map = new HashMap<L,V>() ;
                dest.put( entry.getKey(), map ) ;
            }
            map.putAll( source.get( entry.getKey() ) ) ;
        }
    }

    private MBeanSkeleton( MBeanSkeleton first, MBeanSkeleton second ) {
        dputil = new DprintUtil( this ) ;
	this.mom = first.mom ;

        type = first.type ;

        sequenceNumber = new AtomicLong() ;
	setters = new HashMap<String,AttributeDescriptor>() ;
        setters.putAll( second.setters ) ;
        setters.putAll( first.setters ) ;

	getters = new HashMap<String,AttributeDescriptor>() ; 
        getters.putAll( second.getters ) ;
        getters.putAll( first.getters ) ;

	operations = new HashMap<String,Map<List<String>,Operation>>() ;
        addToCompoundMap( second.operations, operations ) ;
        addToCompoundMap( first.operations, operations ) ;

	mbeanAttributeInfoList = new ArrayList<OpenMBeanAttributeInfo>() ;
        mbeanAttributeInfoList.addAll( second.mbeanAttributeInfoList ) ;
        mbeanAttributeInfoList.addAll( first.mbeanAttributeInfoList ) ;

	mbeanOperationInfoList = new ArrayList<OpenMBeanOperationInfo>() ;
        mbeanOperationInfoList.addAll( second.mbeanOperationInfoList ) ;
        mbeanOperationInfoList.addAll( first.mbeanOperationInfoList ) ;

        mbInfo = makeMbInfo( first.mbInfo.getDescription() ) ;
    }

    private MBeanInfo makeMbInfo( String description ) {
	OpenMBeanAttributeInfo[] attrInfos = mbeanAttributeInfoList.toArray( 
	    new OpenMBeanAttributeInfo[mbeanAttributeInfoList.size()] ) ;
	OpenMBeanOperationInfo[] operInfos = mbeanOperationInfoList.toArray(
	    new OpenMBeanOperationInfo[mbeanOperationInfoList.size() ] ) ;
        
	return new OpenMBeanInfoSupport( 
	    type, description, attrInfos, null, 
            operInfos, null ) ;
    }

    /** Create a new MBeanSkeleton that is the composition of this one
     * and skel.  Note that, if this and skel contain the same attribute,
     * the version from skel will appear in the composition.
     */
    public MBeanSkeleton compose( MBeanSkeleton skel ) {
        return new MBeanSkeleton( skel, this ) ;
    }


    @Override
    public String toString() {
        return "DynamicMBeanSkeleton[type=" + type + "]" ;
    }
    
    // This method should only be called when getter.id.equals( setter.id ) 
    private void processAttribute( AttributeDescriptor getter, 
        AttributeDescriptor setter ) {

        if (mom.registrationFineDebug()) {
            dputil.enter( "processAttribute", "getter=", getter,
                "setter=", setter ) ;
        }
        
        try {
            if ((setter == null) && (getter == null)) {
                throw new IllegalArgumentException(
                    "At least one of getter and setter must not be null" ) ;
            }

            if ((setter != null) && (getter != null) 
                && !setter.type().equals( getter.type() )) {
                    throw new IllegalArgumentException( 
                        "Getter and setter types do not match" ) ;
            }

            AttributeDescriptor nonNullDescriptor = 
                (getter != null) ? getter : setter ;

            String name = nonNullDescriptor.id() ;
            String description = nonNullDescriptor.description() ;
            if (mom.registrationFineDebug()) {
                dputil.info( "name=", name, "description=", description ) ;
            }
            
            TypeConverter tc = mom.getTypeConverter( nonNullDescriptor.type() ) ;

            OpenMBeanAttributeInfo ainfo = new OpenMBeanAttributeInfoSupport( name, 
                description, tc.getManagedType(), 
                getter != null, setter != null, false ) ;
            
            if (mom.registrationFineDebug()) {
                dputil.info("ainfo=", ainfo ) ;
            }

            mbeanAttributeInfoList.add( ainfo ) ;
        } finally {
            if (mom.registrationFineDebug()) {
                dputil.exit() ;
            }
        }
    }

    private void analyzeAttributes( ClassAnalyzer ca ) {
        if (mom.registrationFineDebug()) {
            dputil.enter( "analyzeAttributes", "ca=", ca ) ;
        }
        
        try {
            Pair<Map<String,AttributeDescriptor>,
                Map<String,AttributeDescriptor>> amap =
                mom.getAttributes( ca ) ;

            getters.putAll( amap.first() ) ;
            setters.putAll( amap.second() ) ;
            
            if (mom.registrationFineDebug()) {
                dputil.info( "attributes=", amap ) ;
            }

            final Set<String> setterNames = new HashSet<String>(setters.keySet()) ;
            if (mom.registrationFineDebug()) {
                dputil.info( "(Before removing getters):setterNames=", setterNames ) ;
            }
            
            for (String str : getters.keySet()) {
                processAttribute( getters.get( str ), setters.get( str ) ) ;
                setterNames.remove( str ) ;
            }
            
            if (mom.registrationFineDebug()) {
                dputil.info( "(After removing getters):setterNames=", setterNames ) ;
            }
   
            // Handle setters without getters
            for (String str : setterNames) {
                processAttribute( null, setters.get( str ) ) ;
            }
        } finally {
            if (mom.registrationFineDebug()) {
                dputil.exit() ;
            }
        }
    }

    private void analyzeObjectNameKeys( ClassAnalyzer ca) {
        if (mom.registrationFineDebug()) {
            dputil.enter( "analyzeObjectNameKeys", "ca=", ca ) ;
        }
        
        try {
            final List<Method> annotatedMethods = ca.findMethods(
                mom.forAnnotation( ObjectNameKey.class )) ;
            
            if (annotatedMethods.size() == 0) {
                return ;
            }
            
            // If there are two methods with @ObjectNameKey in the same
            // class, we have an error.
            Method annotatedMethod = annotatedMethods.get(0) ;
            if (annotatedMethods.size() > 1) {
                Method second = annotatedMethods.get(1) ;
                
                if (annotatedMethod.getDeclaringClass().equals(
                    second.getDeclaringClass())) {
                    
                    throw new IllegalArgumentException( "Methods " 
                        + annotatedMethod + " and " + second 
                        + "are both annotated with @ObjectKeyName in class "
                        + annotatedMethod.getDeclaringClass().getName() ) ;
                }
            } 

            if (mom.registrationFineDebug()) {
                dputil.info( "annotatedMethod=", annotatedMethod ) ;
            }
            
            // XXX Need an I18N description
            nameAttributeDescriptor = AttributeDescriptor.makeFromAnnotated(
                mom, annotatedMethod, "name", "Name of this ManagedObject" ) ;            
        } finally {
            if (mom.registrationFineDebug()) {
                dputil.exit() ;
            }
        }
    }
    
    private Pair<Operation,OpenMBeanOperationInfo> makeOperation( 
        final Method m ) {
	
        if (mom.registrationFineDebug()) {
            dputil.enter( "makeOperation", "m=", m ) ;
        }
        
        try {
            final String desc = mom.getDescription( m ) ;
            final Type rtype = m.getGenericReturnType() ;
            final TypeConverter rtc = rtype == null ? null : mom.getTypeConverter( 
                rtype ) ;
            final Type[] atypes = m.getGenericParameterTypes() ;
            final List<TypeConverter> atcs = new ArrayList<TypeConverter>() ;
            for (Type ltype : atypes) {
                atcs.add( mom.getTypeConverter( ltype ) ) ;
            }

            if (mom.registrationFineDebug()) {
                dputil.info( "desc=", desc ) ;
                dputil.info( "rtype=", rtype ) ;
                dputil.info( "rtc=", rtc ) ;
                dputil.info( "atcs=", atcs ) ;
                dputil.info( "atypes=", atypes ) ;
            }
            
            final Operation oper = new Operation() {
                public Object evaluate( FacetAccessor target, List<Object> args ) {
                    if (mom.runtimeDebug()) {
                        dputil.enter( "Operation:evaluate", "taget=", target, 
                            "args=", args ) ;
                    }

                    Object[] margs = new Object[args.size()] ;
                    Iterator<Object> argsIterator = args.iterator() ;
                    Iterator<TypeConverter> tcIterator = atcs.iterator() ;
                    int ctr = 0 ;
                    while (argsIterator.hasNext() && tcIterator.hasNext()) {
                        final Object arg = argsIterator.next() ;
                        final TypeConverter tc = tcIterator.next() ;
                        margs[ctr++] = tc.fromManagedEntity( arg ) ;
                    }

                    if (mom.runtimeDebug()) {
                        dputil.info( "Before invoke: margs=", Arrays.asList( margs ) ) ;
                    }

                    Object result = target.invoke( m, margs ) ;

                    if (mom.runtimeDebug()) {
                        dputil.info( "After invoke: result=", result ) ;
                    }

                    if (rtc == null) {
                        return null ;
                    } else {
                        return rtc.toManagedEntity( result ) ;
                    }
                }
            } ;

            final ParameterNames pna = m.getAnnotation( ParameterNames.class ) ;
            if (mom.registrationFineDebug()) {
                dputil.info( "pna=", pna.value() ) ;
            }
            
            if (pna != null && pna.value().length != atcs.size()) {
                // XXX I18N
                throw new IllegalArgumentException( 
                    "ParametersNames annotation must have the same number" 
                    + " of arguments as the length of the method parameter list" );
            }

            final OpenMBeanParameterInfo[] paramInfo = 
                new OpenMBeanParameterInfo[ atcs.size() ] ;
            int ctr = 0 ;
            for (TypeConverter tc : atcs) {
                paramInfo[ctr] = new OpenMBeanParameterInfoSupport( 
                    (pna == null) ? "arg" + ctr : pna.value()[ctr], 
                    desc, tc.getManagedType() ) ;
                ctr++ ;
            }

            // XXX Note that impact is always set to ACTION_INFO here.  If this is 
            // useful to set in general, we need to add impact to the 
            // ManagedOperation annotation.
            // This is basically what JSR 255 does.
            final OpenMBeanOperationInfo operInfo = 
                new OpenMBeanOperationInfoSupport( m.getName(),
                desc, paramInfo, rtc.getManagedType(), 
                MBeanOperationInfo.ACTION_INFO ) ;

            if (mom.registrationFineDebug()) {
                dputil.info( "operInfo=", operInfo ) ;
            }
            
            return new Pair<Operation,OpenMBeanOperationInfo>( oper, operInfo ) ;
        } finally {
            if (mom.registrationFineDebug()) {
                dputil.exit() ;
            }
        }
    }

    private void analyzeOperations( ClassAnalyzer ca ) {
        if (mom.registrationFineDebug()) {
            dputil.enter( "analyzeOperations", "ca=", ca ) ;
        }
        
        try {
            // Scan for all methods annotation with @ManagedOperation, 
            // including inherited methods.
            final List<Method> ops = ca.findMethods( mom.forAnnotation( 
                ManagedOperation.class ) ) ;
            for (Method m : ops) {             
                final Pair<Operation,OpenMBeanOperationInfo> data = 
                    makeOperation( m ) ;
                final OpenMBeanOperationInfo info = data.second() ;

                final List<String> dataTypes = new ArrayList<String>() ;
                for (MBeanParameterInfo pi : info.getSignature()) {
                    // Replace recursion marker with the constructed implementation
                    dataTypes.add( pi.getType() ) ;
                }

                Map<List<String>,Operation> map = operations.get( m.getName() ) ;
                if (map == null) {
                    map = new HashMap<List<String>,Operation>() ;
                    operations.put( m.getName(), map ) ;
                }

                // Note that the first occurrence of any method will be the most
                // derived, so if there is already an entry, don't overwrite it.
                mom.putIfNotPresent( map, dataTypes, data.first() ) ;

                mbeanOperationInfoList.add( info ) ;
            }
        } finally {
            if (mom.registrationFineDebug()) {
                dputil.exit() ;
            }
        }
    }

    public MBeanSkeleton( final Class<?> annotatedClass, 
        final ClassAnalyzer ca, final ManagedObjectManagerInternal mom ) {

        dputil = new DprintUtil( this ) ;
	this.mom = mom ;

        final ManagedObject mo = annotatedClass.getAnnotation( 
            ManagedObject.class ) ;
        
        if (mo.type().equals("")) {
	    type = mom.getStrippedName( annotatedClass ) ;
        } else {
            type = mo.type() ;
        }

        sequenceNumber = new AtomicLong() ;
	setters = new HashMap<String,AttributeDescriptor>() ;
	getters = new HashMap<String,AttributeDescriptor>() ; 
	operations = new HashMap<String,Map<List<String>,Operation>>() ;
	mbeanAttributeInfoList = new ArrayList<OpenMBeanAttributeInfo>() ;
	mbeanOperationInfoList = new ArrayList<OpenMBeanOperationInfo>() ;

        analyzeAttributes( ca ) ;
        analyzeOperations( ca ) ;
        analyzeObjectNameKeys( ca ) ;

        mbInfo = makeMbInfo( mom.getDescription( annotatedClass ) ) ;
    }

    // The rest of the methods are used in the DynamicMBeanImpl code.
    
    public String getType() {
        if (mom.runtimeDebug()) {
            dputil.enter( "getType" ) ;
        }
        
        try {
            return type ;
        } finally {
            if (mom.runtimeDebug()) {
                dputil.exit( type ) ;
            }
        }
    }

    public Object getAttribute( FacetAccessor fa, String name) 
        throws AttributeNotFoundException, MBeanException, ReflectionException {

        if (mom.runtimeDebug()) {
            dputil.enter( "getAttribute", "fa=", fa, "name=", name ) ;
        }
        
        Object result = null ;
        try {
            AttributeDescriptor getter = getters.get( name ) ;
            if (getter == null) {
                if (mom.runtimeDebug()) {
                    dputil.info( "Error in finding getter ", name ) ;
                }
                throw new AttributeNotFoundException( "Could not find attribute " 
                    + name ) ;
            }
            result = getter.get( fa, mom.runtimeDebug() ) ;
            return result ;
        } finally {
            if (mom.runtimeDebug()) {
                dputil.exit( result ) ; 
            }
        }
    }
    
    public void setAttribute( final NotificationBroadcasterSupport emitter, 
        final FacetAccessor fa, final Attribute attribute) 
        throws AttributeNotFoundException, InvalidAttributeValueException, 
        MBeanException, ReflectionException  {
        
        if (mom.runtimeDebug()) {
            dputil.enter( "setAttribute", "emitter=", emitter,
                "fa=", fa, "attribute=", attribute ) ;
        }

        try {
            final String name = attribute.getName() ;
            final Object value = attribute.getValue() ;
            final AttributeDescriptor getter = getters.get( name ) ;
            final Object oldValue = (getter == null) ?
                null :
                getter.get( fa, mom.runtimeDebug() ) ;

            if (mom.runtimeDebug()) {
                dputil.info( "oldValue=", oldValue ) ;
            }
            
            final AttributeDescriptor setter = setters.get( name ) ;
            if (setter == null) {
                if (mom.runtimeDebug()) {
                    dputil.info( "Could not find setter" ) ;
                }
                throw new AttributeNotFoundException( 
                    "Could not find writable attribute " + name ) ;

            }

            setter.set( fa, value, mom.runtimeDebug() ) ;

            // Note that this code assumes that emitter is also the MBean,
            // because the MBean extends NotificationBroadcasterSupport!
            AttributeChangeNotification notification =
                new AttributeChangeNotification( emitter,
                    sequenceNumber.incrementAndGet(),
                    System.currentTimeMillis(),
                    "Changed attribute " + name,
                    name,
                    setter.tc().getManagedType().toString(),
                    oldValue,
                    value ) ;

            if (mom.runtimeDebug()) {
                dputil.info( "sending notification ", notification ) ;
            }
            
            emitter.sendNotification( notification ) ;    
        } finally {
            if (mom.runtimeDebug()) {
                dputil.exit() ;
            }
        }
    }
        
    public AttributeList getAttributes( FacetAccessor fa, String[] attributes) {
        if (mom.runtimeDebug()) {
            dputil.enter( "getAttributes", "attributes=",
                Arrays.asList( attributes ) ) ;
        }
        
        try {
            AttributeList result = new AttributeList() ;
            for (String str : attributes) {
                Object value = null ;
                Exception exception = null ;
                
                try {
                    value = getAttribute(fa, str);
                } catch (JMException ex) {
                    exception = ex ;
                }

                // If value == null, we had a problem in trying to fetch it,
                // so just ignore that attribute.  Returning null simply leads to
                // a blank entry in jconsole.  Do not let an error in fetching
                // one attribute prevent fetching the others.
                
                if (exception != null) {
                    if (mom.runtimeDebug()) {
                        dputil.exception( "getAttribute: ", exception ) ;
                    }
                }
                
                Attribute attr = new Attribute( str, value ) ;
                result.add( attr ) ;
            }

            return result ;
        } finally {
            if (mom.runtimeDebug()) {
                dputil.exit() ;
            }
        }
    }
        
    public AttributeList setAttributes( 
        final NotificationBroadcasterSupport emitter,
        final FacetAccessor fa, final AttributeList attributes) {
	
        if (mom.runtimeDebug()) {
            dputil.enter( "setAttributes", "emitter=", emitter,
                "fa=", fa, "attributes=", attributes ) ;
        }
        
        AttributeList result = new AttributeList() ;

        try {
            for (Object elem : attributes) {
                Attribute attr = (Attribute)elem ;
                Exception exception = null ;
                try {
                    setAttribute(emitter, fa, attr);
                } catch (JMException ex) {
                    exception = ex ;
                }
                
                if (exception == null) {
                    result.add( attr ) ;
                } else {
                    if (mom.runtimeDebug()) {
                        dputil.exception( "Error in setting attribute" 
                            + attr.getName(), exception ) ;
                    }
                }
            }
            
            return result ;
        } finally {
            if (mom.runtimeDebug()) {
                dputil.exit( result ) ;
            }
        }
    }
    
    public Object invoke( FacetAccessor fa, String actionName, Object params[], 
        String sig[]) throws MBeanException, ReflectionException  {
        final List<String> signature = Arrays.asList( sig ) ;
        final List<Object> parameters = Arrays.asList( params ) ;
        Object result = null ;
        
        if (mom.runtimeDebug()) {
            dputil.enter( "invoke", "fa=", fa, "actionName", actionName,
                "params=", parameters, "signature=", signature ) ;
        }
        
        try {
            final Map<List<String>,Operation> opMap = operations.get( 
                actionName ) ;
            if (opMap == null) {
                if (mom.runtimeDebug()) {
                    dputil.info( "Operation not found" ) ;
                }
                
                throw new IllegalArgumentException( 
                    "Could not find operation named " + actionName ) ;
            }

            final Operation op = opMap.get( signature ) ;
            if (op == null) {
                if (mom.runtimeDebug()) {
                    dputil.info( "Cound not find signature" ) ;
                }
                
                throw new IllegalArgumentException( 
                    "Could not find operation named " + actionName 
                    + " with signature " + Arrays.asList( sig ) ) ;
            }

            result = op.evaluate( fa, parameters ) ;
        } finally {
            if (mom.runtimeDebug()) {
                dputil.exit( result ) ;
            }
        }
        
        return result ;
    }
    
    public String getNameValue( final FacetAccessor fa ) throws
        MBeanException, ReflectionException {
        
        if (mom.runtimeDebug()) {
            dputil.enter( "getNameValue", "fa=", fa ) ;
        }
        
        String value = null ;
        try { 
            if (nameAttributeDescriptor == null) {
                if (mom.runtimeDebug()) {
                    dputil.info( "nameAttributeDescriptor is null" ) ;
                }
            } else {
                value = nameAttributeDescriptor.get(fa, 
                    mom.runtimeDebug()).toString();
            }
        } finally {
            if (mom.runtimeDebug()) {
                dputil.exit( value ) ;
            }
        }
        
        return value ;
    }
    
    public MBeanInfo getMBeanInfo() {
	return mbInfo ;
    }
}
