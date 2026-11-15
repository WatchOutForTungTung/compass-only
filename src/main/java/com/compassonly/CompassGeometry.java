package com.compassonly;

import java.awt.Rectangle;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * Where the enlarged compass goes on the current frame, in canvas pixels.
 */
@Value
class CompassGeometry
{
	/** The space the hidden minimap occupied. */
	Rectangle minimapBounds;
	/** Sprite whose shape is the minimap's visible area, or -1. */
	int minimapMaskSprite;
	/** The space the game's own small compass occupied. */
	@Nullable
	Rectangle compassBounds;
	/** Sprite whose shape is the small compass's visible area, or -1. */
	int compassMaskSprite;

	double centerX;
	double centerY;
	/** Diameter of the enlarged compass. */
	double diameter;
	/** Diameter of the part of the compass sprite the game normally shows. */
	double sourceDiameter;
	/** Bigger than the minimap's space, so it is drawn over the interface instead of in the minimap's place. */
	boolean oversized;

	boolean contains(int x, int y)
	{
		final double dx = x - centerX;
		final double dy = y - centerY;
		final double radius = diameter / 2;
		return dx * dx + dy * dy <= radius * radius;
	}
}
