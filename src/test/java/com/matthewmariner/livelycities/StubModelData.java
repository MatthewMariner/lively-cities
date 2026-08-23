package com.matthewmariner.livelycities;

/**
 * Every abstract method of {@link net.runelite.api.ModelData}, throwing.
 *
 * <p>Mechanically generated from {@code javap net.runelite.api.ModelData} against
 * the 1.12.36 API jar and then checked in — no reflection, no mocking
 * framework, nothing generated at runtime. It exists so {@link FakeModelData} can implement
 * the 7 methods the render core actually calls without hand-typing the other
 * 27: a method this plugin has never used fails loudly the first time a test
 * reaches it, instead of quietly returning {@code null}.
 *
 * <p>One method per line on purpose. This is a lookup table, not code to read.
 */
class StubModelData implements net.runelite.api.ModelData
{
	static UnsupportedOperationException unsupported(String method)
	{
		return new UnsupportedOperationException(
			"StubModelData does not implement ModelData." + method + "(..) — the render core has never needed it");
	}

	// --- net.runelite.api.ModelData ---
	@Override public net.runelite.api.ModelData cloneColors() { throw unsupported("cloneColors"); }
	@Override public net.runelite.api.ModelData cloneTextures() { throw unsupported("cloneTextures"); }
	@Override public net.runelite.api.ModelData cloneTransparencies() { throw unsupported("cloneTransparencies"); }
	@Override public net.runelite.api.ModelData cloneTransparencies(boolean a0) { throw unsupported("cloneTransparencies"); }
	@Override public net.runelite.api.ModelData cloneVertices() { throw unsupported("cloneVertices"); }
	@Override public short[] getFaceColors() { throw unsupported("getFaceColors"); }
	@Override public net.runelite.api.Model light() { throw unsupported("light"); }
	@Override public net.runelite.api.Model light(int a0, int a1, int a2, int a3, int a4) { throw unsupported("light"); }
	@Override public net.runelite.api.ModelData recolor(short a0, short a1) { throw unsupported("recolor"); }
	@Override public net.runelite.api.ModelData retexture(short a0, short a1) { throw unsupported("retexture"); }
	@Override public net.runelite.api.ModelData shallowCopy() { throw unsupported("shallowCopy"); }

	// --- net.runelite.api.Mesh ---
	@Override public int getFaceCount() { throw unsupported("getFaceCount"); }
	@Override public int[] getFaceIndices1() { throw unsupported("getFaceIndices1"); }
	@Override public int[] getFaceIndices2() { throw unsupported("getFaceIndices2"); }
	@Override public int[] getFaceIndices3() { throw unsupported("getFaceIndices3"); }
	@Override public short[] getFaceTextures() { throw unsupported("getFaceTextures"); }
	@Override public byte[] getFaceTransparencies() { throw unsupported("getFaceTransparencies"); }
	@Override public int getVerticesCount() { throw unsupported("getVerticesCount"); }
	@Override public float[] getVerticesX() { throw unsupported("getVerticesX"); }
	@Override public float[] getVerticesY() { throw unsupported("getVerticesY"); }
	@Override public float[] getVerticesZ() { throw unsupported("getVerticesZ"); }
	@Override public net.runelite.api.ModelData rotateY180Ccw() { throw unsupported("rotateY180Ccw"); }
	@Override public net.runelite.api.ModelData rotateY270Ccw() { throw unsupported("rotateY270Ccw"); }
	@Override public net.runelite.api.ModelData rotateY90Ccw() { throw unsupported("rotateY90Ccw"); }
	@Override public net.runelite.api.ModelData scale(int a0, int a1, int a2) { throw unsupported("scale"); }
	@Override public net.runelite.api.ModelData translate(int a0, int a1, int a2) { throw unsupported("translate"); }

	// --- net.runelite.api.Renderable ---
	@Override public int getAnimationHeightOffset() { throw unsupported("getAnimationHeightOffset"); }
	@Override public net.runelite.api.Model getModel() { throw unsupported("getModel"); }
	@Override public int getModelHeight() { throw unsupported("getModelHeight"); }
	@Override public int getRenderMode() { throw unsupported("getRenderMode"); }
	@Override public void setModelHeight(int a0) { throw unsupported("setModelHeight"); }

	// --- net.runelite.api.Node ---
	@Override public long getHash() { throw unsupported("getHash"); }
	@Override public net.runelite.api.Node getNext() { throw unsupported("getNext"); }
	@Override public net.runelite.api.Node getPrevious() { throw unsupported("getPrevious"); }
}
