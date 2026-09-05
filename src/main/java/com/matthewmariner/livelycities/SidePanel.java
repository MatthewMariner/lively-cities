package com.matthewmariner.livelycities;

/**
 * The panel's place in RuneLite's sidebar: put it there, take it away, ask whether
 * anyone is looking, tell it what to draw.
 *
 * <p>Behind an interface for the same reason as {@link OverlayRegistry} and
 * {@link ConfigWriter} — {@code ClientToolbar}'s only constructor is private, verified
 * with {@code javap -p} against the 1.12.37 client jar, so it can be neither
 * constructed nor subclassed and there is no mocking framework on this classpath —
 * and for one more that matters as much: <b>the thing on the other side is Swing</b>.
 * A lifecycle test that had to build a real {@code PluginPanel} would need a windowing
 * system, and the promise being kept here is precisely the one that has to hold on a
 * machine with no display: {@code shutDown()} leaves nothing registered.
 *
 * <p>That promise is not rhetorical here. This plugin has already shipped a fix for a
 * teardown that left objects behind, and a {@code NavigationButton} left in the
 * toolbar is the same defect wearing a different coat — a button that opens a panel
 * whose plugin is gone, which no amount of re-enabling can remove because the toolbar
 * keys its navigation off the button instance and a second one would leave the first
 * where it was. {@code LivelyCitiesPluginLifecycleTest} asserts against a recording
 * implementation of this interface, so "startUp adds it and shutDown removes it" is an
 * assertion rather than a reading of the source.
 */
interface SidePanel
{
	/** Adds the button to the sidebar. What {@code startUp()} calls. */
	void show();

	/**
	 * Takes it away again — button and panel both.
	 *
	 * <p>Called unconditionally from {@code shutDown()}, never under whatever condition
	 * put it there. A teardown has to undo what happened, not what the settings
	 * currently say should have happened.
	 */
	void hide();

	/**
	 * @return whether the panel is the one currently open in the sidebar.
	 *
	 * <p>This is a cost question, not a cosmetic one. {@link #refresh} is fed from the
	 * game tick, and composing the model walks every cached wrapper and asks the client
	 * about each one — real per-tick work, in a plugin whose whole performance argument
	 * is that it does almost none. A closed panel is nobody watching, so the plugin
	 * asks first and a closed panel costs one boolean read every 600ms.
	 */
	boolean isOpen();

	/**
	 * A new reading has been taken: draw it.
	 *
	 * <p>Called from the client thread, so an implementation that touches Swing has to
	 * hop to the event dispatch thread itself rather than making every caller remember
	 * to.
	 */
	void refresh(PanelModel model);
}
