#!/bin/bash

if [ $# -lt 5 ]
then
        echo "Usage: $0 <dl19 res> <dl20 res> <method (nqc/jm/corel)> <ap/ndcg> <nqc/uef>"
        exit
fi

mvn exec:java -Dexec.mainClass="experiments.TRECDLQPPEvaluator" -Dexec.args="$1 $2 $3 $4 $5"
