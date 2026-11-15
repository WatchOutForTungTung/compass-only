package com.compassonly;

import java.awt.Rectangle;
import lombok.Value;

/**
 * Where the real inventory is and where its enlarged copy is drawn, in canvas pixels.
 */
@Value
class InventoryGeometry
{
	/** The real inventory, drawn by the game at normal size. */
	Rectangle source;
	/** The enlarged copy drawn on top. */
	Rectangle target;

	int toSourceX(int x)
	{
		return source.x + (int) ((long) (x - target.x) * source.width / target.width);
	}

	int toSourceY(int y)
	{
		return source.y + (int) ((long) (y - target.y) * source.height / target.height);
	}
}
