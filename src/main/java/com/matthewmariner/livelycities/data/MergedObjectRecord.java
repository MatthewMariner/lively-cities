package com.matthewmariner.livelycities.data;

import com.google.gson.annotations.SerializedName;

/**
 * Wire shape of an entry in {@code mergedObjects} — an extra model merged into
 * the entity's own, optionally pre-rotated in 90 degree counter-clockwise steps.
 */
public class MergedObjectRecord
{
	@SerializedName("objectID")
	public Integer objectId;

	public Integer count90CCWRotations;
}
