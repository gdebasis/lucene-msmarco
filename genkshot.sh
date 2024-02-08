#!/bin/bash

if [ $# -lt 3 ]
then
        echo "Usage: $0 <TREC DL query file> <out json file> <out text file>"
        exit
fi

mvn exec:java -Dexec.mainClass="retrieval.KNNRelModel" -Dexec.args="$1 $2" > $3 

