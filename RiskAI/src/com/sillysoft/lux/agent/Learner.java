package com.sillysoft.lux.agent;

import com.sillysoft.lux.*;
import com.sillysoft.lux.util.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;

/**
 * This class is an adaptive AI that was designed for CSE 5523 (Machine Learning) at The Ohio State University.
 * It makes use of dynamic scripting and weighted game policy techniques to refine its strategy.
 * 
 * @authors Christopher Meek, Kyle Donovan
 */
public class Learner extends SmartAgentBase {
	// values used in tactics analysis, adjusted via learning weights
		float recklessness;
		float recklessFortifyThreshold;
		float recklessCardThreshold;
	// fine-tuning weights that can be adjusted via the rule set
		float[] deployWeights, attackWeights, fortifyWeights;
		// A filename for the log
		private String fileName;
		private String rulesPath = Board.getAgentPath() + "rules.txt";
		

	public float version() {
		return 1.0f;
	}

	public String description() {
		String result = "Learner is an AI that uses dynamic scripting and weighted game policy to improve its Risk strategy.";
		return result;
	}

	public String name() {
		String result = "Learner";
		return result;
	}

	public void placeArmies( int numberOfArmies )
	{
		setup();
	Country mostValuableCountry = null;
	float largestStrategicValue=-100000;
	// Use a PlayerIterator to cycle through all the countries that we own.
	CountryIterator own = new PlayerIterator( ID, countries );
	while(numberOfArmies>0)
	{
		while (own.hasNext()) 
		{
			Country us = own.next();
			float strategicValue=calculateStrategicValue(us, deployWeights);
			
			// If it's the best so far store it
			if ( strategicValue > largestStrategicValue )
			{
				largestStrategicValue=strategicValue;
				mostValuableCountry=us;
			}
		}
		board.placeArmies( 1, mostValuableCountry);
		numberOfArmies--;
		}
	}
	
	public void cardsPhase( Card[] cards )
	{
		Card[] set=null;
		if(cards.length==5 || recklessness>recklessCardThreshold)
		{
			set=Card.getBestSet(cards, ID, countries);
		}
		if(set!=null)
		{
			board.cashCards(set[0], set[1], set[2]);
		}
	}
	
	
public void attackPhase()
{
//We choose a target and attack, then evaluate if we should continue attacking
int countriesConquered=0;
boolean stillAttacking=true;
while(stillAttacking)
{
	// Cycle through all of the countries that we have 4 or more armies on. 
	// It is never wise to attack with less than 4 armies (3 committed to attack)
	// We look through all owned countries that are able to attack,
	// and check the strategic value of of the neighboring enemy countries
	// The enemy country with the lowest Strategic value is selected as the attack target 
	// After checking all possible attacking countries 
	CountryIterator armies = new ArmiesIterator( ID, 4, countries );
	Country attacker=null;
	Country target=null;
	float lowestStrategicValue=1000000;
	while (armies.hasNext()) 
	{
		Country us = armies.next();
		int[] possibleTargets=us.getHostileAdjoiningCodeList();
		for(int i=0; i<possibleTargets.length; i++)
		{
			float strategicValue=calculateStrategicValue(countries[possibleTargets[i]], attackWeights);
			//if target has low strategic value (should be taken)
			// and is plausible attack (can be taken), we set this as current preferred target
			if(strategicValue<lowestStrategicValue&&plausibleAttack(us,countries[possibleTargets[i]]))
			{
				lowestStrategicValue=strategicValue;
				attacker=us;
				target=countries[possibleTargets[i]];
			}
		}
	}
	//If target found
	if(target!=null)
	{
		board.attack(attacker, target, false);
		if(target.getOwner()==ID)
		{
			countriesConquered++;
		}
		stillAttacking=evaluateAttackPhase(countriesConquered);
	}
	else
	{
		stillAttacking=false;
	}
}
}

public int moveArmiesIn( int cca, int ccd)
{
// If the defending country has no adjacent enemies we keep the maximum number of troops
// possible in the attacking country
if ( countries[ccd].getHostileAdjoiningCodeList().length>0 )
	return 0;

// Otherwise we move everyone into the newly conquered country
return countries[cca].getArmies()-1;
}

public void fortifyPhase()
{	
	// Cycle through all the countries and find countries that we could move from:
	// if country has no surrounding enemies, move armies toward country with most strategic value
	// otherwise check recklessness to decide how to move armies
	CountryIterator armies = new ArmiesIterator( ID, 2, countries );
	
	while(armies.hasNext())
	{
		Country us=armies.next();
		int[] adjoiningCountries = us.getFriendlyAdjoiningCodeList();
		Country fortifyTarget=null;
		// if reckless, move to attack position
		if(recklessness>recklessFortifyThreshold)
		{
			float highestStrategicValue=calculateStrategicValue(us, fortifyWeights);
			for(int i=0; i<adjoiningCountries.length;i++)
			{
				float strategicValue=calculateStrategicValue(countries[adjoiningCountries[i]], fortifyWeights);
				if( strategicValue > highestStrategicValue)
				{
					fortifyTarget=countries[adjoiningCountries[i]];
					highestStrategicValue=strategicValue;
				}
			}	
		}
		// else move to defend
		else
		{
			float highestVulnerability=calculateVulnerability(us, fortifyWeights);
			for(int i=0; i<adjoiningCountries.length;i++)
			{
				float vulnerability=calculateVulnerability(countries[adjoiningCountries[i]], fortifyWeights);
				if(vulnerability>highestVulnerability)
				{
					fortifyTarget=countries[adjoiningCountries[i]];
					highestVulnerability=vulnerability;
				}
			}
		}
		if(fortifyTarget!=null)
		{
			board.fortifyArmies(us.getMoveableArmies(), us, fortifyTarget);
		}
	}
}



	// methods for machine learning aspects of the AI
	
	
	private float howDivided(Country country) 
	{
		int[] hostileCountries=country.getHostileAdjoiningCodeList();
		List <Integer> IDList = new ArrayList <Integer>();
		for(int i=0; i<hostileCountries.length; i++)
		{
			int countryID=countries[hostileCountries[i]].getOwner();
			if(!IDList.contains(countryID))
			{
				IDList.add(countryID);
			}			
		}
		return IDList.size();
	
	}
	
	
	/**
	 * This method weights the number of troops according to their distance, with closer troops being more relevant.
	 * 
	 * @param numberOfTroops
	 * @return The weighted number of troops
	 */
	public float calculateWeightedTroopValue(Country srcCountry, Country destCountry) {
		float result = 0;
		int distance = BoardHelper.easyCostBetweenCountries(srcCountry,destCountry, countries).length;
		result = (float) distance;
		return result;
	}
	/**
	 * This method calculates the strategic value of a country.
	 * A high strategic value means the country is valuable to its owner and is unattractive to potential attackers.
	 * A low strategic value means the country is not valuable to its owner and is attractive to potential attackers.
	 * 
	 * @param country The country for which the strategic value is to be calculated
	 * @return The strategic value of the country as a float between 0.0 and 1.0
	 */
	public float calculateStrategicValue(Country country, float[] weights) {
		float result = 0;
		float advantage = calculateAdvantage(ID, weights);
		result = (calculateRecklessness(advantage)*calculateImportance(country, weights))/(calculateVulnerability(country, weights)/calculateRecklessness(advantage));
		return result;
	}
	
	/**
	 * This method calculates the vulnerability of a country.
	 * 
	 * @param country The country for which the vulnerability is to be calculated
	 * @return The vulnerability of the country as a float between 0.0 and 1.0
	 */
	public float calculateVulnerability(Country country, float[] weights) {
		float result = 0;
		int enemyTroops = 0;
		int friendlyTroops = 0;
		for (int i=0; i<numContinents; i++) {
			ContinentIterator itr = new ContinentIterator(i, countries);
			while (itr.hasNext()) {
				Country otherCountry = itr.next();
				if (otherCountry.getOwner() != ID) { // if the country is owned by an enemy
					enemyTroops += calculateWeightedTroopValue(country, otherCountry);
				}
				else
				{
					friendlyTroops += calculateWeightedTroopValue(country, otherCountry);
				}
			}
			enemyTroops += BoardHelper.getEnemyArmiesInContinent(ID, i, countries);
		}
		
		float divided = howDivided(country);
		result = (enemyTroops/divided) - friendlyTroops;
		return result;
	}
	
	/**
	 * This method calculates the threat of an enemy player.
	 * 
	 * @param player The player whose threat is to be calculated
	 * @return The threat of the player as a float between 0.0 and 1.0
	 */
	public float calculateThreat(int player, float[] weights) {
		float result = 0;
		// threat = (G*troop_income + H*card_count)/J*distance
		// "distance" is a little abstract, since we're talking about the enemy as a whole
		int income = board.getPlayerIncome(player);
		int cards = board.getPlayerCards(player);
		result = weights[6]*income + weights[7]*cards;
		return result;
	}
	
	/**
	 * This method calculates the current recklessness value.
	 * @param advantage The advantage value returned by getAdvantage()
	 * @return The current recklessness value
	 */
	public float calculateRecklessness(float advantage) {
		float result = 0;
		// Recklessness = advantage + number of turns taken + current card bonus
		int turnsTaken = board.getTurnCount(); // check that these two methods do what we actually need
		int bonus = board.getNextCardSetValue();
		result = advantage + turnsTaken + bonus;
		return result;
	}
	private float calculateImportance(Country country, float[] weights) 
	{
		
		int countryOwner=country.getOwner();
		int continentCode=country.getContinent();
		CountryIterator continent = new ContinentIterator(continentCode, countries);
		int countryCount=0;
		int ownedCount=0;
		while(continent.hasNext())
		{
			Country countryInContinent=continent.next();
			countryCount++;
			if(countryInContinent.getOwner()==countryOwner)
			{
				ownedCount++;
			}
		}
		float percentageOfContinent=1/countryCount;
		float percentageOwned=ownedCount/countryCount;
		float result= weights[0] * percentageOfContinent + weights[1] * percentageOwned;
		return result;
	}
	private int[] getContinents(int playerID)
	{
		List <Integer> continentCodes= new ArrayList <Integer>();
		int[] result = new int[BoardHelper.numberOfContinents(countries)];
		CountryIterator playerCountries = new PlayerIterator(playerID, countries);
		int i=0;
		while(playerCountries.hasNext())
		{
			Country us = playerCountries.next();
			int continentCode=us.getContinent();
			if(!continentCodes.contains(continentCode))
			{
				continentCodes.add(continentCode);
				result[i]=continentCode;
				i++;
			}
		}
		return result;
	}
	private float calculateStability(int playerID, float[] weights) 
	{
		int continent;
		int[] continents = getContinents(playerID);
		float greatestVulnerability=-100000;
		float continentsHeld=0;
		for(int i=0; i<continents.length; i++)
		{
			continent=continents[i];
			//if player owns this continent
			if(BoardHelper.playerOwnsContinent(playerID, continent, countries))
			{
				continentsHeld++;
				//check boarders of this continent
				int[] boarderCountries=BoardHelper.getContinentBorders(continent, countries);
				for(int j=0; j<boarderCountries.length; j++)
				{
					//check all boarders within this country for most vulnerable overall
					float vulnerability=calculateVulnerability(countries[boarderCountries[j]], weights);
					if(vulnerability>greatestVulnerability)
					{
						greatestVulnerability=vulnerability;
					}
				}
				
			}
		}
		float result=(weights[4]*continentsHeld)/(weights[5]*greatestVulnerability);
		return result;
	}
	
	private float calculateAdvantage(int playerID, float[] weights) 
	{
		float stability=calculateStability(playerID, weights);
		float threat=calculateThreat(playerID, weights);
		float result=weights[10]*stability-weights[11]*threat;
		return result;
	}
	private boolean plausibleAttack(Country attacker, Country target) {
		return true;
	}

	private boolean evaluateAttackPhase(int countriesConquered) {
		//return TRUE if attacking should continue
		
		return true;
	}
	@Override
	public int pickCountry()
	{
	int goalCont = BoardHelper.getSmallestPositiveEmptyCont(countries, board);

	if (goalCont == -1) // oops, there are no unowned conts
		goalCont = BoardHelper.getSmallestPositiveOpenCont(countries, board);

	// So now pick a country in the desired continent
	return pickCountryInContinent(goalCont);
	}
	
	
	public String youWon()
	{ 
		// run the "win" fitness function to adjust rules
		// store the new weight values
		String answer = "The machines are learning";
		float gameResult = winFitnessFunction();
		adjustRules(deployWeights, attackWeights, fortifyWeights, gameResult);
		return answer;
	}

	public String message( String message, Object data )
	{
	if ("youLose".equals(message))
		{
			float gameResult = lossFitnessFunction();
			adjustRules(deployWeights, attackWeights, fortifyWeights, gameResult);
		}
	return null;
	}
	
	public float winFitnessFunction() {
		float result = 0.0f;
		return result;
	}
	
	public float lossFitnessFunction() {
		float result = 0.0f;
		return result;
	}
	
	public void getWeightValues() {
		// get the array of deploy rules
		char[] raw = {};
		FileReader reader;
		try {
			reader = new FileReader(rulesPath);
			try {
				reader.read(raw);
				reader.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String[] rulesStrings = raw.toString().split("\n");
		Rule[] rules = new Rule[rulesStrings.length];
		for (int i=1; i < rulesStrings.length; i++) {
			rules[i] = new Rule(rulesStrings[i]);
		}
		// sort in ascending rank (1,2,...,n) i.e., better rules first
		RuleComparator<Rule> c = new RuleComparator<Rule>();
		Arrays.sort(rules, c);
		
		// for A-L, find the first rule that mentions that letter
		// assign that rule's weight to the corresponding letter's index (A=0,B=1,...,L=12)

		// get the array of attack rules, sorted in ascending rank (1,2,...,n)
		// for A-L, find the first rule that mentions that letter
		// assign that rule's weight to the corresponding letter's index (A=0,B=1,...,L=12)
		
		// get the array of fortify rules, sorted in ascending rank (1,2,...,n)
		// for A-L, find the first rule that mentions that letter
		// assign that rule's weight to the corresponding letter's index (A=0,B=1,...,L=12)
	}
	
	public void adjustRules(float[] deployWeights, float[] attackWeights, float[] fortifyWeights, float gameResult) {
		// get the array of deploy rules
		// for A-L, find the rule that has the weight that was used during this game
		// change the weight's rank by amount gameResult - determined by the fitness function and passed in
		
		// get the array of attack rules
		// get the array of deploy rules
		// for A-L, find the rule that has the weight that was used during this game
		// change the weight's rank by amount gameResult - determined by the fitness function and passed in
		
		// get the array of fortify rules
		// get the array of deploy rules
		// for A-L, find the rule that has the weight that was used during this game
		// change the weight's rank by amount gameResult - determined by the fitness function and passed in
		
		// write the changes to disk for persistence
		File rulesFile = new File(rulesPath);
		
		FileWriter writer;
		try {
			writer = new FileWriter(rulesFile, false);
			writer.write("");
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}
	
	public void setup() {
		rand = new Random();
		makeLogEntry("About to get deployA...\n");
		float stored = board.storageGetFloat("deployA", Float.NaN);
		makeLogEntry("Got deployA: " + stored + "\n");
		if (Float.isNaN(stored)) {
			makeLogEntry("stored is NaN.\n");
			// if the weights have not been previously initialized, initialize them
			java.util.Arrays.fill(deployWeights, 1);
			java.util.Arrays.fill(attackWeights, 1);
			java.util.Arrays.fill(fortifyWeights, 1);
		}
		else { // otherwise, retrieve them
			getWeightValues();
		}
	}
	
	public void makeLogEntry(String message) {
		FileWriter writer;
		try {
			// write to the file
			if (fileName == null) {
				Date date = new Date();
				fileName = date.toString();
				fileName = fileName.replace(' ', '-');
				fileName = fileName.replace(":", "");
				File file = new File(Board.getAgentPath() + File.separator + name() + "Logs" + File.separator + fileName + ".txt");
				file.getParentFile().mkdirs();
				writer = new FileWriter(file, false);
			} else {
				writer = new FileWriter(Board.getAgentPath() + File.separator + name() + "Logs" + File.separator + fileName + ".txt", true);
			}
			writer.write(message);
			writer.close(); // flush and close
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	} 
	
}
