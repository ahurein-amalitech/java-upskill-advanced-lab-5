import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;
import java.util.Map;
import java.util.stream.Collectors;

public class LogProcessor {
    public static void main(String[] args) {
        SparkSession spark = SparkSession.builder()
                .appName("LogProcessorApp")
                .master("local[*]")
                .getOrCreate();

        JavaSparkContext sc = new JavaSparkContext(spark.sparkContext());

        // Read log file
        JavaRDD<String> logLines = sc.textFile("./log_p_application.log");

        JavaRDD<String> errorLogs = logLines.filter(line -> line.contains("ERROR"));

        // Count occurrences of each error type
        Map<String, Long> errorCounts = errorLogs
                .map(line -> line.split(" ")[2])
                .countByValue();

        // Print results
        errorCounts.forEach((key, value) -> System.out.println(key + ": " + value));

        // Save output
        sc.parallelize(errorCounts.entrySet().stream()
                        .map(e -> e.getKey() + " : " + e.getValue())
                        .collect(Collectors.toList()))
                .saveAsTextFile("output/error_summary");

        sc.close();
    }
}
