package com.sillysoft.lux.agent;

public class Rule {
	
	private String name;
	private Float weight;
	private Integer rank;
	
	public Rule(String input) {
		String[] values = input.split("_");
		name = values[0];
		weight = new Float(values[1]);
		rank = new Integer(values[2]);
	}
	
	public String getName() {
		String result = name;
		return result;
	}
	public int setName(String input) {
		name = input;
		return 0;
	}
	public float getWeight() {
		float result = weight.floatValue();
		return result;
	}
	public int setWeight(float input) {
		weight = input;
		return 0;
	}
	public int getRank() {
		int result = rank.intValue();
		return result;
	}
	public int setRank(int input) {
		rank = input;
		return 0;
	}
	public String toString() {
		String result = name.toString() + "_" + weight.toString() + "_" + rank.toString();
		return result;
	}
	
}
