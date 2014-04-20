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
		Rule[] deployRules, attackRules, fortifyRules;
		float[] deployWeights, attackWeights, fortifyWeights;
		// A filename for the log
		private String fileName;
		private String rulesPath = Board.getAgentPath() + "rules.txt";
		private float explorationThreshold = 0.15f; // probability to explore instead of exploit (0.0 - 1.0 range)
		private String[] lettersArray = {"A","B","C","D","E","F","G","H","I","J","K"};

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
	
	
	private float howDivided(Country country, float[] weights) 
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
		return IDList.size()*weights[2];
	
	}
	
	
	/**
	 * This method weights the number of troops according to their distance, with closer troops being more relevant.
	 * 
	 * @param numberOfTroops
	 * @return The weighted number of troops
	 */
	public float calculateWeightedTroopValue(Country srcCountry, Country destCountry, float weights[]) {
		float result = 0;
		int distance = BoardHelper.easyCostBetweenCountries(srcCountry,destCountry, countries).length;
		result = weights[3] * destCountry.getArmies()/(float) distance;
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
					enemyTroops += calculateWeightedTroopValue(country, otherCountry, weights);
				}
				else
				{
					friendlyTroops += calculateWeightedTroopValue(country, otherCountry,weights);
				}
			}
			enemyTroops += BoardHelper.getEnemyArmiesInContinent(ID, i, countries);
		}
		
		float divided = howDivided(country,weights);
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
		result = weights[7]*income + weights[8]*cards;
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
		int armiesCount=BoardHelper.getPlayerArmies(playerID, countries);
		float result=(weights[4]*continentsHeld + weights[5]*armiesCount)/(weights[6]*greatestVulnerability);
		return result;
	}
	
	private float calculateAdvantage(int playerID, float[] weights) 
	{
		float stability=calculateStability(playerID, weights);
		int[] enemyPlayers=getEnemyPlayerIDs(playerID);
		float totalThreat=0;
		for(int i=0; i<enemyPlayers.length; i++)
		{
			totalThreat+=calculateThreat(enemyPlayers[i], weights);
		}
		float result=weights[9]*stability-weights[10]*totalThreat;
		return result;
	}
	private int[] getEnemyPlayerIDs(int playerID)
	{
		
		int remainingPlayers=board.getNumberOfPlayersLeft()-1;
		int[] result=new int[remainingPlayers];
		List <Integer> enemyIDs= new ArrayList <Integer>();
		int i=0;
		int j=0;
		while(i<remainingPlayers)
		{
			int currentID=countries[j].getOwner();
			Integer ID=new Integer(currentID);
			if(currentID!=playerID && enemyIDs.contains(ID))
			{
				result[i]=currentID;
				enemyIDs.add(ID);
				i++;
			}
			j++;
		}
		
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
		adjustRules(gameResult);
		return answer;
	}

	public String message( String message, Object data )
	{
	if ("youLose".equals(message))
		{
			float gameResult = lossFitnessFunction();
			adjustRules(gameResult);
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
		makeLogEntry("------Rule Path: " + rulesPath + "\n\n");

		try {
			reader = new FileReader(rulesPath);
			try {
				reader.read(raw);
				reader.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
		}
		String[] threeArrays = raw.toString().split("\n---\n");
		String deployString = threeArrays[0];
		String[] deployRulesStrings = deployString.toString().split("\n");
		deployRules = new Rule[deployRulesStrings.length];
		for (int i=1; i < deployRulesStrings.length; i++) {
			deployRules[i] = new Rule(deployRulesStrings[i]);
		}
		// sort in ascending rank (1,2,...,n) i.e., better rules first
		RuleComparator<Rule> c = new RuleComparator<Rule>();
		Arrays.sort(deployRules, c);
		// for A-L, find the first rule that mentions that letter
		for (int i = 0; i < lettersArray.length; i++) {
			Rule deployRule;
			int j = 0;
			do {
				deployRule = deployRules[j];
				if (j < deployRules.length - 1) { // iterate if not at the end
					j++;
				} else { // otherwise start over
					j = 0;
				}
			// stop iterating with probability P = (1 - explorationThreshold) if a matching rule is found
			} while (deployRule.getName().equals(lettersArray[i]) == false || rand.nextFloat() < explorationThreshold);
		// assign that rule's weight to the corresponding letter's index (A=0,B=1,...,L=12)
			deployWeights[i] = deployRule.getWeight().floatValue();
		}
		
		
		// get the array of attack rules, sorted in ascending rank (1,2,...,n)
		String attackString = threeArrays[1];
		String[] attackRulesStrings = attackString.toString().split("\n");
		attackRules = new Rule[attackRulesStrings.length];
		for (int i=1; i < attackRulesStrings.length; i++) {
			attackRules[i] = new Rule(attackRulesStrings[i]);
		}
		Arrays.sort(attackRules, c);
		// for A-L, find the first rule that mentions that letter
		String[] lettersArray = {"A","B","C","D","E","F","G","H","I","J","K","L"};
		for (int i = 0; i < lettersArray.length; i++) {
			Rule attackRule;
			int j = 0;
			do {
				attackRule = attackRules[j];
				if (j < attackRules.length - 1) { // iterate if not at the end
					j++;
				} else { // otherwise start over
					j = 0;
				}
			// stop iterating with probability P = (1 - explorationThreshold) if a matching rule is found
			} while (attackRule.getName().equals(lettersArray[i]) == false || rand.nextFloat() < explorationThreshold);
		// assign that rule's weight to the corresponding letter's index (A=0,B=1,...,L=12)
			attackWeights[i] = attackRule.getWeight().floatValue();
		}
		
		// get the array of fortify rules, sorted in ascending rank (1,2,...,n)
		String fortifyString = threeArrays[2];
		String[] fortifyRulesStrings = fortifyString.toString().split("\n");
		fortifyRules = new Rule[fortifyRulesStrings.length];
		for (int i=1; i < fortifyRulesStrings.length; i++) {
			fortifyRules[i] = new Rule(fortifyRulesStrings[i]);
		}
		// for A-L, find the first rule that mentions that letter
		String[] lettersArray = {"A","B","C","D","E","F","G","H","I","J","K"};
		for (int i = 0; i < lettersArray.length; i++) {
			Rule fortifyRule;
			int j = 0;
			do {
				fortifyRule = fortifyRules[j];
				if (j < fortifyRules.length - 1) { // iterate if not at the end
					j++;
				} else { // otherwise start over
					j = 0;
				}
			// stop iterating with probability P = (1 - explorationThreshold) if a matching rule is found
			} while (fortifyRule.getName().equals(lettersArray[i]) == false || rand.nextFloat() < explorationThreshold);
		// assign that rule's weight to the corresponding letter's index (A=0,B=1,...,L=12)
			fortifyWeights[i] = fortifyRule.getWeight().floatValue();
		}
	}
	
	public void adjustRules(float adjustment) {
		String newRules = "";
		// change each deploy weight's rank by amount adjustment - determined by the fitness function and passed in
		for (int i=0; i < deployWeights.length; i++) {
			String name = lettersArray[i];
			Float weight = Float(deployWeights[i]);
			for (Rule rule : deployRules) {
				if (rule.getName().equals(name) && rule.getWeight().floatValue() == weight.floatValue()) {
					int currentRank = rule.getRank();
					currentRank = currentRank + adjustment;
					rule.SetRank(currentRank);
				}
			}
		}
		// change each attack weight's rank by amount gameResult - determined by the fitness function and passed in
		for (int i=0; i < attackWeights.length; i++) {
			String name = lettersArray[i];
			Float weight = Float(attackWeights[i]);
			for (Rule rule : attackRules) {
				if (rule.getName().equals(name) && rule.getWeight().floatValue() == weight.floatValue()) {
					int currentRank = rule.getRank();
					currentRank = currentRank + adjustment;
					rule.SetRank(currentRank);
				}
			}
		}
		// change each fortify weight's rank by amount gameResult - determined by the fitness function and passed in
		for (int i=0; i < fortifyWeights.length; i++) {
			String name = lettersArray[i];
			Float weight = Float(fortifyWeights[i]);
			for (Rule rule : fortifyRules) {
				if (rule.getName().equals(name) && rule.getWeight().floatValue() == weight.floatValue()) {
					int currentRank = rule.getRank();
					currentRank = currentRank + adjustment;
					rule.SetRank(currentRank);
				}
			}
		}
		// convert the rules to string format
		
		for (int i=0; i < deployRules.length; i++) {
			newRules = newRules + "\n" + deployRules[i].toString();
			newRules = newRules + "\n" + attackRules[i].toString();
			newRules = newRules + "\n" + fortifyRules[i].toString();
		}
		
		// write the changes to disk for persistence, overwriting the old values
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
		getWeightValues();
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
