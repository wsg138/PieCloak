package games.cubi.raycastedantiesp.core.utils;

import java.lang.annotation.*; /**
 * {@code int[]} fields annotated with this are backing arrays for {@code IntArrayLists}. {@code IntArrayLists} are not objects directly, instead just an {@code int[]} array, with static operations provided by {@link PrimitiveIntArrayList}.
 * <p>
 * This is used to avoid the overhead of an actual object for each {@link PrimitiveIntArrayList}. To further reduce overhead, the field may not be initialised, {@link PrimitiveIntArrayList} is null safe.
 *
 * <p> Do not use this array directly, all calls should go through {@link PrimitiveIntArrayList}.
 */
@Documented @Retention(RetentionPolicy.SOURCE) @Target({ElementType.TYPE_USE, ElementType.FIELD, ElementType.PARAMETER})
public @interface IntArrayListMarker {}
