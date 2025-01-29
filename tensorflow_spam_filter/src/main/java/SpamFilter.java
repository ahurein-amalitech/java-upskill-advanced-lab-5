import weka.classifiers.bayes.NaiveBayes;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.StringToWordVector;

import java.io.File;

public class SpamFilter {
    public static void main(String[] args) throws Exception {
        CSVLoader loader = new CSVLoader();
        String path = new File("").getAbsolutePath() + "/tensorflow_spam_filter/src/main/java/spam.csv";
        loader.setSource(new File(path));

        //Column index for the text content (v2)
        loader.setStringAttributes("2");
        Instances data = loader.getDataSet();
        // First column (v1) is the class (ham/spam)
        data.setClassIndex(0);

        // Convert text data to numerical form
        StringToWordVector filter = new StringToWordVector();
        filter.setInputFormat(data);
        Instances filteredData = Filter.useFilter(data, filter);

        // Train the model
        NaiveBayes classifier = new NaiveBayes();
        classifier.buildClassifier(filteredData);

        // Create a new instance for prediction
        Instance filtered = (Instance) filteredData.firstInstance().copy();
        filtered.setValue(data.attribute(1), "Congratulations! You've won a $1000 Walmart gift card. Click here to claim now.");

        // Predict the class
        double prediction = classifier.classifyInstance(filtered);
        System.out.println("Prediction: " + data.classAttribute().value((int) prediction));
    }

}