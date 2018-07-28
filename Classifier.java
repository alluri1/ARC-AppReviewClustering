package ARC;

import java.io.*;
import java.util.*;

/**
 * Performs text classification using a Naive Bayes Classifier.
 * 
 * Builds a Naive Bayes Classifier using a set of training documents,
 * uses the classifier to classify individual test documents and report the class label,
 * and uses the classifier to classify a set of test documents and report the classification accuracy.
 * @author Brienna Herold
 */
public class Classifier {
	ArrayList<String> trainingDocs; // indices are aligned with trainingLabels
	ArrayList<Integer> trainingLabels; // numbers for the convenience of implementation, e.g. 0 and 1
	int numClasses; 
	int[] classCounts; // number of docs per class, will be used for computing the probability of each class
	String[] classStrings; // concatenated string for a given class, a big document that includes all documents from that class, helps us compute the class conditional probability of each term in this class
	int[] classTokenCounts; // total number of tokens per class including duplicates, later computed using the classString as a single document
	HashMap<String,Double>[] condProb; // stores the class conditional probability of each term, a hash map for each class
	HashSet<String> vocabulary; // entire vocabulary, distinct?? terms in the training set, for denominator TNc + |V| in Laplace smoothing
	
	/**
	 * Build a Naive Bayes classifier using a training document set
	 * @param trainDocs the training document folder
	 * @param myLabels labels of the training documents
	 */
	public Classifier(ArrayList<String> trainDocs, ArrayList<Integer> myLabels) {
		trainingDocs = trainDocs;
		trainingLabels = myLabels;
		numClasses = 4; // hard code num of classes
		classCounts = new int[numClasses];
		classStrings = new String[numClasses];
		classTokenCounts = new int[numClasses];
		condProb = new HashMap[numClasses];
		vocabulary = new HashSet<String>();
		Double threshold =10.0;
		
		// For each class, initialize classString & class conditional probability hashmap
		for (int i = 0; i < numClasses; i++) {
			classStrings[i] = "";
			condProb[i] = new HashMap<String,Double>();
		}
		
		// For each training document, based on its class
		for (int i = 0; i < trainingLabels.size(); i++){
			// Increment class count (this is why integers provide convenience, they act as indexes here)
			classCounts[trainingLabels.get(i)]++; 
			// Concatenate its string to the big class string
			classStrings[trainingLabels.get(i)] += (trainingDocs.get(i) + " ");
		}
		
		// For each class, tokenize & populate hash map of conditional probabilities (not yet computing them)
		for(int i = 0; i < numClasses; i++){
			// Split the class string into tokens based on given delimiters
			String[] tokens = classStrings[i].split("[ _\".,?!/:;$%&*+()\\-\\^]+");
			classTokenCounts[i] = tokens.length;
			// For each token,
			for(String token:tokens){
				vocabulary.add(token);
				// If we've already recorded the token, just increase the occurrence count by 1
				if(condProb[i].containsKey(token)){
					double count = condProb[i].get(token);
					condProb[i].put(token, count+1);
				} else {
					// Otherwise put a new entry for the token, initializing the occurrence count as 1
					condProb[i].put(token, 1.0);
				}
					
			}
		}
		
		// Compute the class conditional probability for each term in all the classes
		// Each hash map is used to store the class conditional probability of all the terms for a given class
		// Class conditional probability formula: P(t | c) 
		// It is the probability that a term belongs to class c, which we estimate as Tct / TNc 
		// where Tct is the number of occurrences of t in documents from class c, and TNc is the total number of terms including duplicate ones in class c. 
		for(int i = 0; i < numClasses; i++){
			Iterator<Map.Entry<String, Double>> iterator = condProb[i].entrySet().iterator();
			int vSize = vocabulary.size();
			int tokensImpacted = 0;
			while (iterator.hasNext()) {
				Map.Entry<String, Double> entry = iterator.next();
				String token = entry.getKey();
				Double count = entry.getValue();
				if(count > threshold){
					count = (count+1)/(classTokenCounts[i]+vSize); // conditional probability equation with Laplace smoothing
					condProb[i].put(token, count);
				}else{
					tokensImpacted++;
					//System.out.println("Removing token: "+token +"\tFrequency: "+ count.intValue());
					iterator.remove();
				}
			}
			// System.out.println(condProb[i]); // print out all conditional probabilities for each term in the class
			System.out.println("Tokens impacted by frequency extraction: " + tokensImpacted);
		}
	}
	
	/**
	 * Classify a test doc using log of posterior probability
	 * We are using logs because prior belief and conditional probability are all between 0-1,
	 * and if we multiply a lot of those values with each other, the whole product will be too small
	 * for the computer, so we need to use log as it is a monotonic function.
	 * If you have a large posterior probability, the log of posterior is also the largest 
	 * @param doc test doc
	 * @return class label
	 */
	public int classify(String doc){
		int label = 0;
		int vSize = vocabulary.size();
		double[] score = new double[numClasses]; // compute a score for each class, and the greater score is the class that gets assigned
		
		// Calculate the first term of the posterior probability, which is the prior belief,
		// aka the prior probability of a class
		// prior belief P(c) Nc / N where Nc is the number of documents in class c and N is the total number of documents. It is between 0-1
		for (int i = 0; i < score.length; i++){
			score[i] = Math.log(classCounts[i]*1.0/trainingDocs.size()); // the *1.0 converts int to double
		}
		
		// Calculate class conditional probability for each term in the test document
		// and multiply together to get the product
		String[] tokens = doc.split("[ _\".,?!/:;$%&*+()\\-\\^]+");
		for (int i = 0; i < numClasses; i++){
			// Add each term's conditional probability to prior belief and any other terms' conditional probabilities
			for (String token: tokens){
				if (condProb[i].containsKey(token))
					// Note: we are adding instead of multiplying because we are using logs
					score[i] += Math.log(condProb[i].get(token));
				else {
					score[i] += Math.log(1.0 / (classTokenCounts[i]+vSize)); // conditional probability equation with smoothing for new term that is not part of training vocabulary
				}	
			}
		}
		
		// Assign the class with the greater score
		double maxScore = score[0];
		for(int i = 0; i < score.length; i++){
			if (score[i] > maxScore) {
				label = i;
			}
		}
		
		return label;
	}
	
	/**
	 *  Classify a set of testing documents and report the accuracy
	 * @param  docs test documents
	 * @param labels labels for test documents
	 * @return classification accuracy
	 */
	public double classifyAll(ArrayList<String> docs, ArrayList<Integer> labels) {
		ArrayList<String> testDocs = docs;
		ArrayList<Integer> testLabels = labels;
		ArrayList<Integer> classifiedLabels = new ArrayList<Integer>();
		double accuracy = 0.0; 
		
		// Classify each test doc
		for (int i = 0; i < testDocs.size(); i++) {
			int score = classify(testDocs.get(i));
			classifiedLabels.add(score);
		}
		
		// Calculate accuracy
		int correct = 0;
		for (int i = 0; i < classifiedLabels.size(); i++) {
			if (classifiedLabels.get(i) == testLabels.get(i)) {
				correct++;
			}
		}
		accuracy = ((double)correct)/testDocs.size();
		System.out.println("Correctly classified " + correct + " out of " + testDocs.size());
		return accuracy;
	}
}
