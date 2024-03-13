#!/bin/bash

if [ $# -lt 5 ]
then
        echo "Usage: $0 <dl19 res> <dl20 res> <ap/ndcg> <nqc/uef> <rlm/w2v>"
        exit
fi

mvn exec:java -Dexec.mainClass="experiments.TRECDLQPPEvaluatorWithGenVariants" -Dexec.args="$1 $2 $3 $4 $5"
