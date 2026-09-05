package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A {@link SidePanel} that remembers whether it is in the sidebar and what it was last
 * told to draw.
 *
 * <p><b>This is the whole reason {@link SidePanel} is an interface.</b> The real
 * implementation ends in a {@code ClientToolbar} (private constructor, unmockable, no
 * mocking framework here) and a {@code PluginPanel} (Swing, which a lifecycle test must
 * not need). The promise being kept — {@code shutDown()} leaves nothing registered —
 * has to hold on a build machine with no display, which is exactly where it is checked.
 *
 * <p>{@link #shown} is a count and not a flag, because "added once" and "added, removed,
 * added" are different states and only one of them is what {@code startUp} does.
 */
final class RecordingSidePanel implements SidePanel
{
	private final List<PanelModel> models = new ArrayList<>();

	private int shown;
	private int hidden;
	private boolean open;

	@Override
	public void show()
	{
		shown++;
	}

	@Override
	public void hide()
	{
		hidden++;

		// The real one tells the panel it is no longer in the sidebar, or the game tick
		// would go on composing models for it. Mirrored here so a test can assert the
		// consequence — that nothing is refreshed after a teardown — rather than the
		// call.
		open = false;
	}

	@Override
	public boolean isOpen()
	{
		return open;
	}

	@Override
	public void refresh(PanelModel model)
	{
		models.add(model);
	}

	/** Pretends the user clicked the toolbar button. */
	RecordingSidePanel opened()
	{
		open = true;
		return this;
	}

	/** @return whether the button is in the sidebar now: shown at least once, and not since hidden */
	boolean inSidebar()
	{
		return shown > hidden;
	}

	int getShown()
	{
		return shown;
	}

	int getHidden()
	{
		return hidden;
	}

	/** @return every model handed over, in order */
	List<PanelModel> models()
	{
		return models;
	}

	/** @return the most recent model, or {@code null} if nothing was ever drawn */
	@Nullable
	PanelModel last()
	{
		return models.isEmpty() ? null : models.get(models.size() - 1);
	}
}
