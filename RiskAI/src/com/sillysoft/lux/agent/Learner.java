package com.sillysoft.lux.agent;

import com.sillysoft.lux.*;
import com.sillysoft.lux.util.*;

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
	float[][] outgoingWeights = winFitnessFunction();
	storeWeightValues(outgoingWeights);
	return answer;
	}

	public String message( String message, Object data )
	{
	if ("youLose".equals(message))
		{
		// run the "loss" fitness function to adjust rules
		// store the new weight values
		int conqueringPlayerID = ((Integer)data).intValue();
		float[][] outgoingWeights = lossFitnessFunction();
		storeWeightValues(outgoingWeights);
		// now you could log that you have lost this game...
//		board.playAudioAtURL("http://sillysoft.net/sounds/boo.wav");
		}
	return null;
	}
	
	public float[][] winFitnessFunction() {
		float[][] results =  new float[3][12];
		for (int i=0;i<12;i++) {
			float newVal;
			// apply fitness function to deploy[i]
			newVal = 3; // for testing purposes
			results[1][i] = newVal;
		}
		for (int i=0;i<12;i++) {
			float newVal;
			// apply fitness function to attack[i]
			newVal = 3;
			results[2][i] = newVal;
		}
		for (int i=0;i<12;i++) {
			float newVal;
			// apply fitness function to fortify[i]
			newVal = 3;
			results[3][i] = newVal;
		}
		return results;
	}
	
	public float[][] lossFitnessFunction() {
		float[][] results =  new float[3][12];
		for (int i=0;i<12;i++) {
			float newVal;
			// apply fitness function to deploy[i]
			newVal = 2; // for testing purposes
			results[0][i] = newVal;
		}
		for (int i=0;i<12;i++) {
			float newVal;
			// apply fitness function to attack[i]
			newVal = 2;
			results[1][i] = newVal;
		}
		for (int i=0;i<12;i++) {
			float newVal;
			// apply fitness function to fortify[i]
			newVal = 2;
			results[2][i] = newVal;
		}
		return results;
	}
	
	public void getWeightValues() {
		deployWeights[0] = board.storageGetFloat("deployA", Float.NaN);
		deployWeights[1] = board.storageGetFloat("deployB", Float.NaN);
		deployWeights[2] = board.storageGetFloat("deployC", Float.NaN);
		deployWeights[3] = board.storageGetFloat("deployD", Float.NaN);
		deployWeights[4] = board.storageGetFloat("deployE", Float.NaN);
		deployWeights[5] = board.storageGetFloat("deployF", Float.NaN);
		deployWeights[6] = board.storageGetFloat("deployG", Float.NaN);
		deployWeights[7] = board.storageGetFloat("deployH", Float.NaN);
		deployWeights[8] = board.storageGetFloat("deployI", Float.NaN);
		deployWeights[9] = board.storageGetFloat("deployJ", Float.NaN);
		deployWeights[10] = board.storageGetFloat("deployK", Float.NaN);
		deployWeights[11] = board.storageGetFloat("deployL", Float.NaN);
		
		attackWeights[0] = board.storageGetFloat("attackA", Float.NaN);
		attackWeights[1] = board.storageGetFloat("attackB", Float.NaN);
		attackWeights[2] = board.storageGetFloat("attackC", Float.NaN);
		attackWeights[3] = board.storageGetFloat("attackD", Float.NaN);
		attackWeights[4] = board.storageGetFloat("attackE", Float.NaN);
		attackWeights[5] = board.storageGetFloat("attackF", Float.NaN);
		attackWeights[6] = board.storageGetFloat("attackG", Float.NaN);
		attackWeights[7] = board.storageGetFloat("attackH", Float.NaN);
		attackWeights[8] = board.storageGetFloat("attackI", Float.NaN);
		attackWeights[9] = board.storageGetFloat("attackJ", Float.NaN);
		attackWeights[10] = board.storageGetFloat("attackK", Float.NaN);
		attackWeights[11] = board.storageGetFloat("attackL", Float.NaN);
		
		fortifyWeights[0] = board.storageGetFloat("fortifyA", Float.NaN);
		fortifyWeights[1] = board.storageGetFloat("fortifyB", Float.NaN);
		fortifyWeights[2] = board.storageGetFloat("fortifyC", Float.NaN);
		fortifyWeights[3] = board.storageGetFloat("fortifyD", Float.NaN);
		fortifyWeights[4] = board.storageGetFloat("fortifyE", Float.NaN);
		fortifyWeights[5] = board.storageGetFloat("fortifyF", Float.NaN);
		fortifyWeights[6] = board.storageGetFloat("fortifyG", Float.NaN);
		fortifyWeights[7] = board.storageGetFloat("fortifyH", Float.NaN);
		fortifyWeights[8] = board.storageGetFloat("fortifyI", Float.NaN);
		fortifyWeights[9] = board.storageGetFloat("fortifyJ", Float.NaN);
		fortifyWeights[10] = board.storageGetFloat("fortifyK", Float.NaN);
		fortifyWeights[11] = board.storageGetFloat("fortifyL", Float.NaN);
	}
	
	public void storeWeightValues(float[][] weights) {
		board.storagePutFloat("deployA", weights[0][0]);
		board.storagePutFloat("deployB", weights[0][1]);
		board.storagePutFloat("deployC", weights[0][2]);
		board.storagePutFloat("deployD", weights[0][3]);
		board.storagePutFloat("deployE", weights[0][4]);
		board.storagePutFloat("deployF", weights[0][5]);
		board.storagePutFloat("deployG", weights[0][6]);
		board.storagePutFloat("deployH", weights[0][7]);
		board.storagePutFloat("deployI", weights[0][8]);
		board.storagePutFloat("deployJ", weights[0][9]);
		board.storagePutFloat("deployK", weights[0][10]);
		board.storagePutFloat("deployL", weights[0][11]);
		
		board.storagePutFloat("attackA", weights[1][0]);
		board.storagePutFloat("attackB", weights[1][1]);
		board.storagePutFloat("attackC", weights[1][2]);
		board.storagePutFloat("attackD", weights[1][3]);
		board.storagePutFloat("attackE", weights[1][4]);
		board.storagePutFloat("attackF", weights[1][5]);
		board.storagePutFloat("attackG", weights[1][6]);
		board.storagePutFloat("attackH", weights[1][7]);
		board.storagePutFloat("attackI", weights[1][8]);
		board.storagePutFloat("attackJ", weights[1][9]);
		board.storagePutFloat("attackK", weights[1][10]);
		board.storagePutFloat("attackL", weights[1][11]);
		
		board.storagePutFloat("fortifyA", weights[2][0]);
		board.storagePutFloat("fortifyB", weights[2][1]);
		board.storagePutFloat("fortifyC", weights[2][2]);
		board.storagePutFloat("fortifyD", weights[2][3]);
		board.storagePutFloat("fortifyE", weights[2][4]);
		board.storagePutFloat("fortifyF", weights[2][5]);
		board.storagePutFloat("fortifyG", weights[2][6]);
		board.storagePutFloat("fortifyH", weights[2][7]);
		board.storagePutFloat("fortifyI", weights[2][8]);
		board.storagePutFloat("fortifyJ", weights[2][9]);
		board.storagePutFloat("fortifyK", weights[2][10]);
		board.storagePutFloat("fortifyL", weights[2][11]);
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
			makeLogEntry("retrieving stored...\n");
			getWeightValues();
			makeLogEntry("deployWeights: " + deployWeights.toString()
					+ "\nattackWeights: " + attackWeights.toString() + "\nfortifyWeights: " + fortifyWeights.toString() + "\n");
		}
	}
	
}
