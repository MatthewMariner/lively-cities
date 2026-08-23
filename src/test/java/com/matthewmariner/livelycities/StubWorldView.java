package com.matthewmariner.livelycities;

/**
 * Every abstract method of {@link net.runelite.api.WorldView}, throwing.
 *
 * <p>Mechanically generated from {@code javap net.runelite.api.WorldView} against
 * the 1.12.36 API jar and then checked in — no reflection, no mocking
 * framework, nothing generated at runtime. It exists so {@link FakeWorldView} can implement
 * the 9 methods the render core actually calls without hand-typing the other
 * 18: a method this plugin has never used fails loudly the first time a test
 * reaches it, instead of quietly returning {@code null}.
 *
 * <p>One method per line on purpose. This is a lookup table, not code to read.
 */
class StubWorldView implements net.runelite.api.WorldView
{
	static UnsupportedOperationException unsupported(String method)
	{
		return new UnsupportedOperationException(
			"StubWorldView does not implement WorldView." + method + "(..) — the render core has never needed it");
	}

	// --- net.runelite.api.WorldView ---
	@Override public boolean contains(net.runelite.api.coords.LocalPoint a0) { throw unsupported("contains"); }
	@Override public boolean contains(net.runelite.api.coords.WorldPoint a0) { throw unsupported("contains"); }
	@Override public net.runelite.api.Projectile createProjectile(int a0, int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8, int a9, net.runelite.api.Actor a10, int a11, int a12) { throw unsupported("createProjectile"); }
	@Override public net.runelite.api.IndexedObjectSet<? extends net.runelite.api.NPC> npcs() { throw unsupported("npcs"); }
	@Override public net.runelite.api.IndexedObjectSet<? extends net.runelite.api.Player> players() { throw unsupported("players"); }
	@Override public net.runelite.api.IndexedObjectSet<? extends net.runelite.api.WorldEntity> worldEntities() { throw unsupported("worldEntities"); }
	@Override public net.runelite.api.IndexedObjectSet<? extends net.runelite.api.WorldView> worldViews() { throw unsupported("worldViews"); }
	@Override public int getBaseX() { throw unsupported("getBaseX"); }
	@Override public int getBaseY() { throw unsupported("getBaseY"); }
	@Override public net.runelite.api.Projection getCanvasProjection() { throw unsupported("getCanvasProjection"); }
	@Override public net.runelite.api.CollisionData[] getCollisionMaps() { throw unsupported("getCollisionMaps"); }
	@Override public net.runelite.api.Deque<net.runelite.api.GraphicsObject> getGraphicsObjects() { throw unsupported("getGraphicsObjects"); }
	@Override public int getId() { throw unsupported("getId"); }
	@Override public int[][][] getInstanceTemplateChunks() { throw unsupported("getInstanceTemplateChunks"); }
	@Override public net.runelite.api.Projection getMainWorldProjection() { throw unsupported("getMainWorldProjection"); }
	@Override public int[] getMapRegions() { throw unsupported("getMapRegions"); }
	@Override public int getPlane() { throw unsupported("getPlane"); }
	@Override public net.runelite.api.Scene getScene() { throw unsupported("getScene"); }
	@Override public net.runelite.api.Tile getSelectedSceneTile() { throw unsupported("getSelectedSceneTile"); }
	@Override public int getSizeX() { throw unsupported("getSizeX"); }
	@Override public int getSizeY() { throw unsupported("getSizeY"); }
	@Override public int getTileHeight(int a0, int a1, int a2) { throw unsupported("getTileHeight"); }
	@Override public int[][][] getTileHeights() { throw unsupported("getTileHeights"); }
	@Override public byte[][][] getTileSettings() { throw unsupported("getTileSettings"); }
	@Override public int getYellowClickAction() { throw unsupported("getYellowClickAction"); }
	@Override public boolean isInstance() { throw unsupported("isInstance"); }
	@Override public boolean isTopLevel() { throw unsupported("isTopLevel"); }
}
