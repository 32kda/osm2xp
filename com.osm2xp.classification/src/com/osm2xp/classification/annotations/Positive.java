package com.osm2xp.classification.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
/**
 * Indicates that only positive values are valid for given field
 * In case of negative or zero value it will be regarded as "no valid value"
 * 
 * @author 32kda 
 */
public @interface Positive {

}
