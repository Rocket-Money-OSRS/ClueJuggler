package com.cluejuggler.model;

public enum ClueStatus
{
	GOOD(1),
	BAD(-1),
	UNSURE(0),
	CUSTOM(2);
	
	private final int value;
	
	ClueStatus(int value)
	{
		this.value = value;
	}
	
	public int getValue()
	{
		return value;
	}
	
	public static ClueStatus fromValue(int value)
	{
		for (ClueStatus status : values())
		{
			if (status.value == value)
			{
				return status;
			}
		}
		return UNSURE;
	}
}

