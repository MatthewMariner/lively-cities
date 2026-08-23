package com.matthewmariner.livelycities;

import net.runelite.client.ui.overlay.Overlay;

/**
 * Where an overlay is registered and unregistered.
 *
 * <p><b>Why an interface and not {@code OverlayManager} itself.</b> The same
 * reason as {@link ConfigWriter}: its only constructor is <i>private</i> —
 * verified with {@code javap -p} against the 1.12.36 client jar, where it takes
 * seven collaborators — so it can be neither constructed nor subclassed, and
 * there is no mocking framework on this classpath.
 *
 * <p>That is not a theoretical problem here. "An overlay left in the manager keeps
 * drawing after shutdown" is the same class of leak as "a {@code RuneLiteObject}
 * left active renders forever", which this plugin already guards with a
 * mutation-verified test, and this repo has already been caught once shipping a
 * plugin class nothing had ever constructed. Two methods behind an interface is
 * what makes {@code startUp} adding and {@code shutDown} removing an assertion
 * instead of a reading of the source.
 */
interface OverlayRegistry
{
	void add(Overlay overlay);

	void remove(Overlay overlay);
}
