package com.matthewmariner.livelycities;

import java.awt.Rectangle;

/**
 * A widget that knows where it is on the canvas and whether it is drawn.
 *
 * <p>Two overrides, because {@link CitizenMenu}'s minimap guard asks exactly two
 * questions: is this widget hidden, and what rectangle does it occupy. Everything
 * else is inherited from {@link StubWidget} and throws. There were {@code getWidth()}
 * and {@code getHeight()} overrides here too and they were dead — {@link #getBounds()}
 * builds its rectangle from the fields directly, and nothing in production asks a
 * widget its width.
 *
 * <p>{@link #getBounds()} is written out rather than delegated to
 * {@code getCanvasLocation()} because that is what the real one does: in the injected
 * 1.12.36 client {@code lw.getBounds()} is
 * {@code new Rectangle(cz, ca, getWidth(), getHeight())}, built fresh on
 * every call and never null. A fake that returned null sometimes would be modelling a
 * failure the real widget does not have.
 *
 * <p><b>The unpositioned case is {@link #neverLaidOut()}, and its numbers come from
 * the bytecode rather than from a guess.</b> {@code lw}'s no-arg constructor sets
 * {@code cz} and {@code ca} to {@code -1} and the raw width and height fields
 * {@code cv} and {@code do} to {@code 0}, so a widget the client has loaded and never
 * laid out reports {@code Rectangle(-1, -1, 0, 0)} — an empty box that contains
 * nothing. An earlier revision of this javadoc said it reported a rectangle at canvas
 * (0, 0), "inside the viewport", and described that as a hole the guard could fall
 * into; it is not one, and the test that pinned it has been replaced.
 */
final class FakeWidget extends StubWidget
{
	private final int canvasX;
	private final int canvasY;
	private final int width;
	private final int height;

	private boolean hidden;

	private FakeWidget(int canvasX, int canvasY, int width, int height)
	{
		this.canvasX = canvasX;
		this.canvasY = canvasY;
		this.width = width;
		this.height = height;
	}

	/** A widget occupying one rectangle of the canvas, drawn. */
	static FakeWidget at(int canvasX, int canvasY, int width, int height)
	{
		return new FakeWidget(canvasX, canvasY, width, height);
	}

	/**
	 * A widget the client has loaded and never laid out: exactly the state
	 * {@code lw}'s constructor leaves it in, and therefore exactly the bounds the real
	 * one reports there. Not drawn-and-hidden — {@code isHidden()} is false, because
	 * hiding is not what makes an unpositioned widget harmless.
	 */
	static FakeWidget neverLaidOut()
	{
		return new FakeWidget(-1, -1, 0, 0);
	}

	/** The same widget, with the client's own "not drawn" flag set. */
	FakeWidget hidden()
	{
		this.hidden = true;
		return this;
	}

	@Override
	public boolean isHidden()
	{
		return hidden;
	}

	@Override
	public Rectangle getBounds()
	{
		return new Rectangle(canvasX, canvasY, width, height);
	}
}
