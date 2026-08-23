package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;

/**
 * The client minimenu, as an array a test can read.
 *
 * <p>{@link #createMenuEntry(int)} reproduces the real index arithmetic, verified
 * by disassembling 1.12.36: a negative index is folded to
 * {@code count + idx + 1} (so {@code -1} appends at the end) and a non-negative
 * one is used directly, shifting whatever is already there. That is the whole
 * point of this class rather than a list that only counts calls — "our entries go
 * in below every real option" is a claim about <i>where</i> in the array they
 * landed, and the array is rendered last-first
 * ({@code MenuOpened.getFirstEntry()} returns {@code menuEntries[length - 1]}), so
 * index 0 is the bottom row of the menu and index {@code count} is the top one.
 * A fake that appended regardless of the index would have made the two
 * indistinguishable.
 *
 * <p>{@link #seedWorldClick()} fills the menu with the entries the client itself
 * would have built for a right-click on the world, so that inserting at index 0 has
 * something real to be below — and so that {@link CitizenMenu}'s
 * world-click-versus-interface-click test has something to read.
 */
final class FakeMenu implements Menu
{
	private final List<MenuEntry> entries = new ArrayList<>();

	/** Only the ones {@link #createMenuEntry(int)} produced — see {@link #created()}. */
	private final List<MenuEntry> created = new ArrayList<>();

	/**
	 * One stand-in for a client entry, appended in array order — i.e. below
	 * everything seeded before it, because the array renders last-first.
	 *
	 * <p>The {@link MenuAction} is a required argument rather than a default,
	 * because {@link CitizenMenu} reads it: "was this a click on the world or on an
	 * interface" is answered by looking for a {@code WALK} entry, and a fixture
	 * whose seeds all carried the same type could not tell the two apart.
	 */
	FakeMenu seed(MenuAction action, String option)
	{
		entries.add(new FakeMenuEntry().setOption(option).setType(action));
		return this;
	}

	/**
	 * The menu the client builds for a right-click on the game world: Cancel, Walk
	 * here, and something real to click on.
	 */
	FakeMenu seedWorldClick()
	{
		return seed(MenuAction.CANCEL, "Cancel")
			.seed(MenuAction.WALK, "Walk here")
			.seed(MenuAction.NPC_FIRST_OPTION, "Talk-to");
	}

	/**
	 * The menu the client builds for a right-click on an interface: no "Walk here",
	 * because there is no tile under an inventory slot.
	 */
	FakeMenu seedInterfaceClick()
	{
		return seed(MenuAction.CANCEL, "Cancel")
			.seed(MenuAction.CC_OP, "Drop")
			.seed(MenuAction.CC_OP, "Wield");
	}

	@Override
	public MenuEntry createMenuEntry(int idx)
	{
		int at = idx;
		if (at < 0)
		{
			at = entries.size() + at + 1;
			if (at < 0)
			{
				throw new IllegalArgumentException("index " + idx + " is past the start of the menu");
			}
		}

		if (at > entries.size())
		{
			throw new IllegalStateException("index " + idx + " is past the end of a "
				+ entries.size() + "-entry menu");
		}

		MenuEntry entry = new FakeMenuEntry();
		entries.add(at, entry);
		created.add(entry);
		return entry;
	}

	@Override
	public MenuEntry[] getMenuEntries()
	{
		return entries.toArray(new MenuEntry[0]);
	}

	@Override
	public void setMenuEntries(MenuEntry[] menuEntries)
	{
		throw new UnsupportedOperationException(
			"FakeMenu.setMenuEntries — this plugin only ever adds entries, never replaces the array");
	}

	@Override
	public void removeMenuEntry(MenuEntry entry)
	{
		throw new UnsupportedOperationException(
			"FakeMenu.removeMenuEntry — this plugin never removes an entry; "
				+ "conditional removal of real entries is a hub-forbidden feature");
	}

	@Override
	public int getMenuX()
	{
		throw new UnsupportedOperationException("FakeMenu.getMenuX");
	}

	@Override
	public int getMenuY()
	{
		throw new UnsupportedOperationException("FakeMenu.getMenuY");
	}

	@Override
	public int getMenuWidth()
	{
		throw new UnsupportedOperationException("FakeMenu.getMenuWidth");
	}

	@Override
	public int getMenuHeight()
	{
		throw new UnsupportedOperationException("FakeMenu.getMenuHeight");
	}

	// --- what the tests ask ---------------------------------------------------

	/** @return the entries in array order, i.e. bottom row of the menu first */
	List<MenuEntry> entries()
	{
		return entries;
	}

	/**
	 * @return the entries {@link #createMenuEntry(int)} handed out, in the order it
	 * handed them out.
	 *
	 * <p>Tracked separately rather than filtered out of {@link #entries()} by type.
	 * Filtering on {@code getType() == RUNELITE} would make "every entry we add is
	 * RUNELITE" true by construction — the assertion would be checking the fake.
	 */
	List<MenuEntry> created()
	{
		return created;
	}

	/** @return how many rows the menu holds */
	int size()
	{
		return entries.size();
	}
}
