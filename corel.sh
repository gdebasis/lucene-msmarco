#!/bin/bash

if [ $# -lt 4 ]
then
        echo "Usage: $0 <dl19 res> <dl20 res> <ap/ndcg> <nqc/uef>"
        exit
fi

mvn exec:java -Dexec.mainClass="experiments.TRECDLQPPEvaluator" -Dexec.args="$1 $2 $3 $4"
