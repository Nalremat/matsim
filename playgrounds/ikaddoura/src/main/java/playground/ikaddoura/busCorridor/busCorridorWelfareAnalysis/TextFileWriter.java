package playground.ikaddoura.busCorridor.busCorridorWelfareAnalysis;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.misc.Time;

public class TextFileWriter {
	private final static Logger log = Logger.getLogger(TextFileWriter.class);

	public void writeFile(String outputExternalIterationDirPath, Map<Integer, Double> iteration2numberOfBuses, Map<Integer, String> iteration2day, Map<Integer, Double> iteration2fare, Map<Integer, Double> iteration2capacity, Map<Integer, Double> iteration2operatorCosts, Map<Integer, Double> iteration2operatorRevenue, Map<Integer, Double> iteration2operatorProfit, Map<Integer, Double> iteration2userScore, Map<Integer, Double> iteration2userScoreSum, Map<Integer, Double> iteration2totalScore, Map<Integer, Integer> iteration2numberOfCarLegs, Map<Integer, Integer> iteration2numberOfPtLegs, Map<Integer, Integer> iteration2numberOfWalkLegs){
		File file = new File(outputExternalIterationDirPath+"/busNumberScoreStats.txt");
		   
	    try {
	    BufferedWriter bw = new BufferedWriter(new FileWriter(file));
	    String zeile1 = "ITERATION ; NumberOfBuses ; Tagesverlauf ; fare ; capacity ; OperatorCosts (AUD) ; OperatorRevenue (AUD); OperatorProfit (AUD) ; UserScore (avg. executed) ; UserScore (LogSum) (AUD) ; TotalScore ; CarLegs ; PtLegs ; WalkLegs";
	    bw.write(zeile1);
	    bw.newLine();
	
	    for (Integer iteration : iteration2numberOfBuses.keySet()){
	    	double numberOfBuses = iteration2numberOfBuses.get(iteration);
	    	double costs = iteration2operatorCosts.get(iteration);
	    	double revenue = iteration2operatorRevenue.get(iteration);
	    	double operatorProfit = iteration2operatorProfit.get(iteration);
	    	double userScore = iteration2userScore.get(iteration);
	    	double userScoreSum = iteration2userScoreSum.get(iteration);
	    	double totalScore = iteration2totalScore.get(iteration);
	    	int carLegs = iteration2numberOfCarLegs.get(iteration);
	    	int ptLegs = iteration2numberOfPtLegs.get(iteration);
	    	int walkLegs = iteration2numberOfWalkLegs.get(iteration);
	    	double fare = iteration2fare.get(iteration);
	    	double capacity = iteration2capacity.get(iteration);
	    	String tagesverlauf = iteration2day.get(iteration);
	    
	    	String zeile = iteration+ " ; "+numberOfBuses+" ; "+tagesverlauf+" ; "+fare+" ; "+capacity+" ; "+costs+ " ; "+revenue+" ; "+operatorProfit+" ; "+userScore+" ; "+userScoreSum+" ; "+totalScore+" ; "+carLegs+" ; "+ptLegs+" ; "+walkLegs;
	
	    	bw.write(zeile);
	        bw.newLine();
	    }
	
	    bw.flush();
	    bw.close();
	    log.info("Analysis Textfile written to "+file.toString());
    
	    } catch (IOException e) {}
	}

	public void writeWaitingTimes(String outputExternalIterationDirPath, int extItNr, Map<Id, Double> map) {
		File file = new File(outputExternalIterationDirPath+"/extITERS/extIt."+extItNr+"/personId2waitingTime.txt");	
		double sumWork = 0;
		double sumOther = 0;
		int anzahlWork = 0;
		int anzahlOther = 0;
		for (Id id : map.keySet()){
			double waitingTime = map.get(id)/2; // (the map gives you the sum of waiting time for both directions)
			
			if(id.toString().contains("Work")){
				sumWork = sumWork + waitingTime;
				anzahlWork++;
			}
			else if(id.toString().contains("Other")){
				sumOther = sumOther + waitingTime;
				anzahlOther++;
			}
			else {
				System.out.println("unknown personId");
			}
		}
		double meanWaitingTimeWork = sumWork/anzahlWork;
		double meanWaitingTimeOther = sumOther/anzahlOther;
		
		try {
		    BufferedWriter bw = new BufferedWriter(new FileWriter(file));
		    String zeile0 = "mean waitingTime Work: "+Time.writeTime(meanWaitingTimeWork, Time.TIMEFORMAT_HHMMSS);
		    bw.write(zeile0);
		    bw.newLine();
		    
		    String zeile01 = "mean waitingTime Other: "+Time.writeTime(meanWaitingTimeOther, Time.TIMEFORMAT_HHMMSS);
		    bw.write(zeile01);
		    bw.newLine();
		    
		    String zeile1 = "personId ; waitingTime";
		    bw.write(zeile1);
		    bw.newLine();
		
		    for (Id id : map.keySet()){
		    	
		    	Double waitingTime = map.get(id);
		
		    	String zeile = id+ " ; "+waitingTime;
		
		    	bw.write(zeile);
		        bw.newLine();
		    }
		
		    bw.flush();
		    bw.close();
		    log.info("WaitingTimes written to "+file.toString());
	    
		    } catch (IOException e) {}
	}
}