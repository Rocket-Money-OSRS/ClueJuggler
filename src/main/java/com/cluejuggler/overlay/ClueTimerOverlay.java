package com.cluejuggler.overlay;

import com.cluejuggler.ClueJugglerConfig;
import com.cluejuggler.ClueJugglerPlugin;
import com.cluejuggler.service.ClueTimerService;
import com.cluejuggler.service.ClueTimerService.TimedClue;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class ClueTimerOverlay extends OverlayPanel
{
	private final Client client;
	private final ClueJugglerConfig config;
	private final ClueTimerService timerService;
	private final ItemManager itemManager;

	@Inject
	public ClueTimerOverlay(Client client, ClueJugglerPlugin plugin, ClueJugglerConfig config, 
		ClueTimerService timerService, ItemManager itemManager)
	{
		super(plugin);
		this.client = client;
		this.config = config;
		this.timerService = timerService;
		this.itemManager = itemManager;
		
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(OverlayPriority.LOW);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showTimers())
		{
			return null;
		}

		List<TimedClue> timers = timerService.getTimedClues();
		if (timers.isEmpty())
		{
			return null;
		}

		panelComponent.getChildren().clear();
		
		// Title
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Clue Timers")
			.color(Color.WHITE)
			.build());

		if (config.combineTimers())
		{
			// Show only the lowest timer
			TimedClue lowestTimer = null;
			for (TimedClue timer : timers)
			{
				if (lowestTimer == null || timer.getTimeRemaining().compareTo(lowestTimer.getTimeRemaining()) < 0)
				{
					lowestTimer = timer;
				}
			}

			if (lowestTimer != null)
			{
				addTimerLine(lowestTimer, timers.size() > 1 ? " (" + timers.size() + " clues)" : "");
			}
		}
		else
		{
			// Show all timers
			for (TimedClue timer : timers)
			{
				addTimerLine(timer, "");
			}
		}

		return super.render(graphics);
	}

	private void addTimerLine(TimedClue timer, String suffix)
	{
		Duration remaining = timer.getTimeRemaining();
		String timeText = formatDuration(remaining);
		
		Color textColor = Color.WHITE;
		if (timer.isNotified() || remaining.getSeconds() <= config.timerNotificationSeconds())
		{
			textColor = Color.RED;
		}
		else if (remaining.getSeconds() <= 60)
		{
			textColor = Color.ORANGE;
		}

		String label = timer.getListName() != null ? timer.getListName() : "Good";
		
		panelComponent.getChildren().add(LineComponent.builder()
			.left(label + suffix)
			.leftColor(config.goodClueTextColor())
			.right(timeText)
			.rightColor(textColor)
			.build());
	}

	private String formatDuration(Duration duration)
	{
		long totalSeconds = duration.getSeconds();
		if (totalSeconds < 0)
		{
			return "0:00";
		}

		long minutes = totalSeconds / 60;
		long seconds = totalSeconds % 60;

		if (minutes >= 10)
		{
			return String.format("%dm", minutes);
		}

		return String.format("%d:%02d", minutes, seconds);
	}
}

