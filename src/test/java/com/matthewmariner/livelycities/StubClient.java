package com.matthewmariner.livelycities;

/**
 * Every abstract method of {@link net.runelite.api.Client}, throwing.
 *
 * <p>Mechanically generated from {@code javap net.runelite.api.Client} against
 * the 1.12.36 API jar and then checked in — no reflection, no mocking
 * framework, nothing generated at runtime. It exists so {@link FakeClient} can implement
 * the 8 methods the render core actually calls without hand-typing the other
 * 313: a method this plugin has never used fails loudly the first time a test
 * reaches it, instead of quietly returning {@code null}.
 *
 * <p>One method per line on purpose. This is a lookup table, not code to read.
 */
class StubClient implements net.runelite.api.Client
{
	static UnsupportedOperationException unsupported(String method)
	{
		return new UnsupportedOperationException(
			"StubClient does not implement Client." + method + "(..) — the render core has never needed it");
	}

	// --- net.runelite.api.Client ---
	@Override public net.runelite.api.MessageNode addChatMessage(net.runelite.api.ChatMessageType a0, java.lang.String a1, java.lang.String a2, java.lang.String a3) { throw unsupported("addChatMessage"); }
	@Override public net.runelite.api.MessageNode addChatMessage(net.runelite.api.ChatMessageType a0, java.lang.String a1, java.lang.String a2, java.lang.String a3, boolean a4) { throw unsupported("addChatMessage"); }
	@Override public net.runelite.api.Model applyTransformations(net.runelite.api.Model a0, net.runelite.api.Animation a1, int a2, net.runelite.api.Animation a3, int a4) { throw unsupported("applyTransformations"); }
	@Override public void changeMemoryMode(boolean a0) { throw unsupported("changeMemoryMode"); }
	@Override public void changeWorld(net.runelite.api.World a0) { throw unsupported("changeWorld"); }
	@Override public void checkClickbox(net.runelite.api.Projection a0, net.runelite.api.Model a1, int a2, int a3, int a4, int a5, long a6) { throw unsupported("checkClickbox"); }
	@Override public void clearHintArrow() { throw unsupported("clearHintArrow"); }
	@Override public void closeInterface(net.runelite.api.WidgetNode a0, boolean a1) { throw unsupported("closeInterface"); }
	@Override public net.runelite.api.IndexedSprite createIndexedSprite() { throw unsupported("createIndexedSprite"); }
	@Override public net.runelite.api.SpritePixels createItemSprite(int a0, int a1, int a2, int a3, int a4, boolean a5, int a6) { throw unsupported("createItemSprite"); }
	@Override public net.runelite.api.MenuEntry createMenuEntry(int a0) { throw unsupported("createMenuEntry"); }
	@Override public net.runelite.api.Projectile createProjectile(int a0, int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8, int a9, net.runelite.api.Actor a10, int a11, int a12) { throw unsupported("createProjectile"); }
	@Override public net.runelite.api.Projectile createProjectile(int a0, net.runelite.api.coords.WorldPoint a1, int a2, net.runelite.api.Actor a3, net.runelite.api.coords.WorldPoint a4, int a5, net.runelite.api.Actor a6, int a7, int a8, int a9, int a10) { throw unsupported("createProjectile"); }
	@Override public net.runelite.api.RuneLiteObject createRuneLiteObject() { throw unsupported("createRuneLiteObject"); }
	@Override public net.runelite.api.SceneTilePaint createSceneTilePaint(int a0, int a1, int a2, int a3, int a4, int a5, boolean a6) { throw unsupported("createSceneTilePaint"); }
	@Override public net.runelite.api.ScriptEventBuilder createScriptEventBuilder(java.lang.Object... a0) { throw unsupported("createScriptEventBuilder"); }
	@Override public net.runelite.api.SpritePixels createSpritePixels(int[] a0, int a1, int a2) { throw unsupported("createSpritePixels"); }
	@Override public net.runelite.api.World createWorld() { throw unsupported("createWorld"); }
	@Override public void draw2010Menu(int a0) { throw unsupported("draw2010Menu"); }
	@Override public net.runelite.api.SpritePixels drawInstanceMap(int a0) { throw unsupported("drawInstanceMap"); }
	@Override public void drawOriginalMenu(int a0) { throw unsupported("drawOriginalMenu"); }
	@Override public net.runelite.api.WorldView findWorldViewFromWorldPoint(net.runelite.api.coords.WorldPoint a0) { throw unsupported("findWorldViewFromWorldPoint"); }
	@Override public int get3dZoom() { throw unsupported("get3dZoom"); }
	@Override public net.runelite.api.vars.AccountType getAccountType() { throw unsupported("getAccountType"); }
	@Override public java.util.List<net.runelite.api.MidiRequest> getActiveMidiRequests() { throw unsupported("getActiveMidiRequests"); }
	@Override public net.runelite.api.Deque<net.runelite.api.AmbientSoundEffect> getAmbientSoundEffects() { throw unsupported("getAmbientSoundEffects"); }
	@Override public net.runelite.api.NodeCache getAnimationCache() { throw unsupported("getAnimationCache"); }
	@Override public java.util.function.IntPredicate getAnimationInterpolationFilter() { throw unsupported("getAnimationInterpolationFilter"); }
	@Override public int[] getArray(int a0) { throw unsupported("getArray"); }
	@Override public int getArraySizes(int a0) { throw unsupported("getArraySizes"); }
	@Override public int getBoostedSkillLevel(net.runelite.api.Skill a0) { throw unsupported("getBoostedSkillLevel"); }
	@Override public int[] getBoostedSkillLevels() { throw unsupported("getBoostedSkillLevels"); }
	@Override public net.runelite.api.BufferProvider getBufferProvider() { throw unsupported("getBufferProvider"); }
	@Override public java.lang.String getBuildID() { throw unsupported("getBuildID"); }
	@Override public net.runelite.api.hooks.Callbacks getCallbacks() { throw unsupported("getCallbacks"); }
	@Override public float getCameraFocalPointX() { throw unsupported("getCameraFocalPointX"); }
	@Override public float getCameraFocalPointY() { throw unsupported("getCameraFocalPointY"); }
	@Override public float getCameraFocalPointZ() { throw unsupported("getCameraFocalPointZ"); }
	@Override public net.runelite.api.CameraFocusableEntity getCameraFocusEntity() { throw unsupported("getCameraFocusEntity"); }
	@Override public float getCameraFpPitch() { throw unsupported("getCameraFpPitch"); }
	@Override public float getCameraFpX() { throw unsupported("getCameraFpX"); }
	@Override public float getCameraFpY() { throw unsupported("getCameraFpY"); }
	@Override public float getCameraFpYaw() { throw unsupported("getCameraFpYaw"); }
	@Override public float getCameraFpZ() { throw unsupported("getCameraFpZ"); }
	@Override public int getCameraMode() { throw unsupported("getCameraMode"); }
	@Override public int getCameraPitch() { throw unsupported("getCameraPitch"); }
	@Override public int getCameraPitchTarget() { throw unsupported("getCameraPitchTarget"); }
	@Override public int getCameraX() { throw unsupported("getCameraX"); }
	@Override public int getCameraY() { throw unsupported("getCameraY"); }
	@Override public int getCameraYaw() { throw unsupported("getCameraYaw"); }
	@Override public int getCameraYawTarget() { throw unsupported("getCameraYawTarget"); }
	@Override public int getCameraZ() { throw unsupported("getCameraZ"); }
	@Override public java.awt.Canvas getCanvas() { throw unsupported("getCanvas"); }
	@Override public int getCanvasHeight() { throw unsupported("getCanvasHeight"); }
	@Override public int getCanvasWidth() { throw unsupported("getCanvasWidth"); }
	@Override public int getCenterX() { throw unsupported("getCenterX"); }
	@Override public int getCenterY() { throw unsupported("getCenterY"); }
	@Override public net.runelite.api.clan.ClanChannel getClanChannel() { throw unsupported("getClanChannel"); }
	@Override public net.runelite.api.clan.ClanChannel getClanChannel(int a0) { throw unsupported("getClanChannel"); }
	@Override public net.runelite.api.clan.ClanSettings getClanSettings() { throw unsupported("getClanSettings"); }
	@Override public net.runelite.api.clan.ClanSettings getClanSettings(int a0) { throw unsupported("getClanSettings"); }
	@Override public net.runelite.api.HashTable<net.runelite.api.WidgetNode> getComponentTable() { throw unsupported("getComponentTable"); }
	@Override public net.runelite.api.SpritePixels[] getCrossSprites() { throw unsupported("getCrossSprites"); }
	@Override public long[] getCrossWorldMessageIds() { throw unsupported("getCrossWorldMessageIds"); }
	@Override public int getCrossWorldMessageIdsIndex() { throw unsupported("getCrossWorldMessageIdsIndex"); }
	@Override public int getCurrentLoginField() { throw unsupported("getCurrentLoginField"); }
	@Override public net.runelite.api.dbtable.DBRowConfig getDBRowConfig(int a0) { throw unsupported("getDBRowConfig"); }
	@Override public java.util.List<java.lang.Integer> getDBRowsByValue(int a0, int a1, int a2, java.lang.Object a3) { throw unsupported("getDBRowsByValue"); }
	@Override public java.lang.Object[] getDBTableField(int a0, int a1, int a2) { throw unsupported("getDBTableField"); }
	@Override public java.util.List<java.lang.Integer> getDBTableRows(int a0) { throw unsupported("getDBTableRows"); }
	@Override public int getDragTime() { throw unsupported("getDragTime"); }
	@Override public net.runelite.api.widgets.Widget getDraggedOnWidget() { throw unsupported("getDraggedOnWidget"); }
	@Override public net.runelite.api.widgets.Widget getDraggedWidget() { throw unsupported("getDraggedWidget"); }
	@Override public int getDraw2DMask() { throw unsupported("getDraw2DMask"); }
	@Override public net.runelite.api.hooks.DrawCallbacks getDrawCallbacks() { throw unsupported("getDrawCallbacks"); }
	@Override public int getEnergy() { throw unsupported("getEnergy"); }
	@Override public net.runelite.api.EnumComposition getEnum(int a0) { throw unsupported("getEnum"); }
	@Override public int getEnvironment() { throw unsupported("getEnvironment"); }
	@Override public int getExpandedMapLoading() { throw unsupported("getExpandedMapLoading"); }
	@Override public int getFPS() { throw unsupported("getFPS"); }
	@Override public net.runelite.api.widgets.Widget getFocusedInputFieldWidget() { throw unsupported("getFocusedInputFieldWidget"); }
	@Override public net.runelite.api.NPC getFollower() { throw unsupported("getFollower"); }
	@Override public net.runelite.api.FriendContainer getFriendContainer() { throw unsupported("getFriendContainer"); }
	@Override public net.runelite.api.FriendsChatManager getFriendsChatManager() { throw unsupported("getFriendsChatManager"); }
	@Override public int getGameCycle() { throw unsupported("getGameCycle"); }
	@Override public net.runelite.api.GameState getGameState() { throw unsupported("getGameState"); }
	@Override public net.runelite.api.GrandExchangeOffer[] getGrandExchangeOffers() { throw unsupported("getGrandExchangeOffers"); }
	@Override public net.runelite.api.clan.ClanChannel getGuestClanChannel() { throw unsupported("getGuestClanChannel"); }
	@Override public net.runelite.api.clan.ClanSettings getGuestClanSettings() { throw unsupported("getGuestClanSettings"); }
	@Override public net.runelite.api.NPC getHintArrowNpc() { throw unsupported("getHintArrowNpc"); }
	@Override public net.runelite.api.Player getHintArrowPlayer() { throw unsupported("getHintArrowPlayer"); }
	@Override public net.runelite.api.coords.WorldPoint getHintArrowPoint() { throw unsupported("getHintArrowPoint"); }
	@Override public int getHintArrowType() { throw unsupported("getHintArrowType"); }
	@Override public int getIdleTimeout() { throw unsupported("getIdleTimeout"); }
	@Override public net.runelite.api.NameableContainer<net.runelite.api.Ignore> getIgnoreContainer() { throw unsupported("getIgnoreContainer"); }
	@Override public net.runelite.api.IndexDataBase getIndex(int a0) { throw unsupported("getIndex"); }
	@Override public net.runelite.api.IndexDataBase getIndexConfig() { throw unsupported("getIndexConfig"); }
	@Override public net.runelite.api.IndexDataBase getIndexScripts() { throw unsupported("getIndexScripts"); }
	@Override public net.runelite.api.IndexDataBase getIndexSprites() { throw unsupported("getIndexSprites"); }
	@Override public int[][][] getInstanceTemplateChunks() { throw unsupported("getInstanceTemplateChunks"); }
	@Override public int[] getIntStack() { throw unsupported("getIntStack"); }
	@Override public int getIntStackSize() { throw unsupported("getIntStackSize"); }
	@Override public net.runelite.api.NodeCache getItemCompositionCache() { throw unsupported("getItemCompositionCache"); }
	@Override public net.runelite.api.ItemContainer getItemContainer(int a0) { throw unsupported("getItemContainer"); }
	@Override public net.runelite.api.ItemContainer getItemContainer(net.runelite.api.InventoryID a0) { throw unsupported("getItemContainer"); }
	@Override public net.runelite.api.HashTable<net.runelite.api.ItemContainer> getItemContainers() { throw unsupported("getItemContainers"); }
	@Override public int getItemCount() { throw unsupported("getItemCount"); }
	@Override public net.runelite.api.ItemComposition getItemDefinition(int a0) { throw unsupported("getItemDefinition"); }
	@Override public net.runelite.api.NodeCache getItemModelCache() { throw unsupported("getItemModelCache"); }
	@Override public net.runelite.api.NodeCache getItemSpriteCache() { throw unsupported("getItemSpriteCache"); }
	@Override public int getKeyboardIdleTicks() { throw unsupported("getKeyboardIdleTicks"); }
	@Override public java.lang.String getLauncherDisplayName() { throw unsupported("getLauncherDisplayName"); }
	@Override public net.runelite.api.coords.LocalPoint getLocalDestinationLocation() { throw unsupported("getLocalDestinationLocation"); }
	@Override public net.runelite.api.Player getLocalPlayer() { throw unsupported("getLocalPlayer"); }
	@Override public int getLoginIndex() { throw unsupported("getLoginIndex"); }
	@Override public net.runelite.api.SpritePixels[] getMapDots() { throw unsupported("getMapDots"); }
	@Override public net.runelite.api.worldmap.MapElementConfig getMapElementConfig(int a0) { throw unsupported("getMapElementConfig"); }
	@Override public net.runelite.api.SpritePixels[] getMapIcons() { throw unsupported("getMapIcons"); }
	@Override public int[] getMapRegions() { throw unsupported("getMapRegions"); }
	@Override public net.runelite.api.IndexedSprite[] getMapScene() { throw unsupported("getMapScene"); }
	@Override public net.runelite.api.Menu getMenu() { throw unsupported("getMenu"); }
	@Override public net.runelite.api.MenuEntry[] getMenuEntries() { throw unsupported("getMenuEntries"); }
	@Override public int getMenuHeight() { throw unsupported("getMenuHeight"); }
	@Override public int getMenuScroll() { throw unsupported("getMenuScroll"); }
	@Override public int getMenuWidth() { throw unsupported("getMenuWidth"); }
	@Override public int getMenuX() { throw unsupported("getMenuX"); }
	@Override public int getMenuY() { throw unsupported("getMenuY"); }
	@Override public net.runelite.api.IterableHashTable<net.runelite.api.MessageNode> getMessages() { throw unsupported("getMessages"); }
	@Override public double getMinimapZoom() { throw unsupported("getMinimapZoom"); }
	@Override public net.runelite.api.IndexedSprite[] getModIcons() { throw unsupported("getModIcons"); }
	@Override public net.runelite.api.Point getMouseCanvasPosition() { throw unsupported("getMouseCanvasPosition"); }
	@Override public int getMouseCurrentButton() { throw unsupported("getMouseCurrentButton"); }
	@Override public int getMouseIdleTicks() { throw unsupported("getMouseIdleTicks"); }
	@Override public long getMouseLastPressedMillis() { throw unsupported("getMouseLastPressedMillis"); }
	@Override public int getMusicVolume() { throw unsupported("getMusicVolume"); }
	@Override public net.runelite.api.NPCComposition getNpcDefinition(int a0) { throw unsupported("getNpcDefinition"); }
	@Override public net.runelite.api.NodeCache getObjectCompositionCache() { throw unsupported("getObjectCompositionCache"); }
	@Override public net.runelite.api.ObjectComposition getObjectDefinition(int a0) { throw unsupported("getObjectDefinition"); }
	@Override public java.lang.Object[] getObjectStack() { throw unsupported("getObjectStack"); }
	@Override public int getObjectStackSize() { throw unsupported("getObjectStackSize"); }
	@Override public int getOculusOrbFocalPointX() { throw unsupported("getOculusOrbFocalPointX"); }
	@Override public int getOculusOrbFocalPointY() { throw unsupported("getOculusOrbFocalPointY"); }
	@Override public int getOculusOrbState() { throw unsupported("getOculusOrbState"); }
	@Override public long getOverallExperience() { throw unsupported("getOverallExperience"); }
	@Override public int[] getPlayerMenuTypes() { throw unsupported("getPlayerMenuTypes"); }
	@Override public java.lang.String[] getPlayerOptions() { throw unsupported("getPlayerOptions"); }
	@Override public boolean[] getPlayerOptionsPriorities() { throw unsupported("getPlayerOptionsPriorities"); }
	@Override public net.runelite.api.Preferences getPreferences() { throw unsupported("getPreferences"); }
	@Override public net.runelite.api.Deque<net.runelite.api.Projectile> getProjectiles() { throw unsupported("getProjectiles"); }
	@Override public net.runelite.api.Rasterizer getRasterizer() { throw unsupported("getRasterizer"); }
	@Override public int getRasterizer3D_clipMidX2() { throw unsupported("getRasterizer3D_clipMidX2"); }
	@Override public int getRasterizer3D_clipMidY2() { throw unsupported("getRasterizer3D_clipMidY2"); }
	@Override public int getRasterizer3D_clipNegativeMidX() { throw unsupported("getRasterizer3D_clipNegativeMidX"); }
	@Override public int getRasterizer3D_clipNegativeMidY() { throw unsupported("getRasterizer3D_clipNegativeMidY"); }
	@Override public java.awt.Dimension getRealDimensions() { throw unsupported("getRealDimensions"); }
	@Override public int getRealSkillLevel(net.runelite.api.Skill a0) { throw unsupported("getRealSkillLevel"); }
	@Override public int[] getRealSkillLevels() { throw unsupported("getRealSkillLevels"); }
	@Override public net.runelite.api.RenderOverview getRenderOverview() { throw unsupported("getRenderOverview"); }
	@Override public int getRevision() { throw unsupported("getRevision"); }
	@Override public int getScale() { throw unsupported("getScale"); }
	@Override public net.runelite.api.widgets.Widget getScriptActiveWidget() { throw unsupported("getScriptActiveWidget"); }
	@Override public net.runelite.api.widgets.Widget getScriptDotWidget() { throw unsupported("getScriptDotWidget"); }
	@Override public net.runelite.api.widgets.Widget getSelectedWidget() { throw unsupported("getSelectedWidget"); }
	@Override public int getServerVarbitValue(int a0) { throw unsupported("getServerVarbitValue"); }
	@Override public int getServerVarpValue(int a0) { throw unsupported("getServerVarpValue"); }
	@Override public int[] getServerVarps() { throw unsupported("getServerVarps"); }
	@Override public int getSkillExperience(net.runelite.api.Skill a0) { throw unsupported("getSkillExperience"); }
	@Override public int[] getSkillExperiences() { throw unsupported("getSkillExperiences"); }
	@Override public int getSkyboxColor() { throw unsupported("getSkyboxColor"); }
	@Override public java.io.FileDescriptor getSocketFD() { throw unsupported("getSocketFD"); }
	@Override public net.runelite.api.SpritePixels[] getSprites(net.runelite.api.IndexDataBase a0, int a1, int a2) { throw unsupported("getSprites"); }
	@Override public java.awt.Dimension getStretchedDimensions() { throw unsupported("getStretchedDimensions"); }
	@Override public net.runelite.api.StructComposition getStructComposition(int a0) { throw unsupported("getStructComposition"); }
	@Override public net.runelite.api.NodeCache getStructCompositionCache() { throw unsupported("getStructCompositionCache"); }
	@Override public net.runelite.api.TextureProvider getTextureProvider() { throw unsupported("getTextureProvider"); }
	@Override public int getTickCount() { throw unsupported("getTickCount"); }
	@Override public int getTopLevelInterfaceId() { throw unsupported("getTopLevelInterfaceId"); }
	@Override public net.runelite.api.WorldView getTopLevelWorldView() { throw unsupported("getTopLevelWorldView"); }
	@Override public int getTotalLevel() { throw unsupported("getTotalLevel"); }
	@Override public java.lang.String getUsername() { throw unsupported("getUsername"); }
	@Override public int getVar(int a0) { throw unsupported("getVar"); }
	@Override public net.runelite.api.VarbitComposition getVarbit(int a0) { throw unsupported("getVarbit"); }
	@Override public int getVarbitValue(int a0) { throw unsupported("getVarbitValue"); }
	@Override public int getVarbitValue(int[] a0, int a1) { throw unsupported("getVarbitValue"); }
	@Override public int getVarcIntValue(int a0) { throw unsupported("getVarcIntValue"); }
	@Override public java.lang.String getVarcStrValue(int a0) { throw unsupported("getVarcStrValue"); }
	@Override public int getVarpValue(int a0) { throw unsupported("getVarpValue"); }
	@Override public int[] getVarps() { throw unsupported("getVarps"); }
	@Override public int getViewportHeight() { throw unsupported("getViewportHeight"); }
	@Override public int getViewportWidth() { throw unsupported("getViewportWidth"); }
	@Override public int getViewportXOffset() { throw unsupported("getViewportXOffset"); }
	@Override public int getViewportYOffset() { throw unsupported("getViewportYOffset"); }
	@Override public int getWeight() { throw unsupported("getWeight"); }
	@Override public net.runelite.api.widgets.Widget getWidget(int a0) { throw unsupported("getWidget"); }
	@Override public net.runelite.api.widgets.Widget getWidget(int a0, int a1) { throw unsupported("getWidget"); }
	@Override public net.runelite.api.widgets.Widget getWidget(net.runelite.api.widgets.WidgetInfo a0) { throw unsupported("getWidget"); }
	@Override public net.runelite.api.widgets.WidgetConfigNode getWidgetConfig(net.runelite.api.widgets.Widget a0) { throw unsupported("getWidgetConfig"); }
	@Override public net.runelite.api.HashTable<net.runelite.api.widgets.WidgetConfigNode> getWidgetFlags() { throw unsupported("getWidgetFlags"); }
	@Override public net.runelite.api.widgets.Widget[] getWidgetRoots() { throw unsupported("getWidgetRoots"); }
	@Override public net.runelite.api.NodeCache getWidgetSpriteCache() { throw unsupported("getWidgetSpriteCache"); }
	@Override public int getWorld() { throw unsupported("getWorld"); }
	@Override public java.lang.String getWorldHost() { throw unsupported("getWorldHost"); }
	@Override public net.runelite.api.World[] getWorldList() { throw unsupported("getWorldList"); }
	@Override public net.runelite.api.worldmap.WorldMap getWorldMap() { throw unsupported("getWorldMap"); }
	@Override public java.util.EnumSet<net.runelite.api.WorldType> getWorldType() { throw unsupported("getWorldType"); }
	@Override public net.runelite.api.WorldView getWorldView(int a0) { throw unsupported("getWorldView"); }
	@Override public boolean hasHintArrow() { throw unsupported("hasHintArrow"); }
	@Override public void hopToWorld(net.runelite.api.World a0) { throw unsupported("hopToWorld"); }
	@Override public void invalidateStretching(boolean a0) { throw unsupported("invalidateStretching"); }
	@Override public boolean isCameraShakeDisabled() { throw unsupported("isCameraShakeDisabled"); }
	@Override public boolean isDraggingWidget() { throw unsupported("isDraggingWidget"); }
	@Override public boolean isFriended(java.lang.String a0, boolean a1) { throw unsupported("isFriended"); }
	@Override public boolean isGpu() { throw unsupported("isGpu"); }
	@Override public boolean isInInstancedRegion() { throw unsupported("isInInstancedRegion"); }
	@Override public boolean isKeyPressed(int a0) { throw unsupported("isKeyPressed"); }
	@Override public boolean isMenuOpen() { throw unsupported("isMenuOpen"); }
	@Override public boolean isMenuScrollable() { throw unsupported("isMenuScrollable"); }
	@Override public boolean isMinimapZoom() { throw unsupported("isMinimapZoom"); }
	@Override public boolean isMouseoverTextEnabled() { throw unsupported("isMouseoverTextEnabled"); }
	@Override public boolean isPrayerActive(net.runelite.api.Prayer a0) { throw unsupported("isPrayerActive"); }
	@Override public boolean isResized() { throw unsupported("isResized"); }
	@Override public boolean isRuneLiteObjectRegistered(net.runelite.api.RuneLiteObjectController a0) { throw unsupported("isRuneLiteObjectRegistered"); }
	@Override public boolean isStretchedEnabled() { throw unsupported("isStretchedEnabled"); }
	@Override public boolean isStretchedFast() { throw unsupported("isStretchedFast"); }
	@Override public boolean isWidgetSelected() { throw unsupported("isWidgetSelected"); }
	@Override public java.util.Map<java.lang.Integer, java.lang.Object> getVarcMap() { throw unsupported("getVarcMap"); }
	@Override public net.runelite.api.Animation loadAnimation(int a0) { throw unsupported("loadAnimation"); }
	@Override public net.runelite.api.Model loadModel(int a0) { throw unsupported("loadModel"); }
	@Override public net.runelite.api.Model loadModel(int a0, short[] a1, short[] a2) { throw unsupported("loadModel"); }
	@Override public net.runelite.api.ModelData loadModelData(int a0) { throw unsupported("loadModelData"); }
	@Override public void menuAction(int a0, int a1, net.runelite.api.MenuAction a2, int a3, int a4, java.lang.String a5, java.lang.String a6) { throw unsupported("menuAction"); }
	@Override public net.runelite.api.Model mergeModels(net.runelite.api.Model... a0) { throw unsupported("mergeModels"); }
	@Override public net.runelite.api.ModelData mergeModels(net.runelite.api.ModelData... a0) { throw unsupported("mergeModels"); }
	@Override public net.runelite.api.ModelData mergeModels(net.runelite.api.ModelData[] a0, int a1) { throw unsupported("mergeModels"); }
	@Override public net.runelite.api.Model mergeModels(net.runelite.api.Model[] a0, int a1) { throw unsupported("mergeModels"); }
	@Override public java.util.Map<java.lang.Integer, net.runelite.api.ChatLineBuffer> getChatLineMap() { throw unsupported("getChatLineMap"); }
	@Override public java.util.Map<java.lang.Integer, net.runelite.api.SpritePixels> getSpriteOverrides() { throw unsupported("getSpriteOverrides"); }
	@Override public java.util.Map<java.lang.Integer, net.runelite.api.SpritePixels> getWidgetSpriteOverrides() { throw unsupported("getWidgetSpriteOverrides"); }
	@Override public net.runelite.api.WidgetNode openInterface(int a0, int a1, int a2) { throw unsupported("openInterface"); }
	@Override public void openWorldHopper() { throw unsupported("openWorldHopper"); }
	@Override public void playSoundEffect(int a0) { throw unsupported("playSoundEffect"); }
	@Override public void playSoundEffect(int a0, int a1) { throw unsupported("playSoundEffect"); }
	@Override public void playSoundEffect(int a0, int a1, int a2, int a3) { throw unsupported("playSoundEffect"); }
	@Override public void playSoundEffect(int a0, int a1, int a2, int a3, int a4) { throw unsupported("playSoundEffect"); }
	@Override public void queueChangedSkill(net.runelite.api.Skill a0) { throw unsupported("queueChangedSkill"); }
	@Override public void queueChangedVarp(int a0) { throw unsupported("queueChangedVarp"); }
	@Override public void refreshChat() { throw unsupported("refreshChat"); }
	@Override public void registerRuneLiteObject(net.runelite.api.RuneLiteObjectController a0) { throw unsupported("registerRuneLiteObject"); }
	@Override public void removeRuneLiteObject(net.runelite.api.RuneLiteObjectController a0) { throw unsupported("removeRuneLiteObject"); }
	@Override public void resetHealthBarCaches() { throw unsupported("resetHealthBarCaches"); }
	@Override public void runScript(java.lang.Object... a0) { throw unsupported("runScript"); }
	@Override public void setAllWidgetsAreOpTargetable(boolean a0) { throw unsupported("setAllWidgetsAreOpTargetable"); }
	@Override public void setAnimationInterpolationFilter(java.util.function.IntPredicate a0) { throw unsupported("setAnimationInterpolationFilter"); }
	@Override public void setCameraFocalPointX(float a0) { throw unsupported("setCameraFocalPointX"); }
	@Override public void setCameraFocalPointY(float a0) { throw unsupported("setCameraFocalPointY"); }
	@Override public void setCameraFocalPointZ(float a0) { throw unsupported("setCameraFocalPointZ"); }
	@Override public void setCameraMode(int a0) { throw unsupported("setCameraMode"); }
	@Override public void setCameraMouseButtonMask(int a0) { throw unsupported("setCameraMouseButtonMask"); }
	@Override public void setCameraPitchRelaxerEnabled(boolean a0) { throw unsupported("setCameraPitchRelaxerEnabled"); }
	@Override public void setCameraPitchTarget(int a0) { throw unsupported("setCameraPitchTarget"); }
	@Override public void setCameraShakeDisabled(boolean a0) { throw unsupported("setCameraShakeDisabled"); }
	@Override public void setCameraSpeed(float a0) { throw unsupported("setCameraSpeed"); }
	@Override public void setCameraYawTarget(int a0) { throw unsupported("setCameraYawTarget"); }
	@Override public void setCompass(net.runelite.api.SpritePixels a0) { throw unsupported("setCompass"); }
	@Override public void setDraggedOnWidget(net.runelite.api.widgets.Widget a0) { throw unsupported("setDraggedOnWidget"); }
	@Override public void setDraw2DMask(int a0) { throw unsupported("setDraw2DMask"); }
	@Override public void setDrawCallbacks(net.runelite.api.hooks.DrawCallbacks a0) { throw unsupported("setDrawCallbacks"); }
	@Override public void setExpandedMapLoading(int a0) { throw unsupported("setExpandedMapLoading"); }
	@Override public void setFreeCameraSpeed(int a0) { throw unsupported("setFreeCameraSpeed"); }
	@Override public void setGameState(net.runelite.api.GameState a0) { throw unsupported("setGameState"); }
	@Override public void setGeSearchResultCount(int a0) { throw unsupported("setGeSearchResultCount"); }
	@Override public void setGeSearchResultIds(short[] a0) { throw unsupported("setGeSearchResultIds"); }
	@Override public void setGeSearchResultIndex(int a0) { throw unsupported("setGeSearchResultIndex"); }
	@Override public void setGpuFlags(int a0) { throw unsupported("setGpuFlags"); }
	@Override public void setHintArrow(net.runelite.api.NPC a0) { throw unsupported("setHintArrow"); }
	@Override public void setHintArrow(net.runelite.api.Player a0) { throw unsupported("setHintArrow"); }
	@Override public void setHintArrow(net.runelite.api.coords.LocalPoint a0) { throw unsupported("setHintArrow"); }
	@Override public void setHintArrow(net.runelite.api.coords.WorldPoint a0) { throw unsupported("setHintArrow"); }
	@Override public void setIdleTimeout(int a0) { throw unsupported("setIdleTimeout"); }
	@Override public void setIntStackSize(int a0) { throw unsupported("setIntStackSize"); }
	@Override public void setInventoryDragDelay(int a0) { throw unsupported("setInventoryDragDelay"); }
	@Override public void setInvertPitch(boolean a0) { throw unsupported("setInvertPitch"); }
	@Override public void setInvertYaw(boolean a0) { throw unsupported("setInvertYaw"); }
	@Override public void setLoginScreen(net.runelite.api.SpritePixels a0) { throw unsupported("setLoginScreen"); }
	@Override public void setMenuEntries(net.runelite.api.MenuEntry[] a0) { throw unsupported("setMenuEntries"); }
	@Override public void setMenuScroll(int a0) { throw unsupported("setMenuScroll"); }
	@Override public void setMinimapTileDrawer(net.runelite.api.TileFunction a0) { throw unsupported("setMinimapTileDrawer"); }
	@Override public void setMinimapZoom(boolean a0) { throw unsupported("setMinimapZoom"); }
	@Override public void setMinimapZoom(double a0) { throw unsupported("setMinimapZoom"); }
	@Override public void setModIcons(net.runelite.api.IndexedSprite[] a0) { throw unsupported("setModIcons"); }
	@Override public void setMouseoverTextEnabled(boolean a0) { throw unsupported("setMouseoverTextEnabled"); }
	@Override public void setMusicVolume(int a0) { throw unsupported("setMusicVolume"); }
	@Override public void setObjectStackSize(int a0) { throw unsupported("setObjectStackSize"); }
	@Override public void setOculusOrbNormalSpeed(int a0) { throw unsupported("setOculusOrbNormalSpeed"); }
	@Override public void setOculusOrbState(int a0) { throw unsupported("setOculusOrbState"); }
	@Override public void setOtp(java.lang.String a0) { throw unsupported("setOtp"); }
	@Override public void setPassword(java.lang.String a0) { throw unsupported("setPassword"); }
	@Override public void setScalingFactor(int a0) { throw unsupported("setScalingFactor"); }
	@Override public void setShouldRenderLoginScreenFire(boolean a0) { throw unsupported("setShouldRenderLoginScreenFire"); }
	@Override public void setSkyboxColor(int a0) { throw unsupported("setSkyboxColor"); }
	@Override public void setStretchedEnabled(boolean a0) { throw unsupported("setStretchedEnabled"); }
	@Override public void setStretchedFast(boolean a0) { throw unsupported("setStretchedFast"); }
	@Override public void setStretchedIntegerScaling(boolean a0) { throw unsupported("setStretchedIntegerScaling"); }
	@Override public void setStretchedKeepAspectRatio(boolean a0) { throw unsupported("setStretchedKeepAspectRatio"); }
	@Override public void setTickCount(int a0) { throw unsupported("setTickCount"); }
	@Override public void setUnlockedFps(boolean a0) { throw unsupported("setUnlockedFps"); }
	@Override public void setUnlockedFpsTarget(int a0) { throw unsupported("setUnlockedFpsTarget"); }
	@Override public void setUsername(java.lang.String a0) { throw unsupported("setUsername"); }
	@Override public void setVarbit(int a0, int a1) { throw unsupported("setVarbit"); }
	@Override public void setVarbitValue(int[] a0, int a1, int a2) { throw unsupported("setVarbitValue"); }
	@Override public void setVarcIntValue(int a0, int a1) { throw unsupported("setVarcIntValue"); }
	@Override public void setVarcStrValue(int a0, java.lang.String a1) { throw unsupported("setVarcStrValue"); }
	@Override public void setWidgetSelected(boolean a0) { throw unsupported("setWidgetSelected"); }
	@Override public void stopNow() { throw unsupported("stopNow"); }

	// --- net.runelite.api.GameEngine ---
	@Override public java.lang.Thread getClientThread() { throw unsupported("getClientThread"); }
	@Override public void initialize() { throw unsupported("initialize"); }
	@Override public boolean isClientThread() { throw unsupported("isClientThread"); }
	@Override public void resizeCanvas() { throw unsupported("resizeCanvas"); }
	@Override public void setConfiguration(net.runelite.api.ClientConfiguration a0) { throw unsupported("setConfiguration"); }
	@Override public void unblockStartup() { throw unsupported("unblockStartup"); }

	// --- com.jagex.oldscape.pub.OAuthApi ---
	@Override public long getAccountHash() { throw unsupported("getAccountHash"); }
}
