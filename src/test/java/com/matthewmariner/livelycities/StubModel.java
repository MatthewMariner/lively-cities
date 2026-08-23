package com.matthewmariner.livelycities;

/**
 * Every abstract method of {@link net.runelite.api.Model}, throwing.
 *
 * <p>Mechanically generated from {@code javap net.runelite.api.Model} against
 * the 1.12.36 API jar and then checked in — no reflection, no mocking
 * framework, nothing generated at runtime.
 *
 * <p>Nothing here is ever called: the render core hands the lit model straight
 * to {@code RuneLiteObject.setModel}, which stores it and reads nothing. That is
 * exactly why every method throws — if a future change starts asking a model
 * questions, the test that reaches it says so instead of quietly getting
 * {@code null}.
 *
 * <p>One method per line on purpose. This is a lookup table, not code to read.
 */
class StubModel implements net.runelite.api.Model
{
	static UnsupportedOperationException unsupported(String method)
	{
		return new UnsupportedOperationException(
			"StubModel does not implement Model." + method + "(..) — the render core has never needed it");
	}

	// --- net.runelite.api.Model ---
	@Override public void calculateBoundsCylinder() { throw unsupported("calculateBoundsCylinder"); }
	@Override public void calculateExtreme(int a0) { throw unsupported("calculateExtreme"); }
	@Override public void drawFrustum(int a0, int a1, int a2, int a3, int a4, int a5, int a6) { throw unsupported("drawFrustum"); }
	@Override public void drawOrtho(int a0, int a1, int a2, int a3, int a4, int a5, int a6, int a7) { throw unsupported("drawOrtho"); }
	@Override public net.runelite.api.AABB getAABB(int a0) { throw unsupported("getAABB"); }
	@Override public int getBottomY() { throw unsupported("getBottomY"); }
	@Override public int getBufferOffset() { throw unsupported("getBufferOffset"); }
	@Override public int getDiameter() { throw unsupported("getDiameter"); }
	@Override public byte[] getFaceBias() { throw unsupported("getFaceBias"); }
	@Override public int[] getFaceColors1() { throw unsupported("getFaceColors1"); }
	@Override public int[] getFaceColors2() { throw unsupported("getFaceColors2"); }
	@Override public int[] getFaceColors3() { throw unsupported("getFaceColors3"); }
	@Override public byte[] getFaceRenderPriorities() { throw unsupported("getFaceRenderPriorities"); }
	@Override public byte getOverrideAmount() { throw unsupported("getOverrideAmount"); }
	@Override public byte getOverrideHue() { throw unsupported("getOverrideHue"); }
	@Override public byte getOverrideLuminance() { throw unsupported("getOverrideLuminance"); }
	@Override public byte getOverrideSaturation() { throw unsupported("getOverrideSaturation"); }
	@Override public int getRadius() { throw unsupported("getRadius"); }
	@Override public int getSceneId() { throw unsupported("getSceneId"); }
	@Override public int[] getTexIndices1() { throw unsupported("getTexIndices1"); }
	@Override public int[] getTexIndices2() { throw unsupported("getTexIndices2"); }
	@Override public int[] getTexIndices3() { throw unsupported("getTexIndices3"); }
	@Override public byte[] getTextureFaces() { throw unsupported("getTextureFaces"); }
	@Override public byte getTransparency() { throw unsupported("getTransparency"); }
	@Override public short[] getUnlitFaceColors() { throw unsupported("getUnlitFaceColors"); }
	@Override public net.runelite.api.Model getUnskewedModel() { throw unsupported("getUnskewedModel"); }
	@Override public int getUvBufferOffset() { throw unsupported("getUvBufferOffset"); }
	@Override public int[] getVertexNormalsX() { throw unsupported("getVertexNormalsX"); }
	@Override public int[] getVertexNormalsY() { throw unsupported("getVertexNormalsY"); }
	@Override public int[] getVertexNormalsZ() { throw unsupported("getVertexNormalsZ"); }
	@Override public int getXYZMag() { throw unsupported("getXYZMag"); }
	@Override public void setBufferOffset(int a0) { throw unsupported("setBufferOffset"); }
	@Override public void setSceneId(int a0) { throw unsupported("setSceneId"); }
	@Override public void setUvBufferOffset(int a0) { throw unsupported("setUvBufferOffset"); }
	@Override public boolean useBoundingBox() { throw unsupported("useBoundingBox"); }

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
	@Override public net.runelite.api.Model rotateY180Ccw() { throw unsupported("rotateY180Ccw"); }
	@Override public net.runelite.api.Model rotateY270Ccw() { throw unsupported("rotateY270Ccw"); }
	@Override public net.runelite.api.Model rotateY90Ccw() { throw unsupported("rotateY90Ccw"); }
	@Override public net.runelite.api.Model scale(int a0, int a1, int a2) { throw unsupported("scale"); }
	@Override public net.runelite.api.Model translate(int a0, int a1, int a2) { throw unsupported("translate"); }

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
