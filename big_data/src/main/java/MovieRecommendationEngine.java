import org.apache.spark.ml.evaluation.RegressionEvaluator;
import org.apache.spark.ml.recommendation.ALS;
import org.apache.spark.ml.recommendation.ALSModel;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import java.util.Collections;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.explode;

public class MovieRecommendationEngine {
    private final SparkSession spark;

    public MovieRecommendationEngine() {
        this.spark = SparkSession.builder()
                .appName("MovieRecommendationEngine")
                .master("local[*]")
                .config("spark.executor.memory", "2g")
                .getOrCreate();
    }

    private StructType getSchema(){
        return new StructType()
                .add("userId", DataTypes.IntegerType, false)
                .add("movieId", DataTypes.IntegerType, false)
                .add("rating", DataTypes.FloatType, false)
                .add("timestamp", DataTypes.LongType, false);
    }

    private Dataset<Row> loadRatingsData(String path){
        return this.spark.read()
                .option("header", "true")
                .schema(getSchema())
                .csv(path);
    }

    private ALSModel trainAlsModel(Dataset<Row> trainingData){
        ALS als = new ALS()
                .setMaxIter(10)
                .setRegParam(0.01)
                .setUserCol("userId")
                .setItemCol("movieId")
                .setRatingCol("rating")
                .setColdStartStrategy("drop");
        return als.fit(trainingData);
    }

    private double evaluateModel(ALSModel model, Dataset<Row> testData) {
        Dataset<Row> predictions = model.transform(testData);

        RegressionEvaluator evaluator = new RegressionEvaluator()
                .setMetricName("rmse")
                .setLabelCol("rating")
                .setPredictionCol("prediction");

        return evaluator.evaluate(predictions);
    }

    private Dataset<Row> generateRecommendations(ALSModel model,int userId, int numRecommendations){
        StructType userSchema = new StructType(new StructField[]{
                DataTypes.createStructField("userId", DataTypes.IntegerType, false)
        });

        Dataset<Row> users = spark.createDataFrame(
                Collections.singletonList(Row.fromTuple(scala.Tuple1.apply(userId))),
                userSchema
        );
        return model.recommendForUserSubset(users, numRecommendations);
    }

    private Dataset<Row> flattenRecommendations(Dataset<Row> recommendations) {

        return recommendations
                .select(
                        col("userId"),
                        explode(col("recommendations")).as("rec")
                )
                .select(
                        col("userId"),
                        col("rec.movieId").as("recommendedMovieId"),
                        col("rec.rating").as("predictedRating")
                );
    }

    public void run(String dataPath){
        try {
            Dataset<Row> ratingsData = loadRatingsData(dataPath);
            Dataset<Row>[] splits = ratingsData.randomSplit(new double[]{0.8, 0.2}, 42L);
            Dataset<Row> trainingData = splits[0];
            Dataset<Row> testData = splits[1];

            ALSModel model = trainAlsModel(trainingData);
            double rmse = evaluateModel(model, testData);
            System.out.println("Root Mean Square Error = " + rmse);

            int sampleUserId = 1;
            Dataset<Row> userRecs = generateRecommendations(model, sampleUserId, 10);

            System.out.println("Top 10 movie recommendations for user " + sampleUserId + ":");
            userRecs.show(false);

            Dataset<Row> allRecs = model.recommendForAllUsers(10);
            Dataset<Row> flattenedRecs = flattenRecommendations(allRecs);

            System.out.println("Sample of flattened recommendations:");
            flattenedRecs.show(10);

            flattenedRecs
                    .coalesce(1)
                    .write()
                    .mode("overwrite")
                    .option("header", "true")
                    .csv("recommendations_output");

        } catch (Exception e) {
            System.err.println("Error in recommendation engine: " + e.getMessage());
            e.printStackTrace();
        } finally {
            spark.stop();
        }
    }

    public static void main(String[] args){
        MovieRecommendationEngine recommender = new MovieRecommendationEngine();
        recommender.run("./data.csv");
    }
}
