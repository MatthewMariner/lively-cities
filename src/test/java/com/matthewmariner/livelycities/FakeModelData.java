package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Model;
import net.runelite.api.ModelData;

/**
 * A {@link ModelData} that records what was done to it and hands itself back.
 *
 * <p>The client's real ModelData mutates in place and returns {@code this}, so
 * that is what this does. The recorded call list is what lets a test assert
 * ordering — specifically that {@code cloneColors()} happens before the first
 * {@code recolor(..)}, which is the one thing standing between a recolour and
 * the client's shared model cache.
 */
final class FakeModelData extends StubModelData
{
	private final List<String> calls = new ArrayList<>();

	@Override
	public ModelData cloneVertices()
	{
		calls.add("cloneVertices");
		return this;
	}

	@Override
	public ModelData cloneColors()
	{
		calls.add("cloneColors");
		return this;
	}

	@Override
	public ModelData recolor(short find, short replace)
	{
		calls.add("recolor " + find + "->" + replace);
		return this;
	}

	@Override
	public ModelData rotateY90Ccw()
	{
		calls.add("rotateY90Ccw");
		return this;
	}

	@Override
	public ModelData scale(int x, int y, int z)
	{
		calls.add("scale " + x + "," + y + "," + z);
		return this;
	}

	@Override
	public ModelData translate(int x, int y, int z)
	{
		calls.add("translate " + x + "," + y + "," + z);
		return this;
	}

	@Override
	public Model light(int ambient, int contrast, int x, int y, int z)
	{
		calls.add("light " + ambient + "," + contrast + "," + x + "," + y + "," + z);
		return new StubModel();
	}

	List<String> calls()
	{
		return calls;
	}
}
