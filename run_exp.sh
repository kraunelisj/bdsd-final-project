#!/bin/bash

for i in {1..9}; do
    java -jar target/scala-2.11/timing-spark-assembly-0.1_2.11.1.jar --conf mx.lenet.conf mselect.csv ${i}E-7 | grep "^0" > results-timing${i}e-7.csv
done

