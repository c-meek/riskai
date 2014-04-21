package com.sillysoft.lux.agent;

import java.util.Comparator;

public class RuleComparator<T> implements Comparator<T> {

	public int compare(T o1, T o2) {
		int result;
		Rule rule1 = (Rule)o1;
		Rule rule2 = (Rule)o2;
		if (rule1.getRank() < rule2.getRank()) {
			result = -1;
		} else if (rule1.getRank() > rule2.getRank()) {
			result =1 ;
		} else {
			result = 0;
		}
		return result;
	}

}
