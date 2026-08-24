package com.matthewmariner.livelycities;

/**
 * Every abstract method of {@link net.runelite.api.widgets.Widget}, throwing.
 *
 * <p>Mechanically generated from {@code javap net.runelite.api.widgets.Widget}
 * against the 1.12.36 API jar and then checked in — the same treatment, for the
 * same reasons, as {@link StubClient}: no reflection, no mocking framework,
 * nothing generated at runtime. {@link FakeWidget} implements the two methods
 * {@link CitizenMenu}'s minimap guard actually reads and inherits the other 158
 * of the 160 declared here, so a guard that started asking a widget a question
 * nobody has thought about fails loudly instead of quietly reading a zero.
 *
 * <p>One method per line on purpose. This is a lookup table, not code to read.
 */
class StubWidget implements net.runelite.api.widgets.Widget
{
	static UnsupportedOperationException unsupported(String method)
	{
		return new UnsupportedOperationException(
			"StubWidget does not implement Widget." + method + "(..) — nothing in this plugin has needed it");
	}

	// --- net.runelite.api.widgets.Widget ---
	@Override public int getId() { throw unsupported("getId"); }
	@Override public int getType() { throw unsupported("getType"); }
	@Override public void setType(int a0) { throw unsupported("setType"); }
	@Override public int getContentType() { throw unsupported("getContentType"); }
	@Override public net.runelite.api.widgets.Widget setContentType(int a0) { throw unsupported("setContentType"); }
	@Override public int getClickMask() { throw unsupported("getClickMask"); }
	@Override public net.runelite.api.widgets.Widget setClickMask(int a0) { throw unsupported("setClickMask"); }
	@Override public net.runelite.api.widgets.Widget getParent() { throw unsupported("getParent"); }
	@Override public int getParentId() { throw unsupported("getParentId"); }
	@Override public net.runelite.api.widgets.Widget getChild(int a0) { throw unsupported("getChild"); }
	@Override public net.runelite.api.widgets.Widget[] getChildren() { throw unsupported("getChildren"); }
	@Override public void setChildren(net.runelite.api.widgets.Widget[] a0) { throw unsupported("setChildren"); }
	@Override public net.runelite.api.widgets.Widget[] getDynamicChildren() { throw unsupported("getDynamicChildren"); }
	@Override public net.runelite.api.widgets.Widget[] getStaticChildren() { throw unsupported("getStaticChildren"); }
	@Override public net.runelite.api.widgets.Widget[] getNestedChildren() { throw unsupported("getNestedChildren"); }
	@Override public int getRelativeX() { throw unsupported("getRelativeX"); }
	@Override public void setRelativeX(int a0) { throw unsupported("setRelativeX"); }
	@Override public int getRelativeY() { throw unsupported("getRelativeY"); }
	@Override public void setRelativeY(int a0) { throw unsupported("setRelativeY"); }
	@Override public void setForcedPosition(int a0, int a1) { throw unsupported("setForcedPosition"); }
	@Override public java.lang.String getText() { throw unsupported("getText"); }
	@Override public net.runelite.api.widgets.Widget setText(java.lang.String a0) { throw unsupported("setText"); }
	@Override public int getTextColor() { throw unsupported("getTextColor"); }
	@Override public net.runelite.api.widgets.Widget setTextColor(int a0) { throw unsupported("setTextColor"); }
	@Override public int getOpacity() { throw unsupported("getOpacity"); }
	@Override public net.runelite.api.widgets.Widget setOpacity(int a0) { throw unsupported("setOpacity"); }
	@Override public java.lang.String getName() { throw unsupported("getName"); }
	@Override public net.runelite.api.widgets.Widget setName(java.lang.String a0) { throw unsupported("setName"); }
	@Override public int getModelId() { throw unsupported("getModelId"); }
	@Override public net.runelite.api.widgets.Widget setModelId(int a0) { throw unsupported("setModelId"); }
	@Override public int getModelType() { throw unsupported("getModelType"); }
	@Override public net.runelite.api.widgets.Widget setModelType(int a0) { throw unsupported("setModelType"); }
	@Override public int getAnimationId() { throw unsupported("getAnimationId"); }
	@Override public net.runelite.api.widgets.Widget setAnimationId(int a0) { throw unsupported("setAnimationId"); }
	@Override public int getRotationX() { throw unsupported("getRotationX"); }
	@Override public net.runelite.api.widgets.Widget setRotationX(int a0) { throw unsupported("setRotationX"); }
	@Override public int getRotationY() { throw unsupported("getRotationY"); }
	@Override public net.runelite.api.widgets.Widget setRotationY(int a0) { throw unsupported("setRotationY"); }
	@Override public int getRotationZ() { throw unsupported("getRotationZ"); }
	@Override public net.runelite.api.widgets.Widget setRotationZ(int a0) { throw unsupported("setRotationZ"); }
	@Override public int getModelZoom() { throw unsupported("getModelZoom"); }
	@Override public net.runelite.api.widgets.Widget setModelZoom(int a0) { throw unsupported("setModelZoom"); }
	@Override public int getSpriteId() { throw unsupported("getSpriteId"); }
	@Override public boolean getSpriteTiling() { throw unsupported("getSpriteTiling"); }
	@Override public net.runelite.api.widgets.Widget setSpriteTiling(boolean a0) { throw unsupported("setSpriteTiling"); }
	@Override public net.runelite.api.widgets.Widget setSpriteId(int a0) { throw unsupported("setSpriteId"); }
	@Override public boolean isHidden() { throw unsupported("isHidden"); }
	@Override public boolean isSelfHidden() { throw unsupported("isSelfHidden"); }
	@Override public net.runelite.api.widgets.Widget setHidden(boolean a0) { throw unsupported("setHidden"); }
	@Override public int getIndex() { throw unsupported("getIndex"); }
	@Override public net.runelite.api.Point getCanvasLocation() { throw unsupported("getCanvasLocation"); }
	@Override public int getWidth() { throw unsupported("getWidth"); }
	@Override public void setWidth(int a0) { throw unsupported("setWidth"); }
	@Override public int getHeight() { throw unsupported("getHeight"); }
	@Override public void setHeight(int a0) { throw unsupported("setHeight"); }
	@Override public java.awt.Rectangle getBounds() { throw unsupported("getBounds"); }
	@Override public int getItemId() { throw unsupported("getItemId"); }
	@Override public net.runelite.api.widgets.Widget setItemId(int a0) { throw unsupported("setItemId"); }
	@Override public int getItemQuantity() { throw unsupported("getItemQuantity"); }
	@Override public net.runelite.api.widgets.Widget setItemQuantity(int a0) { throw unsupported("setItemQuantity"); }
	@Override public boolean contains(net.runelite.api.Point a0) { throw unsupported("contains"); }
	@Override public int getScrollX() { throw unsupported("getScrollX"); }
	@Override public net.runelite.api.widgets.Widget setScrollX(int a0) { throw unsupported("setScrollX"); }
	@Override public int getScrollY() { throw unsupported("getScrollY"); }
	@Override public net.runelite.api.widgets.Widget setScrollY(int a0) { throw unsupported("setScrollY"); }
	@Override public int getScrollWidth() { throw unsupported("getScrollWidth"); }
	@Override public net.runelite.api.widgets.Widget setScrollWidth(int a0) { throw unsupported("setScrollWidth"); }
	@Override public int getScrollHeight() { throw unsupported("getScrollHeight"); }
	@Override public net.runelite.api.widgets.Widget setScrollHeight(int a0) { throw unsupported("setScrollHeight"); }
	@Override public int getOriginalX() { throw unsupported("getOriginalX"); }
	@Override public net.runelite.api.widgets.Widget setOriginalX(int a0) { throw unsupported("setOriginalX"); }
	@Override public int getOriginalY() { throw unsupported("getOriginalY"); }
	@Override public net.runelite.api.widgets.Widget setOriginalY(int a0) { throw unsupported("setOriginalY"); }
	@Override public net.runelite.api.widgets.Widget setPos(int a0, int a1) { throw unsupported("setPos"); }
	@Override public net.runelite.api.widgets.Widget setPos(int a0, int a1, int a2, int a3) { throw unsupported("setPos"); }
	@Override public int getOriginalHeight() { throw unsupported("getOriginalHeight"); }
	@Override public net.runelite.api.widgets.Widget setOriginalHeight(int a0) { throw unsupported("setOriginalHeight"); }
	@Override public int getOriginalWidth() { throw unsupported("getOriginalWidth"); }
	@Override public net.runelite.api.widgets.Widget setOriginalWidth(int a0) { throw unsupported("setOriginalWidth"); }
	@Override public net.runelite.api.widgets.Widget setSize(int a0, int a1) { throw unsupported("setSize"); }
	@Override public net.runelite.api.widgets.Widget setSize(int a0, int a1, int a2, int a3) { throw unsupported("setSize"); }
	@Override public java.lang.String[] getActions() { throw unsupported("getActions"); }
	@Override public java.lang.String[][] getSubOps() { throw unsupported("getSubOps"); }
	@Override public net.runelite.api.widgets.Widget createChild(int a0, int a1) { throw unsupported("createChild"); }
	@Override public net.runelite.api.widgets.Widget createChild(int a0) { throw unsupported("createChild"); }
	@Override public void deleteAllChildren() { throw unsupported("deleteAllChildren"); }
	@Override public void setAction(int a0, java.lang.String a1) { throw unsupported("setAction"); }
	@Override public void setSubOp(int a0, int a1, java.lang.String a2) { throw unsupported("setSubOp"); }
	@Override public void clearActions() { throw unsupported("clearActions"); }
	@Override public void setOnOpListener(java.lang.Object... a0) { throw unsupported("setOnOpListener"); }
	@Override public void setOnDialogAbortListener(java.lang.Object... a0) { throw unsupported("setOnDialogAbortListener"); }
	@Override public void setOnKeyListener(java.lang.Object... a0) { throw unsupported("setOnKeyListener"); }
	@Override public void setOnMouseOverListener(java.lang.Object... a0) { throw unsupported("setOnMouseOverListener"); }
	@Override public void setOnMouseRepeatListener(java.lang.Object... a0) { throw unsupported("setOnMouseRepeatListener"); }
	@Override public void setOnMouseLeaveListener(java.lang.Object... a0) { throw unsupported("setOnMouseLeaveListener"); }
	@Override public void setOnTimerListener(java.lang.Object... a0) { throw unsupported("setOnTimerListener"); }
	@Override public void setOnTargetEnterListener(java.lang.Object... a0) { throw unsupported("setOnTargetEnterListener"); }
	@Override public void setOnTargetLeaveListener(java.lang.Object... a0) { throw unsupported("setOnTargetLeaveListener"); }
	@Override public boolean hasListener() { throw unsupported("hasListener"); }
	@Override public net.runelite.api.widgets.Widget setHasListener(boolean a0) { throw unsupported("setHasListener"); }
	@Override public boolean isIf3() { throw unsupported("isIf3"); }
	@Override public void revalidate() { throw unsupported("revalidate"); }
	@Override public void revalidateScroll() { throw unsupported("revalidateScroll"); }
	@Override public java.lang.Object[] getOnOpListener() { throw unsupported("getOnOpListener"); }
	@Override public java.lang.Object[] getOnKeyListener() { throw unsupported("getOnKeyListener"); }
	@Override public java.lang.Object[] getOnLoadListener() { throw unsupported("getOnLoadListener"); }
	@Override public java.lang.Object[] getOnInvTransmitListener() { throw unsupported("getOnInvTransmitListener"); }
	@Override public int getFontId() { throw unsupported("getFontId"); }
	@Override public net.runelite.api.widgets.Widget setFontId(int a0) { throw unsupported("setFontId"); }
	@Override public int getBorderType() { throw unsupported("getBorderType"); }
	@Override public void setBorderType(int a0) { throw unsupported("setBorderType"); }
	@Override public boolean isFlippedVertically() { throw unsupported("isFlippedVertically"); }
	@Override public void setFlippedVertically(boolean a0) { throw unsupported("setFlippedVertically"); }
	@Override public boolean isFlippedHorizontally() { throw unsupported("isFlippedHorizontally"); }
	@Override public void setFlippedHorizontally(boolean a0) { throw unsupported("setFlippedHorizontally"); }
	@Override public boolean getTextShadowed() { throw unsupported("getTextShadowed"); }
	@Override public net.runelite.api.widgets.Widget setTextShadowed(boolean a0) { throw unsupported("setTextShadowed"); }
	@Override public int getDragDeadZone() { throw unsupported("getDragDeadZone"); }
	@Override public void setDragDeadZone(int a0) { throw unsupported("setDragDeadZone"); }
	@Override public int getDragDeadTime() { throw unsupported("getDragDeadTime"); }
	@Override public void setDragDeadTime(int a0) { throw unsupported("setDragDeadTime"); }
	@Override public int getItemQuantityMode() { throw unsupported("getItemQuantityMode"); }
	@Override public net.runelite.api.widgets.Widget setItemQuantityMode(int a0) { throw unsupported("setItemQuantityMode"); }
	@Override public int getXPositionMode() { throw unsupported("getXPositionMode"); }
	@Override public net.runelite.api.widgets.Widget setXPositionMode(int a0) { throw unsupported("setXPositionMode"); }
	@Override public int getYPositionMode() { throw unsupported("getYPositionMode"); }
	@Override public net.runelite.api.widgets.Widget setYPositionMode(int a0) { throw unsupported("setYPositionMode"); }
	@Override public int getLineHeight() { throw unsupported("getLineHeight"); }
	@Override public net.runelite.api.widgets.Widget setLineHeight(int a0) { throw unsupported("setLineHeight"); }
	@Override public int getXTextAlignment() { throw unsupported("getXTextAlignment"); }
	@Override public net.runelite.api.widgets.Widget setXTextAlignment(int a0) { throw unsupported("setXTextAlignment"); }
	@Override public int getYTextAlignment() { throw unsupported("getYTextAlignment"); }
	@Override public net.runelite.api.widgets.Widget setYTextAlignment(int a0) { throw unsupported("setYTextAlignment"); }
	@Override public int getWidthMode() { throw unsupported("getWidthMode"); }
	@Override public net.runelite.api.widgets.Widget setWidthMode(int a0) { throw unsupported("setWidthMode"); }
	@Override public int getHeightMode() { throw unsupported("getHeightMode"); }
	@Override public net.runelite.api.widgets.Widget setHeightMode(int a0) { throw unsupported("setHeightMode"); }
	@Override public net.runelite.api.FontTypeFace getFont() { throw unsupported("getFont"); }
	@Override public boolean isFilled() { throw unsupported("isFilled"); }
	@Override public net.runelite.api.widgets.Widget setFilled(boolean a0) { throw unsupported("setFilled"); }
	@Override public java.lang.String getTargetVerb() { throw unsupported("getTargetVerb"); }
	@Override public void setTargetVerb(java.lang.String a0) { throw unsupported("setTargetVerb"); }
	@Override public int getTargetPriority() { throw unsupported("getTargetPriority"); }
	@Override public void setTargetPriority(int a0) { throw unsupported("setTargetPriority"); }
	@Override public boolean getNoClickThrough() { throw unsupported("getNoClickThrough"); }
	@Override public void setNoClickThrough(boolean a0) { throw unsupported("setNoClickThrough"); }
	@Override public boolean getNoScrollThrough() { throw unsupported("getNoScrollThrough"); }
	@Override public void setNoScrollThrough(boolean a0) { throw unsupported("setNoScrollThrough"); }
	@Override public int[] getVarTransmitTrigger() { throw unsupported("getVarTransmitTrigger"); }
	@Override public void setVarTransmitTrigger(int... a0) { throw unsupported("setVarTransmitTrigger"); }
	@Override public void setOnClickListener(java.lang.Object... a0) { throw unsupported("setOnClickListener"); }
	@Override public void setOnHoldListener(java.lang.Object... a0) { throw unsupported("setOnHoldListener"); }
	@Override public void setOnReleaseListener(java.lang.Object... a0) { throw unsupported("setOnReleaseListener"); }
	@Override public void setOnDragCompleteListener(java.lang.Object... a0) { throw unsupported("setOnDragCompleteListener"); }
	@Override public void setOnDragListener(java.lang.Object... a0) { throw unsupported("setOnDragListener"); }
	@Override public void setOnScrollWheelListener(java.lang.Object... a0) { throw unsupported("setOnScrollWheelListener"); }
	@Override public net.runelite.api.widgets.Widget getDragParent() { throw unsupported("getDragParent"); }
	@Override public net.runelite.api.widgets.Widget setDragParent(net.runelite.api.widgets.Widget a0) { throw unsupported("setDragParent"); }
	@Override public java.lang.Object[] getOnVarTransmitListener() { throw unsupported("getOnVarTransmitListener"); }
	@Override public void setOnVarTransmitListener(java.lang.Object... a0) { throw unsupported("setOnVarTransmitListener"); }
}
