package com.matthewmariner.livelycities;

import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;

/**
 * A {@link MenuEntry} that remembers what was set on it.
 *
 * <p>The six setters {@link CitizenMenu} uses, each returning {@code this} so the
 * chained call in the plugin works exactly as it does against the real client, and
 * the matching getters so a test can assert on the result. Everything else is
 * inherited from {@link StubMenuEntry} and throws — including
 * {@code setForceLeftClick} and {@code onClick}, which is how
 * {@code CitizenMenuTest} can claim the plugin never reaches for them.
 *
 * <p>The default type is deliberately <b>not</b> {@link MenuAction#RUNELITE}. It
 * is {@link MenuAction#CANCEL}, i.e. wrong, so that "every entry is RUNELITE" is a
 * claim about what the plugin set rather than about what this class defaults to.
 * Same for {@link #deprioritized}: false by default, so the assertion has
 * something to fail on. That is the fixture-too-uniform trap, in the one place
 * where it would have hidden the whole of upstream issue #14.
 */
final class FakeMenuEntry extends StubMenuEntry
{
	private String option = "";
	private String target = "";
	private MenuAction type = MenuAction.CANCEL;
	private int identifier;
	private boolean deprioritized;

	@Override
	public String getOption()
	{
		return option;
	}

	@Override
	public MenuEntry setOption(String option)
	{
		this.option = option;
		return this;
	}

	@Override
	public String getTarget()
	{
		return target;
	}

	@Override
	public MenuEntry setTarget(String target)
	{
		this.target = target;
		return this;
	}

	@Override
	public MenuAction getType()
	{
		return type;
	}

	@Override
	public MenuEntry setType(MenuAction type)
	{
		this.type = type;
		return this;
	}

	@Override
	public int getIdentifier()
	{
		return identifier;
	}

	@Override
	public MenuEntry setIdentifier(int identifier)
	{
		this.identifier = identifier;
		return this;
	}

	@Override
	public boolean isDeprioritized()
	{
		return deprioritized;
	}

	@Override
	public MenuEntry setDeprioritized(boolean deprioritized)
	{
		this.deprioritized = deprioritized;
		return this;
	}

	@Override
	public String toString()
	{
		return "FakeMenuEntry{" + option + " " + target
			+ ", " + type + ", id " + identifier
			+ ", deprioritized " + deprioritized + '}';
	}
}
