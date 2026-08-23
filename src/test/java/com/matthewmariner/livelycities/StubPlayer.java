package com.matthewmariner.livelycities;

/**
 * Every abstract method of {@link net.runelite.api.Player}, throwing.
 *
 * <p>Mechanically generated from {@code javap net.runelite.api.Player}, and its
 * {@code Actor} / {@code Renderable} / {@code CameraFocusableEntity} / {@code Node}
 * supertypes, against the 1.12.36 API jar and then checked in — no reflection, no
 * mocking framework, nothing generated at runtime. Same reasoning as
 * {@link StubClient}: it exists so {@link FakePlayer} can implement the one method
 * the plugin actually calls without hand-typing the other 79, and so a method this
 * plugin has never used fails loudly the first time a test reaches it instead of
 * quietly returning {@code null}.
 *
 * <p>One method per line on purpose. This is a lookup table, not code to read.
 */
class StubPlayer implements net.runelite.api.Player
{
	static UnsupportedOperationException unsupported(String method)
	{
		return new UnsupportedOperationException(
			"StubPlayer does not implement Player." + method + "(..) — the plugin has never needed it");
	}

	// --- net.runelite.api.Player ---
	@Override public boolean isClanMember() { throw unsupported("isClanMember"); }
	@Override public boolean isFriend() { throw unsupported("isFriend"); }
	@Override public boolean isFriendsChatMember() { throw unsupported("isFriendsChatMember"); }
	@Override public int getCombatLevel() { throw unsupported("getCombatLevel"); }
	@Override public int getId() { throw unsupported("getId"); }
	@Override public int getSkullIcon() { throw unsupported("getSkullIcon"); }
	@Override public int getTeam() { throw unsupported("getTeam"); }
	@Override public net.runelite.api.HeadIcon getOverheadIcon() { throw unsupported("getOverheadIcon"); }
	@Override public net.runelite.api.PlayerComposition getPlayerComposition() { throw unsupported("getPlayerComposition"); }
	@Override public void setSkullIcon(int a0) { throw unsupported("setSkullIcon"); }

	// --- net.runelite.api.Actor ---
	@Override public boolean hasSpotAnim(int a0) { throw unsupported("hasSpotAnim"); }
	@Override public boolean isDead() { throw unsupported("isDead"); }
	@Override public boolean isInteracting() { throw unsupported("isInteracting"); }
	@Override public int getAnimation() { throw unsupported("getAnimation"); }
	@Override public int getAnimationFrame() { throw unsupported("getAnimationFrame"); }
	@Override public int getAnimationHeightOffset() { throw unsupported("getAnimationHeightOffset"); }
	@Override public int getCurrentOrientation() { throw unsupported("getCurrentOrientation"); }
	@Override public int getFootprintSize() { throw unsupported("getFootprintSize"); }
	@Override public int getGraphic() { throw unsupported("getGraphic"); }
	@Override public int getGraphicHeight() { throw unsupported("getGraphicHeight"); }
	@Override public int getHealthRatio() { throw unsupported("getHealthRatio"); }
	@Override public int getHealthScale() { throw unsupported("getHealthScale"); }
	@Override public int getIdlePoseAnimation() { throw unsupported("getIdlePoseAnimation"); }
	@Override public int getIdleRotateLeft() { throw unsupported("getIdleRotateLeft"); }
	@Override public int getIdleRotateRight() { throw unsupported("getIdleRotateRight"); }
	@Override public int getLogicalHeight() { throw unsupported("getLogicalHeight"); }
	@Override public int getOrientation() { throw unsupported("getOrientation"); }
	@Override public int getOverheadCycle() { throw unsupported("getOverheadCycle"); }
	@Override public int getPoseAnimation() { throw unsupported("getPoseAnimation"); }
	@Override public int getPoseAnimationFrame() { throw unsupported("getPoseAnimationFrame"); }
	@Override public int getRunAnimation() { throw unsupported("getRunAnimation"); }
	@Override public int getSpotAnimFrame() { throw unsupported("getSpotAnimFrame"); }
	@Override public int getWalkAnimation() { throw unsupported("getWalkAnimation"); }
	@Override public int getWalkRotate180() { throw unsupported("getWalkRotate180"); }
	@Override public int getWalkRotateLeft() { throw unsupported("getWalkRotateLeft"); }
	@Override public int getWalkRotateRight() { throw unsupported("getWalkRotateRight"); }
	@Override public java.awt.Polygon getCanvasTilePoly() { throw unsupported("getCanvasTilePoly"); }
	@Override public java.awt.Shape getConvexHull() { throw unsupported("getConvexHull"); }
	@Override public java.lang.String getName() { throw unsupported("getName"); }
	@Override public java.lang.String getOverheadText() { throw unsupported("getOverheadText"); }
	@Override public net.runelite.api.Actor getInteracting() { throw unsupported("getInteracting"); }
	@Override public net.runelite.api.IterableHashTable<net.runelite.api.ActorSpotAnim> getSpotAnims() { throw unsupported("getSpotAnims"); }
	@Override public net.runelite.api.Point getCanvasImageLocation(java.awt.image.BufferedImage a0, int a1) { throw unsupported("getCanvasImageLocation"); }
	@Override public net.runelite.api.Point getCanvasSpriteLocation(net.runelite.api.SpritePixels a0, int a1) { throw unsupported("getCanvasSpriteLocation"); }
	@Override public net.runelite.api.Point getCanvasTextLocation(java.awt.Graphics2D a0, java.lang.String a1, int a2) { throw unsupported("getCanvasTextLocation"); }
	@Override public net.runelite.api.Point getMinimapLocation() { throw unsupported("getMinimapLocation"); }
	@Override public net.runelite.api.WorldView getWorldView() { throw unsupported("getWorldView"); }
	@Override public net.runelite.api.coords.LocalPoint getLocalLocation() { throw unsupported("getLocalLocation"); }
	@Override public net.runelite.api.coords.WorldArea getWorldArea() { throw unsupported("getWorldArea"); }
	@Override public net.runelite.api.coords.WorldPoint getWorldLocation() { throw unsupported("getWorldLocation"); }
	@Override public void clearSpotAnims() { throw unsupported("clearSpotAnims"); }
	@Override public void createSpotAnim(int a0, int a1, int a2, int a3) { throw unsupported("createSpotAnim"); }
	@Override public void removeSpotAnim(int a0) { throw unsupported("removeSpotAnim"); }
	@Override public void setActionFrame(int a0) { throw unsupported("setActionFrame"); }
	@Override public void setAnimation(int a0) { throw unsupported("setAnimation"); }
	@Override public void setAnimationFrame(int a0) { throw unsupported("setAnimationFrame"); }
	@Override public void setDead(boolean a0) { throw unsupported("setDead"); }
	@Override public void setGraphic(int a0) { throw unsupported("setGraphic"); }
	@Override public void setGraphicHeight(int a0) { throw unsupported("setGraphicHeight"); }
	@Override public void setIdlePoseAnimation(int a0) { throw unsupported("setIdlePoseAnimation"); }
	@Override public void setIdleRotateLeft(int a0) { throw unsupported("setIdleRotateLeft"); }
	@Override public void setIdleRotateRight(int a0) { throw unsupported("setIdleRotateRight"); }
	@Override public void setOverheadCycle(int a0) { throw unsupported("setOverheadCycle"); }
	@Override public void setOverheadText(java.lang.String a0) { throw unsupported("setOverheadText"); }
	@Override public void setPoseAnimation(int a0) { throw unsupported("setPoseAnimation"); }
	@Override public void setPoseAnimationFrame(int a0) { throw unsupported("setPoseAnimationFrame"); }
	@Override public void setRunAnimation(int a0) { throw unsupported("setRunAnimation"); }
	@Override public void setSpotAnimFrame(int a0) { throw unsupported("setSpotAnimFrame"); }
	@Override public void setWalkAnimation(int a0) { throw unsupported("setWalkAnimation"); }
	@Override public void setWalkRotate180(int a0) { throw unsupported("setWalkRotate180"); }
	@Override public void setWalkRotateLeft(int a0) { throw unsupported("setWalkRotateLeft"); }
	@Override public void setWalkRotateRight(int a0) { throw unsupported("setWalkRotateRight"); }

	// --- net.runelite.api.Renderable ---
	@Override public int getModelHeight() { throw unsupported("getModelHeight"); }
	@Override public int getRenderMode() { throw unsupported("getRenderMode"); }
	@Override public net.runelite.api.Model getModel() { throw unsupported("getModel"); }
	@Override public void setModelHeight(int a0) { throw unsupported("setModelHeight"); }

	// --- net.runelite.api.CameraFocusableEntity ---
	@Override public net.runelite.api.coords.LocalPoint getCameraFocus() { throw unsupported("getCameraFocus"); }

	// --- net.runelite.api.Node ---
	@Override public long getHash() { throw unsupported("getHash"); }
	@Override public net.runelite.api.Node getNext() { throw unsupported("getNext"); }
	@Override public net.runelite.api.Node getPrevious() { throw unsupported("getPrevious"); }
}
