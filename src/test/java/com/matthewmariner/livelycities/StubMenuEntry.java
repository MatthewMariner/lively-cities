package com.matthewmariner.livelycities;

/**
 * Every abstract method of {@link net.runelite.api.MenuEntry}, throwing.
 *
 * <p>Mechanically generated from {@code javap net.runelite.api.MenuEntry}
 * against the 1.12.36 API jar and then checked in — no reflection, no mocking
 * framework, nothing generated at runtime. Same purpose as {@link StubClient}:
 * {@link FakeMenuEntry} implements the six {@link CitizenMenu} actually uses, and
 * anything else this plugin starts reaching for fails loudly on its first test
 * rather than quietly returning {@code null}.
 *
 * <p>That matters more here than usual. {@code MenuEntry} is the one interface in
 * this plugin's surface with methods that <b>do</b> reach the game —
 * {@code onClick}, {@code setForceLeftClick}, {@code createSubMenu} — and
 * "the plugin never touched them" is a claim the tests have to be able to make.
 * A permissive stub would have made it unprovable.
 *
 * <p>One method per line on purpose. This is a lookup table, not code to read.
 */
class StubMenuEntry implements net.runelite.api.MenuEntry
{
	static UnsupportedOperationException unsupported(String method)
	{
		return new UnsupportedOperationException(
			"StubMenuEntry does not implement MenuEntry." + method + "(..) — this plugin has never needed it");
	}

	// --- net.runelite.api.MenuEntry ---
	@Override public java.lang.String getOption() { throw unsupported("getOption"); }
	@Override public net.runelite.api.MenuEntry setOption(java.lang.String a0) { throw unsupported("setOption"); }
	@Override public java.lang.String getTarget() { throw unsupported("getTarget"); }
	@Override public net.runelite.api.MenuEntry setTarget(java.lang.String a0) { throw unsupported("setTarget"); }
	@Override public int getIdentifier() { throw unsupported("getIdentifier"); }
	@Override public net.runelite.api.MenuEntry setIdentifier(int a0) { throw unsupported("setIdentifier"); }
	@Override public net.runelite.api.MenuAction getType() { throw unsupported("getType"); }
	@Override public net.runelite.api.MenuEntry setType(net.runelite.api.MenuAction a0) { throw unsupported("setType"); }
	@Override public int getParam0() { throw unsupported("getParam0"); }
	@Override public net.runelite.api.MenuEntry setParam0(int a0) { throw unsupported("setParam0"); }
	@Override public int getParam1() { throw unsupported("getParam1"); }
	@Override public net.runelite.api.MenuEntry setParam1(int a0) { throw unsupported("setParam1"); }
	@Override public boolean isForceLeftClick() { throw unsupported("isForceLeftClick"); }
	@Override public net.runelite.api.MenuEntry setForceLeftClick(boolean a0) { throw unsupported("setForceLeftClick"); }
	@Override public int getWorldViewId() { throw unsupported("getWorldViewId"); }
	@Override public net.runelite.api.MenuEntry setWorldViewId(int a0) { throw unsupported("setWorldViewId"); }
	@Override public boolean isDeprioritized() { throw unsupported("isDeprioritized"); }
	@Override public net.runelite.api.MenuEntry setDeprioritized(boolean a0) { throw unsupported("setDeprioritized"); }
	@Override public net.runelite.api.MenuEntry onClick(java.util.function.Consumer<net.runelite.api.MenuEntry> a0) { throw unsupported("onClick"); }
	@Override public java.util.function.Consumer<net.runelite.api.MenuEntry> onClick() { throw unsupported("onClick"); }
	@Override public boolean isItemOp() { throw unsupported("isItemOp"); }
	@Override public int getItemOp() { throw unsupported("getItemOp"); }
	@Override public int getItemId() { throw unsupported("getItemId"); }
	@Override public net.runelite.api.MenuEntry setItemId(int a0) { throw unsupported("setItemId"); }
	@Override public net.runelite.api.widgets.Widget getWidget() { throw unsupported("getWidget"); }
	@Override public net.runelite.api.NPC getNpc() { throw unsupported("getNpc"); }
	@Override public net.runelite.api.Player getPlayer() { throw unsupported("getPlayer"); }
	@Override public net.runelite.api.Actor getActor() { throw unsupported("getActor"); }
	@Override public net.runelite.api.Menu getSubMenu() { throw unsupported("getSubMenu"); }
	@Override public net.runelite.api.Menu createSubMenu() { throw unsupported("createSubMenu"); }
	@Override public void deleteSubMenu() { throw unsupported("deleteSubMenu"); }
}
