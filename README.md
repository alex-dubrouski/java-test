# java-test

## Idea
Recent Java releases added a lot of syntactical sugarcoating like `Collection.stream().forEach()` and in this repo
I would like to use OpenJDK JMH benchmarking framework to find out how it affects performance.

## Running

- To build code `$ mvn clean install`
- To run it `$ java -jar target/benchmarks.jar`
- To see list of possible options `java -jar target/benchmarks.jar -h`