package com.matthewmariner.livelycities.data;

/**
 * Wire shape of {@code {"x":3238,"y":3425,"plane":0}}.
 *
 * <p>Boxed on purpose: a missing field must be distinguishable from a genuine
 * zero so the loader can reject the record instead of silently placing an
 * entity at the world origin. Deliberately NOT
 * {@link net.runelite.api.coords.WorldPoint} — that class is immutable with no
 * no-arg constructor, and asking Gson to fabricate one is exactly the kind of
 * implicit behaviour that breaks quietly on a library bump.
 */
public class PointRecord
{
	public Integer x;
	public Integer y;
	public Integer plane;
}
