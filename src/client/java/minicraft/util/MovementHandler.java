package minicraft.util;

import minicraft.core.io.InputHandler;
import minicraft.entity.Direction;

public class MovementHandler {
	private static final double ROTATION_SPEED = Math.toRadians(3);
	private static final double STICK_ROTATION_SPEED = Math.toRadians(4);

	public double rotation = Math.PI / 2;
	private Direction direction = Direction.DOWN;
	private int stepX, stepY;
	private double remainderX, remainderY;

	public Vector2 update(InputHandler input, boolean canStrafe) {
		Vector2 vec = new Vector2(0, 0);
		if (input.inputDown("move-up")) {
			vec.y--;
		}
		if (input.inputDown("move-down")) {
			vec.y++;
		}
		if (input.inputDown("move-left")) {
			vec.x--;
		}
		if (input.inputDown("move-right")) {
			vec.x++;
		}
		vec.x += input.leftStickX();
		vec.y += input.leftStickY();
		vec.x = Math.max(-1, Math.min(1, vec.x));
		vec.y = Math.max(-1, Math.min(1, vec.y));
		if (!canStrafe) {
			vec.x = 0;
		}
		if (input.inputDown("turn-left")) {
			rotation -= ROTATION_SPEED;
		}
		if (input.inputDown("turn-right")) {
			rotation += ROTATION_SPEED;
		}
		rotation += input.rightStickX() * STICK_ROTATION_SPEED;
		double facingX = Math.cos(rotation);
		double facingY = Math.sin(rotation);
		int directionX = (int) Math.round(facingX * 100);
		int directionY = (int) Math.round(facingY * 100);
		direction = Direction.getDirection(directionX, directionY);
		double forward = -vec.y;
		double right = vec.x;
		vec.x = facingX * forward - facingY * right;
		vec.y = facingY * forward + facingX * right;
		return vec;
	}

	public void add(double dx, double dy) {
		remainderX += dx;
		remainderY += dy;
		stepX = (int) remainderX;
		stepY = (int) remainderY;
		remainderX -= stepX;
		remainderY -= stepY;
	}

	public Direction getDirection() {
		return direction;
	}

	public int getStepX() {
		return stepX;
	}

	public int getStepY() {
		return stepY;
	}
}
